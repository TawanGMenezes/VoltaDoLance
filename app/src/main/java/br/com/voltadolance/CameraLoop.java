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
    public void setFacing(int value) {if(!running&&!isCapturing())facing=value==CameraCharacteristics.LENS_FACING_FRONT?value:CameraCharacteristics.LENS_FACING_BACK;}
    public int facing() {return facing;}
    public String lensLabel(){return facing==CameraCharacteristics.LENS_FACING_FRONT?"Frontal":"Traseira";}
    public String selectedMode(){return modeIndex<0||modeIndex>=modes.size()?requestedMode:modes.get(modeIndex).key();}
    private void persistSelection(){activity.getSharedPreferences("local_settings",0).edit().putInt("camera_facing",facing).putString("video_mode",selectedMode()).apply();}
    public boolean switchCamera() {
        if(isCapturing()||closing||disposed||rollbackFacing!=null)return false;
        try{
            int destination=facing==CameraCharacteristics.LENS_FACING_BACK?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            String target=CameraModes.cameraForFacing(manager,destination);
            List<CameraModes.Mode> supported=CameraModes.detect(manager.getCameraCharacteristics(target));
            int selected=CameraModes.chooseModeIndex(supported,selectedMode());
            if(selected<0)throw new IllegalStateException("Nenhum modo de vídeo compatível nesta câmera");
            int previous=facing;String previousMode=selectedMode();
            stopBuffer();facing=destination;requestedMode=supported.get(selected).key();modes=supported;modeIndex=selected;
            preview.setScaleX(facing==CameraCharacteristics.LENS_FACING_FRONT?-1f:1f);
            if(manualPaused){persistSelection();state.stopped();status(lensLabel()+" selecionada • "+modeLabel()+" • histórico pausado");}
            else{rollbackFacing=previous;rollbackMode=previousMode;start();}
            return true;
        }catch(Exception e){listener.onError("Não foi possível trocar: "+e.getMessage());return false;}
    }
    public void setPostMinutes(int minutes) {
        if(!Arrays.asList(0,3,10,20,30,60).contains(minutes)) throw new IllegalArgumentException("Duração inválida");
        maxEventMs=minutes*60000L;
    }
    public boolean isCapturing() { return state.isRecording() || state.isSaving(); }
    public boolean isRunning() { return running; }
    public String completionReason() { return safetyNotice; }
    public String modeLabel() { return modeIndex<0||modeIndex>=modes.size()?"Modo indisponível":modes.get(modeIndex).label(); }
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
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            cameraId=CameraModes.cameraForFacing(manager,facing);
            CameraCharacteristics cc=manager.getCameraCharacteristics(cameraId);
            Integer angle=cc.get(CameraCharacteristics.SENSOR_ORIENTATION); if(angle!=null) orientation=angle;
            modes=CameraModes.detect(cc);
            if(modes.isEmpty()) throw new IllegalStateException("Nenhum modo compatível informado");
            modeIndex=CameraModes.chooseModeIndex(modes,requestedMode);
            preview.setScaleX(facing==CameraCharacteristics.LENS_FACING_FRONT?-1f:1f);
            running=true; state.preparing(); status("Preparando histórico contínuo…");
            prepareMode(++generation);
        } catch(Exception e) { fail("Não foi possível abrir a câmera: "+e.getMessage()); }
    }
    private boolean current(int token) { return token==generation && running && !closing && activityActive && !disposed; }
    private void prepareMode(final int token) {
        final CameraModes.Mode mode=modes.get(modeIndex);
        io.execute(()->{
            try {
                ContinuousRecorder prepared=new ContinuousRecorder(activity,mode,orientation,pastSeconds,new ContinuousRecorder.Listener() {
                    public void onPcm(byte[] pcm,int length) { listener.onPcm(pcm,length); }
                    public void onReady() {
                        main.post(()->{ if(current(token)) { if(rollbackFacing!=null){persistSelection();rollbackFacing=null;rollbackMode=null;}state.ready(); status(lensLabel()+" • "+mode.label()+" • histórico de até "+pastSeconds+" s"); } });
                    }
                    public void onError(String message) { main.post(()->{ if(current(token)) fail(message); }); }
                });
                main.post(()->{
                    if(!current(token)) { discardLater(prepared); return; }
                    recorder=prepared; openCamera(token,mode,prepared);
                });
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
                        device.createCaptureSession(Arrays.asList(view,owner.inputSurface()),new CameraCaptureSession.StateCallback() {
                            public void onConfigured(CameraCaptureSession configured) {
                                if(!current(token)) { configured.close(); return; }
                                session=configured;
                                try {
                                    configured.setRepeatingRequest(request.build(),null,main);
                                    owner.start(); main.postDelayed(safetyTick,1000);
                                } catch(Exception e) { retryMode("Não foi possível iniciar captura: "+e.getMessage()); }
                            }
                            public void onConfigureFailed(CameraCaptureSession configured) {
                                configured.close(); if(current(token)) retryMode("Modo rejeitado pela câmera");
                            }
                        },main);
                    } catch(Exception e) { retryMode("Falha ao preparar câmera: "+e.getMessage()); }
                }
                public void onDisconnected(CameraDevice device) { device.close(); if(current(token)) fail("Câmera desconectada"); }
                public void onError(CameraDevice device,int code) { device.close(); if(current(token)) fail("Erro na câmera ("+code+")"); }
            },main);
        } catch(Exception e) { retryMode("Falha ao abrir câmera: "+e.getMessage()); }
    }
    private void retryMode(String reason) {
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
        io.execute(()->{
            File output=null;
            try {
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
        if(session!=null) { try { session.stopRepeating(); } catch(Exception ignored) { } session.close(); session=null; }
        if(camera!=null) { camera.close(); camera=null; }
        if(previewSurface!=null) { previewSurface.release(); previewSurface=null; }
    }
    private void stopBuffer() {
        generation++; running=false; main.removeCallbacks(safetyTick); closeCamera();
        ContinuousRecorder old=recorder; recorder=null;
        if(old!=null) discardLater(old);
    }
    private void discardLater(ContinuousRecorder old) {
        if(!io.isShutdown()) { io.execute(old::discard); return; }
        Thread cleanup=new Thread(old::discard,"capture-cleanup"); cleanup.setDaemon(true); cleanup.start();
    }
    private void error(String message) { state.error(); listener.onError(message); }
    private void fail(String message) {
        if(state.isRecording()) { safetyPaused=true; safetyNotice=message; finishAndSave(); return; }
        if(restorePreviousCamera(message))return;
        stopBuffer(); error(message);
    }
    private boolean restorePreviousCamera(String reason){
        if(rollbackFacing==null)return false;
        stopBuffer();facing=rollbackFacing;requestedMode=rollbackMode;rollbackFacing=null;rollbackMode=null;modes=Collections.emptyList();modeIndex=0;
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
