# Stereo karaoke separator — 4.0 beta 4

This replaces the earlier centre-only vocal subtraction engine. It keeps immediate on-device playback and API 17 compatibility. It does not require a new Android runtime library, remote separation service, or pre-processing a whole song.

## Implementation and FOSS status

The processor and newly trained model weights are GPL-3.0-or-later, like Grapefruit. No pretrained Demucs/FMA teacher weights are used by this model. PyTorch 2.5.1+cpu (BSD-3-Clause) is a training tool, not an APK dependency. The packaged model is 63,064 bytes; training source, export code, data splits and the model hash are published with the app source. The original training scripts remain for historical reproducibility and are marked as retired.

Training uses only **SiSEC18-MUS 7s Excerpts**, curated by Fabian-Robert Stöter, Antoine Liutkus and Nobutaka Ito: https://zenodo.org/records/3270814. The record explicitly licenses this excerpt dataset as **CC BY 4.0**, https://creativecommons.org/licenses/by/4.0/. This statement concerns these excerpts, not a licence to the complete underlying recordings. Audio is decoded/remixed for training and is not packaged in the app. Training and selection excerpts are attributed in the app; the complete split is in `app/src/main/assets/karaoke_training.json`.

The 94 available training-partition excerpts are deterministically split into 84 training and 10 model-selection clips (seed 137). Training includes source remixes, relative-level changes, vocal panning and instrumental-only examples. The separate 50 test-partition excerpts are not used for training, normalization or selecting the new model. The stems contain all vocals, not an annotation separating lead from backing singers: the processor cannot promise lead-only removal.

## Reproduce

Download `MUSDB18-7-STEMS.zip` from the record above and verify its checksum against the record. Extract `train/` and `test/`. With Python 3.12, NumPy, ffmpeg, and an isolated environment:

```sh
python -m venv --system-site-packages venv
venv/bin/pip install torch==2.5.1+cpu --index-url https://download.pytorch.org/whl/cpu
OPENBLAS_NUM_THREADS=1 venv/bin/python prepare_v4.py /path/to/extracted-stems /path/to/spectra
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=4 venv/bin/python stereo_v4.py /path/to/spectra /path/to/run --steps 6000 --vocal-weight 1 --mask-power 1.5
```

Run these scripts from `tools/karaoke`, or supply their complete paths. `prepare_v4.py` writes stereo vocal/backing STFT arrays; no unlabelled music is fetched. `stereo_v4.py` saves the best checkpoint by selection loss, 4-bit model, normalization, per-excerpt manifest, credits and synthetic Java/Python golden vectors. Copy `karaoke.bin`, `manifest.json` (as `karaoke_training.json`) and `karaoke_credits.txt` into the app assets, and the golden files into `app/src/test/resources/karaoke/`. Exact low-level floating-point results can vary with numerical libraries; golden tests allow small rounding differences. The shipped manifest records tool versions and model identity.

To compare against a saved pre-beta-4 model:

```sh
OPENBLAS_NUM_THREADS=1 venv/bin/python evaluate_v4.py /path/to/run /path/to/spectra --legacy /path/to/legacy-karaoke.bin
```

The previous model can be obtained from the pre-beta-4 source commit `23fc5bc48c2b7028a63bfa8b279d51c52d6da7dd`. Evaluation writes full per-clip values as well as summaries.

## Processing

A 2048-sample sine window advances by 1024 samples. One packed complex FFT recovers left/right and mid/side spectral views. Their log energies in 128 triangular mel bands feed a 64-value encoder, five-frame short context, two running memories, 96-value context and hidden layers, and 256 independent left/right mask outputs. Inference uses approximately 109,568 weight multiplies per frame, comparable to the retired model; the new path uses two real inverse FFTs. The calibrated mask exponent is 1.5, selected on the 10 selection clips to balance reduction and instrument loss. Each channel keeps its input phase. Exactly mono windows use averaged masks and identical channel spectra to preserve the centre image; a silent input channel remains silent.

The binary model format is version 5, with bounded dimensions and normalization checks. The stream delay remains 3072 samples (69.7 ms at 44.1 kHz), plus any existing limiter/mixer delay. The implementation reuses arrays during processing. Slider changes retain smoothing and engine toggles retain the existing crossfade. At zero strength the output is the delayed original.

## Measured comparison and limitations

The following are PCM-domain medians on the 50 seven-second test excerpts. Masks are inferred once from the mixture and then applied to the isolated references to measure vocal residual and backing removed. A larger “backing removed below original” value means less instrument energy was removed. These are energy metrics, not a listener score or a promise of transparency.

| Evaluation | Retired centre-only engine | New stereo engine |
| --- | ---: | ---: |
| Original mixes: vocal reduction | 4.15 dB | 5.13 dB |
| Original mixes: backing removed below original | 15.61 dB | 15.75 dB |
| Controlled hard-panned vocal: vocal reduction | 1.32 dB | 5.71 dB |
| Controlled hard-panned vocal: backing removed below original | 17.72 dB | 14.74 dB |

Thus the normal-mix median improves modestly; the larger change is in the controlled panning test, which also removes more backing. Individual clips can improve or regress. These short excerpts cannot establish quality across entire songs, genres or reverberant live recordings. The **retired model's documented recipe trained on its test partition**, so its side of this comparison is not an independent held-out benchmark; the new model did not train on these 50 clips. The detailed report is `evaluation-v4.json`.

A warmed host JVM processed 20 seconds of input in roughly 0.18–0.21 seconds under a 48 MB Java heap, with modestly higher processing time than the old engine in most rounds. This is a host check, not a benchmark of the user's Android 4.2.2 device. Immediate playback is retained architecturally; actual Jelly Bean throughput and subjective listening still need device confirmation. The 100% slider setting is not a claim of complete vocal removal.
