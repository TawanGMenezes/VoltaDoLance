package br.com.voltadolance;
import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class OnDeviceVoiceTest {
 @Test public void closedVoiceNeverLoadsOrAcceptsPcm() {
  OnDeviceVoice v=new OnDeviceVoice(Robolectric.buildActivity(Activity.class).create().get(),new OnDeviceVoice.Callback(){
   public void onMark(){fail();} public void onFinish(){fail();} public void onUnavailable(){}
  });
  v.close(); v.close();
  assertFalse(v.enable()); assertFalse(v.isReady());
  v.onPcm(new byte[320],320); v.disable();
 }
}
