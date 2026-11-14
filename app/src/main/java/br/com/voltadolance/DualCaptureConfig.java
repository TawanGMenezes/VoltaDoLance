package br.com.voltadolance;

/** Validated source streams and upright encoded output for one dual session. */
final class DualCaptureConfig {
    final String rearId, frontId;
    final CameraModes.Mode rearMode, frontMode, outputMode;
    final int rearOrientation, frontOrientation;
    final boolean rearRealtime;

    DualCaptureConfig(String rearId, String frontId, CameraModes.Mode rearMode,
            CameraModes.Mode frontMode, CameraModes.Mode outputMode,
            int rearOrientation, int frontOrientation, boolean rearRealtime) {
        this.rearId=rearId; this.frontId=frontId;
        this.rearMode=rearMode; this.frontMode=frontMode; this.outputMode=outputMode;
        this.rearOrientation=rearOrientation; this.frontOrientation=frontOrientation;
        this.rearRealtime=rearRealtime;
    }
}
