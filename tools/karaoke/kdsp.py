"""Karaoke Mode signal processing, exactly as the app does it (VocalRemover.java mirrors this).

Version 2 (Grapefruit 3.2): half the frames of version 1, so it needs far less of the tablet's
time. Mid M = (L+R)/2 and side S = (L-R)/2 are cut into frames of N=2048 samples, a new one every
HOP=1024, under a sine window (the square root of a Hann window), used again when the vocal
estimate is put back together: at half-overlap the two windows add up to exactly 1. Each frame's
power is summed into B=96 triangular mel bands (60 Hz to 16 kHz); the model sees log band energies
of M and S, normalized, for frames t-3 .. t+1 (and, with EMAS, slowly-moving averages of its own
encoding, for longer memory), and outputs a vocal mask per band for frame t, and how sharply to
favour sound in the middle within each band. The band mask, spread back to bins with the same
triangles (normalized), times centredness^sharpness (computed with the fast power below, as the
app does), times M (mixture phase) is the vocal estimate; it's subtracted from both channels.
"""
import numpy as np

VERSION = 4
N = 2048
HOP = 1024
BANDS = 96
FMIN = 60.0
FMAX = 16000.0
PAST = 3
FUTURE = 1
CONTEXT = PAST + 1 + FUTURE
EPS = 1e-8
# Per-frame decays of the encoding's moving averages (empty: none).
EMAS = (0.8, 0.95)

# x^s as 2^(s log2 x), each part a polynomial on float bits (no exp or log per frequency bin):
# fitted on [1, 2) and [0, 1), errors 2e-4 in log2 and 7e-6 relative in 2^f.
LOG2 = np.array([-0.07915037, 0.31221426, -0.6695152, 1.4360981, 0.00020415891], dtype=np.float32)
EXP2 = np.array([0.013676531, 0.051666877, 0.24171026, 0.6929313, 1.0000073], dtype=np.float32)


def window():
    """The periodic sine window: sin(pi n / N). Squared, it's a Hann window."""
    n = np.arange(N)
    return np.sin(np.pi * n / N).astype(np.float64)


def fast_log2(x):
    """log2 of positive float32s, as VocalRemover.log2 computes it."""
    x = np.asarray(x, dtype=np.float32)
    bits = x.view(np.int32)
    e = ((bits >> 23) & 0xFF) - 127
    m = ((bits & 0x7FFFFF) | 0x3F800000).view(np.float32) - np.float32(1)
    p = LOG2[0]
    for c in LOG2[1:]:
        p = p * m + c
    return (e.astype(np.float32) + p).astype(np.float32)


def fast_exp2(y):
    """2^y for float32 y <= 0 (down to 2^-126), as VocalRemover.exp2 computes it."""
    y = np.maximum(np.asarray(y, dtype=np.float32), np.float32(-126))
    i = np.floor(y).astype(np.int32)
    f = (y - i.astype(np.float32)).astype(np.float32)
    p = EXP2[0]
    for c in EXP2[1:]:
        p = p * f + c
    scale = ((i + 127) << 23).astype(np.int32).view(np.float32)
    return (scale * p).astype(np.float32)


def fast_pow(x, s):
    """x^s for x in (0, 1] and s >= 0, in float32."""
    return fast_exp2(np.asarray(s, dtype=np.float32) * fast_log2(x))


def mel(f):
    return 2595.0 * np.log10(1.0 + f / 700.0)


def unmel(m):
    return 700.0 * (10.0 ** (m / 2595.0) - 1.0)


def band_centers(rate):
    top = min(FMAX, 0.48 * rate)
    m = np.linspace(mel(FMIN), mel(top), BANDS)
    return unmel(m)


def triangles(rate):
    """(BANDS, N/2+1): triangle b peaks at center b and reaches 0 at its neighbours' centers.
    The first and last bands extend flat to the ends, so every bin belongs somewhere."""
    c = band_centers(rate)
    f = np.arange(N // 2 + 1) * rate / N
    t = np.zeros((BANDS, N // 2 + 1))
    for b in range(BANDS):
        lo = c[b - 1] if b > 0 else -1.0
        hi = c[b + 1] if b < BANDS - 1 else 1e9
        for k, fk in enumerate(f):
            if fk <= c[b]:
                if b == 0:
                    t[b, k] = 1.0
                elif fk > lo:
                    t[b, k] = (fk - lo) / (c[b] - lo)
            else:
                if b == BANDS - 1:
                    t[b, k] = 1.0
                elif fk < hi:
                    t[b, k] = (hi - fk) / (hi - c[b])
    return t


def spread(rate):
    """(N/2+1, BANDS): bin mask = spread @ band mask (triangles normalized per bin)."""
    t = triangles(rate)
    return (t / t.sum(axis=0, keepdims=True)).T


def frames(x):
    """x: (samples,) -> (T, N) windowed frames, frame t starting at t*HOP."""
    count = 1 + (len(x) - N) // HOP
    idx = np.arange(N)[None, :] + HOP * np.arange(count)[:, None]
    return x[idx] * window()[None, :]
