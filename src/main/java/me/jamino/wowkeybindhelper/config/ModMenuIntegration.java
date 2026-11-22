package me.jamino.wowkeybindhelper.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Only loaded by Mod Menu, so the mod itself never needs Mod Menu on the classpath. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Without Cloth Config there is no screen; Mod Menu then hides the Configure button
        // and the config file is edited by hand instead.
        if (!FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return parent -> null;
        }
        return ClothConfigScreen::create;
    }
}
