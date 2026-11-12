package br.com.voltadolance;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-insensitive, whole-token phrases; the finish command has priority. */
public final class VoiceCommands {
    public enum Command { NONE, MARK, FINISH }
    private final String start;
    private final String stop;
    private final String[] grammar;
    public VoiceCommands(String start, String stop) {
        this.start = normalize(start); this.stop = normalize(stop);
        grammar = new String[]{grammarPhrase(start), grammarPhrase(stop), "encerrar", "[unk]"};
        if (this.start.isEmpty() || this.stop.isEmpty() || this.start.equals(this.stop)
                || start.length() > 80 || stop.length() > 80
                || (" " + this.start + " ").contains(" encerrar "))
            throw new IllegalArgumentException("Use duas frases diferentes, até 80 caracteres. Reserve “encerrar” para finalizar.");
    }
    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static String grammarPhrase(String value) {
        return value == null ? "" : Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    String[] grammarPhrases() { return grammar.clone(); }
    public Command match(String utterance) {
        String value = normalize(utterance);
        String padded = " " + value + " ";
        return (padded.contains(" " + stop + " ") || padded.contains(" encerrar ")) ? Command.FINISH
                : padded.contains(" " + start + " ") ? Command.MARK : Command.NONE;
    }
}
