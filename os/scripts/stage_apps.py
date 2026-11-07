#!/usr/bin/env python3
"""Stage five signed Grapefruit APKs without changing their package IDs or certificates.

The caller supplies expected certificate fingerprints/version floors from the existing releases.
Debug and unsigned artifacts must never silently become ROM app update identities.
"""
import argparse
import hashlib
import json
import pathlib
import re
import shutil
import struct
import subprocess
import zipfile
from stage_vendor import safe_destination

APPS={
    'GrapefruitLauncher':('io.github.hayliewordsman.grapefruitlauncher','Launcher2'),
    'GrapefruitKeyboard':('io.github.hayliewordsman.grapefruitkeyboard.legacyholo','LatinIME'),
    'GrapefruitCalculator':('io.github.hayliewordsman.grapefruitcalculator.legacyholo','Calculator'),
    'GrapefruitMaps':('io.github.hayliewordsman.grapefruitmaps',''),
    'GrapefruitMedia':('io.github.hayliewordsman.bluegrapefruit.victory','')
}

def stage(spec,output,aapt,apksigner):
    output=safe_destination(output)
    if output.exists() and any(output.iterdir()):raise ValueError('Output must be empty')
    apps=json.loads(pathlib.Path(spec).read_text())
    if set(apps)!=set(APPS):raise ValueError('Exactly the five ROM app modules are required')
    libraries={};verified=[]
    for module,(package,override) in APPS.items():
        entry=apps[module];source=pathlib.Path(entry['apk']).resolve()
        fingerprint=entry.get('certificate_sha256')
        if not fingerprint or not re.fullmatch('[0-9a-fA-F]{64}',fingerprint):raise ValueError('Supply the original release certificate SHA-256 for '+module)
        badging=subprocess.check_output([aapt,'dump','badging',str(source)],text=True)
        match=re.search(r"package: name='([^']+)' versionCode='(\d+)'",badging)
        if not match or match[1]!=package:raise ValueError('Wrong package identity: '+module)
        floor=entry.get('minimum_version_code')
        if not isinstance(floor,int) or floor<1 or int(match[2])<floor:raise ValueError('Missing version floor or package downgrade: '+module)
        minimum=re.search(r"sdkVersion:'(\d+)'",badging)
        if not minimum or int(minimum[1])>17:raise ValueError('Requires Android newer than 4.2.2: '+module)
        result=subprocess.run([apksigner,'verify','--min-sdk-version','17','--print-certs',str(source)],text=True,capture_output=True,check=True)
        certs=re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]+)',result.stdout)
        if certs!=[fingerprint.lower()]:raise ValueError('Certificate does not match the existing release: '+module)
        with zipfile.ZipFile(source) as z:
            if z.testzip():raise ValueError('Corrupt APK: '+module)
            all_native=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
            arm=[n for n in all_native if n.startswith('lib/armeabi-v7a/')]
            if all_native and not arm:raise ValueError('Missing ARMv7 JNI implementation: '+module)
            for name in arm:
                data=z.read(name);target=pathlib.Path(name).name
                if data[:6]!=b'\x7fELF\x01\x01' or struct.unpack_from('<H',data,18)[0]!=40:raise ValueError('Invalid ARM ELF: '+name)
                if target in libraries and libraries[target]!=data:raise ValueError('Conflicting JNI binaries in Android 4.2 system/lib: '+target)
                libraries[target]=data
        verified.append((module,source,package,int(match[2]),fingerprint,override))
    output.mkdir(parents=True,exist_ok=True)
    make=['LOCAL_PATH := $(call my-dir)']
    for module,source,package,version,cert,override in verified:
        shutil.copy2(source,output/(module+'.apk'))
        make+=['include $(CLEAR_VARS)','LOCAL_MODULE := '+module,'LOCAL_SRC_FILES := '+module+'.apk','LOCAL_MODULE_CLASS := APPS','LOCAL_MODULE_TAGS := optional','LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)','LOCAL_CERTIFICATE := PRESIGNED']
        if override:make+=['LOCAL_OVERRIDES_PACKAGES := '+override]
        make+=['include $(BUILD_PREBUILT)']
    # JB system apps resolve JNI through /system/lib; do not repackage or resign their APKs.
    copies=[]
    for name,data in libraries.items():
        target=output/'native'/name;target.parent.mkdir(exist_ok=True);target.write_bytes(data)
        copies.append('    vendor/grapefruit/apps/native/'+name+':system/lib/'+name)
    (output/'Android.mk').write_text('\n'.join(make)+'\n')
    (output/'apps.mk').write_text('PRODUCT_PACKAGES += '+' '.join(APPS)+'\n'+('PRODUCT_COPY_FILES += \\\n'+' \\\n'.join(copies)+'\n' if copies else ''))
    report=[dict(module=m,package=p,version_code=v,certificate_sha256=c,apk_sha256=hashlib.sha256(s.read_bytes()).hexdigest()) for m,s,p,v,c,o in verified]
    (output/'identities.json').write_text(json.dumps(report,indent=2)+'\n')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--spec',required=True,type=pathlib.Path);p.add_argument('--output',required=True,type=pathlib.Path);p.add_argument('--aapt',default='aapt');p.add_argument('--apksigner',default='apksigner');a=p.parse_args()
    try:print(json.dumps(stage(a.spec,a.output,a.aapt,a.apksigner),indent=2))
    except (ValueError,OSError,subprocess.CalledProcessError,zipfile.BadZipFile) as e:p.exit(1,str(e)+'\n')
