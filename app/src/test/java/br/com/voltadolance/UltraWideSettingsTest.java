package br.com.voltadolance;

import android.hardware.camera2.CameraManager;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UltraWideSettingsTest {
    private void finishAvailability(SettingsActivity activity) throws Exception {
        Field field=SettingsActivity.class.getDeclaredField("availability");field.setAccessible(true);
        ((ExecutorService)field.get(activity)).submit(()->{}).get(5,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void captureShowsUltraWideStatusWithoutAssumingHardware() throws Exception {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            SettingsActivity activity=controller.get();activity.findViewById(R.id.capture_setting).performClick();
            TextView status=activity.findViewById(R.id.ultra_wide_status);
            assertNotNull(status);assertEquals(View.VISIBLE,status.getVisibility());
            finishAvailability(activity);
            assertTrue(status.getText().toString().contains("indisponível para vídeo neste acesso"));
            assertTrue(status.getText().toString().contains("não confirma a ausência"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test @Config(shadows=DeniedCameraMetadata.class)
    public void deniedMetadataDoesNotClaimUltraWidePresence() throws Exception {
        ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            SettingsActivity activity=controller.get();activity.findViewById(R.id.capture_setting).performClick();
            finishAvailability(activity);
            String status=((TextView)activity.findViewById(R.id.ultra_wide_status)).getText().toString();
            assertTrue(status.contains("indisponível para vídeo neste acesso"));
            assertFalse(status.contains("Opção ultra-angular informada"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Implements(CameraManager.class)
    public static class DeniedCameraMetadata {
        @Implementation protected String[] getCameraIdList() { throw new SecurityException("Metadata denied"); }
    }
}
