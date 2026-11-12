package br.com.voltadolance;

import android.widget.Button;
import android.widget.TextView;
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
        state.mark(0); refresh.invoke(activity);
        assertFalse(mark.isEnabled()); assertTrue(stop.isEnabled());
        assertTrue(rec.getText().toString().startsWith("REC "));
        state.beginSaving(); refresh.invoke(activity);
        assertFalse(mark.isEnabled()); assertFalse(stop.isEnabled());
        assertEquals("SALVANDO…",rec.getText().toString());
        state.saved(); refresh.invoke(activity); assertEquals("VÍDEO SALVO",rec.getText().toString());
        state.error(); refresh.invoke(activity); assertEquals("CAPTURA PAUSADA",rec.getText().toString());
    }
}
