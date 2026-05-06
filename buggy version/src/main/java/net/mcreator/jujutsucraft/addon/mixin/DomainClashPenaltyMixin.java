package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.util.ClashSessionSnapshot;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainClashHudSnapshot;
import net.mcreator.jujutsucraft.addon.util.DomainClashConstants;
import net.mcreator.jujutsucraft.addon.util.DomainClashRegistry;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.addon.util.DomainParticipantSnapshot;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clash-tick mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()` that applies temporary clash boosts and processes unified pressure during active domain clashes.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public class DomainClashPenaltyMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();
    // Thread-local stack storing temporary-strength recipients so clash-only buffs can be restored cleanly after processing.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Deque<List<UUID>>> JJKBRP$TEMP_CLASH_BOOST_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    // Persistent-data key indicating whether a temporary clash strength effect was applied by this mixin.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_APPLIED = "jjkbrp_temp_clash_strength_applied";
    // Persistent-data key recording whether the target already had the strength effect before clash processing began.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_HAD_EFFECT = "jjkbrp_temp_clash_strength_had_effect";
    // Persistent-data key storing the original strength duration before the clash-only override.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_DURATION = "jjkbrp_temp_clash_strength_duration";
    // Persistent-data key storing the original strength amplifier before the clash-only override.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_AMPLIFIER = "jjkbrp_temp_clash_strength_amplifier";
    // Persistent-data key storing the original ambient flag for restored strength effects.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_AMBIENT = "jjkbrp_temp_clash_strength_ambient";
    // Persistent-data key storing the original visibility flag for restored strength effects.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_VISIBLE = "jjkbrp_temp_clash_strength_visible";
    // Persistent-data key storing the original icon flag for restored strength effects.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final String JJKBRP$TEMP_STRENGTH_ICON = "jjkbrp_temp_clash_strength_icon";
    @Unique
    private static final String JJKBRP$TEMP_TOTAL_DAMAGE_APPLIED = "jjkbrp_temp_clash_total_damage_applied";
    @Unique
    private static final String JJKBRP$TEMP_TOTAL_DAMAGE_PRESENT = "jjkbrp_temp_clash_total_damage_present";
    @Unique
    private static final String JJKBRP$TEMP_TOTAL_DAMAGE_ORIGINAL = "jjkbrp_temp_clash_total_damage_original";
    @Unique
    private static final String JJKBRP$TEMP_TOTAL_DAMAGE_STAGED = "jjkbrp_temp_clash_total_damage_staged";
    @Unique
    private static final long JJKBRP$ACTIONBAR_CONTACT_WINDOW_TICKS = DomainClashConstants.ACTIONBAR_CONTACT_WINDOW_TICKS;


    // ===== CLASH ENTRY =====
    /**
     * Injects at the start of the active domain tick to apply the per-tick incomplete-domain penalty before clash scaling is evaluated.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$applyIncompletePenalty(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        if (world.isClientSide()) {
            return;
        }
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)world;
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!caster.hasEffect(domainEffect)) {
            return;
        }
        CompoundTag nbt = caster.getPersistentData();
        if (DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(caster)) {
            double penalty = nbt.getDouble("jjkbrp_incomplete_penalty_per_tick");
            if (penalty <= 0.0) {
                penalty = DomainClashConstants.DEFAULT_INCOMPLETE_PENALTY_PER_TICK;
            }
            double current = nbt.getDouble("totalDamage");
            nbt.putDouble("totalDamage", current + penalty);
        }
        double effectivePower = DomainClashPenaltyMixin.jjkbrp$computeEffectivePower(caster, nbt, domainEffect);
        nbt.putDouble("jjkbrp_effective_power", effectivePower);
        if (DomainClashPenaltyMixin.jjkbrp$shouldApplyDirectClashBoost(caster, nbt, domainEffect)) {
            DomainClashPenaltyMixin.jjkbrp$applyTemporaryClashBoosts(sl, caster, nbt, domainEffect);
        }
    }


    // ===== CLASH TICK RESOLUTION =====
    /**
     * Injects after the active domain tick to apply temporary boosts and process unified pressure.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN", shift=At.Shift.AFTER)}, remap=false)
    private static void jjkbrp$applyClashPowerAndErosion(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        if (world.isClientSide()) {
            return;
        }
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)world;
        DomainClashPenaltyMixin.jjkbrp$restoreTemporaryClashBoosts(sl);
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        CompoundTag nbt = caster.getPersistentData();
        if (!caster.hasEffect(domainEffect)) {
            return;
        }
        // ===== BARRIER-LOCK GUARD =====
        // If this entity recently won a clash resolution, its barrier/form NBT keys
        // are locked.  Skip ALL form re-evaluation and pressure application so the
        // global tick loop can't overwrite the winner's barrier state based on cast
        // order.  This is the core fix for the cast-order dependency bug.
        if (nbt.getBoolean("jjkbrp_clash_won_barrier_locked")) {
            long lockTick = nbt.getLong("jjkbrp_clash_won_barrier_lock_tick");
            long lockAge = sl.getGameTime() - lockTick;
            if (lockAge >= 0 && lockAge < DomainClashConstants.BARRIER_LOCK_DURATION_TICKS) {
                // Only update the registry entry (so power tracking stays alive)
                // but do NOT apply pressure, erosion, or form changes.
                if (DomainClashConstants.USE_REGISTRY) {
                    DomainClashRegistry.updateFromEntity(caster, world);
                }
                return;
            }
            // Lock expired — clear it and continue normal processing
            nbt.remove("jjkbrp_clash_won_barrier_locked");
            nbt.remove("jjkbrp_clash_won_barrier_lock_tick");
            nbt.remove("jjkbrp_clash_won_original_form");
        }
        DomainClashPenaltyMixin.jjkbrp$resetTransientClashFlags(nbt);
        double effectivePower = DomainClashPenaltyMixin.jjkbrp$computeEffectivePower(caster, nbt, domainEffect);
        nbt.putDouble("jjkbrp_effective_power", effectivePower);
        DomainClashPenaltyMixin.jjkbrp$applyFormPassives(sl, caster, nbt, effectivePower);
        double resolvedEffectivePower = nbt.contains("jjkbrp_effective_power") ? nbt.getDouble("jjkbrp_effective_power") : effectivePower;
        DomainClashRegistry.updateFromEntity(caster, world);
        // Unified pressure reads from NBT bridge keys written by the registry
        DomainClashPenaltyMixin.jjkbrp$applyUnifiedPressure(sl, caster, nbt);
        DomainClashPenaltyMixin.jjkbrp$spawnClashVFX(sl, caster, nbt);
        DomainClashPenaltyMixin.jjkbrp$sendClashActionBar(sl, caster, nbt, resolvedEffectivePower);
    }


    // ===== POWER HELPERS =====
    /**
     * Builds the effective clash power value from base strength, mastery bonuses, and runtime form data.
     * @param caster entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @param domainEffect effect instance processed by this helper.
     * @return the resulting compute effective power value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static double jjkbrp$computeEffectivePower(LivingEntity caster, CompoundTag nbt, MobEffect domainEffect) {
        double strBonus = DomainClashPenaltyMixin.jjkbrp$getBaseStrengthPower(caster, domainEffect);
        double hpRatio = DomainClashPenaltyMixin.jjkbrp$getHealthPowerRatio(caster, nbt);
        double timeFactor = DomainClashPenaltyMixin.jjkbrp$getDomainTimeFactor(caster, domainEffect);
        double basePower = strBonus * hpRatio * timeFactor * DomainClashPenaltyMixin.jjkbrp$getDomainIdMultiplier(nbt);
        return Math.max(0.0, basePower + DomainClashPenaltyMixin.jjkbrp$getClashPowerBonus(caster));
    }

    @Unique
    private static double jjkbrp$getDomainIdMultiplier(CompoundTag nbt) {
        double skillDomain = nbt.getDouble("skill_domain");
        if (skillDomain == 0.0) {
            skillDomain = nbt.getDouble("select");
        }
        if (skillDomain == 27.0) {
            return 1.5;
        }
        if (skillDomain == 29.0) {
            return 2.0;
        }
        return 1.0;
    }

    @Unique
    private static double jjkbrp$getDomainTimeFactor(LivingEntity caster, MobEffect domainEffect) {
        double duration = 0.0;
        MobEffectInstance domainInstance = caster.getEffect(domainEffect);
        if (domainInstance != null) {
            duration = domainInstance.getDuration();
        }
        return Math.min(Math.min(duration, 1200.0) / 2400.0 + 0.5, 1.0);
    }

    @Unique
    private static double jjkbrp$getHealthPowerRatio(LivingEntity caster, CompoundTag nbt) {
        double maxHP = Math.max((double)caster.getMaxHealth(), 1.0);
        double totalDamage = Math.max(0.0, nbt.getDouble("totalDamage"));
        return Math.max(maxHP - totalDamage * 2.0, 0.0) / maxHP;
    }

    /**
     * Performs get base strength power for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @param domainEffect effect instance processed by this helper.
     * @return the resulting get base strength power value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static double jjkbrp$getBaseStrengthPower(LivingEntity entity, MobEffect domainEffect) {
        int strengthAmplifier = 0;
        MobEffectInstance strengthEffect = entity.getEffect(MobEffects.DAMAGE_BOOST);
        if (strengthEffect != null) {
            strengthAmplifier = strengthEffect.getAmplifier();
        }
        double strBonus = (double)strengthAmplifier + 10.0;
        MobEffectInstance domainInstance = entity.getEffect(domainEffect);
        if (domainInstance != null && domainInstance.getAmplifier() > 0) {
            strBonus *= 1.15;
        }
        return strBonus;
    }

    /**
     * Performs get clash power bonus for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return the resulting get clash power bonus value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static double jjkbrp$getClashPowerBonus(LivingEntity entity) {
        if (!(entity instanceof Player)) {
            return 0.0;
        }
        Player player = (Player)entity;
        double[] bonus = new double[]{0.0};
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            bonus[0] = data.getClashPowerBonus();
        });
        return bonus[0];
    }


    // ===== TEMPORARY CLASH BOOSTS =====
    /**
     * Performs should apply direct clash boost for this mixin.
     * @param caster entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @param domainEffect effect instance processed by this helper.
     * @return whether should apply direct clash boost is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$shouldApplyDirectClashBoost(LivingEntity caster, CompoundTag nbt, MobEffect domainEffect) {
        MobEffectInstance domainInstance = caster.getEffect(domainEffect);
        if (domainInstance == null) {
            return false;
        }
        if (nbt.getDouble("select") != 0.0) {
            return false;
        }
        boolean update1 = nbt.getDouble("skill_domain") == 0.0 && nbt.getDouble("skill") == 0.0;
        return update1 || domainInstance.getDuration() % 20 == 0;
    }

    /**
     * Applies temporary clash boosts for the current mixin flow.
     * @param sl sl used by this method.
     * @param caster entity involved in the current mixin operation.
     * @param casterNbt caster nbt used by this method.
     * @param domainEffect effect instance processed by this helper.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$applyTemporaryClashBoosts(ServerLevel sl, LivingEntity caster, CompoundTag casterNbt, MobEffect domainEffect) {
        ArrayList<UUID> touched = new ArrayList<UUID>();
        HashSet<UUID> seen = new HashSet<UUID>();
        DomainClashPenaltyMixin.jjkbrp$stageTemporaryClashOverride(caster, domainEffect, touched, seen);
        double casterRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)sl, caster, casterNbt);
        if (casterRange > 0.0) {
            Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
            double searchRadius = Math.max(6.0, casterRange * 0.5 + 2.0);
            AABB searchBox = new AABB(casterCenter.x - searchRadius, casterCenter.y - searchRadius, casterCenter.z - searchRadius, casterCenter.x + searchRadius, casterCenter.y + searchRadius, casterCenter.z + searchRadius);
            for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != caster)) {
                CompoundTag otherNbt = other.getPersistentData();
                if (!other.hasEffect(domainEffect) && otherNbt.getDouble("select") == 0.0 || !DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, caster, casterNbt, other, otherNbt)) continue;
                DomainClashPenaltyMixin.jjkbrp$stageTemporaryClashOverride(other, domainEffect, touched, seen);
            }
        }
        JJKBRP$TEMP_CLASH_BOOST_STACK.get().push(touched);
    }

    /**
     * Applies temporary strength boost for the current mixin flow.
     * @param target entity involved in the current mixin operation.
     * @param touched touched used by this method.
     * @param seen seen used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$stageTemporaryClashOverride(LivingEntity target, MobEffect domainEffect, List<UUID> touched, Set<UUID> seen) {
        UUID uuid = target.getUUID();
        if (!seen.add(uuid)) {
            return;
        }
        CompoundTag nbt = target.getPersistentData();
        double effectivePower = DomainClashPenaltyMixin.jjkbrp$computeEffectivePower(target, nbt, domainEffect);
        nbt.putDouble("jjkbrp_effective_power", effectivePower);
        boolean strengthApplied = DomainClashPenaltyMixin.jjkbrp$applyTemporaryStrengthBoost(target, nbt, domainEffect, effectivePower);
        boolean damageApplied = DomainClashPenaltyMixin.jjkbrp$applyTemporaryEffectivePowerBridge(target, nbt, domainEffect, effectivePower);
        if (strengthApplied || damageApplied) {
            touched.add(uuid);
        }
    }

    /**
     * Applies temporary strength boost for the current mixin flow.
     * @param target entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @param domainEffect effect instance processed by this helper.
     * @param desiredPower desired power used by this method.
     * @return whether temporary strength boost is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$applyTemporaryStrengthBoost(LivingEntity target, CompoundTag nbt, MobEffect domainEffect, double desiredPower) {
        if (nbt.getBoolean(JJKBRP$TEMP_STRENGTH_APPLIED)) {
            return false;
        }
        MobEffectInstance currentStrength = target.getEffect(MobEffects.DAMAGE_BOOST);
        int originalAmplifier = currentStrength != null ? currentStrength.getAmplifier() : 0;
        int boostedAmplifier = originalAmplifier;
        if (DomainClashPenaltyMixin.jjkbrp$shouldBridgeBasePower(target, nbt, domainEffect)) {
            double strengthPowerPerAmplifier = DomainClashPenaltyMixin.jjkbrp$getStrengthPowerPerAmplifier(target, domainEffect);
            double unitFactor = DomainClashPenaltyMixin.jjkbrp$getDomainTimeFactor(target, domainEffect)
                    * DomainClashPenaltyMixin.jjkbrp$getDomainIdMultiplier(nbt);
            if (strengthPowerPerAmplifier > 0.0 && unitFactor > 0.0) {
                double requiredStrengthPower = Math.max(0.0, desiredPower / unitFactor);
                boostedAmplifier = Math.max(boostedAmplifier,
                        (int)Math.ceil(requiredStrengthPower / strengthPowerPerAmplifier - 10.0));
            }
        }
        else {
            double clashBonus = DomainClashPenaltyMixin.jjkbrp$getClashPowerBonus(target);
            int amplifierBonus = (int)Math.round(clashBonus);
            if (amplifierBonus != 0) {
                boostedAmplifier = Math.max(boostedAmplifier, originalAmplifier + amplifierBonus);
            }
        }
        boostedAmplifier = Math.max(0, boostedAmplifier);
        if (currentStrength != null && boostedAmplifier == currentStrength.getAmplifier()) {
            return false;
        }
        if (currentStrength == null && boostedAmplifier <= 0) {
            return false;
        }
        nbt.putBoolean(JJKBRP$TEMP_STRENGTH_APPLIED, true);
        nbt.putBoolean(JJKBRP$TEMP_STRENGTH_HAD_EFFECT, currentStrength != null);
        if (currentStrength != null) {
            nbt.putInt(JJKBRP$TEMP_STRENGTH_DURATION, currentStrength.getDuration());
            nbt.putInt(JJKBRP$TEMP_STRENGTH_AMPLIFIER, currentStrength.getAmplifier());
            nbt.putBoolean(JJKBRP$TEMP_STRENGTH_AMBIENT, currentStrength.isAmbient());
            nbt.putBoolean(JJKBRP$TEMP_STRENGTH_VISIBLE, currentStrength.isVisible());
            nbt.putBoolean(JJKBRP$TEMP_STRENGTH_ICON, currentStrength.showIcon());
        }
        int duration = currentStrength != null ? Math.max(currentStrength.getDuration(), 5) : 5;
        boolean ambient = currentStrength != null && currentStrength.isAmbient();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, boostedAmplifier, ambient, false, false));
        return true;
    }

    @Unique
    private static boolean jjkbrp$applyTemporaryEffectivePowerBridge(LivingEntity target, CompoundTag nbt, MobEffect domainEffect, double desiredPower) {
        if (!DomainClashPenaltyMixin.jjkbrp$shouldBridgeBasePower(target, nbt, domainEffect)) {
            return false;
        }
        if (nbt.getBoolean(JJKBRP$TEMP_TOTAL_DAMAGE_APPLIED)) {
            return false;
        }
        double strengthPowerPerAmplifier = DomainClashPenaltyMixin.jjkbrp$getStrengthPowerPerAmplifier(target, domainEffect);
        double unitFactor = DomainClashPenaltyMixin.jjkbrp$getDomainTimeFactor(target, domainEffect)
                * DomainClashPenaltyMixin.jjkbrp$getDomainIdMultiplier(nbt);
        if (!(strengthPowerPerAmplifier > 0.0) || !(unitFactor > 0.0)) {
            return false;
        }
        MobEffectInstance stagedStrength = target.getEffect(MobEffects.DAMAGE_BOOST);
        int stagedAmplifier = stagedStrength != null ? stagedStrength.getAmplifier() : 0;
        double stagedStrengthPower = ((double)stagedAmplifier + 10.0) * strengthPowerPerAmplifier;
        double stagedMaxPower = stagedStrengthPower * unitFactor;
        if (!(stagedMaxPower > 0.0)) {
            return false;
        }
        double hpRatio = Math.max(0.0, Math.min(desiredPower / stagedMaxPower, 1.0));
        double maxHP = Math.max((double)target.getMaxHealth(), 1.0);
        double stagedTotalDamage = maxHP * (1.0 - hpRatio) * 0.5;
        double currentTotalDamage = nbt.contains("totalDamage") ? nbt.getDouble("totalDamage") : 0.0;
        if (Math.abs(currentTotalDamage - stagedTotalDamage) < 1.0E-6) {
            return false;
        }
        nbt.putBoolean(JJKBRP$TEMP_TOTAL_DAMAGE_APPLIED, true);
        nbt.putBoolean(JJKBRP$TEMP_TOTAL_DAMAGE_PRESENT, nbt.contains("totalDamage"));
        if (nbt.contains("totalDamage")) {
            nbt.putDouble(JJKBRP$TEMP_TOTAL_DAMAGE_ORIGINAL, currentTotalDamage);
        }
        nbt.putDouble(JJKBRP$TEMP_TOTAL_DAMAGE_STAGED, stagedTotalDamage);
        nbt.putDouble("totalDamage", stagedTotalDamage);
        return true;
    }

    @Unique
    private static boolean jjkbrp$shouldBridgeBasePower(LivingEntity target, CompoundTag nbt, MobEffect domainEffect) {
        return target.hasEffect(domainEffect) && nbt.getDouble("select") == 0.0;
    }

    @Unique
    private static double jjkbrp$getStrengthPowerPerAmplifier(LivingEntity target, MobEffect domainEffect) {
        MobEffectInstance domainInstance = target.getEffect(domainEffect);
        return domainInstance != null && domainInstance.getAmplifier() > 0 ? 1.15 : 1.0;
    }

    /**
     * Restores restore temporary clash boosts after temporary mixin changes.
     * @param sl sl used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$restoreTemporaryClashBoosts(ServerLevel sl) {
        Deque<List<UUID>> stack = JJKBRP$TEMP_CLASH_BOOST_STACK.get();
        if (stack.isEmpty()) {
            return;
        }
        List<UUID> touched = stack.pop();
        for (UUID uuid : touched) {
            Entity resolved = sl.getEntity(uuid);
            if (!(resolved instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity)resolved;
            DomainClashPenaltyMixin.jjkbrp$restoreTemporaryStrengthBoost(living);
        }
        if (stack.isEmpty()) {
            JJKBRP$TEMP_CLASH_BOOST_STACK.remove();
        }
    }

    /**
     * Restores restore temporary strength boost after temporary mixin changes.
     * @param target entity involved in the current mixin operation.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$restoreTemporaryStrengthBoost(LivingEntity target) {
        CompoundTag nbt = target.getPersistentData();
        boolean restoreStrength = nbt.getBoolean(JJKBRP$TEMP_STRENGTH_APPLIED);
        boolean restoreDamage = nbt.getBoolean(JJKBRP$TEMP_TOTAL_DAMAGE_APPLIED);
        if (!restoreStrength && !restoreDamage) {
            return;
        }
        if (restoreStrength) {
            boolean hadEffect = nbt.getBoolean(JJKBRP$TEMP_STRENGTH_HAD_EFFECT);
            int duration = nbt.getInt(JJKBRP$TEMP_STRENGTH_DURATION);
            int amplifier = nbt.getInt(JJKBRP$TEMP_STRENGTH_AMPLIFIER);
            boolean ambient = nbt.getBoolean(JJKBRP$TEMP_STRENGTH_AMBIENT);
            boolean visible = nbt.getBoolean(JJKBRP$TEMP_STRENGTH_VISIBLE);
            boolean icon = nbt.getBoolean(JJKBRP$TEMP_STRENGTH_ICON);
            if (hadEffect) {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Math.max(duration, 1), amplifier, ambient, visible, icon));
            } else {
                target.removeEffect(MobEffects.DAMAGE_BOOST);
            }
        }
        if (restoreDamage) {
            double stagedTotalDamage = nbt.getDouble(JJKBRP$TEMP_TOTAL_DAMAGE_STAGED);
            double currentTotalDamage = nbt.getDouble("totalDamage");
            double delta = currentTotalDamage - stagedTotalDamage;
            if (nbt.getBoolean(JJKBRP$TEMP_TOTAL_DAMAGE_PRESENT)) {
                nbt.putDouble("totalDamage", nbt.getDouble(JJKBRP$TEMP_TOTAL_DAMAGE_ORIGINAL) + delta);
            }
            else if (Math.abs(delta) > 1.0E-6) {
                nbt.putDouble("totalDamage", delta);
            }
            else {
                nbt.remove("totalDamage");
            }
        }
        nbt.remove(JJKBRP$TEMP_STRENGTH_APPLIED);
        nbt.remove(JJKBRP$TEMP_STRENGTH_HAD_EFFECT);
        nbt.remove(JJKBRP$TEMP_STRENGTH_DURATION);
        nbt.remove(JJKBRP$TEMP_STRENGTH_AMPLIFIER);
        nbt.remove(JJKBRP$TEMP_STRENGTH_AMBIENT);
        nbt.remove(JJKBRP$TEMP_STRENGTH_VISIBLE);
        nbt.remove(JJKBRP$TEMP_STRENGTH_ICON);
        nbt.remove(JJKBRP$TEMP_TOTAL_DAMAGE_APPLIED);
        nbt.remove(JJKBRP$TEMP_TOTAL_DAMAGE_PRESENT);
        nbt.remove(JJKBRP$TEMP_TOTAL_DAMAGE_ORIGINAL);
        nbt.remove(JJKBRP$TEMP_TOTAL_DAMAGE_STAGED);
    }


    /**
     * Performs is incomplete domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is incomplete domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isIncompleteDomainState(LivingEntity entity) {
        return DomainAddonUtils.isIncompleteDomainState(entity);
    }

    /**
     * Performs is within base clash window for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param sourceNbt source nbt used by this method.
     * @param target entity involved in the current mixin operation.
     * @param targetNbt target nbt used by this method.
     * @return whether is within base clash window is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isWithinBaseClashWindow(ServerLevel world, LivingEntity source, CompoundTag sourceNbt, LivingEntity target, CompoundTag targetNbt) {
        if (source == null || target == null) {
            return false;
        }
        Vec3 sourceCenter = DomainAddonUtils.getDomainCenter((Entity)source);
        Vec3 targetBody = new Vec3(target.getX(), target.getY() + (double)target.getBbHeight() * 0.5, target.getZ());
        Vec3 targetCenter = DomainAddonUtils.getDomainCenter((Entity)target);
        double dx = sourceCenter.x - targetBody.x;
        double dy = sourceCenter.y - targetBody.y;
        double dz = sourceCenter.z - targetBody.z;
        double distToBodySq = dx * dx + dy * dy + dz * dz;
        double cdx = sourceCenter.x - targetCenter.x;
        double cdy = sourceCenter.y - targetCenter.y;
        double cdz = sourceCenter.z - targetCenter.z;
        double distToCenterSq = cdx * cdx + cdy * cdy + cdz * cdz;
        double distanceSq = Math.min(distToBodySq, distToCenterSq);
        double sourceRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)world, source, sourceNbt);
        double targetRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)world, target, targetNbt);
        double combinedRange = Math.max(sourceRange, targetRange);
        if (combinedRange <= 0.0) {
            return false;
        }
        double threshold = Math.max(4.0, combinedRange * 0.65);
        return distanceSq < threshold * threshold;
    }

    /**
     * Performs base clash range for this mixin.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return the resulting base clash range value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static double jjkbrp$baseClashRange(LevelAccessor world, LivingEntity entity, CompoundTag nbt) {
        double radius = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
        return radius * (DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(entity, nbt) ? 18.0 : 2.0);
    }

    /**
     * Performs is open domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return whether is open domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isOpenDomainState(LivingEntity entity, CompoundTag nbt) {
        return DomainAddonUtils.isOpenDomainState(entity);
    }


    private static void jjkbrp$applyUnifiedPressure(ServerLevel sl, LivingEntity caster, CompoundTag nbt) {
        if (!nbt.getBoolean("jjkbrp_unified_clash_active")) {
            return;
        }
        double pressure = nbt.getDouble("jjkbrp_unified_clash_pressure");
        if (pressure <= 0.0) {
            nbt.putBoolean("jjkbrp_unified_clash_active", false);
            return;
        }
        // Increment totalDamage by the pressure amount (matches legacy erosion/wrap behavior)
        double currentTotalDamage = nbt.getDouble("totalDamage");
        nbt.putDouble("totalDamage", currentTotalDamage + pressure);
        // Apply actual health damage using barrier resistance divisor
        double maxHP = caster.getMaxHealth();
        double barrierRef = nbt.getDouble("jjkbrp_barrier_refinement");
        double divisor = 1.0 + barrierRef * 5.0;
        double damage = pressure * (maxHP * 0.25) / divisor;
        caster.hurt(sl.damageSources().generic(), (float)damage);
        nbt.putBoolean("jjkbrp_unified_clash_active", false);
        LOGGER.debug("[UnifiedPressure] Applied {:.3f} damage to {} from pressure {:.3f}, barrierRef={:.3f}",
                damage, caster.getName().getString(), pressure, barrierRef);
    }

    @Unique
    private static void jjkbrp$resetTransientClashFlags(CompoundTag nbt) {
        if (DomainClashConstants.USE_REGISTRY) {
            return;
        }
        nbt.remove("jjkbrp_barrier_clash_active");
        nbt.remove("jjkbrp_open_clash_active");
        nbt.remove("jjkbrp_barrier_clash_opponent_uuid");
        nbt.remove("jjkbrp_open_clash_opponent_uuid");
    }


    // ===== ISSUE 10: DOMAIN FORM PASSIVES =====
    @Unique
    private static void jjkbrp$applyFormPassives(ServerLevel sl, LivingEntity caster, CompoundTag nbt, double effectivePower) {
        if (nbt.getBoolean("Failed") || nbt.getBoolean("DomainDefeated")) {
            return;
        }

        // Use registry sessions to determine if this caster is in a clash
        boolean inClash = DomainClashRegistry.isInClash(caster.getUUID());
        if (!inClash) {
            return;
        }

        if (DomainAddonUtils.isClosedDomainActive(caster)) {
            double barrierRef = nbt.contains("jjkbrp_barrier_refinement") ? nbt.getDouble("jjkbrp_barrier_refinement") : 0.3;
            barrierRef = Math.max(0.0, Math.min(0.75, barrierRef));
            double dmgReduction = 0.02 * barrierRef;
            double totalDamage = nbt.getDouble("totalDamage");
            if (totalDamage > 0.0) {
                nbt.putDouble("totalDamage", Math.max(0.0, totalDamage - dmgReduction));
            }
        }

        if (DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(caster, nbt)) {
            double sureHitMult = nbt.contains("jjkbrp_open_surehit_multiplier") ? nbt.getDouble("jjkbrp_open_surehit_multiplier") : 1.0;
            double openBonus = 0.005 * sureHitMult;
            nbt.putDouble("jjkbrp_effective_power", effectivePower + openBonus);
        }

        if (DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(caster)) {
            double wrapPenaltyReduce = 0.003;
            double totalDamage = nbt.getDouble("totalDamage");
            if (totalDamage > 0.0) {
                nbt.putDouble("totalDamage", Math.max(0.0, totalDamage - wrapPenaltyReduce));
            }
        }
    }


    // ===== CLASH VFX =====
    @Unique
    private static void jjkbrp$spawnClashVFX(ServerLevel sl, LivingEntity caster, CompoundTag nbt) {
        if (caster.tickCount % 5 != 0) {
            return;
        }
        if (nbt.getBoolean("Failed") || nbt.getBoolean("DomainDefeated")) {
            return;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!caster.hasEffect(domainEffect)) {
            return;
        }
        DomainParticipantSnapshot casterEntry = DomainClashRegistry.getEntry(caster.getUUID());
        List<ClashSessionSnapshot> sessions = DomainClashRegistry.getSessionsFor(caster.getUUID());
        if (casterEntry == null || sessions.isEmpty()) {
            return;
        }
        Vec3 casterCenter = casterEntry.getCenter();
        double casterPower = Math.max(0.0, casterEntry.getEffectivePower());
        for (ClashSessionSnapshot session : sessions) {
            UUID opponentUuid = session.getOpponent(caster.getUUID());
            if (opponentUuid == null) {
                continue;
            }
            DomainParticipantSnapshot opponentEntry = DomainClashRegistry.getEntry(opponentUuid);
            if (opponentEntry == null || opponentEntry.isDefeated()) {
                continue;
            }
            Entity resolved = sl.getEntity(opponentUuid);
            if (!(resolved instanceof LivingEntity other) || !other.hasEffect(domainEffect)) {
                continue;
            }
            CompoundTag otherNbt = other.getPersistentData();
            if (otherNbt.getBoolean("Failed") || otherNbt.getBoolean("DomainDefeated")) {
                continue;
            }
            Vec3 otherCenter = opponentEntry.getCenter();
            Vec3 midpoint = casterCenter.add(otherCenter).scale(0.5);
            float intensity = DomainClashPenaltyMixin.jjkbrp$clashVfxIntensity(casterPower, opponentEntry.getEffectivePower());
            double spread = Math.max(1.5, casterCenter.distanceTo(otherCenter) * (0.12 + intensity * 0.07));
            DomainClashPenaltyMixin.jjkbrp$spawnSessionParticles(sl, casterEntry.getForm(), opponentEntry.getForm(), midpoint, spread, intensity);
        }
    }

    @Unique
    private static float jjkbrp$clashVfxIntensity(double casterPower, double opponentPower) {
        double safeCaster = Math.max(0.0, casterPower);
        double safeOpponent = Math.max(0.0, opponentPower);
        double total = safeCaster + safeOpponent;
        if (total <= 0.0) {
            return 0.5f;
        }
        double delta = Math.abs(safeCaster - safeOpponent) / total;
        return (float)Math.max(0.35, 1.0 - delta * 0.7);
    }

    @Unique
    private static void jjkbrp$spawnSessionParticles(ServerLevel sl, DomainForm casterForm, DomainForm opponentForm,
                                                     Vec3 midpoint, double spread, float intensity) {
        int lightCount = Math.max(2, Math.round(3.0f + intensity * 3.0f));
        int heavyCount = Math.max(1, Math.round(2.0f + intensity * 4.0f));
        if (casterForm == DomainForm.CLOSED && opponentForm == DomainForm.CLOSED) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.END_ROD,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount, spread, spread * 0.5, spread, 0.02);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.CRIT,
                    midpoint.x, midpoint.y + 0.5, midpoint.z, heavyCount + 2, spread, spread * 0.5, spread, 0.05);
        } else if (casterForm == DomainForm.OPEN && opponentForm == DomainForm.OPEN) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.SOUL_FIRE_FLAME,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount + 1, spread, spread * 0.5, spread, 0.03);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.FLAME,
                    midpoint.x, midpoint.y + 0.5, midpoint.z, heavyCount + 1, spread, spread * 0.4, spread, 0.02);
        } else if ((casterForm == DomainForm.OPEN && opponentForm == DomainForm.CLOSED) || (casterForm == DomainForm.CLOSED && opponentForm == DomainForm.OPEN)) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.SMOKE,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount + 1, spread, spread * 0.5, spread, 0.01);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                    midpoint.x, midpoint.y + 1.5, midpoint.z, heavyCount, spread * 0.5, spread * 0.3, spread * 0.5, 0.0);
        } else if ((casterForm == DomainForm.OPEN && opponentForm == DomainForm.INCOMPLETE) || (casterForm == DomainForm.INCOMPLETE && opponentForm == DomainForm.OPEN)) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.SMOKE,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount, spread, spread * 0.5, spread, 0.02);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.ENCHANT,
                    midpoint.x, midpoint.y + 1.5, midpoint.z, heavyCount + 1, spread, spread * 0.8, spread, 0.05);
        } else if ((casterForm == DomainForm.INCOMPLETE && opponentForm == DomainForm.CLOSED) || (casterForm == DomainForm.CLOSED && opponentForm == DomainForm.INCOMPLETE)) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.ENCHANT,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount + 2, spread, spread * 0.8, spread, 0.04);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.SCULK_SOUL,
                    midpoint.x, midpoint.y + 1.5, midpoint.z, heavyCount, spread * 0.5, spread * 0.3, spread * 0.5, 0.01);
        } else if (casterForm == DomainForm.INCOMPLETE && opponentForm == DomainForm.INCOMPLETE) {
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.ENCHANT,
                    midpoint.x, midpoint.y + 1.0, midpoint.z, lightCount + 1, spread, spread * 0.6, spread, 0.03);
            DomainAddonUtils.sendLongDistanceParticles(sl, ParticleTypes.WITCH,
                    midpoint.x, midpoint.y + 0.5, midpoint.z, heavyCount + 1, spread, spread * 0.4, spread, 0.02);
        }
    }


    // ===== ISSUE 7: CLASH ACTIONBAR FEEDBACK =====
    @Unique
    private static void jjkbrp$sendClashActionBar(ServerLevel sl, LivingEntity caster, CompoundTag nbt, double effectivePower) {
        if (!(caster instanceof ServerPlayer casterPlayer)) {
            return;
        }
        if (caster.tickCount % 10 != 0) {
            return;
        }
        if (nbt.getBoolean("Failed") || nbt.getBoolean("DomainDefeated")) {
            LOGGER.debug("[DomainClashHUD] fallback skipped inactive caster={} reason=failed_or_defeated tick={}", caster.getName().getString(), sl.getGameTime());
            return;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!caster.hasEffect(domainEffect) && !DomainAddonUtils.hasOgLikeDomainClashRuntime(caster) && !DomainAddonUtils.isDomainBuildOrActive(sl, caster)) {
            LOGGER.debug("[DomainClashHUD] fallback skipped inactive caster={} reason=missing_domain_runtime tick={}", caster.getName().getString(), sl.getGameTime());
            return;
        }
        DomainParticipantSnapshot casterEntry = DomainClashRegistry.getEntry(caster.getUUID());
        List<ClashSessionSnapshot> sessions = DomainClashRegistry.getSessionsFor(caster.getUUID());
        if (casterEntry == null || sessions.isEmpty()) {
            LOGGER.debug("[DomainClashHUD] fallback skipped inactive caster={} reason=registry_empty entryPresent={} sessionCount={} tick={}",
                    caster.getName().getString(), casterEntry != null, sessions.size(), sl.getGameTime());
            return;
        }
        DomainClashHudSnapshot hudSnapshot = DomainClashHudSnapshot.fromRegistry(caster, sl.getGameTime());
        if (hudSnapshot == null || !hudSnapshot.isActive() || hudSnapshot.getOpponents().isEmpty()) {
            LOGGER.debug("[DomainClashHUD] fallback skipped inactive caster={} reason=no_hud_snapshot snapshotNull={} active={} opponents={} sessionCount={} tick={}",
                    caster.getName().getString(), hudSnapshot == null, hudSnapshot != null && hudSnapshot.isActive(), hudSnapshot != null ? hudSnapshot.getOpponents().size() : 0, sessions.size(), sl.getGameTime());
            return;
        }
        List<ModNetworking.DomainClashOpponentPayload> opponents = new ArrayList<>();
        for (DomainClashHudSnapshot.OpponentSnapshot opponent : hudSnapshot.getOpponents()) {
            opponents.add(new ModNetworking.DomainClashOpponentPayload(
                    opponent.power(),
                    opponent.form(),
                    opponent.domainId(),
                    opponent.name()));
        }
        float casterPower = hudSnapshot.getCasterPower();
        if (casterPower <= 0.0f) {
            casterPower = (float)Math.max(0.0, effectivePower);
        }
        LOGGER.info("[DomainClashHUD] active sync caster={} casterForm={} casterPower={:.1f} opponents={} tick={}",
                caster.getName().getString(), casterEntry.getForm(), casterPower, opponents.size(), sl.getGameTime());
        // Registry tick now sends per-viewer snapshots; this fallback only sends the caster perspective.
        DomainClashPenaltyMixin.jjkbrp$sendSnapshotToPlayer(casterPlayer, hudSnapshot);
    }

    @Unique
    private static void jjkbrp$sendSnapshotToPlayer(ServerPlayer player, DomainClashHudSnapshot snapshot) {
        if (player == null || snapshot == null) {
            return;
        }
        List<ModNetworking.DomainClashOpponentPayload> payloads = snapshot.getOpponents().stream()
                .map(opp -> new ModNetworking.DomainClashOpponentPayload(opp.power(), opp.form(), opp.domainId(), opp.name()))
                .toList();
        ModNetworking.sendDomainClashSync(player, snapshot.getCasterPower(), snapshot.getCasterDomainId(),
                snapshot.getCasterForm(), snapshot.getCasterName(), true, snapshot.getSyncedGameTime(), payloads);
    }

    @Unique
    private static void jjkbrp$broadcastClashSyncToAllParticipants(ServerLevel serverLevel,
                                                                    DomainClashHudSnapshot casterSnapshot,
                                                                    List<DomainClashHudSnapshot.OpponentSnapshot> opponents) {
        if (casterSnapshot == null || opponents == null || opponents.isEmpty()) {
            return;
        }

        Set<ServerPlayer> allParticipants = new HashSet<>();
        ServerPlayer casterPlayer = serverLevel.getServer().getPlayerList().getPlayer(casterSnapshot.getViewerUuid());
        if (casterPlayer != null) {
            allParticipants.add(casterPlayer);
        }
        for (DomainClashHudSnapshot.OpponentSnapshot opponent : opponents) {
            try {
                ServerPlayer opponentPlayer = serverLevel.getServer().getPlayerList().getPlayer(opponent.uuid());
                if (opponentPlayer != null) {
                    allParticipants.add(opponentPlayer);
                }
            } catch (IllegalArgumentException e) {
                // Skip malformed UUID
            }
        }

        float casterPower = casterSnapshot.getCasterPower();
        if (casterPower <= 0.0f) {
            DomainParticipantSnapshot entry = DomainClashRegistry.getEntry(casterSnapshot.getViewerUuid());
            if (entry != null) {
                casterPower = (float)entry.getEffectivePower();
            } else {
                casterPower = 0.0f;
            }
        }

        // Convert OpponentSnapshots to payloads for networking
        List<ModNetworking.DomainClashOpponentPayload> payloads = opponents.stream()
                .map(opp -> new ModNetworking.DomainClashOpponentPayload(
                        opp.power(), opp.form(), opp.domainId(), opp.name()))
                .toList();

        for (ServerPlayer player : allParticipants) {
            ModNetworking.sendDomainClashSync(player, casterPower,
                    casterSnapshot.getCasterDomainId(), casterSnapshot.getCasterForm(),
                    casterSnapshot.getCasterName(), true,
                    casterSnapshot.getSyncedGameTime(), payloads);
        }
    }

    @Unique
    private static void jjkbrp$sendInactiveClashSync(ServerPlayer player) {
        if (DomainClashRegistry.isInClash(player.getUUID())) {
            return;
        }
        ModNetworking.sendDomainClashSync(player, 0.0f, 0, 1,
                player.getName().getString(), false,
                player.level().getGameTime(), new ArrayList<>());
    }
}
