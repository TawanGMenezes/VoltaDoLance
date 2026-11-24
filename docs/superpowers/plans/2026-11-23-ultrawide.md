# Ultra-angular separada — 0.5.1

Request: discover real ultra-wide lenses in addition to main-camera zoom, expose compatible capture without inventing 0.5x support. Preserve stable alpha signing, local privacy, bounded history and voice matching.

1. CameraLenses catalog enumerates public camera IDs and advertised physical IDs of logical multi-camera devices. Compare focal length normalized by sensor diagonal; reject absent/ambiguous metadata, depth/macro and no video modes. Prefer directly openable routes. Physical outputs require exact session validation.
2. CameraLoop adds an optional rear lens route. UI merges its approximate relative ratio with primary zoom shortcuts. Switching physical sources is blocked during REC/save; live digital zoom remains available. Before marking, source switch clears only unmarked history, negotiates actual lens modes, pauses voice until ready, and rolls back lens/mode/zoom on failure. Front and dual retain their existing safe paths.
3. Physical sources open logical owner with OutputConfiguration.setPhysicalCameraId on both preview and encoder surfaces. Query exact SessionConfiguration when supported; otherwise safe runtime attempt. Use physical metadata/orientation and physical capture results. Do not apply logical zoom to a pinned physical stream.
4. Settings display asynchronous ultra-wide discovery status, including unavailable/unconfirmed access and dual restrictions. No model-name assumptions, guessed vendor IDs or permissions added.
5. Policy + UI/state regression tests, exact output routing test, native availability branch with save if exposed, full existing suite. CI builds code8/0.5.1, stable signature. Review, main, full Actions, verify public APK link.

The PC has no connected phone. Android metadata may withhold a lens used by the manufacturer's own camera app; disclose this instead of faking a button.
