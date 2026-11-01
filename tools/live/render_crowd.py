#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Live Mode's stadium audience: original synthesis, no recordings.

Writes app/src/main/assets/live_crowd.bin and live_crowd.json: a crowd bed that loops under the
songs, an opening cheer that starts before the first note, and a closing ovation that starts at the
last one. Everything is built here from noise and arithmetic:

* Applause: a few thousand people clapping, each with their own hands (a resonant noise burst,
  as in Peltola et al., "Synthesis of hand clapping sounds", IEEE TASLP 2007), clapping rate,
  timing jitter, position and distance. They join and stop as the crowd's mood rises and falls.
* Cheering: hundreds of voices: a band-limited glottal source with jitter and breath noise, shaped
  by four formant resonators moving between vowels ("woo-oh", "yeah", "hey", screams), with
  rising and falling pitch.
* Two-finger whistles.
* A stadium: every source is placed near, mid-field or far away; distance turns it down and dulls
  it, and a synthesized stadium response (frequency-dependent decay, early reflections from the
  stands) supplies the space around it.

Requires Python 3 with NumPy 2.3.5 and SciPy 1.16.3 (both BSD-3-Clause), used only here.
Run from the repository root: python3 tools/live/render_crowd.py
"""
import hashlib
import json
import struct
from pathlib import Path

import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parents[2]
FS = 32000  # rendering rate; the assets are resampled below
EVENT_RATE = 22050
BED_RATE = 16000
MU = 255.0

# Where a source stands: (gain of its direct sound, its direct sound's bandwidth in Hz, its share of
# the stadium's reverberation). Far away, a voice is mostly reverberation, and dull.
DISTANCE = {
    "near": (1.0, 12000, 0.25),
    "mid": (0.8, 8000, 0.55),
    "far": (0.5, 4800, 1.0),
}

VOWELS = {  # (formant Hz, bandwidth Hz), an adult male voice shouting
    "u": [(330, 70), (900, 90), (2250, 130), (3300, 180)],
    "o": [(560, 80), (950, 90), (2400, 130), (3300, 180)],
    "a": [(820, 100), (1250, 100), (2600, 140), (3500, 190)],
    "e": [(600, 80), (1800, 110), (2500, 140), (3500, 190)],
    "ae": [(720, 90), (1700, 110), (2450, 140), (3500, 190)],
}
# Shouts and what they move between: ("woo", from u to o), and so on.
SHOUTS = {
    "woo": ("u", "o"),
    "yeah": ("e", "a"),
    "hey": ("ae", "e"),
    "scream": ("a", "ae"),
    "oh": ("o", "a"),
}


def ramp(n, at, length):
    """0 before at, 1 after at + length, a raised cosine between (all in samples)."""
    x = np.clip((np.arange(n) - at) / max(1, length), 0, 1)
    return 0.5 - 0.5 * np.cos(np.pi * x)


def envelope(n, points):
    """Piecewise-linear curve through (seconds, value) points."""
    t = np.arange(n) / FS
    xs, ys = zip(*points)
    return np.interp(t, xs, ys)


def stadium_response(rng, seconds=3.6):
    """A stereo stadium impulse response, without the direct sound, at unit energy per channel."""
    n = int(seconds * FS)
    t = np.arange(n) / FS
    f = np.fft.rfftfreq(n, 1 / FS)
    logf = np.log2(np.maximum(f, 1.0))
    centres = [63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]
    # Seconds to decay 60dB in each octave: long in the bass, the treble soaked up by air and people.
    decay = [3.3, 3.1, 2.9, 2.6, 2.2, 1.8, 1.35, 0.95, 0.55]
    late = np.zeros((n, 2))
    for ch in range(2):
        spectrum = np.fft.rfft(rng.standard_normal(n))
        for centre, rt in zip(centres, decay):
            weight = np.clip(1 - np.abs(logf - np.log2(centre)), 0, 1)
            late[:, ch] += np.fft.irfft(spectrum * weight, n) * np.exp(-6.9078 * t / rt)
    pre = int(0.024 * FS)
    late = np.vstack([np.zeros((pre, 2)), late[:-pre]])
    late *= (1 - np.exp(-np.arange(n) / (0.05 * FS)))[:, None]
    late /= np.sqrt(np.mean(np.sum(late ** 2, axis=0)))
    early = np.zeros((n, 2))
    tap = signal.sosfilt(signal.butter(2, 5000, fs=FS, output="sos"), np.r_[1.0, np.zeros(63)])
    # The pitch, the lower stands, the upper stands, the roof and the far end.
    for delay, gain in [(0.009, 0.45), (0.017, 0.32), (0.038, 0.28), (0.055, 0.24), (0.079, 0.2),
                        (0.104, 0.17), (0.141, 0.13), (0.19, 0.1), (0.31, 0.07), (0.43, 0.05)]:
        k = int(delay * FS)
        pan = rng.uniform(0.15, 0.85)
        early[k:k + len(tap), 0] += gain * np.sqrt(1 - pan) * tap
        early[k:k + len(tap), 1] += gain * np.sqrt(pan) * tap
    return late * 0.9 + early


def place(rng, kind):
    """Distance (m) and stereo position for someone in the near, mid or far crowd."""
    lo, hi = {"near": (2.5, 7), "mid": (8, 35), "far": (35, 160)}[kind]
    distance = float(np.exp(rng.uniform(np.log(lo), np.log(hi))))
    return distance, rng.uniform(0.05, 0.95)


class Crowd:
    """Dry sound for each distance band, turned into the stadium's sound by render()."""

    def __init__(self, seconds):
        self.n = int(seconds * FS)
        self.dry = {kind: np.zeros((self.n, 2)) for kind in DISTANCE}

    def add(self, kind, start, sound, gain, pan):
        start = int(start)
        if start >= self.n:
            return
        if start < 0:
            sound = sound[-start:]
            start = 0
        end = min(self.n, start + len(sound))
        piece = sound[:end - start] * gain
        self.dry[kind][start:end, 0] += piece * np.sqrt(1 - pan)
        self.dry[kind][start:end, 1] += piece * np.sqrt(pan)

    def render(self, response):
        out = np.zeros((self.n, 2))
        for kind, (direct, bandwidth, reverb) in DISTANCE.items():
            dry = self.dry[kind]
            if not dry.any():
                continue
            sos = signal.butter(2, bandwidth, fs=FS, output="sos")
            out += signal.sosfilt(sos, dry, axis=0) * direct
            mono = dry.mean(axis=1) * reverb
            for ch in range(2):
                out[:, ch] += signal.fftconvolve(mono, response[:, ch])[:self.n]
        return out


def applause(rng, crowd, mood, people):
    """people: {kind: count}. mood: 0..1 per sample, how much of the crowd is clapping."""
    n = crowd.n
    bursts = []
    for _ in range(96):
        length = 384
        tau = rng.uniform(0.0006, 0.0024) * FS
        bursts.append(rng.standard_normal(length) * np.exp(-np.arange(length) / tau))
    bursts = np.array(bursts)
    centres = np.geomspace(620, 3300, 18)
    widths = [2.4, 5.0]
    crack = signal.butter(2, 1800, btype="high", fs=FS, output="sos")
    for kind, count in people.items():
        hands = []
        for _ in range(count):
            distance, pan = place(rng, kind)
            hands.append(dict(
                gain=3.0 / distance * rng.uniform(0.5, 1.2),
                pan=pan,
                centre=int(rng.integers(len(centres))),
                width=int(rng.integers(len(widths))),
                period=1 / float(np.clip(rng.normal(4.4, 0.65), 2.8, 6.6)),
                # Who claps first and keeps going longest: the keenest have the lowest threshold.
                threshold=rng.uniform(0.02, 0.98),
                phase=rng.uniform(0, 1),
            ))
        for c in range(len(centres)):
            for w in range(len(widths)):
                group = [h for h in hands if h["centre"] == c and h["width"] == w]
                if not group:
                    continue
                excite = np.zeros((n + 400, 2))
                for h in group:
                    t = h["phase"] * h["period"]
                    while t * FS < n:
                        i = int(t * FS)
                        level = mood[i]
                        if level > h["threshold"]:
                            amp = h["gain"] * rng.uniform(0.75, 1.2) * (0.55 + 0.45 * level)
                            b = bursts[int(rng.integers(len(bursts)))] * amp
                            excite[i:i + len(b), 0] += b * np.sqrt(1 - h["pan"])
                            excite[i:i + len(b), 1] += b * np.sqrt(h["pan"])
                        t += h["period"] * (1 + 0.07 * rng.standard_normal())
                excite = excite[:n]
                fc, q = centres[c], widths[w]
                w0 = 2 * np.pi * fc / FS
                alpha = np.sin(w0) / (2 * q)
                b = np.array([alpha, 0, -alpha]) / (1 + alpha)
                a = np.array([1, -2 * np.cos(w0) / (1 + alpha), (1 - alpha) / (1 + alpha)])
                body = signal.lfilter(b, a, excite, axis=0) * 7.0
                snap = signal.sosfilt(crack, excite, axis=0) * 2.2
                crowd.dry[kind] += body + snap


def formant_filter(vowel, scale):
    """Second-order sections of a cascade of formant resonators (unity gain at 0Hz)."""
    sos = []
    for freq, bw in VOWELS[vowel]:
        freq *= scale
        if freq >= FS * 0.45:
            continue
        r = np.exp(-np.pi * bw * scale / FS)
        c = -r * r
        b = 2 * r * np.cos(2 * np.pi * freq / FS)
        a = 1 - b - c
        sos.append([a, 0, 0, 1, -b, -c])
    return np.array(sos)


def smooth_noise(rng, n, rate_hz):
    """Random wandering around 0 (standard deviation about 1), changing at about rate_hz."""
    points = max(4, int(n / FS * rate_hz) + 4)
    return np.interp(np.arange(n), np.linspace(0, n, points), rng.standard_normal(points))


def voice(rng, shout, female):
    """One person's shout, dry, at about unit RMS."""
    length = {"woo": (0.8, 2.2), "yeah": (0.45, 0.85), "hey": (0.25, 0.45),
              "scream": (0.9, 2.4), "oh": (0.6, 1.4)}[shout]
    n = int(rng.uniform(*length) * FS)
    t = np.arange(n) / FS
    dur = n / FS
    if shout == "scream":
        f0 = rng.uniform(520, 950) if female else rng.uniform(330, 560)
    else:
        f0 = rng.uniform(300, 560) if female else rng.uniform(150, 290)
    if shout in ("woo", "oh"):
        contour = 0.82 + 0.38 * (1 - np.exp(-t / 0.09)) - 0.16 * ramp(n, n - 0.25 * FS, 0.25 * FS)
    elif shout == "yeah":
        contour = 1.12 - 0.3 * t / dur
    elif shout == "hey":
        contour = 1.05 - 0.12 * t / dur
    else:
        contour = 1 + 0.05 * smooth_noise(rng, n, 3)
    vibrato = 0.015 * np.sin(2 * np.pi * rng.uniform(4.5, 6.5) * t + rng.uniform(0, 6.3)) * ramp(n, 0.2 * FS, 0.2 * FS)
    jitter = 0.012 * smooth_noise(rng, n, 40)
    inc = f0 * contour * (1 + vibrato + jitter) / FS
    phase = (np.cumsum(inc) + rng.uniform(0, 1)) % 1.0
    # A band-limited sawtooth (polyBLEP): the glottis, pressed hard.
    src = 2 * phase - 1
    edge = phase < inc
    x = phase[edge] / inc[edge]
    src[edge] -= x + x - x * x - 1
    edge = phase > 1 - inc
    x = (phase[edge] - 1) / inc[edge]
    src[edge] -= x * x + x + x + 1
    breath = {"scream": 0.45, "hey": 0.3}.get(shout, 0.22)
    noise = rng.standard_normal(n) * (0.55 + 0.45 * np.cos(2 * np.pi * phase))
    src = -src + breath * noise
    src *= 1 + 0.08 * smooth_noise(rng, n, 25)  # shimmer
    scale = rng.uniform(1.1, 1.22) if female else rng.uniform(0.94, 1.06)
    first, second = SHOUTS[shout]
    a = signal.sosfilt(formant_filter(first, scale), src)
    b = signal.sosfilt(formant_filter(second, scale), src)
    move = ramp(n, rng.uniform(0.1, 0.35) * n, rng.uniform(0.2, 0.45) * n)
    y = a * (1 - move) + b * move
    y = signal.sosfilt(signal.butter(1, 120, btype="high", fs=FS, output="sos"), y)
    attack = rng.uniform(0.03, 0.09) * FS
    release = rng.uniform(0.1, 0.3) * FS
    y *= ramp(n, 0, attack) * (1 - ramp(n, n - release, release))
    return y / (np.sqrt(np.mean(y ** 2)) + 1e-9)


def cheering(rng, crowd, when, people, kinds_mix, loudness=1.0):
    """people voices for each distance band; when(rng) gives a start time in seconds."""
    for kind, count in people.items():
        for _ in range(count):
            shout = rng.choice(list(kinds_mix), p=list(kinds_mix.values()))
            female = rng.random() < 0.45
            distance, pan = place(rng, kind)
            sound = voice(rng, shout, female)
            gain = loudness * 1.6 / distance * rng.uniform(0.45, 1.15)
            crowd.add(kind, when(rng) * FS, sound, gain, pan)


def whistle(rng):
    n = int(rng.uniform(0.45, 1.0) * FS)
    t = np.arange(n) / FS
    f = rng.uniform(2100, 3300) * (0.86 + 0.14 * (1 - np.exp(-t / 0.06)) - 0.06 * ramp(n, n - 0.15 * FS, 0.15 * FS))
    f *= 1 + 0.004 * smooth_noise(rng, n, 30)
    phase = 2 * np.pi * np.cumsum(f) / FS
    y = np.sin(phase) + 0.12 * np.sin(2 * phase) + 0.05 * rng.standard_normal(n)
    y *= ramp(n, 0, 0.03 * FS) * (1 - ramp(n, n - 0.08 * FS, 0.08 * FS)) * (1 + 0.15 * smooth_noise(rng, n, 12))
    return y / (np.sqrt(np.mean(y ** 2)) + 1e-9)


def whistles(rng, crowd, when, count, loudness=1.0):
    for _ in range(count):
        kind = rng.choice(["mid", "mid", "far"])
        distance, pan = place(rng, kind)
        crowd.add(kind, when(rng) * FS, whistle(rng), loudness * 0.9 / distance * rng.uniform(0.4, 1.0), pan)


def stadium_hum(rng, crowd, level):
    """Thousands of people at once, too many to hear one: shaped noise, in the far band."""
    n = crowd.n
    band = signal.butter(2, [180, 1500], btype="band", fs=FS, output="sos")
    swell = 1 + 0.25 * smooth_noise(rng, n, 1.5)
    for ch in range(2):
        crowd.dry["far"][:, ch] += signal.sosfilt(band, rng.standard_normal(n)) * swell * level


def opening(rng, response):
    """The cheer for a song starting. The music comes in at SONG_START seconds."""
    seconds, song = 7.0, 2.0
    crowd = Crowd(seconds)
    n = crowd.n
    mood = envelope(n, [(0, 0.15), (0.5, 0.65), (1.4, 0.9), (song, 1.0), (song + 0.8, 0.95),
                        (song + 2.2, 0.35), (song + 3.6, 0.08), (seconds, 0)])
    applause(rng, crowd, mood, {"near": 18, "mid": 260, "far": 1900})
    shouts = {"woo": 0.5, "yeah": 0.12, "hey": 0.08, "scream": 0.18, "oh": 0.12}

    def roar(r):  # most people cheer around the moment the song is recognised
        return float(np.clip(r.normal(song - 0.3, 0.75), 0.05, seconds - 1.8))

    cheering(rng, crowd, roar, {"near": 4, "mid": 70, "far": 260}, shouts)
    cheering(rng, crowd, lambda r: r.uniform(song, song + 3.0), {"mid": 14, "far": 40}, shouts, 0.6)
    whistles(rng, crowd, lambda r: r.uniform(0.3, song + 1.2), 5)
    stadium_hum(rng, crowd, 0.02)
    out = crowd.render(response)
    out *= (ramp(n, 0, 0.35 * FS) * (1 - ramp(n, n - 1.4 * FS, 1.4 * FS)))[:, None]
    return out, song


def closing(rng, response):
    """The ovation as a song ends: the last note is at 0 seconds."""
    seconds = 9.0
    crowd = Crowd(seconds)
    n = crowd.n
    mood = envelope(n, [(0, 0.0), (0.12, 0.55), (0.6, 0.95), (1.2, 1.0), (4.2, 0.92), (6.2, 0.45),
                        (7.6, 0.12), (seconds, 0)])
    applause(rng, crowd, mood, {"near": 22, "mid": 300, "far": 2300})
    shouts = {"woo": 0.52, "yeah": 0.14, "hey": 0.06, "scream": 0.16, "oh": 0.12}
    cheering(rng, crowd, lambda r: float(np.clip(r.gamma(2.0, 0.28), 0, 3.0)),
             {"near": 5, "mid": 90, "far": 320}, shouts, 1.1)
    cheering(rng, crowd, lambda r: r.uniform(1.0, 5.5), {"near": 2, "mid": 30, "far": 90}, shouts, 0.75)
    whistles(rng, crowd, lambda r: r.uniform(0.2, 4.5), 7)
    stadium_hum(rng, crowd, 0.025)
    out = crowd.render(response)
    out *= (ramp(n, 0, 0.02 * FS) * (1 - ramp(n, n - 2.0 * FS, 2.0 * FS)))[:, None]
    return out


def bed(rng, response):
    """The crowd during a song: a loop with no seam. Only the distant crowd, with no one voice or
    whistle that could be noticed coming round again; the player adds cheers at big moments."""
    seconds, overlap = 20.0, 2.5
    crowd = Crowd(seconds + overlap)
    shouts = {"woo": 0.45, "yeah": 0.2, "hey": 0.15, "scream": 0.05, "oh": 0.15}
    span = seconds + overlap
    cheering(rng, crowd, lambda r: r.uniform(-1, span), {"far": 520}, shouts, 0.5)
    stadium_hum(rng, crowd, 0.03)
    out = crowd.render(response)
    m = int(seconds * FS)
    k = int(overlap * FS)
    fade = ramp(k, 0, k)[:, None]
    loop = out[:m].copy()
    # The end of the render fades into the start of the loop.
    loop[:k] = out[m:m + k] * np.sqrt(1 - fade) + out[:k] * np.sqrt(fade)
    return loop


def mulaw(x):
    y = np.sign(x) * np.log1p(MU * np.abs(x)) / np.log1p(MU)
    return np.clip(np.round(y * 127), -127, 127).astype(np.int8)


def unmulaw(q):
    y = q.astype(np.float64) / 127
    return np.sign(y) * np.expm1(np.abs(y) * np.log1p(MU)) / MU


def section(tag, audio, rate, marker):
    """Resampled from FS, peak-normalized to -1dBFS, mu-law coded."""
    if rate != FS:
        g = np.gcd(rate, FS)
        audio = signal.resample_poly(audio, rate // g, FS // g, axis=0)
    audio = audio / np.max(np.abs(audio)) * 0.89
    coded = mulaw(audio)
    level = float(np.sqrt(np.mean(unmulaw(coded) ** 2)))
    head = struct.pack(">4siiiif", tag, rate, 2, len(audio), int(round(marker * rate)), level)
    return head, coded.tobytes(), dict(tag=tag.decode(), rate=rate, channels=2, frames=len(audio),
                                      seconds=round(len(audio) / rate, 3), marker_seconds=marker,
                                      rms=round(level, 5))


def main():
    rng = np.random.default_rng(20261101)
    response = stadium_response(rng)
    bed_audio = bed(np.random.default_rng(1101), response)
    open_audio, song_start = opening(np.random.default_rng(2202), response)
    close_audio = closing(np.random.default_rng(3303), response)
    parts = [section(b"BED ", bed_audio, BED_RATE, 0),
             section(b"OPEN", open_audio, EVENT_RATE, song_start),
             section(b"CLOS", close_audio, EVENT_RATE, 0)]
    blob = struct.pack(">4sii", b"GFLC", 1, len(parts)) + b"".join(p[0] for p in parts) + b"".join(p[1] for p in parts)
    out = ROOT / "app/src/main/assets/live_crowd.bin"
    out.write_bytes(blob)
    manifest = dict(
        name="Grapefruit Live Mode crowd v2",
        license="GPL-3.0-or-later",
        made_by="tools/live/render_crowd.py: original synthesis",
        recordings_used=False,
        sampled_audio_used=False,
        pretrained_models_used=False,
        encoding="GFLC v1: big-endian header, then per section signed 8-bit mu-law (mu=255), interleaved stereo",
        sections=[p[2] for p in parts],
        bytes=len(blob),
        sha256=hashlib.sha256(blob).hexdigest(),
        script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        numpy=np.__version__,
        seeds=dict(stadium=20261101, bed=1101, opening=2202, closing=3303),
    )
    (ROOT / "app/src/main/assets/live_crowd.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
