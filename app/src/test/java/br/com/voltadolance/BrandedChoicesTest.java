package br.com.voltadolance;
import android.app.Dialog;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class BrandedChoicesTest {
 @Test public void appearanceOffersBrandedOptionCards() {
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();a.findViewById(R.id.appearance_setting).performClick();
  Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d.getWindow().getDecorView().findViewWithTag("choice_2"));
 }
}
