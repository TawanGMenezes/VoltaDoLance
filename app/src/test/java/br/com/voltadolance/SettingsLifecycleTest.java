package br.com.voltadolance;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class SettingsLifecycleTest {
    private SharedPreferences p;
    @Before public void reset(){p=RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0);p.edit().clear().commit();}
    private static Object field(Object o,String key)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    private static void open(MainActivity a)throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("openSettings");m.setAccessible(true);m.invoke(a);}
    @Test public void settingsRefusesRecordingAndExportEvenIfButtonIsStaleEnabled()throws Exception{
        AlphaNotice.accept(RuntimeEnvironment.getApplication());MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
        while(shadowOf(a).getNextStartedActivity()!=null){}
        CameraLoop loop=(CameraLoop)field(a,"cameraLoop");loop.state().ready();loop.state().mark(1);
        a.findViewById(R.id.settings_button).setEnabled(true);open(a);assertNull(shadowOf(a).getNextStartedActivity());
        loop.state().beginSaving();open(a);assertNull(shadowOf(a).getNextStartedActivity());
    }
    @Test public void manualPausePersistsAndPreferencesReloadOnSameActivityResume()throws Exception{
        AlphaNotice.accept(RuntimeEnvironment.getApplication());ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
        CameraLoop loop=(CameraLoop)field(a,"cameraLoop");loop.pauseHistory();ctl.pause();
        p.edit().putInt("past",60).putInt("post_minutes",30).putString("start_phrase","Peixe na linha").putString("stop_phrase","Pode salvar").commit();ctl.resume();
        assertTrue(loop.isManuallyPaused());assertFalse(loop.isRunning());
        assertEquals(60,field(a,"past"));assertEquals(30,field(a,"postMinutes"));assertEquals("Peixe na linha",field(a,"startPhrase"));ctl.pause().stop().destroy();
    }
    @Test public void voiceCallbackCannotMarkWhileSettingsIsOpening()throws Exception{
        AlphaNotice.accept(RuntimeEnvironment.getApplication());ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
        CameraLoop loop=(CameraLoop)field(a,"cameraLoop");loop.state().ready();open(a);
        OnDeviceVoice.Callback callback=(OnDeviceVoice.Callback)field(field(a,"voice"),"callback");callback.onMark();assertFalse(loop.state().isRecording());ctl.pause().stop().destroy();
    }
    private static CheckBox checkbox(View v){if(v instanceof CheckBox)return(CheckBox)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox c=checkbox(((ViewGroup)v).getChildAt(i));if(c!=null)return c;}}return null;}
    @Test public void firstLaunchRequiresExplicitTestTermsAcceptanceBeforeCapture()throws Exception{
        MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();shadowOf(Looper.getMainLooper()).idle();
        AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());assertFalse(d.getButton(-1).isEnabled());
        assertFalse(AlphaNotice.accepted(a));assertFalse(((CameraLoop)field(a,"cameraLoop")).isRunning());
        checkbox(d.getWindow().getDecorView()).setChecked(true);d.getButton(-1).performClick();assertTrue(AlphaNotice.accepted(a));assertTrue(p.getLong("notice_accepted_at",0)>0);
    }
}
