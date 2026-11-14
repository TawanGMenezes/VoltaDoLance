package br.com.voltadolance;
import android.app.Activity;
import android.net.Uri;
import android.view.TextureView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DualLifecycleTest {
 private CameraLoop loop(){Activity a=Robolectric.buildActivity(Activity.class).create().get();return new CameraLoop(a,new TextureView(a),new CameraLoop.Listener(){public void onStatus(String s){}public void onSaved(Uri u){}public void onError(String s){}});}
 @Test public void dualSelectionKeepsNormalQualityAndRejectsLensSwitch(){CameraLoop c=loop();try{c.setMode("3840x2160@30");c.setDualVideo(true);assertTrue(c.isDualVideo());assertFalse(c.switchCamera());assertEquals("3840x2160@30",c.selectedMode());c.setDualVideo(false);assertEquals("3840x2160@30",c.selectedMode());}finally{c.release();}}
 @Test public void markedOrSavingEventCannotChangeDualMode(){CameraLoop c=loop();try{c.state().ready();c.state().mark(1);c.setDualVideo(true);assertFalse(c.isDualVideo());c.state().beginSaving();c.setDualVideo(true);assertFalse(c.isDualVideo());}finally{c.release();}}
 @Test public void unsupportedDualStartupKeepsManualPauseAndRestoresNormalRear()throws Exception{
  CameraLoop c=loop();try{c.pauseHistory();c.setDualVideo(true);java.lang.reflect.Method m=CameraLoop.class.getDeclaredMethod("fallbackDual",String.class);m.setAccessible(true);m.invoke(c,"Unavailable");assertFalse(c.isDualVideo());assertTrue(c.isManuallyPaused());assertFalse(c.isRunning());assertEquals(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK,c.facing());}finally{c.release();}
 }
}
