package me.jamino.wynnkeybindwizard.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/**
 * WynnExtras keeps its keys in its own settings, not in Controls, so they have no keybind id.
 * Entries name them as {@code wynnextras:<setting>} (e.g. {@code wynnextras:territoryMenuKey}),
 * and this reads that setting live from WynnExtras, so rebinding it there shows up straight away.
 *
 * <p>A key whose feature has an on/off switch named {@code <setting>Enabled} or, for settings
 * ending in {@code Key}, {@code <name>Enabled} ({@code quickRepairKey} → {@code quickRepairEnabled})
 * counts as unbound while that feature is off, since pressing it does nothing.
 */
public final class WynnExtrasKeys {
    public static final String PREFIX = "wynnextras:";
    private static final String CONFIG_CLASS = "julianh06.wynnextras.config.WynnExtrasConfig";

    private static boolean available = FabricLoader.getInstance().isModLoaded("wynnextras");
    private static Field instanceField;
    /** Key setting → its feature switch, if it has one. Lookups are cached, misses included. */
    private static final Map<String, Optional<Field>> KEYS = new HashMap<>();
    private static final Map<String, Optional<Field>> SWITCHES = new HashMap<>();

    private WynnExtrasKeys() {}

    /**
     * The GLFW key code WynnExtras has for this setting, or {@link GLFW#GLFW_KEY_UNKNOWN} when
     * WynnExtras is missing, the setting does not exist, it is unbound, or its feature is off.
     */
    public static int keyCode(String setting) {
        if (!available) return GLFW.GLFW_KEY_UNKNOWN;
        try {
            Object config = instance();
            if (config == null) return GLFW.GLFW_KEY_UNKNOWN;
            Optional<Field> key = KEYS.computeIfAbsent(setting, name -> field(name, int.class));
            if (key.isEmpty()) return GLFW.GLFW_KEY_UNKNOWN;
            Optional<Field> enabled = SWITCHES.computeIfAbsent(setting, WynnExtrasKeys::featureSwitch);
            if (enabled.isPresent() && !enabled.get().getBoolean(config)) return GLFW.GLFW_KEY_UNKNOWN;
            return key.get().getInt(config);
        } catch (Throwable t) {
            // A WynnExtras update moved its settings. Stop asking for the rest of the session.
            available = false;
            WynnKeybindWizard.LOGGER.warn("Could not read WynnExtras keys; its entries are hidden", t);
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    /** WynnExtras replaces its settings object when it reloads them, so it is looked up every time. */
    private static Object instance() throws ReflectiveOperationException {
        if (instanceField == null) instanceField = Class.forName(CONFIG_CLASS).getField("INSTANCE");
        return instanceField.get(null);
    }

    private static Optional<Field> featureSwitch(String setting) {
        Optional<Field> exact = field(setting + "Enabled", boolean.class);
        if (exact.isPresent() || !setting.endsWith("Key")) return exact;
        return field(setting.substring(0, setting.length() - 3) + "Enabled", boolean.class);
    }

    private static Optional<Field> field(String name, Class<?> type) {
        try {
            Field field = Class.forName(CONFIG_CLASS).getField(name);
            boolean usable = field.getType() == type && !Modifier.isStatic(field.getModifiers());
            return usable ? Optional.of(field) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }
}
