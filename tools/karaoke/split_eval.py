"""How much quieter the lead vocal gets, and how much of the backing goes with it (per song, dB).

Usage: split_eval.py run_dir "npz/test/*.npz"   (or run_dir valid: the clips held back in training)
"""
import glob
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(__file__))
import export  # noqa: E402
import kdsp  # noqa: E402
from train import Net  # noqa: E402


def main():
    run, paths = sys.argv[1], sys.argv[2:]
    norm = torch.load(os.path.join(run, "norm.pt"))
    mean = norm["mean"].numpy()[:, 0].astype(np.float32).astype(np.float64)
    std = norm["std"].numpy()[:, 0].astype(np.float32).astype(np.float64)
    net = Net()
    net.load_state_dict(torch.load(os.path.join(run, "best.pt")))
    lays = export.quantized_layers(net)
    D = kdsp.N + kdsp.FUTURE * kdsp.HOP
    quieter, damage = [], []
    files = norm["valid"] if paths == ["valid"] else sorted(glob.glob(paths[0]))
    for p in files:
        d = np.load(p)
        v = d["vocals"].astype(np.float64)
        a = d["accompaniment"].astype(np.float64)
        if np.sum(v ** 2) < 1e-3:
            continue
        # The app's mask, from the mix, applied to each stem's mid separately (masking is linear).
        M, S = export.spectra(v + a)
        mask = export.masks(M, S, lays, mean, std)
        T = mask.shape[0]
        Mv, _ = export.spectra(v)
        Ma, _ = export.spectra(a)
        ev = export.overlap_add(Mv[:T] * mask, len(v))
        ea = export.overlap_add(Ma[:T] * mask, len(a))
        quieter.append(10 * np.log10(np.sum(v[D:] ** 2) / np.sum((v[D:] - ev[D:, None]) ** 2)))
        damage.append(10 * np.log10(np.sum(a[D:] ** 2) / (2 * np.sum(ea[D:] ** 2) + 1e-12)))
    q, dm = np.array(quieter), np.array(damage)
    print("%s on %d songs: lead vocal %.1f dB quieter (median; 25-75%%: %.1f to %.1f); backing removed is "
          "%.1f dB below the backing (median)" % (run, len(q), np.median(q), np.percentile(q, 25),
                                                 np.percentile(q, 75), np.median(dm)))


if __name__ == "__main__":
    main()
