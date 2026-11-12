package br.com.voltadolance;

import org.junit.Test;
import static org.junit.Assert.*;

public class RollingWindowTest {
    @Test public void preservesSegmentsAtBoundary() {
        RollingWindow<String> w = new RollingWindow<>();
        w.add("a", 0, 4000); w.add("b", 4000, 8000); w.add("c", 8000, 12000);
        assertEquals(3, w.from(4000).size());
        assertEquals(1, w.purgeOlderThan(7000).size());
        assertEquals("b", w.from(7000).get(0).value);
    }
    @Test public void rejectsInvertedTimestamps() {
        RollingWindow<String> w = new RollingWindow<>();
        try { w.add("bad", 99, 1); fail("Expected validation"); }
        catch (IllegalArgumentException expected) { assertTrue(true); }
    }
    @Test public void clearingRemovesEverything() {
        RollingWindow<Integer> w = new RollingWindow<>();
        w.add(1, 1, 2);
        assertEquals(1, w.clear().size());
        assertTrue(w.from(0).isEmpty());
    }
}
