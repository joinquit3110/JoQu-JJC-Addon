package net.mcreator.jujutsucraft.addon.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clash-tick mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()` that applies temporary clash boosts, open-barrier erosion pressure, incomplete wrap pressure, and mutual contact refresh logic during active domain clashes.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public class DomainClashPenaltyMixin {
    // Base erosion rate applied when an open domain pressures a closed barrier during a clash.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final double JJKBRP$BASE_EROSION_RATE = 0.2;
    // Maximum allowed attacker-versus-defender power ratio when clash calculations are normalized.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final double JJKBRP$MAX_POWER_RATIO = 3.0;
    // Base wrap-pressure rate applied by incomplete domains against complete domains.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final double JJKBRP$INCOMPLETE_WRAP_PRESSURE = 0.1;
    // Stability factor used to damp incomplete wrap-pressure calculations.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final double JJKBRP$INCOMPLETE_WRAP_STABILITY = 0.45;
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
        if (DomainClashPenaltyMixin.jjkbrp$shouldApplyDirectClashBoost(caster, nbt, domainEffect)) {
            DomainClashPenaltyMixin.jjkbrp$applyTemporaryClashBoosts(sl, caster, nbt, domainEffect);
        }
        if (!DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(caster)) {
            return;
        }
        double penalty = nbt.getDouble("jjkbrp_incomplete_penalty_per_tick");
        if (penalty <= 0.0) {
            penalty = 0.01;
        }
        if (nbt.getBoolean("jjkbrp_incomplete_wrap_active")) {
            penalty *= 0.45;
        }
        double current = nbt.getDouble("totalDamage");
        nbt.putDouble("totalDamage", current + penalty);
    }


    // ===== CLASH TICK RESOLUTION =====
    /**
     * Injects after the active domain tick to evaluate clash power, apply temporary boosts, process open erosion, and apply incomplete wrap pressure.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
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
        double effectivePower = DomainClashPenaltyMixin.jjkbrp$computeEffectivePower(caster, nbt, domainEffect);
        nbt.putDouble("jjkbrp_effective_power", effectivePower);
        DomainClashPenaltyMixin.jjkbrp$applyBarrierErosion(sl, caster, nbt, effectivePower);
        DomainClashPenaltyMixin.jjkbrp$applyIncompleteWrapPressure(sl, caster, nbt, effectivePower);
        DomainClashPenaltyMixin.jjkbrp$refreshMutualClashContact(sl, caster, nbt);
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
        float maxHP = caster.getMaxHealth();
        double totalDamage = Math.max(0.0, nbt.getDouble("totalDamage"));
        double strBonus = DomainClashPenaltyMixin.jjkbrp$getBaseStrengthPower(caster, domainEffect);
        double domainIdMult = 1.0;
        double skillDomain = nbt.getDouble("skill_domain");
        if (skillDomain == 0.0) {
            skillDomain = nbt.getDouble("select");
        }
        if (skillDomain == 27.0) {
            domainIdMult = 1.5;
        } else if (skillDomain == 29.0) {
            domainIdMult = 2.0;
        }
        double duration = 0.0;
        MobEffectInstance domainInstance = caster.getEffect(domainEffect);
        if (domainInstance != null) {
            duration = domainInstance.getDuration();
        }
        double timeFactor = Math.min(Math.min(duration, 1200.0) / 2400.0 + 0.5, 1.0);
        double hpRatio = Math.max((double)maxHP - totalDamage * 2.0, 0.0) / Math.max((double)maxHP, 1.0);
        double basePower = strBonus * hpRatio * timeFactor * domainIdMult;
        return Math.max(0.0, basePower + DomainClashPenaltyMixin.jjkbrp$getClashPowerBonus(caster));
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
        DomainClashPenaltyMixin.jjkbrp$applyTemporaryStrengthBoost(caster, touched, seen);
        double casterRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)sl, caster, casterNbt);
        if (casterRange > 0.0) {
            Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
            double searchRadius = Math.max(6.0, casterRange * 0.5 + 2.0);
            AABB searchBox = new AABB(casterCenter.x - searchRadius, casterCenter.y - searchRadius, casterCenter.z - searchRadius, casterCenter.x + searchRadius, casterCenter.y + searchRadius, casterCenter.z + searchRadius);
            for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != caster)) {
                CompoundTag otherNbt = other.getPersistentData();
                if (!other.hasEffect(domainEffect) && otherNbt.getDouble("select") == 0.0 || !DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, caster, casterNbt, other, otherNbt)) continue;
                DomainClashPenaltyMixin.jjkbrp$applyTemporaryStrengthBoost(other, touched, seen);
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
    private static void jjkbrp$applyTemporaryStrengthBoost(LivingEntity target, List<UUID> touched, Set<UUID> seen) {
        UUID uuid = target.getUUID();
        if (!seen.add(uuid)) {
            return;
        }
        double clashBonus = DomainClashPenaltyMixin.jjkbrp$getClashPowerBonus(target);
        int amplifierBonus = (int)Math.round(clashBonus);
        if (amplifierBonus == 0) {
            return;
        }
        CompoundTag nbt = target.getPersistentData();
        if (nbt.getBoolean(JJKBRP$TEMP_STRENGTH_APPLIED)) {
            return;
        }
        MobEffectInstance currentStrength = target.getEffect(MobEffects.DAMAGE_BOOST);
        int originalAmplifier = currentStrength != null ? currentStrength.getAmplifier() : 0;
        int boostedAmplifier = Math.max(0, originalAmplifier + amplifierBonus);
        if (currentStrength != null && boostedAmplifier == currentStrength.getAmplifier()) {
            return;
        }
        if (currentStrength == null && boostedAmplifier <= 0) {
            return;
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
        touched.add(uuid);
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
        if (!nbt.getBoolean(JJKBRP$TEMP_STRENGTH_APPLIED)) {
            return;
        }
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
        nbt.remove(JJKBRP$TEMP_STRENGTH_APPLIED);
        nbt.remove(JJKBRP$TEMP_STRENGTH_HAD_EFFECT);
        nbt.remove(JJKBRP$TEMP_STRENGTH_DURATION);
        nbt.remove(JJKBRP$TEMP_STRENGTH_AMPLIFIER);
        nbt.remove(JJKBRP$TEMP_STRENGTH_AMBIENT);
        nbt.remove(JJKBRP$TEMP_STRENGTH_VISIBLE);
        nbt.remove(JJKBRP$TEMP_STRENGTH_ICON);
    }


    // ===== EROSION AND WRAP PRESSURE =====
    /**
     * Applies open-barrier erosion pressure against a valid closed-domain target during a clash.
     * @param sl sl used by this method.
     * @param openCaster open caster used by this method.
     * @param openNbt open nbt used by this method.
     * @param openPower open power used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$applyBarrierErosion(ServerLevel sl, LivingEntity openCaster, CompoundTag openNbt, double openPower) {
        double erosion;
        double closedPower;
        double sureHitMult;
        if (!openNbt.getBoolean("jjkbrp_is_eroding_barrier")) {
            return;
        }
        LivingEntity previousTarget = DomainClashPenaltyMixin.jjkbrp$resolveLinkedLivingEntity(sl, openNbt.getString("jjkbrp_erosion_target_uuid"));
        if (!DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(openCaster, openNbt)) {
            if (previousTarget != null) {
                DomainClashPenaltyMixin.jjkbrp$cleanupDefenderState(previousTarget.getPersistentData());
            }
            DomainClashPenaltyMixin.jjkbrp$cleanupAttackerState(openNbt);
            return;
        }
        LivingEntity closedCaster = previousTarget;
        if (!DomainClashPenaltyMixin.jjkbrp$isValidErosionTarget(sl, closedCaster, openCaster)) {
            LivingEntity retargetedClosedCaster;
            UUID retargetUuid = DomainClashPenaltyMixin.jjkbrp$retargetErosion(openCaster, sl);
            if (retargetUuid == null) {
                if (closedCaster != null) {
                    DomainClashPenaltyMixin.jjkbrp$cleanupDefenderState(closedCaster.getPersistentData());
                }
                DomainClashPenaltyMixin.jjkbrp$cleanupAttackerState(openNbt);
                return;
            }
            Entity retargetEntity = sl.getEntity(retargetUuid);
            if (!(retargetEntity instanceof LivingEntity) || !DomainClashPenaltyMixin.jjkbrp$isValidErosionTarget(sl, retargetedClosedCaster = (LivingEntity)retargetEntity, openCaster)) {
                if (closedCaster != null) {
                    DomainClashPenaltyMixin.jjkbrp$cleanupDefenderState(closedCaster.getPersistentData());
                }
                DomainClashPenaltyMixin.jjkbrp$cleanupAttackerState(openNbt);
                return;
            }
            DomainClashPenaltyMixin.jjkbrp$assignErosionTarget(openCaster, openNbt, closedCaster, retargetedClosedCaster);
            closedCaster = retargetedClosedCaster;
        }
        if (closedCaster == null) {
            DomainClashPenaltyMixin.jjkbrp$cleanupAttackerState(openNbt);
            return;
        }
        CompoundTag closedNbt = closedCaster.getPersistentData();
        long tick = sl.getGameTime();
        openNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
        closedNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
        double d = sureHitMult = openNbt.contains("jjkbrp_open_surehit_multiplier") ? openNbt.getDouble("jjkbrp_open_surehit_multiplier") : 1.0;
        if (sureHitMult <= 0.0) {
            sureHitMult = 1.0;
        }
        double barrierRef = closedNbt.contains("jjkbrp_barrier_refinement") ? closedNbt.getDouble("jjkbrp_barrier_refinement") : 0.5;
        barrierRef = Math.max(0.0, Math.min(1.0, barrierRef));
        boolean targetIncomplete = DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(closedCaster);
        double incompleteMultiplier = targetIncomplete ? 2.0 : 1.0;
        double d2 = closedPower = closedNbt.contains("jjkbrp_effective_power") ? closedNbt.getDouble("jjkbrp_effective_power") : 100.0;
        if (closedPower <= 0.0) {
            closedPower = 1.0;
        }
        double powerRatio = Math.min(openPower / closedPower, 3.0);
        powerRatio = Math.max(0.1, powerRatio);
        double totalErosionThisTick = erosion = 0.2 * sureHitMult * (1.0 - barrierRef) * incompleteMultiplier * powerRatio;
        totalErosionThisTick = Math.min(totalErosionThisTick, 2.0);
        double currentTotalDamage = closedNbt.getDouble("totalDamage");
        closedNbt.putDouble("totalDamage", currentTotalDamage + totalErosionThisTick);
        double cumulativeErosion = closedNbt.getDouble("jjkbrp_barrier_erosion_total");
        closedNbt.putDouble("jjkbrp_barrier_erosion_total", cumulativeErosion + totalErosionThisTick);
        closedNbt.putBoolean("jjkbrp_barrier_under_attack", true);
        closedNbt.putString("jjkbrp_open_attacker_uuid", openCaster.getStringUUID());
    }

    /**
     * Applies incomplete-domain wrap pressure against the current valid clash target.
     * @param sl sl used by this method.
     * @param incompleteCaster incomplete caster used by this method.
     * @param incompleteNbt incomplete nbt used by this method.
     * @param incompletePower incomplete power used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$applyIncompleteWrapPressure(ServerLevel sl, LivingEntity incompleteCaster, CompoundTag incompleteNbt, double incompletePower) {
        double targetPower;
        if (!DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(incompleteCaster)) {
            return;
        }
        if (!incompleteNbt.getBoolean("jjkbrp_incomplete_wrap_active")) {
            return;
        }
        LivingEntity previousTarget = DomainClashPenaltyMixin.jjkbrp$resolveLinkedLivingEntity(sl, incompleteNbt.getString("jjkbrp_incomplete_wrap_target_uuid"));
        LivingEntity targetCaster = previousTarget;
        if (!DomainClashPenaltyMixin.jjkbrp$isValidWrapTarget(sl, targetCaster, incompleteCaster)) {
            LivingEntity retargetedTarget;
            UUID retargetUuid = DomainClashPenaltyMixin.jjkbrp$retargetWrap(incompleteCaster, sl);
            if (retargetUuid == null) {
                if (targetCaster != null) {
                    DomainClashPenaltyMixin.jjkbrp$cleanupWrappedTargetState(targetCaster.getPersistentData());
                }
                DomainClashPenaltyMixin.jjkbrp$cleanupIncompleteWrapState(incompleteNbt);
                return;
            }
            Entity retargetEntity = sl.getEntity(retargetUuid);
            if (!(retargetEntity instanceof LivingEntity) || !DomainClashPenaltyMixin.jjkbrp$isValidWrapTarget(sl, retargetedTarget = (LivingEntity)retargetEntity, incompleteCaster)) {
                if (targetCaster != null) {
                    DomainClashPenaltyMixin.jjkbrp$cleanupWrappedTargetState(targetCaster.getPersistentData());
                }
                DomainClashPenaltyMixin.jjkbrp$cleanupIncompleteWrapState(incompleteNbt);
                return;
            }
            DomainClashPenaltyMixin.jjkbrp$assignWrapTarget(incompleteCaster, incompleteNbt, targetCaster, retargetedTarget);
            targetCaster = retargetedTarget;
        }
        if (targetCaster == null) {
            DomainClashPenaltyMixin.jjkbrp$cleanupIncompleteWrapState(incompleteNbt);
            return;
        }
        CompoundTag targetNbt = targetCaster.getPersistentData();
        long tick = sl.getGameTime();
        incompleteNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
        targetNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
        double d = targetPower = targetNbt.contains("jjkbrp_effective_power") ? targetNbt.getDouble("jjkbrp_effective_power") : 80.0;
        if (targetPower <= 0.0) {
            targetPower = 1.0;
        }
        double ratio = Math.max(0.35, Math.min(incompletePower / targetPower, 1.45));
        double wrapPressure = 0.1 * ratio;
        if (DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(targetCaster, targetNbt)) {
            wrapPressure *= 0.7;
        }
        wrapPressure = Math.min(wrapPressure, 0.18);
        double current = targetNbt.getDouble("totalDamage");
        targetNbt.putDouble("totalDamage", current + wrapPressure);
        targetNbt.putBoolean("jjkbrp_wrapped_by_incomplete", true);
        targetNbt.putString("jjkbrp_incomplete_wrapper_uuid", incompleteCaster.getStringUUID());
    }

    /**
     * Resolves linked living entity from the currently available runtime data.
     * @param sl sl used by this method.
     * @param targetUuidStr target uuid str used by this method.
     * @return the resulting resolve linked living entity value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    @Nullable
    private static LivingEntity jjkbrp$resolveLinkedLivingEntity(ServerLevel sl, String targetUuidStr) {
        if (targetUuidStr == null || targetUuidStr.isEmpty()) {
            return null;
        }
        try {
            LivingEntity living;
            Entity targetEntity = sl.getEntity(UUID.fromString(targetUuidStr));
            return targetEntity instanceof LivingEntity ? (living = (LivingEntity)targetEntity) : null;
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Performs is valid erosion target for this mixin.
     * @param sl sl used by this method.
     * @param target entity involved in the current mixin operation.
     * @param caster entity involved in the current mixin operation.
     * @return whether is valid erosion target is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isValidErosionTarget(ServerLevel sl, @Nullable LivingEntity target, LivingEntity caster) {
        if (target == null || caster == null || target == caster) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        CompoundTag targetNbt = target.getPersistentData();
        if (targetNbt.getBoolean("Failed") || targetNbt.getBoolean("DomainDefeated")) {
            return false;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!target.hasEffect(domainEffect)) {
            return false;
        }
        if (DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(target, targetNbt)) {
            return false;
        }
        CompoundTag casterNbt = caster.getPersistentData();
        if (!DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, caster, casterNbt, target, targetNbt)) {
            return false;
        }
        double openRange = DomainAddonUtils.getOpenDomainRange((LevelAccessor)sl, (Entity)caster);
        double closedRange = Math.max(1.0, DomainAddonUtils.getActualDomainRadius((LevelAccessor)sl, targetNbt)) * 2.0;
        double maxLinkDistance = Math.max(24.0, Math.max(openRange, closedRange));
        return caster.distanceToSqr((Entity)target) <= maxLinkDistance * maxLinkDistance;
    }

    /**
     * Performs is valid wrap target for this mixin.
     * @param sl sl used by this method.
     * @param target entity involved in the current mixin operation.
     * @param incompleteCaster incomplete caster used by this method.
     * @return whether is valid wrap target is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isValidWrapTarget(ServerLevel sl, @Nullable LivingEntity target, LivingEntity incompleteCaster) {
        if (target == null || incompleteCaster == null || target == incompleteCaster) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        CompoundTag targetNbt = target.getPersistentData();
        if (targetNbt.getBoolean("Failed") || targetNbt.getBoolean("DomainDefeated")) {
            return false;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!target.hasEffect(domainEffect)) {
            return false;
        }
        if (DomainClashPenaltyMixin.jjkbrp$isIncompleteDomainState(target)) {
            return false;
        }
        return DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, incompleteCaster, incompleteCaster.getPersistentData(), target, targetNbt);
    }

    /**
     * Performs retarget erosion for this mixin.
     * @param caster entity involved in the current mixin operation.
     * @param sl sl used by this method.
     * @return the resulting retarget erosion value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    @Nullable
    private static UUID jjkbrp$retargetErosion(LivingEntity caster, ServerLevel sl) {
        double casterRange;
        CompoundTag casterNbt = caster.getPersistentData();
        UUID oldTargetUuid = null;
        String oldTargetUuidStr = casterNbt.getString("jjkbrp_erosion_target_uuid");
        if (!oldTargetUuidStr.isEmpty()) {
            try {
                oldTargetUuid = UUID.fromString(oldTargetUuidStr);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        if ((casterRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)sl, caster, casterNbt)) <= 0.0) {
            return null;
        }
        Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
        double searchRadius = Math.max(6.0, casterRange * 0.5 + 2.0);
        AABB searchBox = new AABB(casterCenter.x - searchRadius, casterCenter.y - searchRadius, casterCenter.z - searchRadius, casterCenter.x + searchRadius, casterCenter.y + searchRadius, casterCenter.z + searchRadius);
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != caster)) {
            double distance;
            if (!candidate.hasEffect(domainEffect) || oldTargetUuid != null && candidate.getUUID().equals(oldTargetUuid) || !DomainClashPenaltyMixin.jjkbrp$isValidErosionTarget(sl, candidate, caster) || (distance = candidate.distanceToSqr((Entity)caster)) >= closestDistance) continue;
            closest = candidate;
            closestDistance = distance;
        }
        return closest != null ? closest.getUUID() : null;
    }

    /**
     * Performs retarget wrap for this mixin.
     * @param caster entity involved in the current mixin operation.
     * @param sl sl used by this method.
     * @return the resulting retarget wrap value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    @Nullable
    private static UUID jjkbrp$retargetWrap(LivingEntity caster, ServerLevel sl) {
        double casterRange;
        CompoundTag casterNbt = caster.getPersistentData();
        UUID oldTargetUuid = null;
        String oldTargetUuidStr = casterNbt.getString("jjkbrp_incomplete_wrap_target_uuid");
        if (!oldTargetUuidStr.isEmpty()) {
            try {
                oldTargetUuid = UUID.fromString(oldTargetUuidStr);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        if ((casterRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)sl, caster, casterNbt)) <= 0.0) {
            return null;
        }
        Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
        double searchRadius = Math.max(6.0, casterRange * 0.5 + 2.0);
        AABB searchBox = new AABB(casterCenter.x - searchRadius, casterCenter.y - searchRadius, casterCenter.z - searchRadius, casterCenter.x + searchRadius, casterCenter.y + searchRadius, casterCenter.z + searchRadius);
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != caster)) {
            double distance;
            if (!candidate.hasEffect(domainEffect) || oldTargetUuid != null && candidate.getUUID().equals(oldTargetUuid) || !DomainClashPenaltyMixin.jjkbrp$isValidWrapTarget(sl, candidate, caster) || (distance = candidate.distanceToSqr((Entity)caster)) >= closestDistance) continue;
            closest = candidate;
            closestDistance = distance;
        }
        return closest != null ? closest.getUUID() : null;
    }


    // ===== TARGET TRACKING AND CLEANUP =====
    /**
     * Performs assign erosion target for this mixin.
     * @param openCaster open caster used by this method.
     * @param openNbt open nbt used by this method.
     * @param oldTarget old target used by this method.
     * @param newTarget new target used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$assignErosionTarget(LivingEntity openCaster, CompoundTag openNbt, @Nullable LivingEntity oldTarget, LivingEntity newTarget) {
        if (oldTarget != null && oldTarget != newTarget) {
            DomainClashPenaltyMixin.jjkbrp$cleanupDefenderState(oldTarget.getPersistentData());
        }
        CompoundTag newTargetNbt = newTarget.getPersistentData();
        String newTargetUuid = newTarget.getStringUUID();
        boolean sameTarget = newTargetUuid.equals(openNbt.getString("jjkbrp_erosion_target_uuid"));
        openNbt.putBoolean("jjkbrp_is_eroding_barrier", true);
        openNbt.putString("jjkbrp_erosion_target_uuid", newTargetUuid);
        newTargetNbt.putBoolean("jjkbrp_barrier_under_attack", true);
        newTargetNbt.putString("jjkbrp_open_attacker_uuid", openCaster.getStringUUID());
        if (!sameTarget || !newTargetNbt.contains("jjkbrp_barrier_erosion_total")) {
            newTargetNbt.putDouble("jjkbrp_barrier_erosion_total", 0.0);
        }
        if (!newTargetNbt.contains("jjkbrp_barrier_refinement")) {
            newTargetNbt.putDouble("jjkbrp_barrier_refinement", 0.5);
        }
    }

    /**
     * Performs assign wrap target for this mixin.
     * @param incompleteCaster incomplete caster used by this method.
     * @param incompleteNbt incomplete nbt used by this method.
     * @param oldTarget old target used by this method.
     * @param newTarget new target used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$assignWrapTarget(LivingEntity incompleteCaster, CompoundTag incompleteNbt, @Nullable LivingEntity oldTarget, LivingEntity newTarget) {
        if (oldTarget != null && oldTarget != newTarget) {
            DomainClashPenaltyMixin.jjkbrp$cleanupWrappedTargetState(oldTarget.getPersistentData());
        }
        incompleteNbt.putBoolean("jjkbrp_incomplete_wrap_active", true);
        incompleteNbt.putString("jjkbrp_incomplete_wrap_target_uuid", newTarget.getStringUUID());
        CompoundTag newTargetNbt = newTarget.getPersistentData();
        newTargetNbt.putBoolean("jjkbrp_wrapped_by_incomplete", true);
        newTargetNbt.putString("jjkbrp_incomplete_wrapper_uuid", incompleteCaster.getStringUUID());
    }

    /**
     * Performs cleanup attacker state for this mixin.
     * @param attackerNbt attacker nbt used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$cleanupAttackerState(CompoundTag attackerNbt) {
        attackerNbt.remove("jjkbrp_is_eroding_barrier");
        attackerNbt.remove("jjkbrp_erosion_target_uuid");
    }

    /**
     * Performs cleanup defender state for this mixin.
     * @param defenderNbt defender nbt used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$cleanupDefenderState(CompoundTag defenderNbt) {
        defenderNbt.remove("jjkbrp_barrier_under_attack");
        defenderNbt.remove("jjkbrp_open_attacker_uuid");
        defenderNbt.remove("jjkbrp_barrier_erosion_total");
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
     * Performs cleanup incomplete wrap state for this mixin.
     * @param incompleteNbt incomplete nbt used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$cleanupIncompleteWrapState(CompoundTag incompleteNbt) {
        incompleteNbt.remove("jjkbrp_incomplete_wrap_active");
        incompleteNbt.remove("jjkbrp_incomplete_wrap_target_uuid");
    }

    /**
     * Performs cleanup wrapped target state for this mixin.
     * @param wrappedNbt wrapped nbt used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$cleanupWrappedTargetState(CompoundTag wrappedNbt) {
        wrappedNbt.remove("jjkbrp_wrapped_by_incomplete");
        wrappedNbt.remove("jjkbrp_incomplete_wrapper_uuid");
    }

    /**
     * Refreshes mutual contact timestamps so both participants remain inside the recent-clash window.
     * @param sl sl used by this method.
     * @param caster entity involved in the current mixin operation.
     * @param casterNbt caster nbt used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$refreshMutualClashContact(ServerLevel sl, LivingEntity caster, CompoundTag casterNbt) {
        if (sl == null || caster == null || casterNbt == null) {
            return;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!caster.hasEffect(domainEffect) && !DomainAddonUtils.isDomainBuildOrActive(sl, caster)) {
            return;
        }
        if (casterNbt.getBoolean("Failed") || casterNbt.getBoolean("DomainDefeated")) {
            return;
        }
        double casterRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)sl, caster, casterNbt);
        if (casterRange <= 0.0) {
            return;
        }
        Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
        double searchRadius = Math.max(6.0, casterRange * 0.5 + 2.0);
        AABB searchBox = new AABB(casterCenter.x - searchRadius, casterCenter.y - searchRadius, casterCenter.z - searchRadius, casterCenter.x + searchRadius, casterCenter.y + searchRadius, casterCenter.z + searchRadius);
        long tick = sl.getGameTime();
        for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != caster)) {
            CompoundTag otherNbt = other.getPersistentData();
            if (otherNbt.getBoolean("Failed") || otherNbt.getBoolean("DomainDefeated") || !other.hasEffect(domainEffect) && !DomainAddonUtils.isDomainBuildOrActive(sl, other) || !DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, caster, casterNbt, other, otherNbt) || !DomainClashPenaltyMixin.jjkbrp$isWithinBaseClashWindow(sl, other, otherNbt, caster, casterNbt)) continue;
            casterNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
            otherNbt.putLong("jjkbrp_last_clash_contact_tick", tick);
            casterNbt.putString("jjkbrp_last_clash_opponent_uuid", other.getStringUUID());
            otherNbt.putString("jjkbrp_last_clash_opponent_uuid", caster.getStringUUID());
        }
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
        double dx = sourceCenter.x - targetBody.x;
        double dy = sourceCenter.y - targetBody.y;
        double dz = sourceCenter.z - targetBody.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double sourceRange = DomainClashPenaltyMixin.jjkbrp$baseClashRange((LevelAccessor)world, source, sourceNbt);
        if (sourceRange <= 0.0) {
            return false;
        }
        double threshold = Math.max(2.0, sourceRange * 0.5);
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

    /**
     * Performs is base startup open state for this mixin.
     * @param nbt persistent data container used by this helper.
     * @return whether is base startup open state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isBaseStartupOpenState(CompoundTag nbt) {
        int resolvedId;
        if (nbt == null) {
            return false;
        }
        if (!nbt.contains("cnt2") || nbt.getDouble("cnt2") <= 0.0) {
            return false;
        }
        if (nbt.getDouble("cnt7") <= 0.0 && !nbt.contains("x_pos_doma")) {
            return false;
        }
        double domainId = nbt.getDouble("select");
        if (domainId == 0.0) {
            domainId = nbt.getDouble("skill_domain");
        }
        if (domainId == 0.0) {
            domainId = nbt.getDouble("jjkbrp_domain_id_runtime");
        }
        return (resolvedId = (int)Math.round(domainId)) == 1 || resolvedId == 18;
    }
}
