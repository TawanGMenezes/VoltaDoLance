# Portuguese speech fixtures

Fixed synthetic Portuguese speech, generated locally with Windows SAPI Portuguese Maria for test reproducibility. No user's microphone audio or personal recording. `start.wav` says “peixe na linha”; `stop.wav` says “pode salvar”. Mono PCM16, 16 kHz.

The build converts these WAVs to test APK assets with one second leading silence and three seconds trailing silence. They are not application assets. Endpointed recognized text must still equal the complete configured phrase; no alternate dispatch or shortened matching is allowed.

Previous runner-generated eSpeak speech intermittently decoded the stop phrase as only “salvar”, correctly producing no command. Stable input improves the speech fixture; it does not prove recognition of every real voice or permit partial commands.
