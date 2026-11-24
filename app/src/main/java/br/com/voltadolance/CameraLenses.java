package br.com.voltadolance;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.SizeF;
import java.util.*;

/** Public Camera2 metadata only; physical routes still require session validation. */
public final class CameraLenses {
    public static final class Lens {
        public final String cameraId, physicalId;
        public final float ratio;
        public final List<CameraModes.Mode> modes;
        public final CameraCharacteristics characteristics;
        Lens(String cameraId, String physicalId, float ratio, List<CameraModes.Mode> modes,
                CameraCharacteristics characteristics) {
            this.cameraId=cameraId; this.physicalId=physicalId; this.ratio=ratio;
            this.modes=Collections.unmodifiableList(new ArrayList<>(modes));
            this.characteristics=characteristics;
        }
        public boolean requiresSessionValidation() { return physicalId!=null; }
    }
    public static final class Result {
        public final String primaryId, message;
        public final Lens ultraWide;
        Result(String primaryId, Lens ultraWide, String message) {
            this.primaryId=primaryId; this.ultraWide=ultraWide; this.message=message;
        }
    }
    private CameraLenses() { }

    public static Result discover(Context context) {
        CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        String primary=null;
        try {
            primary=CameraModes.cameraForFacing(manager,CameraCharacteristics.LENS_FACING_BACK);
            CameraCharacteristics main=manager.getCameraCharacteristics(primary);
            List<Lens> candidates=new ArrayList<>();
            String[] ids=manager.getCameraIdList();
            Set<String> independent=new HashSet<>(Arrays.asList(ids));
            for(String id:ids) {
                try {
                    CameraCharacteristics cc=manager.getCameraCharacteristics(id);
                    if(!id.equals(primary)) add(candidates,id,null,cc,main);
                    if(Build.VERSION.SDK_INT>=28 && isLogical(cc)) {
                        for(String physical:cc.getPhysicalCameraIds()) {
                            // An independently listed device has a simpler, directly openable route.
                            if(independent.contains(physical)) continue;
                            try { add(candidates,id,physical,manager.getCameraCharacteristics(physical),main); }
                            catch(Exception ignored) { /* Some vendors withhold physical metadata. */ }
                        }
                    }
                } catch(Exception ignored) { /* One incomplete camera must not hide the others. */ }
            }
            Lens selected=choose(candidates);
            android.util.Range<Float> zoom=Build.VERSION.SDK_INT>=30?main.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE):null;
            String message=zoom!=null&&zoom.getLower()<1f?"Ultra-angular integrada ao zoom informada pelo Android.":selected==null?"Ultra-angular não disponibilizada para vídeo neste acesso.":selected.requiresSessionValidation()?"Ultra-angular: acesso físico ainda precisa ser validado para vídeo.":"Ultra-angular separada informada para vídeo.";
            return new Result(primary,selected,message);
        } catch(Exception ignored) {
            return new Result(primary,null,"Não foi possível consultar a ultra-angular neste acesso.");
        }
    }
    private static boolean isLogical(CameraCharacteristics cc) {
        int[] caps=cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if(caps!=null) for(int cap:caps)
            if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)return true;
        return false;
    }
    private static void add(List<Lens> result,String owner,String physical,CameraCharacteristics cc,CameraCharacteristics main) {
        Integer facing=cc.get(CameraCharacteristics.LENS_FACING);
        if(facing==null || facing!=CameraCharacteristics.LENS_FACING_BACK)return;
        float ratio=relativeRatio(cc,main);
        if(!isUltraWide(ratio))return;
        List<CameraModes.Mode> modes=CameraModes.detect(cc);
        if(!modes.isEmpty())result.add(new Lens(owner,physical,ratio,modes,cc));
    }
    private static float relativeRatio(CameraCharacteristics candidate,CameraCharacteristics main) {
        float[] focal=candidate.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        float[] primaryFocal=main.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor=candidate.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        SizeF primarySensor=main.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if(focal==null || focal.length!=1 || primaryFocal==null || primaryFocal.length!=1 || sensor==null || primarySensor==null)return Float.NaN;
        // Multiple focal lengths are sorted ascending, not by the default 1x lens.
        // Without an unambiguous baseline, avoid inventing an ultrawide shortcut.
        return relativeRatio(focal[0],sensor.getWidth(),sensor.getHeight(),primaryFocal[0],primarySensor.getWidth(),primarySensor.getHeight());
    }
    static float relativeRatio(float focal,float width,float height,float primaryFocal,float primaryWidth,float primaryHeight) {
        for(float value:new float[]{focal,width,height,primaryFocal,primaryWidth,primaryHeight})
            if(!Float.isFinite(value) || value<=0)return Float.NaN;
        return (float)((focal/Math.hypot(width,height))/(primaryFocal/Math.hypot(primaryWidth,primaryHeight)));
    }
    private static boolean isUltraWide(float ratio) { return Float.isFinite(ratio) && ratio>0 && ratio<.85f; }
    static Lens choose(List<Lens> candidates) {
        Map<String,Lens> unique=new LinkedHashMap<>();
        for(Lens lens:candidates) {
            if(!isUltraWide(lens.ratio) || lens.modes.isEmpty())continue;
            String identity=lens.physicalId==null?lens.cameraId:lens.physicalId;
            Lens previous=unique.get(identity);
            if(previous==null || previous.physicalId!=null && lens.physicalId==null)unique.put(identity,lens);
        }
        Lens best=null;
        for(Lens lens:unique.values())if(best==null || lens.ratio<best.ratio)best=lens;
        return best;
    }
}
