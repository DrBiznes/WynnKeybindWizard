package me.jamino.wynnkeybindwizard.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.keybinds.WynntilsKeyMapping;
import com.wynntils.features.tooltips.ItemCompareFeature;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.properties.GearTypeItemProperty;
import com.wynntils.models.items.properties.IdentifiableItemProperty;
import com.wynntils.models.mount.type.MountChoice;
import com.wynntils.models.spells.type.SpellType;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.wynn.InventoryUtils;
import com.wynntils.utils.wynn.ItemUtils;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import me.jamino.wynnkeybindwizard.WynnKeybindWizard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Only loaded when Wynntils is present; nothing but {@link WynnInfo} may reference this class. */
final class WynntilsCompat {
    private WynntilsCompat() {}

    /** Name of the current class's nth spell (1-4), or null when no character is loaded. */
    static String spellName(int number) {
        if (!Models.Character.hasCharacter()) return null;
        ClassType classType = Models.Character.getClassType();
        if (classType == null || classType == ClassType.NONE) return null;
        SpellType spell = SpellType.forClass(classType, number);
        return spell == null ? null : spell.getName();
    }

    /** The class as the player sees it ("Knight" for a reskinned Warrior), or null. */
    static String className() {
        if (!Models.Character.hasCharacter()) return null;
        String name = Models.Character.getActualName();
        return name == null || name.isBlank() ? null : name;
    }

    static boolean hasMount() {
        return Models.Mount.findMountSlotNum(MountChoice.FIRST) != -1;
    }

    static WynnInfo.ItemTraits traits(ItemStack stack) {
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        if (item.isEmpty()) return WynnInfo.ItemTraits.PLAIN;
        boolean stats = item.get() instanceof IdentifiableItemProperty;
        boolean gear = false;
        boolean comparable = false;
        if (item.get() instanceof GearTypeItemProperty typed) {
            // The gear types Wynntils' compare feature can match against what is equipped.
            GearType type = typed.getGearType();
            gear = type != null && (type.isWeapon() || type.isArmor() || type.isAccessory());
            comparable = gear && canCompare(stack, type);
        }
        return new WynnInfo.ItemTraits(stats, gear, comparable);
    }

    /**
     * Whether holding Compare over this item would show anything, mirroring Wynntils'
     * ItemCompareFeature: armor is compared with worn armor of the same type, accessories with
     * worn accessories, a weapon only with the weapon in the main hand, and any of them with
     * items marked for comparing.
     */
    private static boolean canCompare(ItemStack stack, GearType type) {
        ItemCompareFeature feature = Managers.Feature.getFeatureInstance(ItemCompareFeature.class);
        if (feature == null || !feature.isEnabled()) return false;

        List<Pair<WynnItem, ItemStack>> marked = markedItems(feature);
        if (marked == null) return true; // can't tell; show it rather than hide a working compare
        boolean hoveredMarked = false;
        for (Pair<WynnItem, ItemStack> pair : marked) {
            if (ItemUtils.isItemEqual(pair.b(), stack)) hoveredMarked = true;
            else if (pair.a() instanceof GearItem gear && gear.getGearType() == type) return true;
        }
        // A marked item is only compared with the other marked items, not with what is equipped.
        if (hoveredMarked) return false;

        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (type.isWeapon()) {
            ItemStack held = player.getMainHandItem();
            return !held.isEmpty() && held != stack
                    && Models.Item.getWynnItem(held).orElse(null) instanceof GearItem gear && gear.getGearType() == type;
        }
        if (type.isArmor()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack worn = player.getItemBySlot(slot);
                if (worn != stack && isGearOf(worn, type)) return true;
            }
            return false;
        }
        for (ItemStack worn : InventoryUtils.getAccessories(player)) {
            if (worn != stack && isGearOf(worn, type)) return true;
        }
        return false;
    }

    private static boolean isGearOf(ItemStack stack, GearType type) {
        return Models.Item.asWynnItemProperty(stack, GearTypeItemProperty.class)
                .map(item -> item.getGearType() == type)
                .orElse(false);
    }

    private static Field markedField;
    private static boolean markedUnreadable;

    /** Items marked with Mark to Compare. Wynntils keeps them private; null when unreadable. */
    @SuppressWarnings("unchecked")
    private static List<Pair<WynnItem, ItemStack>> markedItems(ItemCompareFeature feature) {
        if (markedUnreadable) return null;
        try {
            if (markedField == null) {
                markedField = ItemCompareFeature.class.getDeclaredField("selectedItems");
                markedField.setAccessible(true);
            }
            return (List<Pair<WynnItem, ItemStack>>) markedField.get(feature);
        } catch (ReflectiveOperationException | RuntimeException e) {
            markedUnreadable = true;
            WynnKeybindWizard.LOGGER.warn("Can't read Wynntils' marked items; Compare shows on all gear", e);
            return null;
        }
    }

    /**
     * Wynntils ignores a key that went down while a screen was open until it is released, so a
     * press replayed after closing the inventory would do nothing. Telling its keybinds on that
     * key the press is over lifts that.
     */
    static void forgetScreenPress(InputConstants.Key key) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping instanceof WynntilsKeyMapping wynntils && mapping.saveString().equals(key.getName())) {
                wynntils.onInput(GLFW.GLFW_RELEASE, false);
            }
        }
    }
}
