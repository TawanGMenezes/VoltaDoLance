package br.com.voltadolance;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoiceCommandsTest {
 @Test public void requiresEntireConfiguredPhraseWithoutExtraWords() {
  VoiceCommands c = new VoiceCommands("peixe na linha", "pode salvar");
  assertEquals(VoiceCommands.Command.MARK,c.match("PEIXE na linha!"));
  assertEquals(VoiceCommands.Command.FINISH,c.match("pode salvar"));
  assertEquals(VoiceCommands.Command.NONE,c.match("Agora, PEIXE na linha!"));
  assertEquals(VoiceCommands.Command.NONE,c.match("peixe na linha e pode salvar agora"));
  assertEquals(VoiceCommands.Command.NONE,c.match("salvar"));
  assertEquals(VoiceCommands.Command.NONE,c.match("peixe na"));
  assertEquals(VoiceCommands.Command.NONE,c.match("pode salvar agora"));
  assertEquals(VoiceCommands.Command.NONE,c.match("marcar momento"));
 }
 @Test public void normalizesAccentsButRespectsWholeTokens() {
  VoiceCommands c = new VoiceCommands("ação já", "parar");
  assertEquals(VoiceCommands.Command.MARK,c.match("ACAO, JA!"));
  assertEquals(VoiceCommands.Command.NONE,c.match("por favor ACAO, JA!"));
  assertEquals(VoiceCommands.Command.NONE,c.match("pararam"));
  assertEquals(VoiceCommands.Command.NONE,c.match(null));
 }
 @Test public void rejectsInvalidPhrases() {
  for(String[] p:new String[][]{{"!!!","fim"},{"vídeo","VIDEO"},{"a".repeat(81),"fim"},{null,"fim"}})
   assertThrows(IllegalArgumentException.class,()->new VoiceCommands(p[0],p[1]));
 }
 @Test public void encerrarOnlyFinishesWhenItIsTheConfiguredStopPhrase() {
  VoiceCommands c=new VoiceCommands("gravar","pode salvar");
  assertEquals(VoiceCommands.Command.NONE,c.match("encerrar"));
  assertEquals(VoiceCommands.Command.NONE,c.match("agora encerrar e salvar"));
  assertEquals(VoiceCommands.Command.NONE,c.match("encerraram"));
  assertEquals(VoiceCommands.Command.FINISH,new VoiceCommands("gravar","encerrar").match("encerrar"));
 }
 @Test public void grammarPreservesAccentsAndOnlyIncludesConfiguredPhrasesAndUnknown() {
  VoiceCommands c=new VoiceCommands("AÇÃO já!","Encerrar vídeo");
  assertArrayEquals(new String[]{"ação já","encerrar vídeo","[unk]"},c.grammarPhrases());
 }}
