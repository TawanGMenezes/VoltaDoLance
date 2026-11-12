package br.com.voltadolance;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import org.json.JSONObject;
import org.json.JSONArray;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Native;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Offline Portuguese decoding of the recorder's shared 16 kHz mono PCM16 stream. */
public final class OnDeviceVoice implements AutoCloseable {
    public interface Callback {
        void onMark();
        void onFinish();
        void onUnavailable();
        default void onState(String state) { }
    }

    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoicePcmQueue pcm = new VoicePcmQueue(16);
    private volatile VoiceUtterances utterances = new VoiceUtterances(
            new VoiceCommands("Marcar momento", "Encerrar vídeo"));
    private volatile boolean enabled;
    private volatile boolean closed;
    private volatile boolean ready;
    private volatile long session;
    private Thread worker;

    public OnDeviceVoice(Activity activity, Callback callback) {
        this.context = activity.getApplicationContext();
        this.callback = callback;
    }

    public synchronized void setPhrases(String start, String stop) {
        utterances = new VoiceUtterances(new VoiceCommands(start, stop));
        // Discard speech begun with the previous settings, including queued UI callbacks.
        session++;
        ready = false;
        pcm.clear();
    }

    /** True means requested, even while the bundled model loads or a decoder retries. */
    public synchronized boolean enable() {
        if (closed) return false;
        if (enabled) return true;
        enabled = true;
        session++;
        ready = false;
        state(session, "Voz: carregando modelo local…");
        if (worker == null) {
            worker = new Thread(this::decode, "PortugueseVoice");
            worker.setDaemon(true);
            worker.start();
        }
        return true;
    }

    public boolean isReady() { return enabled && ready && !closed; }

    /** Called by the single shared microphone producer. Never performs native decoding. */
    public void onPcm(byte[] bytes, int length) {
        long current = session;
        if (isReady()) pcm.offer(bytes, length, current);
    }

    public synchronized void disable() {
        enabled = false;
        ready = false;
        session++;
        pcm.clear();
        handler.removeCallbacksAndMessages(null);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        disable();
        if (worker != null) worker.interrupt();
        // Native handles are released by their owning thread, never during acceptWaveForm.
    }

    private void state(long current, String message) {
        handler.post(() -> {
            if (!closed && enabled && session == current) callback.onState(message);
        });
    }

    private void decode() {
        Model model = null;
        File modelDirectory = null;
        Recognizer recognizer = null;
        long activeSession = -1;
        long retryAt = 0;
        boolean listening = false;
        boolean reportedUnavailable = false;
        try {
            while (!closed) {
                if (!enabled || SystemClock.elapsedRealtime() < retryAt) {
                    pcm.poll(200);
                    continue;
                }
                long current = session;
                try {
                    if (model == null) {
                        state(current, "Voz: carregando modelo local…");
                        modelDirectory = extractModel();
                        model = new Model(modelDirectory.getAbsolutePath());
                    }
                    if (closed) break;
                    if (!enabled || current != session) continue;

                    if (activeSession != current) {
                        closeRecognizer(recognizer);
                        recognizer = null;
                        String grammar = commandGrammar(model, utterances.grammarPhrases());
                        recognizer = new Recognizer(model, 16000.0f, grammar);
                        activeSession = current;
                        listening = false;
                        pcm.clear();
                        ready = true;
                        state(current, "Voz: pronta; aguardando áudio");
                    }
                    VoicePcmQueue.Chunk chunk = pcm.poll(200);
                    if (chunk == null || chunk.session != current || !enabled || session != current) continue;
                    if (chunk.discontinuity) {
                        recognizer.reset();
                        state(current, "Voz: áudio atrasou; escuta retomada");
                    }
                    boolean endpoint = recognizer.acceptWaveForm(chunk.bytes, chunk.bytes.length);
                    if (!listening) {
                        listening = true;
                        state(current, "Voz: ouvindo comandos");
                    }
                    // A partial hypothesis may change from MARK to FINISH. Only endpointed
                    // results dispatch, once per endpoint; getResult consumes that utterance.
                    if (endpoint) {
                        String text = new JSONObject(recognizer.getResult()).optString("text", "");
                        if (!text.isEmpty()) {
                            VoiceCommands.Command command = utterances.accept(text, true);
                            handler.post(() -> {
                                if (closed || !enabled || session != current) return;
                                callback.onState("Voz ouviu: “" + text + "”");
                                if (command == VoiceCommands.Command.FINISH) callback.onFinish();
                                else if (command == VoiceCommands.Command.MARK) callback.onMark();
                            });
                        }
                    }
                    reportedUnavailable = false;
                } catch (InterruptedException interrupted) {
                    throw interrupted;
                } catch (Exception | LinkageError failure) {
                    ready = false;
                    pcm.clear();
                    closeRecognizer(recognizer);
                    recognizer = null;
                    activeSession = -1;
                    retryAt = SystemClock.elapsedRealtime() + 5000;
                    String detail = failure.getMessage();
                    state(current, "Voz: erro local; nova tentativa em 5 s"
                            + (detail == null ? "" : " (" + detail + ")"));
                    if (!reportedUnavailable) {
                        handler.post(() -> {
                            if (!closed && enabled && session == current) callback.onUnavailable();
                        });
                        reportedUnavailable = true;
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            ready = false;
            pcm.clear();
            closeRecognizer(recognizer);
            if (model != null) model.close();
        }
    }

    /** The legacy model embeds word symbols inside its FSTs; it has no words.txt. */
    private static String commandGrammar(Model model, String[] phrases) throws IOException {
        // Official C API; the Java Model wrapper does not expose this operation.
        Function findWord = NativeLibrary.getInstance("vosk").getFunction("vosk_model_find_word");
        JSONArray grammar = new JSONArray();
        for (String phrase : phrases) {
            if (phrase.equals("[unk]")) { grammar.put(phrase); continue; }
            StringBuilder mapped = new StringBuilder();
            for (String word : phrase.split(" ")) {
                String symbol = word;
                if (findWord.invokeInt(new Object[]{model.getPointer(), Native.toByteArray(symbol, "UTF-8")}) < 0) {
                    symbol = VoiceCommands.normalize(word);
                    if (findWord.invokeInt(new Object[]{model.getPointer(), Native.toByteArray(symbol, "UTF-8")}) < 0)
                        throw new IOException("Palavra fora do modelo local: “" + word
                                + "”. Escolha outra frase nas configurações");
                }
                if (mapped.length() > 0) mapped.append(' ');
                mapped.append(symbol);
            }
            grammar.put(mapped.toString());
        }
        return grammar.toString();
    }
    private static void closeRecognizer(Recognizer recognizer) {
        if (recognizer != null) {
            try { recognizer.close(); } catch (RuntimeException | LinkageError ignored) { }
        }
    }

    private File extractModel() throws IOException {
        return VoiceModelFiles.prepare(context.getFilesDir(),
                staging -> copyAssets(context.getAssets(), "model-pt", staging));
    }
    private void copyAssets(AssetManager assets, String assetPath, File output) throws IOException {
        if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Voz encerrada");
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Não foi possível criar " + output);
            for (String child : children) copyAssets(assets, assetPath + "/" + child, new File(output, child));
        } else {
            try (InputStream input = assets.open(assetPath); FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Voz encerrada");
                    out.write(buffer, 0, count);
                }
            }
        }
    }

}
