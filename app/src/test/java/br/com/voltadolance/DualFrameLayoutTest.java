package br.com.voltadolance;
import org.junit.Test;
import static org.junit.Assert.*;
public class DualFrameLayoutTest {
 @Test public void insetUsesTopRightPortraitCoordinates(){float[] r=DualFrameLayout.inset(1000,2000);assertArrayEquals(new float[]{680,1500,280,440},r,.01f);}
 @Test public void rotatedLandscapeCoversPortraitWithoutStretch(){float[] uv=DualFrameLayout.uv(640,480,480,640,90,false);assertArrayEquals(new float[]{1,0,1,1,0,0,0,1},uv,.001f);}
 @Test public void mirroringReversesDisplayHorizontalBeforeRotation(){float[] a=DualFrameLayout.uv(640,480,480,640,90,false),b=DualFrameLayout.uv(640,480,480,640,90,true);assertEquals(a[2],b[0],0);assertEquals(a[3],b[1],0);}
 @Test public void centerCropRemainsCentered(){float[] uv=DualFrameLayout.uv(1920,1080,100,100,0,false);assertEquals(.5f,(uv[0]+uv[2])/2,.001);assertEquals(1,uv[5],0);}
}
