package br.com.voltadolance;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceCommandsTest {
    @Test public void matchesPortugueseAccentsCaseAndPunctuation() {
        VoiceCommands commands = new VoiceCommands("Marcar momento", "Encerrar vídeo");
        assertEquals(VoiceCommands.Command.MARK, commands.match("  MARCAR, momento! "));
        assertEquals(VoiceCommands.Command.FINISH, commands.match("encerrar video"));
    }
    @Test public void requiresWholeUtteranceToAvoidAccidentalMarks() {
        VoiceCommands commands = new VoiceCommands("vai", "parar");
        assertEquals(VoiceCommands.Command.NONE, commands.match("não vai agora"));
        assertEquals(VoiceCommands.Command.NONE, commands.match("pararam"));
        assertEquals(VoiceCommands.Command.NONE, commands.match(null));
    }
    @Test public void rejectsEmptyIdenticalAndOversizedPhrases() {
        for (String[] pair : new String[][]{{"!!!", "fim"}, {"vídeo", "VIDEO"}, {"a".repeat(81), "fim"}}) {
            assertThrows(IllegalArgumentException.class, () -> new VoiceCommands(pair[0], pair[1]));
        }
    }
    @Test public void usesCustomPhrasesInsteadOfOldDefaults() {
        VoiceCommands commands = new VoiceCommands("peixe na linha", "pode salvar");
        assertEquals(VoiceCommands.Command.MARK, commands.match("Peixe na linha"));
        assertEquals(VoiceCommands.Command.NONE, commands.match("marcar momento"));
        assertEquals(VoiceCommands.Command.FINISH, commands.match("pode salvar"));
    }
}
