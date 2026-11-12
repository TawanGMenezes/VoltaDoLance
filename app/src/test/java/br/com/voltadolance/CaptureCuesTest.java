package br.com.voltadolance;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureCuesTest {
    @Test public void soundsOnlyOnAcceptedRecordingAndStopTransitions() {
        assertEquals(CaptureCues.Cue.START,CaptureCues.between(CaptureState.Phase.BUFFERING,CaptureState.Phase.RECORDING));
        assertEquals(CaptureCues.Cue.STOP,CaptureCues.between(CaptureState.Phase.RECORDING,CaptureState.Phase.SAVING));
        assertEquals(CaptureCues.Cue.NONE,CaptureCues.between(CaptureState.Phase.RECORDING,CaptureState.Phase.RECORDING));
        assertEquals(CaptureCues.Cue.NONE,CaptureCues.between(CaptureState.Phase.SAVING,CaptureState.Phase.SAVED));
        assertEquals(CaptureCues.Cue.NONE,CaptureCues.between(CaptureState.Phase.PREPARING,CaptureState.Phase.BUFFERING));
    }
}
