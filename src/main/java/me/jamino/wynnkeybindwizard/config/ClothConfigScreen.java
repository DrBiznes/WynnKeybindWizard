package me.jamino.wynnkeybindwizard.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import me.jamino.wynnkeybindwizard.compat.WynnInfo;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game settings screen. Only loaded when Cloth Config is installed, so nothing but
 * {@link ModMenuIntegration} may reference this class. It is a view over {@link BarConfig}:
 * the JSON file stays the source of truth. Reordering entries, physical-key prompts and
 * {@code when} conditions other than "item" are still done in the file.
 */
public final class ClothConfigScreen {
    public static final String PAGE_COMMANDS = "Custom Commands";
    public static final String PAGE_ADD = "Add Keybind";
    private static final String DEFAULT_NEW_GROUP = "Other";

    private ClothConfigScreen() {}

    public static Screen create(Screen parent) {
        return create(parent, null);
    }

    /** {@code page} is the tab to open on, by title; null for the first. */
    public static Screen create(Screen parent, String page) {
        BarConfig.refresh();
        BarConfig cfg = BarConfig.get();
        BarConfig defaults = new BarConfig();
        // Filled in by the Add Keybind page's save consumers, applied once they have all run.
        NewEntry adding = new NewEntry();
        Set<BarConfig.Entry> removing = new HashSet<>();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("wynnkeybindwizard.config.title"))
                .setSavingRunnable(() -> {
                    cfg.keybinds.removeAll(removing);
                    BarConfig.Entry added = adding.toEntry();
                    if (added != null) cfg.keybinds.add(added);
                    BarConfig.save();
                });
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), cfg.enabled)
                .setDefaultValue(defaults.enabled)
                .setSaveConsumer(value -> cfg.enabled = value)
                .build());
        general.addEntry(entries.startEnumSelector(Component.literal("Spell keys"), BarConfig.SpellBar.class, cfg.spellBar)
                .setDefaultValue(defaults.spellBar)
                .setEnumNameProvider(value -> Component.literal(switch ((BarConfig.SpellBar) value) {
                    case TOP -> "Top bar";
                    case BOTTOM -> "Bottom bar";
                    case OFF -> "Popup only";
                }))
                .setTooltip(Component.literal("Where the spell keys for your current class are shown:"),
                        Component.literal("their own bar above the inventory, a row on the bottom bar,"),
                        Component.literal("or only in the All Keybinds popup."))
                .setSaveConsumer(value -> cfg.spellBar = value)
                .build());
        general.addEntry(entries.startEnumSelector(Component.literal("Bottom bar position"), BarConfig.BarPosition.class, cfg.barPosition)
                .setDefaultValue(defaults.barPosition)
                .setEnumNameProvider(value -> Component.literal(switch ((BarConfig.BarPosition) value) {
                    case SCREEN_BOTTOM -> "Bottom of screen";
                    case UNDER_INVENTORY -> "Under inventory";
                }))
                .setTooltip(Component.literal("Put the bottom bar on the bottom edge of the screen,"),
                        Component.literal("or right under the inventory."))
                .setSaveConsumer(value -> cfg.barPosition = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Dynamic mode"), cfg.dynamic)
                .setDefaultValue(defaults.dynamic)
                .setTooltip(Component.literal("Show item actions while hovering an item, and replace"),
                        Component.literal("keybinds you can't use yet (Mount without a mount)."))
                .setSaveConsumer(value -> cfg.dynamic = value)
                .build());
        general.addEntry(entries.startIntSlider(Component.literal("Keybinds on the bar"), cfg.barSlots, 1, 12)
                .setDefaultValue(defaults.barSlots)
                .setSaveConsumer(value -> cfg.barSlots = value)
                .build());
        general.addEntry(entries.startIntSlider(Component.literal("Item actions on the bar"), cfg.itemSlots, 1, 12)
                .setDefaultValue(defaults.itemSlots)
                .setTooltip(Component.literal("How many actions the bar shows while you hover an item."))
                .setSaveConsumer(value -> cfg.itemSlots = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Show on all containers"), cfg.showOnAllContainers)
                .setDefaultValue(defaults.showOnAllContainers)
                .setTooltip(Component.literal("Also show under banks, chests and other container screens."))
                .setSaveConsumer(value -> cfg.showOnAllContainers = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Bar keys work in the inventory"), cfg.keysWorkInInventory)
                .setDefaultValue(defaults.keysWorkInInventory)
                .setTooltip(Component.literal("Pressing a key from the bottom bar closes the inventory and does what it does in the world."))
                .setSaveConsumer(value -> cfg.keysWorkInInventory = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Highlight held keys"), cfg.showHeldKeys)
                .setDefaultValue(defaults.showHeldKeys)
                .setSaveConsumer(value -> cfg.showHeldKeys = value)
                .build());
        general.addEntry(entries.startIntSlider(Component.literal("Background opacity"), cfg.backgroundOpacity, 0, 100)
                .setDefaultValue(defaults.backgroundOpacity)
                .setTextGetter(value -> Component.literal(value == 0 ? "Off" : value + "%"))
                .setTooltip(Component.literal("How dark the box behind the bars is."),
                        Component.literal("0 hides it, leaving just the keys."))
                .setSaveConsumer(value -> cfg.backgroundOpacity = value)
                .build());
        general.addEntry(entries.startFloatField(Component.literal("Scale"), cfg.scale)
                .setDefaultValue(defaults.scale)
                .setMin(0.5f)
                .setMax(3.0f)
                .setSaveConsumer(value -> cfg.scale = value)
                .build());
        general.addEntry(entries.startIntField(Component.literal("Gap from inventory"), cfg.offsetY)
                .setDefaultValue(defaults.offsetY)
                .setMin(0)
                .setMax(100)
                .setSaveConsumer(value -> cfg.offsetY = value)
                .build());

        // One toggle per keybind that could go on the bar. Spells have their own switch above.
        ConfigCategory bar = builder.getOrCreateCategory(Component.literal("Bottom Bar"));
        bar.addEntry(entries.startTextDescription(Component.literal(
                "Pick what goes on the bar. It shows the first ones that fit, in this order;"
                        + " reorder them in config/wynnkeybindwizard.json. Add keybinds on the Add Keybind page.")).build());
        ConfigCategory items = builder.getOrCreateCategory(Component.literal("Item Actions"));
        items.addEntry(entries.startTextDescription(Component.literal(
                "Pick what the bar shows while you hover an item (dynamic mode). Unpicked actions"
                        + " are still listed in the All Keybinds popup.")).build());
        for (BarConfig.Entry entry : cfg.keybinds) {
            if (entry.isSpell()) continue;
            String label = displayLabel(cfg, entry);
            String name = entry.group.isEmpty() || entry.isItemAction()
                    ? label : label + "  (" + entry.group + ")";
            (entry.isItemAction() ? items : bar).addEntry(entries.startBooleanToggle(Component.literal(name), entry.onBar)
                    .setDefaultValue(defaultOnBar(defaults, entry))
                    .setSaveConsumer(value -> entry.onBar = value)
                    .build());
        }

        addCommandsPage(builder, entries, cfg);
        addKeybindPage(builder, entries, cfg, defaults, adding, removing);
        if (page != null) builder.setFallbackCategory(builder.getOrCreateCategory(Component.literal(page)));
        return builder.build();
    }

    /** One text box per custom command slot (Wynntils custom command or WynnMod hotkey): what it does in this pack. */
    private static void addCommandsPage(ConfigBuilder builder, ConfigEntryBuilder entries, BarConfig cfg) {
        ConfigCategory commands = builder.getOrCreateCategory(Component.literal(PAGE_COMMANDS));
        commands.addEntry(entries.startTextDescription(Component.literal(
                "Name what each of Wynntils' custom command keybinds (or WynnMod's chat hotkeys) does in"
                        + " your pack. The commands themselves are set in Wynntils (Custom Command Keybinds) or"
                        + " WynnMod (Hotkey). The name is the label on the bar and in the popup. Leave a slot"
                        + " empty to hide it.")).build());
        for (int i = 0; i < BarConfig.CUSTOM_COMMAND_KEYS.size(); i++) {
            int index = i;
            commands.addEntry(entries.startStrField(Component.literal("Custom command " + (i + 1)), cfg.customCommands.get(i))
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Label for Wynntils' \"Execute " + ordinal(i + 1)
                            + " Custom Command Keybind\", or WynnMod's \"Hotkey " + (i + 1) + "\", e.g. Party Finder."))
                    .setSaveConsumer(value -> cfg.customCommands.set(index, value.strip()))
                    .build());
        }
    }

    /** Pick any keybind the game knows (from any mod) and add it; also remove ones added before. */
    private static void addKeybindPage(ConfigBuilder builder, ConfigEntryBuilder entries, BarConfig cfg,
                                       BarConfig defaults, NewEntry adding, Set<BarConfig.Entry> removing) {
        ConfigCategory page = builder.getOrCreateCategory(Component.literal(PAGE_ADD));
        page.addEntry(entries.startTextDescription(Component.literal(
                "Add a keybind from any installed mod. Pick it below (type to search), give it a label"
                        + " and a heading, then Save. To add another, open this page again. Players without"
                        + " that mod never see it.")).build());

        Map<String, String> byName = keybindChoices();
        page.addEntry(entries.startDropdownMenu(Component.literal("Keybind"),
                        DropdownMenuBuilder.TopCellElementBuilder.of("",
                                ClothConfigScreen.toKeybindId(byName),
                                id -> Component.literal(id.isEmpty() ? "" : choiceName(id))),
                        DropdownMenuBuilder.CellCreatorBuilder.<String>ofWidth(260, id -> Component.literal(choiceName(id))))
                .setSelections(byName.values())
                .setDefaultValue("")
                .setTooltip(Component.literal("Every keybind in Options > Controls, with the mod it belongs to."))
                .setSaveConsumer(value -> adding.key = value)
                .build());
        page.addEntry(entries.startStrField(Component.literal("Label"), "")
                .setDefaultValue("")
                .setTooltip(Component.literal("Text beside the key. Empty uses the keybind's name from Controls."))
                .setSaveConsumer(value -> adding.label = value.strip())
                .build());

        Set<String> groups = new LinkedHashSet<>();
        for (BarConfig.Entry entry : cfg.keybinds) {
            if (!entry.group.isEmpty() && !entry.isSpell()) groups.add(entry.group);
        }
        groups.add(DEFAULT_NEW_GROUP);
        page.addEntry(entries.startStringDropdownMenu(Component.literal("Heading"), DEFAULT_NEW_GROUP)
                .setSelections(groups)
                .setSuggestionMode(true)
                .setDefaultValue(DEFAULT_NEW_GROUP)
                .setTooltip(Component.literal("Which heading it goes under in the popup. Type a new one to make it."))
                .setSaveConsumer(value -> adding.group = value.strip())
                .build());
        page.addEntry(entries.startBooleanToggle(Component.literal("Item action"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Only offer it while an item is hovered, like Lock Slot."))
                .setSaveConsumer(value -> adding.itemAction = value)
                .build());
        page.addEntry(entries.startBooleanToggle(Component.literal("On the bar"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Also a candidate for the bottom bar (it goes last in line)."),
                        Component.literal("Otherwise it is only in the All Keybinds popup."))
                .setSaveConsumer(value -> adding.onBar = value)
                .build());
        page.addEntry(entries.startBooleanToggle(Component.literal("Works in inventory"), true)
                .setDefaultValue(true)
                .setTooltip(Component.literal("While it is on the bar, pressing its key in the inventory closes it"),
                        Component.literal("and does what the key does in the world. Not used by item actions."))
                .setSaveConsumer(value -> adding.worksInInventory = value)
                .build());

        List<BarConfig.Entry> added = cfg.keybinds.stream().filter(entry -> !isStock(defaults, entry)).toList();
        if (added.isEmpty()) return;
        page.addEntry(entries.startTextDescription(Component.literal("Keybinds that are not in the defaults")).build());
        for (BarConfig.Entry entry : added) {
            String name = displayLabel(cfg, entry) + (entry.group.isEmpty() ? "" : "  (" + entry.group + ")");
            page.addEntry(entries.startBooleanToggle(Component.literal(name), true)
                    .setDefaultValue(true)
                    .setYesNoTextSupplier(keep -> Component.literal(keep ? "Keep" : "Remove"))
                    .setSaveConsumer(keep -> {
                        if (!keep) removing.add(entry);
                    })
                    .build());
            if (entry.isItemAction()) continue;
            page.addEntry(entries.startBooleanToggle(Component.literal("    Works in inventory"), entry.worksInInventory)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("While " + displayLabel(cfg, entry) + " is on the bar, pressing its key in the"),
                            Component.literal("inventory closes it and does what the key does in the world."))
                    .setSaveConsumer(value -> entry.worksInInventory = value)
                    .build());
        }
    }

    /** What the search box text means: a picked "Name (Mod)", a raw keybind id, nothing (""), or invalid (null). */
    private static java.util.function.Function<String, String> toKeybindId(Map<String, String> byName) {
        return text -> {
            if (text.isEmpty()) return "";
            if (byName.containsKey(text)) return byName.get(text);
            return KeyMapping.get(text) != null ? text : null;
        };
    }

    /** "Name  (Mod)" for every registered keybind except our own, sorted by mod then name; mapped to its id. */
    private static Map<String, String> keybindChoices() {
        List<KeyMapping> mappings = new ArrayList<>(List.of(Minecraft.getInstance().options.keyMappings));
        mappings.remove(WynnKeybindWizard.openKey());
        mappings.sort(Comparator.comparing((KeyMapping m) -> m.getCategory().label().getString())
                .thenComparing(m -> Component.translatable(m.getName()).getString()));
        Map<String, String> byName = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings) byName.putIfAbsent(choiceName(mapping.getName()), mapping.getName());
        return byName;
    }

    private static String choiceName(String id) {
        KeyMapping mapping = KeyMapping.get(id);
        if (mapping == null) return id;
        return Component.translatable(id).getString() + "  (" + mapping.getCategory().label().getString() + ")";
    }

    /** The entry's label as a person reads it: placeholders explained rather than shown raw. */
    private static String displayLabel(BarConfig cfg, BarConfig.Entry entry) {
        int slot = entry.commandSlot();
        if (slot == 0) return WynnInfo.label(entry.label);
        String text = cfg.customCommand(slot);
        return text.isEmpty() ? "Custom command " + slot + " (unnamed, hidden)" : text;
    }

    /** The shipped default for this entry, matched by label and keys; off for anything hand-made. */
    private static boolean defaultOnBar(BarConfig defaults, BarConfig.Entry entry) {
        return defaults.keybinds.stream().anyMatch(stock -> stock.onBar
                && stock.label.equals(entry.label) && stock.keys.equals(entry.keys));
    }

    private static boolean isStock(BarConfig defaults, BarConfig.Entry entry) {
        return defaults.keybinds.stream().anyMatch(stock -> stock.label.equals(entry.label) && stock.keys.equals(entry.keys));
    }

    private static String ordinal(int n) {
        return n + switch (n) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /** What the Add Keybind page collected; {@link #toEntry()} is null when no keybind was picked. */
    private static final class NewEntry {
        String key = "";
        String label = "";
        String group = DEFAULT_NEW_GROUP;
        boolean itemAction;
        boolean onBar;
        boolean worksInInventory = true;

        BarConfig.Entry toEntry() {
            if (key == null || key.isEmpty() || KeyMapping.get(key) == null) return null;
            String text = label.isEmpty() ? Component.translatable(key).getString() : label;
            BarConfig.Entry entry = new BarConfig.Entry(text, group.isEmpty() ? DEFAULT_NEW_GROUP : group, key);
            if (onBar) entry.onBar();
            if (itemAction) entry.when(BarConfig.WHEN_ITEM);
            entry.worksInInventory = worksInInventory;
            return entry;
        }
    }
}
