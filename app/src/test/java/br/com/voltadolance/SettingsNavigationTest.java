package br.com.voltadolance;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class SettingsNavigationTest {
    @Test public void settingsOpensDedicatedScreenWithoutRequiringPermissions() throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
        while(shadowOf(activity).getNextStartedActivity()!=null) { /* Ignore initial Android permission request. */ }
        java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openSettings");
        open.setAccessible(true); open.invoke(activity);
        Intent next=shadowOf(activity).getNextStartedActivity();
        assertNotNull("Settings must navigate to a dedicated screen",next);
        assertEquals("br.com.voltadolance.SettingsActivity",next.getComponent().getClassName());
    }
}
