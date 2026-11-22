package me.jamino.wynnkeybindwizard.compat;

import java.util.function.Supplier;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the mod asks Wynntils. Safe to call without Wynntils installed: each question then
 * gets its "don't know" answer, which is always the one that hides nothing.
 */
public final class WynnInfo {
    /** What Wynntils can do with a hovered item. */
    public record ItemTraits(boolean stats, boolean gear) {
        /** Not a recognised Wynncraft item: no stat views, nothing to compare. */
        static final ItemTraits PLAIN = new ItemTraits(false, false);
        /** Wynntils unavailable: show every item action rather than guess. */
        static final ItemTraits UNKNOWN = new ItemTraits(true, true);
    }

    private static boolean available = FabricLoader.getInstance().isModLoaded("wynntils");

    private WynnInfo() {}

    /** Turns {@code $spell1}..{@code $spell4} into the current class's spell name, or "Spell N". */
    public static String label(String label) {
        if (label.length() != 7 || !label.startsWith("$spell")) return label;
        int number = label.charAt(6) - '0';
        if (number < 1 || number > 4) return label;
        String name = ask(() -> WynntilsCompat.spellName(number), null);
        return name != null ? name : "Spell " + number;
    }

    /** Current class name, or null when unknown. */
    public static String className() {
        return ask(WynntilsCompat::className, null);
    }

    public static boolean hasMount() {
        return ask(WynntilsCompat::hasMount, true);
    }

    public static ItemTraits traits(ItemStack stack) {
        return ask(() -> WynntilsCompat.traits(stack), ItemTraits.UNKNOWN);
    }

    private static <T> T ask(Supplier<T> question, T unknown) {
        if (!available) return unknown;
        try {
            return question.get();
        } catch (Throwable t) {
            // A Wynntils update moved something. Stop asking for the rest of the session.
            available = false;
            WynnKeybindWizard.LOGGER.warn("Wynntils lookup failed; class, mount and item awareness are off", t);
            return unknown;
        }
    }
}
