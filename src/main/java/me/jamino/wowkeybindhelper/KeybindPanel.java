package me.jamino.wowkeybindhelper;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.jamino.wowkeybindhelper.compat.WynnInfo;
import me.jamino.wowkeybindhelper.config.BarConfig;
import me.jamino.wowkeybindhelper.render.KeyPromptRenderer;
import me.jamino.wowkeybindhelper.render.Prompts;
import me.jamino.wowkeybindhelper.render.Prompts.Shown;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The All Keybinds popup: every configured keybind, grouped under headings, in as many columns
 * as fit. Drawn as a modal over a container screen, so opening it never closes the inventory
 * or a bank. It is only available from those screens, not from the world.
 */
public final class KeybindPanel {
    private static final int PAD = 9;
    private static final int TITLE_HEIGHT = 15;
    private static final int HEADER_HEIGHT = 11;
    private static final int ROW_HEIGHT = KeyPromptRenderer.HEIGHT + 2;
    private static final int GROUP_GAP = 7;
    private static final int COLUMN_GAP = 16;
    private static final int SCREEN_MARGIN = 12;
    private static final int SCROLL_STEP = ROW_HEIGHT * 2;

    private static final int DIM = 0xA0000000;
    private static final int BORDER = 0xFF2B1818;
    private static final int BORDER_LIGHT = 0xFF50352D;
    private static final int FILL = 0xF2161618;
    private static final int TITLE_COLOR = 0xFFD37F;
    private static final int HEADER_COLOR = 0xA8B4BC;
    private static final int SCROLLBAR = 0x80FFFFFF;

    // The little "show spells on the inventory" switch above the Spells heading.
    private static final int TOGGLE_HEIGHT = 10;
    private static final int SWITCH_W = 13;
    private static final int SWITCH_H = 7;
    private static final int SWITCH_GAP = 4;
    private static final float TOGGLE_TEXT_SCALE = 0.75f;
    private static final int SWITCH_ON = 0xFF3F8F4A;
    private static final int SWITCH_OFF = 0xFF4A4448;
    private static final int SWITCH_KNOB = 0xFFE8E2D6;
    private static final int TOGGLE_TEXT = 0x8C979E;

    private record Group(String title, List<Shown> items, boolean spellToggle) {
        int height() {
            return (spellToggle ? TOGGLE_HEIGHT : 0) + HEADER_HEIGHT + items.size() * ROW_HEIGHT;
        }

        int width(Font font) {
            int w = font.width(title);
            if (spellToggle) w = Math.max(w, toggleWidth(font));
            for (Shown item : items) w = Math.max(w, item.width());
            return w;
        }
    }

    private static boolean open;
    private static int scroll;
    private static int maxScroll;
    private static int x0, y0, x1, y1;
    /** Clickable area of the spell switch in screen coordinates; empty when it is scrolled out of view. */
    private static int tx0, ty0, tx1, ty1;
    /** Where the spells were before the switch turned them off, so turning it back on restores that. */
    private static BarConfig.SpellBar lastShown = BarConfig.SpellBar.TOP;

    private KeybindPanel() {}

    public static boolean isOpen() {
        return open;
    }

    public static void toggle() {
        open = !open;
        scroll = 0;
    }

    public static void close() {
        open = false;
    }

    public static boolean contains(double x, double y) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    /** Handles a click inside the panel. */
    public static void click(double x, double y) {
        if (x >= tx0 && x < tx1 && y >= ty0 && y < ty1) toggleSpellBar();
    }

    /** Center of the spell switch in GUI coordinates as last drawn, or null if it isn't visible. For tests. */
    public static double[] spellToggleCenter() {
        if (tx1 <= tx0) return null;
        return new double[] {tx0 + 1 + SWITCH_W / 2.0, (ty0 + ty1) / 2.0};
    }

    private static void toggleSpellBar() {
        BarConfig cfg = BarConfig.get();
        if (cfg.spellBar == BarConfig.SpellBar.OFF) {
            cfg.spellBar = lastShown;
        } else {
            lastShown = cfg.spellBar;
            cfg.spellBar = BarConfig.SpellBar.OFF;
        }
        BarConfig.save();
    }

    private static String toggleLabel() {
        return Component.translatable("wowkeybindhelper.panel.show_spells").getString();
    }

    private static int toggleWidth(Font font) {
        return SWITCH_W + SWITCH_GAP + Math.round(font.width(toggleLabel()) * TOGGLE_TEXT_SCALE);
    }

    public static void scrollBy(double wheel) {
        scroll = Math.clamp(scroll - (int) Math.signum(wheel) * SCROLL_STEP, 0, maxScroll);
    }

    /** @param modal true when drawn over another screen: dims it and lifts the panel above its contents */
    public static void draw(GuiGraphics g, Font font, int screenWidth, int screenHeight, boolean modal) {
        List<Group> groups = groups(font);

        int columnWidth = 0;
        int total = 0;
        for (Group group : groups) {
            columnWidth = Math.max(columnWidth, group.width(font));
            total += group.height() + GROUP_GAP;
        }

        int usable = screenWidth - SCREEN_MARGIN * 2 - PAD * 2;
        int columnCount = Math.clamp((usable + COLUMN_GAP) / (columnWidth + COLUMN_GAP), 1, Math.max(1, groups.size()));

        // Fill columns left to right, moving on once a column is about its fair share tall.
        List<List<Group>> columns = new ArrayList<>();
        columns.add(new ArrayList<>());
        int target = total / columnCount;
        int filled = 0;
        int contentHeight = 0;
        for (Group group : groups) {
            int size = group.height() + GROUP_GAP;
            if (filled > 0 && filled + size / 2 > target && columns.size() < columnCount) {
                columns.add(new ArrayList<>());
                filled = 0;
            }
            columns.getLast().add(group);
            filled += size;
            contentHeight = Math.max(contentHeight, filled - GROUP_GAP);
        }

        int viewHeight = Math.min(contentHeight, screenHeight - SCREEN_MARGIN * 2 - TITLE_HEIGHT - PAD * 2);
        // Each column is only as wide as its own contents, so one long label doesn't stretch them all.
        int[] widths = new int[columns.size()];
        int width = Math.max(0, columns.size() - 1) * COLUMN_GAP + PAD * 2;
        for (int i = 0; i < widths.length; i++) {
            for (Group group : columns.get(i)) widths[i] = Math.max(widths[i], group.width(font));
            width += widths[i];
        }
        width = Math.max(width, font.width(title()) + PAD * 2);
        int height = TITLE_HEIGHT + viewHeight + PAD * 2;
        x0 = (screenWidth - width) / 2;
        y0 = (screenHeight - height) / 2;
        x1 = x0 + width;
        y1 = y0 + height;
        maxScroll = Math.max(0, contentHeight - viewHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);

        if (modal) {
            KeyPromptRenderer.raise(g);
            KeyPromptRenderer.rect(g, 0, 0, screenWidth, screenHeight, DIM);
        }
        KeyPromptRenderer.rect(g, x0 + 1, y0, x1 - 1, y1, BORDER);
        KeyPromptRenderer.rect(g, x0, y0 + 1, x1, y1 - 1, BORDER);
        KeyPromptRenderer.rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, BORDER_LIGHT);
        KeyPromptRenderer.rect(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, FILL);

        String title = title();
        KeyPromptRenderer.text(g, font, title, (screenWidth - font.width(title)) / 2, y0 + PAD - 1, TITLE_COLOR, 255);

        int top = y0 + PAD + TITLE_HEIGHT;
        tx0 = ty0 = tx1 = ty1 = 0;
        KeyPromptRenderer.scissor(g, x0 + 2, top, x1 - 2, top + viewHeight);
        int x = x0 + PAD;
        for (int i = 0; i < columns.size(); i++) {
            List<Group> column = columns.get(i);
            int y = top - scroll;
            for (Group group : column) {
                if (group.spellToggle) {
                    drawToggle(g, font, x, y);
                    // Only clickable where it is actually visible inside the scroll view.
                    int visTop = Math.max(y, top), visBottom = Math.min(y + SWITCH_H + 1, top + viewHeight);
                    if (visTop < visBottom) {
                        tx0 = x - 1;
                        ty0 = visTop - 1;
                        tx1 = x + toggleWidth(font) + 1;
                        ty1 = visBottom + 1;
                    }
                    y += TOGGLE_HEIGHT;
                }
                KeyPromptRenderer.text(g, font, group.title, x, y, HEADER_COLOR, 255);
                y += HEADER_HEIGHT;
                for (Shown item : group.items) {
                    Prompts.draw(g, font, item, x, y, Prompts.LABEL_COLOR, 255);
                    y += ROW_HEIGHT;
                }
                y += GROUP_GAP;
            }
            x += widths[i] + COLUMN_GAP;
        }
        KeyPromptRenderer.endScissor(g);

        if (maxScroll > 0) {
            int thumb = Math.max(8, viewHeight * viewHeight / contentHeight);
            int thumbY = top + (viewHeight - thumb) * scroll / maxScroll;
            KeyPromptRenderer.rect(g, x1 - 5, thumbY, x1 - 4, thumbY + thumb, SCROLLBAR);
        }
    }

    private static void drawToggle(GuiGraphics g, Font font, int x, int y) {
        boolean on = BarConfig.get().spellBar != BarConfig.SpellBar.OFF;
        // A rounded-off pill: the track minus its corners, with a square knob at one end.
        KeyPromptRenderer.rect(g, x + 1, y, x + SWITCH_W - 1, y + SWITCH_H, on ? SWITCH_ON : SWITCH_OFF);
        KeyPromptRenderer.rect(g, x, y + 1, x + SWITCH_W, y + SWITCH_H - 1, on ? SWITCH_ON : SWITCH_OFF);
        int knobX = on ? x + SWITCH_W - SWITCH_H + 1 : x + 1;
        KeyPromptRenderer.rect(g, knobX, y + 1, knobX + SWITCH_H - 2, y + SWITCH_H - 1, SWITCH_KNOB);

        float textY = y + (SWITCH_H - font.lineHeight * TOGGLE_TEXT_SCALE) / 2f + 0.5f;
        KeyPromptRenderer.scaled(g, x + SWITCH_W + SWITCH_GAP, textY, TOGGLE_TEXT_SCALE,
                () -> KeyPromptRenderer.text(g, font, toggleLabel(), 0, 0, on ? HEADER_COLOR : TOGGLE_TEXT, 255));
    }

    private static String title() {
        return Component.translatable("wowkeybindhelper.panel.title").getString();
    }

    /** Every bound keybind under its heading, headings in order of first appearance in the config. */
    private static List<Group> groups(Font font) {
        Map<String, List<Shown>> byGroup = new LinkedHashMap<>();
        Set<InputConstants.Key> itemKeys = new HashSet<>();
        for (BarConfig.Entry entry : BarConfig.get().keybinds) {
            Shown shown = Prompts.resolve(entry, font);
            if (shown == null) continue;
            // Same rule as the bar: item actions sharing a key are one press (Wynntils stacks
            // several views on Shift), so only the first is listed. Other shared keys stay, since
            // two menus on one key is a real clash the player should see.
            if (entry.isItemAction() && !itemKeys.add(shown.key())) continue;
            byGroup.computeIfAbsent(entry.group.isEmpty() ? "Other" : entry.group, k -> new ArrayList<>()).add(shown);
        }

        List<Group> groups = new ArrayList<>();
        byGroup.forEach((name, items) -> {
            String heading = name;
            boolean spells = BarConfig.GROUP_SPELLS.equals(name);
            if (spells) {
                // The spell keys are whatever the current class's profile has bound right now.
                String className = WynnInfo.className();
                if (className != null) heading = className + " " + name;
            }
            groups.add(new Group(heading, items, spells));
        });
        return groups;
    }
}
