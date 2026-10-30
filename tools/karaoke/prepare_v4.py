"""GPL-3.0-or-later: decode CC BY 4.0 7-second excerpts into deterministic stereo STFT arrays.
Usage: python prepare_v4.py extracted_STEMS_directory output_spectra_directory
Requires NumPy and ffmpeg. No audio from this cache is packaged in the Android app.
"""

from pathlib import Path
import concurrent.futures, subprocess, numpy as np, tempfile
import argparse

p = argparse.ArgumentParser()
p.add_argument("source", type=Path)
p.add_argument("output", type=Path)
args = p.parse_args()
root = args.output.parent
out = args.output
out.mkdir(exist_ok=True, parents=True)


def prepare(p):
    target = out / p.parent.name / (p.name.removesuffix(".stem.mp4") + ".npz")
    target.parent.mkdir(exist_ok=True)
    if target.exists():
        return
    with tempfile.TemporaryDirectory(dir=root) as tmp:
        a = Path(tmp) / "a.raw"
        v = Path(tmp) / "v.raw"
        subprocess.run(
            [
                "ffmpeg",
                "-v",
                "error",
                "-threads",
                "1",
                "-i",
                str(p),
                "-filter_complex_threads",
                "1",
                "-filter_complex",
                "[0:a:1][0:a:2][0:a:3]amix=inputs=3:normalize=0[a]",
                "-map",
                "[a]",
                "-f",
                "f32le",
                "-ac",
                "2",
                "-ar",
                "44100",
                str(a),
                "-map",
                "0:a:4",
                "-f",
                "f32le",
                "-ac",
                "2",
                "-ar",
                "44100",
                str(v),
            ],
            check=True,
        )
        aa = np.fromfile(a, dtype="<f4").reshape(-1, 2)
        vv = np.fromfile(v, dtype="<f4").reshape(-1, 2)
        length = min(len(aa), len(vv))
        aa = aa[:length]
        vv = vv[:length]

        def spec(x):
            x = np.concatenate([np.zeros((1024, 2), dtype=np.float32), x])
            idx = np.arange(2048)[None, :] + 1024 * np.arange(length // 1024)[:, None]
            return np.fft.rfft(
                x[idx] * np.sin(np.pi * np.arange(2048) / 2048)[None, :, None], axis=1
            ).astype(np.complex64)

        np.savez(target, v=spec(vv), a=spec(aa), length=length)
    print(p.parent.name, target.name, flush=True)


files = sorted(args.source.glob("*/*.stem.mp4"))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(prepare, files))
print("Prepared", len(files), "licensed stem spectra", flush=True)
