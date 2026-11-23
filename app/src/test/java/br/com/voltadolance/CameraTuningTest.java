package br.com.voltadolance;

import android.graphics.Rect;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class CameraTuningTest {
    private CameraTuning tuning(float min, float max, boolean ratio, boolean video, boolean optical, int w, int h, int fps) {
        return new CameraTuning(min, max, ratio, new Rect(100, 200, 4100, 3200), video, optical, w, h, fps);
    }
    @Test public void exposesOnlySupportedShortcutsAndPreservesUltrawideMinimum() {
        assertArrayEquals(new float[]{0.6f,1f,2f}, tuning(.6f,8,true,true,true,1920,1080,30).choices(),0f);
        assertArrayEquals(new float[]{.7f,1f,2f}, tuning(.7f,8,true,true,true,1920,1080,30).choices(),0f);
        assertArrayEquals(new float[]{1f}, tuning(1,1.5f,false,false,false,1280,720,30).choices(),0f);
    }
    @Test public void clampsUnavailableAndInvalidZoom() {
        CameraTuning t=tuning(.7f,4,true,false,false,1920,1080,30);
        assertEquals(.7f,t.clampZoom(.6f),0f);
        assertEquals(4,t.clampZoom(8),0f);
        assertEquals(1,t.clampZoom(Float.NaN),0f);
    }
    @Test public void legacyCropCannotZoomOutAndKeepsSensorOrigin() {
        CameraTuning t=tuning(.6f,4,false,false,false,1920,1080,30);
        assertEquals(1,t.clampZoom(.6f),0f);
        assertEquals(new Rect(1100,950,3100,2450),t.cropRegion(2));
    }
    @Test public void usesVideoOnlyInsideGuaranteedRecordingEnvelope() {
        CameraTuning t=tuning(1,4,true,true,true,1920,1080,30);
        assertEquals(CameraTuning.Stabilization.VIDEO,t.stabilization());
        assertEquals(CameraTuning.Stabilization.OPTICAL,tuning(1,4,true,true,true,3840,2160,30).stabilization());
        assertEquals(CameraTuning.Stabilization.OPTICAL,tuning(1,4,true,true,true,1920,1080,60).stabilization());
        assertEquals(CameraTuning.Stabilization.NONE,tuning(1,4,true,true,false,1920,1080,60).stabilization());
    }
    @Test public void rejectedVideoFallsBackOnceAndResetsUnconfirmedStatus() {
        CameraTuning t=tuning(1,4,true,true,true,1920,1080,30);
        assertTrue(t.disableVideoStabilization());
        assertEquals(CameraTuning.Stabilization.OPTICAL,t.stabilization());
        assertFalse(t.disableVideoStabilization());
        assertEquals("Estabilização: verificando",t.stabilizationLabel());
    }
    @Test public void summaryReportsResultsRatherThanRequestedMode() {
        CameraTuning t=tuning(1,4,true,true,true,1920,1080,30);
        t.observeModes(0,0);
        assertEquals("Estabilização: inativa",t.stabilizationLabel());
        t.observeModes(1,0);
        assertEquals("Estabilização: vídeo ativa",t.stabilizationLabel());
        t.observeModes(0,1);
        assertEquals("Estabilização: óptica ativa",t.stabilizationLabel());
        t.observeModes(null,null);
        assertEquals("Estabilização: não informada",t.stabilizationLabel());
    }
}
