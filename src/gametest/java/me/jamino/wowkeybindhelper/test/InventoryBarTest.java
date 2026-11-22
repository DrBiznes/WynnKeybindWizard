package me.jamino.wowkeybindhelper.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import me.jamino.wowkeybindhelper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Opens the real inventory with a config that mirrors the modpack's bar and screenshots it.
 *
 * <p>The pack's mods (Wynntils etc.) are not in the dev client, so their binds are stood in for by
 * the physical keys they are bound to in the pack. The vanilla binds (Ultimate, Third Person,
 * Sprint, Sneak) go through real KeyMappings, and Sprint is rebound mid-test to prove the bar
 * follows a rebind without reopening anything.
 */
public class InventoryBarTest implements FabricClientGameTest {
    private static final String CONFIG = """
            {
              "cycleSeconds": 0,
              "pinned": [
                {"label": "$spell1", "keys": ["key.mouse.5"]},
                {"label": "$spell2", "keys": ["key.keyboard.r"]},
                {"label": "$spell3", "keys": ["key.keyboard.q"]},
                {"label": "$spell4", "keys": ["key.mouse.4"]},
                {"label": "Ultimate", "keys": ["key.swapOffhand"]}
              ],
              "pages": [
                {"entries": [
                  {"label": "Party Finder", "keys": ["key.keyboard.p"]},
                  {"label": "Social", "keys": ["key.keyboard.o"]},
                  {"label": "Quest Book", "keys": ["key.keyboard.j"]},
                  {"label": "Item Guide", "keys": ["key.keyboard.g"]},
                  {"label": "Use Menu", "keys": ["key.keyboard.u"]},
                  {"label": "Lootpools", "keys": ["key.keyboard.n"]},
                  {"label": "Achievements", "keys": ["key.keyboard.grave.accent"]}
                ]},
                {"entries": [
                  {"label": "Map", "keys": ["key.keyboard.m"]},
                  {"label": "Guild Map", "keys": ["key.keyboard.k"]},
                  {"label": "Mount", "keys": ["key.keyboard.h"]},
                  {"label": "Third Person", "keys": ["not.installed.mod", "key.togglePerspective"]},
                  {"label": "Zoom", "keys": ["key.keyboard.c"]},
                  {"label": "Shaders", "keys": ["key.keyboard.equal"]},
                  {"label": "Sprint", "keys": ["key.sprint"]},
                  {"label": "Sneak", "keys": ["key.sneak"]},
                  {"label": "Hidden", "keys": ["not.installed.mod"]}
                ]}
              ],
              "itemHover": [
                {"label": "Lock Slot", "keys": ["key.mouse.middle"]},
                {"label": "Favorite", "keys": ["key.keyboard.f"]},
                {"label": "Share", "keys": ["key.keyboard.f3"]},
                {"label": "Screenshot", "keys": ["key.keyboard.f4"]},
                {"label": "ID Ranges", "keys": ["key.keyboard.left.shift"]},
                {"label": "Attack", "keys": ["key.attack"]}
              ]
            }
            """;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path config = FabricLoader.getInstance().getConfigDir().resolve("wowkeybindhelper.json");
        try {
            Files.writeString(config, CONFIG);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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

            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.getInput().setCursorPos(5, 5);
            context.waitTicks(10);
            context.takeScreenshot("1-page-one");

            // Left click on the bar flips to the next page.
            double[] spots = context.computeOnClient(mc -> {
                AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) mc.screen;
                double scale = mc.getWindow().getGuiScale();
                return new double[] {
                    (gui.wkh$leftPos() + gui.wkh$imageWidth() / 2.0) * scale,
                    (gui.wkh$topPos() + gui.wkh$imageHeight() + 16) * scale,
                    (gui.wkh$leftPos() + 16) * scale,
                    (gui.wkh$topPos() + 150) * scale
                };
            });
            context.getInput().setCursorPos(spots[0], spots[1]);
            context.waitTicks(2);
            context.getInput().pressMouse(0);
            context.getInput().setCursorPos(5, 5);
            context.waitTicks(10);
            context.takeScreenshot("2-page-two-after-click");

            // Rebind while the inventory is open, like Class Keybind Profiles does.
            context.runOnClient(mc -> bind("key.sprint", "key.keyboard.x"));
            context.waitTicks(5);
            context.takeScreenshot("3-sprint-rebound-to-x");

            // Hovering an item swaps the page for the item actions.
            context.getInput().setCursorPos(spots[2], spots[3]);
            context.waitTicks(10);
            context.takeScreenshot("4-item-hovered");
        } finally {
            try {
                Files.deleteIfExists(config);
            } catch (IOException ignored) {
                // Only the throwaway test run directory is affected.
            }
        }
    }

    private static void bind(String mapping, String key) {
        KeyMapping.get(mapping).setKey(InputConstants.getKey(key));
        KeyMapping.resetMapping();
    }
}
