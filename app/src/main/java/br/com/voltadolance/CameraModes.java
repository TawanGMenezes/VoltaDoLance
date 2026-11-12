package br.com.voltadolance;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCodec;
import android.util.Range;
import android.util.Size;
import java.util.*;

/** Standard sessions only: high-speed-only modes aren't advertised. */
public final class CameraModes {
    public static final class Mode {
        public final int width, height, fps;
        public final Range<Integer> aeRange;
        Mode(int w,int h,int f,Range<Integer> range) { width=w; height=h; fps=f; aeRange=range; }
        public String key() { return width+"x"+height+"@"+fps; }
        public String label() { return (height==2160?"4K":height+"p")+" / "+fps+" fps"; }
        public int bitrate() { return Math.max(4_000_000,Math.min(60_000_000,width*height*fps/8)); }
    }
    private CameraModes() { }
    public static String backCamera(CameraManager manager) throws Exception {
        for(String id:manager.getCameraIdList()) {
            Integer facing=manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if(facing!=null && facing==CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        throw new IllegalStateException("Câmera traseira não encontrada");
    }
    public static List<Mode> detect(CameraCharacteristics cc) {
        List<Mode> result=new ArrayList<>();
        StreamConfigurationMap map=cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Range<Integer>[] ranges=cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if(map==null || ranges==null) return result;
        Size[] recorder=map.getOutputSizes(MediaCodec.class), preview=map.getOutputSizes(SurfaceTexture.class);
        if(recorder==null || preview==null) return result;
        for(Size size:recorder) {
            int w=size.getWidth(),h=size.getHeight();
            if(w<h || w>3840 || h>2160 || !Arrays.asList(preview).contains(size)) continue;
            long minNs=Math.max(map.getOutputMinFrameDuration(MediaCodec.class,size),
                    map.getOutputMinFrameDuration(SurfaceTexture.class,size));
            for(int fps:new int[]{30,60}) {
                if(fps==60 && minNs==0) continue;
                if(minNs>0 && minNs>1_000_000_000L/fps+1000) continue;
                Range<Integer> chosen=null;
                for(Range<Integer> range:ranges) if(range.getUpper()==fps && range.contains(fps)
                        && (chosen==null || range.getLower()>chosen.getLower())) chosen=range;
                if(chosen!=null && encoderSupports(w,h,fps)) result.add(new Mode(w,h,fps,chosen));
            }
        }
        result.sort(Comparator.comparingLong((Mode m)->(long)m.width*m.height).reversed()
                .thenComparing(Comparator.comparingInt((Mode m)->m.fps).reversed()));
        return result;
    }
    private static boolean encoderSupports(int w,int h,int fps) {
        for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(!codec.isEncoder()) continue;
            for(String type:codec.getSupportedTypes()) if(type.equalsIgnoreCase("video/avc")) {
                try {
                    MediaCodecInfo.VideoCapabilities caps=codec.getCapabilitiesForType(type).getVideoCapabilities();
                    if(caps!=null && caps.areSizeAndRateSupported(w,h,fps)) return true;
                } catch(IllegalArgumentException ignored) { }
            }
        }
        return false;
    }
}
