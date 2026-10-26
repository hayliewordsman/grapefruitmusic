"""Decodes MUSDB18 7 s stems (mix, drums, bass, other, vocals) to float32 npz: vocals and accompaniment."""
import os, subprocess, sys, glob
import numpy as np

def stream(path, index):
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-map", "0:a:%d" % index, "-f", "f32le",
                          "-ac", "2", "-ar", "44100", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).reshape(-1, 2)

root, dest = sys.argv[1], sys.argv[2]
for split in ("train", "test"):
    os.makedirs(os.path.join(dest, split), exist_ok=True)
    for path in sorted(glob.glob(os.path.join(root, split, "*.stem.mp4"))):
        name = os.path.basename(path)[:-len(".stem.mp4")]
        stems = [stream(path, i) for i in range(1, 5)]  # drums, bass, other, vocals
        n = min(len(s) for s in stems)
        acc = sum(s[:n] for s in stems[:3])
        voc = stems[3][:n]
        np.savez_compressed(os.path.join(dest, split, name + ".npz"), vocals=voc, accompaniment=acc)
        print(split, name, n / 44100.0, flush=True)
