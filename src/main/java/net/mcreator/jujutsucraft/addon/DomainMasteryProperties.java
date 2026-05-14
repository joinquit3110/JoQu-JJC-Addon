package net.mcreator.jujutsucraft.addon;

import java.util.Locale;
import java.util.function.Supplier;

public enum DomainMasteryProperties {
    VICTIM_CE_DRAIN("jujutsucraft.domain.prop.ce_drain", "jujutsucraft.domain.prop.ce_drain.desc", 10, 1, () -> 1.2, " CE/0.5s"),
    BF_CHANCE_BOOST("jujutsucraft.domain.prop.bf_chance", "jujutsucraft.domain.prop.bf_chance.desc", 10, 1, () -> 0.5, "% BF"),
    RCT_HEAL_BOOST("jujutsucraft.domain.prop.rct_heal", "jujutsucraft.domain.prop.rct_heal.desc", 10, 1, () -> 0.25, " HP/s"),
    BLIND_EFFECT("jujutsucraft.domain.prop.blind", "jujutsucraft.domain.prop.blind.desc", 5, 1, () -> 1.0, " lvl"),
    SLOW_EFFECT("jujutsucraft.domain.prop.slow", "jujutsucraft.domain.prop.slow.desc", 5, 1, () -> 1.0, " lvl"),
    DURATION_EXTEND("jujutsucraft.domain.prop.duration", "jujutsucraft.domain.prop.duration.desc", 10, 1, () -> 5.0, "s"),
    RADIUS_BOOST("jujutsucraft.domain.prop.radius", "jujutsucraft.domain.prop.radius.desc", 10, 1, () -> 12.0, "%"),
    BARRIER_POWER("jujutsucraft.domain.prop.barrier_power", "jujutsucraft.domain.prop.barrier_power.desc", 10, 1, () -> 0.6, ""),
    BARRIER_REFINEMENT("jujutsucraft.domain.prop.barrier_ref", "jujutsucraft.domain.prop.barrier_ref.desc", 10, 1, () -> 4.0, "% vs Open");

    private final String nameKey;
    private final String descKey;
    private final int maxLevel;
    private final int pointCostPerLevel;
    private final Supplier<Double> valuePerLevelSupplier;
    private final String unit;

    private DomainMasteryProperties(String nameKey, String descKey, int maxLevel, int pointCostPerLevel, Supplier<Double> valuePerLevelSupplier, String unit) {
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.maxLevel = maxLevel;
        this.pointCostPerLevel = pointCostPerLevel;
        this.valuePerLevelSupplier = valuePerLevelSupplier;
        this.unit = unit;
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getDescKey() {
        return this.descKey;
    }

    public int getMaxLevel() {
        return this.maxLevel;
    }

    public int getPointCost() {
        return this.pointCostPerLevel;
    }

    public double getValuePerLevel() {
        return this.valuePerLevelSupplier.get();
    }

    public String getUnit() {
        return this.unit;
    }

    public String formatLevelValue(int level) {
        return switch (this) {
            case DURATION_EXTEND -> String.format(Locale.ROOT, "%+ds", level * 5);
            case BARRIER_POWER -> String.format(Locale.ROOT, "%+.1f", (double) level * 0.6);
            case BARRIER_REFINEMENT -> String.format(Locale.ROOT, "%+.0f%% vs Open", (double) level * 4.0);
            default -> String.format(Locale.ROOT, "%+.1f%s", this.getValuePerLevel() * (double) level, this.getUnit());
        };
    }

    public String formatNegativeValue(int negativePoints) {
        int points = Math.max(0, negativePoints);
        return switch (this) {
            case DURATION_EXTEND -> String.format(Locale.ROOT, "-%ds", points * 5);
            case BARRIER_POWER -> String.format(Locale.ROOT, "-%.1f", (double) points * 0.3);
            case RADIUS_BOOST -> String.format(Locale.ROOT, "-%.1f%%", (double) points * 8.0);
            case BARRIER_REFINEMENT -> String.format(Locale.ROOT, "-%.0f%% vs Open", (double) points * 4.0);
            default -> String.format(Locale.ROOT, "-%.1f%s", this.getValuePerLevel() * (double) points, this.getUnit());
        };
    }

    public int totalPointCost() {
        return this.maxLevel * this.pointCostPerLevel;
    }

    public boolean supportsNegativeModify() {
        return this == DURATION_EXTEND || this == RADIUS_BOOST || this == BARRIER_POWER || this == BARRIER_REFINEMENT;
    }

    public boolean isLocked(int masteryLevel) {
        return masteryLevel < 1;
    }

    public int unlockLevel() {
        return 1;
    }
}
