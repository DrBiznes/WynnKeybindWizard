package me.jamino.wynnkeybindwizard;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.jamino.wynnkeybindwizard.compat.WynnInfo;
import me.jamino.wynnkeybindwizard.config.BarConfig;
import me.jamino.wynnkeybindwizard.mixin.AbstractContainerScreenAccessor;
import me.jamino.wynnkeybindwizard.render.KeyPromptRenderer;
import me.jamino.wynnkeybindwizard.render.Prompts;
import me.jamino.wynnkeybindwizard.render.Prompts.Shown;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The bars around the inventory.
 *
 * <p>Below it: a short row of the most important keybinds, ending in the All Keybinds button.
 * With dynamic mode on, that row becomes the item actions while an item is hovered, and an entry
 * whose condition is not met (Mount without a mount) gives its slot to the next entry in line.
 *
 * <p>The spell keys go in their own bar above the inventory, or as a first row of the bottom bar,
 * or nowhere, per {@link BarConfig#spellBar}.
 */
public final class KeybindBar {
    private static final int ROW_HEIGHT = KeyPromptRenderer.HEIGHT + 3;
    private static final int ENTRY_GAP = 9;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int SCREEN_MARGIN = 2;
    /** Extra room under the spell bar: Wynncraft's inventory art has trim that reaches above the GUI's top edge. */
    private static final int SPELL_BAR_GAP = 6;
    private static final float MIN_SCALE = 0.5f;

    private static final long FADE_MS = 180;
    /** Keeps the item actions up while the mouse crosses the gap between two slots. */
    private static final long ITEM_LINGER_MS = 150;

    private static final int BACKGROUND_RGB = 0x101014;
    private static final int BUTTON_COLOR = 0xFFD37F;
    private static final int BUTTON_HOVER_COLOR = 0xFFFFFF;

    private record Row(List<Shown> items, int width) {}

    /** Screen-space rectangle; empty until something is drawn into it this frame. */
    private static final class Bounds {
        float x0, y0, x1, y1;

        void clear() {
            x1 = x0;
        }

        void set(float x0, float y0, float x1, float y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private static final Bounds BAR = new Bounds();
    private static final Bounds BUTTON = new Bounds();
    /** Every bar drawn last frame as {x0, y0, x1, y1, scale}, in GUI coordinates (read by the layout test). */
    private static final List<float[]> DRAWN = new ArrayList<>();

    /** Keys of the keybinds on the bar last frame that work from the inventory (see {@link #isBarKey}). */
    private static final Set<InputConstants.Key> BAR_KEYS = new HashSet<>();

    private static List<Shown> lastItemActions = List.of();
    private static long lastItemHover;
    private static boolean showingItem;
    private static long shownSince;

    private KeybindBar() {}

    /** Whether the bars (and the popup hotkey) apply to this screen. */
    public static boolean appliesTo(Screen screen) {
        BarConfig cfg = BarConfig.get();
        return cfg.enabled && (screen instanceof InventoryScreen
                || cfg.showOnAllContainers && screen instanceof AbstractContainerScreen<?>);
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY) {
        boolean buttonHovered = BUTTON.contains(mouseX, mouseY);
        BAR.clear();
        BUTTON.clear();
        DRAWN.clear();
        BAR_KEYS.clear();
        if (!appliesTo(screen)) return;

        BarConfig cfg = BarConfig.get();
        Font font = Minecraft.getInstance().font;
        AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) screen;
        long now = System.nanoTime() / 1_000_000L;

        // ---- what to show
        Slot slot = gui.wkh$hoveredSlot();
        ItemStack hovered = cfg.dynamic && !KeybindPanel.isOpen() && slot != null && slot.hasItem()
                && !isIngredientPouch(slot.getItem()) ? slot.getItem() : null;
        WynnInfo.ItemTraits traits = hovered == null ? null : WynnInfo.traits(hovered);
        boolean hasMount = !cfg.dynamic || WynnInfo.hasMount();

        List<Shown> spells = new ArrayList<>();
        List<Shown> main = new ArrayList<>();
        List<Shown> itemActions = new ArrayList<>();
        Set<InputConstants.Key> pressable = new HashSet<>();
        Set<InputConstants.Key> itemKeys = new HashSet<>();
        for (BarConfig.Entry entry : cfg.keybinds) {
            if (entry.isSpell()) {
                if (cfg.spellBar == BarConfig.SpellBar.OFF) continue;
                Shown shown = Prompts.resolve(entry, font);
                // No spell is ever cast from the inventory, so a spell key held for some other
                // bind on the same key (Favorite on F, say) must not light up the spell.
                if (shown != null) spells.add(shown.withHeld(false));
            } else if (entry.isItemAction()) {
                if (traits == null || !entry.onBar || itemActions.size() >= cfg.itemSlots
                        || !applies(entry.when, traits)) continue;
                Shown shown = Prompts.resolve(entry, font);
                // Two actions on one key (Wynntils puts several views on Shift) would read as a
                // contradiction; the earlier entry in the list wins.
                if (shown != null && itemKeys.add(shown.key())) itemActions.add(shown);
            } else if (entry.onBar && main.size() < cfg.barSlots) {
                if (BarConfig.WHEN_HAS_MOUNT.equals(entry.when) && !hasMount) continue;
                Shown shown = Prompts.resolve(entry, font);
                if (shown != null) {
                    main.add(shown);
                    if (entry.worksInInventory) pressable.add(shown.key());
                }
            }
        }

        if (!itemActions.isEmpty()) {
            lastItemActions = itemActions;
            lastItemHover = now;
        }
        boolean itemMode = cfg.dynamic && !lastItemActions.isEmpty() && now - lastItemHover < ITEM_LINGER_MS;
        if (itemMode != showingItem) {
            showingItem = itemMode;
            shownSince = now;
        }
        int fade = (int) Math.clamp(255 * (now - shownSince) / FADE_MS, 16, 255);

        // The keybinds stay pressable while the item actions are showing, except on a key the
        // hovered item is using: there the item action wins, as the bar says.
        // Entries the pack marked worksInInventory: false are shown but stay inert here.
        BAR_KEYS.addAll(pressable);
        if (itemMode) for (Shown shown : lastItemActions) BAR_KEYS.remove(shown.key());

        Shown button = Prompts.of(Prompts.boundKey(WynnKeybindWizard.openKey()),
                Component.translatable("wynnkeybindwizard.all_keybinds").getString(), font);
        List<Shown> bottom = new ArrayList<>(itemMode ? lastItemActions : main);
        bottom.add(button);

        // ---- bottom bar
        int centerX = gui.wkh$leftPos() + gui.wkh$imageWidth() / 2;
        List<Shown> spellsFirst = cfg.spellBar == BarConfig.SpellBar.BOTTOM ? spells : List.of();

        int below = gui.wkh$topPos() + gui.wkh$imageHeight() + cfg.offsetY;
        int spaceBelow = screen.height - below - SCREEN_MARGIN;
        if (spaceBelow >= KeyPromptRenderer.HEIGHT) {
            Layout bar = layout(spellsFirst, bottom, cfg.scale, spaceBelow, screen.width);
            // At the screen edge the bar keeps the same scale, so it still never covers the inventory.
            float top = cfg.barPosition == BarConfig.BarPosition.SCREEN_BOTTOM
                    ? screen.height - SCREEN_MARGIN - height(bar.rows) * bar.scale : below;
            drawBar(g, font, cfg, bar.rows, bar.fadedFrom, fade, button, buttonHovered, centerX, top, bar.scale, true);
        }

        // ---- spell bar above the inventory
        if (cfg.spellBar == BarConfig.SpellBar.TOP && !spells.isEmpty()) {
            int above = gui.wkh$topPos() - cfg.offsetY - SPELL_BAR_GAP;
            int spaceAbove = above - SCREEN_MARGIN;
            if (spaceAbove >= KeyPromptRenderer.HEIGHT) {
                Layout bar = layout(spells, List.of(), cfg.scale, spaceAbove, screen.width);
                float top = above - height(bar.rows) * bar.scale;
                drawBar(g, font, cfg, bar.rows, bar.rows.size(), 255, null, false, centerX, top, bar.scale, false);
            }
        }
    }

    /** Rows of a bar and the scale they are drawn at; rows from {@code fadedFrom} on hold the second list. */
    private record Layout(List<Row> rows, int fadedFrom, float scale) {}

    /**
     * The largest scale, up to the configured one, at which the bar fits the free space, with its
     * rows wrapped at that scale. Shrinking makes room for more entries per row, so a bar that
     * would need two rows at full size may fit one row at a smaller size (or even at full size).
     */
    private static Layout layout(List<Shown> first, List<Shown> second, float maxScale, int space, int screenWidth) {
        float lo = Math.min(MIN_SCALE, maxScale);
        if (fits(first, second, maxScale, space, screenWidth)) return wrapped(first, second, maxScale, screenWidth);
        // Still too tall at the smallest scale: draw it there anyway, as before.
        if (!fits(first, second, lo, space, screenWidth)) return wrapped(first, second, lo, screenWidth);
        // Fitting is monotonic in scale (smaller: fewer, shorter rows), so bisect for the largest that fits.
        float hi = maxScale;
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) / 2;
            if (fits(first, second, mid, space, screenWidth)) lo = mid;
            else hi = mid;
        }
        return wrapped(first, second, lo, screenWidth);
    }

    private static boolean fits(List<Shown> first, List<Shown> second, float scale, int space, int screenWidth) {
        return height(wrapped(first, second, scale, screenWidth).rows) * scale <= space;
    }

    private static Layout wrapped(List<Shown> first, List<Shown> second, float scale, int screenWidth) {
        int wrap = Math.max(60, (int) ((screenWidth - 16) / scale) - PAD_X * 2);
        List<Row> rows = new ArrayList<>(flow(first, wrap));
        int fadedFrom = rows.size();
        rows.addAll(flow(second, wrap));
        return new Layout(rows, fadedFrom, scale);
    }

    /**
     * Whether this key belongs to one of the keybinds on the bottom bar (not a spell, not an item
     * action), so pressing it in the inventory should do what it does in the world.
     */
    public static boolean isBarKey(InputConstants.Key key) {
        return BAR_KEYS.contains(key);
    }

    public static List<float[]> drawnBars() {
        return List.copyOf(DRAWN);
    }

    /** Returns true when the click landed on the bar, so it must not reach the inventory (and drop an item). */
    public static boolean click(double x, double y) {
        if (BUTTON.contains(x, y)) {
            KeybindPanel.toggle();
            return true;
        }
        return BAR.contains(x, y);
    }

    private static boolean applies(String when, WynnInfo.ItemTraits traits) {
        return switch (when) {
            case BarConfig.WHEN_ITEM_STATS -> traits.stats();
            case BarConfig.WHEN_ITEM_GEAR -> traits.gear();
            default -> true;
        };
    }

    /**
     * Wynncraft opens the inventory with the cursor resting on the ingredient pouch, which would
     * flip the bar to item actions every time; it has none worth showing, so it doesn't count.
     */
    private static boolean isIngredientPouch(ItemStack stack) {
        String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        return name != null && name.strip().equals("Ingredient Pouch");
    }

    private static int height(List<Row> rows) {
        return PAD_Y * 2 + rows.size() * ROW_HEIGHT - (ROW_HEIGHT - KeyPromptRenderer.HEIGHT);
    }

    private static void drawBar(GuiGraphics g, Font font, BarConfig cfg, List<Row> rows, int fadedFrom, int fade,
                                Shown button, boolean buttonHovered, int centerX, float top, float scale,
                                boolean isBottom) {
        int contentWidth = 0;
        for (Row row : rows) contentWidth = Math.max(contentWidth, row.width);
        int width = contentWidth + PAD_X * 2;
        int height = height(rows);
        int left = -width / 2;
        if (isBottom) BAR.set(centerX + left * scale, top, centerX + (left + width) * scale, top + height * scale);
        DRAWN.add(new float[] {centerX + left * scale, top, centerX + (left + width) * scale, top + height * scale, scale});

        KeyPromptRenderer.scaled(g, centerX, top, scale, () -> {
            if (cfg.backgroundOpacity > 0) {
                int bg = KeyPromptRenderer.argb(Math.round(cfg.backgroundOpacity * 2.55f), BACKGROUND_RGB);
                // A rectangle with the four corner pixels knocked off.
                KeyPromptRenderer.rect(g, left + 1, 0, left + width - 1, height, bg);
                KeyPromptRenderer.rect(g, left, 1, left + 1, height - 1, bg);
                KeyPromptRenderer.rect(g, left + width - 1, 1, left + width, height - 1, bg);
            }
            int y = PAD_Y;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int alpha = i >= fadedFrom ? fade : 255;
                int x = -row.width / 2;
                for (Shown item : row.items) {
                    boolean isButton = item == button;
                    int color = !isButton ? Prompts.LABEL_COLOR : buttonHovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
                    // The button never fades: it is the same thing in both modes.
                    Prompts.draw(g, font, item, x, y, color, isButton ? 255 : alpha);
                    if (isButton) {
                        BUTTON.set(centerX + (x - 2) * scale, top + (y - 2) * scale,
                                centerX + (x + item.width() + 2) * scale,
                                top + (y + KeyPromptRenderer.HEIGHT + 2) * scale);
                    }
                    x += item.width() + ENTRY_GAP;
                }
                y += ROW_HEIGHT;
            }
        });
    }

    private static List<Row> flow(List<Shown> items, int wrap) {
        List<Row> rows = new ArrayList<>();
        List<Shown> current = new ArrayList<>();
        int width = 0;
        for (Shown item : items) {
            int extended = current.isEmpty() ? item.width() : width + ENTRY_GAP + item.width();
            if (!current.isEmpty() && extended > wrap) {
                rows.add(new Row(current, width));
                current = new ArrayList<>();
                extended = item.width();
            }
            current.add(item);
            width = extended;
        }
        if (!current.isEmpty()) rows.add(new Row(current, width));
        return rows;
    }
}
