#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/androidTest/assets build/speech-fixtures
espeak-ng -v pt-br -s 135 -w build/speech-fixtures/start.wav 'peixe na linha'
espeak-ng -v pt-br -s 135 -w build/speech-fixtures/stop.wav 'pode salvar'
for command in start stop; do
  ffmpeg -hide_banner -loglevel error -y -i "build/speech-fixtures/$command.wav" -af 'adelay=500:all=1,apad=pad_dur=3' -ar 16000 -ac 1 -f s16le "app/src/androidTest/assets/$command.pcm"
done
