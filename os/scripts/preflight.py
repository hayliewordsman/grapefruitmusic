#!/usr/bin/env python3
"""Report development readiness. Missing prerequisites produce a nonzero exit, never a boot claim."""
import argparse,json,pathlib,shutil,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]

def report(aosp_root=None):
    requirements=json.loads((ROOT/'requirements.json').read_text())
    source=bool(aosp_root and (aosp_root/'.repo').is_dir() and (aosp_root/'build/envsetup.sh').is_file())
    java=subprocess.run(['java','-version'],text=True,capture_output=True).stderr
    result={'branch':'grapefruit-os-jp5s-4.2.2','native_source_additions':len(list((ROOT/'aosp').rglob('*.java'))),'full_aosp_checkout':source,'jdk6_available_as_default':'version "1.6.' in java,'flashable_image':False,'physical_boot_tested':False,'remaining_requirements':requirements['blocking_inputs']}
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aosp-root',type=pathlib.Path);a=p.parse_args();r=report(a.aosp_root);print(json.dumps(r,indent=2));raise SystemExit(0 if r['full_aosp_checkout'] and r['jdk6_available_as_default'] else 2)
