package me.jamino.wynnkeybindhelper.compat;

import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.properties.GearTypeItemProperty;
import com.wynntils.models.items.properties.IdentifiableItemProperty;
import com.wynntils.models.mount.type.MountChoice;
import com.wynntils.models.spells.type.SpellType;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

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
        if (item.get() instanceof GearTypeItemProperty typed) {
            // The gear types Wynntils' compare feature can match against what is equipped.
            GearType type = typed.getGearType();
            gear = type != null && (type.isWeapon() || type.isArmor() || type.isAccessory());
        }
        return new WynnInfo.ItemTraits(stats, gear);
    }
}
