package br.com.voltadolance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ALFA: grava em fragmentos MP4 de ~4s. Pode haver pequenas lacunas entre fragmentos;
 * não é um buffer H264 sem interrupções. Usar somente para testes internos.
 */
public final class CameraLoop {
    public interface Listener {
        void onStatus(String message);
        void onSaved(Uri uri);
        void onError(String error);
    }

    private static final long SEGMENT_MS = 4_000;
    // Evita gravação acidental sem limite e esgotamento do armazenamento.
    // Nesta alfa: 3 minutos. A propriedade fica isolada para permitir 
    // presets configuráveis em uma versão posterior, após testes de armazenamento.
    private long maxEventMs = 180_000;
    private final Activity activity;
    private final TextureView preview;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final RollingWindow<File> rolling = new RollingWindow<>();
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;
    private File currentFile;
    private long startedAtMs;
    private long captureAtMs;
    private int pastSeconds = 15;
    private int height = 720;
    private String cameraId;
    private int cameraOrientation = 90;
    private boolean running;
    private boolean recording;
    private boolean saving;
    private boolean closing;
    private boolean activityActive = true;

    public CameraLoop(Activity activity, TextureView preview, Listener listener) {
        this.activity = activity;
        this.preview = preview;
        this.listener = listener;
    }
    public void setPastSeconds(int seconds) { pastSeconds = seconds; }
    public void setHeight(int height) { this.height = height; }
    /** Para versões futuras: valor limitado a 3 minutos no teste público atual. */
    void setTestMaxEventSeconds(int seconds) {
        if (seconds != 180) throw new IllegalArgumentException("Alfa limitada a 180 segundos");
        maxEventMs = seconds * 1_000L;
    }
    public boolean isCapturing() { return saving; }
    public boolean isRunning() { return running; }
    private void status(String msg) { listener.onStatus(msg); }

    @SuppressLint("MissingPermission")
    public void start() {
        if (running || closing || !activityActive) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Autorize câmera e microfone para começar"); return;
        }
        if (!preview.isAvailable()) {
            preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { start(); }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { shutdown(); return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
            });
            status("Preparando câmera…");
            return;
        }
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics cc = manager.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    Integer angle = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (angle != null) cameraOrientation = angle;
                    break;
                }
            }
            if (cameraId == null) throw new IllegalStateException("Câmera traseira não encontrada");
            closing = false;
            running = true;
            status("Inicializando a captura…");
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (!running || closing) { device.close(); return; }
                    camera = device;
                    newSegment();
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close(); camera = null; running = false;
                    listener.onError("Câmera desconectada");
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close(); camera = null; running = false;
                    listener.onError("Falha no acesso à câmera (" + error + ")");
                }
            }, main);
        } catch (Exception e) {
            running = false;
            listener.onError("Não foi possível abrir a câmera: " + e.getMessage());
        }
    }

    private void newSegment() {
        if (!running || closing || camera == null || !activityActive) return;
        try {
            File rollingDir = new File(activity.getCacheDir(), "rolling");
            if (!rollingDir.isDirectory() && !rollingDir.mkdirs())
                throw new IllegalStateException("Não foi possível preparar o espaço temporário");
            currentFile = File.createTempFile("chunk_", ".mp4", rollingDir);
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            int width = height == 1080 ? 1920 : 1280;
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(height == 1080 ? 7_000_000 : 4_000_000);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioChannels(1);
            recorder.setOrientationHint(cameraOrientation);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null) throw new IllegalStateException("Prévia indisponível");
            texture.setDefaultBufferSize(width, height);
            Surface viewSurface = new Surface(texture);
            Surface recordSurface = recorder.getSurface();
            CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            request.addTarget(viewSurface);
            request.addTarget(recordSurface);
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            camera.createCaptureSession(Arrays.asList(viewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (!running || closing) { configured.close(); viewSurface.release(); return; }
                    session = configured;
                    try {
                        configured.setRepeatingRequest(request.build(), null, main);
                        recorder.start();
                        recording = true;
                        startedAtMs = SystemClock.elapsedRealtime();
                        status(saving ? "Gravando o momento…" : "Pronto • memória recente ativa");
                        main.postDelayed(segmentTick, SEGMENT_MS);
                    } catch (Exception e) { fail("Não foi possível iniciar o vídeo", e); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession configured) {
                    viewSurface.release();
                    fail("Configuração de vídeo rejeitada pelo aparelho", null);
                }
                @Override public void onClosed(CameraCaptureSession closed) { viewSurface.release(); }
            }, main);
        } catch (Exception e) { fail("Falha ao configurar a gravação", e); }
    }

    private final Runnable segmentTick = new Runnable() {
        @Override public void run() {
            if (!running || closing) return;
            finishSegment();
            if (running && !closing) {
                if (saving && SystemClock.elapsedRealtime() - captureAtMs >= maxEventMs) {
                    status("Limite de 3 minutos atingido • salvando momento");
                    finishAndSave();
                } else newSegment();
            }
        }
    };

    private void finishSegment() {
        main.removeCallbacks(segmentTick);
        if (session != null) { try { session.stopRepeating(); } catch (Exception ignored) { }
            session.close(); session = null; }
        boolean valid = recording;
        long end = SystemClock.elapsedRealtime();
        if (recorder != null) {
            try { if (recording) recorder.stop(); }
            catch (RuntimeException e) { valid = false; }
            try { recorder.reset(); } catch (Exception ignored) { }
            recorder.release(); recorder = null;
        }
        recording = false;
        if (currentFile != null) {
            if (valid && currentFile.length() > 1024) {
                rolling.add(currentFile, startedAtMs, end);
                if (!saving) {
                    long cutoff = end - pastSeconds * 1000L - SEGMENT_MS * 2;
                    for (File old : rolling.purgeOlderThan(cutoff)) old.delete();
                }
            } else currentFile.delete();
        }
        currentFile = null;
    }

    public void markMoment() {
        if (!running || closing || saving || !recording) {
            if (!running) start();
            else status("Aguarde a câmera ficar pronta");
            return;
        }
        captureAtMs = SystemClock.elapsedRealtime();
        saving = true;
        status("Momento marcado • diga ‘Encerrar vídeo’ ou toque em finalizar");
    }

    public void finishAndSave() {
        if (!saving || !running) return;
        status("Preparando vídeo para a galeria…");
        closing = true;
        finishSegment();
        closeCamera();
        List<File> selected = new ArrayList<>();
        long cutoff = captureAtMs - pastSeconds * 1000L;
        for (RollingWindow.Entry<File> item : rolling.from(cutoff)) selected.add(item.value);
        // Esta alpha usa os limites de fragmentos; a duração anterior pode variar em até ~4 s.
        List<File> all = rolling.clear();
        saving = false;
        // Protege a exportação contra a limpeza da câmera quando o usuário abre
        // a galeria, bloqueia a tela ou alterna de aplicativo durante o salvamento.
        File exportDir = new File(activity.getCacheDir(), "exports");
        List<File> stable = new ArrayList<>();
        try {
            if (!exportDir.isDirectory() && !exportDir.mkdirs())
                throw new IllegalStateException("Não foi possível preparar a exportação");
            for (File file : selected) {
                File target = File.createTempFile("pending_", ".mp4", exportDir);
                Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                stable.add(target);
            }
        } catch (Exception e) {
            for (File file : stable) file.delete();
            for (File file : all) file.delete();
            listener.onError("Não foi possível proteger os vídeos temporários: " + e.getMessage());
            return;
        }
        for (File file : all) file.delete();
        io.execute(() -> {
            File merged = null;
            try {
                if (stable.isEmpty()) throw new IllegalStateException("Nenhum segmento íntegro");
                merged = File.createTempFile("moment_", ".mp4", activity.getCacheDir());
                Mp4Joiner.join(stable, merged);
                Uri uri = VideoStore.save(activity, merged);
                main.post(() -> listener.onSaved(uri));
            } catch (Exception e) {
                main.post(() -> listener.onError("Falha ao salvar: " + e.getMessage()));
            } finally {
                for (File file : stable) file.delete();
                if (merged != null) merged.delete();
            }
        });
    }

    private void closeCamera() {
        running = false;
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        closing = false;
    }
    private void fail(String prefix, Exception e) {
        listener.onError(prefix + (e == null ? "" : ": " + e.getMessage()));
        shutdown();
    }
    public void shutdown() {
        activityActive = false;
        closing = true;
        main.removeCallbacks(segmentTick);
        if (recorder != null || recording) finishSegment();
        closeCamera();
        for (File old : rolling.clear()) old.delete();
        saving = false;
        File folder = new File(activity.getCacheDir(), "rolling");
        File[] orphans = folder.listFiles();
        if (orphans != null) for (File f : orphans) f.delete();
    }
    public void resumeActivity() { activityActive = true; }
}
