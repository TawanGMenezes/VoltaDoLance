package br.com.voltadolance;
import android.util.Range;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class CameraSelectionTest {
 private CameraModes.Mode m(int w,int h,int fps){return new CameraModes.Mode(w,h,fps,new Range<>(fps,fps));}
 private int choose(List<CameraModes.Mode> modes,String requested)throws Exception{
  java.lang.reflect.Method method=null;for(java.lang.reflect.Method candidate:CameraModes.class.getDeclaredMethods())if(candidate.getName().equals("chooseModeIndex"))method=candidate;
  assertNotNull("Destination camera quality policy is missing",method);method.setAccessible(true);return(Integer)method.invoke(null,modes,requested);
 }
 @Test public void retainsHdWhenDestinationSupportsItEvenIfItSupportsHigher()throws Exception{assertEquals(1,choose(Arrays.asList(m(1920,1080,60),m(1280,720,30)),"1280x720@30"));}
 @Test public void fourKRearFallsBackToMaximumFrontMode()throws Exception{assertEquals(1,choose(Arrays.asList(m(1280,720,30),m(1920,1080,60),m(1920,1080,30)),"3840x2160@30"));}
 @Test public void retainsResolutionWhenOnlyFpsMustChange()throws Exception{assertEquals(0,choose(Arrays.asList(m(1920,1080,30),m(1280,720,60)),"1920x1080@60"));}
 @Test public void exactFpsWinsOverHigherRate()throws Exception{assertEquals(1,choose(Arrays.asList(m(1920,1080,60),m(1920,1080,30)),"1920x1080@30"));}
 @Test public void emptyDestinationIsNotSelectable()throws Exception{assertEquals(-1,choose(Collections.emptyList(),"3840x2160@30"));}
}
