package me.jamino.wowkeybindhelper.config;

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
                .setTitle(Component.translatable("wowkeybindhelper.config.title"))
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
        general.addEntry(entries.startBooleanToggle(Component.literal("Show on all containers"), cfg.showOnAllContainers)
                .setDefaultValue(defaults.showOnAllContainers)
                .setTooltip(Component.literal("Also show under banks, chests and other container screens."))
                .setSaveConsumer(value -> cfg.showOnAllContainers = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Highlight held keys"), cfg.showHeldKeys)
                .setDefaultValue(defaults.showHeldKeys)
                .setSaveConsumer(value -> cfg.showHeldKeys = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Background"), cfg.background)
                .setDefaultValue(defaults.background)
                .setSaveConsumer(value -> cfg.background = value)
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

        // One toggle per keybind that could go on the bar. Spells and item actions have their
        // own switches above, so they are not listed.
        ConfigCategory bar = builder.getOrCreateCategory(Component.literal("Bottom Bar"));
        bar.addEntry(entries.startTextDescription(Component.literal(
                "Pick what goes on the bar. It shows the first ones that fit, in this order;"
                        + " reorder or add keybinds in config/wowkeybindhelper.json.")).build());
        for (BarConfig.Entry entry : cfg.keybinds) {
            if (entry.isSpell() || entry.isItemAction()) continue;
            String name = entry.group.isEmpty() ? entry.label : entry.label + "  (" + entry.group + ")";
            bar.addEntry(entries.startBooleanToggle(Component.literal(name), entry.onBar)
                    .setDefaultValue(false)
                    .setSaveConsumer(value -> entry.onBar = value)
                    .build());
        }

        return builder.build();
    }
}
