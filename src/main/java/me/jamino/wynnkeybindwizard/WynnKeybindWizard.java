package me.jamino.wynnkeybindwizard;

import com.mojang.blaze3d.platform.InputConstants;
import me.jamino.wynnkeybindwizard.compat.WynnInfo;
import me.jamino.wynnkeybindwizard.config.BarConfig;
import me.jamino.wynnkeybindwizard.render.Prompts;
import me.jamino.wynnkeybindwizard.render.WynnKeyArt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WynnKeybindWizard implements ClientModInitializer {
    public static final String MOD_ID = "wynnkeybindwizard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openKey;
    /** A bar key pressed in the inventory, waiting for the next tick to be pressed in the world. */
    private static InputConstants.Key replayNextTick;
    /** The replayed key while it is held down for the game, until a full tick has seen it. */
    private static InputConstants.Key replayHeld;

    /** The rebindable "Show All Keybinds" key. */
    public static KeyMapping openKey() {
        return openKey;
    }

    @Override
    public void onInitializeClient() {
        BarConfig.refresh();

        // Picks up Wynncraft's key art whenever its server pack is applied or removed.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
                Identifier.fromNamespaceAndPath(MOD_ID, "key_art"),
                (ResourceManagerReloadListener) WynnKeyArt::reload);

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.wynnkeybindwizard.all_keybinds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

        ClientTickEvents.START_CLIENT_TICK.register(client -> replayTick());

        // The popup only opens from inventory screens; in the world the key does nothing.
        // On container screens the bars are drawn by AbstractContainerScreenMixin and the popup is
        // a modal on top; this wires up their input. While the popup is open it swallows everything.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            BarConfig.refresh();

            ScreenEvents.remove(screen).register(s -> KeybindPanel.close());
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (KeybindPanel.isOpen()) {
                    if (KeybindPanel.contains(event.x(), event.y())) KeybindPanel.click(event.x(), event.y());
                    else KeybindPanel.close();
                    return false;
                }
                if (KeybindBar.click(event.x(), event.y())) return false;
                // Left, right and middle click belong to the inventory; side buttons can be keybinds.
                return event.button() <= GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                        || !pressInWorld(s, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (!KeybindPanel.isOpen()) return true;
                KeybindPanel.scrollBy(vertical);
                return false;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register(WynnKeybindWizard::allowKey);
        });
    }

    private static boolean allowKey(Screen screen, KeyEvent event) {
        if (KeybindPanel.isOpen()) {
            boolean closes = event.key() == GLFW.GLFW_KEY_ESCAPE
                    || openKey.matches(event)
                    || Minecraft.getInstance().options.keyInventory.matches(event);
            // Escape backs out of the turn-off confirmation first, leaving the popup open.
            if (event.key() == GLFW.GLFW_KEY_ESCAPE && KeybindPanel.cancelConfirm()) return false;
            if (closes) KeybindPanel.close();
            return false;
        }
        // Not while typing in a search box (Wynntils adds them to bank screens).
        if (openKey.matches(event) && KeybindBar.appliesTo(screen) && !(screen.getFocused() instanceof EditBox)) {
            KeybindPanel.toggle();
            return false;
        }
        return !pressInWorld(screen, InputConstants.getKey(event));
    }

    /**
     * A key on the bottom bar does what it does in the world: the inventory closes and the press
     * goes to the game as if it had been made outside it. Keys the inventory itself uses (hotbar
     * swaps, drop, offhand) are left alone, as is typing in a search box.
     *
     * @return true when the key was taken
     */
    private static boolean pressInWorld(Screen screen, InputConstants.Key key) {
        if (!BarConfig.get().keysWorkInInventory || !KeybindBar.appliesTo(screen) || !KeybindBar.isBarKey(key)
                || screen.getFocused() instanceof EditBox || isInventoryKey(key)) return false;
        screen.onClose();
        // Keybinds that count presses get theirs now. Ones that check each tick whether their key
        // is held (all of Wynntils') are handled in replayTick: when a screen closes itself on a
        // key press, the game lets go of that key straight after, so holding it down here would
        // be undone before any tick saw it.
        KeyMapping.click(key);
        replayNextTick = key;
        return true;
    }

    /**
     * Holds the replayed key down from the start of one tick to the start of the next, so every
     * keybind checking it during that tick sees it pressed, even after a tap shorter than a tick.
     * If the key is still physically down then, the game's own release clears it later instead.
     */
    private static void replayTick() {
        if (replayHeld != null) {
            if (!Prompts.isDown(replayHeld)) KeyMapping.set(replayHeld, false);
            replayHeld = null;
        }
        if (replayNextTick != null) {
            WynnInfo.forgetScreenPress(replayNextTick);
            KeyMapping.set(replayNextTick, true);
            replayHeld = replayNextTick;
            replayNextTick = null;
        }
    }

    private static boolean isInventoryKey(InputConstants.Key key) {
        Options options = Minecraft.getInstance().options;
        if (key.equals(Prompts.boundKey(options.keyInventory)) || key.equals(Prompts.boundKey(options.keyDrop))
                || key.equals(Prompts.boundKey(options.keySwapOffhand))
                || key.equals(Prompts.boundKey(options.keyPickItem))) return true;
        for (KeyMapping hotbar : options.keyHotbarSlots) {
            if (key.equals(Prompts.boundKey(hotbar))) return true;
        }
        return false;
    }
}
