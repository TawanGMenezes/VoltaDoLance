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
public class CameraSwitchGuardsTest {
 private void set(CameraLoop c,String name,Object value)throws Exception{java.lang.reflect.Field f=null;for(java.lang.reflect.Field x:CameraLoop.class.getDeclaredFields())if(x.getName().equals(name))f=x;assertNotNull("Missing recovery state "+name,f);f.setAccessible(true);f.set(c,value);}
 private CameraLoop loop(){Activity a=Robolectric.buildActivity(Activity.class).create().get();return new CameraLoop(a,new TextureView(a),new CameraLoop.Listener(){public void onStatus(String s){}public void onSaved(Uri u){}public void onError(String s){}});}
 @Test public void recordingAndExportRejectLensSwitchBeforeQueryingHardware(){
  CameraLoop c=loop();try{c.state().ready();c.state().mark(1);int facing=c.facing();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().isRecording());c.state().beginSaving();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().isSaving());}finally{c.release();}
 }
 @Test public void unavailableDestinationLeavesReadyHistoryAndPreferenceUntouched(){
  CameraLoop c=loop();try{c.state().ready();int facing=c.facing();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().canMark());}finally{c.release();}
 }
 @Test public void exhaustedModesStillExposeSafeRequestedMode()throws Exception{
  CameraLoop c=loop();try{c.setMode("1280x720@30");set(c,"modes",java.util.Arrays.asList(new CameraModes.Mode(1280,720,30,new android.util.Range<>(30,30))));set(c,"modeIndex",1);assertEquals("1280x720@30",c.selectedMode());}finally{c.release();}
 }
 @Test public void asynchronousDestinationFailureRestoresPreviousLensWhileBackgrounded()throws Exception{
  CameraLoop c=loop();try{set(c,"facing",android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT);set(c,"rollbackFacing",android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK);set(c,"rollbackMode","1280x720@30");set(c,"activityActive",false);
   java.lang.reflect.Method fail=CameraLoop.class.getDeclaredMethod("fail",String.class);fail.setAccessible(true);fail.invoke(c,"Destination rejected");
   assertEquals(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK,c.facing());assertEquals("1280x720@30",c.selectedMode());assertFalse(c.isRunning());
  }finally{c.release();}
 }
}
