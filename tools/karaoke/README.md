# Karaoke training recipes

**4.0 beta 4 ships the new stereo separator:** see [README-v4.md](README-v4.md) for its training recipe, licensing, evaluation and limits. The material below documents the retired centre-only separator and its historical data; it is not the provenance of the current model.

---

# Karaoke Mode model

Grapefruit's Karaoke Mode (`app/src/main/java/com/grapefruitmusic/playback/VocalRemover.java`) runs a
small neural network that estimates, for each 46 ms window of sound (a new one every 23 ms), how much of
each of 96 frequency bands of the middle of the mix is lead vocal. The model ships as
`app/src/main/assets/karaoke.bin` (4-bit weights, about 0.06 MB). These scripts make it.

Version 2 (Grapefruit 3.2) replaced version 1 (3.0 to 3.1), which needed too much of a 2013 tablet's
time and could stutter: it does about a third of the work (half as many windows, a smaller network,
and no exp or log per frequency bin), and it learned from hours of music instead of 17 minutes.

## Data

- **MUSDB18 7-second excerpts** (SiSEC18-MUS 7s, Rafii, Liutkus, Stöter, Mimilakis and Bittner;
  https://zenodo.org/records/3270814, CC BY 4.0): 144 song clips with separate vocal, drums, bass and
  other tracks. Download `MUSDB18-7-STEMS.zip`, unzip it to `raw/`, then:

      python3 decode.py raw npz          # needs ffmpeg; writes vocals and accompaniment per clip

- **Free Music Archive songs, labelled by Demucs.** The MUSDB excerpts add up to 17 minutes. Demucs
  (htdemucs; Défossez et al., MIT License), a large separation model, splits any song into vocals and
  the rest about as well as the best published systems: far too big for a tablet, but it can label
  hours of music for Grapefruit's small model to learn from. `fma_fetch.py` takes the 30-second clips
  of `fma_small` (Defferrard et al., https://github.com/mdeff/fma) whose licence allows training a model
  that ships in an app (Creative Commons Attribution, CC0 and public domain: no NonCommercial,
  NoDerivatives or ShareAlike), reading each straight out of the 7 GB zip with HTTP range requests;
  `teacher.py` labels them; `credits.py` writes the credits the licences ask for
  (`fma_credits.csv`, and the app's list under Settings › About › Karaoke Mode):

      ./venv/bin/pip install demucs
      python3 fma_fetch.py fma_metadata/tracks.csv fma        # 1001 songs, about 8 hours
      THREADS=4 ./venv/bin/python teacher.py fma npz_fma        # about 2x real time on 4 CPU cores
      python3 credits.py fma npz_fma fma_credits.csv ../../app/src/main/assets/karaoke_credits.txt

## Train, check, export

    python3 -m venv venv && ./venv/bin/pip install torch numpy --index-url https://download.pytorch.org/whl/cpu
    VOCAL_WEIGHT=2 FMA=npz_fma ./venv/bin/python train.py npz runs/check 6000                  # reports below
    ./venv/bin/python split_eval.py runs/check "npz/test/*.npz"
    VOCAL_WEIGHT=2 FMA=npz_fma EXTRA=npz/test ./venv/bin/python train.py npz runs/model 8000   # shipped
    ./venv/bin/python export.py runs/model npz out

`train.py` holds 10 MUSDB training clips back for validation (real stems) and keeps the best
checkpoint by validation loss. A batch is a MUSDB clip 35% of the time and an FMA song otherwise
(the singer from a song Demucs found a vocal in, the backing from any), remixed across songs.
`VOCAL_WEIGHT=2` makes vocal left behind count twice as much as backing taken with it. `export.py`
quantizes the weights to 4 bits (one scale per output row, two weights a byte), writes
`out/karaoke.bin`, and writes `golden_in.bin` / `golden_out.bin`: a synthetic sung-like signal
(`voice_synth.py`) and what Python's reference of the app's processing makes of it. Copy `karaoke.bin`
to `app/src/main/assets/` and the golden files to `app/src/test/resources/karaoke/`;
`VocalRemoverTest` checks the app gives the same sound.

`split_eval.py run "files"` reports how much quieter the lead vocal gets and how much of the backing
goes with it (both as medians over the songs). On the 50 MUSDB test clips (real stems, not trained
on) and on 43 FMA songs with a vocal that no model trained on (Demucs' split as the reference):

| Model (trained without the clips it's tested on) | MUSDB: vocal quieter / backing removed, below the backing | FMA: the same |
|---|---|---|
| Version 1 (3.0 to 3.1) | 3.6 dB / 17.1 dB | 3.6 dB / 15.6 dB |
| Version 2 on MUSDB only | 3.6 dB / 15.9 dB | 3.7 dB / 14.6 dB |
| Version 2 on MUSDB and FMA | 3.0 dB / 17.7 dB | 2.9 dB / 18.8 dB |
| Version 2 on MUSDB, VOCAL_WEIGHT=2 | 5.1 dB / 13.2 dB | 4.6 dB / 13.5 dB (a quarter of songs under 1.2 dB) |
| **Version 2 on MUSDB and FMA, VOCAL_WEIGHT=2 (shipped recipe)** | **3.9 dB / 16.0 dB** | **4.6 dB / 16.7 dB** |

Learning from Demucs' labels alone made the model more cautious (less vocal, but also less backing,
taken away); weighting the vocal left behind gave both: more vocal removed than version 1 on both sets,
the backing about as untouched, and the steadiest results from song to song.

`kdsp.py` defines the signal processing (windows, bands, framing, the fast power) the app mirrors exactly.

## Model

Per window (2048 samples, hop 1024, sine window): log energies of 96 mel bands (60 Hz to 16 kHz) of mid
(L+R)/2 and side (L-R)/2, normalized -> 64 (ReLU) per window -> 128 (ReLU) from 5 windows (3 back, 1
ahead) and two moving averages of the 64 (decays 0.8 and 0.95 a window: a memory of about a tenth of a
second and half a second) -> 128 (ReLU) -> 96 band vocal masks (sigmoid) and 96 "centredness" exponents
(softplus). The band mask, spread back to frequency bins, times each bin's share of power in the middle
to that exponent (|M|^2 / (|M|^2 + |S|^2), computed as 2^(s log2 x) on float bits), times the mid
spectrum is the vocal estimate, subtracted from both channels (scaled by the Vocal Removal amount). About
110,000 weights; per window about 110,000 multiply-adds, one 2048-point complex FFT (mid and side
together) and one 1024-point inverse (the real output's even and odd samples together).
