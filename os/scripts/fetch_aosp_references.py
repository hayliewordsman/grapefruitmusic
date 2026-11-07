#!/usr/bin/env python3
"""Fetch the locked, original AOSP files needed to validate patches (not a complete ROM tree)."""
import argparse
import base64
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[1]

def fetch(destination):
    lock=json.loads((ROOT/'aosp-source-lock.json').read_text())
    def one(entry):
        target=destination/entry['project']/entry['path']
        if target.exists():
            data=target.read_bytes()
        else:
            url='https://android.googlesource.com/platform/'+entry['project']+'/+/'+lock['tag']+'/'+entry['path']+'?format=TEXT'
            data=base64.b64decode(urllib.request.urlopen(url,timeout=45).read())
        if hashlib.sha256(data).hexdigest()!=entry['sha256']:
            raise ValueError('Upstream/reference checksum mismatch: '+str(target))
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(one,lock['files']))
    return len(lock['files'])

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True,type=pathlib.Path);a=p.parse_args()
    print('Verified',fetch(a.output),'original Android 4.2.2_r1 files')
