package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.PurpleEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    // Black Flash proc cooldown applied after a successful ranged proc, measured in ticks.
    private static final int BF_PROC_COOLDOWN_TICKS = 60;
    // Amount of near-death cooldown removed when a valid Black Flash proc is detected.
    private static final int BF_ND_CD_REDUCTION = 600;
    // Thread-local owner reference used to temporarily grant and then safely remove Purple-owner invulnerability around the redirected attack execution.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Player> JJKBRP$protectedOwner = new ThreadLocal();
    // Thread-local backup of the global domain radius so the original value can be restored after the attack finishes.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Double> JJKBRP$scaledDomainRadiusOriginal = new ThreadLocal();
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
        // Temporarily swap the shared domain radius so all downstream range checks operate on the mastery-scaled radius instead of the global default.
        RangeAttackProcedureMixin.jjkblueredpurple$scaleDomainAttackRadius(world, entity);
        JJKBRP$protectedOwner.remove();
        if (entity instanceof PurpleEntity && (ownerPlayer = RangeAttackProcedureMixin.jjkblueredpurple$resolvePlayerOwner(world, data.getString("OWNER_UUID"))) != null && RangeAttackProcedureMixin.jjkblueredpurple$isInfinityActive(ownerPlayer) && !ownerPlayer.isInvulnerable()) {
            ownerPlayer.setInvulnerable(true);
            JJKBRP$protectedOwner.set(ownerPlayer);
        }
        // Open domains receive their extra sure-hit damage, range, and cursed-energy drain tuning here before the original procedure consumes those values.
        if (data.getBoolean("DomainAttack") && sourceOpen) {
            double surehitMul = domainStateData.getDouble("jjkbrp_open_surehit_multiplier");
            double ceMul = domainStateData.getDouble("jjkbrp_open_ce_drain_multiplier");
            if (surehitMul <= 0.0) {
                surehitMul = 1.0;
            }
            if (ceMul <= 0.0) {
                ceMul = 1.0;
            }
            int domainId = (int)Math.round(domainStateData.getDouble("jjkbrp_domain_id_runtime"));
            double rangeMul = Math.max(0.7, Math.min(1.35, surehitMul));
            if (domainId == 1 || domainId == 15) {
                rangeMul = Math.min(rangeMul, 1.0);
            }
            if (data.contains("Damage")) {
                data.putDouble("Damage", data.getDouble("Damage") * surehitMul);
            }
            if (data.contains("Range")) {
                data.putDouble("Range", data.getDouble("Range") * rangeMul);
            }
            if (entity instanceof Player) {
                Player p = (Player)entity;
                double finalCeMul = ceMul;
                p.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY).ifPresent(vars -> {
                    vars.PlayerCursePowerChange -= 10.0 * (finalCeMul - 1.0);
                    vars.syncPlayerVariables((Entity)p);
                });
            }
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
        Double originalRadius = JJKBRP$scaledDomainRadiusOriginal.get();
        if (originalRadius != null) {
            JJKBRP$scaledDomainRadiusOriginal.remove();
            try {
                JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius = originalRadius;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
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


    // ===== RUNTIME RADIUS SUPPORT =====
    /**
     * Temporarily swaps the shared domain radius with the caster's actual mastery-scaled radius so range-based attack logic sees the correct value during execution.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkblueredpurple$scaleDomainAttackRadius(LevelAccessor world, Entity entity) {
        JJKBRP$scaledDomainRadiusOriginal.remove();
        if (world.isClientSide() || entity == null) {
            return;
        }
        LivingEntity radiusSource = DomainAddonUtils.resolveOwnerEntity(world, entity);
        if (radiusSource == null && entity instanceof LivingEntity) {
            LivingEntity living;
            radiusSource = living = (LivingEntity)entity;
        }
        if (radiusSource == null || !DomainAddonUtils.hasActiveDomainExpansion(radiusSource)) {
            return;
        }
        CompoundTag radiusNbt = radiusSource.getPersistentData();
        if (!radiusNbt.contains("jjkbrp_base_domain_radius")) {
            return;
        }
        if (Math.abs(radiusNbt.getDouble("jjkbrp_radius_multiplier") - 1.0) < 1.0E-4) {
            return;
        }
        try {
            JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
            double original = mapVars.DomainExpansionRadius;
            double scaled = DomainAddonUtils.getActualDomainRadius(world, radiusNbt);
            if (Math.abs(original - scaled) < 1.0E-4) {
                return;
            }
            JJKBRP$scaledDomainRadiusOriginal.set(original);
            mapVars.DomainExpansionRadius = scaled;
        }
        catch (Exception exception) {
            // empty catch block
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
        if (data.getBoolean("jjkbrp_incomplete_session_active")) {
            return true;
        }
        if (data.contains("jjkbrp_domain_form_cast_locked") && data.getInt("jjkbrp_domain_form_cast_locked") == 0) {
            return true;
        }
        if (data.contains("cnt2") && data.getDouble("cnt2") < 0.0) {
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
        if (data.contains("jjkbrp_domain_form_effective") && data.getInt("jjkbrp_domain_form_effective") == 2) {
            return true;
        }
        return data.contains("cnt2") && data.getDouble("cnt2") > 0.0;
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
