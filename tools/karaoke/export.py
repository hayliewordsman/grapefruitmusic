"""Exports a trained Karaoke Mode model for the app, checks it, and writes test vectors.

Usage: export.py run_dir npz_dir out_dir
Writes out_dir/karaoke.bin (4-bit weights), out_dir/golden_in.bin, out_dir/golden_out.bin and
prints how much vocal the model removes on the 50 MUSDB18 test excerpts.
"""
import glob
import os
import struct
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(__file__))
import kdsp  # noqa: E402
from train import Net, RATE  # noqa: E402

FADE = 441


LEVELS = 7  # 4-bit weights, -7 .. 7: as good as 8-bit here (see bits_eval.py), half the size


def quantize(w):
    """Per output row symmetric 4-bit. w: (out, in) -> (scale (out,), q (out, in) int8 in -7..7)."""
    peak = np.abs(w).max(axis=1)
    scale = np.where(peak > 0, peak / LEVELS, 1.0).astype(np.float32)
    q = np.clip(np.round(w / scale[:, None]), -LEVELS, LEVELS).astype(np.int8)
    return scale, q


def pack(q):
    """Two weights a byte, low nibble first (two's complement nibbles); odd counts pad a zero."""
    flat = q.reshape(-1).astype(np.int16)
    if len(flat) % 2:
        flat = np.append(flat, 0)
    nib = (flat & 0xF).astype(np.uint8)
    return (nib[0::2] | (nib[1::2] << 4)).astype(np.uint8).tobytes()


def layers(net):
    """(weight (out, in) float, bias) per layer, in the app's input order."""
    sd = {k: v.detach().numpy().astype(np.float64) for k, v in net.state_dict().items()}
    if os.environ.get("CENTRED", "1") != "1":
        # Trained without the centredness cue: its sharpness outputs are untrained; make them 0.
        sd["out.weight"][kdsp.BANDS:] = 0
        sd["out.bias"][kdsp.BANDS:] = -40
    enc = (sd["enc.weight"][:, :, 0], sd["enc.bias"])
    w = sd["ctx.weight"]                       # (out, enc, context): app wants [out][frame][enc]
    wc = w.transpose(0, 2, 1).reshape(w.shape[0], -1)
    if "ema.weight" in sd:
        # The moving averages come after the frames: [out][average][enc].
        wc = np.concatenate([wc, sd["ema.weight"][:, :, 0]], axis=1)
    ctx = (wc, sd["ctx.bias"])
    mid = (sd["mid.weight"][:, :, 0], sd["mid.bias"])
    out = (sd["out.weight"][:, :, 0], sd["out.bias"])
    return [enc, ctx, mid, out]


def quantized_layers(net):
    out = []
    for w, b in layers(net):
        scale, q = quantize(w)
        out.append((q.astype(np.float64) * scale[:, None].astype(np.float64), b.astype(np.float32).astype(np.float64),
                    scale, q, b.astype(np.float32)))
    return out


def write_model(path, net, mean, std):
    with open(path, "wb") as f:
        f.write(struct.pack(">ii", 0x47464B4D, kdsp.VERSION))
        f.write(struct.pack(">iiiii", kdsp.N, kdsp.HOP, kdsp.BANDS, kdsp.PAST, kdsp.FUTURE))
        f.write(struct.pack(">fff", kdsp.FMIN, kdsp.FMAX, kdsp.EPS))
        f.write(struct.pack(">i", len(kdsp.EMAS)))
        f.write(np.asarray(kdsp.EMAS, dtype=">f4").tobytes())
        f.write(np.asarray(mean, dtype=">f4").tobytes())
        f.write(np.asarray(std, dtype=">f4").tobytes())
        for _, _, scale, q, b in quantized_layers(net):
            f.write(struct.pack(">ii", q.shape[0], q.shape[1]))
            f.write(scale.astype(">f4").tobytes())
            f.write(b.astype(">f4").tobytes())
            f.write(pack(q))


def spectra(x):
    """x: (samples, 2) on the app's timeline -> mid and side spectra (J, K), frame j ending at (j+1)*HOP."""
    N, H = kdsp.N, kdsp.HOP
    xx = np.concatenate([np.zeros((N - H, 2)), x.astype(np.float64)])
    mid = (xx[:, 0] + xx[:, 1]) * 0.5
    side = (xx[:, 0] - xx[:, 1]) * 0.5
    J = len(x) // H
    w = kdsp.window()
    idx = np.arange(N)[None, :] + H * np.arange(J)[:, None]
    return np.fft.rfft(mid[idx] * w, axis=1), np.fft.rfft(side[idx] * w, axis=1)


def masks(M, S, lays, mean, std, rate=RATE):
    """Bin masks (T, K) for the frames the app finishes (all but the last FUTURE), as the app makes them."""
    B, P, F = kdsp.BANDS, kdsp.PAST, kdsp.FUTURE
    tri = kdsp.triangles(rate)
    em = (np.abs(M) ** 2) @ tri.T
    es = (np.abs(S) ** 2) @ tri.T
    feats = np.concatenate([np.log(em + kdsp.EPS), np.log(es + kdsp.EPS)], axis=1)
    feats = (feats - mean[None, :]) / std[None, :]
    (we, be), (wc, bc), (wm, bm), (wo, bo) = [(l[0], l[1]) for l in lays]
    enc = np.maximum(feats @ we.T + be, 0)                      # (J, E)
    J, E = enc.shape
    padded = np.concatenate([np.zeros((P, E)), enc, np.zeros((F, E))])
    T = J - F                                                   # frames the app finishes
    ctx = np.stack([padded[t:t + P + 1 + F].reshape(-1) for t in range(T)])
    if kdsp.EMAS:
        avgs = []
        for a in kdsp.EMAS:
            e = enc[0].copy()
            seq = np.zeros_like(enc)
            for j in range(J):
                e = a * e + (1 - a) * enc[j]
                seq[j] = e
            avgs.append(seq[F:F + T])                           # frame t: the average at t + FUTURE
        ctx = np.concatenate([ctx] + avgs, axis=1)
    h = np.maximum(ctx @ wc.T + bc, 0)
    h = np.maximum(h @ wm.T + bm, 0)
    o = h @ wo.T + bo
    bands = 1 / (1 + np.exp(-o[:, :B]))
    sharp = np.logaddexp(0, o[:, B:])                           # softplus
    spread = kdsp.spread(rate).T
    pm = (np.abs(M[:T]) ** 2).astype(np.float32)
    ps = (np.abs(S[:T]) ** 2).astype(np.float32)
    centred = pm / (pm + ps + np.float32(kdsp.EPS))
    return (bands @ spread) * kdsp.fast_pow(np.maximum(centred, np.float32(1e-12)), (sharp @ spread).astype(np.float32))


def overlap_add(X, length):
    """Masked spectra (T, K) back to sound on the app's timeline (sine window again: adds up to 1)."""
    N, H = kdsp.N, kdsp.HOP
    T = X.shape[0]
    v = np.fft.irfft(X, n=N, axis=1) * kdsp.window()[None, :]
    out = np.zeros(length + N)
    for t in range(T):
        start = (t + 1) * H - N
        a = max(start, 0)
        out[a:start + N] += v[t, a - start:]
    return out[:length]


def vocal_estimate(x, lays, mean, std, rate=RATE):
    """x: (samples, 2). The vocal estimate on the app's timeline: v[s] for s = 0..len-1."""
    M, S = spectra(x)
    mask = masks(M, S, lays, mean, std, rate)
    return overlap_add(M[:mask.shape[0]] * mask, len(x))


def app_output(x, lays, mean, std, amount=1.0, rate=RATE):
    """What the app plays: dry delayed by the latency, minus the vocal, faded in at the start."""
    D = kdsp.N + kdsp.FUTURE * kdsp.HOP
    v = vocal_estimate(x, lays, mean, std, rate)
    y = np.zeros_like(x, dtype=np.float64)
    y[D:] = x[:-D] - amount * v[:-D, None]
    gain = np.clip((np.arange(len(x)) - D) / FADE, 0, 1)
    return y * gain[:, None]


def synthetic(seconds=3.0, rate=RATE, seed=5):
    """A made-up song: a sung-like tone in the middle, panned chords and noise, a bass line."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(seconds * rate)) / rate
    f0 = 220 * 2 ** (np.round(4 * np.sin(2 * np.pi * 0.5 * t)) / 12) * (1 + 0.01 * np.sin(2 * np.pi * 5.5 * t))
    phase = 2 * np.pi * np.cumsum(f0) / rate
    voice = sum(np.sin(h * phase) / h for h in range(1, 12)) * 0.15 * (0.6 + 0.4 * np.sin(2 * np.pi * 2 * t) ** 2)
    chord = sum(np.sin(2 * np.pi * f * t) for f in (261.6, 329.6, 392.0)) * 0.05
    noise = rng.standard_normal(len(t)) * 0.02
    bass = np.sin(2 * np.pi * 55 * t) * 0.2
    left = voice + chord * 1.4 + noise + bass
    right = voice + chord * 0.6 - noise + bass
    x = np.stack([left, right], axis=1)
    return (np.round(np.clip(x, -1, 1) * 32767) / 32768.0).astype(np.float64)


def evaluate(lays, mean, std, npz):
    """Per test song: dB of vocal taken away (how much smaller the error vs the backing is)."""
    D = kdsp.N + kdsp.FUTURE * kdsp.HOP
    gains = []
    for p in sorted(glob.glob(os.path.join(npz, "test", "*.npz"))):
        d = np.load(p)
        v, a = d["vocals"].astype(np.float64), d["accompaniment"].astype(np.float64)
        if np.sum(v ** 2) < 1e-3:
            continue
        est = vocal_estimate(v + a, lays, mean, std)
        err = v - est[:, None]
        sl = slice(D, None)
        gains.append(10 * np.log10(np.sum(v[sl] ** 2) / np.sum(err[sl] ** 2)))
    return np.array(gains)


def main():
    run, npz, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    norm = torch.load(os.path.join(run, "norm.pt"))
    mean = norm["mean"].numpy()[:, 0].astype(np.float32).astype(np.float64)
    std = norm["std"].numpy()[:, 0].astype(np.float32).astype(np.float64)
    net = Net()
    net.load_state_dict(torch.load(os.path.join(run, "best.pt")))
    full = [(w, b) for w, b in layers(net)]
    quant = quantized_layers(net)
    g_full = evaluate(full, mean, std, npz)
    g_q = evaluate(quant, mean, std, npz)
    print("test songs %d: vocal error reduced by median %.2f dB (mean %.2f, worst %.2f, best %.2f); as shipped (4-bit): median %.2f dB"
          % (len(g_full), np.median(g_full), g_full.mean(), g_full.min(), g_full.max(), np.median(g_q)))
    write_model(os.path.join(out, "karaoke.bin"), net, mean, std)
    print("model bytes", os.path.getsize(os.path.join(out, "karaoke.bin")))
    import voice_synth
    x = voice_synth.song(1.5)
    y = app_output(x, quant, mean, std, 1.0)
    pcm = np.round(x * 32768).astype(">i2")
    pcm.tofile(os.path.join(out, "golden_in.bin"))
    np.clip(np.round(y * 32768), -32768, 32767).astype(">i2").tofile(os.path.join(out, "golden_out.bin"))
    D = kdsp.N + kdsp.FUTURE * kdsp.HOP
    print("golden: %d frames, removed rms %.4f of input %.4f" % (
        len(x), np.sqrt(np.mean((y[D + FADE:] - x[FADE:-D]) ** 2)), np.sqrt(np.mean(x ** 2))))


if __name__ == "__main__":
    main()
