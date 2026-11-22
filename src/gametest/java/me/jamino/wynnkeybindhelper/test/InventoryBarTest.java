package me.jamino.wynnkeybindhelper.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import me.jamino.wynnkeybindhelper.KeybindPanel;
import me.jamino.wynnkeybindhelper.config.BarConfig;
import me.jamino.wynnkeybindhelper.config.ClothConfigScreen;
import me.jamino.wynnkeybindhelper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the real inventory with a config that mirrors the modpack's layout and screenshots it.
 *
 * <p>The pack's mods (Wynntils etc.) are not in the dev client, so their binds are stood in for by
 * the physical keys they are bound to in the pack; the vanilla binds (Ultimate, Third Person,
 * Sprint, Sneak) go through real KeyMappings. Without Wynntils every "don't know" answer applies:
 * spells are "Spell N", the mount is assumed owned, and every item action is offered.
 */
public class InventoryBarTest implements FabricClientGameTest {
    private static final String KEYBINDS = """
              "keybinds": [
                {"label": "$spell1", "group": "Spells", "keys": ["key.mouse.5"]},
                {"label": "$spell2", "group": "Spells", "keys": ["key.keyboard.r"]},
                {"label": "$spell3", "group": "Spells", "keys": ["key.keyboard.q"]},
                {"label": "$spell4", "group": "Spells", "keys": ["key.mouse.4"]},
                {"label": "Ultimate", "group": "Spells", "keys": ["key.swapOffhand"]},
                {"label": "Quest Book", "group": "Menus", "keys": ["key.keyboard.j"], "onBar": true},
                {"label": "$command1", "group": "Commands", "keys": ["key.keyboard.p"], "onBar": true},
                {"label": "$command2", "group": "Commands", "keys": ["key.keyboard.u"], "onBar": true},
                {"label": "$command3", "group": "Commands", "keys": ["key.keyboard.i"], "onBar": true},
                {"label": "Mount", "group": "World", "keys": ["key.keyboard.h"], "onBar": true, "when": "has_mount"},
                {"label": "Social", "group": "Menus", "keys": ["key.keyboard.o"], "onBar": true},
                {"label": "Not Installed", "group": "Menus", "keys": ["not.installed.mod"], "onBar": true},
                {"label": "Item Guide", "group": "Menus", "keys": ["key.keyboard.g"], "onBar": true},
                {"label": "Lootpools", "group": "Menus", "keys": ["key.keyboard.n"], "onBar": true},
                {"label": "Achievements", "group": "Menus", "keys": ["key.keyboard.grave.accent"]},
                {"label": "Map", "group": "World", "keys": ["key.keyboard.m"]},
                {"label": "Guild Map", "group": "World", "keys": ["key.keyboard.k"]},
                {"label": "Third Person", "group": "World", "keys": ["not.installed.mod", "key.togglePerspective"]},
                {"label": "Zoom", "group": "World", "keys": ["key.keyboard.c"]},
                {"label": "Shaders", "group": "World", "keys": ["key.keyboard.equal"]},
                {"label": "Sprint", "group": "World", "keys": ["key.sprint"]},
                {"label": "Sneak", "group": "World", "keys": ["key.sneak"]},
                {"label": "Compare", "group": "Items", "keys": ["key.keyboard.keypad.enter"], "when": "item_gear"},
                {"label": "Stat Ranges", "group": "Items", "keys": ["key.keyboard.left.shift"], "when": "item_stats"},
                {"label": "Reroll Chance", "group": "Items", "keys": ["key.keyboard.left.control"], "when": "item_stats"},
                {"label": "Lock Slot", "group": "Items", "keys": ["key.mouse.middle"], "when": "item"},
                {"label": "Favorite", "group": "Items", "keys": ["key.keyboard.f"], "when": "item"},
                {"label": "Same Key As Favorite", "group": "Items", "keys": ["key.keyboard.f"], "when": "item"},
                {"label": "Screenshot", "group": "Items", "keys": ["key.keyboard.f4"], "when": "item"}
              ]
            """;

    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("wynnkeybindhelper.json");

    @Override
    public void runTest(ClientGameTestContext context) {
        writeConfig("\"spellBar\": \"TOP\"");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("give @a diamond_sword");

            // 1280x720 at GUI scale 2 is the same 640x360 GUI as 1080p at scale 3.
            context.runOnClient(mc -> {
                mc.getWindow().setWindowed(1280, 720);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                bind("key.sprint", "key.keyboard.left.shift");
                bind("key.sneak", "key.keyboard.left.control");
            });
            context.waitTicks(10);

            openInventory(context);
            context.takeScreenshot("1-bar-with-spells-on-top");

            // Rebind while the inventory is open, like Class Keybind Profiles does.
            context.runOnClient(mc -> bind("key.swapOffhand", "key.keyboard.x"));
            context.waitTicks(5);
            context.takeScreenshot("2-ultimate-rebound-to-x");
            context.runOnClient(mc -> bind("key.swapOffhand", "key.keyboard.f"));

            // Hovering an item swaps the bar for the item actions.
            double[] slot = context.computeOnClient(mc -> {
                AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) mc.screen;
                double scale = mc.getWindow().getGuiScale();
                return new double[] {(gui.wkh$leftPos() + 16) * scale, (gui.wkh$topPos() + 150) * scale};
            });
            context.getInput().setCursorPos(slot[0], slot[1]);
            context.waitTicks(10);
            context.takeScreenshot("3-item-hovered");

            // The hotkey opens the popup over the inventory; the item tooltip must not cover it.
            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
            context.waitTicks(5);
            if (!KeybindPanel.isOpen()) throw new AssertionError("hotkey did not open the popup");
            context.takeScreenshot("4-popup-over-inventory");

            // The switch above the spells turns the inventory spell bar off and back on.
            clickSpellToggle(context);
            if (context.computeOnClient(mc -> BarConfig.get().spellBar) != BarConfig.SpellBar.OFF) {
                throw new AssertionError("spell switch did not turn the spell bar off");
            }
            if (!KeybindPanel.isOpen()) throw new AssertionError("clicking the switch closed the popup");
            context.takeScreenshot("4b-popup-spells-switched-off");
            clickSpellToggle(context);
            if (context.computeOnClient(mc -> BarConfig.get().spellBar) != BarConfig.SpellBar.TOP) {
                throw new AssertionError("spell switch did not restore the spell bar to the top");
            }

            // Escape closes the popup, not the inventory.
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            boolean stillInInventory = context.computeOnClient(mc -> mc.screen instanceof InventoryScreen);
            if (KeybindPanel.isOpen() || !stillInInventory) {
                throw new AssertionError("Escape should close only the popup");
            }

            // Spells pinned to the bottom bar instead, dynamic mode off.
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            writeConfig("\"spellBar\": \"BOTTOM\", \"dynamic\": false");
            openInventory(context);
            context.getInput().setCursorPos(slot[0], slot[1]);
            context.waitTicks(10);
            context.takeScreenshot("5-spells-on-bottom-dynamic-off");

            // From the world, the hotkey does nothing: the popup is inventory-only.
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
            context.waitTicks(10);
            boolean anyScreen = context.computeOnClient(mc -> mc.screen != null);
            if (anyScreen || KeybindPanel.isOpen()) {
                throw new AssertionError("hotkey should not open anything outside the inventory");
            }
            context.takeScreenshot("6-hotkey-in-world-does-nothing");

            // The Cloth Config screen builds and opens, including the custom command and add pages.
            context.setScreen(() -> ClothConfigScreen.create(null));
            context.waitTicks(10);
            context.takeScreenshot("7-settings-screen");
            context.setScreen(() -> ClothConfigScreen.create(null, ClothConfigScreen.PAGE_COMMANDS));
            context.waitTicks(10);
            context.takeScreenshot("8-settings-custom-commands");
            context.setScreen(() -> ClothConfigScreen.create(null, ClothConfigScreen.PAGE_ADD));
            context.waitTicks(10);
            context.takeScreenshot("9-settings-add-keybind");

            // Search the keybind picker for Jump and screenshot the list it offers.
            click(context, 1017, 311);
            context.getInput().typeChars("jump");
            context.waitTicks(5);
            context.takeScreenshot("10-add-keybind-search");

            // Pick it, leave the label empty (so it uses the Controls name) and save.
            click(context, 1000, 343);
            click(context, 837, 687);
            context.waitTicks(5);
            boolean added = context.computeOnClient(mc -> BarConfig.get().keybinds.stream()
                    .anyMatch(e -> e.keys.equals(java.util.List.of("key.jump")) && e.label.equals("Jump")
                            && e.group.equals("Other")));
            if (!added) throw new AssertionError("Add Keybind did not add Jump to the config");
            context.setScreen(() -> ClothConfigScreen.create(null, ClothConfigScreen.PAGE_ADD));
            context.waitTicks(10);
            context.takeScreenshot("11-add-keybind-after-save");
        } finally {
            try {
                Files.deleteIfExists(CONFIG);
            } catch (IOException ignored) {
                // Only the throwaway test run directory is affected.
            }
        }
    }

    /** Left-click at a window pixel (the screenshots are window-sized). */
    private static void click(ClientGameTestContext context, double x, double y) {
        context.getInput().setCursorPos(x, y);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    private static void clickSpellToggle(ClientGameTestContext context) {
        double[] pos = context.computeOnClient(mc -> {
            double[] center = KeybindPanel.spellToggleCenter();
            if (center == null) throw new AssertionError("spell switch is not visible in the popup");
            double scale = mc.getWindow().getGuiScale();
            return new double[] {center[0] * scale, center[1] * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }

    private static void openInventory(ClientGameTestContext context) {
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.getInput().setCursorPos(5, 5);
        context.waitTicks(10);
    }

    private static void writeConfig(String settings) {
        try {
            // Slot 3 is bound but left unnamed, so it must stay hidden.
            Files.writeString(CONFIG, "{\n  " + settings + ",\n  \"customCommands\": [\"Party Finder\", \"Use Menu\"],\n"
                    + KEYBINDS + "}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void bind(String mapping, String key) {
        KeyMapping.get(mapping).setKey(InputConstants.getKey(key));
        KeyMapping.resetMapping();
    }
}
