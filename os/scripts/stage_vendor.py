#!/usr/bin/env python3
"""Verify uploaded archives and stage selected vendor candidates outside every Git checkout.

No code from an archive is loaded or executed. No device access, mounts or image creation.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import struct
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]

def sha(data):
    return hashlib.sha256(data).hexdigest()

def safe_destination(path):
    path = pathlib.Path(path).resolve()
    for parent in (path, *path.parents):
        marker=parent / '.git'
        if marker.is_file() or (marker / 'HEAD').is_file():
            raise ValueError('Proprietary staging must be outside every Git checkout')
    return path

def stage(inputs, output):
    output = safe_destination(output)
    lock = json.loads((ROOT/'reference/binary-manifest.json').read_text())
    data = {}
    for archive in lock['archives']:
        matches = list(pathlib.Path(inputs).rglob(archive['name']))
        if len(matches) != 1:
            raise ValueError('Need exactly one original archive: '+archive['name'])
        source = matches[0]
        if sha(source.read_bytes()) != archive['sha256']:
            raise ValueError('Archive checksum mismatch: '+source.name)
        with zipfile.ZipFile(source) as z:
            if z.testzip():
                raise ValueError('Archive CRC mismatch: '+source.name)
            for f in [f for f in lock['files'] if f['archive'] == source.name]:
                content = z.read(f['member'])
                if sha(content) != f['sha256']:
                    raise ValueError('Member checksum mismatch: '+f['member'])
                if content.startswith(b'\x7fELF') and (content[4:6] != b'\x01\x01' or struct.unpack_from('<H',content,18)[0] != 40):
                    raise ValueError('Expected ELF32 little-endian ARM: '+f['member'])
                if f['candidate_destination'] and '.ko' in f['member']:
                    if b'vermagic=3.0.36+ SMP preempt mod_unload ARMv7' not in content:
                        raise ValueError('Wrong kernel vermagic: '+f['member'])
                data[(source.name,f['member'])] = content
    # Verify everything before writing anything. Never replace staged originals.
    if output.exists() and any(output.iterdir()):
        raise ValueError('Output must be empty; existing staged files will not be overwritten')
    output.mkdir(parents=True,exist_ok=True)
    archives = output/'originals';archives.mkdir()
    for archive in lock['archives']:
        source = next(pathlib.Path(inputs).rglob(archive['name']))
        shutil.copy2(source,archives/source.name)
    records=[];copies=[]
    for f in lock['files']:
        destination=f['candidate_destination']
        if not destination:continue
        # The source bytes and filenames are preserved, including .ko.3.0.36+ suffixes.
        target=output/'proprietary'/destination;target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data[(f['archive'],f['member'])])
        needed=[]
        if target.read_bytes().startswith(b'\x7fELF'):
            result=subprocess.run(['readelf','-d',str(target)],check=True,capture_output=True,text=True)
            import re
            needed=re.findall(r'Shared library: \[(.*?)\]',result.stdout)
        records.append(dict(f,needed=needed,status='candidate; kernel/BSP and runtime linkage unverified'))
        copies.append('    vendor/jpay/jp5s/proprietary/'+destination+':'+destination)
    (output/'jp5s-vendor.mk').write_text('# Generated from verified original binaries. No init/module-loading commands.\nPRODUCT_COPY_FILES += \\\n'+' \\\n'.join(copies)+'\n')
    (output/'dependency-report.json').write_text(json.dumps(records,indent=2)+'\n')
    (output/'NOTICE').write_text('Original JP5S/Rockchip binaries supplied for limited noncommercial device development.\nOriginal archives, embedded copyright strings and binary bytes are preserved.\nNo additional redistribution license is granted by this staging tool.\n')
    return {'archives_verified':len(lock['archives']),'files_verified':len(lock['files']),'staged_candidates':len(records),'output':str(output)}

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs',required=True,type=pathlib.Path)
    parser.add_argument('--vendor-out',required=True,type=pathlib.Path)
    args=parser.parse_args()
    try:print(json.dumps(stage(args.inputs,args.vendor_out),indent=2))
    except (ValueError,OSError,zipfile.BadZipFile) as error:parser.exit(1,str(error)+'\n')
