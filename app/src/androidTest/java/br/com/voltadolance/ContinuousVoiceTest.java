package br.com.voltadolance;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.Assert.*;

/** Real Camera2/AVC/AAC and real bundled Portuguese decoder; fixtures replace speech acoustics only. */
@RunWith(AndroidJUnit4.class)
public class ContinuousVoiceTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
    private ActivityScenario<MainActivity> scenario;
    private static Object field(Object owner,String name) {
        try {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
        catch(Exception e) {throw new AssertionError(e);}
    }
    private <T> T ui(Function<MainActivity,T> action) {
        AtomicReference<T> result=new AtomicReference<>(); scenario.onActivity(a->result.set(action.apply(a))); return result.get();
    }
    private void await(String reason,java.util.function.BooleanSupplier condition,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline) {if(condition.getAsBoolean())return;Thread.sleep(100);}
        fail(reason+"; state="+ui(a->((TextView)a.findViewById(R.id.rec_indicator)).getText().toString())+
                "; status="+ui(a->((TextView)field(a,"stateLabel")).getText().toString())+
                "; voice="+ui(a->(String)field(a,"voiceStatus")));
    }
    private void speech(OnDeviceVoice decoder,String file) throws Exception {
        Context tests=InstrumentationRegistry.getInstrumentation().getContext();
        try(InputStream input=tests.getAssets().open(file)) {
            byte[] chunk=new byte[3200];int length;
            while((length=input.read(chunk))!=-1) {decoder.onPcm(chunk,length);Thread.sleep(100);}
        }
    }
    private void ambientSilence(OnDeviceVoice decoder,long milliseconds) throws Exception {
        byte[] silence=new byte[3200];long until=SystemClock.elapsedRealtime()+milliseconds;
        while(SystemClock.elapsedRealtime()<until) {decoder.onPcm(silence,silence.length);Thread.sleep(100);}
    }
    private Uri newest(Context context) {
        try(Cursor c=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Video.Media._ID},MediaStore.Video.Media.RELATIVE_PATH+" LIKE ?",
                new String[]{"Movies/VoltaDoLance/%"},MediaStore.Video.Media.DATE_ADDED+" DESC, "+MediaStore.Video.Media._ID+" DESC")) {
            return c!=null && c.moveToFirst()?ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(0)):null;
        }
    }
    @Test public void enabledActivityVoiceActuallyReceivesTheRecordersMicrophone() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.getSharedPreferences("local_settings",0).edit().clear().putBoolean("voice",true)
                .putInt("past",15).putString("video_mode","1280x720@30").putInt("notice_accepted_version",AlphaNotice.VERSION).commit();
        scenario=ActivityScenario.launch(MainActivity.class);
        try {
            await("Production shared microphone did not reach activity-owned voice",()->ui(a->
                    ((OnDeviceVoice)field(a,"voice")).isReady() &&
                    ((String)field(a,"voiceStatus")).contains("ouvindo comandos")),90000);
            assertTrue(target.getSharedPreferences("local_settings",0).getBoolean("voice",false));
            ui(a->{((Button)field(a,"pauseButton")).performClick();return null;});
            await("Pause did not disable microphone and voice",()->ui(a->
                    ((CameraLoop)field(a,"cameraLoop")).state().phase()==CaptureState.Phase.STOPPED &&
                    !((OnDeviceVoice)field(a,"voice")).isReady()),10000);
        } finally { scenario.close(); }
    }
    private long[] historyBounds(Object engine) throws Exception {
        Object packets=field(engine,"packets");
        Method method=packets.getClass().getDeclaredMethod("snapshot");method.setAccessible(true);
        List<?> samples=(List<?>)method.invoke(packets);
        assertFalse(samples.isEmpty());
        return new long[]{(Long)field(samples.get(0),"ptsUs"),(Long)field(samples.get(samples.size()-1),"ptsUs")};
    }
    @Test public void actualPortugueseSpeechDrivesRecAndSaveAcrossContinuousHistory() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.getSharedPreferences("local_settings",0).edit().clear().putBoolean("voice",false)
                .putInt("past",15).putString("video_mode","1280x720@30")
                .putString("start_phrase","peixe na linha").putString("stop_phrase","pode salvar").putInt("notice_accepted_version",AlphaNotice.VERSION).commit();
        Uri before=newest(target);
        scenario=ActivityScenario.launch(MainActivity.class);
        OnDeviceVoice decoder=null;
        try {
            await("Camera/encoders did not become ready",()->ui(a->((CameraLoop)field(a,"cameraLoop")).state().canMark()),45000);
            Object engine=ui(a->field(field(a,"cameraLoop"),"recorder"));
            Object session=ui(a->field(field(a,"cameraLoop"),"session"));
            assertNotNull(engine); assertNotNull(session);
            // Use production callbacks with controlled PCM. Actual recorder still owns the only microphone.
            decoder=ui(a->{
                OnDeviceVoice original=(OnDeviceVoice)field(a,"voice");
                OnDeviceVoice.Callback delegate=(OnDeviceVoice.Callback)field(original,"callback");
                OnDeviceVoice controlled=new OnDeviceVoice(a,new OnDeviceVoice.Callback() {
                    public void onMark() {delegate.onMark();}
                    public void onFinish() {delegate.onFinish();}
                    public void onUnavailable() {delegate.onUnavailable();}
                    public void onState(String value) {android.util.Log.i("VoiceFixture",value);delegate.onState(value);}
                });
                controlled.setPhrases("peixe na linha","pode salvar");controlled.enable();return controlled;
            });
            final OnDeviceVoice readyDecoder=decoder;
            await("Bundled Portuguese model did not load",readyDecoder::isReady,60000);
            long initialStart=historyBounds(engine)[0];
            // The real recorder streams ambient audio continuously before a user speaks.
            // Exercise that same warm-up instead of beginning a cold decoder at the first phoneme.
            ambientSilence(decoder,20000);
            long[] retained=historyBounds(engine);
            assertTrue("unmarked history retained old startup video",retained[0]>initialStart+2_000_000);
            assertTrue("unmarked history grew beyond selected window",retained[1]-retained[0]<17_000_000);
            speech(decoder,"start.pcm");
            await("Spoken custom start did not show REC",()->ui(a->((CameraLoop)field(a,"cameraLoop")).state().isRecording()),15000);
            assertTrue(ui(a->((TextView)a.findViewById(R.id.rec_indicator)).getText().toString().startsWith("REC ")));
            assertFalse(ui(a->((Button)a.findViewById(R.id.mark_moment)).isEnabled()));
            // A real microphone keeps supplying ambient PCM between commands. Do not
            // represent ten seconds of silence by removing ten seconds of decoder input.
            ambientSilence(decoder,10500);
            assertSame(engine,ui(a->field(field(a,"cameraLoop"),"recorder")));
            assertSame(session,ui(a->field(field(a,"cameraLoop"),"session")));
            speech(decoder,"stop.pcm");
            await("Spoken custom finish did not save",()->{Uri now=newest(target);return now!=null && !now.equals(before);},45000);
            Uri saved=newest(target);assertNotNull(saved);
            File sample=new File(target.getFilesDir(),"continuity-sample.mp4");
            try(InputStream input=target.getContentResolver().openInputStream(saved);OutputStream output=new FileOutputStream(sample)) {
                byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
            }
            verifyTimeline(sample,target);
            // Screen capture/launcher cleanup is visual QA, not part of the camera
            // continuity workload. Collect it only after the saved MP4 was verified.
            SettingsScreensTest.screenshot("camera-saved");
        } finally {
            if(decoder!=null)decoder.close();
            if(scenario!=null)scenario.close();
        }
    }
    private void verifyTimeline(File video,Context target) throws Exception {
        MediaExtractor extractor=new MediaExtractor();extractor.setDataSource(video.getAbsolutePath());
        StringBuilder report=new StringBuilder(); int audioTracks=0,videoTracks=0;
        try {
            for(int track=0;track<extractor.getTrackCount();track++) {
                String mime=extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME);
                boolean image=mime.startsWith("video/");
                if(image)videoTracks++;else if(mime.startsWith("audio/"))audioTracks++;else continue;
                extractor.selectTrack(track);extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                long first=-1,last=-1,maxGap=0;int frames=0;
                while(extractor.getSampleTrackIndex()>=0) {
                    long pts=extractor.getSampleTime();
                    if(first<0)first=pts;
                    if(last>=0){assertTrue("non-monotonic "+mime,pts>last);maxGap=Math.max(maxGap,pts-last);}
                    last=pts;frames++;if(!extractor.advance())break;
                }
                report.append(mime).append(" frames=").append(frames).append(" firstUs=").append(first)
                        .append(" durationUs=").append(last-first).append(" maxGapUs=").append(maxGap).append('\n');
                assertTrue("expected history + uninterrupted event: "+report,last-first>=15_000_000);
                assertTrue("unexpected absolute clock in MP4: "+report,first<1_000_000);
                assertTrue("long timeline gap: "+report,maxGap<(image?350000:250000));
                extractor.unselectTrack(track);
            }
        } finally {
            extractor.release();
            android.util.Log.i("ContinuityTimeline",report.toString());
            try(FileWriter writer=new FileWriter(new File(target.getFilesDir(),"continuity-report.txt"))) {writer.write(report.toString());}
        }
        assertEquals(1,videoTracks);assertEquals(1,audioTracks);
    }
}
