package me.jamino.wynnkeybindwizard.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws key prompts: with Wynncraft's own key art when its resource pack is loaded
 * ({@link WynnKeyArt}), otherwise as a plain text chip.
 *
 * <p>Every call into Minecraft's GUI drawing API lives in this class, so a rendering API change
 * in a future version (26.x) is contained here.
 */
public final class KeyPromptRenderer {
    /** Height every prompt occupies, keycap or mouse. */
    public static final int HEIGHT = 14;

    /** Multiplied over the key art while the key is held. */
    private static final int HELD_TINT = 0xFF8A70;

    private static final int CHIP_PAD = 3;
    private static final int CHIP_OUTLINE = 0x2B1818;
    private static final int CHIP_FACE = 0xD9D9D9;
    private static final int CHIP_LIP = 0x8A9095;
    private static final int CHIP_TEXT = 0x3A3F42;
    private static final int CHIP_FACE_HELD = 0xDB5B3C;
    private static final int CHIP_LIP_HELD = 0x8A2A1A;
    private static final int CHIP_TEXT_HELD = 0xFFFFFF;
    private static final String[] MOUSE_TEXT = {"LMB", "RMB", "MMB"};

    private KeyPromptRenderer() {}

    public static int width(KeyPrompt prompt) {
        WynnKeyArt art = WynnKeyArt.current();
        if (art == null) return Minecraft.getInstance().font.width(chipText(prompt)) + CHIP_PAD * 2;
        if (prompt.isMouseIcon()) return art.mouse(prompt.mouseIcon() - 1).w();
        int width = art.key(WynnKeyArt.CAP_LEFT).w() + art.key(WynnKeyArt.CAP_RIGHT).w();
        for (int i = 0; i < prompt.text().length(); i++) {
            WynnKeyArt.Glyph glyph = art.key(prompt.text().charAt(i));
            if (glyph != null) width += glyph.w();
        }
        return width;
    }

    /** @param alpha 0-255, applied to the whole prompt */
    public static void draw(GuiGraphics g, int x, int y, KeyPrompt prompt, boolean held, int alpha) {
        WynnKeyArt art = WynnKeyArt.current();
        if (art == null) {
            drawChip(g, x, y, chipText(prompt), held, alpha);
            return;
        }

        int tint = argb(alpha, held ? HELD_TINT : 0xFFFFFF);
        if (prompt.isMouseIcon()) {
            blit(g, art.mouse(prompt.mouseIcon() - 1), x, y, tint);
            return;
        }
        x += blit(g, art.key(WynnKeyArt.CAP_LEFT), x, y, tint);
        for (int i = 0; i < prompt.text().length(); i++) {
            WynnKeyArt.Glyph glyph = art.key(prompt.text().charAt(i));
            if (glyph != null) x += blit(g, glyph, x, y, tint);
        }
        blit(g, art.key(WynnKeyArt.CAP_RIGHT), x, y, tint);
    }

    /** Draws a glyph 1:1, vertically centred in the prompt height; returns its width. */
    private static int blit(GuiGraphics g, WynnKeyArt.Glyph glyph, int x, int y, int tint) {
        int h = Math.min(glyph.h(), HEIGHT);
        g.blit(RenderPipelines.GUI_TEXTURED, glyph.texture(), x, y + (HEIGHT - h) / 2, glyph.u(), glyph.v(),
                glyph.w(), h, glyph.texW(), glyph.texH(), tint);
        return glyph.w();
    }

    private static String chipText(KeyPrompt prompt) {
        return prompt.isMouseIcon() ? MOUSE_TEXT[prompt.mouseIcon() - 1] : prompt.text();
    }

    /** A flat rounded key drawn from fills, for when Wynncraft's art is not loaded. */
    private static void drawChip(GuiGraphics g, int x, int y, String text, boolean held, int alpha) {
        Font font = Minecraft.getInstance().font;
        int x1 = x + font.width(text) + CHIP_PAD * 2;
        int y1 = y + HEIGHT;
        int outline = argb(alpha, CHIP_OUTLINE);
        rect(g, x + 1, y, x1 - 1, y1, outline);
        rect(g, x, y + 1, x1, y1 - 1, outline);
        rect(g, x + 1, y + 1, x1 - 1, y1 - 1, argb(alpha, held ? CHIP_LIP_HELD : CHIP_LIP));
        rect(g, x + 1, y + 1, x1 - 1, y1 - 3, argb(alpha, held ? CHIP_FACE_HELD : CHIP_FACE));
        g.drawString(font, text, x + CHIP_PAD, y + 3, argb(alpha, held ? CHIP_TEXT_HELD : CHIP_TEXT), false);
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
