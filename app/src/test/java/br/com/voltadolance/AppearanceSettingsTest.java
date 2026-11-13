package br.com.voltadolance;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class AppearanceSettingsTest {
    @Before public void reset() { RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0).edit().clear().commit(); }
    @Test public void systemThemeTracksPhoneButExplicitChoicesOverrideIt() {
        android.content.Context c=RuntimeEnvironment.getApplication();
        Configuration cfg=new Configuration(c.getResources().getConfiguration());
        cfg.uiMode=Configuration.UI_MODE_NIGHT_YES;
        assertTrue(AppAppearance.isDark("system",cfg));
        assertFalse(AppAppearance.isDark("light",cfg));
        cfg.uiMode=Configuration.UI_MODE_NIGHT_NO;
        assertFalse(AppAppearance.isDark("system",cfg));
        assertTrue(AppAppearance.isDark("dark",cfg));
        assertFalse(AppAppearance.isDark("invalid",cfg));
    }
    @Test public void appearanceSelectionPersistsWithoutOverwritingCapturePreferences() {
        android.content.SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0);
        p.edit().putInt("past",60).putString("start_phrase","peixe na linha").commit();
        SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
        a.findViewById(R.id.appearance_setting).performClick();
        AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        d.getListView().performItemClick(d.getListView().getChildAt(2),2,2);
        assertEquals("dark",p.getString("appearance",null));
        assertEquals(60,p.getInt("past",0));
        assertEquals("peixe na linha",p.getString("start_phrase",null));
    }
    @Test public void invalidPhrasesStayEditableAndValidPairPersistsAtomically() {
        SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
        a.findViewById(R.id.voice_setting).performClick();
        EditText start=a.findViewById(R.id.start_phrase),stop=a.findViewById(R.id.stop_phrase);
        start.setText("ação");stop.setText("acao");a.findViewById(R.id.save_phrases).performClick();
        assertFalse(a.getSharedPreferences("local_settings",0).contains("start_phrase"));
        assertEquals("ação",start.getText().toString());
        start.setText("Peixe na linha");stop.setText("Pode salvar");a.findViewById(R.id.save_phrases).performClick();
        assertEquals("Peixe na linha",a.getSharedPreferences("local_settings",0).getString("start_phrase",null));
        assertEquals("Pode salvar",a.getSharedPreferences("local_settings",0).getString("stop_phrase",null));
    }
    @Test public void phraseDraftSurvivesRecreationWithoutBeingCommitted() {
        ActivityController<SettingsActivity> ctl=Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity a=ctl.get();a.findViewById(R.id.voice_setting).performClick();
        ((EditText)a.findViewById(R.id.start_phrase)).setText("Meu novo lance");
        Bundle state=new Bundle();ctl.saveInstanceState(state).pause().stop().destroy();
        SettingsActivity restored=Robolectric.buildActivity(SettingsActivity.class).create(state).start().restoreInstanceState(state).resume().get();
        assertEquals("Meu novo lance",((EditText)restored.findViewById(R.id.start_phrase)).getText().toString());
        assertFalse(restored.getSharedPreferences("local_settings",0).contains("start_phrase"));
    }
}
