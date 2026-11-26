package me.jamino.wynnkeybindwizard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/wynnkeybindwizard.json. The modpack ships this file; the defaults below are the
 * World of Wynncraft layout and are written out on first launch. The optional Cloth Config
 * screen edits this same object and saves it back through {@link #save()}.
 *
 * <p>{@link #keybinds} is the one list of everything the mod knows about. Its order is the
 * priority order for the bar; {@code group} only decides which heading an entry sits under in
 * the All Keybinds popup.
 */
public final class BarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "wynnkeybindwizard.json";

    /** Where the spell keys are shown on the inventory. They are always in the popup. */
    public enum SpellBar { TOP, BOTTOM, OFF }

    /** Where the bottom bar sits: on the bottom edge of the screen, or right under the inventory. */
    public enum BarPosition { SCREEN_BOTTOM, UNDER_INVENTORY }

    /** Values for {@link Entry#when}. */
    public static final String WHEN_HAS_MOUNT = "has_mount";
    public static final String WHEN_ITEM = "item";
    public static final String WHEN_ITEM_STATS = "item_stats";
    public static final String WHEN_ITEM_GEAR = "item_gear";
    /** Gear Wynntils' Compare has something to compare with right now. */
    public static final String WHEN_ITEM_COMPARABLE = "item_comparable";

    public static final String GROUP_SPELLS = "Spells";
    public static final String GROUP_COMMANDS = "Commands";

    /** Wynntils' "Execute Nth Custom Command" keybinds, in slot order. */
    public static final List<String> CUSTOM_COMMAND_KEYS = List.of(
            "wynntils.keybind.customCommandOne", "wynntils.keybind.customCommandTwo",
            "wynntils.keybind.customCommandThree", "wynntils.keybind.customCommandFour",
            "wynntils.keybind.customCommandFive", "wynntils.keybind.customCommandSix");
    /**
     * WynnMod's six chat hotkeys do the same job, so each command slot falls back to the
     * matching one: a pack uses one mod or the other, and the slot's name covers both.
     */
    public static final List<String> WYNNMOD_HOTKEYS = List.of(
            "Hotkey 1", "Hotkey 2", "Hotkey 3", "Hotkey 4", "Hotkey 5", "Hotkey 6");
    /** Entry label that stands for the text in {@link #customCommands}; {@code $command1}..{@code $command6}. */
    private static final String COMMAND_TOKEN = "$command";

    private static final String LOCK_FALLBACK = "key.mouse.middle";
    private static final String SCREENSHOT_KEY = "wynntils.keybind.screenshotItem";
    private static final String MARK_COMPARE_KEY = "wynntils.keybind.selectForComparing";

    private static BarConfig instance = new BarConfig();
    private static FileTime loadedAt;

    public boolean enabled = true;
    /** Also show under chests, banks and other container screens, not just the inventory. */
    public boolean showOnAllContainers = false;
    /**
     * Pressing a key from the bottom bar in the inventory closes it and does what the key does in
     * the world. Off, those keys do nothing there, as in vanilla.
     */
    public boolean keysWorkInInventory = true;
    /** How many keybinds the bottom bar holds (the All Keybinds button is extra). */
    public int barSlots = 6;
    /** How many item actions the bottom bar holds while an item is hovered (dynamic mode). */
    public int itemSlots = 5;
    public SpellBar spellBar = SpellBar.TOP;
    public BarPosition barPosition = BarPosition.SCREEN_BOTTOM;
    /**
     * Context awareness: the bar swaps to item actions while an item is hovered, and entries
     * whose {@code when} is not met (no mount yet) give their slot to the next one in line.
     */
    public boolean dynamic = true;
    /** Gap between the inventory and the bars. */
    public int offsetY = 6;
    public float scale = 1.0f;
    /** Opacity of the dark box behind the bars, 0 (just the keys) to 100. */
    public int backgroundOpacity = 50;
    /** Draw a key in its red "pressed" style while it is physically held down. */
    public boolean showHeldKeys = true;
    /**
     * What each of Wynntils' six custom command keybinds does in this pack ("Party Finder"),
     * shown as the label of the {@code $command1}..{@code $command6} entries. An empty label
     * hides that slot, so unused slots never show up.
     */
    public List<String> customCommands = new ArrayList<>(Collections.nCopies(CUSTOM_COMMAND_KEYS.size(), ""));

    public List<Entry> keybinds = new ArrayList<>(List.of(
            // Each spell is the first bound key of: Wynncraft Spell Caster, Better Wynn Macros,
            // Wynntils' own spell macros. Whichever macro mod the pack uses shows up.
            new Entry("$spell1", GROUP_SPELLS, "key.wynncraft-spell-caster.spell.first", "key.ktnwynnmacros.spell.1", "wynntils.keybind.castFirstSpell"),
            new Entry("$spell2", GROUP_SPELLS, "key.wynncraft-spell-caster.spell.second", "key.ktnwynnmacros.spell.2", "wynntils.keybind.castSecondSpell"),
            new Entry("$spell3", GROUP_SPELLS, "key.wynncraft-spell-caster.spell.third", "key.ktnwynnmacros.spell.3", "wynntils.keybind.castThirdSpell"),
            new Entry("$spell4", GROUP_SPELLS, "key.wynncraft-spell-caster.spell.fourth", "key.ktnwynnmacros.spell.4", "wynntils.keybind.castFourthSpell"),
            new Entry("Ultimate", GROUP_SPELLS, "key.swapOffhand"),

            new Entry("Quest Book", "Menus", "wynntils.keybind.openContentBook").onBar(),
            new Entry("$command1", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(0), WYNNMOD_HOTKEYS.get(0)).onBar(),
            new Entry("$command2", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(1), WYNNMOD_HOTKEYS.get(1)).onBar(),
            new Entry("$command3", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(2), WYNNMOD_HOTKEYS.get(2)).onBar(),
            new Entry("$command4", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(3), WYNNMOD_HOTKEYS.get(3)).onBar(),
            new Entry("$command5", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(4), WYNNMOD_HOTKEYS.get(4)).onBar(),
            new Entry("$command6", GROUP_COMMANDS, CUSTOM_COMMAND_KEYS.get(5), WYNNMOD_HOTKEYS.get(5)).onBar(),
            new Entry("Mount", "World", "wynntils.keybind.mountHorse").onBar().when(WHEN_HAS_MOUNT),
            new Entry("Social", "Menus", "wynntils.keybind.openPartyManagement").onBar(),
            new Entry("Item Guide", "Menus", "wynntils.keybind.openGuidesList").onBar(),
            // Only reaches the bar when something above it is hidden or unlabelled.
            new Entry("Lootpools", "Menus", "key.wynnventory.openRewardScreen").onBar(),
            new Entry("Achievements", "Menus", "key.wynntitles.open"),
            new Entry("Ingredient Pouch", "Menus", "wynntils.keybind.openIngredientPouch"),
            // WynnExtras: shown only while the feature is switched on in WynnExtras.
            new Entry("Territory Menu", "Menus", "wynnextras:territoryMenuKey"),
            new Entry("Guild Bank", "Menus", "wynnextras:guildBankKey"),
            // WynnMod
            new Entry("Scan Party", "Commands", "Scan Party"),

            new Entry("Map", "World", "wynntils.keybind.openMainMap"),
            new Entry("Guild Map", "World", "wynntils.keybind.openGuildMap"),
            new Entry("Third Person", "World", "key.nimblerewynnded.toggleperspective", "key.togglePerspective"),
            new Entry("Ping", "World", "Ping World"),
            new Entry("Zoom", "World", "zoomify.key.zoom"),
            new Entry("Shaders", "World", "iris.keybind.toggleShaders"),
            new Entry("Sprint", "World", "key.sprint"),
            new Entry("Sneak", "World", "key.sneak"),

            new Entry("Compare", "Items", "wynntils.keybind.holdToCompare").onBar().when(WHEN_ITEM_COMPARABLE),
            new Entry("Stat Ranges", "Items", "wynntils.keybind.showIdentificationRange").onBar().when(WHEN_ITEM_STATS),
            new Entry("Reroll Chance", "Items", "wynntils.keybind.showIdentificationReroll").onBar().when(WHEN_ITEM_STATS),
            // Wynntils' slot lock, else Wynncraft's own middle-click lock. One entry: they are the
            // same button by default and listing both read as two different actions.
            new Entry("Lock Slot", "Items", "wynntils.keybind.lockSlot", LOCK_FALLBACK).onBar().when(WHEN_ITEM),
            new Entry("Favorite", "Items", "wynntils.keybind.toggleFavorite").onBar().when(WHEN_ITEM),
            // Popup only: another mod already shows the screenshot key beside the screen, and
            // Mark to Compare is close enough to Compare.
            new Entry("Screenshot", "Items", SCREENSHOT_KEY).when(WHEN_ITEM),
            new Entry("Mark to Compare", "Items", MARK_COMPARE_KEY).when(WHEN_ITEM_GEAR)));

    /**
     * One keybind prompt.
     *
     * <p>{@code keys} are tried in order. Each is normally a keybind id exactly as it appears
     * after {@code key_} in options.txt (e.g. {@code wynntils.keybind.openMainMap}), so the prompt
     * follows rebinds and Class Keybind Profiles. {@code wynnextras:<setting>} reads a key from
     * WynnExtras' own settings (see {@link me.jamino.wynnkeybindwizard.compat.WynnExtrasKeys}). A physical key name such as
     * {@code key.keyboard.f3} also works for prompts that are not rebindable. An entry whose
     * keybind is missing (mod not installed) or unbound is hidden everywhere.
     *
     * <p>{@code when} is empty for "always", {@code has_mount}, or one of the item conditions
     * ({@code item}, {@code item_stats}, {@code item_gear}, {@code item_comparable}); item entries with {@code onBar} are
     * what the bar shows while an item is hovered. Labels {@code $spell1}..{@code $spell4} become spell names,
     * and {@code $command1}..{@code $command6} become the matching {@link #customCommands} text.
     *
     * <p>{@code worksInInventory} off keeps this entry's key from closing the inventory and acting
     * in the world while it is on the bottom bar (see {@link BarConfig#keysWorkInInventory}).
     * Spells and item actions never act from the inventory, so it does nothing for them.
     */
    public static final class Entry {
        public String label = "";
        public String group = "";
        public List<String> keys = new ArrayList<>();
        public boolean onBar = false;
        public String when = "";
        public boolean worksInInventory = true;

        public Entry() {}

        public Entry(String label, String group, String... keys) {
            this.label = label;
            this.group = group;
            this.keys = new ArrayList<>(List.of(keys));
        }

        public Entry onBar() {
            this.onBar = true;
            return this;
        }

        public Entry when(String when) {
            this.when = when;
            return this;
        }

        public boolean isItemAction() {
            return when.startsWith(WHEN_ITEM);
        }

        public boolean isSpell() {
            return GROUP_SPELLS.equals(group);
        }

        /** 1-6 when the label is a {@code $commandN} placeholder, else 0. */
        public int commandSlot() {
            if (label.length() != COMMAND_TOKEN.length() + 1 || !label.startsWith(COMMAND_TOKEN)) return 0;
            int slot = label.charAt(COMMAND_TOKEN.length()) - '0';
            return slot >= 1 && slot <= CUSTOM_COMMAND_KEYS.size() ? slot : 0;
        }
    }

    /** The pack's text for custom command slot 1-6, or "" when that slot is unused. */
    public String customCommand(int slot) {
        return slot >= 1 && slot <= customCommands.size() ? customCommands.get(slot - 1) : "";
    }

    /** Looked up on use, not at class load, so the defaults can be read outside the game (the mod key check). */
    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static BarConfig get() {
        return instance;
    }

    /** Loads the file if it is new or was edited since the last load, so hand edits apply on the next inventory open. */
    public static void refresh() {
        Path path = path();
        try {
            if (!Files.exists(path)) {
                instance = new BarConfig();
                save();
                return;
            }
            FileTime modified = Files.getLastModifiedTime(path);
            if (modified.equals(loadedAt)) return;
            loadedAt = modified;
            BarConfig loaded = GSON.fromJson(Files.readString(path), BarConfig.class);
            if (loaded != null) instance = loaded.sanitized();
        } catch (IOException | RuntimeException e) {
            // Bad JSON or an unreadable file: keep whatever was working before.
            WynnKeybindWizard.LOGGER.error("Could not load {}, keeping previous settings", path, e);
        }
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance.sanitized()));
            loadedAt = Files.getLastModifiedTime(path);
        } catch (IOException e) {
            WynnKeybindWizard.LOGGER.error("Could not save {}", path, e);
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
        if (customCommands == null) customCommands = new ArrayList<>();
        customCommands.replaceAll(text -> text == null ? "" : text.strip());
        while (customCommands.size() < CUSTOM_COMMAND_KEYS.size()) customCommands.add("");
        while (customCommands.size() > CUSTOM_COMMAND_KEYS.size()) customCommands.removeLast();
        if (spellBar == null) spellBar = SpellBar.TOP;
        if (barPosition == null) barPosition = BarPosition.SCREEN_BOTTOM;
        if (!(scale > 0)) scale = 1.0f;
        barSlots = Math.clamp(barSlots, 1, 12);
        itemSlots = Math.clamp(itemSlots, 1, 12);
        backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
        return this;
    }
}
