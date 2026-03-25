package net.mcreator.jujutsucraft.addon.limb;

public enum LimbState {
    INTACT,
    SEVERED,
    REVERSING,
    ;

    public static LimbState fromOrdinal(int ordinal) {
        LimbState[] values = LimbState.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return INTACT;
    }
}
