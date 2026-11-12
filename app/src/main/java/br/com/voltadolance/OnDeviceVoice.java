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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

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
    private final Runnable restart = this::listen;
    public OnDeviceVoice(Activity activity, Callback callback) {
        this.activity = activity; this.callback = callback;
    }
    public boolean enable() {
        if (enabled) return true;
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
            callback.onUnavailable(); return false;
        }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
        enabled = true;
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                capturing = false;
                if (enabled) handler.postDelayed(restart, 900);
            }
            @Override public void onResults(Bundle results) {
                capturing = false;
                consume(results);
                if (enabled) handler.postDelayed(restart, 500);
            }
            @Override public void onPartialResults(Bundle partialResults) { consume(partialResults); }
            @Override public void onEvent(int type, Bundle params) { }
        });
        listen();
        return true;
    }
    private void listen() {
        if (!enabled || capturing || recognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try { recognizer.startListening(intent); capturing = true; }
        catch (Exception e) { capturing = false; handler.postDelayed(restart, 1400); }
    }
    private long lastCommandAt;
    private void consume(Bundle data) {
        if (data == null) return;
        ArrayList<String> texts = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null) return;
        for (String text : texts) {
            String t = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").trim();
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCommandAt < 2200) return;
            if (t.contains("encerrar video") || t.contains("finalizar video") || t.contains("parar video")) {
                lastCommandAt = now; callback.onFinish(); return;
            }
            if (t.contains("marcar momento") || t.contains("guardar lance")) {
                lastCommandAt = now; callback.onMark(); return;
            }
        }
    }
    public void disable() {
        enabled = false;
        handler.removeCallbacks(restart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            recognizer.destroy(); recognizer = null;
        }
        capturing = false;
    }
}
