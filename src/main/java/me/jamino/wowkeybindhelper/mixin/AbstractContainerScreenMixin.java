package me.jamino.wowkeybindhelper.mixin;

import me.jamino.wowkeybindhelper.KeybindBar;
import me.jamino.wowkeybindhelper.KeybindPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the bars (and the All Keybinds popup, when open) right after the container's slots and
 * labels, so they sit under the dragged item and under tooltips. (Fabric's
 * ScreenEvents.afterRender fires after the deferred tooltip has been flushed, which put the bar
 * on top of it.)
 *
 * <p>The hook is renderContents rather than render because the inventory's recipe-book screen
 * overrides render without calling super, but every container screen goes through renderContents.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "renderContents", at = @At("TAIL"))
    private void wkh$drawKeybindBar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        KeybindBar.render(screen, graphics, mouseX, mouseY);
        if (KeybindPanel.isOpen()) {
            KeybindPanel.draw(graphics, Minecraft.getInstance().font, screen.width, screen.height, true);
        }
    }

    /** Item tooltips are drawn above everything, so they would cover the popup. */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void wkh$noTooltipUnderPopup(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (KeybindPanel.isOpen()) ci.cancel();
    }
}
