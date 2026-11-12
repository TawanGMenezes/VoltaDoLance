package br.com.voltadolance;

/** Reserve space for merged file + MediaStore copy, plus upcoming camera segments. */
public final class CaptureSafety {
    private CaptureSafety() { }
    public static boolean durationReached(long limitMs, long elapsedMs) {
        return limitMs > 0 && elapsedMs >= limitMs;
    }
    public static boolean lowSpace(long available, long eventBytes, long segmentBytes) {
        return available < 128L * 1024 * 1024 + 2 * eventBytes + 3 * segmentBytes;
    }
    public static boolean lowBattery(int percent, boolean charging) {
        return percent >= 0 && percent <= 5 && !charging;
    }
}
