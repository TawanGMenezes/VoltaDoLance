package br.com.voltadolance;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DualVideoAvailabilityTest {
 @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
 private static CameraLoop loop(MainActivity a){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("cameraLoop");f.setAccessible(true);return(CameraLoop)f.get(a);}catch(Exception e){throw new AssertionError(e);}}
 private static long newest(Context c){try(Cursor q=c.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},MediaStore.Video.Media.RELATIVE_PATH+" LIKE ?",new String[]{"Movies/VoltaDoLance/%"},"_id DESC")){return q!=null&&q.moveToFirst()?q.getLong(0):-1;}}
 @Test public void optionMatchesActualConcurrencyAndNeverPretendsUnsupportedCapture()throws Exception{
  Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();target.getSharedPreferences("local_settings",0).edit().clear().putInt("notice_accepted_version",AlphaNotice.VERSION).putBoolean("voice",false).commit();
  DualCameraSupport.Result support=DualCameraSupport.inspect(target);android.util.Log.i("DualAvailability",support.available+" • "+support.message);
  try(ActivityScenario<SettingsActivity> settings=ActivityScenario.launch(SettingsActivity.class)){
   settings.onActivity(a->a.findViewById(R.id.capture_setting).performClick());long deadline=SystemClock.elapsedRealtime()+20000;AtomicReference<String> status=new AtomicReference<>("");
   do{settings.onActivity(a->status.set(((TextView)a.findViewById(R.id.dual_video_status)).getText().toString()));if(!status.get().contains("Verificando"))break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
   assertFalse("Availability query did not complete",status.get().contains("Verificando"));
   settings.onActivity(a->{Switch option=a.findViewById(R.id.dual_video);assertEquals(support.available,option.isEnabled());assertFalse(option.isChecked());if(support.available)option.performClick();});
   SettingsScreensTest.screenshot("dual-availability");
  }
  if(!support.available){assertFalse(target.getSharedPreferences("local_settings",0).getBoolean("dual_video",false));return;}
  long before=newest(target);
  try(ActivityScenario<MainActivity> main=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<Boolean> ready=new AtomicReference<>(false);long deadline=SystemClock.elapsedRealtime()+45000;
   do{main.onActivity(a->ready.set(loop(a).state().canMark()));if(ready.get())break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
   assertTrue("Advertised concurrent cameras did not become ready",ready.get());
   main.onActivity(a->{assertTrue("Concurrent startup silently fell back",loop(a).isDualVideo());assertFalse(a.findViewById(R.id.camera_switch).isEnabled());a.findViewById(R.id.mark_moment).performClick();assertTrue(loop(a).state().isRecording());assertFalse(loop(a).switchCamera());});
   Thread.sleep(2000);main.onActivity(a->a.findViewById(R.id.finish_save).performClick());deadline=SystemClock.elapsedRealtime()+30000;while(newest(target)<=before&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);assertTrue("Concurrent clip not saved",newest(target)>before);
   SettingsScreensTest.screenshot("dual-camera-saved");
  }
 }
}
