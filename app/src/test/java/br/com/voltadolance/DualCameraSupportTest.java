package br.com.voltadolance;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Range;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DualCameraSupportTest {
    @Test @Config(sdk=29) public void preAndroidElevenCannotBeAvailable() {
        DualCameraSupport.Result result=DualCameraSupport.inspect(org.robolectric.RuntimeEnvironment.getApplication());
        assertFalse(result.available); assertNull(result.config); assertTrue(result.message.contains("Android 11"));
    }
    @Test public void missingPermissionCannotBeAvailable() {
        DualCameraSupport.Result result=DualCameraSupport.inspect(org.robolectric.RuntimeEnvironment.getApplication());
        assertFalse(result.available); assertNull(result.config); assertTrue(result.message.contains("Permita"));
    }
    private CameraModes.Mode m(int w,int h,int fps) { return new CameraModes.Mode(w,h,fps,new Range<>(fps,fps)); }
    @Test public void pairMustBeAdvertisedAndContainOppositeFacingCameras() {
        Map<String,Integer> facings=new HashMap<>();
        facings.put("rear",CameraCharacteristics.LENS_FACING_BACK);
        facings.put("front",CameraCharacteristics.LENS_FACING_FRONT);
        facings.put("tele",CameraCharacteristics.LENS_FACING_BACK);
        Set<Set<String>> sets=new HashSet<>();
        sets.add(new HashSet<>(Arrays.asList("rear","tele")));
        assertTrue(DualCameraSupport.frontBackPairs(sets,facings).isEmpty());
        sets.add(new HashSet<>(Arrays.asList("rear","front","tele")));
        List<String[]> pairs=DualCameraSupport.frontBackPairs(sets,facings);
        assertEquals(2,pairs.size());
        assertEquals("rear",pairs.get(0)[0]); assertEquals("front",pairs.get(0)[1]);
    }
    @Test public void unknownFacingAndEmptyAdvertisementsNeverInventAPair() {
        assertTrue(DualCameraSupport.frontBackPairs(Collections.emptySet(),Collections.emptyMap()).isEmpty());
        assertTrue(DualCameraSupport.frontBackPairs(Collections.singleton(new HashSet<>(Arrays.asList("0","1"))),Collections.emptyMap()).isEmpty());
    }
    @Test public void rearPrefersHdThenSmallerThirtyFpsOnlyWithThreeAttemptLimit() {
        List<CameraModes.Mode> modes=Arrays.asList(m(1920,1080,30),m(1280,720,60),m(320,240,30),m(960,540,30),m(1280,720,30),m(640,480,30));
        List<CameraModes.Mode> selected=DualCameraSupport.candidateModes(modes,false);
        assertEquals(3,selected.size()); assertEquals("1280x720@30",selected.get(0).key());
        assertEquals("960x540@30",selected.get(1).key()); assertEquals("640x480@30",selected.get(2).key());
    }
    @Test public void frontPrefersVgaAndDeduplicatesOnlyLandscape720pOrSmaller() {
        List<CameraModes.Mode> modes=Arrays.asList(m(1280,720,30),m(640,480,30),m(640,480,30),m(720,1280,30),m(1920,1080,30),m(320,240,30));
        List<CameraModes.Mode> selected=DualCameraSupport.candidateModes(modes,true);
        assertEquals(3,selected.size()); assertEquals("640x480@30",selected.get(0).key());
        assertEquals("1280x720@30",selected.get(1).key()); assertEquals("320x240@30",selected.get(2).key());
    }
}

