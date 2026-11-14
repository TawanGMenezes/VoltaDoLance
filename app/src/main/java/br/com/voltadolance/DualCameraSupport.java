package br.com.voltadolance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Range;
import android.view.Surface;
import java.util.*;

/** Metadata and bounded session-query checks only; never opens a device or microphone. */
public final class DualCameraSupport {
    public static final class Result {
        public final boolean available;
        public final String message;
        public final DualCaptureConfig config;
        private Result(boolean available,String message,DualCaptureConfig config) {
            this.available=available; this.message=message; this.config=config;
        }
    }
    private DualCameraSupport() { }
    private static Result unavailable(String message) { return new Result(false,message,null); }

    public static Result inspect(Context context) {
        if(Build.VERSION.SDK_INT<30) return unavailable("Vídeo duplo precisa de Android 11 ou mais recente.");
        if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            return unavailable("Permita o acesso à câmera para verificar o vídeo duplo.");
        CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        if(manager==null) return unavailable("Serviço de câmera indisponível neste aparelho.");
        try {
            Set<Set<String>> advertised=manager.getConcurrentCameraIds();
            if(advertised.isEmpty()) return unavailable("Este aparelho não disponibiliza câmeras simultâneas para aplicativos.");
            Map<String,CameraCharacteristics> characteristics=new HashMap<>();
            Map<String,Integer> facings=new HashMap<>();
            for(Set<String> group:advertised) for(String id:group) {
                if(characteristics.containsKey(id)) continue;
                CameraCharacteristics cc=manager.getCameraCharacteristics(id);
                characteristics.put(id,cc); facings.put(id,cc.get(CameraCharacteristics.LENS_FACING));
            }
            List<String[]> pairs=frontBackPairs(advertised,facings);
            if(pairs.isEmpty()) return unavailable("O aparelho não oferece câmera frontal e traseira juntas para aplicativos.");
            boolean hasFormats=false;
            for(String[] pair:pairs) {
                CameraCharacteristics rear=characteristics.get(pair[0]),front=characteristics.get(pair[1]);
                Integer rearOrientation=rear.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Integer frontOrientation=front.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if(rearOrientation==null || frontOrientation==null) continue;
                List<CameraModes.Mode> rearModes=candidateModes(CameraModes.detect(rear),false);
                List<CameraModes.Mode> frontModes=candidateModes(CameraModes.detect(front),true);
                if(rearModes.isEmpty() || frontModes.isEmpty()) continue;
                hasFormats=true;
                for(CameraModes.Mode rearMode:rearModes) {
                    // Encoding is upright, so landscape encoder support is insufficient.
                    if(!encoderSupports(rearMode.height,rearMode.width,30)) continue;
                    for(CameraModes.Mode frontMode:frontModes) {
                        if(!supportsSessions(manager,pair[0],pair[1],rearMode,frontMode)) continue;
                        CameraModes.Mode output=new CameraModes.Mode(rearMode.height,rearMode.width,30,new Range<>(30,30));
                        Integer clock=rear.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
                        DualCaptureConfig config=new DualCaptureConfig(pair[0],pair[1],rearMode,frontMode,output,
                                rearOrientation,frontOrientation,clock!=null && clock==CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
                        return new Result(true,"Vídeo duplo disponível: traseira com frontal no canto superior direito.",config);
                    }
                }
            }
            return unavailable(hasFormats ? "As câmeras não aceitam juntas os formatos de vídeo duplo testados."
                    : "Não há formatos de vídeo compatíveis para usar as duas câmeras juntas.");
        } catch(SecurityException denied) {
            return unavailable("Permita o acesso à câmera para verificar o vídeo duplo.");
        } catch(Exception failure) {
            return unavailable("Não foi possível confirmar o vídeo duplo agora. Tente novamente com as câmeras livres.");
        }
    }

    static List<String[]> frontBackPairs(Set<Set<String>> advertised,Map<String,Integer> facings) {
        TreeMap<String,String[]> result=new TreeMap<>();
        for(Set<String> group:advertised) for(String rear:group) for(String front:group) {
            if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(facings.get(rear))
                    && Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(facings.get(front)))
                result.put(rear+"\u0000"+front,new String[]{rear,front});
        }
        return new ArrayList<>(result.values());
    }

    static List<CameraModes.Mode> candidateModes(List<CameraModes.Mode> detected,boolean front) {
        List<CameraModes.Mode> result=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(CameraModes.Mode mode:detected) {
            if(mode.fps!=30 || mode.width<mode.height || mode.width>1280 || mode.height>720) continue;
            if(seen.add(mode.key())) result.add(mode);
        }
        final int preferredWidth=front?640:1280,preferredHeight=front?480:720;
        result.sort(Comparator.comparingInt((CameraModes.Mode m)->m.width==preferredWidth && m.height==preferredHeight?0:1)
                .thenComparing(Comparator.comparingLong((CameraModes.Mode m)->(long)m.width*m.height).reversed()));
        return new ArrayList<>(result.subList(0,Math.min(3,result.size())));
    }

    private static boolean supportsSessions(CameraManager manager,String rearId,String frontId,
            CameraModes.Mode rear,CameraModes.Mode front) throws Exception {
        // Detached textures avoid requiring an EGL context on this metadata worker thread.
        SurfaceTexture rearTexture=null,frontTexture=null;
        Surface rearSurface=null,frontSurface=null;
        try {
            rearTexture=new SurfaceTexture(false);
            rearTexture.setDefaultBufferSize(rear.width,rear.height);
            rearSurface=new Surface(rearTexture);
            frontTexture=new SurfaceTexture(false);
            frontTexture.setDefaultBufferSize(front.width,front.height);
            frontSurface=new Surface(frontTexture);
            Map<String,SessionConfiguration> sessions=new HashMap<>();
            sessions.put(rearId,session(rearSurface)); sessions.put(frontId,session(frontSurface));
            return manager.isConcurrentSessionConfigurationSupported(sessions);
        } finally {
            if(frontSurface!=null) frontSurface.release();
            if(rearSurface!=null) rearSurface.release();
            if(frontTexture!=null) frontTexture.release();
            if(rearTexture!=null) rearTexture.release();
        }
    }
    private static SessionConfiguration session(Surface surface) {
        return new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                Collections.singletonList(new OutputConfiguration(surface)),Runnable::run,
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession session) { }
                    @Override public void onConfigureFailed(CameraCaptureSession session) { }
                });
    }
    private static boolean encoderSupports(int width,int height,int fps) {
        for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(!codec.isEncoder()) continue;
            for(String type:codec.getSupportedTypes()) if("video/avc".equalsIgnoreCase(type)) {
                try {
                    MediaCodecInfo.CodecCapabilities caps=codec.getCapabilitiesForType(type);
                    boolean surfaceInput=false;
                    for(int format:caps.colorFormats) if(format==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) surfaceInput=true;
                    if(surfaceInput && caps.getVideoCapabilities()!=null
                            && caps.getVideoCapabilities().areSizeAndRateSupported(width,height,fps)) return true;
                } catch(IllegalArgumentException ignored) { }
            }
        }
        return false;
    }
}
