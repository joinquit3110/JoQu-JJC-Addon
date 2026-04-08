package net.mcreator.jujutsucraft.addon;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Enumeration of the eight addon domain mastery properties. Each entry defines localization keys, per-level values, point costs, display formatting, and whether the property supports negative modification.
 */
public enum DomainMasteryProperties {
    /** Domain property that drains cursed energy from affected victims over time. */
    VICTIM_CE_DRAIN("jujutsucraft.domain.prop.ce_drain", "jujutsucraft.domain.prop.ce_drain.desc", 10, 1, () -> 2.0, " CE/0.5s"),
    /** Domain property that increases the owner's Black Flash proc chance. */
    BF_CHANCE_BOOST("jujutsucraft.domain.prop.bf_chance", "jujutsucraft.domain.prop.bf_chance.desc", 10, 1, () -> 0.5, "% BF"),
    /** Domain property that increases reverse cursed technique healing while the domain is active. */
    RCT_HEAL_BOOST("jujutsucraft.domain.prop.rct_heal", "jujutsucraft.domain.prop.rct_heal.desc", 10, 1, () -> 0.4, " HP/s"),
    /** Domain property that applies or strengthens blindness inside the domain. */
    BLIND_EFFECT("jujutsucraft.domain.prop.blind", "jujutsucraft.domain.prop.blind.desc", 10, 1, () -> 1.0, " lvl"),
    /** Domain property that applies or strengthens slowing effects inside the domain. */
    SLOW_EFFECT("jujutsucraft.domain.prop.slow", "jujutsucraft.domain.prop.slow.desc", 10, 1, () -> 1.0, " lvl"),
    /** Domain property that extends how long the domain can remain active. */
    DURATION_EXTEND("jujutsucraft.domain.prop.duration", "jujutsucraft.domain.prop.duration.desc", 10, 1, () -> 10.0, "s"),
    /** Domain property that increases the effective range of the domain. */
    RADIUS_BOOST("jujutsucraft.domain.prop.radius", "jujutsucraft.domain.prop.radius.desc", 10, 1, () -> 25.0, "%"),
    /** Domain property that improves clash performance against competing domains. */
    CLASH_POWER("jujutsucraft.domain.prop.clash_power", "jujutsucraft.domain.prop.clash_power.desc", 10, 1, () -> 1.0, "");

    // Translation key for the property name shown in UI and command output.
    private final String nameKey;
    // Translation key for the tooltip description shown in the mastery screen.
    private final String descKey;
    // Maximum level this property can reach through point investment.
    private final int maxLevel;
    // Property point cost required for each level invested in this property.
    private final int pointCostPerLevel;
    // Lazy supplier that returns the numeric value granted by each level.
    private final Supplier<Double> valuePerLevelSupplier;
    // Display suffix appended when formatting this property's numeric value.
    private final String unit;

    /**
     * Creates a new domain mastery properties instance and initializes its addon state.
     * @param nameKey name key used by this method.
     * @param descKey desc key used by this method.
     * @param maxLevel level value used by this operation.
     * @param pointCostPerLevel level value used by this operation.
     * @param valuePerLevelSupplier value per level supplier used by this method.
     * @param unit unit used by this method.
     */
    private DomainMasteryProperties(String nameKey, String descKey, int maxLevel, int pointCostPerLevel, Supplier<Double> valuePerLevelSupplier, String unit) {
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.maxLevel = maxLevel;
        this.pointCostPerLevel = pointCostPerLevel;
        this.valuePerLevelSupplier = valuePerLevelSupplier;
        this.unit = unit;
    }

    /**
     * Returns name key for the current addon state.
     * @return the resolved name key.
     */
    public String getNameKey() {
        return this.nameKey;
    }

    /**
     * Returns desc key for the current addon state.
     * @return the resolved desc key.
     */
    public String getDescKey() {
        return this.descKey;
    }

    /**
     * Returns max level for the current addon state.
     * @return the resolved max level.
     */
    public int getMaxLevel() {
        return this.maxLevel;
    }

    /**
     * Returns point cost for the current addon state.
     * @return the resolved point cost.
     */
    public int getPointCost() {
        return this.pointCostPerLevel;
    }

    /**
     * Returns value per level for the current addon state.
     * @return the resolved value per level.
     */
    public double getValuePerLevel() {
        return this.valuePerLevelSupplier.get();
    }

    /**
     * Returns unit for the current addon state.
     * @return the resolved unit.
     */
    public String getUnit() {
        return this.unit;
    }

    /**
     * Formats level value for addon display output.
     * @param level level value used by this operation.
     * @return the resulting format level value value.
     */
    public String formatLevelValue(int level) {
        return switch (this) {
            case DURATION_EXTEND -> String.format(Locale.ROOT, "%+ds", level * 10);
            case CLASH_POWER -> String.format(Locale.ROOT, "%+.1f", (double)level * 1.0);
            default -> String.format(Locale.ROOT, "%+.1f%s", this.getValuePerLevel() * (double)level, this.getUnit());
        };
    }

    /**
     * Formats negative value for addon display output.
     * @param negativePoints negative points used by this method.
     * @return the resulting format negative value value.
     */
    public String formatNegativeValue(int negativePoints) {
        int points = Math.max(0, negativePoints);
        return switch (this) {
            case DURATION_EXTEND -> String.format(Locale.ROOT, "-%ds", points * 10);
            case CLASH_POWER -> String.format(Locale.ROOT, "-%.1f", (double)points * 0.5);
            case RADIUS_BOOST -> String.format(Locale.ROOT, "-%.1f%%", (double)points * 10.0);
            default -> String.format(Locale.ROOT, "-%.1f%s", this.getValuePerLevel() * (double)points, this.getUnit());
        };
    }

    /**
     * Performs total point cost for this addon component.
     * @return the resulting total point cost value.
     */
    public int totalPointCost() {
        return this.maxLevel * this.pointCostPerLevel;
    }

    /**
     * Checks whether supports negative modify is true for the current addon state.
     * @return true when supports negative modify succeeds; otherwise false.
     */
    public boolean supportsNegativeModify() {
        return this == DURATION_EXTEND || this == RADIUS_BOOST || this == CLASH_POWER;
    }

    /**
     * Checks whether is locked is true for the current addon state.
     * @param masteryLevel level value used by this operation.
     * @return true when is locked succeeds; otherwise false.
     */
    public boolean isLocked(int masteryLevel) {
        return masteryLevel < 1;
    }

    /**
     * Performs unlock level for this addon component.
     * @return the resulting unlock level value.
     */
    public int unlockLevel() {
        return 1;
    }
}

