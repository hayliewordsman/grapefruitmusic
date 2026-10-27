"""Trains the Karaoke Mode vocal mask.

Usage: [FMA=fma_npz_dir] [LOSS=complex|mag] [CENTRED=1|0] [AUG=0|1] [DROPOUT=0.1] [EXTRA=more_npz_dir]
       [INIT=run_dir LR=3e-4] [VOCAL_WEIGHT=1] train.py npz_dir out_dir [steps]

Learns from the MUSDB18 7 s excerpts (CC BY 4.0; npz_dir/train, true vocal and backing tracks) and,
with FMA, from Free Music Archive songs that Demucs split (teacher.py): hours more music, labelled by
a far bigger model. A batch draws MUSDB clips MUSDB_SHARE of the time. FMA songs don't all fit in
memory, so ROTATE of them are held at once and swapped for others every ROTATE_STEPS steps.

LOSS=complex measures what's left of the vocal after subtracting the estimate (as export.py's score
does); mag compares compressed magnitudes. CENTRED adds the per-bin "how much is in the middle" cue.
AUG pitches vocals and tilts tone. EXTRA adds more clips to training (validation stays the same 10
MUSDB clips, real stems). INIT starts from another run's model (fine-tuning), at learning rate LR.
VOCAL_WEIGHT > 1 makes vocal left behind count more than backing taken with it (bolder removal).
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
HIDDEN = int(os.environ.get("HIDDEN", "128"))
ENCODED = int(os.environ.get("ENCODED", "64"))
SEG_FRAMES = 96
BATCH = 16
MUSDB_SHARE = float(os.environ.get("MUSDB_SHARE", "0.35"))
ROTATE = int(os.environ.get("ROTATE", "300"))
ROTATE_STEPS = int(os.environ.get("ROTATE_STEPS", "1500"))

torch.manual_seed(1)
random.seed(1)
np.random.seed(1)
# The shipped model: LOSS=complex, CENTRED=1, AUG=0, DROPOUT=0.1, EXTRA=npz/test (see README).
LOSS = os.environ.get("LOSS", "complex")
CENTRED = os.environ.get("CENTRED", "1") == "1"
AUG = os.environ.get("AUG", "0") == "1"
DROPOUT = float(os.environ.get("DROPOUT", "0.1"))
# How much more vocal left behind counts than backing taken with it (1: the same).
VOCAL_WEIGHT = float(os.environ.get("VOCAL_WEIGHT", "1"))


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


def moving_averages(enc):
    """enc (n, E, T) -> (n, E * len(EMAS), T): for frame t, each average of the encoding up to frame
    t + FUTURE (the newest the app has), starting from the first frame, as the app does."""
    if not kdsp.EMAS:
        return enc[:, :0]
    T = enc.shape[2]
    outs = []
    for a in kdsp.EMAS:
        e = enc[:, :, 0]
        seq = []
        for j in range(T):
            e = a * e + (1 - a) * enc[:, :, j]
            seq.append(e)
        s = torch.stack(seq, dim=2)
        # Frame t uses the average at t + FUTURE (the last frames reuse the newest).
        idx = torch.clamp(torch.arange(T) + kdsp.FUTURE, max=T - 1)
        outs.append(s[:, :, idx])
    return torch.cat(outs, dim=1)


class Net(nn.Module):
    """Band vocal mask (sigmoid) and, per band, how sharply to favour centred bins (softplus).

    Encoding per frame -> the encodings of frames t-PAST .. t+FUTURE plus their moving averages ->
    HIDDEN -> HIDDEN -> masks."""

    def __init__(self, dropout=0.0):
        super().__init__()
        self.enc = nn.Conv1d(2 * kdsp.BANDS, ENCODED, 1)
        self.ctx = nn.Conv1d(ENCODED, HIDDEN, kdsp.CONTEXT)
        self.ema = nn.Conv1d(ENCODED * len(kdsp.EMAS), HIDDEN, 1, bias=False) if kdsp.EMAS else None
        self.mid = nn.Conv1d(HIDDEN, HIDDEN, 1)
        self.out = nn.Conv1d(HIDDEN, 2 * kdsp.BANDS, 1)
        self.drop = nn.Dropout(dropout)

    def forward(self, feats):
        e = F.relu(self.enc(feats))
        h = self.ctx(F.pad(e, (kdsp.PAST, kdsp.FUTURE)))
        if self.ema is not None:
            h = h + self.ema(moving_averages(e))
        h = self.drop(F.relu(h))
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


def load(paths, dtype=np.float32):
    out = []
    for p in paths:
        d = np.load(p)
        out.append((d["vocals"].astype(dtype), d["accompaniment"].astype(dtype)))
    return out


def sung(tracks, threshold_db=-20.0):
    """Indexes of the songs whose vocal is at least threshold_db against the whole song (FMA has many
    instrumentals: Demucs finds next to no vocal in them). Their backing is still used."""
    out = []
    for i, (v, a) in enumerate(tracks):
        step = 441
        ev = float(np.mean(v[::step].astype(np.float32) ** 2))
        ea = float(np.mean(a[::step].astype(np.float32) ** 2))
        if ev > 0 and 10 * np.log10(ev / (ev + ea + 1e-12)) >= threshold_db:
            out.append(i)
    return out


def batch(tracks, frames=SEG_FRAMES, size=BATCH, extra=None, extra_sung=None):
    """size random training mixes. extra: a second pool (FMA songs) drawn 1 - MUSDB_SHARE of the time,
    its singers taken from extra_sung (indexes of its songs with a vocal)."""
    length = (frames - 1) * kdsp.HOP + kdsp.N
    mixes, vocs, accs = [], [], []
    pool = tracks
    for _ in range(size):
        use_extra = bool(extra) and random.random() > MUSDB_SHARE
        tracks = extra if use_extra else pool
        if use_extra and extra_sung and random.random() < 0.92:
            vi = random.choice(extra_sung)
        else:
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
        v = v[vs:vs + length].astype(np.float32)
        a = a[as_:as_ + length].astype(np.float32)
        if AUG and random.random() < 0.4:
            v = tilt(v)
        if AUG and random.random() < 0.4:
            a = tilt(a)
        if ai != vi and random.random() < 0.3:
            # A second song's backing on top: busier mixes.
            a2 = tracks[random.randrange(len(tracks))][1]
            s2 = random.randrange(0, len(a2) - length)
            a = a + a2[s2:s2 + length].astype(np.float32) * random.uniform(0.3, 0.8)
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
    """Band mask spread to bins, times centredness |M|^2/(|M|^2+|S|^2) to the band's power.
    (Exact powers here; the app's fast ones are within 0.1%.)"""
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
    aspec = stft((a[..., 0] + a[..., 1]) / 2)
    amid = aspec.abs()
    f = (features(mid, side) - mean) / std
    bands, sharp = net(f)                            # (n, B, T) each
    mask = bin_mask(bands, sharp, mid, side)
    # Only frames with all their context (as in a song the app is playing).
    keep = slice(kdsp.PAST, mid.shape[2] - kdsp.FUTURE)
    mask, mid, vspec, vmid, amid = mask[..., keep], mid[..., keep], vspec[..., keep], vmid[..., keep], amid[..., keep]
    aspec = aspec[..., keep]
    if LOSS == "complex":
        # What's left of the vocal after subtracting the estimate, relative to the vocal (as the
        # evaluation measures it), plus a little of the compressed loss to keep quiet parts honest.
        if VOCAL_WEIGHT != 1:
            # The error split: vocal left behind, and backing taken with it, weighted.
            err = VOCAL_WEIGHT * ((mask - 1) * vspec).abs() ** 2 + (mask * aspec).abs() ** 2
        else:
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
    fma_paths = sorted(glob.glob(os.path.join(os.environ["FMA"], "*.npz"))) if os.environ.get("FMA") else []
    print("FMA songs", len(fma_paths), flush=True)

    def fma_pool():
        return load(random.sample(fma_paths, min(ROTATE, len(fma_paths))), np.float16) if fma_paths else None

    fma = fma_pool()
    fma_sung = sung(fma) if fma else None
    if fma:
        print("FMA songs held: %d, with a vocal: %d" % (len(fma), len(fma_sung)), flush=True)
    # The features' scale from both kinds of music.
    mean, std = stats(train + ([(v.astype(np.float32), a.astype(np.float32)) for v, a in fma[:60]] if fma else []))
    torch.save({"mean": mean, "std": std, "valid": valid_paths}, os.path.join(out, "norm.pt"))
    net = Net(dropout=DROPOUT)
    if os.environ.get("INIT"):
        # Fine-tuning: start from another run's model, and its feature scale.
        net.load_state_dict(torch.load(os.path.join(os.environ["INIT"], "best.pt")))
        prior = torch.load(os.path.join(os.environ["INIT"], "norm.pt"))
        mean, std = prior["mean"], prior["std"]
        torch.save({"mean": mean, "std": std, "valid": valid_paths}, os.path.join(out, "norm.pt"))
    print("parameters", sum(p.numel() for p in net.parameters()), flush=True)
    opt = torch.optim.AdamW(net.parameters(), lr=float(os.environ.get("LR", "1e-3")), weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: 0.5 * (1 + math.cos(math.pi * min(s, steps) / steps)))
    rng_state = random.getstate()
    random.seed(99)
    valid_batches = [batch(valid, 128, 8) for _ in range(6)]
    random.setstate(rng_state)
    best = float("inf")
    t0 = time.time()
    for s in range(1, steps + 1):
        if fma and s % ROTATE_STEPS == 0:
            fma = None
            fma = fma_pool()
            fma_sung = sung(fma)
        v, a = batch(train, extra=fma, extra_sung=fma_sung)
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
