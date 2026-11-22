package me.jamino.wowkeybindhelper.compat;

import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.spells.type.SpellType;

/** Only loaded when Wynntils is present; nothing but {@link SpellNames} may reference this class. */
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
}
