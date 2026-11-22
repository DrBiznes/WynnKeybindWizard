package me.jamino.wynnkeybindhelper.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game settings screen. Only loaded when Cloth Config is installed, so nothing but
 * {@link ModMenuIntegration} may reference this class. It is a view over {@link BarConfig}:
 * the JSON file stays the source of truth, and adding new keybind entries is still done there.
 */
public final class ClothConfigScreen {
    private ClothConfigScreen() {}

    public static Screen create(Screen parent) {
        BarConfig.refresh();
        BarConfig cfg = BarConfig.get();
        BarConfig defaults = new BarConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("wynnkeybindhelper.config.title"))
                .setSavingRunnable(BarConfig::save);
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
                        + " reorder or add keybinds in config/wynnkeybindhelper.json.")).build());
        ConfigCategory items = builder.getOrCreateCategory(Component.literal("Item Actions"));
        items.addEntry(entries.startTextDescription(Component.literal(
                "Pick what the bar shows while you hover an item (dynamic mode). Unpicked actions"
                        + " are still listed in the All Keybinds popup.")).build());
        for (BarConfig.Entry entry : cfg.keybinds) {
            if (entry.isSpell()) continue;
            String name = entry.group.isEmpty() || entry.isItemAction()
                    ? entry.label : entry.label + "  (" + entry.group + ")";
            (entry.isItemAction() ? items : bar).addEntry(entries.startBooleanToggle(Component.literal(name), entry.onBar)
                    .setDefaultValue(defaultOnBar(defaults, entry))
                    .setSaveConsumer(value -> entry.onBar = value)
                    .build());
        }

        return builder.build();
    }

    /** The shipped default for this entry, matched by label and keys; off for anything hand-made. */
    private static boolean defaultOnBar(BarConfig defaults, BarConfig.Entry entry) {
        return defaults.keybinds.stream().anyMatch(stock -> stock.onBar
                && stock.label.equals(entry.label) && stock.keys.equals(entry.keys));
    }
}
