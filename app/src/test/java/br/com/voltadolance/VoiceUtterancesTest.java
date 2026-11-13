package br.com.voltadolance;
import org.junit.Test;
import static org.junit.Assert.*;
/** Final-only dispatch prevents speculative partial commands and partial/final duplicates. */
public class VoiceUtterancesTest {
 @Test public void waitsForFinalAndDispatchesOncePerUtterance() {
  VoiceUtterances u = new VoiceUtterances(new VoiceCommands("marcar momento","encerrar vídeo"));
  assertEquals(VoiceCommands.Command.NONE,u.accept("marcar momento",false));
  assertEquals(VoiceCommands.Command.NONE,u.accept("marcar momento",false));
  assertEquals(VoiceCommands.Command.NONE,u.accept("marcar momento e encerrar video",true));
  assertEquals(VoiceCommands.Command.NONE,u.accept("agora marcar momento",true));
  assertEquals(VoiceCommands.Command.MARK,u.accept("marcar momento",true));
  assertEquals(VoiceCommands.Command.FINISH,u.accept("encerrar video",true));
 }
}
