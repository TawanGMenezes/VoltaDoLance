package br.com.voltadolance;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraLensesTest {
    private CameraLenses.Lens lens(String id, String physical, float ratio, boolean video) {
        return new CameraLenses.Lens(id, physical, ratio,
                video ? Arrays.asList(new CameraModes.Mode(1920,1080,30,null)) : Collections.emptyList(), null);
    }
    @Test public void comparesFieldOfViewRatherThanRawFocalLength() {
        assertEquals(.5f, CameraLenses.relativeRatio(2,4,3,4,4,3), .0001f);
        // A small macro sensor with a shorter focal length is not an ultrawide.
        assertEquals(1f, CameraLenses.relativeRatio(2,2,1.5f,4,4,3), .0001f);
    }
    @Test public void rejectsMissingOrInvalidMetadata() {
        assertTrue(Float.isNaN(CameraLenses.relativeRatio(0,4,3,4,4,3)));
        assertTrue(Float.isNaN(CameraLenses.relativeRatio(2,0,3,4,4,3)));
        assertTrue(Float.isNaN(CameraLenses.relativeRatio(2,4,3,Float.NaN,4,3)));
    }
    @Test public void requiresWiderFieldAndVideoModes() {
        assertNull(CameraLenses.choose(Arrays.asList(lens("macro",null,1,true),lens("near",null,.85f,true),lens("blocked",null,.5f,false))));
        assertNull(CameraLenses.choose(Arrays.asList(lens("missing",null,Float.NaN,true))));
        assertEquals("wide",CameraLenses.choose(Arrays.asList(lens("wide",null,.6f,true))).cameraId);
    }
    @Test public void prefersIndependentRouteForSamePhysicalCamera() {
        CameraLenses.Lens picked=CameraLenses.choose(Arrays.asList(lens("logical","wide",.5f,true),lens("wide",null,.5f,true)));
        assertEquals("wide",picked.cameraId);
        assertFalse(picked.requiresSessionValidation());
        assertTrue(lens("logical","wide",.5f,true).requiresSessionValidation());
    }
}
