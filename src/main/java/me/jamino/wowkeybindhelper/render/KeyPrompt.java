package me.jamino.wowkeybindhelper.render;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.Map;

/**
 * What to draw for a physical key: either a keycap carrying {@code text}, or one of the mouse
 * icons ({@code mouseIcon} 1-3 = left, right, middle). Text only uses characters that exist in
 * glyphs.png; the lower-case letters u/d/l/r are that sheet's arrow glyphs.
 */
public record KeyPrompt(String text, int mouseIcon) {
    /** Every character glyphs.png can draw. */
    private static final String GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`-=+[]\\/;',.*?!:<>_#udlr";
    private static final int MAX_CHARS = 6;

    private static final String KEYBOARD = "key.keyboard.";

    /** Keys whose prompt is not simply the character printed on them. */
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("left.shift", "SHIFT"), Map.entry("right.shift", "RSHIFT"),
            Map.entry("left.control", "CTRL"), Map.entry("right.control", "RCTRL"),
            Map.entry("left.alt", "ALT"), Map.entry("right.alt", "RALT"),
            Map.entry("left.win", "WIN"), Map.entry("right.win", "RWIN"),
            Map.entry("space", "SPACE"), Map.entry("tab", "TAB"), Map.entry("enter", "ENTER"),
            Map.entry("escape", "ESC"), Map.entry("backspace", "BKSP"), Map.entry("delete", "DEL"),
            Map.entry("insert", "INS"), Map.entry("home", "HOME"), Map.entry("end", "END"),
            Map.entry("page.up", "PGUP"), Map.entry("page.down", "PGDN"),
            Map.entry("caps.lock", "CAPS"), Map.entry("num.lock", "NUM"), Map.entry("scroll.lock", "SCRL"),
            Map.entry("print.screen", "PRT"), Map.entry("pause", "PAUSE"), Map.entry("menu", "MENU"),
            Map.entry("up", "u"), Map.entry("down", "d"), Map.entry("left", "l"), Map.entry("right", "r"),
            Map.entry("keypad.add", "N+"), Map.entry("keypad.subtract", "N-"),
            Map.entry("keypad.multiply", "N*"), Map.entry("keypad.divide", "N/"),
            Map.entry("keypad.decimal", "N."), Map.entry("keypad.enter", "NENT"),
            Map.entry("keypad.equal", "N="));

    /** US-layout fallback for punctuation when the OS cannot name the key. */
    private static final Map<String, String> PUNCTUATION = Map.ofEntries(
            Map.entry("grave.accent", "`"), Map.entry("minus", "-"), Map.entry("equal", "="),
            Map.entry("left.bracket", "["), Map.entry("right.bracket", "]"), Map.entry("backslash", "\\"),
            Map.entry("semicolon", ";"), Map.entry("apostrophe", "'"), Map.entry("comma", ","),
            Map.entry("period", "."), Map.entry("slash", "/"));

    public boolean isMouseIcon() {
        return mouseIcon > 0;
    }

    public static KeyPrompt of(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            int button = key.getValue();
            return button >= 0 && button <= 2 ? new KeyPrompt("", button + 1) : text("M" + (button + 1));
        }

        String name = key.getName();
        if (name.startsWith(KEYBOARD)) {
            String id = name.substring(KEYBOARD.length());
            String named = NAMED.get(id);
            if (named != null) return text(named);
            if (id.startsWith("keypad.")) return text("N" + id.substring(7));
            if (id.length() >= 2 && id.charAt(0) == 'f' && Character.isDigit(id.charAt(1))) {
                return text(id.toUpperCase(Locale.ROOT));
            }

            // Ask the OS first so non-QWERTY layouts show the character actually on the key.
            String local = key.getDisplayName().getString().toUpperCase(Locale.ROOT);
            if (local.length() == 1 && isDrawable(local.charAt(0))) return text(local);
            String punctuation = PUNCTUATION.get(id);
            if (punctuation != null) return text(punctuation);
            if (id.length() == 1) return text(id.toUpperCase(Locale.ROOT));
        }

        // Scancode-only or exotic keys: best effort from whatever the game calls them.
        return text(sanitize(key.getDisplayName().getString()));
    }

    private static KeyPrompt text(String text) {
        return new KeyPrompt(text, 0);
    }

    /** Upper-cases and drops anything the glyph sheet cannot draw. */
    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            // Upper-casing means the arrow slots (u/d/l/r) can never be hit by accident here.
            if (isDrawable(c) && out.length() < MAX_CHARS) out.append(c);
        }
        return out.isEmpty() ? "?" : out.toString();
    }

    private static boolean isDrawable(char c) {
        return !Character.isLowerCase(c) && GLYPHS.indexOf(c) >= 0;
    }
}
