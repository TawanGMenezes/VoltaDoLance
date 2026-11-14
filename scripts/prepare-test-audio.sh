#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/androidTest/assets build/speech-fixtures
# Fixed Portuguese synthetic utterances: same waveform on every runner.
# These are test-only assets, never bundled in the application APK.
for command in start stop; do
  ffmpeg -hide_banner -loglevel error -y -i "app/src/androidTest/fixtures/$command.wav" -af 'adelay=1000:all=1,apad=pad_dur=3' -ar 16000 -ac 1 -f s16le "app/src/androidTest/assets/$command.pcm"
done
