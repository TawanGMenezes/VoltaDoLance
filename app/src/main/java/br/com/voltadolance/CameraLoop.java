package br.com.voltadolance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.view.Surface;
import android.view.TextureView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private ~4s fragments preserve the approximate pre-record window. */
public final class CameraLoop {
    public interface Listener {
        void onStatus(String message);
        void onSaved(Uri uri);
        void onError(String error);
    }
    private static final long SEGMENT_MS=4000;
    private final Activity activity;
    private final TextureView preview;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final RollingWindow<File> rolling=new RollingWindow<>();
    private final CaptureState state=new CaptureState();
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;
    private File currentFile;
    private long startedAtMs,captureAtMs;
    private int pastSeconds=15,cameraOrientation=90;
    private long maxEventMs=600000;
    private boolean running,recording,closing,activityActive=true,safetyPaused;
    private int generation,segmentGeneration;
    private String requestedMode="1280x720@30";
    private List<CameraModes.Mode> modes=Collections.emptyList();
    private int modeIndex;
    private String safetyNotice="";

    public CameraLoop(Activity activity,TextureView preview,Listener listener) {
        this.activity=activity; this.preview=preview; this.listener=listener;
    }
    public CaptureState state() { return state; }
    public void setPastSeconds(int seconds) { pastSeconds=seconds; }
    public void setMode(String key) { requestedMode=key; }
    public void setPostMinutes(int minutes) {
        if(!Arrays.asList(0,3,10,20,30,60).contains(minutes)) throw new IllegalArgumentException("Duração inválida");
        maxEventMs=minutes*60_000L;
    }
    public boolean isCapturing() { return state.isRecording() || state.isSaving(); }
    public boolean isRunning() { return running; }
    public String modeLabel() { return modes.isEmpty()?"Detectando câmera":modes.get(modeIndex).label(); }
    private void status(String text) { listener.onStatus(text); }

    @SuppressLint("MissingPermission")
    public void start() {
        if(running || closing || state.isSaving() || !activityActive || safetyPaused) return;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
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
            if(unsafe!=null) { safetyPaused=true; error(unsafe+" • captura pausada"); return; }
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            String id=CameraModes.backCamera(manager);
            CameraCharacteristics cc=manager.getCameraCharacteristics(id);
            Integer orientation=cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if(orientation!=null) cameraOrientation=orientation;
            modes=CameraModes.detect(cc);
            if(modes.isEmpty()) throw new IllegalStateException("Nenhum modo de vídeo compatível informado");
            modeIndex=modes.size()-1;
            for(int i=0;i<modes.size();i++) if(modes.get(i).key().equals(requestedMode)) modeIndex=i;
            running=true; closing=false; state.preparing();
            final int token=++generation;
            status("Inicializando a captura…");
            manager.openCamera(id,new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) {
                    if(token!=generation || !running || !activityActive) { device.close(); return; }
                    camera=device; newSegment();
                }
                public void onDisconnected(CameraDevice device) {
                    device.close(); if(token==generation) fail("Câmera desconectada",null);
                }
                public void onError(CameraDevice device,int code) {
                    device.close(); if(token==generation) fail("Falha na câmera ("+code+")",null);
                }
            },main);
        } catch(Exception e) { fail("Não foi possível abrir a câmera",e); }
    }
    private void newSegment() {
        if(!running || closing || camera==null || !activityActive) return;
        state.preparing();
        final int token=++segmentGeneration;
        final CameraModes.Mode mode=modes.get(modeIndex);
        try {
            File dir=new File(activity.getCacheDir(),"rolling");
            if(!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Sem espaço temporário");
            currentFile=File.createTempFile("chunk_",".mp4",dir);
            final MediaRecorder segmentRecorder=new MediaRecorder(); recorder=segmentRecorder;
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoSize(mode.width,mode.height); recorder.setVideoFrameRate(mode.fps);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(mode.bitrate()); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100); recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioChannels(1); recorder.setOrientationHint(cameraOrientation);
            recorder.setOutputFile(currentFile.getAbsolutePath()); recorder.prepare();
            SurfaceTexture texture=preview.getSurfaceTexture();
            if(texture==null) throw new IllegalStateException("Prévia indisponível");
            texture.setDefaultBufferSize(mode.width,mode.height);
            final Surface viewSurface=new Surface(texture);
            Surface recordSurface=recorder.getSurface();
            CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            request.addTarget(viewSurface); request.addTarget(recordSurface);
            request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,mode.aeRange);
            camera.createCaptureSession(Arrays.asList(viewSurface,recordSurface),new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession configured) {
                    if(token!=segmentGeneration || !running || closing || !activityActive) {
                        configured.close(); viewSurface.release(); return;
                    }
                    session=configured;
                    try {
                        configured.setRepeatingRequest(request.build(),null,main);
                        segmentRecorder.start(); recording=true; startedAtMs=SystemClock.elapsedRealtime();
                        state.ready(); status(state.isRecording()?"Gravando o momento…":
                                "Pronto • memória recente ativa • "+mode.label());
                        main.postDelayed(segmentTick,SEGMENT_MS);
                        main.removeCallbacks(safetyTick); main.postDelayed(safetyTick,1000);
                    } catch(Exception e) { modeFailed("Não foi possível iniciar o vídeo",e); }
                }
                public void onConfigureFailed(CameraCaptureSession configured) {
                    configured.close(); viewSurface.release();
                    if(token==segmentGeneration && !closing) modeFailed("Modo rejeitado pelo aparelho",null);
                }
                public void onClosed(CameraCaptureSession closed) { viewSurface.release(); }
            },main);
        } catch(Exception e) { modeFailed("Falha ao configurar vídeo",e); }
    }
    private void modeFailed(String reason,Exception error) {
        if(state.isRecording()) { safetyNotice=reason; safetyPaused=true; finishAndSave(); return; }
        finishSegment();
        for(File file:rolling.clear()) file.delete();
        if(modeIndex+1<modes.size() && running && activityActive) {
            modeIndex++; status(reason+" • tentando "+modeLabel()); newSegment();
        } else fail(reason,error);
    }
    private final Runnable segmentTick=() -> {
        if(!running || closing) return;
        finishSegment();
        if(running && !closing) newSegment();
    };
    private final Runnable safetyTick=new Runnable() {
        public void run() {
            if(!running || closing) return;
            String reason=unsafeReason();
            if(reason!=null) {
                safetyNotice=reason; safetyPaused=true;
                if(state.isRecording()) finishAndSave();
                else { stopBuffer(); error(reason+" • captura pausada"); }
                return;
            }
            if(state.isRecording() && CaptureSafety.durationReached(maxEventMs,state.elapsed(SystemClock.elapsedRealtime()))) {
                safetyNotice="Duração escolhida atingida"; finishAndSave(); return;
            }
            main.postDelayed(this,1000);
        }
    };
    private String unsafeReason() {
        long bytes=0;
        for(RollingWindow.Entry<File> entry:rolling.from(Long.MIN_VALUE)) bytes+=entry.value.length();
        if(currentFile!=null) bytes+=currentFile.length();
        long segmentBytes=modes.isEmpty()?4_000_000:(modes.get(modeIndex).bitrate()/8L+12000)*4;
        long free=Math.min(activity.getCacheDir().getUsableSpace(),Environment.getExternalStorageDirectory().getUsableSpace());
        if(CaptureSafety.lowSpace(free,bytes,segmentBytes)) return "Pouco espaço para salvar com segurança";
        Intent battery=activity.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            boolean charging=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
            if(CaptureSafety.lowBattery(scale>0 && level>=0?level*100/scale:-1,charging)) return "Bateria em 5% ou menos";
        }
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
        if(power!=null && power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE) return "Aparelho muito quente";
        return null;
    }
    private void finishSegment() {
        main.removeCallbacks(segmentTick); ++segmentGeneration;
        if(session!=null) {
            try { session.stopRepeating(); } catch(Exception ignored) { }
            session.close(); session=null;
        }
        boolean valid=recording; long end=SystemClock.elapsedRealtime();
        if(recorder!=null) {
            try { if(recording) recorder.stop(); } catch(RuntimeException e) { valid=false; }
            try { recorder.reset(); } catch(Exception ignored) { }
            recorder.release(); recorder=null;
        }
        recording=false;
        if(currentFile!=null) {
            if(valid && currentFile.length()>1024) {
                rolling.add(currentFile,startedAtMs,end);
                if(!isCapturing()) for(File old:rolling.purgeOlderThan(end-pastSeconds*1000L-SEGMENT_MS*2)) old.delete();
            } else currentFile.delete();
        }
        currentFile=null;
    }
    public void markMoment() {
        if(!running || closing || !recording || !state.mark(SystemClock.elapsedRealtime())) return;
        captureAtMs=SystemClock.elapsedRealtime(); safetyNotice="";
        status("Momento marcado • toque em encerrar ou use sua frase de voz");
    }
    public void finishAndSave() {
        if(!state.beginSaving()) return;
        closing=true; main.removeCallbacks(safetyTick); main.removeCallbacks(segmentTick);
        status("Salvando vídeo…"+(safetyNotice.isEmpty()?"":" • "+safetyNotice));
        long wait=recording?Math.max(0,800-(SystemClock.elapsedRealtime()-startedAtMs)):0;
        main.postDelayed(this::exportMoment,wait);
    }
    private void exportMoment() {
        finishSegment(); closeCamera();
        List<File> selected=new ArrayList<>();
        for(RollingWindow.Entry<File> item:rolling.from(captureAtMs-pastSeconds*1000L)) selected.add(item.value);
        List<File> all=rolling.clear();
        File dir=new File(activity.getCacheDir(),"exports"); List<File> stable=new ArrayList<>();
        try {
            if(!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Sem espaço para exportar");
            for(File file:selected) {
                File target=File.createTempFile("pending_",".mp4",dir);
                Files.move(file.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); stable.add(target);
            }
        } catch(Exception e) {
            for(File file:stable) file.delete(); for(File file:all) file.delete();
            error("Falha ao proteger vídeo: "+e.getMessage()); return;
        }
        for(File file:all) file.delete();
        final int orientation=cameraOrientation;
        io.execute(() -> {
            File merged=null;
            try {
                if(stable.isEmpty()) throw new IllegalStateException("Nenhum segmento íntegro; aguarde o histórico ficar pronto");
                merged=File.createTempFile("moment_",".mp4",activity.getCacheDir());
                Mp4Joiner.join(stable,merged,orientation);
                Uri uri=VideoStore.save(activity,merged);
                main.post(() -> { state.saved(); listener.onSaved(uri); if(activityActive && !safetyPaused) start(); });
            } catch(Exception e) { main.post(() -> error("Falha ao salvar: "+e.getMessage())); }
            finally { for(File file:stable) file.delete(); if(merged!=null) merged.delete(); }
        });
    }
    private void closeCamera() {
        running=false; generation++;
        if(session!=null) { session.close(); session=null; }
        if(camera!=null) { camera.close(); camera=null; }
        closing=false;
    }
    private void error(String message) { state.error(); listener.onError(message); }
    private void fail(String message,Exception e) {
        if(state.isRecording()) { safetyPaused=true; safetyNotice=message; finishAndSave(); return; }
        stopBuffer(); error(message+(e==null?"":": "+e.getMessage()));
    }
    private void stopBuffer() {
        closing=true; main.removeCallbacks(segmentTick); main.removeCallbacks(safetyTick);
        finishSegment(); closeCamera();
        for(File file:rolling.clear()) file.delete();
    }
    public void shutdown() {
        activityActive=false;
        if(state.isRecording()) { finishAndSave(); return; }
        if(state.isSaving()) return;
        stopBuffer(); state.stopped();
    }
    public void resumeActivity() { activityActive=true; safetyPaused=false; }
}
