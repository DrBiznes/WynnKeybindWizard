package me.jamino.wowkeybindhelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import me.jamino.wowkeybindhelper.WowKeybindHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/wowkeybindhelper.json. The modpack ships this file; the defaults below are the
 * World of Wynncraft layout and are written out on first launch. The optional Cloth Config
 * screen edits this same object and saves it back through {@link #save()}.
 *
 * <p>{@link #keybinds} is the one list of everything the mod knows about. Its order is the
 * priority order for the bar; {@code group} only decides which heading an entry sits under in
 * the All Keybinds popup.
 */
public final class BarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("wowkeybindhelper.json");

    /** Where the spell keys are shown on the inventory. They are always in the popup. */
    public enum SpellBar { TOP, BOTTOM, OFF }

    /** Values for {@link Entry#when}. */
    public static final String WHEN_HAS_MOUNT = "has_mount";
    public static final String WHEN_ITEM = "item";
    public static final String WHEN_ITEM_STATS = "item_stats";
    public static final String WHEN_ITEM_GEAR = "item_gear";

    public static final String GROUP_SPELLS = "Spells";

    private static BarConfig instance = new BarConfig();
    private static FileTime loadedAt;

    public boolean enabled = true;
    /** Also show under chests, banks and other container screens, not just the inventory. */
    public boolean showOnAllContainers = false;
    /** How many keybinds the bottom bar holds (the All Keybinds button is extra). */
    public int barSlots = 6;
    public SpellBar spellBar = SpellBar.TOP;
    /**
     * Context awareness: the bar swaps to item actions while an item is hovered, and entries
     * whose {@code when} is not met (no mount yet) give their slot to the next one in line.
     */
    public boolean dynamic = true;
    /** Gap between the inventory and the bars. */
    public int offsetY = 6;
    public float scale = 1.0f;
    public boolean background = true;
    /** Draw a key in its red "pressed" style while it is physically held down. */
    public boolean showHeldKeys = true;

    public List<Entry> keybinds = new ArrayList<>(List.of(
            new Entry("$spell1", GROUP_SPELLS, "wynntils.keybind.castFirstSpell"),
            new Entry("$spell2", GROUP_SPELLS, "wynntils.keybind.castSecondSpell"),
            new Entry("$spell3", GROUP_SPELLS, "wynntils.keybind.castThirdSpell"),
            new Entry("$spell4", GROUP_SPELLS, "wynntils.keybind.castFourthSpell"),
            new Entry("Ultimate", GROUP_SPELLS, "key.swapOffhand"),

            new Entry("Quest Book", "Menus", "wynntils.keybind.openContentBook").onBar(),
            new Entry("Party Finder", "Menus", "wynntils.keybind.customCommandOne").onBar(),
            new Entry("Use Menu", "Menus", "wynntils.keybind.customCommandTwo").onBar(),
            new Entry("Mount", "World", "wynntils.keybind.mountHorse").onBar().when(WHEN_HAS_MOUNT),
            new Entry("Social", "Menus", "wynntils.keybind.openPartyManagement").onBar(),
            new Entry("Item Guide", "Menus", "wynntils.keybind.openGuidesList").onBar(),
            // Seventh in line: only reaches the bar when something above it is hidden.
            new Entry("Lootpools", "Menus", "key.wynnventory.openRewardScreen").onBar(),
            new Entry("Achievements", "Menus", "key.wynntitles.open"),

            new Entry("Map", "World", "wynntils.keybind.openMainMap"),
            new Entry("Guild Map", "World", "wynntils.keybind.openGuildMap"),
            new Entry("Third Person", "World", "key.nimblerewynnded.toggleperspective", "key.togglePerspective"),
            new Entry("Zoom", "World", "zoomify.key.zoom"),
            new Entry("Shaders", "World", "iris.keybind.toggleShaders"),
            new Entry("Sprint", "World", "key.sprint"),
            new Entry("Sneak", "World", "key.sneak"),

            new Entry("Compare", "Items", "wynntils.keybind.holdToCompare").when(WHEN_ITEM_GEAR),
            new Entry("Stat Ranges", "Items", "wynntils.keybind.showIdentificationRange").when(WHEN_ITEM_STATS),
            new Entry("Reroll Chance", "Items", "wynntils.keybind.showIdentificationReroll").when(WHEN_ITEM_STATS),
            new Entry("Lock Slot", "Items", "wynntils.keybind.lockSlot").when(WHEN_ITEM),
            new Entry("Favorite", "Items", "wynntils.keybind.toggleFavorite").when(WHEN_ITEM),
            new Entry("Share", "Items", "wynntils.keybind.shareItem").when(WHEN_ITEM_STATS),
            new Entry("Screenshot", "Items", "wynntils.keybind.screenshotItem").when(WHEN_ITEM),
            new Entry("Mark to Compare", "Items", "wynntils.keybind.selectForComparing").when(WHEN_ITEM_GEAR)));

    /**
     * One keybind prompt.
     *
     * <p>{@code keys} are tried in order. Each is normally a keybind id exactly as it appears
     * after {@code key_} in options.txt (e.g. {@code wynntils.keybind.openMainMap}), so the prompt
     * follows rebinds and Class Keybind Profiles. A physical key name such as
     * {@code key.keyboard.f3} also works for prompts that are not rebindable. An entry whose
     * keybind is missing (mod not installed) or unbound is hidden everywhere.
     *
     * <p>{@code when} is empty for "always", {@code has_mount}, or one of the item conditions
     * ({@code item}, {@code item_stats}, {@code item_gear}); item entries are what the bar shows
     * while an item is hovered. Labels {@code $spell1}..{@code $spell4} become spell names.
     */
    public static final class Entry {
        public String label = "";
        public String group = "";
        public List<String> keys = new ArrayList<>();
        public boolean onBar = false;
        public String when = "";

        public Entry() {}

        Entry(String label, String group, String... keys) {
            this.label = label;
            this.group = group;
            this.keys = new ArrayList<>(List.of(keys));
        }

        Entry onBar() {
            this.onBar = true;
            return this;
        }

        Entry when(String when) {
            this.when = when;
            return this;
        }

        public boolean isItemAction() {
            return when.startsWith(WHEN_ITEM);
        }

        public boolean isSpell() {
            return GROUP_SPELLS.equals(group);
        }
    }

    public static BarConfig get() {
        return instance;
    }

    /** Loads the file if it is new or was edited since the last load, so hand edits apply on the next inventory open. */
    public static void refresh() {
        try {
            if (!Files.exists(PATH)) {
                instance = new BarConfig();
                save();
                return;
            }
            FileTime modified = Files.getLastModifiedTime(PATH);
            if (modified.equals(loadedAt)) return;
            loadedAt = modified;
            BarConfig loaded = GSON.fromJson(Files.readString(PATH), BarConfig.class);
            if (loaded != null) instance = loaded.sanitized();
        } catch (IOException | RuntimeException e) {
            // Bad JSON or an unreadable file: keep whatever was working before.
            WowKeybindHelper.LOGGER.error("Could not load {}, keeping previous settings", PATH, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(instance.sanitized()));
            loadedAt = Files.getLastModifiedTime(PATH);
        } catch (IOException e) {
            WowKeybindHelper.LOGGER.error("Could not save {}", PATH, e);
        }
    }

    /** Explicit JSON nulls and nonsense numbers would otherwise survive Gson and blow up at render time. */
    private BarConfig sanitized() {
        if (keybinds == null) keybinds = new ArrayList<>();
        keybinds.removeIf(entry -> entry == null);
        for (Entry entry : keybinds) {
            if (entry.label == null) entry.label = "";
            if (entry.group == null) entry.group = "";
            if (entry.when == null) entry.when = "";
            if (entry.keys == null) entry.keys = new ArrayList<>();
            entry.keys.removeIf(key -> key == null);
        }
        if (spellBar == null) spellBar = SpellBar.TOP;
        if (!(scale > 0)) scale = 1.0f;
        barSlots = Math.clamp(barSlots, 1, 12);
        return this;
    }
}
