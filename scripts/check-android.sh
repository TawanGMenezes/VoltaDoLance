#!/usr/bin/env bash
set -euo pipefail
# The runner closes the emulator after this script, so collect evidence before returning.
trap 'adb pull /sdcard/Download/lancevivo-qa install-results/ || true' EXIT
bash scripts/check-install.sh
adb shell am force-stop com.google.android.apps.nexuslauncher
gradle --no-daemon :app:connectedDebugAndroidTest
find app/build/outputs/androidTest-results -name 'logcat*.txt' -print0 | xargs -0 grep 'ContinuityTimeline' > install-results/continuity-report.txt
