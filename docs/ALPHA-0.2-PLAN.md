# Alpha 0.2

Implement the user's supplied scope in the existing Java/Camera2 app, preserving its colors, copy, local-only storage and segmented pre-record window. No billing or network permission.

1. Add a pure Java capture state model and Portuguese phrase matcher, with tests for readiness, mark, finish, export success/failure, repeated commands and phrase validation. Wire buttons and blinking REC/timer to this model.
2. Persist editable phrases and post-mark presets (3/10/20/30/60 minutes, unlimited). Stop and save for duration, low battery, critical heat or insufficient export reserve. Disable recording controls and settings during export; save marked events when leaving the screen.
3. Detect standard Camera2 recorder/preview sizes, frame durations, AE ranges and AVC encoder support. Offer reported modes including 4K30/1080p60 when available; retry lower modes only before marking, discard incompatible buffer on fallback, report actual selection.
4. Load gallery thumbnails off the main thread with a visible playback icon. Preserve MediaStore pending publication and private caches.
5. Run unit and UI tests and debug assembly in GitHub Actions, inspect the exact commit's result, and retrieve APK or provide exact artifact link. Document physical-device checks still needed.

Review focus: immediate finish after mark; pause during async camera configuration/export; unsupported high quality; partial/final voice duplicates and overlapping phrases; export space for long events.

Execution: continue directly as requested by the user; use an isolated clone and branch. Do not merge to main.
