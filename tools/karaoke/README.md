# Karaoke Mode model

Grapefruit's Karaoke Mode (`app/src/main/java/com/grapefruitmusic/playback/VocalRemover.java`) runs a
small neural network that estimates, for each 46 ms window of sound, how much of each of 96 frequency
bands of the middle of the mix is lead vocal. The model ships as `app/src/main/assets/karaoke.bin`
(4-bit weights, about 0.11 MB). These scripts make it.

## Data

The MUSDB18 7-second excerpts (SiSEC18-MUS 7s, Rafii, Liutkus, Stöter, Mimilakis and Bittner;
https://zenodo.org/records/3270814, CC BY 4.0): 144 song clips with separate vocal, drums, bass and
other tracks. Download `MUSDB18-7-STEMS.zip`, unzip it to `raw/`, then:

    python3 decode.py raw npz          # needs ffmpeg; writes vocals and accompaniment per clip

## Train, check, export

    python3 -m venv venv && ./venv/bin/pip install torch numpy --index-url https://download.pytorch.org/whl/cpu
    ./venv/bin/python train.py npz runs/check 6000                 # 84 clips; reports below use this
    ./venv/bin/python export.py runs/check npz check
    EXTRA=npz/test ./venv/bin/python train.py npz runs/model 6000  # the shipped model: 134 clips
    ./venv/bin/python export.py runs/model npz out

`train.py` holds 10 training clips back for validation and keeps the best checkpoint by validation
loss. `export.py` reports how much the model reduces the vocal on the 50 test clips (only meaningful
for a model that didn't train on them), quantizes the
weights to 4 bits (one scale per output row, two weights a byte; `bits_eval.py` shows 4 bits score
the same as 8 here), writes `out/karaoke.bin`, and writes `golden_in.bin` /
`golden_out.bin`: a synthetic sung-like signal (`voice_synth.py`) and what Python's reference of the
app's processing makes of it. Copy `karaoke.bin` to `app/src/main/assets/` and the golden files to
`app/src/test/resources/karaoke/`; `VocalRemoverTest` checks the app gives the same sound.

`split_eval.py runs/check "npz/test/*.npz"` reports separately how much quieter the lead vocal gets
and how much of the backing goes with it. For the shipped recipe, on the 50 test clips (not trained
on): the lead vocal 3.6 dB quieter (median; middle half 2.8 to 5.7 dB), with what's taken from the
backing 17 dB below the backing.

`kdsp.py` defines the signal processing (windows, bands, framing) the app mirrors exactly.

## Model

Per window (2048 samples, hop 512): log energies of 96 mel bands (60 Hz to 16 kHz) of mid (L+R)/2 and
side (L-R)/2, normalized -> 96 (ReLU) per window -> 192 (ReLU) over 7 windows (4 back, 2 ahead) ->
192 (ReLU) -> 96 band vocal masks (sigmoid) and 96 "centredness" exponents (softplus; zero when trained
with `CENTRED=0`). The band mask, spread back to frequency bins, times the mid spectrum is the vocal
estimate, subtracted from both channels (scaled by the Vocal Removal amount).
