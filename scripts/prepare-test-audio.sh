#!/usr/bin/env bash
set -euo pipefail
# Fixtures are already signed 16-bit mono PCM at 16 kHz: no transcoder needed.
python3 - <<'PY'
from pathlib import Path
import wave
out = Path('app/src/androidTest/assets')
out.mkdir(parents=True, exist_ok=True)
for name in ('start', 'stop'):
    with wave.open('app/src/androidTest/fixtures/' + name + '.wav', 'rb') as source:
        assert (source.getnchannels(), source.getsampwidth(), source.getframerate(), source.getcomptype()) == (1, 2, 16000, 'NONE')
        frames = source.readframes(source.getnframes())
    # Same one-second lead-in and three-second endpoint silence used by the decoder test.
    (out / (name + '.pcm')).write_bytes(bytes(32000) + frames + bytes(96000))
PY
