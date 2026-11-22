package me.jamino.wynnkeybindwizard.render;

import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws key prompts from the three textures in textures/gui.
 *
 * <p>Every call into Minecraft's GUI drawing API lives in this class, so a rendering API change
 * in a future version (26.x) is contained here.
 */
public final class KeyPromptRenderer {
    private static final Identifier KEYCAP = id("textures/gui/keycap.png");
    private static final Identifier GLYPHS = id("textures/gui/glyphs.png");
    private static final Identifier MOUSE = id("textures/gui/mouse.png");

    /** Height every prompt occupies, keycap or mouse. */
    public static final int HEIGHT = 14;

    private static final int KEYCAP_TEX = 16;
    private static final int SLICE = 3;
    private static final int HELD_U = 8;
    private static final int HELD_HEIGHT = 12;
    private static final int HELD_DROP = 2;
    private static final int TEXT_PAD = 2;

    private static final int GLYPH_W = 3;
    private static final int GLYPH_H = 5;
    private static final int GLYPH_CELL_W = 4;
    private static final int GLYPH_CELL_H = 6;
    private static final int GLYPH_COLS = 16;
    private static final int GLYPH_FIRST = 32;
    private static final int GLYPH_TEX_W = 64;
    private static final int GLYPH_TEX_H = 36;
    private static final int GLYPH_COLOR = 0x5A6165;
    private static final int GLYPH_COLOR_HELD = 0x732626;

    private static final int MOUSE_W = 11;
    private static final int MOUSE_CELL = 12;
    private static final int MOUSE_TEX_W = 48;
    private static final int MOUSE_TEX_H = 16;

    private KeyPromptRenderer() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WynnKeybindWizard.MOD_ID, path);
    }

    public static int width(KeyPrompt prompt) {
        if (prompt.isMouseIcon()) return MOUSE_W;
        return SLICE * 2 + TEXT_PAD * 2 + textWidth(prompt.text());
    }

    private static int textWidth(String text) {
        return Math.max(GLYPH_W, text.length() * GLYPH_CELL_W - 1);
    }

    /** @param alpha 0-255, applied to the whole prompt */
    public static void draw(GuiGraphics g, int x, int y, KeyPrompt prompt, boolean held, int alpha) {
        int white = argb(alpha, 0xFFFFFF);
        if (prompt.isMouseIcon()) {
            g.blit(RenderPipelines.GUI_TEXTURED, MOUSE, x, y, (prompt.mouseIcon()) * MOUSE_CELL, 0,
                    MOUSE_W, HEIGHT, MOUSE_TEX_W, MOUSE_TEX_H, white);
            return;
        }

        String text = prompt.text();
        int inner = TEXT_PAD * 2 + textWidth(text);
        int u = held ? HELD_U : 0;
        int h = held ? HELD_HEIGHT : HEIGHT;
        if (held) y += HELD_DROP;

        // Left edge, 1px middle column stretched across the label, right edge.
        g.blit(RenderPipelines.GUI_TEXTURED, KEYCAP, x, y, u, 0, SLICE, h, KEYCAP_TEX, KEYCAP_TEX, white);
        g.blit(RenderPipelines.GUI_TEXTURED, KEYCAP, x + SLICE, y, u + SLICE, 0, inner, h, 1, h,
                KEYCAP_TEX, KEYCAP_TEX, white);
        g.blit(RenderPipelines.GUI_TEXTURED, KEYCAP, x + SLICE + inner, y, u + SLICE + 1, 0, SLICE, h,
                KEYCAP_TEX, KEYCAP_TEX, white);

        int tint = argb(alpha, held ? GLYPH_COLOR_HELD : GLYPH_COLOR);
        int gx = x + SLICE + TEXT_PAD;
        int gy = y + 3;
        for (int i = 0; i < text.length(); i++) {
            int index = text.charAt(i) - GLYPH_FIRST;
            if (index >= 0 && index < GLYPH_COLS * (GLYPH_TEX_H / GLYPH_CELL_H)) {
                g.blit(RenderPipelines.GUI_TEXTURED, GLYPHS, gx + i * GLYPH_CELL_W, gy,
                        (index % GLYPH_COLS) * GLYPH_CELL_W, (index / GLYPH_COLS) * GLYPH_CELL_H,
                        GLYPH_W, GLYPH_H, GLYPH_TEX_W, GLYPH_TEX_H, tint);
            }
        }
    }

    /** True when some loaded mod or resource pack provides {@code texture}. */
    public static boolean exists(Identifier texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    /**
     * Stretches a plain (non-atlas) texture over the box as a nine-slice: corners kept as they are,
     * edges and middle stretched. {@code border} is the corner size in texture pixels.
     */
    public static void nineSlice(GuiGraphics g, Identifier texture, int texW, int texH, int border,
                                 int x0, int y0, int x1, int y1) {
        int b = border;
        int w = x1 - x0, h = y1 - y0;
        int innerW = w - b * 2, innerH = h - b * 2;
        int texInnerW = texW - b * 2, texInnerH = texH - b * 2;
        int[] dx = {x0, x0 + b, x1 - b};
        int[] dw = {b, innerW, b};
        int[] u = {0, b, texW - b};
        int[] uw = {b, texInnerW, b};
        int[] dy = {y0, y0 + b, y1 - b};
        int[] dh = {b, innerH, b};
        int[] v = {0, b, texH - b};
        int[] vh = {b, texInnerH, b};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (dw[col] <= 0 || dh[row] <= 0) continue;
                g.blit(RenderPipelines.GUI_TEXTURED, texture, dx[col], dy[row], u[col], v[row],
                        dw[col], dh[row], uw[col], vh[row], texW, texH);
            }
        }
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int rgb, int alpha) {
        g.drawString(font, text, x, y, argb(alpha, rgb), true);
    }

    public static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    public static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    public static void endScissor(GuiGraphics g) {
        g.disableScissor();
    }

    /** Everything drawn after this sits above everything drawn before it. */
    public static void raise(GuiGraphics g) {
        g.nextStratum();
    }

    /** Draws {@code body} scaled about the point (x, y), which becomes its origin. */
    public static void scaled(GuiGraphics g, float x, float y, float scale, Runnable body) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        body.run();
        g.pose().popMatrix();
    }

    public static int argb(int alpha, int rgb) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
