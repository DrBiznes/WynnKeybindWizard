package me.jamino.wowkeybindhelper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The All Keybinds popup when opened with its hotkey from the world, where there is no screen to sit on top of. */
public final class AllKeybindsScreen extends Screen {
    public AllKeybindsScreen() {
        super(Component.translatable("wowkeybindhelper.all_keybinds"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        KeybindPanel.draw(graphics, font, width, height, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (WowKeybindHelper.openKey().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!KeybindPanel.contains(event.x(), event.y())) onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        KeybindPanel.scrollBy(vertical);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
