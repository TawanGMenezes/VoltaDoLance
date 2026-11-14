package br.com.voltadolance;

import android.Manifest;
import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.util.Range;
import android.view.Surface;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** GPU + real AVC/AAC proof; requires no concurrent-camera hardware or camera frames. */
@RunWith(AndroidJUnit4.class)
public class DualFrameComposerTest {
 @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
 private static CameraModes.Mode mode(int w,int h){return new CameraModes.Mode(w,h,30,new Range<>(30,30));}
 private static void paint(Surface surface,int color){Canvas c=surface.lockCanvas(null);try{c.drawColor(color);}finally{surface.unlockCanvasAndPost(c);}}
 private static void frames(DualFrameComposer composer,long millis,AtomicReference<String> failure)throws Exception{
  long end=SystemClock.elapsedRealtime()+millis;
  while(SystemClock.elapsedRealtime()<end){assertNull(failure.get());long start=SystemClock.elapsedRealtime();paint(composer.frontSurface(),Color.GREEN);paint(composer.rearSurface(),Color.RED);long delay=33-(SystemClock.elapsedRealtime()-start);if(delay>0)Thread.sleep(delay);}
 }
 @Test public void savedPortraitIncludesBothFeedsAndPremarkHistory()throws Exception{
  android.content.Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
  target.getSharedPreferences("local_settings",0).edit().clear().putBoolean("voice",false).putBoolean("dual_video",false).putInt("notice_accepted_version",AlphaNotice.VERSION).commit();
  AtomicReference<MainActivity> activity=new AtomicReference<>();
  AtomicReference<String> failure=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);
  AtomicInteger pcmBytes=new AtomicInteger();
  ContinuousRecorder recorder=null;DualFrameComposer composer=null;
  File saved=new File(target.getFilesDir(),"dual-composer-proof.mp4");
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   // Pause the ordinary activity-owned camera/mic before allocating the synthetic recorder.
   scenario.onActivity(a->{activity.set(a);try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("cameraLoop");field.setAccessible(true);((CameraLoop)field.get(a)).pauseHistory();}catch(Exception e){throw new AssertionError(e);}});
   Thread.sleep(1500);
   try{
    recorder=new ContinuousRecorder(activity.get(),mode(480,640),0,3,new ContinuousRecorder.Listener(){public void onReady(){ready.countDown();}public void onPcm(byte[] bytes,int length){pcmBytes.addAndGet(length);}public void onError(String message){failure.compareAndSet(null,message);}});
    composer=new DualFrameComposer(recorder.inputSurface(),null,480,640,mode(640,480),mode(320,240),90,270,false,message->failure.compareAndSet(null,message));
    recorder.start();
    long deadline=SystemClock.elapsedRealtime()+12000;
    while(ready.getCount()!=0&&SystemClock.elapsedRealtime()<deadline)frames(composer,100,failure);
    assertEquals("Recorder never became ready: "+failure.get(),0,ready.getCount());
    frames(composer,2300,failure);assertTrue(recorder.mark(3));frames(composer,1700,failure);
    composer.close();composer.close();composer=null;
    recorder.finish(saved);recorder=null;assertNull(failure.get());
    assertTrue("Shared microphone did not dispatch PCM",pcmBytes.get()>0);
    verify(saved);
   }finally{if(composer!=null)composer.close();if(recorder!=null)recorder.discard();}
  }
 }
 private static void verify(File saved)throws Exception{
  MediaMetadataRetriever reader=new MediaMetadataRetriever();
  try{
   reader.setDataSource(saved.getAbsolutePath());assertEquals("0",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
   Bitmap image=reader.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);assertNotNull(image);
   try{assertEquals(480,image.getWidth());assertEquals(640,image.getHeight());
    red(image.getPixel(240,320));red(image.getPixel(40,40));red(image.getPixel(400,240));
    int inset=image.getPixel(394,90);assertTrue("Inset was not green: "+Integer.toHexString(inset),Color.green(inset)>170&&Color.red(inset)<80&&Color.blue(inset)<80);
    // Rounded extreme corner retains the red rear, and the straight inset edge is white.
    red(image.getPixel(327,20));int border=image.getPixel(394,20);assertTrue("Missing white border",Color.red(border)>170&&Color.green(border)>170&&Color.blue(border)>170);
   }finally{image.recycle();}
  }finally{reader.release();}
  MediaExtractor extractor=new MediaExtractor();int video=0,audio=0;
  try{extractor.setDataSource(saved.getAbsolutePath());
   for(int track=0;track<extractor.getTrackCount();track++){
    String mime=extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME);if(mime.startsWith("video/"))video++;else if(mime.startsWith("audio/"))audio++;else continue;
    extractor.selectTrack(track);extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);long first=-1,last=-1;int count=0;
    while(extractor.getSampleTrackIndex()>=0){long pts=extractor.getSampleTime();if(first<0)first=pts;assertTrue("Non-monotonic "+mime,pts>last);last=pts;count++;if(!extractor.advance())break;}
    assertTrue("Premark history missing from "+mime,last-first>3_000_000L);assertTrue("Too few samples "+mime,count>30);extractor.unselectTrack(track);
   }
  }finally{extractor.release();}
  assertEquals(1,video);assertEquals(1,audio);
 }
 private static void red(int pixel){assertTrue("Expected red rear: "+Integer.toHexString(pixel),Color.red(pixel)>170&&Color.green(pixel)<90&&Color.blue(pixel)<90);}
}
