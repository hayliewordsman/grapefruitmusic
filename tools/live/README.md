# Live Mode

Live Mode plays a song as if to a stadium crowd: original synthesized audience sound, mixed in real
time with the song, the crowd singing along, and the stadium's reverberation. Everything here is
GPL-3.0-or-later, made by this repository's own code. No recordings of people, sound libraries,
sampled audio or trained crowd models are used.

## What happens during a song

| When | What Live Mode does |
| --- | --- |
| Before the first note | The crowd cheers for two seconds before the song starts (only when a song starts from the beginning, not after a seek or when Live Mode is turned on part-way). The time shows 0:00 meanwhile. |
| The first seconds | The cheer carries on under the intro and dies down to the crowd under the song. |
| Through the song | A distant crowd, about 30dB under full scale at 100% strength (scaled with the song's own loudness). Its loop has no single voice or whistle that could be noticed coming round. |
| A big moment | When the song gets 3dB louder than over the last few seconds and stays there (a chorus coming in), part of the crowd cheers. At most once every 35 seconds, not in the first 15 or the last 20. |
| The lead vocal | The crowd sings it: six copies, each a little late (30 to 120ms) and a little out of tune (about 15 cents, drifting), dulled and mostly reverberant, louder in louder sections. |
| The ending | The ovation starts as the music ends: when the song has been falling steadily for two seconds in its last 15 (a fade-out), when it's 9dB down on its recent peak (for a moment in the last three seconds, or over a second before that, so a break before the last chorus doesn't count), or at the end of the file. Either way only once the singing has stopped (the lead vocal 10dB under its recent peak): when the band drops out under the last line ("Blank Space"), the crowd waits for the last word. In a long fade-out it starts late enough to last past the end; after the file ends, it plays out (up to nine seconds) before the next song. |

The song and the crowd's singing go through a simulated stadium: two allpass diffusers and an
eight-line feedback delay network (Hadamard mixing, 2.4-second decay, treble damped in the loop).

## The vocal the crowd sings

Live Mode borrows Karaoke Mode's separator (`VocalRemover`, credited under Karaoke Mode) for the lead
vocal: it runs whenever Live Mode is on, as for Karaoke Mode. The song itself is not changed (Live
Mode alone plays it untouched, 70ms later, the separator's delay). If Karaoke Mode is on too, it
removes the vocal as set and the crowd still sings it.

Until the separator's model is loaded (the first few seconds after Live Mode is first turned on), and
on a device where it's too slow, the crowd follows the song's centre instead: the 200Hz to 3kHz band
of the mid channel, where the side channel is quiet, and not on drum hits. "Too slow" is measured on
the device: if the effects keep the audio thread busy more than 65% of the time, two seconds at a
time, twice in a row, while the separator runs for Live Mode alone, Live Mode stops using it until
Grapefruit is next started. Karaoke Mode itself is never turned down.

## Cost

The song-dependent work (the crowd's singing and the stadium) runs at about 11kHz: a factor of
`round(rate / 11025)` below the sample rate, with a one-pole low-pass and averaging on the way down
and linear interpolation on the way up. The crowd is pre-rendered, mu-law coded: a frame is four
table look-ups and two interpolations per playing sound. No allocation once running.

Measured on a desktop JVM (single thread, the 50-second test song in `LiveStageTest`'s style):

| | ns per frame, JIT | ns per frame, interpreter only |
| --- | --- | --- |
| No effects (limiter only) | 72 | 724 |
| Live Mode, without the separator | 138 | 1962 |
| Karaoke Mode | 607 | 6196 |
| Live Mode with the separator | 639 | 7565 |

So Live Mode costs about 1.05x (JIT) to 1.2x (interpreter) what Karaoke Mode does. These are not
device measurements; the on-device check above is what keeps a slower tablet from stuttering.

## The crowd: `render_crowd.py`

`render_crowd.py` writes `app/src/main/assets/live_crowd.bin` and its manifest `live_crowd.json`
(sizes, levels, seeds and SHA-256 hashes). It needs Python 3 with NumPy 2.3.5 and SciPy 1.16.3 (both
BSD-3-Clause, used only here):

```sh
python3 tools/live/render_crowd.py
```

It synthesizes:

- **Applause.** A few thousand people, each clapping with their own hands: a noise burst (0.6 to
  2.4ms decay) through a resonator at 620 to 3300Hz, as Peltola et al. modelled hand claps
  ("Synthesis of hand clapping sounds", IEEE TASLP 2007), plus the clap's crack. Each has a rate
  (around 4.4 claps a second), timing jitter, a place in the stadium and a threshold: they join
  and stop as the crowd's mood rises and falls.
- **Cheering.** Hundreds of voices, men's and women's: a band-limited sawtooth glottal source with
  jitter, shimmer and breath noise, through four formant resonators moving between vowels ("woo-oh",
  "yeah", "hey", "oh", screams), with rising and falling pitch and vibrato.
- **Two-finger whistles** and the hum of a crowd too big to hear any one person.
- **The stadium.** Every sound is near, mid-field or far: distance turns it down and dulls it, and
  a synthesized stereo stadium response (octave-band decay from 3.3 seconds in the bass to 0.4 in
  the treble, early reflections from the pitch and the stands) gives the space around it.

Three sections: the crowd under songs (20 seconds, 16kHz, looping with a 2.5-second crossfade), the
opening cheer (7 seconds, 22.05kHz; the song comes in at 2 seconds) and the closing ovation
(9 seconds, 22.05kHz). 1.3MB in all.

The levels were checked against a public-domain field recording of applause (radio aporee, "7 Rue
Sleidan, Strasbourg", archive.org item `aporee_47869_54396`): the synthesized ovation's long-term
spectrum follows the recording's within a few dB from 300Hz to 10kHz. The recording was only
measured; nothing of it is in the app or this repository.

## Format of `live_crowd.bin`

Big-endian. `GFLC`, version 1, section count 3; then for each section: a four-letter tag (`BED `,
`OPEN`, `CLOS`), sample rate, channels (2), frames, marker (for `OPEN`, the frame the song comes in
at) and the section's RMS as a float. Then each section's audio: signed 8-bit mu-law (mu = 255),
interleaved stereo.

## Limits

This is a stylized, synthesized crowd. It hasn't been compared with real stadium recordings by
listeners, and it can't know a song's structure: the ending and the big moments are guessed from
loudness. The crowd sings whatever the separator (or the centre) hears as the vocal.
