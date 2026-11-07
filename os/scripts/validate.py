#!/usr/bin/env python3
"""Run static, behavioral and optional Android-17 source compilation checks. No device access."""
import argparse
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from stage_vendor import safe_destination

class SourceTests(unittest.TestCase):
    def test_proprietary_staging_rejects_repo(self):
        with self.assertRaises(ValueError):safe_destination(ROOT/'private')
    def test_binary_manifest_has_no_old_kernel_candidates(self):
        manifest=json.loads((ROOT/'reference/binary-manifest.json').read_text())
        self.assertEqual(len(manifest['archives']),6)
        for f in manifest['files']:
            if f['candidate_destination'] and '.ko' in f['member']:self.assertTrue(f['member'].endswith('.3.0.36+'))
            if f['candidate_destination']:self.assertNotIn(pathlib.Path(f['member']).name,['framework.jar','services.jar','framework-res.apk','SystemUI.apk','Settings.apk'])
    def test_native_xml(self):
        for f in (ROOT/'aosp').rglob('*.xml'):ET.parse(f)
    def test_package_ids(self):
        expected={'launcher':'io.github.hayliewordsman.grapefruitlauncher','keyboard':'io.github.hayliewordsman.grapefruitkeyboard.legacyholo','calculator':'io.github.hayliewordsman.grapefruitcalculator.legacyholo','maps':'io.github.hayliewordsman.grapefruitmaps','grapefruit-media':'io.github.hayliewordsman.bluegrapefruit.victory'}
        for module,package in expected.items():self.assertIn('applicationId "'+package+'"',(ROOT.parent/module/'build.gradle').read_text())
    def test_no_proprietary_payloads_tracked(self):
        files=subprocess.check_output(['git','ls-files','-z','os'],cwd=ROOT.parent).decode().split('\0')
        self.assertFalse([f for f in files if f.endswith(('.apk','.so','.jar','.zip','.img')) or '.ko' in pathlib.Path(f).name])

def compile_android17(android_jar,ecj,java):
    with tempfile.TemporaryDirectory(prefix='grapefruit-native-') as temp:
        files=list((ROOT/'aosp').rglob('*.java'))+list((ROOT/'tests').glob('*.java'))
        cmd=[java,'-jar',str(ecj),'-proc:none','-1.6','-bootclasspath',str(android_jar),'-d',temp]+list(map(str,files))
        subprocess.run(cmd,check=True)
        for main in ['org.grapefruit.platform.PlatformModelTest','com.android.settings.grapefruit.ThemeStateTest']:
            subprocess.run([java,'-cp',temp,main],check=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--android-jar',type=pathlib.Path);p.add_argument('--ecj',type=pathlib.Path);p.add_argument('--java',default='java');a=p.parse_args()
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SourceTests))
    if not result.wasSuccessful():sys.exit(1)
    if a.android_jar and a.ecj:compile_android17(a.android_jar,a.ecj,a.java)
    else:print('Android 17 compilation/model execution NOT RUN; supply --android-jar and --ecj')
