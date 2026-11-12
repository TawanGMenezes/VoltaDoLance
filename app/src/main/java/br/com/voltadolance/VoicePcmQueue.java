package br.com.voltadolance;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** The audio producer never waits for native decoding or retains its reusable buffer. */
final class VoicePcmQueue {
    static final int MAX_CHUNK_BYTES = 16384;
    static final class Chunk {
        final byte[] bytes;
        final long session;
        final boolean discontinuity;
        Chunk(byte[] bytes, long session, boolean discontinuity) {
            this.bytes = bytes; this.session = session; this.discontinuity = discontinuity;
        }
    }
    private final ArrayBlockingQueue<Chunk> queue;
    VoicePcmQueue(int capacity) { queue = new ArrayBlockingQueue<>(capacity); }
    void offer(byte[] bytes, int length, long session) {
        if (bytes == null || length <= 0 || length > bytes.length
                || length > MAX_CHUNK_BYTES || (length & 1) != 0) return;
        byte[] copy = Arrays.copyOf(bytes, length);
        if (!queue.offer(new Chunk(copy, session, false))) {
            queue.clear();
            queue.offer(new Chunk(copy, session, true));
        }
    }
    Chunk poll(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }
    void clear() { queue.clear(); }
}
