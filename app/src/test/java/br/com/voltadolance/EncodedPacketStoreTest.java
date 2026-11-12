package br.com.voltadolance;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncodedPacketStoreTest {
    private EncodedPacketStore store() throws Exception {
        return new EncodedPacketStore(Files.createTempDirectory("encoded-history").toFile(), 2, 1024);
    }
    private void video(EncodedPacketStore s,long pts,boolean key) throws Exception {
        s.append(true,pts,key?1:0,ByteBuffer.wrap(new byte[]{1,2,3}));
    }
    @Test public void markIncludesPrecedingKeyframeAndKeepsOriginalTimeline() throws Exception {
        EncodedPacketStore s=store();
        try {
            for(int i=0;i<=7;i++) video(s,i*1_000_000L,i%2==0);
            assertTrue(s.mark(2));
            List<EncodedPacketStore.Packet> packets=s.snapshot();
            assertEquals(4_000_000L,packets.get(0).ptsUs);
            video(s,8_000_000L,true);
            assertEquals(8_000_000L,s.snapshot().get(s.snapshot().size()-1).ptsUs);
            assertFalse(s.mark(2));
        } finally {s.close();}
    }
    @Test public void evictsWholeGopsButPinnedPacketsSurvive() throws Exception {
        EncodedPacketStore s=store();
        try {
            for(int i=0;i<=10;i++) video(s,i*1_000_000L,i%2==0);
            assertEquals(8_000_000L,s.snapshot().get(0).ptsUs);
            assertTrue(s.mark(2));
            for(int i=11;i<30;i++) video(s,i*1_000_000L,i%2==0);
            assertEquals(8_000_000L,s.snapshot().get(0).ptsUs);
            assertEquals(66,s.bytesOnDisk());
        } finally {s.close();}
    }
    @Test public void skipsAudioBeforeSelectedVideoKeyframeAndDeletesFiles() throws Exception {
        EncodedPacketStore s=store(); File directory=s.directory();
        s.append(false,0,0,ByteBuffer.wrap(new byte[]{5}));
        video(s,1_000_000L,true);
        s.append(false,1_010_000L,0,ByteBuffer.wrap(new byte[]{5}));
        assertTrue(s.mark(10));
        assertEquals(2,s.snapshot().size());
        assertTrue(s.snapshot().get(0).video);
        s.close(); assertFalse(directory.exists());
    }
    @Test public void cannotMarkBeforeFirstKeyframe() throws Exception {
        EncodedPacketStore s=store();
        try {video(s,0,false); assertFalse(s.mark(2));} finally {s.close();}
    }
    @Test public void diskLimitFailsRatherThanSilentlyDroppingPinnedCapture() throws Exception {
        EncodedPacketStore s=new EncodedPacketStore(Files.createTempDirectory("encoded-limit").toFile(),2,5);
        try {
            video(s,0,true); assertTrue(s.mark(2));
            try {video(s,1,false); fail("Expected disk cap failure");}
            catch(java.io.IOException expected) {assertEquals(3,s.bytesOnDisk());}
        } finally {s.close();}
    }
}
