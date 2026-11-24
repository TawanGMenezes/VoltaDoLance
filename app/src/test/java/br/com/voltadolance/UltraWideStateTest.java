package br.com.voltadolance;

import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.util.Range;
import android.view.TextureView;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UltraWideStateTest {
    private CameraLenses.Lens lens(){return new CameraLenses.Lens("rear","wide",.5f,Collections.singletonList(new CameraModes.Mode(1280,720,30,new Range<>(30,30))),null);}
    private void set(CameraLoop c,String name,Object value)throws Exception{java.lang.reflect.Field f=CameraLoop.class.getDeclaredField(name);f.setAccessible(true);f.set(c,value);}
    private CameraLoop loop(){Activity a=Robolectric.buildActivity(Activity.class).create().get();return new CameraLoop(a,new TextureView(a),new CameraLoop.Listener(){public void onStatus(String s){}public void onSaved(Uri u){}public void onError(String s){}});}
    @Test public void separateWideAddsRealRatioButDoesNotDuplicateLogicalUltraZoom(){
        assertArrayEquals(new float[]{.5f,1f,2f},CameraLoop.lensChoices(new float[]{1,2},lens()),0);
        assertArrayEquals(new float[]{.6f,1f,2f},CameraLoop.lensChoices(new float[]{.6f,1,2},lens()),0);
        assertArrayEquals(new float[]{1f,2f},CameraLoop.lensChoices(new float[]{1,2},null),0);
    }
    @Test public void sourceChangeIsBlockedDuringMarkedVideoButPrimaryLiveZoomRemains()throws Exception{
        CameraLoop c=loop();try{set(c,"ultraWide",lens());c.state().ready();assertTrue(c.canSelectZoom(.5f));
            c.state().mark(1);assertFalse(c.canSelectZoom(.5f));assertTrue(c.canSelectZoom(2));
            set(c,"activeLens",lens());assertFalse(c.canSelectZoom(1));assertTrue(c.canSelectZoom(.5f));
            c.state().beginSaving();assertFalse(c.canSelectZoom(.5f));assertFalse(c.canSelectZoom(1));
        }finally{c.release();}
    }
    @Test public void frontAndDualNeverOfferSeparateRearLens()throws Exception{
        CameraLoop c=loop();try{set(c,"ultraWide",lens());set(c,"facing",CameraCharacteristics.LENS_FACING_FRONT);assertArrayEquals(new float[]{1},c.zoomChoices(),0);
            set(c,"facing",CameraCharacteristics.LENS_FACING_BACK);c.setDualVideo(true);assertArrayEquals(new float[]{1},c.zoomChoices(),0);
        }finally{c.release();}
    }
    @Test public void rollbackRestoresThePriorPhysicalSourceAndZoom()throws Exception{
        CameraLoop c=loop();try{CameraLenses.Lens previous=lens();set(c,"rollbackFacing",CameraCharacteristics.LENS_FACING_BACK);set(c,"rollbackMode","1280x720@30");set(c,"rollbackLens",previous);set(c,"rollbackZoom",1f);set(c,"activityActive",false);
            java.lang.reflect.Method fail=CameraLoop.class.getDeclaredMethod("fail",String.class);fail.setAccessible(true);fail.invoke(c,"Falha na troca");assertEquals(.5f,c.zoomRatio(),0);assertEquals("Ultra-angular",c.lensLabel());assertFalse(c.isRunning());
        }finally{c.release();}
    }
}
