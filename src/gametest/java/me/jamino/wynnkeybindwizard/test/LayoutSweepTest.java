package me.jamino.wynnkeybindwizard.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.jamino.wynnkeybindwizard.KeybindBar;
import me.jamino.wynnkeybindwizard.KeybindPanel;
import me.jamino.wynnkeybindwizard.config.BarConfig;
import me.jamino.wynnkeybindwizard.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the inventory at every GUI scale of several window sizes, in each bar layout, and
 * screenshots it. Each bar's drawn rectangle is checked against the screen and the inventory;
 * what it finds goes to {@code layout-sweep.txt} in the run directory and the log.
 *
 * <p>An audit, not a pass/fail test: it never fails, so every screenshot gets taken. Slow, so it
 * only runs with {@code -Dwynnkeybindwizard.layoutSweep=true}.
 */
public class LayoutSweepTest implements FabricClientGameTest {
    private static final int[][] WINDOWS = {
            {854, 480}, {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}};
    private static final String[] MODES = {"top", "item", "bottom", "under", "popup"};

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("wynnkeybindwizard.layoutSweep")) return;
        InventoryBarTest.writeConfig("\"spellBar\": \"TOP\"");
        List<String> report = new ArrayList<>();

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("give @a diamond_sword");
            context.runOnClient(mc -> {
                InventoryBarTest.bind("key.sprint", "key.keyboard.left.shift");
                InventoryBarTest.bind("key.sneak", "key.keyboard.left.control");
            });

            for (int[] window : WINDOWS) {
                context.setScreen(() -> null);
                context.runOnClient(mc -> mc.getWindow().setWindowed(window[0], window[1]));
                context.waitTicks(10);
                int[] real = context.computeOnClient(mc -> new int[] {mc.getWindow().getWidth(), mc.getWindow().getHeight()});
                int maxScale = context.computeOnClient(mc -> mc.getWindow().calculateScale(0, mc.isEnforceUnicode()));
                String size = real[0] + "x" + real[1];
                if (real[0] != window[0] || real[1] != window[1]) {
                    report.add(String.format("%dx%d asked, window came out %s (monitor too small?)", window[0], window[1], size));
                }

                for (int guiScale = 1; guiScale <= maxScale; guiScale++) {
                    int s = guiScale;
                    context.setScreen(() -> null);
                    context.runOnClient(mc -> {
                        mc.options.guiScale().set(s);
                        mc.resizeDisplay();
                    });
                    context.waitTicks(3);

                    for (String mode : MODES) {
                        String name = size + "-gui" + guiScale + "-" + mode;
                        context.runOnClient(mc -> {
                            BarConfig cfg = BarConfig.get();
                            // The shared layout leaves item actions off the bar; put them on so hovering swaps the bar.
                            cfg.keybinds.forEach(e -> { if (e.isItemAction()) e.onBar = true; });
                            cfg.spellBar = mode.equals("bottom") ? BarConfig.SpellBar.BOTTOM : BarConfig.SpellBar.TOP;
                            cfg.barPosition = mode.equals("under")
                                    ? BarConfig.BarPosition.UNDER_INVENTORY : BarConfig.BarPosition.SCREEN_BOTTOM;
                        });
                        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                        context.getInput().setCursorPos(2, 2);
                        context.waitTicks(5);
                        if (mode.equals("item")) {
                            double[] slot = context.computeOnClient(mc -> {
                                AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) mc.screen;
                                double scale = mc.getWindow().getGuiScale();
                                return new double[] {(gui.wkh$leftPos() + 16) * scale, (gui.wkh$topPos() + 150) * scale};
                            });
                            context.getInput().setCursorPos(slot[0], slot[1]);
                        } else if (mode.equals("popup")) {
                            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
                        }
                        context.waitTicks(8);
                        context.takeScreenshot("sweep-" + name);
                        if (!mode.equals("popup")) {
                            for (String issue : context.computeOnClient(mc -> check(mc, mode))) report.add(name + ": " + issue);
                        } else if (!context.computeOnClient(mc -> KeybindPanel.isOpen())) {
                            report.add(name + ": popup did not open");
                        }
                        context.setScreen(() -> null);
                    }
                }
            }
        } finally {
            try {
                Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve("wynnkeybindwizard.json"));
            } catch (IOException ignored) {
                // Only the throwaway test run directory is affected.
            }
        }

        if (report.isEmpty()) report.add("no layout issues found");
        Path out = FabricLoader.getInstance().getGameDir().resolve("layout-sweep.txt");
        try {
            Files.write(out, report);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[layout sweep] " + report.size() + " line(s), written to " + out);
        report.forEach(line -> System.out.println("[layout sweep] " + line));
    }

    /** Checks the bars drawn last frame, in GUI coordinates. */
    private static List<String> check(Minecraft mc, String mode) {
        List<String> issues = new ArrayList<>();
        AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) mc.screen;
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();
        int guiScale = (int) mc.getWindow().getGuiScale();
        float[] inv = {gui.wkh$leftPos(), gui.wkh$topPos(),
                gui.wkh$leftPos() + gui.wkh$imageWidth(), gui.wkh$topPos() + gui.wkh$imageHeight()};
        float cfgScale = BarConfig.get().scale;

        List<float[]> bars = KeybindBar.drawnBars();
        int expected = mode.equals("bottom") ? 1 : 2;
        if (bars.size() < expected) issues.add("only " + bars.size() + " of " + expected + " bars drawn (no room)");

        for (int i = 0; i < bars.size(); i++) {
            float[] b = bars.get(i);
            String which = "bar " + (i + 1) + String.format(Locale.ROOT, " [%.0f,%.0f..%.0f,%.0f @%.2f]", b[0], b[1], b[2], b[3], b[4]);
            if (b[0] < 0 || b[2] > w) issues.add(which + " runs off the screen sideways (screen width " + (int) w + ")");
            if (b[1] < 0 || b[3] > h) issues.add(which + " runs off the screen vertically (screen height " + (int) h + ")");
            if (intersects(b, inv)) issues.add(which + " covers the inventory");
            if (b[4] < cfgScale - 0.001f) {
                issues.add(which + " shrunk to " + String.format(Locale.ROOT, "%.2f", b[4])
                        + (b[4] <= 0.5001f ? " (hit MIN_SCALE)" : ""));
            }
            if (b[4] * guiScale < 1f) issues.add(which + " text drawn under 1 screen pixel per font pixel (unreadable)");
            for (int j = i + 1; j < bars.size(); j++) {
                if (intersects(b, bars.get(j))) issues.add(which + " overlaps bar " + (j + 1));
            }
        }
        return issues;
    }

    private static boolean intersects(float[] a, float[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }
}
