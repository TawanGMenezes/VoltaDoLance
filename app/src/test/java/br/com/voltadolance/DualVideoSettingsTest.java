package br.com.voltadolance;
import android.widget.Switch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DualVideoSettingsTest {
 @Test public void captureScreenExposesOffByDefaultDualVideoOption(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();a.findViewById(R.id.capture_setting).performClick();
  Switch option=a.getWindow().getDecorView().findViewWithTag("dual_video_toggle");assertNotNull("Dual video control missing",option);assertFalse(option.isChecked());
 }
}
