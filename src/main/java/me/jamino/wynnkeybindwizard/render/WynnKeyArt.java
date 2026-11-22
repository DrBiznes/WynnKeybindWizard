package me.jamino.wynnkeybindwizard.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The key prompt art from Wynncraft's server resource pack, located through the pack's own font
 * definitions. Nothing is bundled with the mod: when the pack is not loaded (not on Wynncraft and
 * Wynntils is not preloading it) {@link #current()} is null and prompts fall back to plain text.
 *
 * <p>{@code minecraft:banner/keybind} holds a keycap slice for every printable ASCII character
 * plus left and right end caps, so any label can be assembled; {@code minecraft:keybind} holds the
 * mouse icons. Rebuilt on every resource reload, so a resource pack that retextures them applies.
 */
public final class WynnKeyArt {
    private static final Identifier BANNER_FONT = Identifier.withDefaultNamespace("font/banner/keybind.json");
    private static final Identifier ICON_FONT = Identifier.withDefaultNamespace("font/keybind.json");
    static final char CAP_LEFT = '';
    static final char CAP_RIGHT = '';
    private static final char[] MOUSE = {'', '', ''};

    /** Opaque part of one glyph cell: texture, top-left in texels, size. */
    public record Glyph(Identifier texture, int texW, int texH, int u, int v, int w, int h) {}

    private static WynnKeyArt current;

    private final Map<Character, Glyph> keys;
    private final Glyph[] mouse;

    private WynnKeyArt(Map<Character, Glyph> keys, Glyph[] mouse) {
        this.keys = keys;
        this.mouse = mouse;
    }

    /** Null when Wynncraft's key art is not available. */
    public static WynnKeyArt current() {
        return current;
    }

    public static void reload(ResourceManager manager) {
        try {
            Map<Character, Glyph> keys = glyphs(manager, BANNER_FONT);
            Map<Character, Glyph> icons = glyphs(manager, ICON_FONT);
            Glyph[] mouse = new Glyph[MOUSE.length];
            for (int i = 0; i < MOUSE.length; i++) mouse[i] = icons.get(MOUSE[i]);
            boolean complete = keys.containsKey(CAP_LEFT) && keys.containsKey(CAP_RIGHT) && keys.containsKey('A')
                    && mouse[0] != null && mouse[1] != null && mouse[2] != null;
            current = complete ? new WynnKeyArt(keys, mouse) : null;
        } catch (Exception e) {
            WynnKeybindWizard.LOGGER.warn("Could not read Wynncraft's key art, using text prompts", e);
            current = null;
        }
    }

    /** Glyph for a keycap slice, or null if the pack has none for {@code c}. */
    public Glyph key(char c) {
        return keys.get(c);
    }

    /** @param button 0-2 = left, right, middle */
    public Glyph mouse(int button) {
        return mouse[button];
    }

    /** Every bitmap glyph a font definition maps, cropped to its opaque pixels. Empty if absent. */
    private static Map<Character, Glyph> glyphs(ResourceManager manager, Identifier fontId) throws Exception {
        Map<Character, Glyph> out = new HashMap<>();
        Optional<Resource> font = manager.getResource(fontId);
        if (font.isEmpty()) return out;

        JsonObject json;
        try (Reader reader = font.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (JsonElement element : json.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!"bitmap".equals(provider.get("type").getAsString())) continue;

            Identifier file = Identifier.parse(provider.get("file").getAsString());
            Identifier texture = file.withPrefix("textures/");
            Optional<Resource> png = manager.getResource(texture);
            if (png.isEmpty()) continue;

            JsonArray rows = provider.getAsJsonArray("chars");
            try (InputStream in = png.get().open(); NativeImage image = NativeImage.read(in)) {
                int cols = rows.get(0).getAsString().length();
                int cellW = image.getWidth() / cols;
                int cellH = image.getHeight() / rows.size();
                for (int row = 0; row < rows.size(); row++) {
                    String chars = rows.get(row).getAsString();
                    for (int col = 0; col < chars.length(); col++) {
                        char c = chars.charAt(col);
                        if (c == 0 || out.containsKey(c)) continue;
                        Glyph glyph = crop(image, texture, col * cellW, row * cellH, cellW, cellH);
                        if (glyph != null) out.put(c, glyph);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Horizontally the glyph starts at the cell's left edge, as Minecraft lays bitmap glyphs out;
     * vertically it is cropped to the rows that have any pixels.
     */
    private static Glyph crop(NativeImage image, Identifier texture, int x0, int y0, int cellW, int cellH) {
        int right = -1, top = -1, bottom = -1;
        for (int y = 0; y < cellH; y++) {
            for (int x = 0; x < cellW; x++) {
                if ((image.getPixel(x0 + x, y0 + y) >>> 24) == 0) continue;
                right = Math.max(right, x);
                if (top < 0) top = y;
                bottom = y;
            }
        }
        if (right < 0) return null;
        return new Glyph(texture, image.getWidth(), image.getHeight(), x0, y0 + top, right + 1, bottom - top + 1);
    }
}
