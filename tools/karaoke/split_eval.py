"""How much quieter the lead vocal gets, and how much of the backing goes with it (per song, dB)."""
import sys, os, glob, numpy as np, torch
sys.path.insert(0, os.path.dirname(__file__))
import export, kdsp
from train import Net

def masked_estimates(mix, v, a, lays, mean, std, rate=44100):
    # The app's mask, from the mix; applied to each stem's mid separately (masking is linear).
    N, H, B, P, F = kdsp.N, kdsp.HOP, kdsp.BANDS, kdsp.PAST, kdsp.FUTURE
    def spec(x):
        xx = np.concatenate([np.zeros((N - H, 2)), x])
        m = (xx[:, 0] + xx[:, 1]) * 0.5; s = (xx[:, 0] - xx[:, 1]) * 0.5
        J = len(x) // H
        idx = np.arange(N)[None, :] + H * np.arange(J)[:, None]
        w = kdsp.window()
        return np.fft.rfft(m[idx] * w, axis=1), np.fft.rfft(s[idx] * w, axis=1)
    M, S = spec(mix); Mv, _ = spec(v); Ma, _ = spec(a)
    tri = kdsp.triangles(rate)
    feats = np.concatenate([np.log((np.abs(M) ** 2) @ tri.T + kdsp.EPS), np.log((np.abs(S) ** 2) @ tri.T + kdsp.EPS)], 1)
    feats = (feats - mean) / std
    (we, be), (wc, bc), (wm, bm), (wo, bo) = [(l[0], l[1]) for l in lays]
    enc = np.maximum(feats @ we.T + be, 0); E = enc.shape[1]; J = len(enc)
    padded = np.concatenate([np.zeros((P, E)), enc, np.zeros((F, E))]); T = J - F
    ctx = np.stack([padded[t:t + P + 1 + F].reshape(-1) for t in range(T)])
    h = np.maximum(np.maximum(ctx @ wc.T + bc, 0) @ wm.T + bm, 0); o = h @ wo.T + bo
    spread = kdsp.spread(rate).T
    pm = np.abs(M[:T]) ** 2; centred = pm / (pm + np.abs(S[:T]) ** 2 + kdsp.EPS)
    mask = ((1 / (1 + np.exp(-o[:, :B]))) @ spread) * np.exp((np.logaddexp(0, o[:, B:]) @ spread) * np.log(centred + 1e-12))
    def ola(X):
        v = np.fft.irfft(X * mask, n=N, axis=1) * kdsp.window()[None, :] / 1.5
        out = np.zeros(len(mix) + N)
        for t in range(T):
            st = (t + 1) * H - N; a0 = max(st, 0); out[a0:st + N] += v[t, a0 - st:]
        return out[:len(mix)]
    return ola(Mv[:T]), ola(Ma[:T])

run = sys.argv[1]; paths = sys.argv[2:]
norm = torch.load(os.path.join(run, "norm.pt"))
mean = norm["mean"].numpy()[:, 0].astype(np.float32).astype(np.float64)
std = norm["std"].numpy()[:, 0].astype(np.float32).astype(np.float64)
net = Net(); net.load_state_dict(torch.load(os.path.join(run, "best.pt")))
lays = export.quantized_layers(net)
D = kdsp.N + kdsp.FUTURE * kdsp.HOP
quieter, damage = [], []
files = norm["valid"] if paths == ["valid"] else sorted(glob.glob(paths[0]))
for p in files:
    d = np.load(p); v = d["vocals"].astype(np.float64); a = d["accompaniment"].astype(np.float64)
    if np.sum(v ** 2) < 1e-3: continue
    ev, ea = masked_estimates(v + a, v, a, lays, mean, std)
    quieter.append(10 * np.log10(np.sum(v[D:] ** 2) / np.sum((v[D:] - ev[D:, None]) ** 2)))
    damage.append(10 * np.log10(np.sum(a[D:] ** 2) / (2 * np.sum(ea[D:] ** 2) + 1e-12)))
q, dm = np.array(quieter), np.array(damage)
print("%s on %d songs: lead vocal %.1f dB quieter (median; 25-75%%: %.1f to %.1f); backing removed is %.1f dB below the backing (median)"
      % (run, len(q), np.median(q), np.percentile(q, 25), np.percentile(q, 75), np.median(dm)))
