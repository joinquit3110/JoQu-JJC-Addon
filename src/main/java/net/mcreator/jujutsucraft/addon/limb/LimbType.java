package net.mcreator.jujutsucraft.addon.limb;

/**
 * Enumerates every limb tracked by the limb-loss capability system.
 *
 * <p>Each entry exposes both a stable serialized name for NBT and networking usage and a compact
 * index used by detached limb entities and other lightweight payloads.</p>
 */
public enum LimbType {
    /** Left arm slot used for offhand-related penalties and rendering. */
    LEFT_ARM("left_arm", 0),
    /** Right arm slot used for main-hand and attack penalties. */
    RIGHT_ARM("right_arm", 1),
    /** Left leg slot used for movement and jump penalties. */
    LEFT_LEG("left_leg", 2),
    /** Right leg slot used for movement and jump penalties. */
    RIGHT_LEG("right_leg", 3),
    /** Head slot used for fatal sever logic and near-death interactions. */
    HEAD("head", 4);

    // ===== STORED IDENTIFIERS =====

    /** Stable lowercase identifier used in NBT keys such as {@code left_arm_state}. */
    private final String serializedName;
    /** Compact numeric identifier used by packets and detached limb entities. */
    private final int index;

    /**
     * Creates one tracked limb definition.
     *
     * @param serializedName stable text key written to disk and sent across systems
     * @param index compact ordinal-like identifier used where a small integer is enough
     */
    private LimbType(String serializedName, int index) {
        this.serializedName = serializedName;
        this.index = index;
    }

    // ===== ACCESSORS =====

    /**
     * Returns the stable serialized identifier for this limb.
     *
     * @return lowercase storage/network name such as {@code left_arm}
     */
    public String getSerializedName() {
        return this.serializedName;
    }

    /**
     * Returns the compact numeric id for this limb.
     *
     * @return integer index used by lightweight serialization helpers
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Checks whether this limb belongs to the arm pair.
     *
     * @return {@code true} for the left or right arm, otherwise {@code false}
     */
    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    /**
     * Checks whether this limb belongs to the leg pair.
     *
     * @return {@code true} for the left or right leg, otherwise {@code false}
     */
    public boolean isLeg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }

    // ===== LOOKUP HELPERS =====

    /**
     * Resolves a limb from its compact index.
     *
     * @param index stored limb id, typically from entity data or packet payloads
     * @return matching limb type, or {@code null} when the id is unknown
     */
    public static LimbType fromIndex(int index) {
        for (LimbType type : LimbType.values()) {
            // The first matching index is the correct enum entry because ids are unique.
            if (type.index != index) continue;
            return type;
        }
        return null;
    }

    /**
     * Resolves a limb from its serialized name.
     *
     * @param name lowercase storage key such as {@code head}
     * @return matching limb type, or {@code null} when the name is unknown
     */
    public static LimbType fromName(String name) {
        for (LimbType type : LimbType.values()) {
            // Serialized names are used as authoritative cross-system keys.
            if (!type.serializedName.equals(name)) continue;
            return type;
        }
        return null;
    }
}
