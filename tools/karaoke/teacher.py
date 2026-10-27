"""Labels music with Demucs (htdemucs, MIT licence) for Karaoke Mode v2 to learn from.

The MUSDB18 7-second excerpts, the only open recordings with separate vocal tracks, add up to 17
minutes: too little for Karaoke Mode to learn much. Demucs, a large separation model, splits any
song into vocals and the rest about as well as the best published systems; it's far too big and
slow for a tablet, but it can label hours of openly licensed music (fma_fetch.py) for Grapefruit's
small model to learn from.

    python3 teacher.py mp3_dir npz_dir

Writes npz_dir/<name>.npz with "vocals" (Demucs' estimate) and "accompaniment" (the mix minus
that), float16 stereo at 44.1 kHz: the same layout as decode.py's MUSDB clips.
"""
import glob
import os
import subprocess
import sys
import time

import numpy as np
import torch
from demucs.apply import apply_model
from demucs.pretrained import get_model


def decode(path):
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "2", "-ar", "44100", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).reshape(-1, 2)


def main():
    src, dest = sys.argv[1], sys.argv[2]
    os.makedirs(dest, exist_ok=True)
    torch.set_num_threads(int(os.environ.get("THREADS", "4")))
    model = get_model("htdemucs")
    model.eval()
    vocals_index = model.sources.index("vocals")
    paths = sorted(glob.glob(os.path.join(src, "*.mp3")))
    started = time.time()
    seconds = 0.0
    for i, path in enumerate(paths):
        name = os.path.splitext(os.path.basename(path))[0]
        out = os.path.join(dest, name + ".npz")
        if os.path.exists(out):
            continue
        try:
            mix = decode(path)
        except subprocess.CalledProcessError:
            print("skip (can't decode)", name, flush=True)
            continue
        if len(mix) < 44100 * 5:
            continue
        x = torch.from_numpy(mix.T.copy())
        ref = x.mean(0)
        mean, std = ref.mean(), ref.std() + 1e-8
        with torch.no_grad():
            stems = apply_model(model, ((x - mean) / std)[None], shifts=0, overlap=0.25, progress=False)[0]
        vocals = (stems[vocals_index] * std + mean).numpy().T
        vocals = vocals[:len(mix)]
        np.savez_compressed(out, vocals=vocals.astype(np.float16), accompaniment=(mix - vocals).astype(np.float16))
        seconds += len(mix) / 44100.0
        elapsed = time.time() - started
        print("%d/%d %s  %.1fx real time" % (i + 1, len(paths), name, seconds / max(elapsed, 1e-6)), flush=True)


if __name__ == "__main__":
    main()
