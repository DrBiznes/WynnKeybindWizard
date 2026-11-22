package me.jamino.wowkeybindhelper.compat;

import me.jamino.wowkeybindhelper.WowKeybindHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Resolves the {@code $spell1}..{@code $spell4} label placeholders. With Wynntils installed they
 * become the current class's spell names ("Bash", "Meteor", ...); otherwise "Spell 1".."Spell 4".
 */
public final class SpellNames {
    private static boolean wynntils = FabricLoader.getInstance().isModLoaded("wynntils");

    private SpellNames() {}

    public static String resolve(String label) {
        if (label.length() != 7 || !label.startsWith("$spell")) return label;
        int number = label.charAt(6) - '0';
        if (number < 1 || number > 4) return label;

        if (wynntils) {
            try {
                String name = WynntilsCompat.spellName(number);
                if (name != null) return name;
            } catch (Throwable t) {
                // A Wynntils update moved something. Fall back for the rest of the session.
                wynntils = false;
                WowKeybindHelper.LOGGER.warn("Wynntils spell lookup failed, using generic spell labels", t);
            }
        }
        return "Spell " + number;
    }
}
