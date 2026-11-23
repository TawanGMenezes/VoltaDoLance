package br.com.voltadolance;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.util.Range;
import java.util.ArrayList;

/** Per-camera zoom and mutually exclusive stabilization controls. */
public final class CameraTuning {
    public enum Stabilization { NONE, VIDEO, OPTICAL }
    private final float minimum, maximum;
    private final boolean zoomRatio, opticalSupported;
    private final Rect activeArray;
    private Stabilization stabilization;
    private volatile String summary = "Estabilização: verificando";

    public static CameraTuning from(CameraCharacteristics camera, int width, int height, int fps) {
        Range<Float> ratio = Build.VERSION.SDK_INT >= 30
                ? camera.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) : null;
        Float digital = camera.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        Rect sensor = camera.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return new CameraTuning(ratio == null ? 1f : ratio.getLower(),
                ratio == null ? (digital == null ? 1f : digital) : ratio.getUpper(),
                ratio != null, sensor,
                contains(camera.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON),
                contains(camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON), width, height, fps);
    }

    CameraTuning(float min, float max, boolean ratio, Rect sensor, boolean video,
                 boolean optical, int width, int height, int fps) {
        zoomRatio = ratio;
        activeArray = sensor == null ? null : new Rect(sensor);
        minimum = ratio && Float.isFinite(min) && min > 0 ? Math.min(1f,min) : 1f;
        maximum = (!ratio && (sensor == null || sensor.isEmpty())) ? 1f
                : (Float.isFinite(max) ? Math.max(1f,max) : 1f);
        opticalSupported = optical;
        // Android guarantees advertised VIDEO ON for MediaCodec/MediaRecorder outputs
        // only at <=1920x1080 and <=30fps. Larger/faster modes use OIS if advertised.
        // https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE
        stabilization = video && width > 0 && height > 0 && width <= 1920
                && height <= 1080 && fps > 0 && fps <= 30 ? Stabilization.VIDEO
                : optical ? Stabilization.OPTICAL : Stabilization.NONE;
    }

    public float[] choices() {
        ArrayList<Float> values = new ArrayList<>();
        if (minimum < 1f) values.add(minimum);
        values.add(1f);
        if (maximum >= 2f) values.add(2f);
        float[] result = new float[values.size()];
        for (int i=0;i<result.length;i++) result[i]=values.get(i);
        return result;
    }

    public float clampZoom(float requested) {
        return Math.max(minimum, Math.min(maximum, Float.isFinite(requested) ? requested : 1f));
    }

    Rect cropRegion(float requested) {
        if (activeArray == null) return null;
        float ratio = clampZoom(requested);
        int width = Math.min(activeArray.width(), Math.max(1,(int)Math.ceil(activeArray.width()/ratio)));
        int height = Math.min(activeArray.height(), Math.max(1,(int)Math.ceil(activeArray.height()/ratio)));
        int left = activeArray.left + (activeArray.width()-width)/2;
        int top = activeArray.top + (activeArray.height()-height)/2;
        return new Rect(left,top,left+width,top+height);
    }

    /** Apply to each new/repeating builder, including the retry after disabling VIDEO. */
    public float apply(CaptureRequest.Builder request, float requestedZoom) {
        float effective = clampZoom(requestedZoom);
        if (zoomRatio && Build.VERSION.SDK_INT >= 30) {
            request.set(CaptureRequest.CONTROL_ZOOM_RATIO,effective);
            // Leave no extra digital crop when using the zoom-ratio coordinate system.
            if (activeArray != null) request.set(CaptureRequest.SCALER_CROP_REGION,new Rect(activeArray));
        } else {
            Rect crop = cropRegion(effective);
            if (crop != null) request.set(CaptureRequest.SCALER_CROP_REGION,crop);
        }
        request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                stabilization == Stabilization.VIDEO ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        // OFF must be explicit when VIDEO is selected; never combine EIS and OIS.
        if (opticalSupported) request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                stabilization == Stabilization.OPTICAL ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                        : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        return effective;
    }

    public Stabilization stabilization() { return stabilization; }

    /** Call once on a rejected VIDEO request, then rebuild/resubmit; avoid unbounded retries. */
    public boolean disableVideoStabilization() {
        if (stabilization != Stabilization.VIDEO) return false;
        stabilization = opticalSupported ? Stabilization.OPTICAL : Stabilization.NONE;
        summary = "Estabilização: verificando";
        return true;
    }

    public void observe(CaptureResult result) {
        observeModes(result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE),
                result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE));
    }

    void observeModes(Integer video, Integer optical) {
        if (video != null && video == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            summary = "Estabilização: vídeo ativa";
        else if (optical != null && optical == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            summary = "Estabilização: óptica ativa";
        else if (video == null && optical == null) summary = "Estabilização: não informada";
        else summary = "Estabilização: inativa";
    }

    public String stabilizationLabel() { return summary; }

    private static boolean contains(int[] modes, int wanted) {
        if (modes != null) for (int mode : modes) if (mode == wanted) return true;
        return false;
    }
}
