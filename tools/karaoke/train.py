"""Trains the Karaoke Mode vocal mask on MUSDB18 7 s excerpts (CC BY 4.0).

Usage: [LOSS=complex|mag] [CENTRED=1|0] [AUG=0|1] [DROPOUT=0.1] [EXTRA=more_npz_dir] train.py npz_dir out_dir [steps]

LOSS=complex measures what's left of the vocal after subtracting the estimate (as export.py's score
does); mag compares compressed magnitudes. CENTRED adds the per-bin "how much is in the middle" cue.
AUG pitches vocals and tilts tone. EXTRA adds more clips to training (validation stays the same 10).
"""
import glob
import math
import os
import random
import sys
import time

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(__file__))
import kdsp  # noqa: E402

RATE = 44100
HIDDEN = 192
ENCODED = 96
SEG_FRAMES = 64
BATCH = 16

torch.manual_seed(1)
random.seed(1)
np.random.seed(1)
# The shipped model: LOSS=complex, CENTRED=1, AUG=0, DROPOUT=0.1, EXTRA=npz/test (see README).
LOSS = os.environ.get("LOSS", "complex")
CENTRED = os.environ.get("CENTRED", "1") == "1"
AUG = os.environ.get("AUG", "0") == "1"
DROPOUT = float(os.environ.get("DROPOUT", "0.1"))


def pitch(x, semitones):
    """Resamples a (len, 2) segment: higher and shorter, or lower and longer (linear interpolation)."""
    ratio = 2 ** (semitones / 12)
    n = int(len(x) / ratio)
    pos = np.arange(n) * ratio
    i = np.minimum(pos.astype(int), len(x) - 2)
    f = (pos - i)[:, None]
    return (x[i] * (1 - f) + x[i + 1] * f).astype(np.float32)


def tilt(x, rate=44100):
    """A random gentle tone change: a one-pole high or low shelf mixed in (done in the spectrum)."""
    k = random.uniform(-0.5, 0.5)
    a = np.exp(-2 * np.pi * random.uniform(200, 3000) / rate)
    X = np.fft.rfft(x, axis=0)
    w = 2 * np.pi * np.arange(X.shape[0]) / len(x)
    low = (1 - a) / (1 - a * np.exp(-1j * w))
    shape = 1 + k * (low if random.random() < 0.5 else 1 - low)
    return np.fft.irfft(X * shape[:, None], n=len(x), axis=0).astype(np.float32)


class Net(nn.Module):
    """Band vocal mask (sigmoid) and, per band, how sharply to favour centred bins (softplus)."""

    def __init__(self, dropout=0.0):
        super().__init__()
        self.enc = nn.Conv1d(2 * kdsp.BANDS, ENCODED, 1)
        self.ctx = nn.Conv1d(ENCODED, HIDDEN, kdsp.CONTEXT)
        self.mid = nn.Conv1d(HIDDEN, HIDDEN, 1)
        self.out = nn.Conv1d(HIDDEN, 2 * kdsp.BANDS, 1)
        self.drop = nn.Dropout(dropout)

    def forward(self, feats):
        h = F.relu(self.enc(feats))
        h = F.pad(h, (kdsp.PAST, kdsp.FUTURE))
        h = self.drop(F.relu(self.ctx(h)))
        h = self.drop(F.relu(self.mid(h)))
        o = self.out(h)
        return torch.sigmoid(o[:, :kdsp.BANDS]), F.softplus(o[:, kdsp.BANDS:])


WINDOW = torch.tensor(kdsp.window(), dtype=torch.float32)
TRI = torch.tensor(kdsp.triangles(RATE), dtype=torch.float32)          # (B, K)
SPREAD = torch.tensor(kdsp.spread(RATE), dtype=torch.float32)          # (K, B)


def stft(x):
    """x: (batch, samples) -> complex (batch, K, T)."""
    return torch.stft(x, kdsp.N, kdsp.HOP, window=WINDOW, center=False, return_complex=True)


def features(mid, side):
    """Complex spectra (batch, K, T) -> log band energies (batch, 2B, T)."""
    em = torch.einsum("bk,nkt->nbt", TRI, mid.abs() ** 2)
    es = torch.einsum("bk,nkt->nbt", TRI, side.abs() ** 2)
    return torch.cat([torch.log(em + kdsp.EPS), torch.log(es + kdsp.EPS)], dim=1)


def load(paths):
    out = []
    for p in paths:
        d = np.load(p)
        out.append((d["vocals"].astype(np.float32), d["accompaniment"].astype(np.float32)))
    return out


def batch(tracks, frames=SEG_FRAMES, size=BATCH):
    length = (frames - 1) * kdsp.HOP + kdsp.N
    mixes, vocs, accs = [], [], []
    for _ in range(size):
        vi = random.randrange(len(tracks))
        ai = vi if random.random() < 0.4 else random.randrange(len(tracks))
        v, a = tracks[vi][0], tracks[ai][1]
        vs = random.randrange(0, len(v) - length)
        as_ = vs if ai == vi else random.randrange(0, len(a) - length)
        if AUG and random.random() < 0.5:
            # A different singer: pitch the vocal up or down (take a longer or shorter piece first).
            st = random.uniform(-3, 3)
            need = int(length * 2 ** (st / 12)) + 2
            if need < len(v):
                vs = random.randrange(0, len(v) - need)
                v = pitch(v[vs:vs + need], st)[:length]
                vs = 0
        v = v[vs:vs + length].copy()
        a = a[as_:as_ + length].copy()
        if AUG and random.random() < 0.4:
            v = tilt(v)
        if AUG and random.random() < 0.4:
            a = tilt(a)
        if ai != vi and random.random() < 0.3:
            # A second song's backing on top: busier mixes.
            a2 = tracks[random.randrange(len(tracks))][1]
            s2 = random.randrange(0, len(a2) - length)
            a = a + a2[s2:s2 + length] * random.uniform(0.3, 0.8)
        if random.random() < 0.3:
            # Pan the backing a little (vocals stay where they were mixed).
            pan = random.uniform(-0.3, 0.3)
            a = a * np.array([1 - pan, 1 + pan], dtype=np.float32)
        if random.random() < 0.5:
            v = v[:, ::-1]
        if random.random() < 0.5:
            a = a[:, ::-1]
        gv = 10 ** (random.uniform(-8, 4) / 20)
        if random.random() < 0.08:
            gv = 0.0  # instrumental: nothing to remove
        g = 10 ** (random.uniform(-10, 3) / 20)
        v = v * gv * g
        a = a * g
        vocs.append(v)
        accs.append(a)
        mixes.append(v + a)
    v = torch.tensor(np.stack(vocs))      # (n, len, 2)
    a = torch.tensor(np.stack(accs))
    return v, a


def bin_mask(bands, sharp, mid, side):
    """Band mask spread to bins, times centredness |M|^2/(|M|^2+|S|^2) to the band's power."""
    if not CENTRED:
        return torch.einsum("kb,nbt->nkt", SPREAD, bands)
    pm = mid.abs() ** 2
    centred = pm / (pm + side.abs() ** 2 + kdsp.EPS)
    gamma = torch.einsum("kb,nbt->nkt", SPREAD, sharp)
    return torch.einsum("kb,nbt->nkt", SPREAD, bands) * torch.exp(gamma * torch.log(centred + 1e-12))


def step_loss(net, v, a, mean, std):
    mix = v + a
    mid = stft((mix[..., 0] + mix[..., 1]) / 2)
    side = stft((mix[..., 0] - mix[..., 1]) / 2)
    vspec = stft((v[..., 0] + v[..., 1]) / 2)
    vmid = vspec.abs()
    amid = stft((a[..., 0] + a[..., 1]) / 2).abs()
    f = (features(mid, side) - mean) / std
    bands, sharp = net(f)                            # (n, B, T) each
    mask = bin_mask(bands, sharp, mid, side)
    if LOSS == "complex":
        # What's left of the vocal after subtracting the estimate, relative to the vocal (as the
        # evaluation measures it), plus a little of the compressed loss to keep quiet parts honest.
        err = (mask * mid - vspec).abs() ** 2
        rel = err.sum(dim=(1, 2)) / ((vspec.abs() ** 2).sum(dim=(1, 2)) + (mid.abs() ** 2).sum(dim=(1, 2)) * 3e-2)
        mag = mid.abs()
        p = 0.3
        lv = ((mask * mag + 1e-9) ** p - (vmid + 1e-9) ** p) ** 2
        return rel.mean() + 0.5 * lv.mean() / 0.02
    mag = mid.abs()
    p = 0.3
    lv = ((mask * mag + 1e-9) ** p - (vmid + 1e-9) ** p) ** 2
    la = (((1 - mask) * mag + 1e-9) ** p - (amid + 1e-9) ** p) ** 2
    return lv.mean() + la.mean()


def stats(tracks):
    """Mean and std of each feature over the training songs (as they are)."""
    feats = []
    for v, a in tracks:
        x = torch.tensor(v + a)
        mid = stft((x[:, 0] + x[:, 1]).unsqueeze(0) / 2)
        side = stft((x[:, 0] - x[:, 1]).unsqueeze(0) / 2)
        feats.append(features(mid, side)[0])
    f = torch.cat(feats, dim=1)
    return f.mean(dim=1, keepdim=True), f.std(dim=1, keepdim=True) + 1e-3


def main():
    npz, out = sys.argv[1], sys.argv[2]
    steps = int(sys.argv[3]) if len(sys.argv) > 3 else 20000
    os.makedirs(out, exist_ok=True)
    paths = sorted(glob.glob(os.path.join(npz, "train", "*.npz")))
    random.Random(7).shuffle(paths)
    valid_paths, train_paths = paths[:10], paths[10:]
    if os.environ.get("EXTRA"):
        # The shipped model also learns from the test clips (after they've been used to report).
        train_paths += sorted(glob.glob(os.path.join(os.environ["EXTRA"], "*.npz")))
    print("training clips", len(train_paths), flush=True)
    train, valid = load(train_paths), load(valid_paths)
    mean, std = stats(train)
    torch.save({"mean": mean, "std": std, "valid": valid_paths}, os.path.join(out, "norm.pt"))
    net = Net(dropout=DROPOUT)
    print("parameters", sum(p.numel() for p in net.parameters()), flush=True)
    opt = torch.optim.AdamW(net.parameters(), lr=1e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: 0.5 * (1 + math.cos(math.pi * min(s, steps) / steps)))
    rng_state = random.getstate()
    random.seed(99)
    valid_batches = [batch(valid, 128, 8) for _ in range(6)]
    random.setstate(rng_state)
    best = float("inf")
    t0 = time.time()
    for s in range(1, steps + 1):
        v, a = batch(train)
        loss = step_loss(net, v, a, mean, std)
        opt.zero_grad()
        loss.backward()
        opt.step()
        sched.step()
        if s % 250 == 0 or s == 1:
            net.eval()
            with torch.no_grad():
                vl = sum(step_loss(net, vv, aa, mean, std).item() for vv, aa in valid_batches) / len(valid_batches)
            net.train()
            if vl < best:
                best = vl
                torch.save(net.state_dict(), os.path.join(out, "best.pt"))
            torch.save(net.state_dict(), os.path.join(out, "last.pt"))
            print("step %d train %.5f valid %.5f best %.5f %.0fs" % (s, loss.item(), vl, best, time.time() - t0),
                  flush=True)


if __name__ == "__main__":
    main()
