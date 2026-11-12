package br.com.voltadolance;

import android.app.Activity;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class OnDeviceVoiceTest {
    @Test public void dispatchesOnlyBestFinalResultAndIgnoresResultsAfterDisable() throws Exception {
        int[] commands={0,0};
        OnDeviceVoice voice=new OnDeviceVoice(Robolectric.buildActivity(Activity.class).create().get(),new OnDeviceVoice.Callback() {
            public void onMark() { commands[0]++; }
            public void onFinish() { commands[1]++; }
            public void onUnavailable() { }
        });
        voice.setPhrases("peixe na linha","pode salvar");
        Field enabled=OnDeviceVoice.class.getDeclaredField("enabled"); enabled.setAccessible(true); enabled.set(voice,true);
        Method consume=OnDeviceVoice.class.getDeclaredMethod("consume",Bundle.class); consume.setAccessible(true);
        Bundle result=new Bundle();
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(List.of("outra conversa","peixe na linha")));
        consume.invoke(voice,result); assertEquals(0,commands[0]);
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(List.of("PEIXE NA LINHA!")));
        consume.invoke(voice,result); assertEquals(1,commands[0]);
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(List.of("pode salvar")));
        consume.invoke(voice,result); assertEquals(1,commands[1]);
        voice.disable(); consume.invoke(voice,result); assertEquals(1,commands[1]);
    }
}
