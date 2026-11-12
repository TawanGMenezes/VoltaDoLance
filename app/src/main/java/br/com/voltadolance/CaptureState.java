package br.com.voltadolance;

/** Main-thread state shared by touch, voice, camera callbacks and the UI. */
public final class CaptureState {
    public enum Phase { PREPARING, BUFFERING, RECORDING, SAVING, SAVED, ERROR, STOPPED }
    private Phase phase = Phase.PREPARING;
    private long markedAt;
    public Phase phase() { return phase; }
    public boolean canMark() { return phase == Phase.BUFFERING; }
    public boolean canFinish() { return phase == Phase.RECORDING; }
    public boolean isRecording() { return phase == Phase.RECORDING; }
    public boolean isSaving() { return phase == Phase.SAVING; }
    public void preparing() { if (!isRecording() && !isSaving()) phase = Phase.PREPARING; }
    public void ready() { if (!isRecording() && !isSaving()) phase = Phase.BUFFERING; }
    public boolean mark(long now) {
        if (!canMark()) return false;
        markedAt = now; phase = Phase.RECORDING; return true;
    }
    public boolean beginSaving() {
        if (!canFinish()) return false;
        phase = Phase.SAVING; return true;
    }
    public long elapsed(long now) { return Math.max(0, now - markedAt); }
    public void saved() { phase = Phase.SAVED; }
    public void error() { phase = Phase.ERROR; }
    public void stopped() { if (!isSaving()) phase = Phase.STOPPED; }
}
