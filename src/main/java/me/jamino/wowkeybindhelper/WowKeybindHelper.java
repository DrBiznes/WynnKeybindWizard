package me.jamino.wowkeybindhelper;

import me.jamino.wowkeybindhelper.config.BarConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WowKeybindHelper implements ClientModInitializer {
    public static final String MOD_ID = "wowkeybindhelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        BarConfig.refresh();

        // Drawing happens in AbstractContainerScreenMixin; this only wires up the bar's mouse input.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            BarConfig.refresh();
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !KeybindBar.click(event.x(), event.y(), event.button()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    !KeybindBar.scroll(mouseX, mouseY, vertical));
        });
    }
}
