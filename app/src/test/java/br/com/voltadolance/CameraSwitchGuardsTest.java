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
 private CameraLoop loop(){Activity a=Robolectric.buildActivity(Activity.class).create().get();return new CameraLoop(a,new TextureView(a),new CameraLoop.Listener(){public void onStatus(String s){}public void onSaved(Uri u){}public void onError(String s){}});}
 @Test public void recordingAndExportRejectLensSwitchBeforeQueryingHardware(){
  CameraLoop c=loop();try{c.state().ready();c.state().mark(1);int facing=c.facing();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().isRecording());c.state().beginSaving();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().isSaving());}finally{c.release();}
 }
 @Test public void unavailableDestinationLeavesReadyHistoryAndPreferenceUntouched(){
  CameraLoop c=loop();try{c.state().ready();int facing=c.facing();assertFalse(c.switchCamera());assertEquals(facing,c.facing());assertTrue(c.state().canMark());}finally{c.release();}
 }
}
