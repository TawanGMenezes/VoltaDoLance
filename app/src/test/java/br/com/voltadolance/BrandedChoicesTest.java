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
 @Test public void cancellingChoiceKeepsStoredAppearance() {
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();a.getSharedPreferences("local_settings",0).edit().putString("appearance","light").commit();
  a.findViewById(R.id.appearance_setting).performClick();Dialog d=ShadowDialog.getLatestDialog();
  android.view.View cancel=d.getWindow().getDecorView().findViewWithTag("choice_cancel");assertNotNull(cancel);cancel.performClick();
  assertEquals("light",a.getSharedPreferences("local_settings",0).getString("appearance",null));
 }
 @Test public void lastDurationCardStillSavesUnlimitedRatherThanItsDisplayIndex(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();a.findViewById(R.id.capture_setting).performClick();a.findViewById(R.id.duration_setting).performClick();
  Dialog d=ShadowDialog.getLatestDialog();d.getWindow().getDecorView().findViewWithTag("choice_5").performClick();assertEquals(0,a.getSharedPreferences("local_settings",0).getInt("post_minutes",-1));
 }
}
