package me.jamino.wynnkeybindwizard.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.jamino.wynnkeybindwizard.KeybindPanel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the All Keybinds popup after the whole screen, including whatever other mods add on top
 * of it (FancyMenu layouts, Wynntils' inventory widgets, toasts), right before the frame's GUI is
 * submitted. Drawing it inside the container screen left those additions covering it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderDeferredSubtitles()V"))
    private void wkh$drawKeybindPanel(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci,
                                      @Local GuiGraphics graphics) {
        Screen screen = Minecraft.getInstance().screen;
        if (KeybindPanel.isOpen() && screen instanceof AbstractContainerScreen<?>) {
            KeybindPanel.draw(graphics, Minecraft.getInstance().font, screen.width, screen.height, true);
        }
    }
}
