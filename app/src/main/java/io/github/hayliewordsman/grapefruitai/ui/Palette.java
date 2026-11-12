package io.github.hayliewordsman.grapefruitai.ui;

/**
 * The four official Grapefruit themes, with the canonical IDs of the universal theme contract:
 * Grapefruit Dark = 0, Grapefruit Light = 1, Pitch Black = 2, Victory Blue = 3. Colours are
 * Grapefruit's own (app/res/values/colors.xml and victory_blue.xml on the Grapefruit branches).
 */
public final class Palette {
    public static final int GRAPEFRUIT_DARK = 0;
    public static final int GRAPEFRUIT_LIGHT = 1;
    public static final int PITCH_BLACK = 2;
    public static final int VICTORY_BLUE = 3;
    public static final String[] NAMES = {"Grapefruit Dark", "Grapefruit Light", "Pitch Black", "Victory Blue"};

    public final int id;
    public final boolean light;
    public final int background, grouped, surface, bar, text, secondary, separator, disclosure, accent,
            fill, scrim, control, controlPressed, toggleOff, knob, accentEnd, onAccent, outline, danger;

    private Palette(int id, boolean light, int... c) {
        this.id = id;
        this.light = light;
        background = c[0]; grouped = c[1]; surface = c[2]; bar = c[3]; text = c[4]; secondary = c[5];
        separator = c[6]; disclosure = c[7]; accent = c[8]; fill = c[9]; scrim = c[10]; control = c[11];
        controlPressed = c[12]; toggleOff = c[13]; knob = c[14]; accentEnd = c[15]; onAccent = c[16];
        outline = c[17];
        danger = light ? 0xffc8283c : 0xffff5a63;
    }

    public static Palette of(int id) {
        switch (id) {
            case GRAPEFRUIT_LIGHT:
                return new Palette(id, true, 0xFFFCE4D9, 0xFFFCE4D9, 0xFFFFFAF6, 0xFFFDEAE1, 0xFF4A1622,
                        0xFF9A5C63, 0xFFF0C6B8, 0xFF8D4B56, 0xFFE83850, 0xFFFADCCF, 0xE6FCE4D9, 0xFFFADCCF,
                        0xFFF3C5B3, 0xFFF7D6C9, 0xFFFFFFFF, 0xFFD92B4B, 0xFFFFFFFF, 0xFFF3CDBF);
            case PITCH_BLACK:
                return new Palette(id, false, 0xFF000000, 0xFF000000, 0xFF180A0E, 0xFF12070A, 0xFFFFF3EE,
                        0xFFB8949A, 0xFF2F161C, 0xFFCBA6AC, 0xFFFF7F6B, 0xFF1E0C11, 0xD9000000, 0xFF261116,
                        0xFF36181F, 0xFF3A1D23, 0xFFF2D0C6, 0xFFF2546B, 0xFF1A0A0D, 0xFF33181F);
            case VICTORY_BLUE:
                return new Palette(id, false, 0xFF07162B, 0xFF07162B, 0xFF102541, 0xFF0B1D35, 0xFFEAF4FF,
                        0xFFA2BAD8, 0xFF294665, 0xFFB2D5F5, 0xFF67C7FF, 0xFF102541, 0xD907162B, 0xFF1C3B60,
                        0xFF284F78, 0xFF294665, 0xFFBDDDF6, 0xFF398BFF, 0xFF06172C, 0xFF294665);
            default:
                return new Palette(GRAPEFRUIT_DARK, false, 0xFF1C0A0E, 0xFF1C0A0E, 0xFF2E1218, 0xFF250E13,
                        0xFFFFF1E8, 0xFFB88A8F, 0xFF4A2229, 0xFFD4A5AC, 0xFFFF7F6B, 0xFF2E1218, 0xD91C0A0E,
                        0xFF421C24, 0xFF522631, 0xFF4A2229, 0xFFF2D0C6, 0xFFF2546B, 0xFF2A0A10, 0xFF46202A);
        }
    }

    public int windowTheme() {
        switch (id) {
            case GRAPEFRUIT_LIGHT: return io.github.hayliewordsman.grapefruitai.R.style.Theme_GrapefruitAI_GrapefruitLight;
            case PITCH_BLACK: return io.github.hayliewordsman.grapefruitai.R.style.Theme_GrapefruitAI_PitchBlack;
            case VICTORY_BLUE: return io.github.hayliewordsman.grapefruitai.R.style.Theme_GrapefruitAI_VictoryBlue;
            default: return io.github.hayliewordsman.grapefruitai.R.style.Theme_GrapefruitAI_GrapefruitDark;
        }
    }
}
