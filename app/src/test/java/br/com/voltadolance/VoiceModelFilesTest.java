package br.com.voltadolance;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class VoiceModelFilesTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 private File space(long bytes) {
  return new File(temp.getRoot().getPath()) {
   @Override public long getUsableSpace() { return bytes; }
  };
 }
 @Test public void lowSpaceRemovesStalePartialAndNeverStartsCopy() throws Exception {
  File partial=new File(temp.getRoot(),"vosk-small-pt-0.3.partial");
  assertTrue(partial.mkdir()); assertTrue(new File(partial,"old.bin").createNewFile());
  assertThrows(IOException.class,()->VoiceModelFiles.prepare(space(224L*1024*1024-1),file->fail("Must not copy")));
  assertFalse(partial.exists());
 }
 @Test public void failedCopyRemovesPartialImmediately() throws Exception {
  assertThrows(IOException.class,()->VoiceModelFiles.prepare(space(Long.MAX_VALUE),file->{
   assertTrue(file.mkdir()); assertTrue(new File(file,"some.bin").createNewFile());
   throw new IOException("disk full");
  }));
  assertFalse(new File(temp.getRoot(),"vosk-small-pt-0.3.partial").exists());
 }
 @Test public void completeModelNeedsNoAdditionalFreeSpace() throws Exception {
  File complete=new File(temp.getRoot(),"vosk-small-pt-0.3");
  assertTrue(complete.mkdir()); assertTrue(new File(complete,".complete").createNewFile());
  assertTrue(new File(complete,"final.mdl").createNewFile());
  assertEquals(complete,VoiceModelFiles.prepare(space(0),file->fail("Already extracted")));
 }
 @Test public void enoughSpacePublishesOnlyCompletedModel() throws Exception {
  File model=VoiceModelFiles.prepare(space(224L*1024*1024),file->{
   assertTrue(file.mkdir()); assertTrue(new File(file,"final.mdl").createNewFile());
  });
  assertTrue(new File(model,".complete").isFile());
  assertFalse(new File(temp.getRoot(),"vosk-small-pt-0.3.partial").exists());
 }
}
