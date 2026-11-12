package br.com.voltadolance;

/** Cues follow accepted state transitions, not speculative speech hypotheses. */
public final class CaptureCues {
    public enum Cue { NONE, START, STOP }
    private CaptureCues() { }
    public static Cue between(CaptureState.Phase before,CaptureState.Phase after) {
        if(before!=CaptureState.Phase.RECORDING && after==CaptureState.Phase.RECORDING) return Cue.START;
        if(before==CaptureState.Phase.RECORDING && after==CaptureState.Phase.SAVING) return Cue.STOP;
        return Cue.NONE;
    }
}
