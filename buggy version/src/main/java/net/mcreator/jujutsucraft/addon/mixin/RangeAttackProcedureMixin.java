package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.mcreator.jujutsucraft.addon.BlueRedPurpleNukeMod;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.entity.PurpleEntity;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.LogicAttackProcedure;
import net.mcreator.jujutsucraft.procedures.LogicBetrayalProcedure;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Combat mixin for `RangeAttackProcedure.execute()` that blocks incomplete-domain sure-hit attacks, temporarily scales domain radius for active range calculations, adjusts open-domain combat tuning, and records successful Black Flash procs after the base attack resolves.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={RangeAttackProcedure.class}, remap=false)
public class RangeAttackProcedureMixin {
    // Persistent-data key storing the last observed Zone effect duration so Black Flash procs can be detected on the next return pass.
    private static final String KEY_LAST_ZONE_DUR = "jjkbrp_last_zone_dur";
    // Persistent-data key storing the short Black Flash proc cooldown that suppresses immediate repeat boosts.
    private static final String KEY_BF_CD = "jjkbrp_bf_cd";
    // Persistent-data key used to cache the original `cnt6` value before the mixin suppresses or boosts it temporarily.
    private static final String KEY_CNT6_BACKUP = "jjkbrp_cnt6_backup";
    // Persistent-data flag indicating that `cnt6` was modified by this mixin and must be restored on return.
    private static final String KEY_CNT6_SUPPRESSED = "jjkbrp_cnt6_suppressed";
    // Persistent-data flag marking that the near-death cooldown reduction was already handled for the current Black Flash event.
    private static final String KEY_BF_ND_HANDLED = "jjkbrp_bf_nd_handled";
    // Persistent-data key storing the pre-scaled damage value so rank scaling can be safely reverted after each attack call.
    private static final String KEY_RANK_DAMAGE_BACKUP = "jjkbrp_rank_damage_backup";
    // Persistent-data flag marking that rank-based damage scaling was applied for the current attack execution.
    private static final String KEY_RANK_DAMAGE_APPLIED = "jjkbrp_rank_damage_applied";
    // Persistent-data counter storing how many Infinity-technique damage passes this spawned cast has already consumed.
    private static final String KEY_INFINITY_RANK_PASS_COUNT = "jjkbrp_inf_rank_pass_count";
    // Persistent-data key stored on each Blue victim to throttle repeated AI damage ticks against the same target.
    private static final String KEY_BLUE_LAST_HIT_TICK = "addon_blue_last_hit_tick";
    // Persistent-data key stored on each Red victim to throttle repeated AI damage ticks against the same target.
    private static final String KEY_RED_LAST_HIT_TICK = "addon_red_last_hit_tick";
    // Black Flash proc cooldown applied after a successful ranged proc, measured in ticks.
    private static final int BF_PROC_COOLDOWN_TICKS = 60;
    // Amount of near-death cooldown removed when a valid Black Flash proc is detected.
    private static final int BF_ND_CD_REDUCTION = 600;
    // Thread-local owner reference used to temporarily grant and then safely remove Purple-owner invulnerability around the redirected attack execution.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Player> JJKBRP$protectedOwner = new ThreadLocal();
    // Logger used for periodic domain-state diagnostics around ranged attacks.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();


    // ===== HEAD INJECTION =====
    /**
     * Runs at the head of `RangeAttackProcedure.execute()` to cancel incomplete-domain sure-hit attacks, temporarily scale active domain radius, protect Purple owners when needed, and apply open-domain Black Flash tuning before the base procedure executes.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkblueredpurple$boostBlackFlash(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Player ownerPlayer;
        boolean sourceHasDomain;
        if (entity == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        LivingEntity domainStateSource = RangeAttackProcedureMixin.jjkblueredpurple$resolveDomainStateSource(world, entity, data);
        CompoundTag domainStateData = domainStateSource != null ? domainStateSource.getPersistentData() : data;
        boolean entityOpen = RangeAttackProcedureMixin.jjkblueredpurple$isOpenDomainState(data);
        boolean sourceOpen = domainStateSource != null && DomainAddonUtils.isOpenDomainState(domainStateSource);
        boolean entityIncomplete = RangeAttackProcedureMixin.jjkblueredpurple$isIncompleteDomainState(data);
        boolean sourceIncomplete = domainStateSource != null && DomainAddonUtils.isIncompleteDomainState(domainStateSource);
        boolean bl = sourceHasDomain = domainStateSource != null && domainStateSource.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        if (data.getBoolean("DomainAttack") && domainStateSource != null && entity.tickCount % 20 == 0 && (!entityOpen && sourceOpen || !entityIncomplete && sourceIncomplete)) {
            LOGGER.debug("[GojoDomainDiag] RangeAttack state mismatch attackEntity={} source={} domainAttack={} entityOpen={} sourceOpen={} entityIncomplete={} sourceIncomplete={} sourceHasDomain={} damage={} range={} ownerUuidPresent={}", new Object[]{entity.getClass().getSimpleName(), domainStateSource.getName().getString(), data.getBoolean("DomainAttack"), entityOpen, sourceOpen, entityIncomplete, sourceIncomplete, sourceHasDomain, data.contains("Damage") ? data.getDouble("Damage") : -1.0, data.contains("Range") ? data.getDouble("Range") : -1.0, !data.getString("OWNER_UUID").isEmpty()});
        }
        // Incomplete domains intentionally lose sure-hit behavior, so the entire ranged attack procedure is cancelled before the base logic can fire.
        if (RangeAttackProcedureMixin.jjkblueredpurple$isIncompleteDomainAttack(world, entity, data)) {
            ci.cancel();
            return;
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$cancelBlueDamageCooldown(world, x, y, z, entity, data)) {
            ci.cancel();
            return;
        }
        // Apply rank-based scaling for addon RED/BLUE/PURPLE attacks while keeping each call idempotent across recursive execute paths.
        RangeAttackProcedureMixin.jjkblueredpurple$applyRankDamageScale(world, entity, data);
        JJKBRP$protectedOwner.remove();
        if (entity instanceof PurpleEntity && (ownerPlayer = RangeAttackProcedureMixin.jjkblueredpurple$resolvePlayerOwner(world, data.getString("OWNER_UUID"))) != null && RangeAttackProcedureMixin.jjkblueredpurple$isInfinityActive(ownerPlayer) && !ownerPlayer.isInvulnerable()) {
            ownerPlayer.setInvulnerable(true);
            JJKBRP$protectedOwner.set(ownerPlayer);
        }
        if (data.getBoolean("DomainAttack") && sourceOpen && data.getBoolean("jjkbrp_surehit_corrected")) {
            data.remove("jjkbrp_surehit_corrected");
        }
        int bfCd = data.getInt(KEY_BF_CD);
        // The addon suppresses or boosts `cnt6` only for the current execution window, then restores the original value on return.
        if (!data.getBoolean(KEY_CNT6_SUPPRESSED)) {
            double liveCnt6 = data.getDouble("cnt6");
            double domainBonus = Math.max(0.0, data.getDouble("jjkbrp_domain_bf_bonus"));
            if (bfCd > 0) {
                data.putDouble(KEY_CNT6_BACKUP, liveCnt6);
                data.putDouble("cnt6", 0.0);
                data.putBoolean(KEY_CNT6_SUPPRESSED, true);
            } else if (domainBonus > 0.0) {
                data.putDouble(KEY_CNT6_BACKUP, liveCnt6);
                data.putDouble("cnt6", liveCnt6 + domainBonus);
                data.putBoolean(KEY_CNT6_SUPPRESSED, true);
            }
        }
    }


    // ===== RETURN INJECTION =====
    /**
     * Runs after `RangeAttackProcedure.execute()` returns to restore temporary state, detect Black Flash procs from Zone duration changes, increment addon counters, and apply the short proc cooldown.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkblueredpurple$resetBlackFlash(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Player protectedOwner;
        if ((protectedOwner = JJKBRP$protectedOwner.get()) != null) {
            protectedOwner.setInvulnerable(false);
            JJKBRP$protectedOwner.remove();
        }
        if (entity == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(KEY_CNT6_SUPPRESSED)) {
            data.putDouble("cnt6", data.getDouble(KEY_CNT6_BACKUP));
            data.remove(KEY_CNT6_BACKUP);
            data.putBoolean(KEY_CNT6_SUPPRESSED, false);
        }
        if (data.getBoolean(KEY_RANK_DAMAGE_APPLIED)) {
            if (data.contains(KEY_RANK_DAMAGE_BACKUP)) {
                data.putDouble("Damage", data.getDouble(KEY_RANK_DAMAGE_BACKUP));
            }
            data.remove(KEY_RANK_DAMAGE_BACKUP);
            data.putBoolean(KEY_RANK_DAMAGE_APPLIED, false);
        }
        if (entity instanceof LivingEntity) {
            boolean bfJustProcced;
            LivingEntity le = (LivingEntity)entity;
            MobEffectInstance zone = le.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
            int dur = zone != null ? zone.getDuration() : 0;
            int lastDur = data.getInt(KEY_LAST_ZONE_DUR);
            data.putInt(KEY_LAST_ZONE_DUR, dur);
            // A fresh jump into the near-6000 Zone duration band is treated as the reliable Black Flash proc signal for this ranged path.
            boolean bl = bfJustProcced = dur >= 5990 && dur > lastDur;
            if (bfJustProcced) {
                int totalHits = data.getInt("addon_bf_total_hits");
                // Successful procs permanently increase the tracked Black Flash hit counter used by other addon systems.
                data.putInt("addon_bf_total_hits", totalHits + 1);
                int ndCd = data.getInt("jjkbrp_near_death_cd");
                if (ndCd > 0) {
                    data.putInt("jjkbrp_near_death_cd", Math.max(0, ndCd - 600));
                }
                // Apply the short ranged Black Flash cooldown so the temporary proc boost cannot trigger again immediately.
                data.putInt(KEY_BF_CD, 60);
                data.putBoolean(KEY_BF_ND_HANDLED, true);
                data.putBoolean("jjkbrp_bf_regen_boost", true);
            }
        }
    }



    // ===== DOMAIN STATE HELPERS =====
    /**
     * Checks whether the current ranged attack belongs to an incomplete domain and therefore must skip sure-hit handling entirely.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @return whether is incomplete domain attack is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkblueredpurple$isIncompleteDomainAttack(LevelAccessor world, Entity entity, CompoundTag data) {
        if (data == null || !data.getBoolean("DomainAttack")) {
            return false;
        }
        LivingEntity domainStateSource = RangeAttackProcedureMixin.jjkblueredpurple$resolveDomainStateSource(world, entity, data);
        return domainStateSource != null && domainStateSource.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) && DomainAddonUtils.isIncompleteDomainState(domainStateSource);
    }

    /**
     * Checks raw persistent-data flags that mark an entity or spawned attack as being in incomplete-domain form.
     * @param data persistent data container used by this helper.
     * @return whether is incomplete domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkblueredpurple$isIncompleteDomainState(CompoundTag data) {
        if (data == null) {
            return false;
        }
        if (data.getBoolean("jjkbrp_incomplete_form_active")) {
            return true;
        }
        if (data.contains("jjkbrp_domain_form_cast_locked") && data.getInt("jjkbrp_domain_form_cast_locked") == 0) {
            return true;
        }
        return data.contains("jjkbrp_domain_form_effective") && data.getInt("jjkbrp_domain_form_effective") == 0;
    }

    /**
     * Checks raw persistent-data flags that mark an entity or spawned attack as being in open-domain form.
     * @param data persistent data container used by this helper.
     * @return whether is open domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkblueredpurple$isOpenDomainState(CompoundTag data) {
        if (data == null) {
            return false;
        }
        if (data.getBoolean("jjkbrp_open_form_active")) {
            return true;
        }
        if (data.contains("jjkbrp_domain_form_cast_locked") && data.getInt("jjkbrp_domain_form_cast_locked") == 2) {
            return true;
        }
        return data.contains("jjkbrp_domain_form_effective") && data.getInt("jjkbrp_domain_form_effective") == 2;
    }

    /**
     * Resolves the living entity whose domain-state flags should control the current ranged attack instance.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @return the resulting resolve domain state source value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static LivingEntity jjkblueredpurple$resolveDomainStateSource(LevelAccessor world, Entity entity, CompoundTag data) {
        LivingEntity living;
        LivingEntity owner = DomainAddonUtils.resolveOwnerEntity(world, entity);
        if (owner != null) {
            return owner;
        }
        return entity instanceof LivingEntity ? (living = (LivingEntity)entity) : null;
    }

    /**
     * Cancels direct Blue AI damage when the same victim would be hit again before the short anti-multihit cooldown expires.
     * Red no longer uses this coarse path because OG crouch Red still needs its repeated `RangeAttackProcedure.execute()` calls for terrain and world behavior.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @return whether the current Blue damage tick should be cancelled.
     */
    @Unique
    private static boolean jjkblueredpurple$cancelBlueDamageCooldown(LevelAccessor world, double x, double y, double z, Entity entity, CompoundTag data) {
        if (!(world instanceof ServerLevel) || data == null || data.getBoolean("DomainAttack") || !(entity instanceof BlueEntity)) {
            return false;
        }
        double range = data.getDouble("Range");
        if (range <= 0.0) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        long currentGameTime = serverLevel.getGameTime();
        List<LivingEntity> nearbyTargets = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(x, y, z, x, y, z).inflate(range / 2.0), target -> target.isAlive() && target != entity);
        boolean foundTarget = false;
        for (LivingEntity target : nearbyTargets) {
            boolean betrayal = LogicBetrayalProcedure.execute(entity, target);
            if (!LogicAttackProcedure.execute(world, entity, target) && !betrayal) {
                continue;
            }
            foundTarget = true;
            CompoundTag targetData = target.getPersistentData();
            long lastHitTick = targetData.getLong(KEY_BLUE_LAST_HIT_TICK);
            if (targetData.contains(KEY_BLUE_LAST_HIT_TICK) && currentGameTime - lastHitTick < (long)BlueRedPurpleNukeMod.BLUE_DAMAGE_COOLDOWN_TICKS) {
                return true;
            }
        }
        if (!foundTarget) {
            return false;
        }
        for (LivingEntity target : nearbyTargets) {
            boolean betrayal = LogicBetrayalProcedure.execute(entity, target);
            if (!LogicAttackProcedure.execute(world, entity, target) && !betrayal) {
                continue;
            }
            target.getPersistentData().putLong(KEY_BLUE_LAST_HIT_TICK, currentGameTime);
        }
        return false;
    }

    /**
     * Redirects Red's light Black Flash probe hit so cooldown suppression can be applied per target without cancelling the rest of the original procedure.
     */
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal=0), remap=false)
    private static boolean jjkblueredpurple$redirectProbeDamage(Entity target, DamageSource source, float amount, LevelAccessor world, double x, double y, double z, Entity entity) {
        return RangeAttackProcedureMixin.jjkblueredpurple$applyRedirectedRangeDamage(target, source, amount, world, entity, false);
    }

    /**
     * Redirects the real ranged damage call so Red can keep OG block destruction and movement while repeated entity damage is suppressed per victim.
     */
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal=1), remap=false)
    private static boolean jjkblueredpurple$redirectFinalDamage(Entity target, DamageSource source, float amount, LevelAccessor world, double x, double y, double z, Entity entity) {
        return RangeAttackProcedureMixin.jjkblueredpurple$applyRedirectedRangeDamage(target, source, amount, world, entity, true);
    }

    /**
     * Applies per-target Red cooldown gating at the actual hurt call site so OG crouch Red can continue running world/block logic every tick while entities only take the first valid hit inside the cooldown window.
     */
    @Unique
    private static boolean jjkblueredpurple$applyRedirectedRangeDamage(Entity target, DamageSource source, float amount, LevelAccessor world, Entity attacker, boolean commitCooldown) {
        if (!(target instanceof LivingEntity livingTarget)) {
            return target.hurt(source, amount);
        }
        if (!(attacker instanceof RedEntity) || !(world instanceof ServerLevel serverLevel)) {
            return target.hurt(source, amount);
        }
        CompoundTag attackerData = attacker.getPersistentData();
        if (attackerData.getBoolean("DomainAttack")) {
            return target.hurt(source, amount);
        }
        boolean betrayal = LogicBetrayalProcedure.execute(attacker, livingTarget);
        if (!LogicAttackProcedure.execute(world, attacker, livingTarget) && !betrayal) {
            return target.hurt(source, amount);
        }
        CompoundTag targetData = livingTarget.getPersistentData();
        long lastHitTick = targetData.getLong(KEY_RED_LAST_HIT_TICK);
        long currentGameTime = serverLevel.getGameTime();
        if (targetData.contains(KEY_RED_LAST_HIT_TICK) && currentGameTime - lastHitTick < (long)BlueRedPurpleNukeMod.RED_DAMAGE_COOLDOWN_TICKS) {
            return false;
        }
        boolean hurt = target.hurt(source, amount);
        if (commitCooldown && hurt) {
            targetData.putLong(KEY_RED_LAST_HIT_TICK, currentGameTime);
        }
        return hurt;
    }

    /**
     * Applies rank-based damage scaling for addon RED/BLUE/PURPLE paths that route through RangeAttackProcedure.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     */
    @Unique
    private static void jjkblueredpurple$applyRankDamageScale(LevelAccessor world, Entity entity, CompoundTag data) {
        if (data == null || entity == null || data.getBoolean(KEY_RANK_DAMAGE_APPLIED) || !data.contains("Damage")) {
            return;
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$isOgCrouchLowChargeRed(entity, data)) {
            return;
        }
        double ownerRankScale = 1.0;
        double rankScale = 1.0;
        if (entity instanceof RedEntity || entity instanceof BlueEntity || entity instanceof PurpleEntity && data.getBoolean("addon_purple_fusion")) {
            ownerRankScale = RangeAttackProcedureMixin.jjkblueredpurple$getOwnerRankDamageScale(world, data.getString("OWNER_UUID"));
            rankScale = RangeAttackProcedureMixin.jjkblueredpurple$getAdjustedInfinityTechniqueRankScale(entity, data, ownerRankScale);
        }
        if (rankScale <= 1.0) {
            return;
        }
        data.putDouble(KEY_RANK_DAMAGE_BACKUP, data.getDouble("Damage"));
        data.putDouble("Damage", data.getDouble("Damage") * rankScale);
        data.putBoolean(KEY_RANK_DAMAGE_APPLIED, true);
    }

    /**
     * Detects the original crouch low-charge Red route so its base AIRed damage stays unmodified by addon owner-rank scaling.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @return whether the current Red attack is the OG crouch low-charge route.
     */
    @Unique
    private static boolean jjkblueredpurple$isOgCrouchLowChargeRed(Entity entity, CompoundTag data) {
        if (!(entity instanceof RedEntity) || data == null || !data.getBoolean("addon_red_use_og")) {
            return false;
        }
        boolean routeLocked = data.getBoolean("addon_red_route_mode_locked") || data.getBoolean("addon_red_crouch_cast_seen");
        if (!routeLocked) {
            return false;
        }
        double routedCharge = Math.max(Math.max(data.getDouble("cnt6"), data.getDouble("addon_red_route_charge")), Math.max(data.getDouble("addon_red_charge_cached"), data.getDouble("addon_red_charge_used")));
        return routedCharge < 5.0;
    }

    /**
     * Resolves Infinity-technique rank scaling for RangeAttackProcedure paths so Blue uses its damped per-pass helper, Red shift-finisher uses a narrower finisher-only scale, and Purple fusion keeps its existing direct owner-rank behavior.
     * @param entity entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param ownerRankScale raw owner-rank multiplier resolved for the current cast.
     * @return adjusted rank multiplier suitable for the current attack path.
     */
     @Unique
     private static double jjkblueredpurple$getAdjustedInfinityTechniqueRankScale(Entity entity, CompoundTag data, double ownerRankScale) {
         int passIndex = RangeAttackProcedureMixin.jjkblueredpurple$nextInfinityTechniquePass(data);
         if (entity instanceof BlueEntity) {
             return BlueRedPurpleNukeMod.getBlueRankScaleForPass(ownerRankScale, passIndex);
         }
         if (entity instanceof RedEntity) {
             if (data.getBoolean("addon_red_shift_finisher_damage_active")) {
                 return BlueRedPurpleNukeMod.getRedShiftFinisherRankScaleForPass(ownerRankScale, passIndex);
             }
             return BlueRedPurpleNukeMod.getDirectOwnerRankScaleForPass(ownerRankScale, passIndex);
         }
         if (entity instanceof PurpleEntity && data.getBoolean("addon_purple_fusion")) {
             return BlueRedPurpleNukeMod.getDirectOwnerRankScaleForPass(ownerRankScale, passIndex);
         }
         return ownerRankScale;
     }

    /**
     * Advances the per-cast Infinity rank-pass counter so multi-hit skills cannot reuse the full rank multiplier on every `RangeAttackProcedure.execute()` call.
     * @param data persistent data container used by this helper.
     * @return one-based rank pass index for the current Infinity cast.
     */
    @Unique
    private static int jjkblueredpurple$nextInfinityTechniquePass(CompoundTag data) {
        int passIndex = Math.max(0, data.getInt(KEY_INFINITY_RANK_PASS_COUNT)) + 1;
        data.putInt(KEY_INFINITY_RANK_PASS_COUNT, passIndex);
        return passIndex;
    }

    /**
     * Resolves rank damage scaling by mirroring the base mod's damage-fix behavior on the owner.
     * @param world world access used by the current mixin callback.
     * @param ownerUUID identifier used to resolve runtime state for this operation.
     * @return rank multiplier resolved for the current owner.
     */
    @Unique
    private static double jjkblueredpurple$getOwnerRankDamageScale(LevelAccessor world, String ownerUUID) {
        Player owner = RangeAttackProcedureMixin.jjkblueredpurple$resolvePlayerOwner(world, ownerUUID);
        if (!(owner instanceof ServerPlayer)) {
            return 1.0;
        }
        ServerPlayer player = (ServerPlayer)owner;
        return RangeAttackProcedureMixin.jjkblueredpurple$getDamageFixEquivalentScale(player);
    }

    /**
     * Converts a player level into the same baseline damage gain shape used by base Strength-driven combat.
     * @param playerLevel level value used by this helper.
     * @return converted baseline damage scale.
     */
    @Unique
    private static double jjkblueredpurple$getScaleFromPlayerLevel(double playerLevel) {
        if (playerLevel <= 0.0) {
            return 1.0;
        }
        double level = Math.max(playerLevel - 1.0, 0.0);
        double levelPower = Math.round(level);
        if (levelPower < 3.0) {
            levelPower = Math.min(levelPower, 1.0);
        }
        // Base formula: Damage *= 1 + ((1 + StrengthAmp) * 0.333), where StrengthAmp is derived from level.
        return Math.max(1.0, 1.0 + (1.0 + levelPower) * 0.333);
    }

    /**
     * Pulls owner `PlayerLevel` from capability data and converts it to base-like damage scaling.
     * @param player entity involved in the current mixin operation.
     * @return capability-derived damage scale.
     */
    @Unique
    private static double jjkblueredpurple$getPlayerLevelDamageScale(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables vars = (JujutsucraftModVariables.PlayerVariables)player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(null);
        if (vars == null) {
            return 1.0;
        }
        return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(vars.PlayerLevel);
    }

    /**
     * Uses completed grade advancements as a fallback baseline when capability data is stale.
     * @param player entity involved in the current mixin operation.
     * @return advancement-derived baseline scale.
     */
    @Unique
    private static double jjkblueredpurple$getAdvancementRankDamageScale(ServerPlayer player) {
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_special")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(20.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_1")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(13.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_1_semi")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(11.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_2")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(9.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_2_semi")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(7.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_3")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(4.0);
        }
        if (RangeAttackProcedureMixin.jjkblueredpurple$hasAdvancement(player, "jujutsucraft:sorcerer_grade_4")) {
            return RangeAttackProcedureMixin.jjkblueredpurple$getScaleFromPlayerLevel(2.0);
        }
        return 1.0;
    }

    /**
     * Reproduces the core DamageFix multipliers from the owner's current effects and attack attribute.
     * @param player entity involved in the current mixin operation.
     * @return live damage-fix equivalent scale.
     */
    @Unique
    private static double jjkblueredpurple$getDamageFixEquivalentScale(ServerPlayer player) {
        double strengthLevel = 0.0;
        if (player.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            strengthLevel += player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.333;
        }
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (strength != null) {
            strengthLevel += 1.0 + strength.getAmplifier();
        }
        MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
        if (weakness != null) {
            strengthLevel -= 1.0 + weakness.getAmplifier();
        }
        double scale = 1.0 + strengthLevel * 0.333;
        MobEffectInstance zone = player.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
        if (zone != null) {
            scale *= 1.2 + 0.1 * zone.getAmplifier();
        }
        return Math.max(scale, 1.0);
    }

    /**
     * Checks whether the owner has completed a specific advancement.
     * @param player entity involved in the current mixin operation.
     * @param advancementId identifier used to resolve runtime state for this operation.
     * @return true when the advancement is completed; otherwise false.
     */
    @Unique
    private static boolean jjkblueredpurple$hasAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Resolves the owning player from the stored UUID so owner-only protections can be applied safely.
     * @param world world access used by the current mixin callback.
     * @param ownerUUID identifier used to resolve runtime state for this operation.
     * @return the resulting resolve player owner value.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static Player jjkblueredpurple$resolvePlayerOwner(LevelAccessor world, String ownerUUID) {
        if (ownerUUID == null || ownerUUID.isEmpty() || !(world instanceof ServerLevel)) {
            return null;
        }
        ServerLevel sl = (ServerLevel)world;
        try {
            UUID uuid = UUID.fromString(ownerUUID);
            Entity ownerEntity = sl.getEntity(uuid);
            if (ownerEntity instanceof Player) {
                Player player = (Player)ownerEntity;
                return player;
            }
            return sl.getServer().getPlayerList().getPlayer(uuid);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Checks whether the resolved player currently has the Infinity state that should protect them during Purple handling.
     * @param player entity involved in the current mixin operation.
     * @return whether is infinity active is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkblueredpurple$isInfinityActive(Player player) {
        return player != null && (player.getPersistentData().getBoolean("infinity") || player.hasEffect((MobEffect)JujutsucraftModMobEffects.INFINITY_EFFECT.get()));
    }
}
