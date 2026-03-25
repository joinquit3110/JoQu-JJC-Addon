package net.mcreator.jujutsucraft.addon.limb;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * Tracks the state and regeneration progress of every limb on a single entity.
 *
 * <h2>Limb lifecycle</h2>
 * <ol>
 *   <li><b>INTACT</b> — limb is whole; no tracking needed.</li>
 *   <li><b>SEVERED</b> — limb is gone; next RCT activation starts regeneration.</li>
 *   <li><b>REVERSING</b> — RCT is regrowing the limb; progress goes 0→1.</li>
 * </ol>
 *
 * <h2>NBT persistence</h2>
 * All fields are serialised via {@link #serializeNBT} / {@link #deserializeNBT}
 * and stored on the entity's capability data. Death (hard) resets all limbs to
 * {@link LimbState#INTACT} via {@link LimbCapabilityProvider#onPlayerClone}.
 *
 * @see LimbCapabilityProvider
 * @see LimbType
 * @see LimbState
 */

public class LimbData {
    private final EnumMap<LimbType, LimbState> states = new EnumMap<>(LimbType.class);
    private final EnumMap<LimbType, Float> regenProgress = new EnumMap<>(LimbType.class);
    private int severCooldownTicks = 0;
    private int bloodDripTicks = 0;

    public LimbData() {
        for (LimbType type : LimbType.values()) {
            this.states.put(type, LimbState.INTACT);
            this.regenProgress.put(type, 0.0f);
        }
    }

    public LimbState getState(LimbType type) {
        return this.states.getOrDefault(type, LimbState.INTACT);
    }

    public void setState(LimbType type, LimbState state) {
        this.states.put(type, state);
        if (state == LimbState.INTACT) {
            this.regenProgress.put(type, 0.0f);
        } else if (state == LimbState.SEVERED) {
            this.regenProgress.put(type, 0.0f);
        }
    }

    public float getRegenProgress(LimbType type) {
        return this.regenProgress.getOrDefault(type, 0.0f);
    }

    public void setRegenProgress(LimbType type, float progress) {
        this.regenProgress.put(type, Math.min(1.0f, Math.max(0.0f, progress)));
    }

    public boolean hasSeveredLimbs() {
        for (LimbState state : this.states.values()) {
            if (state != LimbState.SEVERED && state != LimbState.REVERSING) continue;
            return true;
        }
        return false;
    }

    public boolean hasReversingLimbs() {
        for (LimbState state : this.states.values()) {
            if (state == LimbState.REVERSING) return true;
        }
        return false;
    }

    public boolean isLimbMissing(LimbType type) {
        LimbState state = this.getState(type);
        return state == LimbState.SEVERED || state == LimbState.REVERSING;
    }

    public int countSeveredArms() {
        int count = 0;
        if (this.isLimbMissing(LimbType.LEFT_ARM)) ++count;
        if (this.isLimbMissing(LimbType.RIGHT_ARM)) ++count;
        return count;
    }

    public int countSeveredLegs() {
        int count = 0;
        if (this.isLimbMissing(LimbType.LEFT_LEG)) ++count;
        if (this.isLimbMissing(LimbType.RIGHT_LEG)) ++count;
        return count;
    }

    public int getSeverCooldownTicks() {
        return this.severCooldownTicks;
    }

    public void setSeverCooldownTicks(int ticks) {
        this.severCooldownTicks = ticks;
    }

    public void tickCooldown() {
        if (this.severCooldownTicks > 0) --this.severCooldownTicks;
        if (this.bloodDripTicks > 0) --this.bloodDripTicks;
    }

    public int getBloodDripTicks() {
        return this.bloodDripTicks;
    }

    public void setBloodDripTicks(int ticks) {
        this.bloodDripTicks = ticks;
    }

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

    public void deserializeNBT(CompoundTag tag) {
        for (LimbType type : LimbType.values()) {
            String key = type.getSerializedName();
            if (tag.contains(key + "_state")) {
                this.states.put(type, LimbState.fromOrdinal(tag.getInt(key + "_state")));
            }
            if (!tag.contains(key + "_regen")) continue;
            this.regenProgress.put(type, tag.getFloat(key + "_regen"));
        }
        this.severCooldownTicks = tag.getInt("sever_cooldown");
        this.bloodDripTicks = tag.getInt("blood_drip");
    }

    public void copyFrom(LimbData other) {
        for (LimbType type : LimbType.values()) {
            this.states.put(type, other.getState(type));
            this.regenProgress.put(type, other.getRegenProgress(type));
        }
        this.severCooldownTicks = other.severCooldownTicks;
        this.bloodDripTicks = other.bloodDripTicks;
    }

    public Map<LimbType, LimbState> getAllStates() {
        return new EnumMap<>(this.states);
    }

    public Map<LimbType, Float> getAllRegenProgress() {
        return new EnumMap<>(this.regenProgress);
    }
}
