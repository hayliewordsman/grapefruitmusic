#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Replace normal sans-serif faces in a generated AOSP font configuration, in place."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def transform(data):
    root = ET.fromstring(data)
    families = [f for f in root.iter("family") if f.get("name") == "sans-serif"]
    if len(families) != 1:
        raise ValueError("Expected exactly one named sans-serif family; review the upstream schema")
    family = families[0]
    for font in list(family):
        if font.tag == "font" and font.get("style", "normal") == "normal":
            family.remove(font)
    for index, (weight, name) in enumerate([(400, "Regular"), (500, "Medium"), (600, "SemiBold"), (700, "Bold")]):
        font = ET.Element("font", weight=str(weight), style="normal")
        font.text = "Geist-" + name + ".ttf"
        family.insert(index, font)
    ET.indent(root)
    return ET.tostring(root, encoding="unicode", xml_declaration=True) + "\n"


if __name__ == "__main__":
    target = Path(sys.argv[1])
    target.write_text(transform(target.read_text()))
