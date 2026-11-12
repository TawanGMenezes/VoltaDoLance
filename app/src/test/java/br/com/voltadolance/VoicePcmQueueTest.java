package br.com.voltadolance;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoicePcmQueueTest {
 @Test public void copiesProducerBufferAndCarriesSession() throws Exception {
  VoicePcmQueue q=new VoicePcmQueue(2);
  byte[] data={1,2,3,4}; q.offer(data,4,7); data[0]=99;
  VoicePcmQueue.Chunk c=q.poll(1);
  assertArrayEquals(new byte[]{1,2,3,4},c.bytes); assertEquals(7,c.session);
 }
 @Test public void overflowDropsStaleBacklogAndMarksDiscontinuity() throws Exception {
  VoicePcmQueue q=new VoicePcmQueue(2);
  q.offer(new byte[]{1,0},2,1); q.offer(new byte[]{2,0},2,1); q.offer(new byte[]{3,0},2,1);
  VoicePcmQueue.Chunk c=q.poll(1);
  assertArrayEquals(new byte[]{3,0},c.bytes); assertTrue(c.discontinuity); assertNull(q.poll(1));
 }
 @Test public void rejectsMalformedAndOversizedChunksAndClearsOnPause() throws Exception {
  VoicePcmQueue q=new VoicePcmQueue(2);
  q.offer(null,4,1); q.offer(new byte[3],3,1); q.offer(new byte[100000],100000,1);
  assertNull(q.poll(1));
  q.offer(new byte[320],320,1); q.clear(); assertNull(q.poll(1));
 }
}
