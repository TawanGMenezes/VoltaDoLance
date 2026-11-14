# LanceVivo dual video plan

User requested an on/off option, gated by actual phone availability, with rear full-frame and front upper-right inset like the supplied functional reference. Both images must be in saved clips and pre-record history. Keep existing brand; no external assets or branding copied.

## Architecture and interfaces

Native Java/Camera2 remains. One ContinuousRecorder AVC/AAC stream and one AudioRecord still feed bounded packet history and offline voice. A GPU compositor renders rear plus rounded front inset to the existing encoder input Surface and preview Surface. Encode upright portrait, orientation hint 0. Normal single-camera path stays direct and unchanged. No cloud, billing, account, extra permissions or dependencies.

- `DualCaptureConfig`: immutable package fields `String rearId,frontId`; `CameraModes.Mode rearMode,frontMode,outputMode`; `int rearOrientation,frontOrientation`; `boolean rearRealtime`. Source modes are landscape; output is portrait at up to 720x1280/30, subject to encoder support.
- `DualCameraSupport.inspect(Context)` returns immutable `Result` with `boolean available`, `String message`, `DualCaptureConfig config` (null if unavailable). API30+ only. Require an advertised concurrent FRONT/BACK pair and `isConcurrentSessionConfigurationSupported` for two single PRIVATE SurfaceTexture outputs at source dimensions. Prefer rear720p30/frontVGA30, try smaller compatible pairs. Never infer support from model name or native camera app. Detector opens no cameras/microphone and closes temporary probe surfaces.
- `DualFrameComposer(Surface encoder, Surface preview, int outputWidth, int outputHeight, CameraModes.Mode rearMode, CameraModes.Mode frontMode, int rearOrientation, int frontOrientation, boolean rearRealtime, Listener listener) throws Exception`: owns GL HandlerThread, two OES inputs; `Surface rearSurface()`, `Surface frontSurface()`, idempotent `close()`. Listener `void onError(String message)` may run on GL thread; caller posts to main and generation-checks. Never release caller-owned encoder/preview surfaces. Preview may be null for native tests.
- Composer draws rear full output, front inset x=.68,y=.03,w=.28,h=.22 measured from TOP, rounded corners/border. Front mirrored in both preview and encoded composition, matching the user-facing view. Camera sensor rotation and center crop applied before composition; source SurfaceTexture transforms honored. Only new rear timestamps drive encoded frames; convert REALTIME to MONOTONIC clock for audio, anchor UNKNOWN once, never stamp duplicates. Detect stale input/fatal GL errors and stop safely.
- CameraLoop opens BOTH devices before configuring either session. One source Surface per camera. Start shared recorder and requests only after both sessions configure. Generation guards, all-resource cleanup on exit/save/failure. Disable lens switch in dual. Unsupported/startup failure falls back to normal rear and clears dual preference with explicit message; never mix camera modes mid-marked clip.
- Settings capture page: off-by-default switch, availability/loading status, upper-right inset description, possible quality reduction and increased energy use. Check compatibility asynchronously; don't imply Reno14 F's stock feature guarantees availability to this app. Persist `dual_video`, keep normal `video_mode` preference for disabling dual. UI never offers active switch on unsupported devices.

## Execution

- [ ] Regression test missing dual toggle, then support-policy and layout tests.
- [ ] Independent support detector/config + tests; independent GL compositor/layout + native encoder pixel proof. Root handles settings/session integration only. Shared API above is fixed; components edit disjoint files.
- [ ] Integrate lifecycle, one mic, voice, limited pre-roll, thermal/space/battery guards. Normal capture regressions remain.
- [ ] Native test: synthetic red rear + green front surfaces compose into an actual saved AVC/AAC file; decode frame to verify green only in upper-right, rear elsewhere, monotonic times, history included. Emulated phone may not advertise concurrent cameras; separately test correct unsupported gating. Do not claim physical concurrent capture tested if unavailable.
- [ ] Review, Actions full suite/build/install/screenshot inspection; publish 0.5.0-alpha code7 on main only after success, verify direct release link.

Physical OPPO validation remains required for reported availability, two actual Camera2 sessions, orientation, fluency, sound/voice and heat/battery. No unsupported 4K dual promise. Maintain saved videos/package/signing identity and leave all commercial features inactive.
