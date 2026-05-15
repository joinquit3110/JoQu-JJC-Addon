package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Domain-expiry cleanup mixin for `DomainExpansionEffectExpiresProcedure.execute()` that preserves the right runtime state during failure checks, signals cleanup entities to break, schedules restore sweeps, and removes addon runtime keys after the domain ends.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionEffectExpiresProcedure.class}, remap=false)
public class DomainExpireBarrierFixMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
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
        boolean failedBeforeHook = nbt.getBoolean("Failed");
        boolean defeatedBeforeHook = nbt.getBoolean("DomainDefeated");
        nbt.putBoolean("jjkbrp_was_failed", failedBeforeHook);
        nbt.putBoolean("jjkbrp_was_domain_defeated", defeatedBeforeHook);
        boolean clashDefeatCleanup = defeatedBeforeHook && nbt.contains("jjkbrp_clash_result_tick");
        if (clashDefeatCleanup) {
            // Preserve vanilla loser-defeat semantics: DomainExpansionEffectExpiresProcedure must see
            // DomainDefeated/Failed and skip BreakDomainProcedure for clash losers.
            return;
        }
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
        boolean forceClosedCleanup = nbt.getBoolean("jjkbrp_force_closed_cleanup");
        boolean failedBeforeHook = nbt.getBoolean("jjkbrp_was_failed");
        boolean defeatedBeforeHook = nbt.getBoolean("jjkbrp_was_domain_defeated");
        boolean clashDefeatCleanup = defeatedBeforeHook && nbt.contains("jjkbrp_clash_result_tick");
        // Some skills refresh DOMAIN_EXPANSION by replacing the effect instance, which can still invoke expire callbacks.
        // For intentional range-cancel removals, keep cleanup enabled even if the effect still appears active during this callback.
        boolean forcedCleanup = nbt.getBoolean("jjkbrp_open_cancelled") || nbt.getBoolean("jjkbrp_incomplete_cancelled");
        if (caster.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) && !forcedCleanup) {
            DomainExpireBarrierFixMixin.jjkbrp$clearTransientExpireState(nbt, true);
            return;
        }

        DomainExpireBarrierFixMixin.jjkbrp$clearTransientExpireState(nbt, false);
        if (forceClosedCleanup) {
            boolean rearmedDomainDefeated = defeatedBeforeHook || !failedBeforeHook;
            nbt.putBoolean("Failed", failedBeforeHook);
            nbt.putBoolean("DomainDefeated", rearmedDomainDefeated);
            LOGGER.debug("[DomainExpireCleanup] forced closed cleanup caster={} failedBeforeHook={} defeatedBeforeHook={} rearmedFailed={} rearmedDomainDefeated={} centerPresent={} cnt1={} cnt3={}",
                    caster.getName().getString(),
                    failedBeforeHook,
                    defeatedBeforeHook,
                    nbt.getBoolean("Failed"),
                    nbt.getBoolean("DomainDefeated"),
                    nbt.contains("x_pos_doma"),
                    nbt.getDouble("cnt1"),
                    nbt.getDouble("cnt3"));
        }
        boolean hadOpenForm = nbt.getBoolean("jjkbrp_open_form_active");
        double finalRestoreRadius = DomainExpireBarrierFixMixin.jjkbrp$resolveEffectiveRadius((LevelAccessor)sl, nbt);
        boolean hasCasterAnchor = nbt.contains("jjkbrp_caster_x_at_cast") && nbt.contains("jjkbrp_caster_y_at_cast") && nbt.contains("jjkbrp_caster_z_at_cast");
        double casterAnchorX = hasCasterAnchor ? nbt.getDouble("jjkbrp_caster_x_at_cast") : caster.getX();
        double casterAnchorY = hasCasterAnchor ? nbt.getDouble("jjkbrp_caster_y_at_cast") : caster.getY();
        double casterAnchorZ = hasCasterAnchor ? nbt.getDouble("jjkbrp_caster_z_at_cast") : caster.getZ();
        if (nbt.contains("x_pos_doma")) {
            cx = nbt.getDouble("x_pos_doma");
            cy = nbt.getDouble("y_pos_doma");
            cz = nbt.getDouble("z_pos_doma");
        } else {
            cx = casterAnchorX;
            cy = casterAnchorY;
            cz = casterAnchorZ;
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
            DomainExpansionEntityEntity domainEnt = entities.stream()
                    .filter(e -> DomainExpireBarrierFixMixin.jjkbrp$isCleanupOwnedBy(e, caster))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(cx, cy, cz)))
                    .orElse(null);
            if (domainEnt != null && !clashDefeatCleanup) {
                // Clash loser expiry can overlap the winner center; delayed guarded sweeps below are safer than waking a nearby cleanup entity.
                domainEnt.getPersistentData().putBoolean("Break", true);
                domainEnt.getPersistentData().putDouble("cnt_life2", 0.0);
                CompoundTag domainNBT = domainEnt.getPersistentData();
                domainNBT.putDouble("range", finalRestoreRadius);
            }
        } else if (!clashDefeatCleanup) {
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
        if (clashDefeatCleanup) {
            DomainExpireBarrierFixMixin.jjkbrp$clearClashDefeatExpireState(nbt);
            return;
        }
        if (nbt.getBoolean("jjkbrp_open_form_active")) {
            nbt.putBoolean("jjkbrp_open_form_active", false);
        }
        if (entity instanceof LivingEntity) {
            LivingEntity le = (LivingEntity)entity;
            DomainAddonUtils.cleanupBFBoost(le);
        }
        // Remove all runtime-only open and incomplete keys so the next domain cast starts from a clean persistent-data state.
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
        DomainAddonUtils.clearIncompleteDomainData(nbt);
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
        nbt.remove("jjkbrp_caster_x_at_cast");
        nbt.remove("jjkbrp_caster_y_at_cast");
        nbt.remove("jjkbrp_caster_z_at_cast");
        nbt.remove("jjkbrp_barrier_refinement");
        if (forceClosedCleanup) {
            DomainExpireBarrierFixMixin.jjkbrp$scheduleForceClosedCleanupFlagClear(sl, caster.getUUID(), 80);
        } else {
            nbt.remove("jjkbrp_force_closed_cleanup");
        }
        if (caster instanceof Player) {
            Player player = (Player)caster;
            player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
                if (data.getPropertyLevel(DomainMasteryProperties.RCT_HEAL_BOOST) >= DomainMasteryProperties.RCT_HEAL_BOOST.getMaxLevel()) {
                    DomainExpireBarrierFixMixin.jjkbrp$scheduleReducedFatigue(sl, player.getUUID());
                }
            });
        }
        boolean hadBarrierBlocks = nbt.getBoolean("jjkbrp_barrier_blocks_placed");
        nbt.remove("jjkbrp_barrier_blocks_placed");
        if (!hadOpenForm || hadBarrierBlocks) {
            double sweepRadius = finalRestoreRadius;
            if (!nbt.contains("x_pos_doma")) {
                sweepRadius = Math.max(sweepRadius, 16.0);
            }
            DomainExpireBarrierFixMixin.jjkbrp$scheduleFinalRestoreSweep(sl, cx, cy, cz, sweepRadius, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, caster.getUUID().toString(), 4);
            DomainExpireBarrierFixMixin.jjkbrp$scheduleFinalRestoreSweep(sl, cx, cy, cz, sweepRadius, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, caster.getUUID().toString(), 20);
            DomainExpireBarrierFixMixin.jjkbrp$scheduleFinalRestoreSweep(sl, cx, cy, cz, sweepRadius, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, caster.getUUID().toString(), 60);
        }
        if (nbt.getBoolean("jjkbrp_adopted_barrier")) {
            double acx = nbt.getDouble("jjkbrp_adopted_cx");
            double acy = nbt.getDouble("jjkbrp_adopted_cy");
            double acz = nbt.getDouble("jjkbrp_adopted_cz");
            double aRadius = nbt.getDouble("jjkbrp_adopted_radius");
            if (aRadius > 0) {
                String adoptedOwnerUuid = nbt.getString("jjkbrp_adopted_owner_uuid");
                DomainExpireBarrierFixMixin.jjkbrp$scheduleAdoptedBarrierRestoreSweep(sl, acx, acy, acz, aRadius, adoptedOwnerUuid, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, 4);
                DomainExpireBarrierFixMixin.jjkbrp$scheduleAdoptedBarrierRestoreSweep(sl, acx, acy, acz, aRadius, adoptedOwnerUuid, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, 20);
                DomainExpireBarrierFixMixin.jjkbrp$scheduleAdoptedBarrierRestoreSweep(sl, acx, acy, acz, aRadius, adoptedOwnerUuid, casterAnchorX, casterAnchorY, casterAnchorZ, hasCasterAnchor, 60);
            }
            nbt.remove("jjkbrp_adopted_barrier");
            nbt.remove("jjkbrp_adopted_cx");
            nbt.remove("jjkbrp_adopted_cy");
            nbt.remove("jjkbrp_adopted_cz");
            nbt.remove("jjkbrp_adopted_radius");
            nbt.remove("jjkbrp_adopted_owner_uuid");
        }
    }


    private static boolean jjkbrp$isCleanupOwnedBy(DomainExpansionEntityEntity cleanup, LivingEntity caster) {
        if (cleanup == null || caster == null) {
            return false;
        }
        CompoundTag nbt = cleanup.getPersistentData();
        String owner = nbt.getString("jjkbrp_owner_uuid");
        if (owner == null || owner.isEmpty()) {
            owner = nbt.getString("OWNER_UUID");
        }
        if (owner != null && !owner.isEmpty()) {
            return caster.getUUID().toString().equals(owner);
        }
        Vec3 cleanupCenter = nbt.contains("x_pos") ? new Vec3(nbt.getDouble("x_pos"), nbt.getDouble("y_pos"), nbt.getDouble("z_pos")) : cleanup.position();
        return cleanupCenter.distanceToSqr(DomainAddonUtils.getDomainCenter((Entity)caster)) <= 9.0;
    }
    /**
     * Clears temporary expire-tracking keys and optionally restores pre-hook failure flags.
     * @param nbt persistent data used by this helper.
     * @param restoreFlags whether the previous Failed/DomainDefeated flags should be restored.
     */
    @Unique
    private static void jjkbrp$clearTransientExpireState(CompoundTag nbt, boolean restoreFlags) {
        if (nbt == null) {
            return;
        }
        if (restoreFlags) {
            if (nbt.contains("jjkbrp_was_failed")) {
                nbt.putBoolean("Failed", nbt.getBoolean("jjkbrp_was_failed"));
            }
            if (nbt.contains("jjkbrp_was_domain_defeated")) {
                nbt.putBoolean("DomainDefeated", nbt.getBoolean("jjkbrp_was_domain_defeated"));
            }
        }
        nbt.remove("jjkbrp_preserve_domain_runtime");
        nbt.remove("jjkbrp_was_failed");
        nbt.remove("jjkbrp_was_domain_defeated");
    }

    @Unique
    private static void jjkbrp$clearClashDefeatExpireState(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        nbt.remove("jjkbrp_preserve_domain_runtime");
        nbt.remove("jjkbrp_was_failed");
        nbt.remove("jjkbrp_was_domain_defeated");
        nbt.remove("jjkbrp_open_cancelled");
        nbt.remove("jjkbrp_incomplete_cancelled");
        nbt.remove("jjkbrp_force_closed_cleanup");
    }

    private static void jjkbrp$scheduleForceClosedCleanupFlagClear(ServerLevel world, UUID entityId, int delayTicks) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + delayTicks, () -> {
            Entity resolved = world.getEntity(entityId);
            if (!(resolved instanceof LivingEntity)) {
                return;
            }
            resolved.getPersistentData().remove("jjkbrp_force_closed_cleanup");
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
    private static void jjkbrp$scheduleFinalRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius, double casterX, double casterY, double casterZ, boolean hasCasterAnchor, String endingOwnerUuid, int delayTicks) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + delayTicks, () -> DomainExpireBarrierFixMixin.jjkbrp$runFinalRestoreSweep(world, x, y, z, effectiveRadius, casterX, casterY, casterZ, hasCasterAnchor, endingOwnerUuid)));
    }

    private static void jjkbrp$scheduleAdoptedBarrierRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius, String ownerUuid, double casterX, double casterY, double casterZ, boolean hasCasterAnchor, int delayTicks) {
        world.getServer().tell(new TickTask(world.getServer().getTickCount() + delayTicks, () -> DomainExpireBarrierFixMixin.jjkbrp$runAdoptedBarrierRestoreSweep(world, x, y, z, effectiveRadius, ownerUuid, casterX, casterY, casterZ, hasCasterAnchor)));
    }

    /**
     * Reroutes run final restore sweep through addon mixin control.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param effectiveRadius distance value used by this runtime calculation.
     */
    private static void jjkbrp$runFinalRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius, double casterX, double casterY, double casterZ, boolean hasCasterAnchor, String endingOwnerUuid) {
        int centerX = (int)Math.round(x);
        int centerY = (int)Math.round(y);
        int centerZ = (int)Math.round(z);
        int sweepRadius = (int)Math.ceil(effectiveRadius + 4.0);
        int verticalExtra = 12;
        double restoreRadius = effectiveRadius + 3.0;
        double restoreRadiusSq = restoreRadius * restoreRadius;
        for (int blockX = centerX - sweepRadius; blockX <= centerX + sweepRadius; ++blockX) {
            double dx = blockX - centerX;
            double dxSq = dx * dx;
            if (dxSq > restoreRadiusSq) continue;
            for (int blockY = centerY - sweepRadius; blockY <= centerY + sweepRadius + verticalExtra; ++blockY) {
                double dy = blockY - centerY;
                double dySq = dy * dy;
                double horizontalDistSq = dxSq;
                for (int blockZ = centerZ - sweepRadius; blockZ <= centerZ + sweepRadius; ++blockZ) {
                    BlockPos currentPos;
                    double dz = blockZ - centerZ;
                    double distanceSq = dxSq + dySq + dz * dz;
                    horizontalDistSq = dxSq + dz * dz;
                    boolean inRadius = distanceSq <= restoreRadiusSq || (horizontalDistSq <= restoreRadiusSq && blockY >= centerY && blockY <= centerY + verticalExtra);
                    if (!inRadius || !world.getBlockState(currentPos = BlockPos.containing((double)blockX, (double)blockY, (double)blockZ)).is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier"))) || DomainExpireBarrierFixMixin.jjkbrp$isProtectedByOtherLiveDomain(world, x, y, z, blockX, blockY, blockZ, currentPos, endingOwnerUuid)) continue;
                    DomainExpireBarrierFixMixin.jjkbrp$restoreBarrierBlock(world, currentPos);
                }
            }
        }
        DomainExpireBarrierFixMixin.jjkbrp$runFocusedCenterFloorRestoreSweep(world, centerX, centerY, centerZ, effectiveRadius, x, y, z);
        if (hasCasterAnchor) {
            DomainExpireBarrierFixMixin.jjkbrp$runFocusedCasterFootprintRestoreSweep(world, casterX, casterY, casterZ, x, y, z);
        }
    }

    private static void jjkbrp$runAdoptedBarrierRestoreSweep(ServerLevel world, double x, double y, double z, double effectiveRadius, String ownerUuid, double casterX, double casterY, double casterZ, boolean hasCasterAnchor) {
        int centerX = (int)Math.round(x);
        int centerY = (int)Math.round(y);
        int centerZ = (int)Math.round(z);
        int sweepRadius = (int)Math.ceil(effectiveRadius + 4.0);
        int verticalExtra = 12;
        double restoreRadius = effectiveRadius + 3.0;
        double restoreRadiusSq = restoreRadius * restoreRadius;
        for (int blockX = centerX - sweepRadius; blockX <= centerX + sweepRadius; ++blockX) {
            double dx = blockX - centerX;
            double dxSq = dx * dx;
            if (dxSq > restoreRadiusSq) continue;
            for (int blockY = centerY - sweepRadius; blockY <= centerY + sweepRadius + verticalExtra; ++blockY) {
                double dy = blockY - centerY;
                double dySq = dy * dy;
                for (int blockZ = centerZ - sweepRadius; blockZ <= centerZ + sweepRadius; ++blockZ) {
                    BlockPos currentPos;
                    double dz = blockZ - centerZ;
                    double horizontalDistSq = dxSq + dz * dz;
                    double distanceSq = dxSq + dySq + dz * dz;
                    boolean inRadius = distanceSq <= restoreRadiusSq || (horizontalDistSq <= restoreRadiusSq && blockY >= centerY && blockY <= centerY + verticalExtra);
                    if (!inRadius || !world.getBlockState(currentPos = BlockPos.containing((double)blockX, (double)blockY, (double)blockZ)).is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier")))) continue;
                    if (!DomainExpireBarrierFixMixin.jjkbrp$isAdoptedBarrierBlock(world, currentPos, ownerUuid) && DomainExpireBarrierFixMixin.jjkbrp$isProtectedByOtherLiveDomain(world, x, y, z, blockX, blockY, blockZ, currentPos, ownerUuid)) continue;
                    DomainExpireBarrierFixMixin.jjkbrp$restoreBarrierBlock(world, currentPos);
                }
            }
        }
        DomainExpireBarrierFixMixin.jjkbrp$runFocusedCenterFloorRestoreSweep(world, centerX, centerY, centerZ, effectiveRadius, x, y, z);
        if (hasCasterAnchor) {
            DomainExpireBarrierFixMixin.jjkbrp$runFocusedCasterFootprintRestoreSweep(world, casterX, casterY, casterZ, x, y, z);
        }
    }

    private static void jjkbrp$runFocusedCenterFloorRestoreSweep(ServerLevel world, int centerX, int centerY, int centerZ, double effectiveRadius, double ownerX, double ownerY, double ownerZ) {
        int focusedRadius = Math.max(3, Math.min(8, (int)Math.ceil(effectiveRadius * 0.25)));
        for (int blockX = centerX - focusedRadius; blockX <= centerX + focusedRadius; ++blockX) {
            for (int blockY = centerY - 2; blockY <= centerY; ++blockY) {
                for (int blockZ = centerZ - focusedRadius; blockZ <= centerZ + focusedRadius; ++blockZ) {
                    BlockPos currentPos = BlockPos.containing((double)blockX, (double)blockY, (double)blockZ);
                    if (!world.getBlockState(currentPos).is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier")))) {
                        continue;
                    }
                    if (DomainExpireBarrierFixMixin.jjkbrp$isProtectedByOtherLiveDomain(world, ownerX, ownerY, ownerZ, blockX, blockY, blockZ, currentPos, null)) {
                        continue;
                    }
                    DomainExpireBarrierFixMixin.jjkbrp$restoreBarrierBlock(world, currentPos);
                }
            }
        }
    }

    private static void jjkbrp$runFocusedCasterFootprintRestoreSweep(ServerLevel world, double casterX, double casterY, double casterZ, double ownerX, double ownerY, double ownerZ) {
        int casterBlockX = (int)Math.floor(casterX);
        int casterFloorY = (int)Math.floor(casterY) - 1;
        int casterBlockZ = (int)Math.floor(casterZ);
        for (int blockX = casterBlockX - 2; blockX <= casterBlockX + 2; ++blockX) {
            for (int blockY = casterFloorY - 1; blockY <= casterFloorY; ++blockY) {
                for (int blockZ = casterBlockZ - 2; blockZ <= casterBlockZ + 2; ++blockZ) {
                    BlockPos currentPos = BlockPos.containing((double)blockX, (double)blockY, (double)blockZ);
                    if (!world.getBlockState(currentPos).is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier")))) {
                        continue;
                    }
                    if (DomainExpireBarrierFixMixin.jjkbrp$isProtectedByOtherLiveDomain(world, ownerX, ownerY, ownerZ, blockX, blockY, blockZ, currentPos, null)) {
                        continue;
                    }
                    DomainExpireBarrierFixMixin.jjkbrp$restoreBarrierBlock(world, currentPos);
                }
            }
        }
    }

    private static void jjkbrp$restoreBarrierBlock(ServerLevel world, BlockPos pos) {
        BlockState before = world.getBlockState(pos);
        if (!before.is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier")))) {
            return;
        }
        BlockEntity be = world.getBlockEntity(pos);
        String oldBlock = be != null ? be.getPersistentData().getString("old_block") : "";
        JujutsuBarrierUpdateTickProcedure.execute((LevelAccessor)world, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
        BlockState after = world.getBlockState(pos);
        if (after.is(BlockTags.create((ResourceLocation)new ResourceLocation("jujutsucraft:barrier"))) && (oldBlock == null || oldBlock.isEmpty())) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean jjkbrp$isAdoptedBarrierBlock(ServerLevel world, BlockPos pos, String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        if (world.getBlockEntity(pos) == null) {
            return false;
        }
        CompoundTag blockNbt = world.getBlockEntity(pos).getPersistentData();
        String blockOwner = blockNbt.getString("OWNER_UUID");
        if (blockOwner == null || blockOwner.isEmpty()) {
            blockOwner = blockNbt.getString("jjkbrp_owner_uuid");
        }
        return ownerUuid.equals(blockOwner);
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
    private static boolean jjkbrp$isProtectedByOtherLiveDomain(ServerLevel world, double ownerX, double ownerY, double ownerZ, double blockX, double blockY, double blockZ, BlockPos blockPosRaw, String endingOwnerUuid) {
        Vec3 blockPos = new Vec3(blockX, blockY, blockZ);
        String blockOwnerUuid = DomainExpireBarrierFixMixin.jjkbrp$getBarrierOwnerUuid(world, blockPosRaw);
        if (endingOwnerUuid != null && !endingOwnerUuid.isEmpty() && endingOwnerUuid.equals(blockOwnerUuid)) {
            return false;
        }
        double scanRange = 128.0;
        for (LivingEntity caster : world.getEntitiesOfClass(LivingEntity.class, new AABB(ownerX - scanRange, ownerY - scanRange, ownerZ - scanRange, ownerX + scanRange, ownerY + scanRange, ownerZ + scanRange), e -> true)) {
            if (!DomainAddonUtils.isDomainBuildOrActive(world, caster) || DomainAddonUtils.isOpenDomainState(caster)) {
                continue;
            }
            CompoundTag casterNbt = caster.getPersistentData();
            Vec3 otherCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
            double otherRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, casterNbt) + 1.75;
            boolean inPrimary = otherCenter.distanceToSqr(blockPos) <= otherRadius * otherRadius;
            boolean inAdopted = DomainExpireBarrierFixMixin.jjkbrp$isWithinAdoptedBarrierRadius(casterNbt, blockPos);
            if (!inPrimary && !inAdopted) {
                continue;
            }
            String liveOwnerUuid = caster.getUUID().toString();
            return blockOwnerUuid == null || blockOwnerUuid.isEmpty() || liveOwnerUuid.equals(blockOwnerUuid);
        }
        return false;
    }

    @Unique
    private static String jjkbrp$getBarrierOwnerUuid(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return "";
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) {
            return "";
        }
        CompoundTag nbt = be.getPersistentData();
        String owner = nbt.getString("OWNER_UUID");
        if (owner == null || owner.isEmpty()) {
            owner = nbt.getString("jjkbrp_owner_uuid");
        }
        return owner == null ? "" : owner;
    }
    @Unique
    private static boolean jjkbrp$isWithinAdoptedBarrierRadius(CompoundTag casterNbt, Vec3 blockPos) {
        if (casterNbt == null || blockPos == null || !casterNbt.getBoolean("jjkbrp_adopted_barrier")) {
            return false;
        }
        double adoptedRadius = casterNbt.getDouble("jjkbrp_adopted_radius");
        if (adoptedRadius <= 0.0) {
            return false;
        }
        Vec3 adoptedCenter = new Vec3(casterNbt.getDouble("jjkbrp_adopted_cx"), casterNbt.getDouble("jjkbrp_adopted_cy"), casterNbt.getDouble("jjkbrp_adopted_cz"));
        double allowedRadius = adoptedRadius + 1.75;
        return adoptedCenter.distanceToSqr(blockPos) <= allowedRadius * allowedRadius;
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
