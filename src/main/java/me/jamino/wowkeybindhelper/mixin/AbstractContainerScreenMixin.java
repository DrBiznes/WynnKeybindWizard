package me.jamino.wowkeybindhelper.mixin;

import me.jamino.wowkeybindhelper.KeybindBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the bar right after the container's slots and labels, so it sits under the dragged item
 * and under tooltips. (Fabric's ScreenEvents.afterRender fires after the deferred tooltip has been
 * flushed, which put the bar on top of it.)
 *
 * <p>The hook is renderContents rather than render because the inventory's recipe-book screen
 * overrides render without calling super, but every container screen goes through renderContents.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "renderContents", at = @At("TAIL"))
    private void wkh$drawKeybindBar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        KeybindBar.render((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }
}
