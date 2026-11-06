# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "os/tools" / (name + ".py"))
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


class PaletteTests(unittest.TestCase):
    def test_catalog_matches_existing_app_colors(self):
        themes = json.loads((ROOT / "os/themes/catalog.json").read_text())["themes"]
        sources = [("app", "grapefruit_dark_"), ("app", "grapefruit_light_"),
                   ("app", "dark_"), ("blue-victory", "grapefruit_dark_")]
        for theme, (app, prefix) in zip(themes, sources):
            resources = ET.parse(ROOT / app / "src/main/res/values/colors.xml").getroot()
            colors = {c.attrib["name"][len(prefix):]: c.text for c in resources
                      if c.tag == "color" and c.attrib["name"].startswith(prefix)}
            self.assertEqual(theme["colors"], colors)

    def test_generated_files_are_current(self):
        for filename, content in module("generate").outputs().items():
            self.assertEqual((ROOT / filename).read_text(), content, filename)

    def test_font_replacement_preserves_fallbacks_and_italics(self):
        original = '''<familyset><family name="sans-serif"><font weight="400" style="normal">Roboto.ttf</font>
            <font weight="400" style="italic">Roboto-Italic.ttf</font></family>
            <family lang="ja"><font>NotoSansCJK.ttc</font></family><alias name="sans-serif-medium" to="sans-serif" weight="500"/></familyset>'''
        output = ET.fromstring(module("font_config").transform(original))
        self.assertEqual([f.text for f in output.find("family") if f.get("style") == "normal"],
                         ["Geist-Regular.ttf", "Geist-Medium.ttf", "Geist-SemiBold.ttf", "Geist-Bold.ttf"])
        self.assertEqual(output.find("family/font[@style='italic']").text, "Roboto-Italic.ttf")
        self.assertEqual(output.find("family[@lang='ja']/font").text, "NotoSansCJK.ttc")
        self.assertEqual(output.find("alias").get("to"), "sans-serif")

    def test_missing_font_family_fails(self):
        with self.assertRaises(ValueError):
            module("font_config").transform("<familyset/>")

    def test_java_engine_contrast_mode_and_unknown_role(self):
        java_home = os.environ.get("JAVA_HOME")
        javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
        java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        if not javac or not java:
            self.skipTest("JDK required")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "Check.java").write_text('''import com.grapefruit.os.theme.GrapefruitPalette;
public class Check {
  public static void main(String[] a) {
    if (GrapefruitPalette.background(0) != 0xff1c0a0e) throw new AssertionError("Dark");
    if (GrapefruitPalette.background(3) != 0xff07162b) throw new AssertionError("Blue");
    if (GrapefruitPalette.role(3,"primary",true,0,42) != 0xff67c7ff) throw new AssertionError("accent");
    if (GrapefruitPalette.role(3,"primary",true,1,42) != 42) throw new AssertionError("high contrast");
    if (GrapefruitPalette.role(3,"primary",false,0,42) != 42) throw new AssertionError("opposite mode");
    if (GrapefruitPalette.role(0,"unknown",true,0,42) != 42) throw new AssertionError("extension");
    if (GrapefruitPalette.valid(90) != 0 || GrapefruitPalette.dark(1)) throw new AssertionError("fallback");
  }
}''')
            subprocess.run([javac, "-d", str(path), str(ROOT / "os/generated/GrapefruitPalette.java"),
                            str(path / "Check.java")], check=True)
            subprocess.run([java, "-cp", str(path), "Check"], check=True)


if __name__ == "__main__":
    unittest.main()
