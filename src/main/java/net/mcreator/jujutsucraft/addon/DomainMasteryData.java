package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent capability data model for addon domain mastery. It stores XP, form unlocks, property levels, negative modify state, runtime scaling helpers, client sync payloads, and NBT serialization.
 */
public class DomainMasteryData {
    // Form id representing an incomplete domain state.
    public static final int FORM_INCOMPLETE = 0;
    // Form id representing a standard closed-barrier domain.
    public static final int FORM_CLOSED = 1;
    // Form id representing an open-barrier domain.
    public static final int FORM_OPEN = 2;
    // Maximum level any single domain mastery property can reach.
    public static final int MAX_PROPERTY_LEVEL = 10;
    // Lowest negative modify value supported by the mastery system.
    public static final int MIN_NEGATIVE_LEVEL = -5;
    // Mastery level required before negative modify becomes available.
    public static final int NEGATIVE_UNLOCK_LEVEL = 5;
    // Legacy per-point debuff scalar retained for negative modify helper calculations.
    public static final double NEGATIVE_DEBUFF_PER_POINT = 0.1;
    // Total domain mastery experience stored for this player.
    private double domainXP = 0.0;
    // Current mastered level, clamped to the addon range of 0 through 5.
    private int domainMasteryLevel = 0;
    // Cached XP threshold for the next mastery level, used by the UI and sync payloads.
    private int domainXPToNextLevel = DomainMasteryData.getXPRequiredForLevel(1);
    // Selected domain form id currently chosen by the player.
    private int domainTypeSelected = 0;
    // Spendable property points earned from mastery progression and negative modify tradeoffs.
    private int domainPropertyPoints = 0;
    // Allocated level in victim cursed energy drain.
    private int propCeDrain = 0;
    // Allocated level in Black Flash chance bonus.
    private int propBfChance = 0;
    // Allocated level in reverse cursed technique healing bonus.
    private int propRctHeal = 0;
    // Allocated level in blind effect strength.
    private int propBlind = 0;
    // Allocated level in slow effect strength.
    private int propSlow = 0;
    // Allocated level in domain duration extension.
    private int propDuration = 0;
    // Allocated level in domain radius increase.
    private int propRadius = 0;
    // Allocated level in domain barrier power.
    private int propBarrierPower = 0;
    // Allocated level in barrier refinement (barrier resilience).
    private int propBarrierRef = 0;
    // Whether the player has the advancement needed to select open form.
    private boolean openBarrierAdvancementUnlocked = false;
    // Name of the single property currently carrying a negative modify penalty.
    private String negativeProperty = "";
    // Stored negative modify amount for the selected negative property, clamped between 0 and -5.
    private int negativeLevel = 0;

    // ===== CORE MASTERY STATE =====
    public double getDomainXP() {
        return this.domainXP;
    }

    /**
     * Updates domain xp for the current addon state.
     * @param xp xp used by this method.
     */
    public void setDomainXP(double xp) {
        this.domainXP = xp;
    }

    /**
     * Performs add domain xp for this addon component.
     * @param amount amount used by this method.
     */
    public void addDomainXP(double amount) {
        this.domainXP += amount;
        if (this.domainXP < 0.0) {
            this.domainXP = 0.0;
        }
        this.checkLevelUp();
    }

    /**
     * Returns domain mastery level for the current addon state.
     * @return the resolved domain mastery level.
     */
    public int getDomainMasteryLevel() {
        return this.domainMasteryLevel;
    }

    /**
     * Updates domain mastery level for the current addon state.
     * @param level level value used by this operation.
     */
    public void setDomainMasteryLevel(int level) {
        this.domainMasteryLevel = Math.max(0, Math.min(5, level));
        this.domainXPToNextLevel = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1);
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(this.domainTypeSelected, this.domainMasteryLevel);
    }

    /**
     * Returns xp progress for the current addon state.
     * @return the resolved xp progress.
     */
    public double getXPProgress() {
        int cur = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel);
        int nxt = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1);
        if (nxt <= cur) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (this.domainXP - (double)cur) / (double)(nxt - cur)));
    }

    /**
     * Returns xp to next level for the current addon state.
     * @return the resolved xp to next level.
     */
    public int getXPToNextLevel() {
        return this.domainXPToNextLevel;
    }

    // ===== XP AND LEVEL FLOW =====
    private void checkLevelUp() {
        while (this.domainMasteryLevel < 5 && this.domainXP >= (double)DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1)) {
            ++this.domainMasteryLevel;
            ++this.domainPropertyPoints;
        }
        this.domainXPToNextLevel = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1);
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(this.domainTypeSelected, this.domainMasteryLevel);
    }

    /**
     * Returns the total XP required to reach the requested mastery level.
     * @param level level value used by this operation.
     * @return the resolved xp required for level.
     */
    private static int getXPRequiredForLevel(int level) {
        // These thresholds define the five-step addon mastery progression curve described by the domain system design documents.
        return switch (level) {
            case 0 -> 0;
            case 1 -> 300;
            case 2 -> 700;
            case 3 -> 1300;
            case 4 -> 2200;
            case 5 -> 3500;
            default -> Integer.MAX_VALUE;
        };
    }

    // ===== FORM SELECTION =====
    public int getDomainTypeSelected() {
        return this.domainTypeSelected;
    }

    /**
     * Updates domain type selected for the current addon state.
     * @param type type used by this method.
     */
    public void setDomainTypeSelected(int type) {
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(type, this.domainMasteryLevel);
    }

    /**
     * Updates domain type selected for the current addon state.
     * @param type type used by this method.
     * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
     */
    public void setDomainTypeSelected(int type, boolean hasOpenBarrierAdvancement) {
        this.openBarrierAdvancementUnlocked = hasOpenBarrierAdvancement;
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(type, this.domainMasteryLevel, hasOpenBarrierAdvancement);
    }

    /**
     * Checks whether has open barrier advancement unlocked is true for the current addon state.
     * @return true when has open barrier advancement unlocked succeeds; otherwise false.
     */
    public boolean hasOpenBarrierAdvancementUnlocked() {
        return this.openBarrierAdvancementUnlocked;
    }

    /**
     * Updates open barrier advancement unlocked for the current addon state.
     * @param unlocked unlocked used by this method.
     */
    public void setOpenBarrierAdvancementUnlocked(boolean unlocked) {
        this.openBarrierAdvancementUnlocked = unlocked;
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(this.domainTypeSelected, this.domainMasteryLevel, unlocked);
    }

    /**
     * Checks whether is closed form unlocked is true for the current addon state.
     * @return true when is closed form unlocked succeeds; otherwise false.
     */
    public boolean isClosedFormUnlocked() {
        return DomainMasteryData.isClosedFormUnlocked(this.domainMasteryLevel);
    }

    /**
     * Checks whether is open form unlocked is true for the current addon state.
     * @return true when is open form unlocked succeeds; otherwise false.
     */
    public boolean isOpenFormUnlocked() {
        return DomainMasteryData.isOpenFormUnlocked(this.domainMasteryLevel, this.openBarrierAdvancementUnlocked);
    }

    /**
     * Returns domain form name for the current addon state.
     * @return the resolved domain form name.
     */
    public String getDomainFormName() {
        return switch (this.domainTypeSelected) {
            case 1 -> "Closed";
            case 2 -> "Open";
            default -> "Incomplete";
        };
    }

    /**
     * Returns domain form amplifier for the current addon state.
     * @return the resolved domain form amplifier.
     */
    public int getDomainFormAmplifier() {
        return switch (this.domainTypeSelected) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 0;
        };
    }

    /**
     * Checks whether is closed form unlocked is true for the current addon state.
     * @param masteryLevel level value used by this operation.
     * @return true when is closed form unlocked succeeds; otherwise false.
     */
    public static boolean isClosedFormUnlocked(int masteryLevel) {
        return masteryLevel >= 1;
    }

    /**
     * Checks whether is open form unlocked is true for the current addon state.
     * @param masteryLevel level value used by this operation.
     * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
     * @return true when is open form unlocked succeeds; otherwise false.
     */
    public static boolean isOpenFormUnlocked(int masteryLevel, boolean hasOpenBarrierAdvancement) {
        return masteryLevel >= 5 && hasOpenBarrierAdvancement;
    }

    /**
     * Clamps a form selection so players cannot keep forms that are not unlocked for their current mastery state.
     * @param type type used by this method.
     * @param masteryLevel level value used by this operation.
     * @return the resulting sanitize form selection value.
     */
    public static int sanitizeFormSelection(int type, int masteryLevel) {
        int clamped = Math.max(0, Math.min(2, type));
        if (clamped == 1 && !DomainMasteryData.isClosedFormUnlocked(masteryLevel)) {
            return 0;
        }
        if (clamped == 2 && !DomainMasteryData.isClosedFormUnlocked(masteryLevel)) {
            return 0;
        }
        return clamped;
    }

    /**
     * Clamps a form selection so players cannot keep forms that are not unlocked for their current mastery state.
     * @param type type used by this method.
     * @param masteryLevel level value used by this operation.
     * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
     * @return the resulting sanitize form selection value.
     */
    public static int sanitizeFormSelection(int type, int masteryLevel, boolean hasOpenBarrierAdvancement) {
        int sanitized = DomainMasteryData.sanitizeFormSelection(type, masteryLevel);
        if (sanitized == 2 && !DomainMasteryData.isOpenFormUnlocked(masteryLevel, hasOpenBarrierAdvancement)) {
            return DomainMasteryData.isClosedFormUnlocked(masteryLevel) ? 1 : 0;
        }
        return sanitized;
    }

    // ===== PROPERTY POINT ECONOMY =====
    public int getDomainPropertyPoints() {
        return this.domainPropertyPoints;
    }

    /**
     * Updates domain property points for the current addon state.
     * @param points points used by this method.
     */
    public void setDomainPropertyPoints(int points) {
        this.domainPropertyPoints = Math.max(0, points);
    }

    /**
     * Performs spend property points for this addon component.
     * @param cost cost used by this method.
     * @return true when spend property points succeeds; otherwise false.
     */
    public boolean spendPropertyPoints(int cost) {
        if (cost <= 0) {
            return true;
        }
        if (this.domainPropertyPoints >= cost) {
            this.domainPropertyPoints -= cost;
            return true;
        }
        return false;
    }

    // ===== NEGATIVE MODIFY STATE =====
    public String getNegativeProperty() {
        return this.negativeProperty;
    }

    /**
     * Returns negative level for the current addon state.
     * @return the resolved negative level.
     */
    public int getNegativeLevel() {
        return this.negativeLevel;
    }

    /**
     * Checks whether has negative modify is true for the current addon state.
     * @return true when has negative modify succeeds; otherwise false.
     */
    public boolean hasNegativeModify() {
        return !this.negativeProperty.isEmpty() && this.negativeLevel < 0;
    }

    /**
     * Checks whether is negative property is true for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return true when is negative property succeeds; otherwise false.
     */
    public boolean isNegativeProperty(DomainMasteryProperties prop) {
        return prop != null && this.negativeProperty.equals(prop.name()) && this.negativeLevel < 0;
    }

    /**
     * Checks whether can set negative is true for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return true when can set negative succeeds; otherwise false.
     */
    public boolean canSetNegative(DomainMasteryProperties prop) {
        if (prop == null) {
            return false;
        }
        // Negative modify is intentionally locked behind max mastery so only fully trained players can trade power in one category for points elsewhere.
        if (this.domainMasteryLevel < 5) {
            return false;
        }
        if (!prop.supportsNegativeModify()) {
            return false;
        }
        return this.negativeProperty.isEmpty() || this.negativeProperty.equals(prop.name());
    }

    /**
     * Pushes the selected negative modify deeper, granting an extra property point each time the penalty increases.
     * @param prop property identifier involved in this operation.
     * @return true when decrease negative succeeds; otherwise false.
     */
    public boolean decreaseNegative(DomainMasteryProperties prop) {
        int currentNeg;
        if (!this.canSetNegative(prop)) {
            return false;
        }
        int n = currentNeg = this.isNegativeProperty(prop) ? this.negativeLevel : 0;
        if (currentNeg <= -5) {
            return false;
        }
        if (this.getPropertyLevel(prop) > 0) {
            return false;
        }
        this.negativeProperty = prop.name();
        this.negativeLevel = currentNeg - 1;
        this.domainPropertyPoints += prop.getPointCost();
        return true;
    }

    /**
     * Reduces an existing negative modify by spending property points until the penalty is fully cleared.
     * @param prop property identifier involved in this operation.
     * @return true when increase negative succeeds; otherwise false.
     */
    public boolean increaseNegative(DomainMasteryProperties prop) {
        if (prop == null) {
            return false;
        }
        if (!this.negativeProperty.equals(prop.name())) {
            return false;
        }
        if (this.negativeLevel >= 0) {
            return false;
        }
        if (!this.spendPropertyPoints(prop.getPointCost())) {
            return false;
        }
        ++this.negativeLevel;
        if (this.negativeLevel >= 0) {
            this.clearNegativeState();
        }
        return true;
    }

    /**
     * Returns negative points for the current addon state.
     * @return the resolved negative points.
     */
    public int getNegativePoints() {
        return Math.abs(Math.min(0, this.negativeLevel));
    }

    @Deprecated
    /**
     * Returns negative debuff factor for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved negative debuff factor.
     */
    public double getNegativeDebuffFactor(DomainMasteryProperties prop) {
        if (prop == null || !this.isNegativeProperty(prop)) {
            return 1.0;
        }
        int negativePoints = this.getNegativePoints();
        return switch (prop) {
            case DURATION_EXTEND -> this.clampRuntimeMultiplier(1.0 - (double)negativePoints * 0.08);
            case RADIUS_BOOST -> this.clampRuntimeMultiplier(1.0 - (double)negativePoints * 0.06);
            case BARRIER_POWER -> this.clampRuntimeMultiplier(1.0 - (double)negativePoints * 0.04);
            case BARRIER_REFINEMENT -> this.clampRuntimeMultiplier(1.0 - (double)negativePoints * 0.06);
            default -> this.clampRuntimeMultiplier(1.0 - (double)negativePoints * 0.08);
        };
    }

    /**
     * Returns effective level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved effective level.
     */
    public int getEffectiveLevel(DomainMasteryProperties prop) {
        if (prop == null) {
            return 0;
        }
        int baseLevel = this.getPropertyLevel(prop);
        if (this.isNegativeProperty(prop)) {
            return baseLevel + this.negativeLevel;
        }
        return baseLevel;
    }

    /**
     * Returns effective property level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved effective property level.
     */
    public int getEffectivePropertyLevel(DomainMasteryProperties prop) {
        return this.getEffectiveLevel(prop);
    }

    /**
     * Returns duration runtime multiplier for the current addon state.
     * @return the resolved duration runtime multiplier.
     */
    public double getDurationRuntimeMultiplier() {
        return this.clampRuntimeMultiplier((double)this.resolveFinalDurationTicks(1200) / 1200.0);
    }

    /**
     * Returns duration bonus ticks for the current addon state.
     * @return the resolved duration bonus ticks.
     */
    public int getDurationBonusTicks() {
        int baseLevel = this.getPropertyLevel(DomainMasteryProperties.DURATION_EXTEND);
        int positiveBonus = baseLevel * 100;
        int negativeReduction = 0;
        if (this.isNegativeProperty(DomainMasteryProperties.DURATION_EXTEND) && this.negativeLevel < 0) {
            negativeReduction = Math.abs(this.negativeLevel) * 100;
        }
        return positiveBonus - negativeReduction;
    }

    /**
     * Resolves final duration ticks from the available addon data.
     * @param baseDurationTicks tick-based timing value used by this operation.
     * @return the resolved resolve final duration ticks.
     */
    public int resolveFinalDurationTicks(int baseDurationTicks) {
        int finalTicks = baseDurationTicks + this.getDurationBonusTicks();
        return Math.max(finalTicks, 200);
    }

    /**
     * Returns radius runtime multiplier for the current addon state.
     * @return the resolved radius runtime multiplier.
     */
    public double getRadiusRuntimeMultiplier() {
        int baseLevel = this.getPropertyLevel(DomainMasteryProperties.RADIUS_BOOST);
        double positiveBonus = (double)baseLevel * 0.12;
        double negativeReduction = 0.0;
        if (this.isNegativeProperty(DomainMasteryProperties.RADIUS_BOOST) && this.negativeLevel < 0) {
            negativeReduction = (double)Math.abs(this.negativeLevel) * 0.08;
        }
        return this.clampRuntimeMultiplier(1.0 + positiveBonus - negativeReduction);
    }

    // ===== RUNTIME SCALING HELPERS =====
    public double getBarrierRuntimeMultiplier() {
        int baseLevel = this.getPropertyLevel(DomainMasteryProperties.BARRIER_POWER);
        double positiveBonus = (double)baseLevel * 0.06;
        double negativeReduction = 0.0;
        if (this.isNegativeProperty(DomainMasteryProperties.BARRIER_POWER) && this.negativeLevel < 0) {
            negativeReduction = (double)Math.abs(this.negativeLevel) * 0.03;
        }
        return this.clampRuntimeMultiplier(1.0 + positiveBonus - negativeReduction);
    }

    /**
     * Performs clamp runtime multiplier for this addon component.
     * @param multiplier multiplier used by this method.
     * @return the resulting clamp runtime multiplier value.
     */
    private double clampRuntimeMultiplier(double multiplier) {
        return Math.max(0.1, multiplier);
    }

    /**
     * Sanitizes negative state before it is stored or reused.
     */
    private void sanitizeNegativeState() {
        this.negativeLevel = Math.max(-5, Math.min(0, this.negativeLevel));
        if (this.negativeLevel == 0 || this.negativeProperty == null || this.negativeProperty.isEmpty()) {
            this.clearNegativeState();
            return;
        }
        try {
            DomainMasteryProperties prop = DomainMasteryProperties.valueOf(this.negativeProperty);
            if (!prop.supportsNegativeModify()) {
                this.clearNegativeState();
            }
        }
        catch (IllegalArgumentException ignored) {
            this.clearNegativeState();
        }
    }

    /**
     * Performs clear negative state for this addon component.
     */
    private void clearNegativeState() {
        this.negativeProperty = "";
        this.negativeLevel = 0;
    }

    // ===== PROPERTY ACCESS HELPERS =====
    private int getPropLevel(DomainMasteryProperties prop) {
        return switch (prop) {
            case VICTIM_CE_DRAIN -> this.propCeDrain;
            case BF_CHANCE_BOOST -> this.propBfChance;
            case RCT_HEAL_BOOST -> this.propRctHeal;
            case BLIND_EFFECT -> this.propBlind;
            case SLOW_EFFECT -> this.propSlow;
            case DURATION_EXTEND -> this.propDuration;
            case RADIUS_BOOST -> this.propRadius;
            case BARRIER_POWER -> this.propBarrierPower;
            case BARRIER_REFINEMENT -> this.propBarrierRef;
            default -> 0;
        };
    }

    /**
     * Updates prop level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @param level level value used by this operation.
     */
    private void setPropLevel(DomainMasteryProperties prop, int level) {
        int clamped = Math.max(0, Math.min(10, Math.min(prop.getMaxLevel(), level)));
        switch (prop) {
            case VICTIM_CE_DRAIN: {
                this.propCeDrain = clamped;
                break;
            }
            case BF_CHANCE_BOOST: {
                this.propBfChance = clamped;
                break;
            }
            case RCT_HEAL_BOOST: {
                this.propRctHeal = clamped;
                break;
            }
            case BLIND_EFFECT: {
                this.propBlind = clamped;
                break;
            }
            case SLOW_EFFECT: {
                this.propSlow = clamped;
                break;
            }
            case DURATION_EXTEND: {
                this.propDuration = clamped;
                break;
            }
            case RADIUS_BOOST: {
                this.propRadius = clamped;
                break;
            }
            case BARRIER_POWER: {
                this.propBarrierPower = clamped;
                break;
            }
            case BARRIER_REFINEMENT: {
                this.propBarrierRef = clamped;
                break;
            }
        }
    }

    /**
     * Returns property level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved property level.
     */
    public int getPropertyLevel(DomainMasteryProperties prop) {
        return this.getPropLevel(prop);
    }

    /**
     * Updates property level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @param level level value used by this operation.
     */
    public void setPropertyLevel(DomainMasteryProperties prop, int level) {
        this.setPropLevel(prop, level);
    }

    // ===== NETWORK SYNC =====
    public void applySync(double xp, int level, int form, int points, int[] propLevels, String negativeProperty, int negativeLevel, boolean hasOpenBarrierAdvancement) {
        this.domainXP = xp;
        this.domainMasteryLevel = level;
        this.openBarrierAdvancementUnlocked = hasOpenBarrierAdvancement;
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(form, level, hasOpenBarrierAdvancement);
        this.domainPropertyPoints = Math.max(0, points);
        this.domainXPToNextLevel = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1);
        DomainMasteryProperties[] props = DomainMasteryProperties.values();
        for (int i = 0; i < Math.min(propLevels.length, props.length); ++i) {
            this.setPropLevel(props[i], propLevels[i]);
        }
        this.negativeProperty = negativeProperty == null ? "" : negativeProperty;
        this.negativeLevel = negativeLevel;
        this.sanitizeNegativeState();
    }

    /**
     * Overwrites the local client-side copy of mastery data using values received from the authoritative server capability.
     * @param xp xp used by this method.
     * @param level level value used by this operation.
     * @param form form used by this method.
     * @param points points used by this method.
     * @param propLevels prop levels used by this method.
     * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
     */
    public void applySync(double xp, int level, int form, int points, int[] propLevels, boolean hasOpenBarrierAdvancement) {
        this.applySync(xp, level, form, points, propLevels, "", 0, hasOpenBarrierAdvancement);
    }

    /**
     * Synchronizes to client with the client or server side copy.
     * @param player player instance involved in this operation.
     */
    public void syncToClient(ServerPlayer player) {
        ModNetworking.syncDomainMasteryToClient(player, this);
    }

    // ===== PROPERTY MUTATION OPERATIONS =====
    public boolean upgradeProperty(DomainMasteryProperties prop) {
        if (prop == null || this.isNegativeProperty(prop)) {
            return false;
        }
        int current = this.getPropLevel(prop);
        if (current >= prop.getMaxLevel()) {
            return false;
        }
        int cost = prop.getPointCost();
        if (!this.spendPropertyPoints(cost)) {
            return false;
        }
        this.setPropLevel(prop, current + 1);
        return true;
    }

    /**
     * Performs downgrade property for this addon component.
     * @param prop property identifier involved in this operation.
     * @return true when downgrade property succeeds; otherwise false.
     */
    public boolean downgradeProperty(DomainMasteryProperties prop) {
        if (prop == null || this.isNegativeProperty(prop)) {
            return false;
        }
        int current = this.getPropLevel(prop);
        if (current <= 0) {
            return false;
        }
        this.setPropLevel(prop, current - 1);
        this.domainPropertyPoints += prop.getPointCost();
        return true;
    }

    /**
     * Refunds every spent property point, clears the negative modify state, and rebuilds the available point pool.
     */
    public void refundAllProperties() {
        int refund = 0;
        refund += this.propCeDrain * DomainMasteryProperties.VICTIM_CE_DRAIN.getPointCost();
        refund += this.propBfChance * DomainMasteryProperties.BF_CHANCE_BOOST.getPointCost();
        refund += this.propRctHeal * DomainMasteryProperties.RCT_HEAL_BOOST.getPointCost();
        refund += this.propBlind * DomainMasteryProperties.BLIND_EFFECT.getPointCost();
        refund += this.propSlow * DomainMasteryProperties.SLOW_EFFECT.getPointCost();
        refund += this.propDuration * DomainMasteryProperties.DURATION_EXTEND.getPointCost();
        refund += this.propRadius * DomainMasteryProperties.RADIUS_BOOST.getPointCost();
        refund += this.propBarrierPower * DomainMasteryProperties.BARRIER_POWER.getPointCost();
        refund += this.propBarrierRef * DomainMasteryProperties.BARRIER_REFINEMENT.getPointCost();
        // Negative modify grants bonus points up front, so a full refund must subtract that borrowed value before restoring the pool.
        refund -= this.getNegativePoints();
        this.propSlow = 0;
        this.propBlind = 0;
        this.propRctHeal = 0;
        this.propBfChance = 0;
        this.propCeDrain = 0;
        this.propBarrierPower = 0;
        this.propBarrierRef = 0;
        this.propRadius = 0;
        this.propDuration = 0;
        this.clearNegativeState();
        this.domainPropertyPoints = Math.max(0, this.domainPropertyPoints + refund);
    }

    /**
     * Returns barrier power bonus for the current addon state.
     * @return the resolved barrier power bonus.
     */
    public double getBarrierPowerBonus() {
        int baseLevel = this.getPropertyLevel(DomainMasteryProperties.BARRIER_POWER);
        double positiveBonus = (double)baseLevel * 0.6;
        double negativeReduction = 0.0;
        if (this.isNegativeProperty(DomainMasteryProperties.BARRIER_POWER) && this.negativeLevel < 0) {
            negativeReduction = (double)Math.abs(this.negativeLevel) * 0.3;
        }
        double masteryScaling = 1.0 + this.domainMasteryLevel * 0.03;
        return (positiveBonus - negativeReduction) * masteryScaling;
    }

    /**
     * Returns the barrier refinement factor used to improve barrier stability.
     * Base value 0.5, each level adds 0.05, maxing at 1.0 (level 10).
     * Negative modify reduces it below 0.5 for glass-cannon builds.
     */
    public double getBarrierRefinementValue() {
        int baseLevel = this.getPropertyLevel(DomainMasteryProperties.BARRIER_REFINEMENT);
        double base = 0.3 + baseLevel * 0.04;
        if (this.isNegativeProperty(DomainMasteryProperties.BARRIER_REFINEMENT) && this.negativeLevel < 0) {
            base -= Math.abs(this.negativeLevel) * 0.04;
        }
        return Math.max(0.05, Math.min(0.75, base));
    }

    // ===== NBT SERIALIZATION =====
    public CompoundTag writeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("jjkbrp_domain_xp", this.domainXP);
        nbt.putInt("jjkbrp_domain_mastery_level", this.domainMasteryLevel);
        nbt.putInt("jjkbrp_domain_type_selected", DomainMasteryData.sanitizeFormSelection(this.domainTypeSelected, this.domainMasteryLevel));
        nbt.putInt("jjkbrp_domain_property_points", this.domainPropertyPoints);
        nbt.putInt("jjkbrp_prop_ce_drain", this.propCeDrain);
        nbt.putInt("jjkbrp_prop_bf_chance", this.propBfChance);
        nbt.putInt("jjkbrp_prop_rct_heal", this.propRctHeal);
        nbt.putInt("jjkbrp_prop_blind", this.propBlind);
        nbt.putInt("jjkbrp_prop_slow", this.propSlow);
        nbt.putInt("jjkbrp_prop_duration", this.propDuration);
        nbt.putInt("jjkbrp_prop_radius", this.propRadius);
        nbt.putInt("jjkbrp_prop_barrier_power", this.propBarrierPower);
        nbt.putInt("jjkbrp_prop_barrier_ref", this.propBarrierRef);
        // Both prefixed and legacy keys are written so older saved data and newer addon revisions stay compatible.
        nbt.putString("jjkbrp_negative_property", this.negativeProperty);
        nbt.putInt("jjkbrp_negative_level", this.negativeLevel);
        nbt.putString("negativeProperty", this.negativeProperty);
        nbt.putInt("negativeLevel", this.negativeLevel);
        return nbt;
    }

    /**
     * Loads the mastery capability from persistent NBT while clamping invalid or legacy values.
     * @param nbt serialized data container used by this operation.
     */
    public void readNBT(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        this.domainXP = Math.max(0.0, nbt.getDouble("jjkbrp_domain_xp"));
        this.domainMasteryLevel = Math.max(0, Math.min(5, nbt.getInt("jjkbrp_domain_mastery_level")));
        this.domainTypeSelected = DomainMasteryData.sanitizeFormSelection(nbt.getInt("jjkbrp_domain_type_selected"), this.domainMasteryLevel);
        this.domainPropertyPoints = Math.max(0, nbt.getInt("jjkbrp_domain_property_points"));
        this.propCeDrain = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_ce_drain")));
        this.propBfChance = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_bf_chance")));
        this.propRctHeal = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_rct_heal")));
        this.propBlind = Math.max(0, Math.min(5, nbt.getInt("jjkbrp_prop_blind")));
        this.propSlow = Math.max(0, Math.min(5, nbt.getInt("jjkbrp_prop_slow")));
        this.propDuration = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_duration")));
        this.propRadius = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_radius")));
        this.propBarrierPower = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_barrier_power")));
        this.propBarrierRef = Math.max(0, Math.min(10, nbt.getInt("jjkbrp_prop_barrier_ref")));
        this.negativeProperty = nbt.contains("negativeProperty") ? nbt.getString("negativeProperty") : nbt.getString("jjkbrp_negative_property");
        this.negativeLevel = nbt.contains("negativeLevel") ? nbt.getInt("negativeLevel") : nbt.getInt("jjkbrp_negative_level");
        this.domainXPToNextLevel = DomainMasteryData.getXPRequiredForLevel(this.domainMasteryLevel + 1);
        this.openBarrierAdvancementUnlocked = false;
        this.sanitizeNegativeState();
    }
    public void clearBlackFlashRuntimeState(net.minecraft.server.level.ServerPlayer player) { }
    public double getClashPowerBonus() { return getBarrierPowerBonus(); }
}



