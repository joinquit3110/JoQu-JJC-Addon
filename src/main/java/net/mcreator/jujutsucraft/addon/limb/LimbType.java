package net.mcreator.jujutsucraft.addon.limb;

/**
 * Enumeration of body limbs that can be severed and regenerated.
 */
public enum LimbType {
    LEFT_ARM("left_arm", 0),
    RIGHT_ARM("right_arm", 1),
    LEFT_LEG("left_leg", 2),
    RIGHT_LEG("right_leg", 3),
    HEAD("head", 4);

    private final String serializedName;
    private final int index;

    LimbType(String serializedName, int index) {
        this.serializedName = serializedName;
        this.index = index;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public int getIndex() {
        return index;
    }

    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    public boolean isLeg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }

    public static LimbType fromIndex(int index) {
        for (LimbType type : values()) {
            if (type.index == index) return type;
        }
        return null;
    }

    public static LimbType fromName(String name) {
        for (LimbType type : values()) {
            if (type.serializedName.equals(name)) return type;
        }
        return null;
    }
}
