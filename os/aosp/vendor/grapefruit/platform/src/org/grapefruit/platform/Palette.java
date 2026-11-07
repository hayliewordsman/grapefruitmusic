// SPDX-License-Identifier: Apache-2.0
package org.grapefruit.platform;

/** Palette values shared with the existing Grapefruit Launcher and Audio design. */
public final class Palette {
    public final int background, surface, control, text, secondary, accent, end, outline, onAccent;
    public Palette(int id) {
        int[][] colors = {
            {0xff1c0a0e,0xff2e1218,0xff421c24,0xfffff1e8,0xffb88a8f,0xffff7f6b,0xfff2546b,0xff46202a,0xff2a0a10},
            {0xfffce4d9,0xfffffaf6,0xfffadccf,0xff4a1622,0xff9a5c63,0xffe83850,0xffd92b4b,0xfff3cdbf,0xffffffff},
            {0xff000000,0xff180a0e,0xff261116,0xfffff3ee,0xffb8949a,0xffff7f6b,0xfff2546b,0xff33181f,0xff1a0a0d},
            {0xff07162b,0xff102541,0xff1c3b60,0xffeaf4ff,0xffa2bad8,0xff67c7ff,0xff398bff,0xff294665,0xff06172c}
        };
        int[] p = colors[id >= 0 && id < colors.length ? id : 0];
        background=p[0]; surface=p[1]; control=p[2]; text=p[3]; secondary=p[4];
        accent=p[5]; end=p[6]; outline=p[7]; onAccent=p[8];
    }
}
