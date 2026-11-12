package br.com.voltadolance;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/**
 * Experimento local: não usa reconhecedor remoto e não exige INTERNET.
 * Atenção: alguns aparelhos não permitem reconhecimento e MediaRecorder simultâneos.
 */
public final class OnDeviceVoice {
    public interface Callback { void onMark(); void onFinish(); void onUnavailable(); }
    private final Activity activity;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean enabled;
    private boolean capturing;
    private int failures;
    private VoiceCommands commands = new VoiceCommands("Marcar momento", "Encerrar vídeo");
    private final Runnable restart = this::listen;
    public OnDeviceVoice(Activity activity, Callback callback) {
        this.activity = activity; this.callback = callback;
    }
    public void setPhrases(String start, String stop) { commands = new VoiceCommands(start, stop); }
    public boolean enable() {
        if (enabled) return true;
        try {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
            callback.onUnavailable(); return false;
        }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
        enabled = true;
        failures = 0;
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                capturing = false;
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && ++failures >= 5) {
                    disable(); callback.onUnavailable(); return;
                }
                if (enabled) handler.postDelayed(restart, 900);
            }
            @Override public void onResults(Bundle results) {
                capturing = false;
                failures = 0;
                consume(results);
                if (enabled) handler.postDelayed(restart, 500);
            }
            // Only final results: partial hypotheses may be retracted or repeated.
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int type, Bundle params) { }
        });
        listen();
        return enabled;
        } catch (RuntimeException e) { disable(); callback.onUnavailable(); return false; }
    }
    private void listen() {
        if (!enabled || capturing || recognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try { recognizer.startListening(intent); capturing = true; }
        catch (Exception e) {
            capturing = false;
            if (++failures >= 5) { disable(); callback.onUnavailable(); }
            else handler.postDelayed(restart, 1400);
        }
    }
    private void consume(Bundle data) {
        if (!enabled || data == null) return;
        ArrayList<String> texts = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null) return;
        if (texts.isEmpty()) return;
        // Use the best hypothesis, never a lower-confidence alternative.
        VoiceCommands.Command command = commands.match(texts.get(0));
        if (command == VoiceCommands.Command.FINISH) callback.onFinish();
        else if (command == VoiceCommands.Command.MARK) callback.onMark();
    }
    public void disable() {
        enabled = false;
        handler.removeCallbacks(restart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        capturing = false;
    }
}
