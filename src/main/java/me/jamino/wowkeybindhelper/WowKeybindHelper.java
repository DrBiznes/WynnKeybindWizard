package me.jamino.wowkeybindhelper;

import com.mojang.blaze3d.platform.InputConstants;
import me.jamino.wowkeybindhelper.config.BarConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WowKeybindHelper implements ClientModInitializer {
    public static final String MOD_ID = "wowkeybindhelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openKey;

    /** The rebindable "Show All Keybinds" key. */
    public static KeyMapping openKey() {
        return openKey;
    }

    @Override
    public void onInitializeClient() {
        BarConfig.refresh();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.wowkeybindhelper.all_keybinds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

        // In the world there is no screen to draw over, so the popup gets a screen of its own.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null && BarConfig.get().enabled) {
                    KeybindPanel.close();
                    client.setScreen(new AllKeybindsScreen());
                }
            }
        });

        // On container screens the bars are drawn by AbstractContainerScreenMixin and the popup is
        // a modal on top; this wires up their input. While the popup is open it swallows everything.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            BarConfig.refresh();

            ScreenEvents.remove(screen).register(s -> KeybindPanel.close());
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (KeybindPanel.isOpen()) {
                    if (!KeybindPanel.contains(event.x(), event.y())) KeybindPanel.close();
                    return false;
                }
                return !KeybindBar.click(event.x(), event.y());
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (!KeybindPanel.isOpen()) return true;
                KeybindPanel.scrollBy(vertical);
                return false;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register(WowKeybindHelper::allowKey);
        });
    }

    private static boolean allowKey(Screen screen, KeyEvent event) {
        if (KeybindPanel.isOpen()) {
            boolean closes = event.key() == GLFW.GLFW_KEY_ESCAPE
                    || openKey.matches(event)
                    || Minecraft.getInstance().options.keyInventory.matches(event);
            if (closes) KeybindPanel.close();
            return false;
        }
        // Not while typing in a search box (Wynntils adds them to bank screens).
        if (openKey.matches(event) && KeybindBar.appliesTo(screen) && !(screen.getFocused() instanceof EditBox)) {
            KeybindPanel.toggle();
            return false;
        }
        return true;
    }
}
