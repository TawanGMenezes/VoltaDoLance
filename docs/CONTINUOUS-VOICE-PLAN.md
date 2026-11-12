# Continuous capture and shared offline voice

User asks to fix speech-triggered REC/save and visible periodic video cuts, carrying changes through to a new APK on main. Existing code restarts MediaRecorder and Camera2 sessions every four seconds; independent SpeechRecognizer microphone capture is incompatible on some devices.

## Design

Use one uninterrupted Camera2 session targeting a MediaCodec AVC surface, and one AudioRecord PCM16 mono 16kHz stream feeding both AAC encoder and bundled Portuguese Vosk recognition. No remote recognition or runtime network permission. Keep encoded pre-record GOPs on private disk, not raw video in RAM. Pin the desired history on mark; retain original per-track timestamps and remux to a single MP4 at finish. Do not restart encoders at history boundaries or mark. Settings, identity, duration and battery/storage protections remain.

## Interfaces and ownership

* Media task owns ContinuousRecorder.java and any EncodedPacketStore/Timeline helpers and tests. Constructor ContinuousRecorder(Activity, CameraModes.Mode, int orientation, int pastSeconds, Listener) throws Exception prepares codecs and video input surface. Listener: onReady(), onPcm(byte[],int), onError(String). Methods: Surface inputSurface(); void start(); boolean mark(int pastSeconds); void setPastSeconds(int); long bytesOnDisk(); void finish(File output) throws Exception; void discard(). start() is called once after Camera2 repeating request begins; finish/discard are blocking worker operations, idempotent stop, close resources, discard private packets after success/error. onReady after both output formats and initial video keyframe exist. onPcm must not block codec/audio threads. Use sensor/AudioRecord monotonic clock timestamps with a consistent base; preserve AAC/video sync and keyframe history. Graceful EOS/timeouts.
* Voice task owns OnDeviceVoice.java, VoiceCommands.java and voice helpers/tests only. Keep existing constructor/callback and setPhrases/enable/disable API. Add onPcm(byte[],int) for nonblocking ingestion from the shared stream, close(), isReady(), default Callback.onState(String). Vosk 0.3.75 + JNA >=5.16.0 and bundled model assets/model-pt (Vosk small pt0.3 Apache2) provided by root Gradle integration. Model extraction asynchronous; enabled means desired state, ready means actual model/listening readiness. No second microphone, SpeechRecognizer, internet, or recognition loop restart. Dedupe partial/final utterances; support exact phrases/whole-token phrase within utterance, stop priority. Clearly report model loading, readiness, heard phrase and errors, preserve desired setting across transient errors.
* Root owns CameraLoop/MainActivity/Gradle/workflows/Android integration tests/docs. CameraLoop routes listener PCM to voice; state/REC changes immediately on accepted mark/finish. Camera2 lifecycle guarded by generation; same codec/session until explicit stop/error. Save runs outside UI; pause/export ownership preserved.

## Validation

Additional user requirements: keep history mode, never accumulate hours of unmarked video; evict by time and hard disk bound, stop with free-space reserve and visible warning. Expose pause-history control for battery conservation. One short audible cue on accepted mark and two on accepted finish, shared by button/voice and no duplicate cues for repeated refreshes.

1. Unit regressions for packet history keyframe cutoff, original timestamps across many GOPs, eviction/pinning, voice boundaries/custom phrases and duplicate partial/final commands.
2. Observe RED before implementation, then full JVM/UI suite GREEN in Actions.
3. Emulator instrumentation with actual Camera2/AVC/AAC records across multiple old four-second boundaries; inspect MP4 timestamps/gaps and tracks. Exercise actual bundled Portuguese recognizer with PCM speech fixtures and REC/save UI callbacks. Install/reinstall/launch and native library alignment checks.
4. Independent review; merge into main after validation, generate new uniquely-versioned APK and publish direct download. Physical OPPO microphone/accent and camera modes still require user validation; do not claim those were tested remotely.
