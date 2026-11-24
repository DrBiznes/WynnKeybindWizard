package me.jamino.wynnkeybindwizard.render;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.HashMap;
import java.util.Map;
import me.jamino.wynnkeybindwizard.compat.WynnExtrasKeys;
import me.jamino.wynnkeybindwizard.compat.WynnInfo;
import me.jamino.wynnkeybindwizard.config.BarConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Turns config entries into drawable "key + label" pairs.
 *
 * <p>The bound key is read from the live {@link KeyMapping} on every call, so rebinding in
 * Controls or a Class Keybind Profiles switch shows up on the next frame with nothing to invalidate.
 */
public final class Prompts {
    public static final int LABEL_GAP = 3;
    public static final int LABEL_COLOR = 0xE6E6E6;

    /** A resolved prompt. {@code prompt} is null for a label with no key (an unbound button). */
    public record Shown(KeyPrompt prompt, InputConstants.Key key, boolean held, String label, int width) {
        public Shown withHeld(boolean held) {
            return held == this.held ? this : new Shown(prompt, key, held, label, width);
        }
    }

    private static final Map<InputConstants.Key, KeyPrompt> CACHE = new HashMap<>();

    private Prompts() {}

    /** Null when the entry should be hidden: its keybind does not exist or is unbound, or it is an unused custom command. */
    public static Shown resolve(BarConfig.Entry entry, Font font) {
        String label = label(entry);
        if (label == null) return null;
        InputConstants.Key key = boundKey(entry);
        return key == null ? null : of(key, label, font);
    }

    /** The text shown beside the key, with placeholders filled in; null for a custom command slot the pack left empty. */
    public static String label(BarConfig.Entry entry) {
        int slot = entry.commandSlot();
        if (slot == 0) return WynnInfo.label(entry.label);
        String text = BarConfig.get().customCommand(slot);
        return text.isEmpty() ? null : text;
    }

    public static Shown of(InputConstants.Key key, String label, Font font) {
        int labelWidth = label.isEmpty() ? 0 : font.width(label);
        if (key == null) return new Shown(null, null, false, label, labelWidth);
        KeyPrompt prompt = CACHE.computeIfAbsent(key, KeyPrompt::of);
        boolean held = BarConfig.get().showHeldKeys && isDown(key);
        int width = KeyPromptRenderer.width(prompt) + (labelWidth == 0 ? 0 : LABEL_GAP + labelWidth);
        return new Shown(prompt, key, held, label, width);
    }

    public static void draw(GuiGraphics g, Font font, Shown shown, int x, int y, int labelColor, int alpha) {
        int labelX = x;
        if (shown.prompt != null) {
            KeyPromptRenderer.draw(g, x, y, shown.prompt, shown.held, alpha);
            labelX += KeyPromptRenderer.width(shown.prompt) + LABEL_GAP;
        }
        if (!shown.label.isEmpty()) KeyPromptRenderer.text(g, font, shown.label, labelX, y + 2, labelColor, alpha);
    }

    /** The key currently bound for a keybind id (or the WynnExtras setting or physical key it names), or null. */
    public static InputConstants.Key boundKey(BarConfig.Entry entry) {
        for (String id : entry.keys) {
            KeyMapping mapping = KeyMapping.get(id);
            try {
                if (mapping != null) {
                    if (!mapping.isUnbound()) return InputConstants.getKey(mapping.saveString());
                } else if (id.startsWith(WynnExtrasKeys.PREFIX)) {
                    int code = WynnExtrasKeys.keyCode(id.substring(WynnExtrasKeys.PREFIX.length()));
                    if (code != GLFW.GLFW_KEY_UNKNOWN) return InputConstants.Type.KEYSYM.getOrCreate(code);
                } else if (id.startsWith("key.keyboard.") || id.startsWith("key.mouse.")) {
                    return InputConstants.getKey(id);
                }
            } catch (IllegalArgumentException ignored) {
                // Not a key name the game recognises; try the next fallback.
            }
        }
        return null;
    }

    public static InputConstants.Key boundKey(KeyMapping mapping) {
        return mapping.isUnbound() ? null : InputConstants.getKey(mapping.saveString());
    }

    /** Whether the physical key or mouse button is down right now. */
    public static boolean isDown(InputConstants.Key key) {
        Window window = Minecraft.getInstance().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
