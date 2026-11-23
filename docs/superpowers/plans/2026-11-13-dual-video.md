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

- [x] Regression test missing dual toggle, then support-policy and layout tests.
- [x] Independent support detector/config + tests; independent GL compositor/layout + native encoder pixel proof. Root handles settings/session integration only. Shared API above is fixed; components edit disjoint files.
- [x] Integrate lifecycle, one mic, voice, limited pre-roll, thermal/space/battery guards. Normal capture regressions remain.
- [x] Native test: synthetic red rear + green front surfaces compose into an actual saved AVC/AAC file; decode frame to verify green only in upper-right, rear elsewhere, monotonic times, history included. Emulated phone may not advertise concurrent cameras; separately test correct unsupported gating. Do not claim physical concurrent capture tested if unavailable.
- [ ] Review, Actions full suite/build/install/screenshot inspection; publish 0.5.0-alpha code7 on main only after success, verify direct release link.

Physical OPPO validation remains required for reported availability, two actual Camera2 sessions, orientation, fluency, sound/voice and heat/battery. No unsupported 4K dual promise. Maintain saved videos/package/signing identity and leave all commercial features inactive.

## Added on 2026-11-23: zoom and automatic stabilization

- Offer actual logical-camera zoom minimum below 1x (e.g. 0.6x), 1x and 2x only within reported range. Legacy API29 uses centered sensor crop and never advertises ultrawide. No claim that every physical lens is exposed to third-party apps.
- Update repeating requests without restarting encoder/session or clearing marked history. Clamp on source change; dual changes rear only.
- Request advertised video stabilization inside Android guaranteed direct-encoder 1080p30 envelope, otherwise optical if advertised. Dual GL surfaces conservatively use optical. Never combine EIS/OIS. Rejected EIS retries once without video stabilization. Show actual capture-result status, not request alone.
- UI choices have 48dp targets, remain usable during recording, disabled during preparing/saving. Dual preview is fitted between top and bottom controls so inset remains visible.
- Preparation worker retains GPU producer ownership until main-thread handoff, and cleans rejected preparations before the next serial allocation.
- Verify policy/UI tests plus native live zoom retaining the same camera session and a saved clip. Physical OPPO handshake and shake/battery performance remain device tests.

## Alpha signing continuity

The old Actions cache was missing after inactivity on 2026-11-23, so its private debug key could not be recovered from APKs. A new alpha-only RSA signing key is stored in the encrypted repository secret LANCEVIVO_DEBUG_KEYSTORE_BASE64, with a local non-repository backup. Workflow restores this secret and fails closed on same-repository builds when absent. External fork PRs can use ephemeral test signing and never release. Do not reuse this alpha key for a production Play release. Existing 0.4.x installs may require uninstalling once; saved gallery videos remain, local preferences/acceptance reset. No private key or base64 value is committed.

Branch 744cac0 validation: all 82 JVM/UI tests and 6 Android 15 tests passed. Emulator advertised a usable concurrent pair; two real emulated Camera2 sessions opened and a combined clip was saved, in addition to synthetic GPU pixel verification. Single-camera continuity max AVC gap 95,123 us and AAC 64,000 us, same thresholds. APK certificate check revealed AGP was using its own default debug store rather than the restored secret: final workflow now explicitly binds signingConfigs.debug to LANCEVIVO_DEBUG_KEYSTORE and asserts the expected public certificate fingerprint before release. Final main build must confirm it.
