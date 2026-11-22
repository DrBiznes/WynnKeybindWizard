package me.jamino.wynnkeybindwizard;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.jamino.wynnkeybindwizard.compat.WynnInfo;
import me.jamino.wynnkeybindwizard.config.BarConfig;
import me.jamino.wynnkeybindwizard.render.KeyPromptRenderer;
import me.jamino.wynnkeybindwizard.render.Prompts;
import me.jamino.wynnkeybindwizard.render.Prompts.Shown;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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

    /**
     * Wynntils' own scalable menu background. Borrowing its path means whatever resource pack
     * reskins Wynntils (e.g. WynnEdits Dark Theme) reskins this panel too. Without Wynntils
     * installed the texture is missing and the flat colours above are used instead.
     */
    private static final Identifier WYNNTILS_BACKGROUND =
            Identifier.fromNamespaceAndPath("wynntils", "textures/build_loadouts/scallable_widget_background.png");
    private static final int BG_TEX_W = 34;
    private static final int BG_TEX_H = 19;
    private static final int BG_BORDER = 5;
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

    // The "mod enabled" switch along the bottom, and the confirmation it asks for before turning off.
    private static final int FOOTER_GAP = 5;
    private static final int CONFIRM_MAX_W = 220;
    private static final int CONFIRM_LINE = 10;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_DIM = 0xB0000000;
    private static final int BUTTON_DANGER = 0xFF8A2E2E;
    private static final int BUTTON_PLAIN = 0xFF4A4448;
    private static final int BUTTON_TEXT = 0xE8E2D6;

    private record Group(String title, List<Shown> items, boolean spellToggle) {
        int height() {
            return (spellToggle ? TOGGLE_HEIGHT : 0) + HEADER_HEIGHT + items.size() * ROW_HEIGHT;
        }

        int width(Font font) {
            int w = font.width(title);
            if (spellToggle) w = Math.max(w, toggleWidth(font, spellLabel()));
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
    /** Clickable area of the mod switch. */
    private static int mx0, my0, mx1, my1;
    /** The turn-off confirmation is showing over the panel. */
    private static boolean confirming;
    /** The confirmation box and its two buttons, as last drawn. */
    private static int cx0, cy0, cx1, cy1;
    private static int okX0, okX1, cancelX0, cancelX1, buttonY0, buttonY1;
    /** Where the spells were before the switch turned them off, so turning it back on restores that. */
    private static BarConfig.SpellBar lastShown = BarConfig.SpellBar.TOP;

    private KeybindPanel() {}

    public static boolean isOpen() {
        return open;
    }

    public static void toggle() {
        open = !open;
        scroll = 0;
        confirming = false;
    }

    public static void close() {
        open = false;
        confirming = false;
    }

    /** Backs out of the turn-off confirmation if it is showing. Returns whether it was. */
    public static boolean cancelConfirm() {
        boolean was = confirming;
        confirming = false;
        return was;
    }

    public static boolean isConfirming() {
        return confirming;
    }

    public static boolean contains(double x, double y) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    /** Handles a click inside the panel. */
    public static void click(double x, double y) {
        if (confirming) {
            boolean onButtons = y >= buttonY0 && y < buttonY1;
            if (onButtons && x >= okX0 && x < okX1) turnOff();
            else if (onButtons && x >= cancelX0 && x < cancelX1) confirming = false;
            // Anywhere else on the panel backs out too; inside the box itself does nothing.
            else if (!(x >= cx0 && x < cx1 && y >= cy0 && y < cy1)) confirming = false;
            return;
        }
        if (x >= tx0 && x < tx1 && y >= ty0 && y < ty1) toggleSpellBar();
        else if (x >= mx0 && x < mx1 && y >= my0 && y < my1) confirming = true;
    }

    /** Center of the mod switch in GUI coordinates as last drawn. For tests. */
    public static double[] modToggleCenter() {
        return new double[] {mx0 + 1 + SWITCH_W / 2.0, (my0 + my1) / 2.0};
    }

    /** Center of the confirmation's Turn off (true) or Cancel (false) button, or null if it isn't showing. For tests. */
    public static double[] confirmButtonCenter(boolean turnOff) {
        if (!confirming) return null;
        return new double[] {turnOff ? (okX0 + okX1) / 2.0 : (cancelX0 + cancelX1) / 2.0, (buttonY0 + buttonY1) / 2.0};
    }

    /** Switches the whole mod off. Only an edit to the config (file or settings screen) turns it back on. */
    private static void turnOff() {
        BarConfig.get().enabled = false;
        BarConfig.save();
        close();
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

    private static String spellLabel() {
        return tr("show_spells");
    }

    private static String modLabel() {
        return tr("mod_enabled");
    }

    private static String tr(String key) {
        return Component.translatable("wynnkeybindwizard.panel." + key).getString();
    }

    private static int toggleWidth(Font font, String label) {
        return SWITCH_W + SWITCH_GAP + Math.round(font.width(label) * TOGGLE_TEXT_SCALE);
    }

    public static void scrollBy(double wheel) {
        if (confirming) return;
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

        int footerHeight = FOOTER_GAP + SWITCH_H;
        int viewHeight = Math.min(contentHeight, screenHeight - SCREEN_MARGIN * 2 - TITLE_HEIGHT - footerHeight - PAD * 2);
        // Each column is only as wide as its own contents, so one long label doesn't stretch them all.
        int[] widths = new int[columns.size()];
        int width = Math.max(0, columns.size() - 1) * COLUMN_GAP + PAD * 2;
        for (int i = 0; i < widths.length; i++) {
            for (Group group : columns.get(i)) widths[i] = Math.max(widths[i], group.width(font));
            width += widths[i];
        }
        width = Math.max(width, font.width(title()) + PAD * 2);
        width = Math.max(width, toggleWidth(font, modLabel()) + PAD * 2);
        int height = TITLE_HEIGHT + viewHeight + footerHeight + PAD * 2;
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
        background(g, x0, y0, x1, y1);

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
                    drawToggle(g, font, x, y, BarConfig.get().spellBar != BarConfig.SpellBar.OFF, spellLabel());
                    // Only clickable where it is actually visible inside the scroll view.
                    int visTop = Math.max(y, top), visBottom = Math.min(y + SWITCH_H + 1, top + viewHeight);
                    if (visTop < visBottom) {
                        tx0 = x - 1;
                        ty0 = visTop - 1;
                        tx1 = x + toggleWidth(font, spellLabel()) + 1;
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

        int footerY = top + viewHeight + FOOTER_GAP;
        drawToggle(g, font, x0 + PAD, footerY, BarConfig.get().enabled, modLabel());
        mx0 = x0 + PAD - 1;
        my0 = footerY - 1;
        mx1 = x0 + PAD + toggleWidth(font, modLabel()) + 1;
        my1 = footerY + SWITCH_H + 1;

        if (confirming) drawConfirm(g, font, screenWidth, screenHeight);
    }

    private static void background(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (KeyPromptRenderer.exists(WYNNTILS_BACKGROUND)) {
            KeyPromptRenderer.nineSlice(g, WYNNTILS_BACKGROUND, BG_TEX_W, BG_TEX_H, BG_BORDER, x0, y0, x1, y1);
        } else {
            KeyPromptRenderer.rect(g, x0 + 1, y0, x1 - 1, y1, BORDER);
            KeyPromptRenderer.rect(g, x0, y0 + 1, x1, y1 - 1, BORDER);
            KeyPromptRenderer.rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, BORDER_LIGHT);
            KeyPromptRenderer.rect(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, FILL);
        }
    }

    /** "Turn off Keybind Wizard?" box over the panel, saying the config is the only way back. */
    private static void drawConfirm(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        int width = Math.min(CONFIRM_MAX_W, screenWidth - SCREEN_MARGIN * 2);
        int inner = width - PAD * 2;
        String title = tr("turn_off.title");
        List<String> body = wrap(font, tr("turn_off.body"), inner);
        int height = PAD + TITLE_HEIGHT + body.size() * CONFIRM_LINE + BUTTON_GAP + BUTTON_H + PAD;

        KeyPromptRenderer.rect(g, x0, y0, x1, y1, PANEL_DIM);
        cx0 = (screenWidth - width) / 2;
        cy0 = (screenHeight - height) / 2;
        cx1 = cx0 + width;
        cy1 = cy0 + height;
        background(g, cx0, cy0, cx1, cy1);

        KeyPromptRenderer.text(g, font, title, (screenWidth - font.width(title)) / 2, cy0 + PAD - 1, TITLE_COLOR, 255);
        int y = cy0 + PAD + TITLE_HEIGHT;
        for (String line : body) {
            KeyPromptRenderer.text(g, font, line, cx0 + PAD, y, HEADER_COLOR, 255);
            y += CONFIRM_LINE;
        }

        buttonY0 = y + BUTTON_GAP;
        buttonY1 = buttonY0 + BUTTON_H;
        int buttonW = (inner - BUTTON_GAP) / 2;
        okX0 = cx0 + PAD;
        okX1 = okX0 + buttonW;
        cancelX1 = cx1 - PAD;
        cancelX0 = cancelX1 - buttonW;
        drawButton(g, font, okX0, okX1, tr("turn_off.confirm"), BUTTON_DANGER);
        drawButton(g, font, cancelX0, cancelX1, tr("turn_off.cancel"), BUTTON_PLAIN);
    }

    private static void drawButton(GuiGraphics g, Font font, int bx0, int bx1, String label, int fill) {
        KeyPromptRenderer.rect(g, bx0 + 1, buttonY0, bx1 - 1, buttonY1, fill);
        KeyPromptRenderer.rect(g, bx0, buttonY0 + 1, bx1, buttonY1 - 1, fill);
        int textY = buttonY0 + (BUTTON_H - font.lineHeight) / 2 + 1;
        KeyPromptRenderer.text(g, font, label, (bx0 + bx1 - font.width(label)) / 2, textY, BUTTON_TEXT, 255);
    }

    /** Word-wraps to {@code width}; a newline in the text starts a new paragraph. */
    private static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String line = "";
            for (String word : paragraph.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(next) > width) {
                    lines.add(line);
                    line = word;
                } else {
                    line = next;
                }
            }
            lines.add(line);
        }
        return lines;
    }

    private static void drawToggle(GuiGraphics g, Font font, int x, int y, boolean on, String label) {
        // A rounded-off pill: the track minus its corners, with a square knob at one end.
        KeyPromptRenderer.rect(g, x + 1, y, x + SWITCH_W - 1, y + SWITCH_H, on ? SWITCH_ON : SWITCH_OFF);
        KeyPromptRenderer.rect(g, x, y + 1, x + SWITCH_W, y + SWITCH_H - 1, on ? SWITCH_ON : SWITCH_OFF);
        int knobX = on ? x + SWITCH_W - SWITCH_H + 1 : x + 1;
        KeyPromptRenderer.rect(g, knobX, y + 1, knobX + SWITCH_H - 2, y + SWITCH_H - 1, SWITCH_KNOB);

        float textY = y + (SWITCH_H - font.lineHeight * TOGGLE_TEXT_SCALE) / 2f + 0.5f;
        KeyPromptRenderer.scaled(g, x + SWITCH_W + SWITCH_GAP, textY, TOGGLE_TEXT_SCALE,
                () -> KeyPromptRenderer.text(g, font, label, 0, 0, on ? HEADER_COLOR : TOGGLE_TEXT, 255));
    }

    private static String title() {
        return Component.translatable("wynnkeybindwizard.panel.title").getString();
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
