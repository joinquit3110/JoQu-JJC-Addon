package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.procedures.JujutsuBarrierUpdateTickProcedure;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * Barrier-restore guard mixin for `JujutsuBarrierUpdateTickProcedure.execute()` that cancels premature restoration sweeps while an addon-managed domain is still building or active.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={JujutsuBarrierUpdateTickProcedure.class}, remap=false)
public class DomainBarrierRestoreGuardMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Injects at the head of the barrier-restore tick and cancels restoration when the scanned block still belongs to a live addon-managed domain.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkbrp$blockPrematureRestore(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        if (DomainBarrierRestoreGuardMixin.jjkbrp$blockBelongsToLiveDomainOwner(serverLevel, x, y, z)) {
            ci.cancel();
            return;
        }
        Vec3 blockPos = new Vec3(x, y, z);
        String restoreBlockOwnerUuid = DomainBarrierRestoreGuardMixin.jjkbrp$getBarrierOwnerUuid(serverLevel, x, y, z);
        double scanRange = 96.0;
        AABB scanBox = new AABB(x - scanRange, y - scanRange, z - scanRange, x + scanRange, y + scanRange, z + scanRange);
        // Scan nearby living casters and cancel restoration as soon as the target block is proven to still belong to a live addon-managed domain.
        for (LivingEntity caster : serverLevel.getEntitiesOfClass(LivingEntity.class, scanBox, e -> true)) {
            if (!DomainAddonUtils.isDomainBuildOrActive(serverLevel, caster) || DomainAddonUtils.isOpenDomainState(caster)) continue;
            CompoundTag casterNbt = caster.getPersistentData();
            Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
            double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)serverLevel, casterNbt);
            boolean primaryMatch = DomainBarrierRestoreGuardMixin.jjkbrp$isWithinRadius(center, actualRadius + 1.75, blockPos);
            boolean adoptedMatch = DomainBarrierRestoreGuardMixin.jjkbrp$isWithinAdoptedBarrierRadius(casterNbt, blockPos);
            if (!primaryMatch && !adoptedMatch) continue;
            if (restoreBlockOwnerUuid != null && !restoreBlockOwnerUuid.isEmpty() && !caster.getUUID().toString().equals(restoreBlockOwnerUuid)) continue;
            double adoptedRadius = casterNbt.getDouble("jjkbrp_adopted_radius");
            double adoptedCx = casterNbt.getDouble("jjkbrp_adopted_cx");
            double adoptedCy = casterNbt.getDouble("jjkbrp_adopted_cy");
            double adoptedCz = casterNbt.getDouble("jjkbrp_adopted_cz");
            LOGGER.debug("[DomainBarrierRestoreGuard] blocked restore at ({}, {}, {}) because caster={} open={} incomplete={} adoptedBarrier={} primaryMatch={} adoptedMatch={} radius={} center=({}, {}, {}) adoptedRadius={} adoptedCenter=({}, {}, {})",
                    x, y, z,
                    caster.getName().getString(),
                    DomainAddonUtils.isOpenDomainState(caster),
                    DomainAddonUtils.isIncompleteDomainState(caster),
                    casterNbt.getBoolean("jjkbrp_adopted_barrier"),
                    primaryMatch,
                    adoptedMatch,
                    actualRadius,
                    center.x, center.y, center.z,
                    adoptedRadius,
                    adoptedCx, adoptedCy, adoptedCz);
            ci.cancel();
            return;
        }
    }

    @Unique
    private static boolean jjkbrp$blockBelongsToLiveDomainOwner(ServerLevel world, double x, double y, double z) {
        BlockEntity blockEntity = world.getBlockEntity(net.minecraft.core.BlockPos.containing(x, y, z));
        if (blockEntity == null) {
            return false;
        }
        CompoundTag blockNbt = blockEntity.getPersistentData();
        String ownerUuidRaw = blockNbt.getString("OWNER_UUID");
        if (ownerUuidRaw == null || ownerUuidRaw.isEmpty()) {
            ownerUuidRaw = blockNbt.getString("jjkbrp_owner_uuid");
        }
        if (ownerUuidRaw == null || ownerUuidRaw.isEmpty()) {
            return false;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerUuidRaw);
        }
        catch (IllegalArgumentException ignored) {
            return false;
        }
        Entity owner = world.getEntity(ownerUuid);
        if (!(owner instanceof LivingEntity)) {
            return false;
        }
        LivingEntity livingOwner = (LivingEntity)owner;
        return DomainAddonUtils.isDomainBuildOrActive(world, livingOwner) && !DomainAddonUtils.isOpenDomainState(livingOwner);
    }

    @Unique
    private static String jjkbrp$getBarrierOwnerUuid(ServerLevel world, double x, double y, double z) {
        BlockEntity blockEntity = world.getBlockEntity(net.minecraft.core.BlockPos.containing(x, y, z));
        if (blockEntity == null) {
            return "";
        }
        CompoundTag blockNbt = blockEntity.getPersistentData();
        String ownerUuidRaw = blockNbt.getString("OWNER_UUID");
        if (ownerUuidRaw == null || ownerUuidRaw.isEmpty()) {
            ownerUuidRaw = blockNbt.getString("jjkbrp_owner_uuid");
        }
        return ownerUuidRaw == null ? "" : ownerUuidRaw;
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
        return DomainBarrierRestoreGuardMixin.jjkbrp$isWithinRadius(adoptedCenter, adoptedRadius + 1.75, blockPos);
    }

    @Unique
    private static boolean jjkbrp$isWithinRadius(Vec3 center, double radius, Vec3 blockPos) {
        if (center == null || blockPos == null || radius <= 0.0) {
            return false;
        }
        return center.distanceToSqr(blockPos) <= radius * radius;
    }
}
