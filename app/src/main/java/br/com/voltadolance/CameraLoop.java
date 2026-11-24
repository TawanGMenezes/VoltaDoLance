package br.com.voltadolance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.net.Uri;
import android.os.*;
import android.view.Surface;
import android.view.TextureView;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** One camera session and encoder lifetime for rolling history and a marked event. */
public final class CameraLoop {
    public interface Listener {
        void onStatus(String message);
        void onSaved(Uri uri);
        void onError(String error);
        default void onPcm(byte[] pcm,int length) { }
    }
    private final Activity activity;
    private final TextureView preview;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final CaptureState state=new CaptureState();
    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface previewSurface;
    private ContinuousRecorder recorder;
    private DualFrameComposer composer;
    private DualCameraSession dualSession;
    private DualCaptureConfig dualConfig;
    private boolean dualVideo;
    private CameraTuning tuning;
    private CaptureRequest.Builder repeatingRequest;
    private float zoom=1f,rollbackZoom=1f;
    private CameraLenses.Lens ultraWide,activeLens,rollbackLens;
    private float[] primaryChoices;
    private boolean ultraRejected;
    private int pastSeconds=15,orientation=90,generation,modeIndex,facing=CameraCharacteristics.LENS_FACING_BACK;
    private long maxEventMs=600000;
    private boolean running,closing,activityActive=true,safetyPaused,manualPaused,disposed;
    private String requestedMode="1280x720@30",cameraId,safetyNotice="";
    private Integer rollbackFacing;
    private String rollbackMode;
    private List<CameraModes.Mode> modes=Collections.emptyList();

    public CameraLoop(Activity activity,TextureView preview,Listener listener) {
        this.activity=activity; this.preview=preview; this.listener=listener;
    }
    public CaptureState state() { return state; }
    public void setPastSeconds(int seconds) { pastSeconds=seconds; if(recorder!=null) recorder.setPastSeconds(seconds); }
    public void setMode(String key) { requestedMode=key; }
    public void setDualVideo(boolean enabled){if(!running&&!isCapturing()){dualVideo=enabled;if(enabled){facing=CameraCharacteristics.LENS_FACING_BACK;activeLens=null;}}}
    public boolean isDualVideo(){return dualVideo;}
    public float previewAspectRatio(){return dualConfig==null?9f/16f:dualConfig.outputMode.width/(float)dualConfig.outputMode.height;}
    public float[] zoomChoices(){
        if(dualVideo&&dualSession!=null)return dualSession.zoomChoices();
        float[] normal=activeLens!=null&&primaryChoices!=null?primaryChoices:tuning==null?new float[]{1f}:tuning.choices();
        return lensChoices(normal,facing==CameraCharacteristics.LENS_FACING_BACK&&!dualVideo?ultraWide:null);
    }
    static float[] lensChoices(float[] normal,CameraLenses.Lens lens){
        if(lens==null||normal.length>0&&normal[0]<1f)return normal;
        float[] choices=new float[normal.length+1];choices[0]=lens.ratio;System.arraycopy(normal,0,choices,1,normal.length);return choices;
    }
    public float zoomRatio(){return dualVideo&&dualSession!=null?dualSession.zoomRatio():activeLens==null?zoom:activeLens.ratio;}
    boolean requiresLensSwitch(float ratio){return !dualVideo&&facing==CameraCharacteristics.LENS_FACING_BACK&&ultraWide!=null&&((activeLens==null&&Math.abs(ratio-ultraWide.ratio)<.025f)||(activeLens!=null&&Math.abs(ratio-activeLens.ratio)>=.025f));}
    public boolean canSelectZoom(float ratio){return (state.canMark()||state.isRecording())&&(!state.isRecording()||!requiresLensSwitch(ratio));}
    public String stabilizationLabel(){return !running?"Estabilização • câmera parada":dualVideo&&dualSession!=null?dualSession.stabilizationLabel():tuning==null?"Estabilização • verificando suporte":tuning.stabilizationLabel();}
    public boolean setZoom(float ratio){
        if(!running||closing||!(state.canMark()||state.isRecording()))return false;
        if(dualVideo)return dualSession!=null&&dualSession.setZoom(ratio);
        if(requiresLensSwitch(ratio)){
            if(state.isRecording()){listener.onError("Encerre e salve o lance antes de trocar a lente");return false;}
            return switchRearLens(activeLens==null?ultraWide:null,ratio);
        }
        if(activeLens!=null)return true;
        if(tuning==null||session==null||repeatingRequest==null)return false;
        float previous=zoom;
        try{zoom=tuning.apply(repeatingRequest,ratio);submitRepeating();return true;}
        catch(Exception e){try{zoom=tuning.apply(repeatingRequest,previous);submitRepeating();}catch(Exception ignored){}listener.onError("Esta aproximação não foi aceita pela câmera");return false;}
    }
    private final CameraCaptureSession.CaptureCallback tuningResults=new CameraCaptureSession.CaptureCallback(){
        @Override public void onCaptureCompleted(CameraCaptureSession owner,CaptureRequest request,TotalCaptureResult result){if(owner==session&&tuning!=null){
            CaptureResult actual=activeLens!=null&&activeLens.physicalId!=null?result.getPhysicalCameraResults().get(activeLens.physicalId):result;
            if(actual!=null)tuning.observe(actual);
        }}
    };
    private void submitRepeating()throws CameraAccessException{
        try{session.setRepeatingRequest(repeatingRequest.build(),tuningResults,main);}
        catch(IllegalArgumentException e){if(tuning==null||!tuning.disableVideoStabilization())throw e;tuning.apply(repeatingRequest,zoom);session.setRepeatingRequest(repeatingRequest.build(),tuningResults,main);}
    }
    public void setFacing(int value) {if(!running&&!isCapturing()){int next=value==CameraCharacteristics.LENS_FACING_FRONT?value:CameraCharacteristics.LENS_FACING_BACK;if(next!=facing)activeLens=null;facing=next;}}
    public int facing() {return facing;}
    public String lensLabel(){return facing==CameraCharacteristics.LENS_FACING_FRONT?"Frontal":activeLens==null?"Traseira":"Ultra-angular";}
    public String selectedMode(){return dualVideo||modeIndex<0||modeIndex>=modes.size()?requestedMode:modes.get(modeIndex).key();}
    private void persistSelection(){activity.getSharedPreferences("local_settings",0).edit().putInt("camera_facing",facing).putString("video_mode",selectedMode()).apply();}
    public boolean switchCamera() {
        if(isCapturing()||closing||disposed||dualVideo||rollbackFacing!=null)return false;
        try{
            int destination=facing==CameraCharacteristics.LENS_FACING_BACK?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            String target=CameraModes.cameraForFacing(manager,destination);
            List<CameraModes.Mode> supported=CameraModes.detect(manager.getCameraCharacteristics(target));
            int selected=CameraModes.chooseModeIndex(supported,selectedMode());
            if(selected<0)throw new IllegalStateException("Nenhum modo de vídeo compatível nesta câmera");
            int previous=facing;String previousMode=selectedMode();CameraLenses.Lens previousLens=activeLens;float previousZoom=zoom;
            stopBuffer();activeLens=null;zoom=1f;facing=destination;requestedMode=supported.get(selected).key();modes=supported;modeIndex=selected;
            preview.setScaleX(facing==CameraCharacteristics.LENS_FACING_FRONT?-1f:1f);
            if(manualPaused){persistSelection();state.stopped();status(lensLabel()+" selecionada • "+modeLabel()+" • histórico pausado");}
            else{rollbackFacing=previous;rollbackMode=previousMode;rollbackLens=previousLens;rollbackZoom=previousZoom;start();}
            return true;
        }catch(Exception e){listener.onError("Não foi possível trocar: "+e.getMessage());return false;}
    }
    private boolean switchRearLens(CameraLenses.Lens target,float ratio){
        if(isCapturing()||closing||disposed||dualVideo||rollbackFacing!=null)return false;
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            List<CameraModes.Mode> available=target==null?CameraModes.detect(manager.getCameraCharacteristics(CameraModes.backCamera(manager))):target.modes;
            int selected=CameraModes.chooseModeIndex(available,selectedMode());if(selected<0)throw new IllegalStateException("Sem formato de vídeo compatível");
            CameraLenses.Lens previous=activeLens;String previousMode=selectedMode();float previousZoom=zoom;
            stopBuffer();activeLens=target;zoom=target==null?ratio:1f;requestedMode=available.get(selected).key();modes=available;modeIndex=selected;
            rollbackFacing=facing;rollbackMode=previousMode;rollbackLens=previous;rollbackZoom=previousZoom;
            state.preparing();status("Trocando para "+lensLabel().toLowerCase(Locale.ROOT)+" • preparando novo histórico");start();return true;
        }catch(Exception e){listener.onError("Não foi possível trocar a lente: "+e.getMessage());return false;}
    }
    public void setPostMinutes(int minutes) {
        if(!Arrays.asList(0,3,10,20,30,60).contains(minutes)) throw new IllegalArgumentException("Duração inválida");
        maxEventMs=minutes*60000L;
    }
    public boolean isCapturing() { return state.isRecording() || state.isSaving(); }
    public boolean isRunning() { return running; }
    public String completionReason() { return safetyNotice; }
    public String modeLabel() { return dualVideo&&dualConfig!=null?"Vídeo duplo • "+dualConfig.rearMode.height+"p / 30 fps":modeIndex<0||modeIndex>=modes.size()?"Modo indisponível":modes.get(modeIndex).label(); }
    private void status(String text) { listener.onStatus(text); }

    public void start() {
        if(running || closing || isCapturing() || !activityActive || safetyPaused || manualPaused || disposed) return;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED ||
                activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            error("Autorize câmera e microfone para começar"); return;
        }
        if(!preview.isAvailable()) {
            preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) { start(); }
                public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) { }
                public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { shutdown(); return true; }
                public void onSurfaceTextureUpdated(SurfaceTexture s) { }
            });
            state.preparing(); status("Preparando câmera…"); return;
        }
        try {
            String unsafe=unsafeReason();
            if(unsafe!=null) { safetyPaused=true; error(unsafe+" • histórico pausado"); return; }
            if(dualVideo){startDual();return;}
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            cameraId=CameraModes.cameraForFacing(manager,facing);
            CameraCharacteristics cc=manager.getCameraCharacteristics(cameraId);
            if(facing==CameraCharacteristics.LENS_FACING_BACK){
                primaryChoices=CameraTuning.from(cc,1280,720,30).choices();
                CameraLenses.Result lenses=CameraLenses.discover(activity);ultraWide=ultraRejected?null:lenses.ultraWide;
                if(primaryChoices.length>0&&primaryChoices[0]<1f)ultraWide=null;
                if(activeLens!=null){cameraId=activeLens.cameraId;cc=activeLens.characteristics;}
            }
            Integer angle=cc.get(CameraCharacteristics.SENSOR_ORIENTATION); if(angle!=null) orientation=angle;
            modes=activeLens!=null?activeLens.modes:CameraModes.detect(cc);
            if(modes.isEmpty()) throw new IllegalStateException("Nenhum modo compatível informado");
            modeIndex=CameraModes.chooseModeIndex(modes,requestedMode);
            preview.setScaleX(facing==CameraCharacteristics.LENS_FACING_FRONT?-1f:1f);
            running=true; state.preparing(); status("Preparando histórico contínuo…");
            prepareMode(++generation);
        } catch(Exception e) { fail("Não foi possível abrir a câmera: "+e.getMessage()); }
    }
    private boolean current(int token) { return token==generation && running && !closing && activityActive && !disposed; }
    private void startDual(){
        running=true;state.preparing();status("Verificando vídeo duplo para este aparelho…");final int token=++generation;
        final SurfaceTexture texture=preview.getSurfaceTexture();if(texture==null){fallbackDual("Prévia indisponível");return;}
        texture.setDefaultBufferSize(Math.max(1,preview.getWidth()),Math.max(1,preview.getHeight()));
        final Surface view=new Surface(texture);previewSurface=view;preview.setScaleX(1f);
        io.execute(()->{
            ContinuousRecorder prepared=null;DualFrameComposer graph=null;
            try{
                DualCameraSupport.Result support=DualCameraSupport.inspect(activity);
                if(!support.available)throw new IllegalStateException(support.message);
                final DualCaptureConfig config=support.config;
                prepared=new ContinuousRecorder(activity,config.outputMode,0,pastSeconds,new ContinuousRecorder.Listener(){
                    public void onPcm(byte[] pcm,int length){listener.onPcm(pcm,length);}
                    public void onReady(){main.post(()->{if(current(token)){state.ready();status("Vídeo duplo • "+config.rearMode.height+"p / 30 fps • histórico de até "+pastSeconds+" s");}});}
                    public void onError(String reason){main.post(()->{if(current(token))fallbackDual(reason);});}
                });
                graph=new DualFrameComposer(prepared.inputSurface(),view,config.outputMode.width,config.outputMode.height,config.rearMode,config.frontMode,config.rearOrientation,config.frontOrientation,config.rearRealtime,reason->main.post(()->{if(current(token))fallbackDual(reason);}));
                final ContinuousRecorder owner=prepared;final DualFrameComposer readyGraph=graph;
                final CaptureHandoff handoff=new CaptureHandoff();
                main.post(()->{
                    if(!current(token)||!handoff.claim()){handoff.reject();return;}
                    try{
                    recorder=owner;composer=readyGraph;dualConfig=config;modes=Collections.singletonList(config.outputMode);modeIndex=0;
                    dualSession=new DualCameraSession((CameraManager)activity.getSystemService(Context.CAMERA_SERVICE),config,readyGraph.rearSurface(),readyGraph.frontSurface(),main,new DualCameraSession.Listener(){
                        public void onConfigured(){if(!current(token)){if(dualSession!=null)dualSession.close();return;}owner.start();main.postDelayed(safetyTick,1000);}
                        public void onError(String reason){if(current(token))fallbackDual(reason);}
                    });dualSession.start();
                    }catch(Exception e){if(current(token))fallbackDual("Falha ao iniciar vídeo duplo: "+e.getMessage());}
                    finally{handoff.complete();}
                });
                if(!handoff.await(8000)){readyGraph.close();owner.discard();view.release();main.post(()->{if(current(token))fallbackDual("Preparação do vídeo duplo excedeu o tempo");});}
            }catch(Exception e){if(graph!=null)graph.close();if(prepared!=null)prepared.discard();view.release();main.post(()->{if(current(token))fallbackDual("Vídeo duplo indisponível: "+e.getMessage());});}
        });
    }
    private void fallbackDual(String reason){
        if(!dualVideo)return;
        dualVideo=false;activity.getSharedPreferences("local_settings",0).edit().putBoolean("dual_video",false).putInt("camera_facing",CameraCharacteristics.LENS_FACING_BACK).apply();
        if(state.isRecording()){fail(reason);return;}
        if(state.isSaving())return;
        stopBuffer();dualConfig=null;modes=Collections.emptyList();modeIndex=0;facing=CameraCharacteristics.LENS_FACING_BACK;state.stopped();
        listener.onError(reason+" • modo normal mantido");if(activityActive&&!disposed&&!manualPaused&&!safetyPaused)start();
    }
    private void prepareMode(final int token) {
        final CameraModes.Mode mode=modes.get(modeIndex);
        io.execute(()->{
            try {
                ContinuousRecorder prepared=new ContinuousRecorder(activity,mode,orientation,pastSeconds,new ContinuousRecorder.Listener() {
                    public void onPcm(byte[] pcm,int length) { listener.onPcm(pcm,length); }
                    public void onReady() {
                        main.post(()->{ if(current(token)) { if(rollbackFacing!=null){persistSelection();rollbackFacing=null;rollbackMode=null;rollbackLens=null;}state.ready(); status(lensLabel()+" • "+mode.label()+" • histórico de até "+pastSeconds+" s"); } });
                    }
                    public void onError(String message) { main.post(()->{ if(current(token)) fail(message); }); }
                });
                CaptureHandoff handoff=new CaptureHandoff();
                main.post(()->{
                    if(!current(token)||!handoff.claim()){handoff.reject();return;}
                    try{recorder=prepared;openCamera(token,mode,prepared);}
                    finally{handoff.complete();}
                });
                if(!handoff.await(8000)){prepared.discard();main.post(()->{if(current(token))retryMode("Preparação da câmera excedeu o tempo");});}
            } catch(Exception e) { main.post(()->{ if(current(token)) retryMode("Modo indisponível: "+e.getMessage()); }); }
        });
    }
    @SuppressLint("MissingPermission")
    private void openCamera(final int token,CameraModes.Mode mode,ContinuousRecorder owner) {
        try {
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            manager.openCamera(cameraId,new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) {
                    if(!current(token)) { device.close(); return; }
                    camera=device;
                    try {
                        SurfaceTexture texture=preview.getSurfaceTexture();
                        if(texture==null) throw new IllegalStateException("Prévia indisponível");
                        texture.setDefaultBufferSize(mode.width,mode.height);
                        final Surface view=new Surface(texture); previewSurface=view;
                        CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        request.addTarget(view); request.addTarget(owner.inputSurface());
                        request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                        request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,mode.aeRange);
                        CameraCharacteristics source=activeLens==null?manager.getCameraCharacteristics(cameraId):activeLens.characteristics;
                        tuning=activeLens!=null&&activeLens.physicalId!=null?CameraTuning.forPhysical(source,mode.width,mode.height,mode.fps):CameraTuning.from(source,mode.width,mode.height,mode.fps);
                        zoom=tuning.apply(request,zoom);repeatingRequest=request;
                        createSession(device,Arrays.asList(view,owner.inputSurface()),new CameraCaptureSession.StateCallback() {
                            public void onConfigured(CameraCaptureSession configured) {
                                if(!current(token)) { configured.close(); return; }
                                session=configured;
                                try {
                                    submitRepeating();
                                    owner.start(); main.postDelayed(safetyTick,1000);
                                } catch(Exception e) { retryMode("Não foi possível iniciar captura: "+e.getMessage()); }
                            }
                            public void onConfigureFailed(CameraCaptureSession configured) {
                                configured.close(); if(current(token)) retryMode("Modo rejeitado pela câmera");
                            }
                        });
                    } catch(Exception e) { retryMode("Falha ao preparar câmera: "+e.getMessage()); }
                }
                public void onDisconnected(CameraDevice device) { device.close(); if(current(token)) fail("Câmera desconectada"); }
                public void onError(CameraDevice device,int code) { device.close(); if(current(token)) fail("Erro na câmera ("+code+")"); }
            },main);
        } catch(Exception e) { retryMode("Falha ao abrir câmera: "+e.getMessage()); }
    }
    private void createSession(CameraDevice device,List<Surface> surfaces,CameraCaptureSession.StateCallback callback)throws Exception{
        if(activeLens==null||activeLens.physicalId==null){device.createCaptureSession(surfaces,callback,main);return;}
        android.hardware.camera2.params.SessionConfiguration config=PhysicalLensSession.configuration(surfaces,activeLens.physicalId,command->main.post(command),callback);
        try{if(!device.isSessionConfigurationSupported(config))throw new IllegalArgumentException("Formato não disponível nesta lente física");}
        catch(UnsupportedOperationException ignored){/* The platform permits trying a session when the query is unavailable. */}
        device.createCaptureSession(config);
    }
    private void retryMode(String reason) {
        if(dualVideo){fallbackDual(reason);return;}
        if(state.isRecording()) { fail(reason); return; }
        closeCamera(); main.removeCallbacks(safetyTick);
        ContinuousRecorder old=recorder; recorder=null; if(old!=null) io.execute(old::discard);
        if(++modeIndex<modes.size() && activityActive && !disposed) {
            state.preparing(); status(reason+" • tentando "+modeLabel()); prepareMode(++generation);
        } else fail(reason);
    }
    public void markMoment() {
        if(!running || closing || !state.canMark() || recorder==null) return;
        if(!recorder.mark(pastSeconds)) { status("Aguarde o histórico ficar pronto"); return; }
        if(!state.mark(SystemClock.elapsedRealtime())) return;
        safetyNotice=""; status("REC • momento marcado • diga sua frase para encerrar");
    }
    public void finishAndSave() {
        if(!state.beginSaving()) return;
        closing=true; generation++; main.removeCallbacks(safetyTick);
        status("Salvando vídeo…"+(safetyNotice.isEmpty()?"":" • "+safetyNotice));
        closeCamera(); final ContinuousRecorder owner=recorder; recorder=null;
        final DualFrameComposer oldGraph=composer;composer=null;final Surface oldView=oldGraph==null?null:previewSurface;if(oldGraph!=null)previewSurface=null;
        io.execute(()->{
            File output=null;
            try {
                if(oldGraph!=null)oldGraph.close();if(oldView!=null)oldView.release();
                if(owner==null) throw new IllegalStateException("Captura indisponível");
                output=File.createTempFile("lance_",".mp4",activity.getCacheDir());
                owner.finish(output); Uri uri=VideoStore.save(activity,output);
                main.post(()->{ closing=false; running=false; state.saved(); listener.onSaved(uri); if(activityActive && !disposed && !safetyPaused && !manualPaused) start(); });
            } catch(Exception e) { main.post(()->{ closing=false; running=false; error("Falha ao salvar: "+e.getMessage()); }); }
            finally { if(owner!=null) owner.discard(); if(output!=null) output.delete(); }
        });
    }
    private final Runnable safetyTick=new Runnable() {
        public void run() {
            if(!running || closing || !activityActive) return;
            String reason=unsafeReason();
            if(reason!=null) {
                safetyNotice=reason; safetyPaused=true;
                if(state.isRecording()) finishAndSave(); else { stopBuffer(); error(reason+" • histórico pausado"); }
                return;
            }
            if(state.isRecording() && CaptureSafety.durationReached(maxEventMs,state.elapsed(SystemClock.elapsedRealtime()))) {
                safetyNotice="Duração escolhida atingida"; finishAndSave(); return;
            }
            main.postDelayed(this,1000);
        }
    };
    private String unsafeReason() {
        long bytes=recorder==null?0:recorder.bytesOnDisk();
        long futureBytes=modeIndex<0||modeIndex>=modes.size()?4_000_000:(modes.get(modeIndex).bitrate()/8L+12000)*4;
        long free=Math.min(activity.getCacheDir().getUsableSpace(),Environment.getExternalStorageDirectory().getUsableSpace());
        if(CaptureSafety.lowSpace(free,bytes,futureBytes)) return "Pouco espaço livre para salvar o lance";
        Intent battery=activity.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            if(CaptureSafety.lowBattery(level>=0 && scale>0?level*100/scale:-1,battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)) return "Bateria em 5% ou menos";
        }
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
        if(power!=null && power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE) return "Aparelho muito quente";
        return null;
    }
    private void closeCamera() {
        repeatingRequest=null;tuning=null;
        if(dualSession!=null){dualSession.close();dualSession=null;}
        if(session!=null) { try { session.stopRepeating(); } catch(Exception ignored) { } session.close(); session=null; }
        if(camera!=null) { camera.close(); camera=null; }
        if(previewSurface!=null&&composer==null) { previewSurface.release(); previewSurface=null; }
    }
    private void stopBuffer() {
        generation++; running=false; main.removeCallbacks(safetyTick); closeCamera();
        ContinuousRecorder old=recorder; recorder=null;DualFrameComposer oldGraph=composer;composer=null;Surface oldView=oldGraph==null?null:previewSurface;if(oldGraph!=null)previewSurface=null;
        if(old!=null||oldGraph!=null)disposeLater(old,oldGraph,oldView);
    }
    private void disposeLater(ContinuousRecorder old,DualFrameComposer graph,Surface view){
        Runnable cleanup=()->{if(graph!=null)graph.close();if(view!=null)view.release();if(old!=null)old.discard();};
        if(!io.isShutdown())io.execute(cleanup);else{Thread t=new Thread(cleanup,"dual-capture-cleanup");t.setDaemon(true);t.start();}
    }
    private void discardLater(ContinuousRecorder old) {
        if(!io.isShutdown()) { io.execute(old::discard); return; }
        Thread cleanup=new Thread(old::discard,"capture-cleanup"); cleanup.setDaemon(true); cleanup.start();
    }
    private void error(String message) { state.error(); listener.onError(message); }
    private void fail(String message) {
        if(state.isRecording()) { safetyPaused=true; safetyNotice=message; finishAndSave(); return; }
        if(restorePreviousCamera(message,true))return;
        if(activeLens!=null){stopBuffer();activeLens=null;ultraWide=null;ultraRejected=true;state.stopped();listener.onError(message+" • voltando à câmera principal");if(activityActive&&!manualPaused&&!disposed)start();return;}
        stopBuffer(); error(message);
    }
    private boolean restorePreviousCamera(String reason){return restorePreviousCamera(reason,false);}
    private boolean restorePreviousCamera(String reason,boolean rejected){
        if(rollbackFacing==null)return false;
        boolean rejectedUltra=activeLens!=null&&activeLens!=rollbackLens;
        stopBuffer();facing=rollbackFacing;requestedMode=rollbackMode;activeLens=rollbackLens;zoom=rollbackZoom;rollbackFacing=null;rollbackMode=null;rollbackLens=null;modes=Collections.emptyList();modeIndex=0;
        if(rejectedUltra&&rejected){ultraRejected=true;ultraWide=null;}
        persistSelection();preview.setScaleX(facing==CameraCharacteristics.LENS_FACING_FRONT?-1f:1f);state.stopped();status(reason+" • voltando à câmera "+lensLabel().toLowerCase(Locale.ROOT));
        if(activityActive&&!manualPaused&&!disposed)start();return true;
    }
    public void pauseHistory() {
        if(isCapturing()) return;
        manualPaused=true; stopBuffer(); state.stopped(); status("Histórico pausado • câmera e microfone desligados");
    }
    public void resumeCapture() { manualPaused=false; safetyPaused=false; activityActive=true; start(); }
    public void shutdown() {
        activityActive=false;
        if(restorePreviousCamera("Troca interrompida"))return;
        if(state.isRecording()) { finishAndSave(); return; }
        if(state.isSaving()) return;
        stopBuffer(); state.stopped();
    }
    public boolean isManuallyPaused() { return manualPaused; }
    public void resumeActivity() { activityActive=true; }
    public void release() { disposed=true; io.shutdown(); }
}
