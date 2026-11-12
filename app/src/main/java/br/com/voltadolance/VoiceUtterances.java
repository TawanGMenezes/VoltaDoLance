package br.com.voltadolance;

/** Dispatch only endpointed hypotheses: partial hypotheses can be retracted by Vosk. */
final class VoiceUtterances {
    private final VoiceCommands commands;
    VoiceUtterances(VoiceCommands commands) { this.commands = commands; }
    String[] grammarPhrases() { return commands.grammarPhrases(); }
    VoiceCommands.Command accept(String text, boolean isFinal) {
        return isFinal ? commands.match(text) : VoiceCommands.Command.NONE;
    }
}
