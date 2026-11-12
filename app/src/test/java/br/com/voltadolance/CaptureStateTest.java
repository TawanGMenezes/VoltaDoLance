package br.com.voltadolance;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureStateTest {
    @Test public void controlsFollowReadyMarkExportAndSave() {
        CaptureState state = new CaptureState();
        assertFalse(state.canMark()); assertFalse(state.canFinish());
        state.ready(); assertTrue(state.canMark());
        assertTrue(state.mark(1000)); assertFalse(state.mark(2000));
        assertTrue(state.isRecording()); assertTrue(state.canFinish());
        assertEquals(65000, state.elapsed(66000));
        assertTrue(state.beginSaving()); assertFalse(state.beginSaving());
        assertFalse(state.canMark()); assertFalse(state.canFinish());
        assertFalse(state.isRecording()); assertTrue(state.isSaving());
        state.ready(); assertTrue(state.isSaving());
        state.saved(); assertFalse(state.canMark());
        state.ready(); assertTrue(state.canMark());
    }
    @Test public void errorsAndPauseNeverLeaveRecOn() {
        CaptureState state = new CaptureState(); state.ready(); state.mark(0);
        state.error(); assertFalse(state.isRecording()); assertFalse(state.canFinish());
        state.ready(); state.mark(100); state.beginSaving(); state.stopped();
        assertTrue(state.isSaving()); state.error(); assertFalse(state.isSaving());
    }
    @Test public void safetyReservesTwoExportCopiesAndHonorsUnlimited() {
        assertFalse(CaptureSafety.durationReached(0, 999999999));
        assertFalse(CaptureSafety.durationReached(600000, 599999));
        assertTrue(CaptureSafety.durationReached(600000, 600000));
        assertTrue(CaptureSafety.lowSpace(500_000_000L, 200_000_000L, 10_000_000));
        assertFalse(CaptureSafety.lowSpace(2_000_000_000L, 200_000_000L, 10_000_000));
        assertTrue(CaptureSafety.lowBattery(5, false));
        assertFalse(CaptureSafety.lowBattery(5, true));
        assertFalse(CaptureSafety.lowBattery(-1, false));
    }
}
