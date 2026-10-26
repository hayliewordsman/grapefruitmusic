"""Karaoke Mode signal processing, exactly as the app does it (VocalRemover.java mirrors this).

Mid M = (L+R)/2 and side S = (L-R)/2 are cut into Hann-windowed frames (N=2048, hop 512). Each
frame's power is summed into B=96 triangular mel bands (60 Hz to 16 kHz); the model sees
log band energies of M and S, normalized, for frames t-4 .. t+2, and outputs a vocal mask per band
for frame t. The band mask is spread back to bins with the same triangles (normalized), the
vocal estimate is mask * M (mixture phase), and it's subtracted from both channels.
"""
import numpy as np

N = 2048
HOP = 512
BANDS = 96
FMIN = 60.0
FMAX = 16000.0
PAST = 4
FUTURE = 2
CONTEXT = PAST + 1 + FUTURE
EPS = 1e-8


def window():
    n = np.arange(N)
    return (0.5 - 0.5 * np.cos(2 * np.pi * n / N)).astype(np.float64)


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
