package br.com.voltadolance;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.view.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Both saved video and preview must come from the same selected physical lens. */
final class PhysicalLensSession {
    static SessionConfiguration configuration(List<Surface> surfaces,String physicalId,Executor executor,CameraCaptureSession.StateCallback callback){
        if(physicalId==null||physicalId.isEmpty())throw new IllegalArgumentException("Lente física ausente");
        List<OutputConfiguration> outputs=new ArrayList<>();
        for(Surface surface:surfaces){OutputConfiguration output=new OutputConfiguration(surface);output.setPhysicalCameraId(physicalId);outputs.add(output);}
        return new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,executor,callback);
    }
}
