# Settings redesign proposal

Status: planning only; brand, visual direction and commercial terms await user selection. No runtime changes or billing activation are authorized by this document.

## Intent and first release

Replace the nested list of dialogs with a dedicated, native Java SettingsActivity. Keep the camera screen focused on capture and make settings feel intentional, legible and more complete. Fishing can lead launch messaging; controls and default command phrases must work for every sport. Do not add a compulsory sport selector or fishing-only behavior. Use independent visual design; external camera apps are functional references only.

Ship first: grouped settings, explicit before/after duration explanations, safe lifecycle handling, local persistence, accessible summaries, local help/privacy and an honest inactive commercial preview. Defer accounts, purchase SDKs, referrals that generate or redeem codes, cloud backup, network analytics and arbitrary new capture capabilities.

## Approaches

- Dedicated Activity (recommended): scrollable space, predictable Android back navigation, large text and keyboard support; naturally pauses MainActivity and the shared microphone while adjusting settings. Returning needs explicit preference reload before camera restart.
- Full-screen Dialog: less navigation scaffolding, but MainActivity normally remains resumed. Camera/history and local voice can keep running behind quality or phrase editing. Making that safe requires a parallel lifecycle path and is easier to regress.
- Existing nested dialogs: useful for individual radio choices, inadequate as the primary settings navigation. Retain small native pickers inside the Activity where useful.

This recommendation is a proposal, not a record that the user selected it.

## Screen content and copy

Top: Back, “Configurações”, and a visible “Câmera e voz pausadas enquanto você ajusta” explanation. Settings do not need camera or microphone permission to open.

| Group | Rows and behavior |
| --- | --- |
| Captura | “Antes de marcar” = 15 / 30 / 60 seconds; summary “Inclui até X segundos anteriores. Ao abrir configurações, esse histórico é descartado e começa novamente ao voltar.” “Após marcar” = existing 3 / 10 / 20 / 30 / 60 minutes / no fixed limit; summary “Tempo máximo depois do comando; você pode encerrar antes.” Quality lists device-reported CameraModes, requested mode and fallback caveat. |
| Voz e avisos | Persisted voice preference, offline explanation, start and finish phrase fields using existing VoiceCommands validation. No microphone preview or second AudioRecord in this screen. Describe reserved “encerrar”, start one beep / finish two beeps. If adding a sound switch, gate only ToneGenerator cues; never recorded audio or voice PCM. |
| Energia e armazenamento | Read-only guidance and optional available-space figure, if derived locally and labeled as an observation. Explain that hiding preview keeps camera active, pausing history stops camera/mic, screen lock/background ends active capture with save attempt, and low storage / 5% battery off charger / thermal protection can end recording. No fabricated remaining-time estimates or claim of guaranteed savings. |
| Planos e indicações | Secondary card “Em estudo — indisponível nesta versão alfa”; no checkout, trial, code input, entitlement, payment permission or enabled purchase button. Names/prices remain proposals pending user decision. Every alpha capability stays available. Prefer omit exact prices until chosen. |
| Ajuda e informações legais | Local “Como funciona”, privacy, alpha limits and app version. Explain single microphone feeds local voice and recording, videos stay local, no subscription or charges in this alpha. No invented support contacts or live legal links. |

The current default `post_minutes=10` is a default, NOT the product maximum: code already permits 60 minutes and no fixed limit. Do not accidentally restrict that range. Before-history seconds are a separate rolling memory window and never a promise to retain minutes before the command.

## Minimal implementation surface

- Add `SettingsActivity.java`, non-exported manifest registration and native scrollable view hierarchy. XML layout/string resources are suitable; keep styling independent of any unchosen brand. Assign stable view IDs and accessibility labels.
- Add `CaptureSettings.java`: immutable validated snapshot plus SharedPreferences read/write helpers for the existing `local_settings` file. Preserve keys `past`, `video_height`, `video_mode`, `post_minutes`, `voice`, `start_phrase`, `stop_phrase`; preserve all unrelated keys. Keep old mode fallback based on legacy `video_height`. Validate enumerated values and phrase pairs with the existing validator. Do not silently destroy old preferences on upgrade.
- MainActivity reads the snapshot on creation and each resume, before applying camera settings and enabling voice. Replace openSettings contents with guarded navigation. Keep existing capture/recording/export logic and native Java; no WebView, React or new microphone implementation.
- Settings uses `CameraModes.detect` for quality availability without opening a recording session; a small package-private helper may be extracted if that reduces duplication. Detection failure disables only quality selection and keeps the stored selection.
- Keep current valid values/defaults, existing recording engine, packet storage and save protections. Add sound preference only if explicitly in first-release scope; default on preserves behavior.

Persist each accepted choice immediately; phrase edits save atomically only after both phrases validate, Cancel leaves them intact. Show an inline error and retain typed input on failure. Preserve draft phrase fields and scroll position across recreation. Voice switch expresses requested preference; actual listening state is shown on capture screen. Do not briefly enable voice in Settings to test availability or write false permanently for a transient startup failure.

## Lifecycle and race constraints

1. `openSettings` must recheck `cameraLoop.isCapturing()` at click time, including saving, rather than relying on button disabled state. Disable voice synchronously before navigating; invalidate any queued voice callbacks so a late “mark” cannot start a recording during transition. A foreground/session generation guard on onMark/onFinish is appropriate and must also protect background callbacks.
2. MainActivity.onPause already disables voice and invokes CameraLoop.shutdown. Preserve this behavior: unmarked rolling history is discarded, a marked event gets the current save attempt, an export continues. Do not replace this with `pauseHistory()`: that sets `manualPaused=true`, which blocks start after returning.
3. On resume: reload settings; set past/mode/post limit and validated phrases before start; resume activity/start once; enable voice only if requested, foreground, permission granted, camera running and not saving. Do not invoke `resumeCapture()` automatically, as it clears a user's explicit pause. Keep single microphone ownership in ContinuousRecorder and Vosk consuming its PCM.
4. `resumeActivity()` currently clears safetyPaused. A settings visit must not be used to bypass a still-applicable storage/thermal/battery stop. Recheck existing startup safety and preserve manualPaused. Do not introduce a restart while recorder shutdown is closing or export is running; respect CameraLoop.start guards.
5. Android can destroy MainActivity while Settings is open. Read persisted settings on fresh construction too, and preserve existing autosave/process behavior without promising recovery of unmarked history. Avoid app-global static capture objects.

## Local onboarding and legal acknowledgement

If a first-run explanation is added, use a local, versioned `notice_accepted_version` only to remember that this information was seen; provide access later from Help. “Entendi e continuar” must explain capture behavior and privacy accurately. This flag is not a purchase authorization, subscription consent, account, marketing consent or entitlement. Do not add a fake commercial terms acceptance gate. Updating a notice must not reset capture preferences or delete local video state.

## Verification and release evidence

Meaningful Robolectric coverage:

- Existing preferences migrate unchanged; invalid duration/history values fall back safely; old video_height fallback survives; unrelated keys remain untouched.
- Setting changes persist and are reloaded by the same MainActivity on resume, not only recreation; requested camera mode and phrase pair are applied before restarting.
- Settings navigation refuses marked recording and export, including a stale-enabled-button case; late voice callbacks cannot mark after settings navigation/background.
- Manual history pause remains paused on return. Opening settings during ordinary buffering pauses camera/mic and return restarts history under existing guards.
- Invalid or equivalent phrases do not persist, valid Portuguese phrases do, and Cancel/recreation do not lose committed values. Commercial preview has no active purchase/referral action.
- If sound preference ships, its off state suppresses ToneGenerator cues only; capture and command routing still work.

Update existing MainActivityTest's reflection-based editPhrases test to exercise SettingsActivity fields instead; retain capture state/button/REC assertions. Keep existing VoiceCommands, PCM queue, model, packet-store and cue tests. Tests should assert behavior, not exact brand copy, padding or implementation structure.

Instrumentation on Android 15 plus a physical OPPO follow-up: buffer → settings → change quality/history/phrases → back → fresh buffer → voice mark → finish → playable video with audio. Verify no marking or microphone acquisition while Settings is foreground, one continuous audio owner, manual pause round trip, home/background return, permissions denied, keyboard/back, recreation and save-in-progress navigation guard. Retain ContinuousVoiceTest as regression evidence rather than replacing it with UI-only assertions.

GitHub Actions visual QA: capture artifacts while APK remains installed. In the emulator script, install/grant permissions, start MainActivity, navigate by stable IDs/text via UI Automator, capture Settings top, scrolled lower groups and phrase editor with `adb exec-out screencap -p`; then run `connectedDebugAndroidTest`. Gradle connected tests can uninstall afterward, so screenshots collected only after the command are unreliable. Alternative: capture within instrumentation and immediately copy into test-managed output or emit bounded base64 evidence to host before cleanup. Never assume external storage files survive uninstall. Upload screenshots and UI hierarchy in `install-results/` with existing always-run test-artifact step.

Check 360dp and larger screens, large font (at least 1.3x and 2x), keyboard visible, dark-system settings, readable selected/disabled states, 48dp touch targets, TalkBack labels/order, system bar insets and no clipped long Portuguese summaries. Review screenshots manually; emulator results do not prove battery, thermal or real camera mode behavior on OPPO.

## Acceptance boundary

Planning can finish before choosing a brand. Product implementation should follow the user's selected visual direction and approved scope. This redesign must not silently activate commerce, add network permissions, change recording duration defaults/ranges, erase videos, replace the continuous recorder or weaken existing autosave and single-microphone protections.
