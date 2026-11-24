package br.com.voltadolance;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.view.Surface;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PhysicalLensSessionTest {
    @Test public void previewAndSavedStreamTargetTheSamePhysicalLens(){
        SurfaceTexture a=new SurfaceTexture(false),b=new SurfaceTexture(false);Surface preview=new Surface(a),encoder=new Surface(b);
        try{
            SessionConfiguration session=PhysicalLensSession.configuration(Arrays.asList(preview,encoder),"wide",Runnable::run,new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){}public void onConfigureFailed(CameraCaptureSession s){}});
            assertEquals(2,session.getOutputConfigurations().size());
            for(OutputConfiguration output:session.getOutputConfigurations())assertEquals("wide",ReflectionHelpers.getField(output,"mPhysicalCameraId"));
            assertSame(preview,session.getOutputConfigurations().get(0).getSurface());assertSame(encoder,session.getOutputConfigurations().get(1).getSurface());
        }finally{preview.release();encoder.release();a.release();b.release();}
    }
}
