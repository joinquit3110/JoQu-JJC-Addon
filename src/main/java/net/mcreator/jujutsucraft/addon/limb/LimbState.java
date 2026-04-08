package net.mcreator.jujutsucraft.addon.limb;

/**
 * Represents the lifecycle state of a tracked limb.
 *
 * <p>The project documentation may refer to the healing phase as "regenerating," but the actual
 * code uses {@link net.mcreator.jujutsucraft.addon.limb.LimbState#REVERSING} to reflect reverse
 * cursed technique recovery.</p>
 */
public enum LimbState {
    /** The limb exists normally and has no missing-limb penalties. */
    INTACT,
    /** The limb has been cut off and is fully absent. */
    SEVERED,
    /** The limb is being restored through reverse cursed technique. */
    REVERSING;


    /**
     * Safely resolves a limb state from a serialized ordinal.
     *
     * @param ordinal ordinal value read from NBT or network payloads
     * @return the matching state, or {@link net.mcreator.jujutsucraft.addon.limb.LimbState#INTACT}
     *         when the ordinal is invalid
     */
    public static LimbState fromOrdinal(int ordinal) {
        LimbState[] values = LimbState.values();
        // Invalid payloads fall back to a safe default so corrupted data does not crash loading.
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return INTACT;
    }
}
