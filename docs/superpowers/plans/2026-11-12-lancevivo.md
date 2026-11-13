# LanceVivo Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Preserve the user's authorization to implement and publish; no extra handoff is required.

**Goal:** Apply the approved LanceVivo design with dedicated settings and locally persisted light/dark/system appearance.

**Architecture:** Native Java Activities share a theme-aware base and palette. Settings write existing preferences; capture reloads before restart. System mode resolves Android uiMode on each Activity creation, without recreating a recording mid-lance.

**Tech Stack:** Java 17, Android 29–36, existing Robolectric/Android instrumentation, GitHub Actions.

**Spec:** docs/LANCEVIVO-DESIGN.md plus user approval and requested Claro/Escuro/Seguir o celular.

## Global Constraints

Preserve package, videos, existing preference keys, continuous recorder, single microphone and offline voice. No billing activation or extra network permissions. Default appearance follows the phone. Keep capture overlays high contrast in both modes. Do not recreate MainActivity from onResume before it pauses safely.

## Review Focus

1. Theme follows uiMode, explicit override ignores it; invalid preference falls back to system.
2. Return from settings reloads phrases and capture values before camera starts.
3. Delayed voice cannot mark while settings/background is active.
4. Manual pause and capture/export navigation guards survive settings.
5. Large fonts, keyboard and theme recreation retain phrase drafts and readable controls.

## Tasks

- [ ] Write failing navigation and appearance tests against current activity; run on Actions branch and observe expected failures.
- [ ] Add AppAppearance palette resolver, ThemedActivity and day/night resources; cover all three preference modes, fallback and recreation.
- [ ] Add CaptureSettings validated snapshot preserving existing keys, SettingsActivity with grouped rows, phrase editor, quality selector, sound preference, appearance selector and truthful local help/plan preview. Test persistence, invalid phrases and cancelled edits.
- [ ] Integrate Main lifecycle: stop voice before navigation, reject marked/saving, guard foreground callbacks, reload settings before start. Apply LanceVivo controls/identity, adaptive scrolling, safe inset handling and button visibility. Test manual pause, late callbacks and state controls.
- [ ] Adapt Gallery palette and empty state; preserve existing video folder and thumbnails. Add original adaptive launcher vector icon, keep application ID; bump to 0.4.0-alpha versionCode 5.
- [ ] Run suite/build/installation/voice integration and capture real light/dark screenshots with Actions; inspect screenshots and test reports, fix failures.
- [ ] Fresh branch review, merge approved work into main, validate final Actions and provide direct installable release APK.

## Execution ledger

User approved visual and requested theme modes. Working on existing feature branch in dedicated project checkout; do not disturb main until checks pass. Local JDK unavailable, use CI for Android checks. Existing key continuity retained. Gallery scope limited to identity/empty state. Sound switch defaults on, controls cues only. Commerce remains explanatory and inactive.
