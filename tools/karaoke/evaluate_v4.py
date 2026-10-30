"""GPL-3.0-or-later: PCM-domain separation comparison, including a controlled hard-pan stress test.
The legacy model trained on MUSDB test material: this is NOT an independent legacy benchmark.
"""

import sys, json, numpy as np, torch, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import stereo_v4 as s
import export
import argparse

parser = argparse.ArgumentParser()
parser.add_argument("run", type=Path)
parser.add_argument("spectra", type=Path)
parser.add_argument("--legacy", required=True, type=Path)
args = parser.parse_args()
root = args.run
net = s.Net()
net.load_state_dict(torch.load(root / "best.pt", weights_only=True))
net.eval()
norm = dict(np.load(root / "norm.npz"))
layers = s.quantize(s.layer_arrays(net))
old = s.legacy(args.legacy)
rows = []
started = time.time()
for p in sorted((args.spectra / "test").glob("*.npz")):
    v, a, length = s.load(p)
    for scenario in ["original", "hard-panned-vocal"]:
        if scenario == "hard-panned-vocal":
            pan = np.stack([v[:, :, 0] * np.sqrt(2), np.zeros_like(v[:, :, 0])], axis=2)
            pan *= np.sqrt((abs(v) ** 2).sum() / ((abs(pan) ** 2).sum() + s.EPS))
            v = pan
        x = v + a
        mask = s.predict(x, layers, norm["mean"], norm["std"]) ** float(
            norm["mask_power"]
        )
        T = len(mask)
        sl = slice(s.N, T * s.HOP - s.HOP)
        vp = np.stack([export.overlap_add(v[:T, :, c], length) for c in [0, 1]], axis=1)
        ap = np.stack([export.overlap_add(a[:T, :, c], length) for c in [0, 1]], axis=1)
        if (vp[sl] ** 2).sum() < 1e-4:
            continue
        for alg in ["new", "legacy"]:
            if alg == "new":
                vr = np.stack(
                    [
                        export.overlap_add(v[:T, :, c] * mask[:, :, c], length)
                        for c in [0, 1]
                    ],
                    axis=1,
                )
                ar = np.stack(
                    [
                        export.overlap_add(a[:T, :, c] * mask[:, :, c], length)
                        for c in [0, 1]
                    ],
                    axis=1,
                )
            else:
                om = export.masks(
                    (x[:, :, 0] + x[:, :, 1]) * 0.5,
                    (x[:, :, 0] - x[:, :, 1]) * 0.5,
                    *old
                )
                vr = export.overlap_add((v[:T, :, 0] + v[:T, :, 1]) * 0.5 * om, length)[
                    :, None
                ]
                ar = export.overlap_add((a[:T, :, 0] + a[:T, :, 1]) * 0.5 * om, length)[
                    :, None
                ]
            q = float(
                10
                * np.log10((vp[sl] ** 2).sum() / (((vp - vr)[sl] ** 2).sum() + s.EPS))
            )
            dm = float(
                10
                * np.log10(
                    ((ap[sl] ** 2).sum() + s.EPS)
                    / (((ar[sl] ** 2).sum() * (2 if alg == "legacy" else 1)) + s.EPS)
                )
            )
            rows.append(
                dict(
                    clip=p.stem,
                    scenario=scenario,
                    algorithm=alg,
                    vocal_reduction_db=q,
                    backing_removed_db_below_original=dm,
                )
            )
summary = {}
for scenario in ["original", "hard-panned-vocal"]:
    summary[scenario] = {}
    for alg in ["legacy", "new"]:
        data = [r for r in rows if r["scenario"] == scenario and r["algorithm"] == alg]
        summary[scenario][alg] = dict(
            count=len(data),
            median_vocal_reduction_db=float(
                np.median([r["vocal_reduction_db"] for r in data])
            ),
            median_backing_removed_db_below_original=float(
                np.median([r["backing_removed_db_below_original"] for r in data])
            ),
        )
(root / "evaluation.json").write_text(
    json.dumps(
        dict(
            summary=summary,
            clips=rows,
            notes="Test clips were not used in training/selection of the new model. The legacy model documentation says these clips were part of its training.",
        ),
        indent=2,
    )
)
print(json.dumps(summary, indent=2))
print("seconds", round(time.time() - started))
