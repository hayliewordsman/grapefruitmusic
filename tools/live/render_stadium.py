#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Original procedural stadium cheers: no recordings, samples or pretrained models."""
import hashlib, json, struct
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
RATE=12000
SECONDS=3

def render(seed):
    rng=np.random.default_rng(seed)
    t=np.arange(RATE*SECONDS)/RATE
    stereo=np.zeros((len(t),2))
    # Independent rising/falling open-vowel shouts, distributed across the stadium.
    for person in range(96):
        start=rng.uniform(0,0.65)
        local=np.maximum(0,t-start)
        length=rng.uniform(1.25,2.8)
        env=np.clip(local/0.10,0,1)*np.clip((length-local)/0.65,0,1)*(t>=start)
        pitch=rng.uniform(115,380)
        vibrato=0.025*np.sin(2*np.pi*rng.uniform(4,7)*local+rng.uniform(0,6.28))
        glide=1+0.25*np.exp(-local*3)-0.15*local/3+vibrato
        phase=2*np.pi*np.cumsum(pitch*glide)/RATE+rng.uniform(0,6.28)
        # A broad 'whoa/woo' vowel, with slight breath noise: cheering, not song echoes.
        formants=rng.uniform([400,900,2100],[750,1450,3000])
        shout=np.zeros(len(t))
        for h in range(1,22):
            f=pitch*h
            weight=(0.12+sum(np.exp(-0.5*((f-formants)/220)**2)))/h
            shout+=weight*np.sin(h*phase)
        shout=(shout+0.06*rng.normal(size=len(t)))*env
        pan=rng.uniform(0.12,0.88)
        stereo[:,0]+=shout*np.sqrt(1-pan)
        stereo[:,1]+=shout*np.sqrt(pan)
    # Dense, independent handclaps and scattered rising whistles.
    for _ in range(180):
        start=int(rng.uniform(0.08,2.7)*RATE)
        n=min(int(RATE*0.12),len(t)-start)
        clap=rng.normal(size=n)*np.exp(-np.arange(n)/(RATE*0.018))*rng.uniform(0.3,0.8)
        pan=rng.uniform(0,1)
        stereo[start:start+n,0]+=clap*(1-pan)
        stereo[start:start+n,1]+=clap*pan
    for _ in range(9):
        start=rng.uniform(0.15,1.7)
        local=t-start
        env=np.clip(local/.08,0,1)*np.clip((.7-local)/.2,0,1)*(local>=0)
        whistle=np.sin(2*np.pi*(rng.uniform(1700,2600)*local+220*local*local))*env*.25
        stereo+=whistle[:,None]*rng.uniform(.2,.8,size=2)
    # Small baked stadium reflections. None of this synthesis runs on the phone.
    dry=stereo.copy()
    for delay,gain in [(0.09,.19),(.17,.12),(.29,.08)]:
        n=int(delay*RATE)
        stereo[n:]+=dry[:-n,::-1]*gain
    fade=np.minimum(np.arange(len(t))/(RATE*.04),1)*np.minimum(np.arange(len(t))[::-1]/(RATE*.35),1)
    stereo*=fade[:,None]
    stereo=np.tanh(stereo/np.std(stereo)*.32)
    return np.round(np.clip(stereo,-1,1)*127).astype(np.int8)

seeds=[7101,7102]
clips=[render(seed) for seed in seeds]
raw=struct.pack('>4sII',b'GSC1',RATE,RATE*SECONDS)+b''.join(x.tobytes() for x in clips)
path=ROOT/'app/src/main/assets/stadium_cheers.pcm'
path.write_bytes(raw)
manifest=dict(name='Original procedural stadium cheers v1',license='GPL-3.0-or-later',recordings_used=False,pretrained_weights_used=False,sample_rate=RATE,channels=2,seconds_per_event=SECONDS,seeds=seeds,encoding='GSC1 header, signed 8-bit interleaved stereo; opening then closing',bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),numpy=np.__version__,rms=[float(np.sqrt(np.mean((x.astype(float)/128)**2))) for x in clips],limitations='Stylized synthetic whoops, applause and whistles; no human crowd recording or perceptual evaluation.')
(ROOT/'app/src/main/assets/stadium_cheers.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
