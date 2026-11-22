package me.jamino.wynnkeybindwizard.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of where the container GUI sits and which slot the mouse is over. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int wkh$leftPos();

    @Accessor("topPos")
    int wkh$topPos();

    @Accessor("imageWidth")
    int wkh$imageWidth();

    @Accessor("imageHeight")
    int wkh$imageHeight();

    @Accessor("hoveredSlot")
    Slot wkh$hoveredSlot();
}
