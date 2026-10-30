"""GPL-3.0-or-later stereo karaoke model: train, export and evaluate a small CPU network.

Uses only the CC BY 4.0 SiSEC18-MUS 7s Excerpts. No pretrained teacher or FMA audio.
See README-v4.md for exact split, preprocessing, provenance and evaluation limitations.
"""

import argparse, glob, hashlib, json, math, os, random, struct, sys, time
from pathlib import Path
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F

N = 2048
HOP = 1024
BANDS = 128
PAST = 3
FUTURE = 1
EMAS = (0.8, 0.95)
RATE = 44100
EPS = 1e-8
ENC = 64
HIDDEN = 96
sys.path.insert(0, str(Path(__file__).parent))
import kdsp


def triangles(rate=RATE):
    old = kdsp.BANDS
    kdsp.BANDS = BANDS
    try:
        return kdsp.triangles(rate)
    finally:
        kdsp.BANDS = old


TRI = triangles().astype(np.float32)
SPREAD = (TRI / TRI.sum(axis=0, keepdims=True)).T


def features(x):
    l, r = x[:, :, 0], x[:, :, 1]
    return np.concatenate(
        [
            np.log((abs(z) ** 2) @ TRI.T + EPS)
            for z in [l, r, (l + r) * 0.5, (l - r) * 0.5]
        ],
        axis=1,
    ).astype(np.float32)


def ema(e, a):
    t = torch.arange(e.shape[-1], dtype=e.dtype, device=e.device)
    scale = a**t
    s = scale * ((1 - a) * torch.cumsum(e / scale, dim=-1) + a * e[:, :, :1])
    idx = torch.clamp(torch.arange(e.shape[-1]) + FUTURE, max=e.shape[-1] - 1)
    return s[:, :, idx]


class Net(nn.Module):
    def __init__(self):
        super().__init__()
        self.enc = nn.Conv1d(4 * BANDS, ENC, 1)
        self.ctx = nn.Conv1d(ENC, HIDDEN, PAST + 1 + FUTURE)
        self.ema = nn.Conv1d(ENC * len(EMAS), HIDDEN, 1, bias=False)
        self.mid = nn.Conv1d(HIDDEN, HIDDEN, 1)
        self.out = nn.Conv1d(HIDDEN, 2 * BANDS, 1)

    def forward(self, x):
        e = F.relu(self.enc(x))
        h = self.ctx(F.pad(e, (PAST, FUTURE))) + self.ema(
            torch.cat([ema(e, a) for a in EMAS], dim=1)
        )
        h = F.dropout(F.relu(h), p=0.1, training=self.training)
        h = F.dropout(F.relu(self.mid(h)), p=0.1, training=self.training)
        return torch.sigmoid(self.out(h))


def load(p):
    d = np.load(p)
    return d["v"], d["a"], int(d["length"])


def example(v, a, wv):
    x = v + a
    ev = np.concatenate([(abs(v[:, :, c]) ** 2) @ TRI.T for c in [0, 1]], axis=1)
    ea = np.concatenate([(abs(a[:, :, c]) ** 2) @ TRI.T for c in [0, 1]], axis=1)
    target = wv * ev / (wv * ev + ea + EPS)
    weight = np.sqrt((wv * ev + ea) / (np.mean(wv * ev + ea) + EPS)).clip(0.1, 6)
    return features(x), target.astype(np.float32), weight.astype(np.float32)


def layer_arrays(net):
    d = {k: v.detach().cpu().numpy() for k, v in net.state_dict().items()}
    wc = d["ctx.weight"].transpose(0, 2, 1).reshape(HIDDEN, -1)
    wc = np.concatenate([wc, d["ema.weight"][:, :, 0]], axis=1)
    return [
        (d["enc.weight"][:, :, 0], d["enc.bias"]),
        (wc, d["ctx.bias"]),
        (d["mid.weight"][:, :, 0], d["mid.bias"]),
        (d["out.weight"][:, :, 0], d["out.bias"]),
    ]


def quantize(layers):
    out = []
    for w, b in layers:
        scale = np.maximum(abs(w).max(axis=1) / 7, 1e-12).astype(np.float32)
        q = np.round(w / scale[:, None]).clip(-7, 7).astype(np.int8)
        out.append(
            (q.astype(np.float64) * scale[:, None], b.astype(np.float64), scale, q)
        )
    return out


def predict(x, layers, mean, std):
    feat = (features(x).astype(np.float64) - mean) / std
    enc = np.maximum(feat @ layers[0][0].T + layers[0][1], 0)
    j, e = enc.shape
    t = j - FUTURE
    pad = np.concatenate([np.zeros((PAST, e)), enc, np.zeros((FUTURE, e))])
    ctx = np.stack([pad[k : k + PAST + 1 + FUTURE].reshape(-1) for k in range(t)])
    seqs = []
    for a in EMAS:
        state = enc[0].copy()
        seq = []
        for row in enc:
            state = a * state + (1 - a) * row
            seq.append(state.copy())
        seqs.append(np.asarray(seq)[FUTURE : FUTURE + t])
    ctx = np.concatenate([ctx] + seqs, axis=1)
    h = np.maximum(ctx @ layers[1][0].T + layers[1][1], 0)
    h = np.maximum(h @ layers[2][0].T + layers[2][1], 0)
    o = (h @ layers[3][0].T + layers[3][1]).clip(-50, 50)
    bands = 1 / (1 + np.exp(-o))
    masks = np.stack(
        [bands[:, c * BANDS : (c + 1) * BANDS] @ SPREAD.T for c in [0, 1]], axis=2
    )
    mono = np.all(x[: len(masks), :, 0] == x[: len(masks), :, 1], axis=1)
    masks[mono] = masks[mono].mean(axis=2, keepdims=True)
    return masks


def legacy(path):
    import export

    with open(path, "rb") as f:

        def ints(n):
            return struct.unpack(">" + str(n) + "i", f.read(4 * n))

        def floats(n):
            return np.frombuffer(f.read(4 * n), dtype=">f4").astype(np.float64)

        magic, version = ints(2)
        assert magic == 0x47464B4D and version == 4
        n, hop, b, past, future = ints(5)
        floats(3)
        floats(ints(1)[0])
        mean = floats(2 * b)
        std = floats(2 * b)
        lays = []
        for _ in range(4):
            outs, ins = ints(2)
            scale = floats(outs)
            bias = floats(outs)
            packed = np.frombuffer(f.read((outs * ins + 1) // 2), dtype=np.uint8)
            q = (
                np.stack([packed & 15, packed >> 4], axis=1)
                .reshape(-1)[: outs * ins]
                .astype(np.int8)
            )
            q[q >= 8] -= 16
            lays.append((q.reshape(outs, ins) * scale[:, None], bias))
    return lays, mean, std


def metrics(paths, net, norm, legacy_model=None):
    layers = quantize(layer_arrays(net))
    old = legacy(legacy_model) if legacy_model else None
    values = {"new": [], "legacy": []}
    for p in paths:
        v, a, length = load(p)
        x = v + a
        mask = predict(x, layers, norm["mean"], norm["std"]) ** float(
            norm.get("mask_power", 1.5)
        )
        t = len(mask)
        ev = abs(v[:t]) ** 2
        ea = abs(a[:t]) ** 2
        if ev.sum() < 1e-4:
            continue
        # STFT energy metrics use the same inferred mask for isolated references.
        q = 10 * np.log10(ev.sum() / ((ev * abs(1 - mask) ** 2).sum() + EPS))
        damage = 10 * np.log10((ea.sum() + EPS) / ((ea * mask**2).sum() + EPS))
        values["new"].append([float(q), float(damage)])
        if old:
            import export

            m = (x[:, :, 0] + x[:, :, 1]) * 0.5
            s = (x[:, :, 0] - x[:, :, 1]) * 0.5
            om = export.masks(m, s, old[0], old[1], old[2])
            tt = len(om)
            vm = (v[:tt, :, 0] + v[:tt, :, 1]) * 0.5
            am = (a[:tt, :, 0] + a[:tt, :, 1]) * 0.5
            residual = v[:tt] - om[:, :, None] * vm[:, :, None]
            oq = 10 * np.log10(
                (abs(v[:tt]) ** 2).sum() / ((abs(residual) ** 2).sum() + EPS)
            )
            od = 10 * np.log10(
                ((abs(a[:tt]) ** 2).sum() + EPS) / (2 * (abs(om * am) ** 2).sum() + EPS)
            )
            values["legacy"].append([float(oq), float(od)])
    return {
        k: {
            "count": len(v),
            "median_vocal_reduction_db": float(np.median(np.array(v)[:, 0])),
            "median_backing_removed_db_below_original": float(
                np.median(np.array(v)[:, 1])
            ),
            "per_clip": dict(zip([Path(p).stem for p in paths], v)),
        }
        for k, v in values.items()
        if v
    }


def write_model(path, net, norm):
    with open(path, "wb") as f:
        f.write(
            struct.pack(
                ">7i4fi",
                0x47464B4D,
                5,
                N,
                HOP,
                BANDS,
                PAST,
                FUTURE,
                60.0,
                16000.0,
                EPS,
                float(norm.get("mask_power", 1.5)),
                len(EMAS),
            )
        )
        for arr in [EMAS, norm["mean"], norm["std"]]:
            f.write(np.asarray(arr, dtype=">f4").tobytes())
        for _, b, scale, q in quantize(layer_arrays(net)):
            f.write(struct.pack(">ii", *q.shape))
            f.write(scale.astype(">f4").tobytes())
            f.write(b.astype(">f4").tobytes())
            flat = q.reshape(-1).astype(np.int16)
            if len(flat) % 2:
                flat = np.append(flat, 0)
            nib = (flat & 15).astype(np.uint8)
            f.write((nib[::2] | (nib[1::2] << 4)).tobytes())


def export_bundle(out, net, norm, manifest):
    import export, voice_synth

    write_model(out / "karaoke.bin", net, norm)
    x = voice_synth.song(1.5)
    m, side = export.spectra(x)
    spectra = np.stack([m + side, m - side], axis=2)
    mask = predict(
        spectra, quantize(layer_arrays(net)), norm["mean"], norm["std"]
    ) ** float(norm["mask_power"])
    estimate = np.stack(
        [
            export.overlap_add(spectra[: len(mask), :, c] * mask[:, :, c], len(x))
            for c in [0, 1]
        ],
        axis=1,
    )
    delay = N + FUTURE * HOP
    y = np.zeros_like(x)
    y[delay:] = x[:-delay] - estimate[:-delay]
    y *= np.clip((np.arange(len(x)) - delay) / 441, 0, 1)[:, None]
    for name, pcm in [("golden_in.bin", x), ("golden_out.bin", y)]:
        np.clip(np.round(pcm * 32768), -32768, 32767).astype(">i2").tofile(out / name)
    manifest.update(
        algorithm="Stereo vocal separator v1",
        model_format=5,
        model_license="GPL-3.0-or-later",
        data_license="CC-BY-4.0",
        dataset_creators=["Fabian-Robert Stöter", "Antoine Liutkus", "Nobutaka Ito"],
        training_framework="PyTorch (BSD-3-Clause, training only)",
        recipe_source="tools/karaoke/stereo_v4.py",
        recipe_source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        model_sha256=hashlib.sha256((out / "karaoke.bin").read_bytes()).hexdigest(),
    )
    (out / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"
    )
    rows = []
    for split in ["training", "validation"]:
        for name in sorted(manifest[split]):
            parts = name.split(" - ", 1)
            artist, title = parts if len(parts) == 2 else ("Dataset contributor", name)
            rows.append(
                "\t".join(
                    [
                        title + " (" + split + " excerpt)",
                        artist,
                        "CC BY 4.0 dataset",
                        "https://zenodo.org/records/3270814",
                    ]
                )
            )
    (out / "karaoke_credits.txt").write_text("\n".join(rows) + "\n")


def train(args):
    torch.set_num_threads(4)
    torch.use_deterministic_algorithms(True)
    torch.manual_seed(137)
    random.seed(137)
    rng = np.random.default_rng(137)
    paths = sorted(Path(args.data, "train").glob("*.npz"))
    rng.shuffle(paths)
    valid = paths[:10]
    train_paths = paths[10:]
    manifest = {
        "seed": 137,
        "training": [p.stem for p in train_paths],
        "validation": [p.stem for p in valid],
        "test": [p.stem for p in sorted(Path(args.data, "test").glob("*.npz"))],
        "vocal_weight": args.vocal_weight,
        "mask_power": args.mask_power,
        "steps": args.steps,
        "torch": torch.__version__,
        "numpy": np.__version__,
        "architecture": {"bands": BANDS, "encoded": ENC, "hidden": HIDDEN},
        "dataset": "https://zenodo.org/records/3270814",
        "dataset_license": "CC-BY-4.0",
    }
    out = Path(args.out)
    out.mkdir(exist_ok=True, parents=True)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    songs = [load(p) for p in train_paths]
    examples = []
    for i, (v, a, _) in enumerate(songs):
        examples.append(example(v, a, args.vocal_weight))
        for j in range(3):
            aa = songs[int(rng.integers(len(songs)))][1]
            length = min(len(v), len(aa))
            vv = v[:length].copy()
            aa = aa[:length].copy()
            vv *= 10 ** rng.uniform(-0.4, 0.4)
            pan = rng.uniform(0.4, 1.6)
            vv[:, :, 0] *= pan
            vv[:, :, 1] *= 2 - pan
            if j == 2 and i % 3 == 0:
                vv *= 0
            examples.append(example(vv, aa, args.vocal_weight))
    feats = np.concatenate([x[0] for x in examples])
    mean = feats.mean(0)
    std = np.maximum(feats.std(0), 0.25)
    norm = {"mean": mean, "std": std, "mask_power": np.float32(args.mask_power)}
    np.savez(out / "norm.npz", **norm)
    examples = [
        tuple(torch.from_numpy(y.T.copy()) for y in [(x - mean) / std, t, w])
        for x, t, w in examples
    ]
    validation = [example(*load(p)[:2], args.vocal_weight) for p in valid]
    validation = [
        tuple(torch.from_numpy(y.T.copy()[None]) for y in [(x - mean) / std, t, w])
        for x, t, w in validation
    ]
    net = Net()
    opt = torch.optim.Adam(net.parameters(), lr=0.001)
    best = 1e9
    start = time.time()
    for step in range(args.steps + 1):
        if step % 100 == 0:
            net.eval()
            with torch.no_grad():
                vl = np.mean(
                    [
                        float(
                            (
                                (net(x[:, :, :300]) - t[:, :, :300]) ** 2
                                * w[:, :, :300]
                            ).mean()
                        )
                        for x, t, w in validation
                    ]
                )
            if vl < best:
                best = vl
                torch.save(net.state_dict(), out / "best.pt")
            print(
                "step",
                step,
                "valid",
                round(vl, 6),
                "best",
                round(best, 6),
                "seconds",
                round(time.time() - start),
                flush=True,
            )
            net.train()
        rows = []
        for _ in range(16):
            x, t, w = examples[int(rng.integers(len(examples)))]
            at = int(rng.integers(max(1, x.shape[1] - 96)))
            rows.append([z[:, at : at + 96] for z in [x, t, w]])
        x, t, w = [torch.stack([r[k] for r in rows]) for k in range(3)]
        pred = net(x)
        loss = ((pred - t) ** 2 * w).mean()
        opt.zero_grad()
        loss.backward()
        opt.step()
    net.load_state_dict(torch.load(out / "best.pt", weights_only=True))
    net.eval()
    export_bundle(out, net, norm, manifest)
    report = metrics(valid, net, norm, args.legacy)
    (out / "validation.json").write_text(json.dumps(report, indent=2))
    print(
        "VALIDATION",
        json.dumps(
            {
                k: {kk: vv for kk, vv in v.items() if kk != "per_clip"}
                for k, v in report.items()
            }
        ),
        flush=True,
    )


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("data")
    p.add_argument("out")
    p.add_argument("--steps", type=int, default=6000)
    p.add_argument("--vocal-weight", type=float, default=1.0)
    p.add_argument("--mask-power", type=float, default=1.5)
    p.add_argument("--legacy", default="app/src/main/assets/karaoke.bin")
    train(p.parse_args())
