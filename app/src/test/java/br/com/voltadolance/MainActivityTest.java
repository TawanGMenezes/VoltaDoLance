package br.com.voltadolance;

import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.ArrayList;
import java.util.List;
import android.os.SystemClock;
import java.time.Duration;
import org.robolectric.shadows.ShadowSystemClock;
import android.os.Looper;
import static org.robolectric.Shadows.shadowOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class MainActivityTest {
    private static void editTexts(View view,List<EditText> result) {
        if(view instanceof EditText) result.add((EditText)view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) editTexts(((ViewGroup)view).getChildAt(i),result);
    }
    @Test public void editedPortuguesePhrasesPersistAcrossActivityRecreation() throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
        Method edit=MainActivity.class.getDeclaredMethod("editPhrases"); edit.setAccessible(true); edit.invoke(activity);
        // Dialog dispatches onShow asynchronously; allow its button handler to attach.
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        List<EditText> inputs=new ArrayList<>(); editTexts(dialog.getWindow().getDecorView(),inputs);
        assertEquals(2,inputs.size());
        inputs.get(0).setText("Peixe na linha"); inputs.get(1).setText("Pode salvar");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals("Peixe na linha",activity.getSharedPreferences("local_settings",0).getString("start_phrase",null));
        assertEquals("Pode salvar",activity.getSharedPreferences("local_settings",0).getString("stop_phrase",null));
        MainActivity recreated=Robolectric.buildActivity(MainActivity.class).create().get();
        Field start=MainActivity.class.getDeclaredField("startPhrase"); start.setAccessible(true);
        assertEquals("Peixe na linha",start.get(recreated));
    }
    @Test public void actualButtonsAndRecFollowCaptureAndExport() throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
        Field field=MainActivity.class.getDeclaredField("cameraLoop"); field.setAccessible(true);
        CaptureState state=((CameraLoop)field.get(activity)).state();
        Method refresh=MainActivity.class.getDeclaredMethod("refreshButtons"); refresh.setAccessible(true);
        Button mark=activity.findViewById(R.id.mark_moment), stop=activity.findViewById(R.id.finish_save);
        TextView rec=activity.findViewById(R.id.rec_indicator);
        refresh.invoke(activity); assertFalse(mark.isEnabled()); assertFalse(stop.isEnabled());
        state.ready(); refresh.invoke(activity);
        assertTrue(mark.isEnabled()); assertEquals("HISTÓRICO ATIVO",rec.getText().toString());
        state.mark(SystemClock.elapsedRealtime()); refresh.invoke(activity);
        assertFalse(mark.isEnabled()); assertTrue(stop.isEnabled());
        assertTrue(rec.getText().toString().startsWith("REC "));
        ShadowSystemClock.advanceBy(Duration.ofSeconds(65)); refresh.invoke(activity);
        assertEquals("REC 00:01:05",rec.getText().toString());
        Field dotField=MainActivity.class.getDeclaredField("recDot"); dotField.setAccessible(true);
        TextView dot=(TextView)dotField.get(activity); float alpha=dot.getAlpha();
        ShadowSystemClock.advanceBy(Duration.ofMillis(500)); refresh.invoke(activity);
        assertNotEquals(alpha,dot.getAlpha(),0.01f);
        state.beginSaving(); refresh.invoke(activity);
        assertFalse(mark.isEnabled()); assertFalse(stop.isEnabled());
        assertEquals("SALVANDO…",rec.getText().toString());
        state.saved(); refresh.invoke(activity); assertEquals("VÍDEO SALVO",rec.getText().toString());
        state.error(); refresh.invoke(activity); assertEquals("CAPTURA PAUSADA",rec.getText().toString());
        assertTrue(mark.isEnabled()); assertEquals("RETOMAR CÂMERA",mark.getText().toString());
    }
}
