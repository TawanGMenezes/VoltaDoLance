# Camera and selection dialogs implementation plan

User requested branded option dialogs, a recognizable gallery icon, front/back switching with supported quality negotiation. Native Java/Camera2 remains unchanged as the platform. Execute inline with existing authorization to publish a tested APK on main.

- [ ] Add regression tests for choice cards, cancellation, compatible quality selection and switch guards; observe missing-feature failures in CI.
- [ ] Build one rounded, theme-aware selection dialog with scrollable accessible cards and explicit selected state; reuse for history, duration, quality and appearance. No new UI dependency.
- [ ] Enumerate selected facing explicitly. Exact resolution/FPS wins; same dimensions with compatible FPS next; otherwise maximum supported resolution/FPS. Query the destination before discarding current history; reject missing/incompatible cameras without losing current state.
- [ ] Switch only outside recording/export; disable voice during teardown and restart after the new history is ready. Preserve manual pause. Persist facing and negotiated mode; show actual applied lens/quality. Restart discards only unmarked history. Mirror front preview, leave exported video unmirrored and use its sensor orientation.
- [ ] Replace gallery glyph with a 28dp vector in an accessible 48dp target; add recognizable camera-switch control.
- [ ] Test actual front/back Camera2 sessions, mode fallback, recording/save guards, themes and dialogs on Android. Preserve single mic, continuous recording, privacy and billing-off state.
- [ ] Review changes, publish 0.4.1-alpha code6 and verify final main Actions/release.

No camera fallback can advertise a mode the destination does not report. One requested encoder session may still be rejected; existing lower-mode retries remain. If there is no usable front camera, keep the rear session and inform the user. Camera switching never combines two camera streams into a marked clip.
