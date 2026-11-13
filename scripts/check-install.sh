#!/usr/bin/env bash
set -euo pipefail

apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
package=br.com.voltadolance.dev
mkdir -p install-results
adb logcat -c
trap 'adb logcat -d > install-results/logcat.txt || true' EXIT

# A clean install is different from an update. Exercise both explicitly.
adb uninstall "$package" > /dev/null 2>&1 || true
timeout 120 adb install --no-streaming "$apk" | tee install-results/clean-install.txt
grep -q 'Success' install-results/clean-install.txt
timeout 120 adb install --no-streaming -r "$apk" | tee install-results/reinstall.txt
grep -q 'Success' install-results/reinstall.txt
adb shell dumpsys package "$package" > install-results/package.txt
grep -q 'versionCode=6' install-results/package.txt
grep -q 'versionName=0.4.1-alpha' install-results/package.txt

adb shell am start -W -n "$package/br.com.voltadolance.MainActivity" | tee install-results/launch.txt
grep -q 'Status: ok' install-results/launch.txt
sleep 5
adb shell pidof "$package" | tee install-results/pid.txt
adb logcat -d -b crash > install-results/crashes.txt
if grep -q "Process: $package" install-results/crashes.txt; then
  cat install-results/crashes.txt
  exit 1
fi
adb exec-out screencap -p > install-results/first-launch.png
