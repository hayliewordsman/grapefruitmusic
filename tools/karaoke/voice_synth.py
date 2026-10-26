import numpy as np

def resonator(x, f, bw, rate):
    r = np.exp(-np.pi * bw / rate); th = 2 * np.pi * f / rate
    a1, a2 = -2 * r * np.cos(th), r * r
    y = np.zeros_like(x); y1 = y2 = 0.0
    g = 1 - r
    for i in range(len(x)):
        yi = g * x[i] - a1 * y1 - a2 * y2
        y[i] = yi; y2 = y1; y1 = yi
    return y

def song(seconds=3.0, rate=44100, seed=5):
    rng = np.random.default_rng(seed)
    n = int(seconds * rate); t = np.arange(n) / rate
    notes = 220 * 2 ** (np.array([0, 3, 5, 7, 5, 3]) / 12)
    f0 = notes[(t / 0.5).astype(int) % len(notes)] * (1 + 0.015 * np.sin(2 * np.pi * 5.5 * t))
    phase = np.cumsum(f0) / rate
    pulses = (phase % 1.0) * 2 - 1                    # sawtooth glottal source
    pulses += rng.standard_normal(n) * 0.05            # breath
    vowels = [(730, 1090, 2440), (270, 2290, 3010), (300, 870, 2240), (530, 1840, 2480)]
    voice = np.zeros(n)
    for k in range(int(np.ceil(seconds / 0.5))):
        a, b = int(k * 0.5 * rate), min(n, int((k + 1) * 0.5 * rate))
        seg = pulses[a:b]
        f1, f2, f3 = vowels[k % len(vowels)]
        v = resonator(seg, f1, 90, rate) * 1.0 + resonator(seg, f2, 110, rate) * 0.6 + resonator(seg, f3, 160, rate) * 0.3
        env = np.minimum(1, np.minimum(np.arange(len(seg)) / 1500, (len(seg) - np.arange(len(seg))) / 3000))
        env[int(0.38 * rate):] = 0                        # a gap between syllables
        voice[a:b] = v * env
    voice *= 0.25 / (np.abs(voice).max() + 1e-9)
    chord = sum(np.sign(np.sin(2 * np.pi * f * t)) * 0.02 for f in (130.8, 164.8, 196.0))
    hats = np.zeros(n)
    for k in range(int(seconds * 4)):
        a = int(k * rate / 4); hats[a:a + 2000] += rng.standard_normal(min(2000, n - a)) * np.exp(-np.arange(min(2000, n - a)) / 300) * 0.08
    bass = np.sin(2 * np.pi * 55 * t) * 0.12
    left = voice + chord * 1.5 + hats * 0.6 + bass
    right = voice + chord * 0.5 + hats * 1.4 + bass
    x = np.stack([left, right], axis=1)
    return (np.round(np.clip(x, -1, 1) * 32767) / 32768.0).astype(np.float64)
