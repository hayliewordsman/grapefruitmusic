"""How the score changes with fewer bits per weight (per-row symmetric quantization)."""
import sys, os, glob, numpy as np, torch
sys.path.insert(0, os.path.dirname(__file__))
import export, kdsp
from train import Net

def quant_layers(net, bits):
    levels = 2 ** (bits - 1) - 1
    out = []
    for w, b in export.layers(net):
        peak = np.abs(w).max(axis=1); scale = np.where(peak > 0, peak / levels, 1.0)
        q = np.clip(np.round(w / scale[:, None]), -levels, levels)
        out.append((q * scale[:, None], b.astype(np.float32).astype(np.float64)))
    return out

def score(lays, mean, std, paths):
    D = kdsp.N + kdsp.FUTURE * kdsp.HOP; g = []
    for p in paths:
        d = np.load(p); v, a = d["vocals"].astype(np.float64), d["accompaniment"].astype(np.float64)
        if np.sum(v ** 2) < 1e-3: continue
        est = export.vocal_estimate(v + a, lays, mean, std)
        g.append(10 * np.log10(np.sum(v[D:] ** 2) / np.sum((v[D:] - est[D:, None]) ** 2)))
    return np.array(g)

run, which = sys.argv[1], sys.argv[2]
norm = torch.load(os.path.join(run, "norm.pt"))
mean = norm["mean"].numpy()[:, 0].astype(np.float32).astype(np.float64)
std = norm["std"].numpy()[:, 0].astype(np.float32).astype(np.float64)
net = Net(); net.load_state_dict(torch.load(os.path.join(run, "best.pt")))
paths = norm["valid"] if which == "valid" else sorted(glob.glob("npz/test/*.npz"))
for bits in (8, 6, 5, 4):
    g = score(quant_layers(net, bits), mean, std, paths)
    print("%s %s %d-bit: median %.2f mean %.2f" % (run, which, bits, np.median(g), g.mean()), flush=True)
