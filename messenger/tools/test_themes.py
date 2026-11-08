#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Checks Grapefruitgram's generated themes against the Grapefruit palette.

Run from the repository root:  python3 -m unittest discover -s messenger/tools -p 'test_*.py'
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import themes  # noqa: E402

THEME_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "overlay", "app", "src", "main", "other", "themes")
UNIVERSAL_JAVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "overlay", "app", "src", "main", "java",
                              "org", "thunderdog", "challegram", "grapefruit", "UniversalTheme.java")


def load(name):
    settings, props, colors = {}, {}, {}
    section = None
    with open(os.path.join(THEME_DIR, name + ".tgx-theme"), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line in ("!", "@", "#"):
                section = line
                continue
            if ":" not in line:
                continue
            keys, value = line.split(":", 1)
            for key in (k.strip() for k in keys.split(",")):
                target = {"!": settings, "@": props, "#": colors}[section]
                if key in target:
                    raise AssertionError("%s defines %s twice" % (name, key))
                target[key] = value.strip()
    return settings, props, colors


class ThemesTest(unittest.TestCase):
    def test_ids_follow_upstream_and_are_contiguous(self):
        ids = sorted(int(load(t[1])[0]["id"]) for t in themes.THEMES)
        # Telegram X's own themes are 1..11; the generator requires contiguous ids.
        self.assertEqual(ids, [12, 13, 14, 15])

    def test_names_match_files(self):
        for tid, name, _, _, _ in themes.THEMES:
            settings = load(name)[0]
            self.assertEqual(settings["name"], '"%s"' % name)
            self.assertEqual(int(settings["id"]), tid)

    def test_pinned_roles_are_the_grapefruit_palette(self):
        for _, name, palette, _, _ in themes.THEMES:
            p = themes.PALETTES[palette]
            colors = load(name)[2]
            dark = palette != "light"
            expect = {
                "background": p["bg"], "filling": p["surface"], "text": p["text"], "textLight": p["secondary"],
                "textLink": p["accent"], "iconActive": p["accent"], "separator": p["outline"],
                "togglerActiveBackground": p["accent"], "togglerInactiveBackground": p["toggle_off"],
                "headerBackground": p["surface"] if dark else p["bg"], "badge": p["end"],
            }
            for key, value in expect.items():
                self.assertEqual(colors[key].upper(), value.upper(), "%s %s" % (name, key))

    def test_darkness_and_status_bar(self):
        for _, name, palette, _, _ in themes.THEMES:
            props = load(name)[1]
            light = palette == "light"
            self.assertEqual(props["dark"], "0" if light else "1", name)
            self.assertEqual(props["lightStatusBar"], "1" if light else "0", name)
            self.assertEqual(props["parentTheme"], "1", name)

    def test_people_keep_distinct_colours(self):
        # Avatar and name colours tell people apart: they must not collapse into the accent.
        for _, name, palette, _, _ in themes.THEMES:
            colors = load(name)[2]
            blue, red, green = colors["avatarBlue"].upper(), colors["avatarRed"].upper(), colors["avatarGreen"].upper()
            self.assertEqual(len({blue, red, green}), 3, name)

    def test_theme_picker_circles_everywhere(self):
        for _, name, _, _, _ in themes.THEMES:
            colors = load(name)[2]
            for circle, value in themes.CIRCLES.items():
                self.assertEqual(colors[circle].upper(), value.upper(), "%s %s" % (name, circle))

    def test_universal_ids_map_in_canonical_order(self):
        # The universal contract: Dark 0, Light 1, Black 2, Blue 3.
        with open(UNIVERSAL_JAVA, encoding="utf-8") as f:
            java = f.read()
        block = re.search(r"THEMES = \{(.*?)\};", java, re.S).group(1)
        order = re.findall(r"ThemeId\.(\w+)", block)
        self.assertEqual(order, ["GRAPEFRUIT_DARK", "GRAPEFRUIT_LIGHT", "PITCH_BLACK", "VICTORY_BLUE"])
        self.assertIn('"content://io.github.hayliewordsman.grapefruitsettings.theme/theme"', java)

    def test_hex_round_trip(self):
        self.assertEqual(themes.fmt(themes.parse_hex("#FFF")), "#FFFFFF")
        self.assertEqual(themes.fmt(themes.parse_hex("#12345678")), "#12345678")
        self.assertEqual(themes.parse_hex("#0008"), (0, 0, 0, 0x88))


if __name__ == "__main__":
    unittest.main()
