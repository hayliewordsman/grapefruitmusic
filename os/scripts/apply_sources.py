#!/usr/bin/env python3
"""Apply source changes to an explicitly supplied, clean AOSP 4.2.2_r1 tree.

Also accepts the reference subset for offline patch validation; this does not make it buildable.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]

def apply(destination):
    destination=destination.resolve()
    if destination==ROOT or ROOT.is_relative_to(destination):
        raise ValueError('Use a separate AOSP workspace, not the application repository')
    lock=json.loads((ROOT/'aosp-source-lock.json').read_text())
    for f in lock['files']:
        target=destination/f['project']/f['path']
        if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest()!=f['sha256']:
            raise ValueError('AOSP source is missing, modified or not 4.2.2_r1: '+str(target))
    overlays=list(f for f in (ROOT/'aosp').rglob('*') if f.is_file())
    for f in overlays:
        if (destination/f.relative_to(ROOT/'aosp')).exists():
            raise ValueError('Refusing to overwrite existing source addition: '+str(f))
    for project in ['frameworks/base','packages/apps/Settings']:
        patch=ROOT/'patches'/(project.replace('/','_')+'.patch')
        subprocess.run(['git','apply','--check',str(patch)],cwd=destination/project,check=True)
    for project in ['frameworks/base','packages/apps/Settings']:
        patch=ROOT/'patches'/(project.replace('/','_')+'.patch')
        subprocess.run(['git','apply',str(patch)],cwd=destination/project,check=True)
    for f in overlays:
        target=destination/f.relative_to(ROOT/'aosp');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
    for font in (ROOT.parent/'grapefruit-ui/src/main/assets/fonts').glob('*'):
        target=destination/'vendor/grapefruit/fonts'/font.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(font,target)
    print('Source patches applied; no boot images, installer or device actions were generated.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aosp-root',required=True,type=pathlib.Path);a=p.parse_args()
    try:apply(a.aosp_root)
    except (ValueError,OSError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
