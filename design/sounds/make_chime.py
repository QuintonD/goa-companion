"""Synthesises the timer's "phase over" chime (original, made for this app; no samples used).

Three rising bell-like notes (E5, A5, E6) built from a few partials with exponential decay,
mono 16-bit PCM at 22.05 kHz, peak-normalised to -3 dBFS, about 1.3 s long.

Usage (from the repo root):
    python -I design/sounds/make_chime.py feature/timer/src/main/res/raw/phase_chime.wav
"""

import math
import struct
import sys
import wave

RATE = 22_050
LENGTH_S = 1.3
NOTES = [(659.25, 0.00), (880.00, 0.15), (1318.51, 0.30)]  # (Hz, start s)
# Bell-ish partials: (frequency ratio, relative amplitude, decay per second).
PARTIALS = [(1.0, 1.0, 4.0), (2.0, 0.45, 6.0), (2.76, 0.25, 8.0), (5.40, 0.10, 12.0)]
ATTACK_S = 0.004
FADE_S = 0.08


def sample(t: float) -> float:
    value = 0.0
    for freq, start in NOTES:
        dt = t - start
        if dt < 0:
            continue
        attack = min(1.0, dt / ATTACK_S)
        for ratio, amp, decay in PARTIALS:
            value += attack * amp * math.exp(-decay * dt) * math.sin(2 * math.pi * freq * ratio * dt)
    return value


def main(path: str) -> None:
    count = int(RATE * LENGTH_S)
    raw = [sample(i / RATE) for i in range(count)]
    fade = int(RATE * FADE_S)
    for i in range(fade):
        raw[count - fade + i] *= 1 - i / fade
    peak = max(abs(v) for v in raw) or 1.0
    scale = 10 ** (-3 / 20) / peak * 32767
    frames = b"".join(struct.pack("<h", int(round(v * scale))) for v in raw)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(frames)


if __name__ == "__main__":
    main(sys.argv[1])
