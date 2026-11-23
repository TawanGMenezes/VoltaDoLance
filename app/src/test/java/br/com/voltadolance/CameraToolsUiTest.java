package br.com.voltadolance;

import android.Manifest;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;
import android.view.TextureView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class CameraToolsUiTest {
    @Before public void permissionsDenied(){
        AlphaNotice.accept(RuntimeEnvironment.getApplication());
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
        RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0).edit().clear().commit();
    }
    private Object field(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private void set(Object target,String name,Object value)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private void refresh(MainActivity activity)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("refreshButtons");m.setAccessible(true);m.invoke(activity);
    }
    @Test public void supportedZoomChoicesRefreshWithoutRebuildingAndFollowCapturePhase()throws Exception{
        MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
        try {
            CameraLoop loop=(CameraLoop)field(a,"cameraLoop");
            set(loop,"tuning",new CameraTuning(.6f,4f,true,new Rect(0,0,4000,3000),true,false,1280,720,30));
            refresh(a);LinearLayout row=(LinearLayout)field(a,"zoomChoicesRow");assertEquals(3,row.getChildCount());
            Button wide=(Button)row.getChildAt(0);assertEquals("0.6×",wide.getText().toString());assertFalse(wide.isEnabled());
            float density=a.getResources().getDisplayMetrics().density;assertTrue(wide.getMinimumWidth()>=48*density-1);assertTrue(wide.getMinimumHeight()>=48*density-1);
            loop.state().ready();refresh(a);assertSame(wide,row.getChildAt(0));assertTrue(wide.isEnabled());assertTrue(row.getChildAt(1).isSelected());
            loop.state().mark(1);refresh(a);assertTrue(wide.isEnabled());
            loop.state().beginSaving();refresh(a);assertFalse(wide.isEnabled());
            set(loop,"tuning",new CameraTuning(1f,1f,true,null,false,false,1280,720,30));refresh(a);
            assertEquals(1,row.getChildCount());assertEquals("1×",((Button)row.getChildAt(0)).getText().toString());
            assertEquals(loop.stabilizationLabel(),((TextView)field(a,"stabilizationHint")).getText().toString());
        } finally {a.onDestroy();}
    }
    @Test public void dualPreviewReservesToolsAndControlsAndFitsAspect()throws Exception{
        MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
        try {
            CameraLoop loop=(CameraLoop)field(a,"cameraLoop");loop.setDualVideo(true);refresh(a);
            FrameLayout root=(FrameLayout)field(a,"previewRoot");
            for(int i=0;i<4;i++){root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,1920);}
            TextureView preview=(TextureView)field(a,"preview");View tools=(View)field(a,"captureToolsPanel"),controls=(View)field(a,"bottomControls");
            assertTrue(preview.getTop()>=tools.getBottom());assertTrue(preview.getBottom()<=controls.getTop());assertTrue(preview.getHeight()>0);
            assertTrue(controls.getHeight()<=root.getHeight()/3+1);
            float[] values=new float[9];preview.getTransform(new Matrix()).getValues(values);
            assertEquals(1f,values[Matrix.MSCALE_X],0f);assertEquals(1f,values[Matrix.MSCALE_Y],0f);
            // GPU compositor owns aspect fitting; TextureView must not fit a second time.
            loop.setDualVideo(false);refresh(a);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)preview.getLayoutParams();assertEquals(0,lp.topMargin);assertEquals(0,lp.bottomMargin);
        } finally {a.onDestroy();}
    }
}
