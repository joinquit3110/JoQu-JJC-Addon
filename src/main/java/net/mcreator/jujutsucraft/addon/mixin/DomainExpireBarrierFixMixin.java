package net.mcreator.jujutsucraft.addon.mixin;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModEntities;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionEffectExpiresProcedure;
import net.mcreator.jujutsucraft.procedures.JujutsuBarrierUpdateTickProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Domain-expiry cleanup mixin for `DomainExpansionEffectExpiresProcedure.execute()` that preserves the right runtime state during failure checks, signals cleanup entities to break, schedules restore sweeps, and removes addon runtime keys after the domain ends.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionEffectExpiresProcedure.class}, remap=false)
public class DomainExpireBarrierFixMixin {
    /**
     * Runs before the base expiry procedure to preserve the runtime state needed by addon cleanup while temporarily disabling failure flags that would skip restoration.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$ensureBarrierCleanup(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity == null) {
            return;
        }
        if (world.isClientSide()) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean("jjkbrp_preserve_domain_runtime", nbt.getBoolean("Cover"));
        nbt.putBoolean("jjkbrp_was_failed", nbt.getBoolean("Failed"));
        nbt.putBoolean("jjkbrp_was_domain_defeated", nbt.getBoolean("DomainDefeated"));
        // Temporarily suppress failure flags so the addon cleanup pass can still schedule restoration even when the base logic marks the expiry as failed.
        nbt.putBoolean("Failed", false);
        nbt.putBoolean("DomainDefeated", false);
    }

    /**
     * Runs after the base expiry procedure to wake or spawn a cleanup entity, schedule delayed restoration, clear addon runtime keys, and remove open-domain state.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$directBreakSignalAndDurationFix(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        double cz;
        double cy;
        double cx;
        if (entity == null) {
            return;
        }
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)world;
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        CompoundTag nbt = caster.getPersistentData();
        boolean preserveRuntimeState = nbt.getBoolean("jjkbrp_preserve_domain_runtime");
        if (preserveRuntimeState) {
            nbt.remove("jjkbrp_preserve_domain_runtime");
            return;
        }
        boolean hadOpenForm = nbt.getBoolean("jjkbrp_open_form_active");
        double finalRestoreRadius = DomainExpireBarrierFixMixin.jjkbrp$resolveEffectiveRadius((LevelAccessor)sl, nbt);
        if (nbt.contains("x_pos_doma")) {
            cx = nbt.getDouble("x_pos_doma");
            cy = nbt.getDouble("y_pos_doma");
            cz = nbt.getDouble("z_pos_doma");
        } else {
            cx = caster.getX();
            cy = caster.getY();
            cz = caster.getZ();
        }
        List<DomainExpansionEntityEntity> entities = sl.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(cx - 1.5, cy - 1.5, cz - 1.5, cx + 1.5, cy + 1.5, cz + 1.5), e -> true);
        if (entities.isEmpty()) {
            double searchRange = 3.0;
            if (nbt.contains("jjkbrp_base_domain_radius")) {
                searchRange = nbt.getDouble("jjkbrp_base_domain_radius");
                double radiusMul = nbt.getDouble("jjkbrp_radius_multiplier");
                if (Math.abs(radiusMul) < 1.0E-4) {
                    radiusMul = 1.0;
                }
                searchRange *= Math.max(0.5, radiusMul);
            }
            entities = sl.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(cx - searchRange, cy - searchRange, cz - searchRange, cx + searchRange, cy + searchRange, cz + searchRange), e -> true);
        }
        if (!entities.isEmpty()) {
            DomainExpansionEntityEntity domainEnt = entities.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(cx, cy, cz))).orElse(null);
            if (domainEnt != null) {
                // Wake the cleanup entity directly so barrier restoration begins even if the original expiry path missed the normal break signal.
                domainEnt.getPersistentData().putBoolean("Break", true);
                domainEnt.getPersistentData().putDouble("cnt_life2", 0.0);
                CompoundTag domainNBT = domainEnt.getPersistentData();
                domainNBT.putDouble("range", finalRestoreRadius);
            }
        } else {
            double searchCx = cx;
            double searchCy = cy;
            double searchCz = cz;
            double finalSearchRange = Math.max(1.0, finalRestoreRadius);
            // If no cleanup entity is available immediately, retry a few ticks later and spawn a fallback cleanup entity if necessary.
            sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + 5, () -> {
                DomainExpansionEntityEntity spawned;
                List<DomainExpansionEntityEntity> retryEntities = sl.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(searchCx - finalSearchRange, searchCy - finalSearchRange, searchCz - finalSearchRange, searchCx + finalSearchRange, searchCy + finalSearchRange, searchCz + finalSearchRange), e -> true);
                if (retryEntities.isEmpty() && (spawned = DomainExpireBarrierFixMixin.jjkbrp$spawnCleanupEntity(sl, searchCx, searchCy, searchCz, finalSearchRange)) != null) {
                    retryEntities = List.of(spawned);
                }
                for (DomainExpansionEntityEntity ent : retryEntities) {
                    ent.getPersistentData().putBoolean("Break", true);
                    ent.getPersistentData().putDouble("cnt_life2", 0.0);
                    ent.getPersistentData().putDouble("range", finalSearchRange);
                }
            }));
        }
        if (nbt.getBoolean("jjkbrp_open_form_active")) {
            nbt.putBoolean("jjkbrp_open_form_active", false);
        }
        if (entity instanceof LivingEntity) {
            LivingEntity le = (LivingEntity)entity;
            DomainAddonUtils.cleanupBFBoost(le);
        }
        // Remove all runtime-only open, incomplete, and clash keys so the next domain cast starts from a clean persistent-data state.
        nbt.remove("jjkbrp_open_darkness_stage");
        nbt.remove("jjkbrp_open_darkness_start");
        nbt.remove("jjkbrp_open_domain_cx");
        nbt.remove("jjkbrp_open_domain_cy");
        nbt.remove("jjkbrp_open_domain_cz");
        nbt.remove("jjkbrp_open_center_locked");
        nbt.remove("jjkbrp_opening_prefire_fired");
        nbt.remove("jjkbrp_domain_grace_ticks");
        nbt.remove("jjkbrp_domain_just_opened");
        nbt.remove("jjkbrp_open_cancelled");
        nbt.remove("jjkbrp_domain_archetype");
        nbt.remove("jjkbrp_domain_id_runtime");
        nbt.remove("jjkbrp_radius_multiplier");
        nbt.remove("jjkbrp_base_domain_radius");
        nbt.remove("jjkbrp_open_range_multiplier");
        nbt.remove("jjkbrp_open_surehit_multiplier");
        nbt.remove("jjkbrp_open_ce_drain_multiplier");
        nbt.remove("jjkbrp_open_duration_multiplier");
        nbt.remove("jjkbrp_incomplete_penalty_per_tick");
        nbt.remove("jjkbrp_incomplete_surface_multiplier");
        nbt.remove("jjkbrp_incomplete_form_active");
        nbt.remove("jjkbrp_incomplete_session_active");
        nbt.remove("jjkbrp_incomplete_cancelled");
        nbt.remove("jjkbrp_open_denied_no_adv");
        nbt.remove("jjkbrp_open_denied_incompatible");
        nbt.remove("jjkbrp_domain_form_cast_locked");
        nbt.remove("jjkbrp_domain_form_effective");
        nbt.remove("jjkbrp_effective_power");
        nbt.remove("jjkbrp_domain_cast_cost_base");
        nbt.remove("jjkbrp_domain_cast_cost_multiplier");
        nbt.remove("jjkbrp_domain_cast_cost_delta");
        nbt.remove("jjkbrp_domain_mastery_level");
        nbt.remove("jjkbrp_domain_form");
        // Delay the final clash cleanup slightly so outcome messages and other post-expiry systems can still read the result state.
        DomainExpireBarrierFixMixin.jjkbrp$scheduleDelayedClashCleanup(sl, caster.getUUID());
        nbt.remove("jjkbrp_caster_x_at_cast");
        nbt.remove("jjkbrp_caster_y_at_cast");
        nbt.remove("jjkbrp_caster_z_at_cast");
        nbt.remove("jjkbrp_barrier_under_attack");
        nbt.remove("jjkbrp_open_attacker_uuid");
        nbt.remove("jjkbrp_barrier_erosion_total");
        nbt.remove("jjkbrp_barrier_refinement");
        nbt.remove("jjkbrp_is_eroding_barrier");
        nbt.remove("jjkbrp_erosion_target_uuid");
        nbt.remove("jjkbrp_incomplete_wrap_active");
        nbt.remove("jjkbrp_incomplete_wrap_target_uuid");
        nbt.remove("jjkbrp_wrapped_by_incomplete");
        nbt.remove("jjkbrp_incomplete_wrapper_uuid");
        if (caster instanceof Player) {
            Player player = (Player)caster;
            player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
                if (data.getPropertyLevel(DomainMasteryProperties.RCT_HEAL_BOOST) >= DomainMasteryProperties.RCT_HEAL_BOOST.getMaxLevel()) {
                    DomainExpireBarrierFixMixin.jjkbrp$scheduleReducedFatigue(sl, player.getUUID());
                }
            });
        }
        if (!hadOpenForm && nbt.contains("x_pos_doma")) {
            DomainExpireBarrierFixMixin.jjkbrp$scheduleFinalRestoreSweep(sl, cx, cy, cz, finalRestoreRadius, 4);
            DomainExpireBarrierFixMixin.jjkbrp$scheduleFinalRestoreSweep(sl, cx, cy, cz, finalRestoreRadius, 20);
        }
    }

    /**
     * Schedules delayed removal of clash-result markers after players have had time to receive the outcome state.
     * @param world world access used by the current mixin callback.
     * @param entityId identifier used to resolve runtime state for this operation.
     */
    private static void jjkbrp$scheduleDelayedClashCleanup(ServerLevel world, UUID entityId) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + 80, () -> {
            Entity resolved = world.getEntity(entityId);
            if (!(resolved instanceof LivingEntity)) {
                return;
            }
            LivingEntity living = (LivingEntity)resolved;
            CompoundTag data = living.getPersistentData();
            data.remove("jjkbrp_clash_result_tick");
            data.remove("jjkbrp_clash_pending_tick");
            data.remove("jjkbrp_last_clash_contact_tick");
            data.remove("jjkbrp_last_clash_opponent_uuid");
            data.remove("jjkbrp_was_failed");
            data.remove("jjkbrp_was_domain_defeated");
            data.remove("jjkbrp_preserve_domain_runtime");
        }));
    }

    /**
     * Resolves the final cleanup radius using stored runtime values before falling back to the shared map radius.
     * @param world world access used by the current mixin callback.
     * @param nbt persistent data container used by this helper.
     * @return the resulting resolve effective radius value.
     */
    private static double jjkbrp$resolveEffectiveRadius(LevelAccessor world, CompoundTag nbt) {
        if (nbt.contains("jjkbrp_base_domain_radius")) {
            double effectiveRadius = nbt.getDouble("jjkbrp_base_domain_radius");
            double radiusMul = nbt.getDouble("jjkbrp_radius_multiplier");
            if (Math.abs(radiusMul) < 1.0E-4) {
                radiusMul = 1.0;
            }
            return Math.max(1.0, effectiveRadius *= Math.max(0.5, radiusMul));
        }
        try {
            return Math.max(1.0, JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius);
        }
        catch (Exception ignored) {
            return 16.0;
        }
    }

    /**
     * Performs schedule final restore sweep for this mixin.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param effectiveRadius distance value used by this runtime calculation.
     * @param delayTicks tick-based timing value used by this helper.
     */
    private static void jjkbrp$scheduleFinalRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius, int delayTicks) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + delayTicks, () -> DomainExpireBarrierFixMixin.jjkbrp$runFinalRestoreSweep(world, x, y, z, effectiveRadius)));
    }

    /**
     * Reroutes run final restore sweep through addon mixin control.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param effectiveRadius distance value used by this runtime calculation.
     */
    private static void jjkbrp$runFinalRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius) {
        int centerX = (int)Math.round(x);
        int centerY = (int)Math.round(y);
        int centerZ = (int)Math.round(z);
        int sweepRadius = (int)Math.ceil(effectiveRadius + 2.0);
        double restoreRadius = effectiveRadius + 1.5;
        double restoreRadiusSq = restoreRadius * restoreRadius;
        for (int blockX = centerX - sweepRadius; blockX <= centerX + sweepRadius; ++blockX) {
            double dx = blockX - centerX;
            double dxSq = dx * dx;
            if (dxSq > restoreRadiusSq) continue;
            for (int blockY = centerY - sweepRadius; blockY <= centerY + sweepRadius; ++blockY) {
                double dy = blockY - centerY;
                double dySq = dy * dy;
                if (dxSq + dySq > restoreRadiusSq) continue;
                for (int blockZ = centerZ - sweepRadius; blockZ <= centerZ + sweepRadius; ++blockZ) {
                    BlockPos currentPos;
                    double dz = blockZ - centerZ;
                    double distanceSq = dxSq + dySq + dz * dz;
                    if (distanceSq > restoreRadiusSq || !world.getBlockState(currentPos = BlockPos.containing((double)blockX, (double)blockY, (double)blockZ)).is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier"))) || DomainExpireBarrierFixMixin.jjkbrp$isProtectedByOtherLiveDomain(world, x, y, z, blockX, blockY, blockZ)) continue;
                    JujutsuBarrierUpdateTickProcedure.execute((LevelAccessor)world, (double)blockX, (double)blockY, (double)blockZ);
                }
            }
        }
    }

    /**
     * Performs is protected by other live domain for this mixin.
     * @param world world access used by the current mixin callback.
     * @param ownerX owner x used by this method.
     * @param ownerY owner y used by this method.
     * @param ownerZ owner z used by this method.
     * @param blockX block x used by this method.
     * @param blockY block y used by this method.
     * @param blockZ block z used by this method.
     * @return whether is protected by other live domain is true for the current runtime state.
     */
    private static boolean jjkbrp$isProtectedByOtherLiveDomain(ServerLevel world, double ownerX, double ownerY, double ownerZ, double blockX, double blockY, double blockZ) {
        Vec3 blockPos = new Vec3(blockX, blockY, blockZ);
        Vec3 ownerCenter = new Vec3(ownerX, ownerY, ownerZ);
        double scanRange = 128.0;
        for (LivingEntity caster : world.getEntitiesOfClass(LivingEntity.class, new AABB(ownerX - scanRange, ownerY - scanRange, ownerZ - scanRange, ownerX + scanRange, ownerY + scanRange, ownerZ + scanRange), e -> true)) {
            Vec3 otherCenter;
            if (!DomainAddonUtils.isDomainBuildOrActive(world, caster) || (otherCenter = DomainAddonUtils.getDomainCenter((Entity)caster)).distanceToSqr(ownerCenter) <= 1.0) continue;
            double otherRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, caster.getPersistentData()) + 1.75;
            if (!(otherCenter.distanceToSqr(blockPos) <= otherRadius * otherRadius)) continue;
            return true;
        }
        return false;
    }

    /**
     * Performs spawn cleanup entity for this mixin.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param effectiveRadius distance value used by this runtime calculation.
     * @return the resulting spawn cleanup entity value.
     */
    private static DomainExpansionEntityEntity jjkbrp$spawnCleanupEntity(ServerLevel world, double x, double y, double z, double effectiveRadius) {
        try {
            DomainExpansionEntityEntity entity = new DomainExpansionEntityEntity((EntityType)JujutsucraftModEntities.DOMAIN_EXPANSION_ENTITY.get(), (Level)world);
            entity.moveTo(x, y, z, 0.0f, 0.0f);
            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setNoAi(true);
            entity.getPersistentData().putDouble("x_pos", x);
            entity.getPersistentData().putDouble("y_pos", y);
            entity.getPersistentData().putDouble("z_pos", z);
            entity.getPersistentData().putDouble("range", effectiveRadius);
            world.addFreshEntity((Entity)entity);
            return entity;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Performs schedule reduced fatigue for this mixin.
     * @param world world access used by the current mixin callback.
     * @param playerId identifier used to resolve runtime state for this operation.
     */
    private static void jjkbrp$scheduleReducedFatigue(ServerLevel world, UUID playerId) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + 3, () -> {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            MobEffectInstance fatigue = player.getEffect((MobEffect)JujutsucraftModMobEffects.FATIGUE.get());
            if (fatigue == null) {
                return;
            }
            int reducedDuration = Math.max(1, (int)Math.ceil((double)fatigue.getDuration() * 0.25));
            if (reducedDuration >= fatigue.getDuration()) {
                return;
            }
            DomainAddonUtils.setEffectDuration(fatigue, reducedDuration);
        }));
    }
}
