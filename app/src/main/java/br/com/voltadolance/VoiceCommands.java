package br.com.voltadolance;

import java.text.Normalizer;
import java.util.Locale;

/** Exact utterances prevent a conversation containing a command from activating it. */
public final class VoiceCommands {
    public enum Command { NONE, MARK, FINISH }
    private final String start;
    private final String stop;
    public VoiceCommands(String start, String stop) {
        this.start = normalize(start); this.stop = normalize(stop);
        if (this.start.isEmpty() || this.stop.isEmpty() || this.start.equals(this.stop)
                || start.length() > 80 || stop.length() > 80)
            throw new IllegalArgumentException("Use duas frases diferentes, com letras ou números e até 80 caracteres.");
    }
    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    public Command match(String utterance) {
        String value = normalize(utterance);
        return value.equals(stop) ? Command.FINISH : value.equals(start) ? Command.MARK : Command.NONE;
    }
}
