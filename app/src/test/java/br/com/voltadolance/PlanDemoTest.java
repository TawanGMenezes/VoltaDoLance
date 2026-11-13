package br.com.voltadolance;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PlanDemoTest {
    @Test public void simulatedTrialExpiresWithoutChangingRealCaptureSettings() {
        android.content.SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0);
        p.edit().clear().putInt("past",60).commit();
        PlanDemo d=new PlanDemo(p);long now=1_000_000;
        d.startTrial(now,"annual");
        assertEquals("trial",d.state(now+29L*86400000));
        assertEquals("expired",d.state(now+30L*86400000));
        assertEquals(60,p.getInt("past",0));
        assertEquals("annual",p.getString("demo_plan",null));
        d.subscribe("monthly");assertEquals("subscribed",d.state(now));
        d.reset();assertEquals("none",d.state(now));
        assertEquals(60,p.getInt("past",0));
    }
}
