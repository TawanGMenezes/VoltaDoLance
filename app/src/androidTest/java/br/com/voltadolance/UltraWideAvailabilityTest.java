package br.com.voltadolance;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UltraWideAvailabilityTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
    private CameraLoop loop(MainActivity a){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("cameraLoop");f.setAccessible(true);return(CameraLoop)f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    @Test public void discoveredRouteCanBeTriedWithoutBreakingPrimaryCapture()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences("local_settings",0).edit().clear().putInt("notice_accepted_version",AlphaNotice.VERSION).putBoolean("voice",false).putString("video_mode","1280x720@30").commit();
        CameraLenses.Result result=CameraLenses.discover(context);android.util.Log.i("UltraWideAvailability",result.message+" route="+(result.ultraWide==null?"none":result.ultraWide.cameraId+":"+result.ultraWide.physicalId));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            AtomicBoolean ready=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+45000;
            do{scenario.onActivity(a->ready.set(loop(a).state().canMark()));if(ready.get())break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);assertTrue("Primary capture unavailable",ready.get());
            if(result.ultraWide!=null){
                AtomicBoolean attempted=new AtomicBoolean();scenario.onActivity(a->{for(float ratio:loop(a).zoomChoices())if(Math.abs(ratio-result.ultraWide.ratio)<.025f){attempted.set(loop(a).setZoom(ratio));break;}});
                if(attempted.get()){
                    ready.set(false);deadline=SystemClock.elapsedRealtime()+45000;do{scenario.onActivity(a->ready.set(loop(a).state().canMark()));if(ready.get())break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);assertTrue("Neither selected lens nor safe primary fallback became ready",ready.get());
                }
            }
            SettingsScreensTest.screenshot("ultra-wide-availability");
        }
    }
}
