# Live Mode — 4.1 beta 1

Live Mode is a local, real-time concert simulation: a song-derived vocal ensemble,
venue reflections, and a procedural audience/applause bed. It is not a text-to-singing
model and does not invent lyrics. With instrumental music, the venue and crowd bed
still run; the separator may leak instruments into the ensemble. No remote inference,
network audio collection, microphone access, or extra Android runtime is involved.

## Sound path

The existing GPL-3.0-or-later stereo separator runs once, at full strength, to estimate
vocals. Its delayed original and estimated backing share a time base. Eight short,
slowly modulated delay taps turn the estimated vocals into a dispersed, slightly
detuned crowd chorus; the original words and melody come from the playing song.
Short stereo feedback paths supply venue reflections. A trained AR-12 statistical
model shapes independently generated noise, with procedural clap bursts and a slow
cheer swell. The crowd bed follows musical activity and decays in silence. This
provides a stylized crowd impression, not a faithful audience performance.

The strength slider blends the complete live addition from 0 to 100% in 1% steps,
with a 25 ms glide. At 0%, output is the delayed original (or the current Karaoke
mix). Enabling Live Mode retains the separator's approximately 70 ms latency at
44.1 kHz; it does not preprocess a whole song. Karaoke and Live Mode can operate
together: Karaoke adjusts the foreground vocal, while Live Mode adds a crowd choir.
Mono Audio is applied after the live mix. The existing limiter bounds the final output.
Effect toggles crossfade; seek/format reset clears audience and venue history.

## New model and original data

`train.py` fits an order-12 autoregressive predictor using regularized least squares
on 24 two-second original procedural clips at 8 kHz. Each clip synthesizes 24 varied
harmonic/formant voices with independent syllabic envelopes plus noise-burst applause.
This is **48 seconds of synthetic training audio**, not licensed recordings of crowds.
Eight independent seeds (16 seconds) are held out for evaluation, with no selection
partition or pretrained crowd weights. The deterministic seeds, versions, hashes,
pole bound, and evaluation results are in `app/src/main/assets/live_training.json`.
The script regenerates the data; no audio dataset is downloaded or packaged.

Code, procedural training data, and exported model are GPL-3.0-or-later under the
repository LICENSE. NumPy 2.3.5 (BSD-3-Clause) is used only by the training script.
There are no sampled performers, scraped videos, commercial sound libraries,
voice clones, or additional third-party audio rights to clear for the new model.
Users' songs are processed on-device and are not collected for training.

The reused vocal separator has separate provenance: CC BY 4.0 SiSEC18-MUS 7-second
excerpts by Fabian-Robert Stöter, Antoine Liutkus and Nobutaka Ito, 84 training clips,
10 selection clips and 50 held-out evaluation clips. Attribution is retained in
About → Karaoke Mode and the existing assets. See [the separator recipe](../karaoke/README-v4.md).
No new training or claim of ownership over those recordings is made here.

## Reproduce

From the repository root, using Python 3 and NumPy 2.3.5:

```sh
OPENBLAS_NUM_THREADS=1 python3 tools/live/train.py
```

This regenerates `LiveCrowdModel.java` and `live_training.json`. Training performs no
network requests. The model's poles are bounded below the unit circle for stable
synthesis. Synthetic held-out normalized prediction error is reported in the manifest;
it measures spectral predictability, **not realism, intelligibility, or listener preference**.
The model provides crowd texture; the separate vocal-doubling path provides singalong.

## Beta limits

No real-crowd benchmark or human listening panel has been run. Source separation can
leak instruments, miss voices, or sound less convincing for reverberant recordings.
Repeated/delayed vocals are an approximation of people singing together, not newly
performed backing vocals. Hardware listening, target Jelly Bean throughput, and UI
appearance at large font sizes still need device confirmation. Host unit tests and
performance checks cannot establish these. APK minimum API 17 and signing identity
remain unchanged; no new runtime dependencies were added.
