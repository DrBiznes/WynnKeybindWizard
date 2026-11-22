package me.jamino.wowkeybindhelper;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.jamino.wowkeybindhelper.compat.SpellNames;
import me.jamino.wowkeybindhelper.config.BarConfig;
import me.jamino.wowkeybindhelper.mixin.AbstractContainerScreenAccessor;
import me.jamino.wowkeybindhelper.render.KeyPrompt;
import me.jamino.wowkeybindhelper.render.KeyPromptRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * The bar under the inventory: a pinned block (spells) that is always shown, and below it one
 * page of the remaining binds at a time. Pages auto-cycle, pause while the mouse is on the bar,
 * and flip on click/scroll. While an item is hovered the page is swapped for the item actions.
 *
 * <p>Bound keys are read from the live {@link KeyMapping}s every frame, so rebinding in Controls
 * or a Class Keybind Profiles switch shows up immediately with nothing to invalidate.
 */
public final class KeybindBar {
    private static final int ROW_HEIGHT = KeyPromptRenderer.HEIGHT + 3;
    private static final int ENTRY_GAP = 9;
    private static final int LABEL_GAP = 3;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int SECTION_GAP = 2;
    private static final int DOT = 2;
    private static final int DOT_GAP = 3;
    private static final int DOTS_HEIGHT = DOT + 3;
    private static final int WIDEN_STEP = 40;
    private static final float MIN_SCALE = 0.5f;

    private static final long FADE_MS = 180;
    private static final long ITEM_LINGER_MS = 150;
    /** A frame gap this long means the screen was closed in between; don't count it as page time. */
    private static final long RESUME_GAP_MS = 250;

    private static final int LABEL_COLOR = 0xE6E6E6;
    private static final int BACKGROUND = 0xC8101014;

    private static final int ITEM_PAGE = -1;

    private record Item(KeyPrompt prompt, boolean held, String label, int width) {}

    private record Row(List<Item> items, int width) {}

    private record Layout(List<Row> pinned, List<List<Row>> pages, List<Row> itemRows, int areaRows,
                          int contentWidth, int height) {}

    private static final Map<InputConstants.Key, KeyPrompt> PROMPTS = new HashMap<>();

    private static int page;
    private static int pageCount;
    private static long pageSince;
    private static int shown = Integer.MIN_VALUE;
    private static long shownSince;
    private static long lastFrame;
    private static long lastItemHover;

    /** Screen-space bounds of the bar as last drawn; empty when it is not on screen. */
    private static float boundsX0, boundsY0, boundsX1, boundsY1;

    private KeybindBar() {}

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY) {
        boundsX1 = boundsX0;
        BarConfig cfg = BarConfig.get();
        if (!cfg.enabled) return;
        if (!(screen instanceof InventoryScreen) && !cfg.showOnAllContainers) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Window window = mc.getWindow();

        List<Item> pinned = resolve(cfg.pinned, cfg, font, window);
        List<List<Item>> pages = new ArrayList<>();
        for (BarConfig.Page p : cfg.pages) {
            List<Item> items = resolve(p.entries, cfg, font, window);
            if (!items.isEmpty()) pages.add(items);
        }
        List<Item> itemActions = resolve(cfg.itemHover, cfg, font, window);
        if (pinned.isEmpty() && pages.isEmpty()) return;

        AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) screen;
        int top = gui.wkh$topPos() + gui.wkh$imageHeight() + cfg.offsetY;
        int centerX = gui.wkh$leftPos() + gui.wkh$imageWidth() / 2;
        int availableHeight = screen.height - top - 2;
        if (availableHeight < KeyPromptRenderer.HEIGHT) return;

        // Wrap at the preferred width; if that is too tall for the space under the inventory
        // (high GUI scale), trade height for width, and only then shrink.
        float scale = cfg.scale;
        int widest = Math.max(60, (int) ((screen.width - 16) / scale) - PAD_X * 2);
        int wrap = Math.min(cfg.maxWidth, widest);
        Layout layout = layout(pinned, pages, itemActions, wrap);
        while (layout.height * scale > availableHeight && wrap < widest) {
            wrap = Math.min(widest, wrap + WIDEN_STEP);
            layout = layout(pinned, pages, itemActions, wrap);
        }
        if (layout.height * scale > availableHeight) {
            scale = Math.max(MIN_SCALE, availableHeight / (float) layout.height);
        }

        int width = layout.contentWidth + PAD_X * 2;
        int left = -width / 2;
        boundsX0 = centerX + left * scale;
        boundsX1 = boundsX0 + width * scale;
        boundsY0 = top;
        boundsY1 = top + layout.height * scale;

        // ---- which page, and how faded in it is
        long now = System.nanoTime() / 1_000_000L;
        long elapsed = now - lastFrame;
        lastFrame = now;

        Slot hovered = gui.wkh$hoveredSlot();
        if (hovered != null && hovered.hasItem() && !itemActions.isEmpty()) lastItemHover = now;
        boolean itemMode = now - lastItemHover < ITEM_LINGER_MS;

        pageCount = pages.size();
        page = pageCount == 0 ? 0 : Math.floorMod(page, pageCount);
        if (itemMode || contains(mouseX, mouseY) || elapsed > RESUME_GAP_MS) {
            pageSince += elapsed;
        } else if (pageCount > 1 && cfg.cycleSeconds > 0 && now - pageSince >= cfg.cycleSeconds * 1000L) {
            page = (page + 1) % pageCount;
            pageSince = now;
        }

        int showing = itemMode ? ITEM_PAGE : page;
        if (showing != shown) {
            shown = showing;
            shownSince = now;
        }
        int alpha = (int) Math.clamp(255 * (now - shownSince) / FADE_MS, 16, 255);

        // ---- draw, in bar-local units with the origin at the top centre
        g.pose().pushMatrix();
        g.pose().translate(centerX, top);
        g.pose().scale(scale, scale);

        if (cfg.background) {
            KeyPromptRenderer.rect(g, left + 1, 0, left + width - 1, layout.height, BACKGROUND);
            KeyPromptRenderer.rect(g, left, 1, left + 1, layout.height - 1, BACKGROUND);
            KeyPromptRenderer.rect(g, left + width - 1, 1, left + width, layout.height - 1, BACKGROUND);
        }

        int y = PAD_Y;
        for (Row row : layout.pinned) {
            drawRow(g, font, row, y, 255);
            y += ROW_HEIGHT;
        }
        if (!layout.pinned.isEmpty() && layout.areaRows > 0) y += SECTION_GAP;

        List<Row> rows = itemMode ? layout.itemRows : pageCount > 0 ? layout.pages.get(page) : List.of();
        for (Row row : rows) {
            drawRow(g, font, row, y, alpha);
            y += ROW_HEIGHT;
        }

        if (pageCount > 1) {
            int dotsWidth = pageCount * DOT + (pageCount - 1) * DOT_GAP;
            int dotX = -dotsWidth / 2;
            int dotY = layout.height - PAD_Y - DOT + 1;
            for (int i = 0; i < pageCount; i++) {
                boolean active = !itemMode && i == page;
                KeyPromptRenderer.rect(g, dotX, dotY, dotX + DOT, dotY + DOT, active ? 0xFFFFFFFF : 0x60FFFFFF);
                dotX += DOT + DOT_GAP;
            }
        }

        g.pose().popMatrix();
    }

    /** Left click = next page, right click = previous. Returns true when the click was on the bar. */
    public static boolean click(double x, double y, int button) {
        if (pageCount < 2 || !contains(x, y)) return false;
        flip(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
        return true;
    }

    public static boolean scroll(double x, double y, double amount) {
        if (pageCount < 2 || amount == 0 || !contains(x, y)) return false;
        flip(amount > 0 ? -1 : 1);
        return true;
    }

    private static void flip(int by) {
        page = Math.floorMod(page + by, pageCount);
        pageSince = System.nanoTime() / 1_000_000L;
    }

    private static boolean contains(double x, double y) {
        return x >= boundsX0 && x < boundsX1 && y >= boundsY0 && y < boundsY1;
    }

    private static void drawRow(GuiGraphics g, Font font, Row row, int y, int alpha) {
        int x = -row.width / 2;
        for (Item item : row.items) {
            KeyPromptRenderer.draw(g, x, y, item.prompt, item.held, alpha);
            int labelX = x + KeyPromptRenderer.width(item.prompt) + LABEL_GAP;
            KeyPromptRenderer.text(g, font, item.label, labelX, y + 2, LABEL_COLOR, alpha);
            x += item.width + ENTRY_GAP;
        }
    }

    // ------------------------------------------------------------------ layout

    private static Layout layout(List<Item> pinned, List<List<Item>> pages, List<Item> itemActions, int wrap) {
        List<Row> pinnedRows = flow(pinned, wrap);
        List<Row> itemRows = flow(itemActions, wrap);
        List<List<Row>> pageRows = new ArrayList<>();
        // The page area is sized for the tallest page so the bar never changes size as it cycles.
        int areaRows = itemRows.size();
        int contentWidth = Math.max(widest(pinnedRows), widest(itemRows));
        for (List<Item> p : pages) {
            List<Row> rows = flow(p, wrap);
            pageRows.add(rows);
            areaRows = Math.max(areaRows, rows.size());
            contentWidth = Math.max(contentWidth, widest(rows));
        }

        int rowCount = pinnedRows.size() + areaRows;
        int height = PAD_Y * 2 + rowCount * ROW_HEIGHT - (ROW_HEIGHT - KeyPromptRenderer.HEIGHT);
        if (!pinnedRows.isEmpty() && areaRows > 0) height += SECTION_GAP;
        if (pages.size() > 1) height += DOTS_HEIGHT;
        return new Layout(pinnedRows, pageRows, itemRows, areaRows, contentWidth, height);
    }

    private static List<Row> flow(List<Item> items, int wrap) {
        List<Row> rows = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int width = 0;
        for (Item item : items) {
            int extended = current.isEmpty() ? item.width : width + ENTRY_GAP + item.width;
            if (!current.isEmpty() && extended > wrap) {
                rows.add(new Row(current, width));
                current = new ArrayList<>();
                extended = item.width;
            }
            current.add(item);
            width = extended;
        }
        if (!current.isEmpty()) rows.add(new Row(current, width));
        return rows;
    }

    private static int widest(List<Row> rows) {
        int widest = 0;
        for (Row row : rows) widest = Math.max(widest, row.width);
        return widest;
    }

    // ------------------------------------------------------------------ live key lookup

    private static List<Item> resolve(List<BarConfig.Entry> entries, BarConfig cfg, Font font, Window window) {
        List<Item> items = new ArrayList<>();
        for (BarConfig.Entry entry : entries) {
            InputConstants.Key key = boundKey(entry);
            if (key == null) continue;
            KeyPrompt prompt = PROMPTS.computeIfAbsent(key, KeyPrompt::of);
            String label = SpellNames.resolve(entry.label);
            int width = KeyPromptRenderer.width(prompt) + (label.isEmpty() ? 0 : LABEL_GAP + font.width(label));
            items.add(new Item(prompt, cfg.showHeldKeys && isDown(window, key), label, width));
        }
        return items;
    }

    /** The key currently bound for an entry, or null if it should be hidden. */
    private static InputConstants.Key boundKey(BarConfig.Entry entry) {
        for (String id : entry.keys) {
            KeyMapping mapping = KeyMapping.get(id);
            try {
                if (mapping != null) {
                    if (!mapping.isUnbound()) return InputConstants.getKey(mapping.saveString());
                } else if (id.startsWith("key.keyboard.") || id.startsWith("key.mouse.")) {
                    return InputConstants.getKey(id);
                }
            } catch (IllegalArgumentException ignored) {
                // Not a key name the game recognises; try the next fallback.
            }
        }
        return null;
    }

    private static boolean isDown(Window window, InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
