#!/usr/bin/env python3
"""Apply source changes to an explicitly supplied, clean AOSP 4.2.2_r1 tree.

Also accepts the reference subset for offline patch validation; this does not make it buildable.
"""
import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]

def validate_navigation(destination):
    namespace='{http://schemas.android.com/apk/res/android}'
    expected=['grapefruit_screenshot','grapefruit_volume_down','back','home','recent_apps','grapefruit_volume_up','grapefruit_spacer']
    for variant in ['layout','layout-sw600dp']:
        tree=ET.parse(destination/'frameworks/base/packages/SystemUI/res'/variant/'navigation_bar.xml')
        rows=[v for v in tree.getroot().iter() if v.tag.endswith('GrapefruitNavigationLayout')]
        if len(rows)!=2:raise ValueError('Missing rotation layouts: '+variant)
        for row in rows:
            if [v.attrib[namespace+'id'].split('/')[-1] for v in row]!=expected:raise ValueError('Wrong navigation order: '+variant)
            spacer=list(row)[-1]
            if spacer.attrib.get(namespace+'clickable')!='false' or spacer.attrib.get(namespace+'focusable')!='false':raise ValueError('Spacer must be inert')

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
    # Reference subsets can be inside an ignored work/ directory of the app repository.
    # Do not let Git interpret their patch paths relative to that outer repository.
    git_env=dict(os.environ,GIT_CEILING_DIRECTORIES=str(destination))
    for project in ['frameworks/base','packages/apps/Settings']:
        patch=ROOT/'patches'/(project.replace('/','_')+'.patch')
        subprocess.run(['git','apply','--check',str(patch)],cwd=destination/project,env=git_env,check=True)
    for project in ['frameworks/base','packages/apps/Settings']:
        patch=ROOT/'patches'/(project.replace('/','_')+'.patch')
        subprocess.run(['git','apply',str(patch)],cwd=destination/project,env=git_env,check=True)
    for f in overlays:
        target=destination/f.relative_to(ROOT/'aosp');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
    for font in (ROOT.parent/'grapefruit-ui/src/main/assets/fonts').glob('*'):
        target=destination/'vendor/grapefruit/fonts'/font.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(font,target)
    validate_navigation(destination)
    print('Source patches applied; no boot images, installer or device actions were generated.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aosp-root',required=True,type=pathlib.Path);a=p.parse_args()
    try:apply(a.aosp_root)
    except (ValueError,OSError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
