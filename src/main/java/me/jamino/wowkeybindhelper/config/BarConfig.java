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
 * World of Wynncraft layout and are written out on first launch.
 *
 * <p>An entry's {@code keys} are tried in order. Each is normally a keybind id exactly as it
 * appears after {@code key_} in options.txt (e.g. {@code wynntils.keybind.openMainMap}), so the
 * prompt follows rebinds and Class Keybind Profiles. A physical key name such as
 * {@code key.keyboard.f3} also works for prompts that are not rebindable. Entries whose keybind
 * is missing (mod not installed) or unbound are hidden.
 */
public final class BarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("wowkeybindhelper.json");

    private static BarConfig instance = new BarConfig();
    private static FileTime loadedAt;

    public boolean enabled = true;
    /** Also show under chests, banks and other container screens, not just the inventory. */
    public boolean showOnAllContainers = false;
    /** Preferred bar width in GUI pixels. It grows on its own when the screen is too short. */
    public int maxWidth = 300;
    /** Gap between the bottom of the inventory and the bar. */
    public int offsetY = 6;
    public float scale = 1.0f;
    /** Seconds each page stays up. 0 disables auto-cycling (click or scroll the bar instead). */
    public int cycleSeconds = 6;
    public boolean background = true;
    /** Draw a key in its red "pressed" style while it is physically held down. */
    public boolean showHeldKeys = true;

    /** Always visible, above the pages. */
    public List<Entry> pinned = new ArrayList<>(List.of(
            new Entry("$spell1", "wynntils.keybind.castFirstSpell"),
            new Entry("$spell2", "wynntils.keybind.castSecondSpell"),
            new Entry("$spell3", "wynntils.keybind.castThirdSpell"),
            new Entry("$spell4", "wynntils.keybind.castFourthSpell"),
            new Entry("Ultimate", "key.swapOffhand")));

    /** Cycled through one at a time. */
    public List<Page> pages = new ArrayList<>(List.of(
            new Page(
                    new Entry("Party Finder", "wynntils.keybind.customCommandOne"),
                    new Entry("Social", "wynntils.keybind.openPartyManagement"),
                    new Entry("Quest Book", "wynntils.keybind.openContentBook"),
                    new Entry("Item Guide", "wynntils.keybind.openGuidesList"),
                    new Entry("Use Menu", "wynntils.keybind.customCommandTwo"),
                    new Entry("Lootpools", "key.wynnventory.openRewardScreen"),
                    new Entry("Achievements", "key.wynntitles.open")),
            new Page(
                    new Entry("Map", "wynntils.keybind.openMainMap"),
                    new Entry("Guild Map", "wynntils.keybind.openGuildMap"),
                    new Entry("Mount", "wynntils.keybind.mountHorse"),
                    new Entry("Third Person", "key.nimblerewynnded.toggleperspective", "key.togglePerspective"),
                    new Entry("Zoom", "zoomify.key.zoom"),
                    new Entry("Shaders", "iris.keybind.toggleShaders"),
                    new Entry("Sprint", "key.sprint"),
                    new Entry("Sneak", "key.sneak"))));

    /** Replaces the current page while the mouse is over an item. */
    public List<Entry> itemHover = new ArrayList<>(List.of(
            new Entry("Lock Slot", "wynntils.keybind.lockSlot"),
            new Entry("Favorite", "wynntils.keybind.toggleFavorite"),
            new Entry("Share", "wynntils.keybind.shareItem"),
            new Entry("Screenshot", "wynntils.keybind.screenshotItem"),
            new Entry("ID Ranges", "wynntils.keybind.showIdentificationRange"),
            new Entry("Search Market", "wynntils.keybind.quickSearchTradeMarket")));

    public static final class Entry {
        public String label = "";
        public List<String> keys = new ArrayList<>();

        public Entry() {}

        Entry(String label, String... keys) {
            this.label = label;
            this.keys = new ArrayList<>(List.of(keys));
        }
    }

    public static final class Page {
        public List<Entry> entries = new ArrayList<>();

        public Page() {}

        Page(Entry... entries) {
            this.entries = new ArrayList<>(List.of(entries));
        }
    }

    public static BarConfig get() {
        return instance;
    }

    /** Loads the file if it is new or was edited since the last load, so tweaks apply on the next inventory open. */
    public static void refresh() {
        try {
            if (!Files.exists(PATH)) {
                instance = new BarConfig();
                Files.createDirectories(PATH.getParent());
                Files.writeString(PATH, GSON.toJson(instance));
                loadedAt = Files.getLastModifiedTime(PATH);
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

    /** Explicit JSON nulls would otherwise survive Gson and blow up at render time. */
    private BarConfig sanitized() {
        if (pinned == null) pinned = new ArrayList<>();
        if (pages == null) pages = new ArrayList<>();
        if (itemHover == null) itemHover = new ArrayList<>();
        pages.removeIf(page -> page == null);
        for (Page page : pages) {
            if (page.entries == null) page.entries = new ArrayList<>();
            clean(page.entries);
        }
        clean(pinned);
        clean(itemHover);
        if (!(scale > 0)) scale = 1.0f;
        return this;
    }

    private static void clean(List<Entry> entries) {
        entries.removeIf(entry -> entry == null || entry.keys == null);
        for (Entry entry : entries) {
            if (entry.label == null) entry.label = "";
            entry.keys.removeIf(key -> key == null);
        }
    }
}
