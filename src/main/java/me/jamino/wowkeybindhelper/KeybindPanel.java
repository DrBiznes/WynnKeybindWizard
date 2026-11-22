package me.jamino.wowkeybindhelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * as fit. Drawn either as a modal over a container screen (so opening it never closes the
 * inventory or a bank) or inside {@link AllKeybindsScreen} when opened from the world.
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

    private record Group(String title, List<Shown> items) {
        int height() {
            return HEADER_HEIGHT + items.size() * ROW_HEIGHT;
        }
    }

    private static boolean open;
    private static int scroll;
    private static int maxScroll;
    private static int x0, y0, x1, y1;

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

    public static void scrollBy(double wheel) {
        scroll = Math.clamp(scroll - (int) Math.signum(wheel) * SCROLL_STEP, 0, maxScroll);
    }

    /** @param modal true when drawn over another screen: dims it and lifts the panel above its contents */
    public static void draw(GuiGraphics g, Font font, int screenWidth, int screenHeight, boolean modal) {
        List<Group> groups = groups(font);

        int columnWidth = 0;
        int total = 0;
        for (Group group : groups) {
            columnWidth = Math.max(columnWidth, font.width(group.title));
            for (Shown item : group.items) columnWidth = Math.max(columnWidth, item.width());
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
            for (Group group : columns.get(i)) {
                widths[i] = Math.max(widths[i], font.width(group.title));
                for (Shown item : group.items) widths[i] = Math.max(widths[i], item.width());
            }
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
        KeyPromptRenderer.scissor(g, x0 + 2, top, x1 - 2, top + viewHeight);
        int x = x0 + PAD;
        for (int i = 0; i < columns.size(); i++) {
            List<Group> column = columns.get(i);
            int y = top - scroll;
            for (Group group : column) {
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

    private static String title() {
        return Component.translatable("wowkeybindhelper.panel.title").getString();
    }

    /** Every bound keybind under its heading, headings in order of first appearance in the config. */
    private static List<Group> groups(Font font) {
        Map<String, List<Shown>> byGroup = new LinkedHashMap<>();
        for (BarConfig.Entry entry : BarConfig.get().keybinds) {
            Shown shown = Prompts.resolve(entry, font);
            if (shown == null) continue;
            byGroup.computeIfAbsent(entry.group.isEmpty() ? "Other" : entry.group, k -> new ArrayList<>()).add(shown);
        }

        List<Group> groups = new ArrayList<>();
        byGroup.forEach((name, items) -> {
            String heading = name;
            if (BarConfig.GROUP_SPELLS.equals(name)) {
                // The spell keys are whatever the current class's profile has bound right now.
                String className = WynnInfo.className();
                if (className != null) heading = className + " " + name;
            }
            groups.add(new Group(heading, items));
        });
        return groups;
    }
}
