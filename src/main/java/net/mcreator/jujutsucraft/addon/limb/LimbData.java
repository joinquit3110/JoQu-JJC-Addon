package net.mcreator.jujutsucraft.addon.limb;

import java.util.EnumMap;
import java.util.Map;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.nbt.CompoundTag;

/**
 * Stores all persistent limb-related state for one living entity.
 *
 * <p>This capability-backed data object tracks whether each limb is intact, severed, or reversing,
 * the regeneration progress for reversing limbs, and short-lived timers used for sever cooldowns and
 * blood drip visuals.</p>
 */
public class LimbData {
    // ===== CORE LIMB STATE =====

    /** Current state for every limb tracked by the system. */
    private final EnumMap<LimbType, LimbState> states = new EnumMap(LimbType.class);
    /**
     * Regeneration progress for each limb in the inclusive range {@code 0.0f} to {@code 1.0f}.
     * Values are meaningful while a limb is in {@link net.mcreator.jujutsucraft.addon.limb.LimbState#REVERSING}.
     */
    private final EnumMap<LimbType, Float> regenProgress = new EnumMap(LimbType.class);
    /** Cooldown in ticks before another sever attempt is allowed. */
    private int severCooldownTicks = 0;
    /** Remaining ticks for passive blood-drip particle effects. */
    private int bloodDripTicks = 0;

    /**
     * Initializes every limb as intact with no regeneration progress.
     */
    public LimbData() {
        for (LimbType type : LimbType.values()) {
            this.states.put(type, LimbState.INTACT);
            this.regenProgress.put(type, Float.valueOf(0.0f));
        }
    }

    // ===== PER-LIMB ACCESS =====

    /**
     * Returns the current state of one limb.
     *
     * @param type limb slot being queried
     * @return stored state, defaulting to {@link net.mcreator.jujutsucraft.addon.limb.LimbState#INTACT}
     */
    public LimbState getState(LimbType type) {
        return this.states.getOrDefault((Object)type, LimbState.INTACT);
    }

    /**
     * Updates the state of one limb.
     *
     * @param type limb slot to change
     * @param state new lifecycle state for that limb
     */
    public void setState(LimbType type, LimbState state) {
        this.states.put(type, state);
        if (state == LimbState.INTACT) {
            // Fully restored limbs should not retain leftover visual progress.
            this.regenProgress.put(type, Float.valueOf(0.0f));
        } else if (state == LimbState.SEVERED) {
            // Freshly severed limbs also restart the reverse-healing bar from zero.
            this.regenProgress.put(type, Float.valueOf(0.0f));
        }
    }

    /**
     * Returns the reverse-healing progress for one limb.
     *
     * @param type limb slot being queried
     * @return progress from {@code 0.0f} to {@code 1.0f}
     */
    public float getRegenProgress(LimbType type) {
        return this.regenProgress.getOrDefault((Object)type, Float.valueOf(0.0f)).floatValue();
    }

    /**
     * Sets the reverse-healing progress for one limb.
     *
     * @param type limb slot to update
     * @param progress raw progress value, clamped into the inclusive {@code 0.0f-1.0f} range
     */
    public void setRegenProgress(LimbType type, float progress) {
        // Clamp aggressively so networking or timer overshoot cannot push invalid values into storage.
        this.regenProgress.put(type, Float.valueOf(Math.min(1.0f, Math.max(0.0f, progress))));
    }

    // ===== STATE QUERIES =====

    /**
     * Checks whether the entity currently has any missing or reversing limb.
     *
     * @return {@code true} when at least one limb is severed or reversing
     */
    public boolean hasSeveredLimbs() {
        for (LimbState state : this.states.values()) {
            // REVERSING still counts as missing for gameplay and rendering purposes until finished.
            if (state != LimbState.SEVERED && state != LimbState.REVERSING) continue;
            return true;
        }
        return false;
    }

    /**
     * Checks whether at least one limb is actively being restored.
     *
     * @return {@code true} when any limb is in the reversing phase
     */
    public boolean hasReversingLimbs() {
        for (LimbState state : this.states.values()) {
            if (state != LimbState.REVERSING) continue;
            return true;
        }
        return false;
    }

    /**
     * Checks whether a specific limb is considered unavailable.
     *
     * @param type limb slot being checked
     * @return {@code true} when the limb is severed or still reversing
     */
    public boolean isLimbMissing(LimbType type) {
        LimbState state = this.getState(type);
        return state == LimbState.SEVERED || state == LimbState.REVERSING;
    }

    /**
     * Counts missing arms for attack and inventory penalties.
     *
     * @return number of missing arms, from {@code 0} to {@code 2}
     */
    public int countSeveredArms() {
        int count = 0;
        if (this.isLimbMissing(LimbType.LEFT_ARM)) {
            ++count;
        }
        if (this.isLimbMissing(LimbType.RIGHT_ARM)) {
            ++count;
        }
        return count;
    }

    /**
     * Counts missing legs for movement penalties.
     *
     * @return number of missing legs, from {@code 0} to {@code 2}
     */
    public int countSeveredLegs() {
        int count = 0;
        if (this.isLimbMissing(LimbType.LEFT_LEG)) {
            ++count;
        }
        if (this.isLimbMissing(LimbType.RIGHT_LEG)) {
            ++count;
        }
        return count;
    }

    // ===== TIMERS =====

    /**
     * Returns the remaining sever cooldown.
     *
     * @return cooldown length in game ticks
     */
    public int getSeverCooldownTicks() {
        return this.severCooldownTicks;
    }

    /**
     * Updates the sever cooldown timer.
     *
     * @param ticks cooldown length in ticks
     */
    public void setSeverCooldownTicks(int ticks) {
        this.severCooldownTicks = ticks;
    }

    /**
     * Ticks down temporary timers maintained by the capability.
     */
    public void tickCooldown() {
        if (this.severCooldownTicks > 0) {
            --this.severCooldownTicks;
        }
        if (this.bloodDripTicks > 0) {
            --this.bloodDripTicks;
        }
    }

    /**
     * Returns the remaining blood-drip visual duration.
     *
     * @return drip duration in ticks
     */
    public int getBloodDripTicks() {
        return this.bloodDripTicks;
    }

    /**
     * Updates the remaining blood-drip visual duration.
     *
     * @param ticks drip duration in ticks
     */
    public void setBloodDripTicks(int ticks) {
        this.bloodDripTicks = ticks;
    }

    // ===== NBT SERIALIZATION =====

    /**
     * Serializes all limb state into NBT.
     *
     * <p>Keys follow the format {@code *_state}, {@code *_regen}, plus the shared
     * {@code sever_cooldown} and {@code blood_drip} timers.</p>
     *
     * @return serialized capability payload
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (LimbType type : LimbType.values()) {
            tag.putInt(type.getSerializedName() + "_state", this.getState(type).ordinal());
            tag.putFloat(type.getSerializedName() + "_regen", this.getRegenProgress(type));
        }
        tag.putInt("sever_cooldown", this.severCooldownTicks);
        tag.putInt("blood_drip", this.bloodDripTicks);
        return tag;
    }

    /**
     * Restores limb state from NBT.
     *
     * @param tag previously serialized capability payload
     */
    public void deserializeNBT(CompoundTag tag) {
        for (LimbType type : LimbType.values()) {
            String key = type.getSerializedName();
            if (tag.contains(key + "_state")) {
                this.states.put(type, LimbState.fromOrdinal(tag.getInt(key + "_state")));
            }
            if (!tag.contains(key + "_regen")) continue;
            this.regenProgress.put(type, Float.valueOf(tag.getFloat(key + "_regen")));
        }
        this.severCooldownTicks = tag.getInt("sever_cooldown");
        this.bloodDripTicks = tag.getInt("blood_drip");
    }

    // ===== COPYING AND SNAPSHOTS =====

    /**
     * Copies all limb data from another capability instance.
     *
     * @param other source capability data to clone from
     */
    public void copyFrom(LimbData other) {
        for (LimbType type : LimbType.values()) {
            this.states.put(type, other.getState(type));
            this.regenProgress.put(type, Float.valueOf(other.getRegenProgress(type)));
        }
        this.severCooldownTicks = other.severCooldownTicks;
        this.bloodDripTicks = other.bloodDripTicks;
    }

    /**
     * Returns a defensive copy of all limb states.
     *
     * @return copied map suitable for networking or rendering snapshots
     */
    public Map<LimbType, LimbState> getAllStates() {
        return new EnumMap<LimbType, LimbState>(this.states);
    }

    /**
     * Returns a defensive copy of all regeneration progress values.
     *
     * @return copied map suitable for networking or rendering snapshots
     */
    public Map<LimbType, Float> getAllRegenProgress() {
        return new EnumMap<LimbType, Float>(this.regenProgress);
    }
}
