#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Makes Grapefruitgram's four built-in Telegram X themes from the Grapefruit palette.

Telegram X's themes name about 600 colours. Rather than choosing each by hand, every theme starts
from the closest upstream theme (its "template"), and each template colour is moved into the
Grapefruit palette:

* neutral colours (backgrounds, surfaces, grey text, separators) and colours in the template's own
  accent hue are mapped by lightness onto the Grapefruit ramp of the theme (page, surface, control,
  secondary text, text), so that what was lighter stays lighter;
* saturated colours in the template's accent hue take the Grapefruit accent's hue and saturation;
* every other colour (green "online", red "delete", file-type and avatar colours) is left alone,
  because it means something;
* then the roles that make a theme look like Grapefruit (page, header, text, accent, bubbles,
  switches) are pinned to the exact palette of the Grapefruit apps (maps/.../Brand.java).

Run from the repository root:  python3 messenger/tools/themes.py <upstream>
It reads the templates from <upstream>/app/src/main/other/themes and writes the four themes to
messenger/overlay/app/src/main/other/themes.
"""
import colorsys
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "overlay", "app", "src", "main", "other", "themes")

# The Grapefruit apps' palette (maps/src/main/java/com/grapefruitmaps/Brand.java), per universal id.
PALETTES = {
    "dark": dict(bg="#1C0A0E", surface="#2E1218", control="#421C24", pressed="#55262F",
                 text="#FFF1E8", secondary="#B88A8F", accent="#FF7F6B", end="#F2546B",
                 outline="#46202A", on_accent="#2A0A10", toggle_off="#4A2229"),
    "light": dict(bg="#FCE4D9", surface="#FFFAF6", control="#FADCCF", pressed="#F3CDBF",
                  text="#4A1622", secondary="#9A5C63", accent="#E83850", end="#D92B4B",
                  outline="#F3CDBF", on_accent="#FFFFFF", toggle_off="#F7D6C9"),
    "black": dict(bg="#000000", surface="#180A0E", control="#261116", pressed="#3A1820",
                  text="#FFF1E8", secondary="#B88A8F", accent="#FF7F6B", end="#F2546B",
                  outline="#46202A", on_accent="#2A0A10", toggle_off="#3A1D23"),
    "blue": dict(bg="#07162B", surface="#102541", control="#1C3B60", pressed="#284F78",
                 text="#EAF4FF", secondary="#A2BAD8", accent="#67C7FF", end="#398BFF",
                 outline="#294665", on_accent="#06172C", toggle_off="#294665"),
}

# id, name, palette, template file, template colours that anchor the lightness ramp.
THEMES = [
    (12, "Grapefruit Dark", "dark", "Night Blue",
     dict(background="bg", filling="surface", headerBackground="control", icon="secondary", text="text")),
    (13, "Grapefruit Light", "light", "Blue",
     dict(text="text", textLight="secondary", background="bg", filling="surface")),
    (14, "Pitch Black", "black", "Night Black",
     dict(background="bg", filling="surface", fillingPressed="pressed", icon="secondary", text="text")),
    (15, "Victory Blue", "blue", "Night Blue",
     dict(background="bg", filling="surface", headerBackground="control", icon="secondary", text="text")),
]

# Upstream's accent hue band (Telegram blue), in degrees.
ACCENT_BAND = (185, 235)

# Circle colours shown in the theme picker; every theme must define them (they live in the source theme).
CIRCLES = dict(themeGrapefruitDark="#FF7F6B", themeGrapefruitLight="#E83850",
               themePitchBlack="#F2546B", themeVictoryBlue="#398BFF")


def parse_hex(h):
    h = h.strip().lstrip("#")
    if len(h) in (3, 4):
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        h += "FF"
    if len(h) != 8:
        raise ValueError(h)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4, 6))


def fmt(rgba):
    r, g, b, a = (max(0, min(255, int(round(v)))) for v in rgba)
    return "#%02X%02X%02X" % (r, g, b) + ("" if a == 255 else "%02X" % a)


def read_theme(path):
    """Returns (settings lines, {property: value}, {color name: rgba})."""
    props, colors, section = {}, {}, None
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if line in ("!", "@", "#"):
                section = line
                continue
            line = line.split("//")[0].strip()
            if ":" not in line or section is None:
                continue
            names, value = line.split(":", 1)
            for name in (n.strip() for n in names.split(",")):
                if section == "@":
                    props[name] = value.strip()
                elif section == "#":
                    colors[name] = parse_hex(value)
    return props, colors


def lum(rgba):
    r, g, b = (v / 255 for v in rgba[:3])
    return colorsys.rgb_to_hls(r, g, b)[1]


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(4))


def ramp(anchors):
    """anchors: [(template lightness, target rgba)] -> f(lightness) -> rgb, piecewise linear."""
    pts = sorted(anchors)
    def f(l):
        if l <= pts[0][0]:
            return pts[0][1]
        for (l0, c0), (l1, c1) in zip(pts, pts[1:]):
            if l <= l1:
                return mix(c0, c1, 0 if l1 == l0 else (l - l0) / (l1 - l0))
        return pts[-1][1]
    return f


def hue_of(rgba):
    r, g, b = (v / 255 for v in rgba[:3])
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    return h * 360, l, s


def transform(rgba, f, accent, accent_l_shift):
    h, l, s = hue_of(rgba)
    in_band = ACCENT_BAND[0] <= h <= ACCENT_BAND[1]
    alpha = rgba[3]
    if s < 0.22 or (in_band and s < 0.45):
        out = f(l)
        return (out[0], out[1], out[2], alpha)
    if in_band:
        ah, al, as_ = hue_of(accent)
        nl = max(0.0, min(1.0, l + accent_l_shift))
        r, g, b = colorsys.hls_to_rgb(ah / 360, nl, min(1.0, as_ * max(0.6, s)))
        return (r * 255, g * 255, b * 255, alpha)
    return rgba


def pins(p, dark):
    """Exact Grapefruit colours for the roles that make the look."""
    c = {k: parse_hex(v) for k, v in p.items()}
    accent, end, text, sec = c["accent"], c["end"], c["text"], c["secondary"]
    bg, surface, control, pressed, outline = c["bg"], c["surface"], c["control"], c["pressed"], c["outline"]
    out_bubble = mix(surface, accent, 0.30 if dark else 0.16)
    a = lambda col, alpha: (col[0], col[1], col[2], alpha)
    pin = {
        # Pages and lists
        "background": bg, "filling": surface, "fillingPressed": pressed, "separator": outline,
        "chatBackground": bg, "chatKeyboard": bg, "overlayFilling": surface, "placeholder": a(text, 0x0C),
        # Floating header: the page colour, as in Grapefruit's iOS-style large titles
        "headerBackground": surface if dark else bg, "headerLightBackground": surface if dark else bg,
        "headerText": text, "headerIcon": text, "headerButtonIcon": on_accent(c),
        "headerLightText": text, "headerLightIcon": text, "headerPlaceholder": a(text, 0x10),
        "headerTabActive": accent, "headerTabActiveText": accent, "headerTabInactiveText": sec,
        "headerButton": accent, "headerBadge": accent, "headerBadgeMuted": sec,
        "drawer": surface, "drawerText": text,
        "passcode": bg, "passcodeIcon": text, "passcodeText": text,
        "statusBar": a((0, 0, 0, 0), 0x40 if dark else 0x00),
        "statusBarContent": text,
        # Text
        "text": text, "textLight": sec, "textPlaceholder": sec, "icon": sec, "iconLight": a(sec, 0xB0),
        "background_text": sec, "background_textLight": sec, "background_icon": sec,
        "textLink": accent, "textNeutral": accent, "textSearchQueryHighlight": accent,
        "textLinkPressHighlight": a(accent, 0x40), "textSelectionHighlight": a(accent, 0x55),
        "messageAuthor": accent, "inlineText": accent, "inlineIcon": accent, "inlineOutline": accent,
        # Accent controls: the grapefruit gradient's two ends
        "iconActive": accent, "controlActive": accent, "checkActive": accent, "inputActive": accent,
        "progress": accent, "sliderActive": accent, "seekDone": accent, "chatSendButton": accent,
        "chatListAction": accent, "badge": end, "unreadText": accent, "promo": accent,
        "circleButtonRegular": end, "circleButtonTheme": end, "circleButtonActive": accent,
        "circleButtonRegularIcon": on_accent(c), "circleButtonThemeIcon": on_accent(c),
        "fillingPositive": accent, "fillingPositiveContent": on_accent(c),
        "ticks": accent, "ticksRead": accent, "online": accent, "notification": end,
        "playerButtonActive": accent, "profileSectionActive": accent, "profileSectionActiveContent": accent,
        # Grapefruit-slice switches
        "togglerActive": parse_hex("#FFFFFF"), "togglerActiveBackground": accent,
        "togglerInactive": parse_hex("#FFFFFF"), "togglerInactiveBackground": c["toggle_off"],
        "togglerPositive": parse_hex("#FFFFFF"), "togglerPositiveBackground": accent,
        "controlInactive": sec, "lineInactive": outline, "sliderInactive": outline,
        # Bubbles
        "bubbleIn_background": surface, "bubbleIn_text": text, "bubbleIn_time": sec,
        "bubbleIn_textLink": accent, "bubbleIn_progress": accent,
        "bubbleOut_background": out_bubble, "bubbleOut_text": text, "bubbleOut_time": mix(sec, accent, 0.25),
        "bubbleOut_textLink": accent if not dark else mix(accent, text, 0.35),
        "bubbleOut_ticks": accent, "bubbleOut_ticksRead": accent, "bubbleOut_messageAuthor": accent,
        "bubbleOut_inlineText": accent, "bubbleOut_inlineIcon": accent, "bubbleOut_inlineOutline": accent,
        "messageSwipeBackground": end, "messageSelection": a(accent, 0x18),
        "bubble_messageSelection": a(accent, 0x22), "bubble_messageSelectionNoWallpaper": a(accent, 0x22),
        "unread": mix(surface, accent, 0.12), "chatListMute": sec, "chatListIcon": sec,
        "file": accent, "bubbleOut_file": accent, "fileAttach": control, "playerCoverPlaceholder": control,
        "snackbarUpdate": accent, "snackbarUpdateText": on_accent(c), "snackbarUpdateAction": on_accent(c),
    }
    return pin


def on_accent(c):
    return c["on_accent"]


def build(template_dir, theme):
    tid, name, palette, template, anchors = theme
    p = {k: parse_hex(v) for k, v in PALETTES[palette].items()}
    dark = palette != "light"
    source_props, source_colors = read_theme(os.path.join(template_dir, "Blue.tgx-theme"))
    props, colors = read_theme(os.path.join(template_dir, template + ".tgx-theme"))
    effective = dict(source_colors)
    effective.update(colors)
    pts = [(lum(effective[k]), p[v]) for k, v in anchors.items()]
    pts.append((0.0, (0, 0, 0, 255) if dark else p["text"]))
    pts.append((1.0, p["text"] if dark else (255, 255, 255, 255)))
    f = ramp(pts)
    accent_template = effective["textLink"]
    shift = lum(p["accent"]) - lum(accent_template)
    # People's colours (avatars, names, reply lines) and attachment/file kinds tell things apart:
    # they keep upstream's hues.
    semantic = ("avatar", "name", "line", "file", "attach", "bubbleIn_line", "bubbleOut_line")
    result = {k: (v if k.startswith(semantic) else transform(v, f, p["accent"], shift))
              for k, v in effective.items()}
    for k in ("avatarSavedMessages", "avatarSavedMessages_big", "avatarReplies", "avatarReplies_big",
              "avatarArchive"):
        if k in effective:
            result[k] = p["end"]
    result.update(pins(PALETTES[palette], dark))
    result.update({k: parse_hex(v) for k, v in CIRCLES.items()})
    # Keep a few colours that must stay as upstream meant them.
    for keep in ("white", "black", "notificationSecure", "textSecure", "textNegative", "fillingNegative",
                 "iconNegative", "circleButtonNegative", "headerRemoveBackground", "previewBackground",
                 "tooltip"):
        if keep in effective:
            result[keep] = effective[keep]
    missing = [k for k in pins(PALETTES[palette], dark) if k not in source_colors]
    if missing:
        raise SystemExit("%s: unknown colour ids %s" % (name, missing))

    out_props = dict(source_props)
    out_props.update(props)
    out_props["parentTheme"] = "1"
    out_props["dark"] = "1" if dark else "0"
    out_props["lightStatusBar"] = "0" if dark else "1"
    out_props["wallpaperUsageId"] = "1" if dark else "0"
    out_props.pop("bubbleOutTheme", None)
    lines = ["!", "id: %d" % tid, 'name: "%s"' % name, "time: 1794268800", "@"]
    by_value = {}
    for k, v in out_props.items():
        by_value.setdefault(v, []).append(k)
    for v, ks in sorted(by_value.items(), key=lambda kv: kv[1][0]):
        lines.append("%s: %s" % (", ".join(sorted(ks)), v))
    lines.append("#")
    grouped = {}
    for k, v in result.items():
        grouped.setdefault(fmt(v), []).append(k)
    for v, ks in sorted(grouped.items(), key=lambda kv: sorted(kv[1])[0].lower()):
        lines.append("%s: %s" % (", ".join(sorted(ks)), v))
    return name, "\n".join(lines) + "\n"


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    template_dir = os.path.join(sys.argv[1], "app", "src", "main", "other", "themes")
    os.makedirs(OUT, exist_ok=True)
    for theme in THEMES:
        name, text = build(template_dir, theme)
        with open(os.path.join(OUT, name + ".tgx-theme"), "w", encoding="utf-8") as f:
            f.write(text)
        print("wrote", name)


if __name__ == "__main__":
    main()
