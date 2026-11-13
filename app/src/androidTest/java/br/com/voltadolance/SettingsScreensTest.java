package br.com.voltadolance;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileInputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsScreensTest {
    private static void shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            FileInputStream input=new FileInputStream(fd.getFileDescriptor())){byte[] bytes=new byte[4096];while(input.read(bytes)!=-1){} }
    }
    static void screenshot(String name)throws Exception{
        // Google APIs emulator's Pixel Launcher can leave its own ANR dialog over
        // the foreground app. Stop only that unrelated launcher, never the app under test.
        shell("am force-stop com.google.android.apps.nexuslauncher");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
        shell("mkdir -p /sdcard/Download/lancevivo-qa");shell("screencap -p /sdcard/Download/lancevivo-qa/"+name+".png");
    }
    private static View find(View root,String value){
        if(root instanceof TextView && ((TextView)root).getText().toString().equals(value))return root;
        if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),value);if(v!=null)return v;}
        return null;
    }
    @Test public void actualSettingsRenderLightDarkVoicePlansAndLargeText()throws Exception{
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(String mode:new String[]{"light","dark"}){
            c.getSharedPreferences("local_settings",0).edit().putString("appearance",mode).commit();
            try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
                scenario.onActivity(a->{assertEquals(mode.equals("dark"),a.palette.dark);assertNotNull(a.findViewById(R.id.appearance_setting));});
                screenshot(mode+"-settings");
                scenario.onActivity(a->a.findViewById(R.id.appearance_setting).performClick());screenshot(mode+"-appearance-choice");
                InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(1);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->{a.findViewById(R.id.capture_setting).performClick();a.findViewById(R.id.history_setting).performClick();});screenshot(mode+"-history-choice");
                InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(1);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->a.findViewById(R.id.duration_setting).performClick());screenshot(mode+"-duration-choice");
                InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(1);InstrumentationRegistry.getInstrumentation().waitForIdleSync();scenario.onActivity(SettingsActivity::onBackPressed);
                scenario.onActivity(a->a.findViewById(R.id.voice_setting).performClick());screenshot(mode+"-voice");
                scenario.onActivity(a->{((EditText)a.findViewById(R.id.start_phrase)).setText("Peixe na linha");((EditText)a.findViewById(R.id.stop_phrase)).setText("Pode salvar");a.findViewById(R.id.save_phrases).performClick();a.onBackPressed();});
                scenario.onActivity(a->a.findViewById(R.id.plans_setting).performClick());screenshot(mode+"-plans");
                scenario.onActivity(a->{a.findViewById(R.id.demo_expire).performClick();assertTrue(((TextView)a.findViewById(R.id.demo_status)).getText().toString().contains("expirado"));});
            }
        }
        try{
            shell("settings put system font_scale 1.3");
            try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){screenshot("dark-large-settings");scenario.onActivity(a->a.findViewById(R.id.voice_setting).performClick());screenshot("dark-large-voice");}
        }finally{shell("settings put system font_scale 1.0");}
    }
}
