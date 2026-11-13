package br.com.voltadolance;
import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.hardware.camera2.*;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.TextureView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CameraSwitchTest {
 @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
 private ActivityScenario<MainActivity> scenario;
 private static Object field(Object o,String n){try{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 private <T>T ui(Function<MainActivity,T> work){AtomicReference<T> out=new AtomicReference<>();scenario.onActivity(a->out.set(work.apply(a)));return out.get();}
 private CameraLoop loop(MainActivity a){return(CameraLoop)field(a,"cameraLoop");}
 private void await(String message,java.util.function.BooleanSupplier condition)throws Exception{long deadline=SystemClock.elapsedRealtime()+45000;while(SystemClock.elapsedRealtime()<deadline){if(condition.getAsBoolean())return;Thread.sleep(100);}fail(message+" state="+ui(a->loop(a).state().phase()));}
 private long newest(Context c){try(Cursor q=c.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},MediaStore.Video.Media.RELATIVE_PATH+" LIKE ?",new String[]{"Movies/VoltaDoLance/%"},"_id DESC")){return q!=null&&q.moveToFirst()?q.getLong(0):-1;}}
 @Test public void swapsActualCamerasSavesFrontVideoAndPreservesManualPause()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.getSharedPreferences("local_settings",0).edit().clear().putInt("notice_accepted_version",AlphaNotice.VERSION).putString("video_mode","1280x720@30").putBoolean("voice",false).commit();
  scenario=ActivityScenario.launch(MainActivity.class);
  try{
   await("Rear camera did not start",()->ui(a->loop(a).state().canMark()));Object rear=ui(a->field(loop(a),"session"));
   ui(a->{a.findViewById(R.id.camera_switch).performClick();return null;});
   await("Front camera did not start",()->ui(a->loop(a).facing()==CameraCharacteristics.LENS_FACING_FRONT&&loop(a).state().canMark()));
   assertNotSame(rear,ui(a->field(loop(a),"session")));assertEquals(CameraCharacteristics.LENS_FACING_FRONT,c.getSharedPreferences("local_settings",0).getInt("camera_facing",-1));
   assertEquals(-1f,ui(a->((TextureView)field(a,"preview")).getScaleX()),0f);
   SettingsScreensTest.screenshot("front-camera-ready");long before=newest(c);
   ui(a->{a.findViewById(R.id.mark_moment).performClick();assertTrue(loop(a).state().isRecording());assertFalse(a.findViewById(R.id.camera_switch).isEnabled());assertFalse(loop(a).switchCamera());assertEquals(CameraCharacteristics.LENS_FACING_FRONT,loop(a).facing());return null;});
   Thread.sleep(2000);ui(a->{a.findViewById(R.id.finish_save).performClick();assertFalse(loop(a).switchCamera());return null;});
   await("Front video was not saved",()->newest(c)>before);await("Front history did not resume",()->ui(a->loop(a).state().canMark()));
   ui(a->{loop(a).pauseHistory();a.findViewById(R.id.camera_switch).setEnabled(true);a.findViewById(R.id.camera_switch).performClick();assertTrue(loop(a).isManuallyPaused());assertFalse(loop(a).isRunning());assertEquals(CameraCharacteristics.LENS_FACING_BACK,loop(a).facing());return null;});
   assertEquals(CameraCharacteristics.LENS_FACING_BACK,c.getSharedPreferences("local_settings",0).getInt("camera_facing",-1));
  }finally{scenario.close();}
 }
}
