package br.com.voltadolance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Metadados puros; não depende do Android e pode ser testado na JVM. */
public final class RollingWindow<T> {
    public static final class Entry<T> {
        public final T value;
        public final long startMs;
        public final long endMs;
        public Entry(T value, long startMs, long endMs) {
            if (endMs < startMs) throw new IllegalArgumentException("endMs < startMs");
            this.value = value; this.startMs = startMs; this.endMs = endMs;
        }
    }
    private final ArrayDeque<Entry<T>> queue = new ArrayDeque<>();
    public synchronized void add(T value, long startMs, long endMs) {
        queue.addLast(new Entry<>(value, startMs, endMs));
    }
    public synchronized List<T> purgeOlderThan(long boundaryMs) {
        List<T> removed = new ArrayList<>();
        while (!queue.isEmpty() && queue.peekFirst().endMs < boundaryMs) {
            removed.add(queue.removeFirst().value);
        }
        return removed;
    }
    public synchronized List<Entry<T>> from(long boundaryMs) {
        List<Entry<T>> found = new ArrayList<>();
        for (Entry<T> e : queue) if (e.endMs >= boundaryMs) found.add(e);
        return found;
    }
    public synchronized List<T> clear() {
        List<T> removed = new ArrayList<>();
        for (Entry<T> e : queue) removed.add(e.value);
        queue.clear();
        return removed;
    }
}
