package net.mcreator.jujutsucraft.addon;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainBlockOwnership;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.addon.util.DomainGeometrySnapshot;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModEntities;
import net.mcreator.jujutsucraft.procedures.JujutsuBarrierUpdateTickProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Performs full-radius barrier cleanup for defeated/expired domain geometry snapshots. */
public final class DomainBarrierCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SWEEP_DELAY_1 = 4;
    private static final int SWEEP_DELAY_2 = 20;
    private static final int SWEEP_DELAY_3 = 60;

    private DomainBarrierCleanup() {}

    public static void scheduleSweeps(ServerLevel world, double x, double y, double z, double radius) {
        DomainGeometrySnapshot target = DomainGeometrySnapshot.of(null,
                world != null ? world.dimension().location().toString() : "",
                new Vec3(x, y, z), radius, DomainForm.CLOSED, 0, false, true,
                world != null ? world.getGameTime() : -1L, "legacy-unowned-cleanup");
        scheduleSweeps(world, target, null, null);
    }

    public static void scheduleSweeps(ServerLevel world, DomainGeometrySnapshot target) {
        scheduleSweeps(world, target, null, null);
    }

    public static void scheduleSweeps(ServerLevel world, DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                      @Nullable DomainGeometrySnapshot protectedWinner) {
        if (world == null || target == null || !target.isValid()) {
            LOGGER.info("[DomainBarrierCleanup] scheduleSweeps skipped worldPresent={} targetValid={}",
                    world != null, target != null && target.isValid());
            return;
        }
        int now = world.getServer().getTickCount();
        UUID authorizedLoser = loserOwnerUuid != null ? loserOwnerUuid : target.getOwnerUUID();
        LOGGER.info("[DomainBarrierCleanup] scheduleSweeps snapshot source={} owner={} authorizedLoser={} center=({}, {}, {}) radius={} now={} delays=[0,{},{},{}]",
                target.getSource(), target.getOwnerUUID(), authorizedLoser, String.format("%.1f", target.getCenter().x),
                String.format("%.1f", target.getCenter().y), String.format("%.1f", target.getCenter().z),
                String.format("%.1f", target.getRadius()), now, SWEEP_DELAY_1, SWEEP_DELAY_2, SWEEP_DELAY_3);
        triggerCleanupEntity(world, target, authorizedLoser);
        scheduleSweep(world, target, authorizedLoser, protectedWinner, now);
        scheduleSweep(world, target, authorizedLoser, protectedWinner, now + SWEEP_DELAY_1);
        scheduleSweep(world, target, authorizedLoser, protectedWinner, now + SWEEP_DELAY_2);
        scheduleSweep(world, target, authorizedLoser, protectedWinner, now + SWEEP_DELAY_3);
    }

    private static void scheduleSweep(ServerLevel world, DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                      @Nullable DomainGeometrySnapshot protectedWinner, int when) {
        world.getServer().tell(new TickTask(when, () -> runSweep(world, target, loserOwnerUuid, protectedWinner)));
    }

    private static void triggerCleanupEntity(ServerLevel world, DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid) {
        if (world == null || target == null || !target.isValid()) return;
        Vec3 c = target.getCenter();
        double radius = Math.max(1.0, target.getRadius());
        double search = Math.max(3.0, radius + 2.0);
        List<DomainExpansionEntityEntity> entities = world.getEntitiesOfClass(DomainExpansionEntityEntity.class,
                new AABB(c.x - search, c.y - search, c.z - search, c.x + search, c.y + search, c.z + search), e -> true);
        DomainExpansionEntityEntity cleanup = entities.stream()
                .filter(e -> e.distanceToSqr(c.x, c.y, c.z) <= search * search)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(c.x, c.y, c.z)))
                .orElse(null);
        if (cleanup == null) {
            cleanup = spawnCleanupEntity(world, c.x, c.y, c.z, radius, loserOwnerUuid != null ? loserOwnerUuid : target.getOwnerUUID());
        }
        if (cleanup == null) return;
        CompoundTag nbt = cleanup.getPersistentData();
        nbt.putDouble("x_pos", c.x);
        nbt.putDouble("y_pos", c.y);
        nbt.putDouble("z_pos", c.z);
        nbt.putDouble("range", radius);
        UUID owner = loserOwnerUuid != null ? loserOwnerUuid : target.getOwnerUUID();
        if (owner != null) nbt.putString("jjkbrp_owner_uuid", owner.toString());
        nbt.putBoolean("Break", true);
        nbt.putDouble("cnt_break", 0.0);
        nbt.putDouble("cnt_life2", 0.0);
    }

    @Nullable
    private static DomainExpansionEntityEntity spawnCleanupEntity(ServerLevel world, double x, double y, double z, double radius, @Nullable UUID ownerUuid) {
        try {
            DomainExpansionEntityEntity entity = new DomainExpansionEntityEntity((EntityType)JujutsucraftModEntities.DOMAIN_EXPANSION_ENTITY.get(), (Level)world);
            entity.moveTo(x, y, z, 0.0f, 0.0f);
            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setNoAi(true);
            CompoundTag nbt = entity.getPersistentData();
            nbt.putDouble("x_pos", x);
            nbt.putDouble("y_pos", y);
            nbt.putDouble("z_pos", z);
            nbt.putDouble("range", radius);
            if (ownerUuid != null) nbt.putString("jjkbrp_owner_uuid", ownerUuid.toString());
            world.addFreshEntity((Entity)entity);
            return entity;
        } catch (Exception ex) {
            LOGGER.warn("[DomainBarrierCleanup] failed to spawn cleanup entity center=({}, {}, {}) radius={}",
                    String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z), String.format("%.1f", radius), ex);
            return null;
        }
    }

    private static void runSweep(ServerLevel world, DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                 @Nullable DomainGeometrySnapshot protectedWinner) {
        if (world == null || target == null || !target.isValid() || !target.isSameDimension(world)) {
            return;
        }
        Vec3 center = target.getCenter();
        int cx = (int)Math.round(center.x);
        int cy = (int)Math.round(center.y);
        int cz = (int)Math.round(center.z);
        double radius = target.getRadius();
        int sweepRadius = (int)Math.ceil(radius + 2.0);
        int verticalExtra = 8;
        double restoreRadius = radius + 1.5;
        double restoreRadiusSq = restoreRadius * restoreRadius;

        for (int bx = cx - sweepRadius; bx <= cx + sweepRadius; ++bx) {
            double dx = bx - cx;
            double dxSq = dx * dx;
            if (dxSq > restoreRadiusSq) continue;
            for (int by = cy - sweepRadius; by <= cy + sweepRadius + verticalExtra; ++by) {
                double dy = by - cy;
                double dySq = dy * dy;
                for (int bz = cz - sweepRadius; bz <= cz + sweepRadius; ++bz) {
                    double dz = bz - cz;
                    double distSq = dxSq + dySq + dz * dz;
                    double horizSq = dxSq + dz * dz;
                    boolean inRadius = distSq <= restoreRadiusSq || (horizSq <= restoreRadiusSq && by >= cy && by <= cy + verticalExtra);
                    if (!inRadius) continue;

                    BlockPos pos = BlockPos.containing(bx, by, bz);
                    if (!isRestorableDomainBlock(world, pos)) continue;
                    if (isProtected(world, target, loserOwnerUuid, protectedWinner, pos)) continue;

                    DomainBarrierCleanupContext.push(target, loserOwnerUuid, protectedWinner);
                    try {
                        JujutsuBarrierUpdateTickProcedure.execute(world, bx, by, bz);
                    } finally {
                        DomainBarrierCleanupContext.pop();
                    }
                }
            }
        }
    }

    private static boolean isBarrierBlock(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).is(BlockTags.create(new net.minecraft.resources.ResourceLocation("jujutsucraft:barrier")));
    }

    private static boolean isRestorableDomainBlock(ServerLevel world, BlockPos pos) {
        return isBarrierBlock(world, pos) || DomainBlockOwnership.isKnownDomainBlock(world, pos);
    }

    private static boolean isProtected(ServerLevel world, DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                       @Nullable DomainGeometrySnapshot protectedWinner, BlockPos pos) {
        if (protectedWinner != null && protectedWinner.isValid()
                && !protectedWinner.isSameOwner(target.getOwnerUUID())
                && !protectedWinner.isSameOwner(loserOwnerUuid)
                && protectedWinner.isSameDimension(world)
                && protectedWinner.contains(pos, 1.75)) {
            return true;
        }
        return isProtectedByOtherLiveDomain(world, target, loserOwnerUuid, Vec3.atCenterOf(pos));
    }

    private static boolean isProtectedByOtherLiveDomain(ServerLevel world, DomainGeometrySnapshot target,
                                                        @Nullable UUID loserOwnerUuid, Vec3 blockPos) {
        Vec3 center = target.getCenter();
        double scanRange = Math.max(128.0, target.getRadius() + 32.0);
        for (LivingEntity caster : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - scanRange, center.y - scanRange, center.z - scanRange,
                        center.x + scanRange, center.y + scanRange, center.z + scanRange), e -> true)) {
            UUID casterUuid = caster.getUUID();
            if (target.isSameOwner(casterUuid) || (loserOwnerUuid != null && loserOwnerUuid.equals(casterUuid))) {
                continue;
            }
            if (!DomainAddonUtils.isDomainBuildOrActive(world, caster) || DomainAddonUtils.isOpenDomainState(caster)) {
                continue;
            }
            DomainGeometrySnapshot other = DomainGeometrySnapshot.fromEntity(caster, world, world.getGameTime(), "live-protection");
            if (!other.isValid() || other.isOpenForm()) {
                continue;
            }
            if (other.contains(blockPos, 1.75)) {
                return true;
            }
        }
        return false;
    }

    public static final class DomainBarrierCleanupContext {
        private static final ThreadLocal<CleanupContext> CURRENT = new ThreadLocal<>();

        private DomainBarrierCleanupContext() {}

        private static void push(DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                 @Nullable DomainGeometrySnapshot protectedWinner) {
            CURRENT.set(new CleanupContext(target, loserOwnerUuid, protectedWinner));
        }

        private static void pop() {
            CURRENT.remove();
        }

        @Nullable
        public static CleanupContext current() {
            return CURRENT.get();
        }
    }

    public record CleanupContext(DomainGeometrySnapshot target, @Nullable UUID loserOwnerUuid,
                                 @Nullable DomainGeometrySnapshot protectedWinner) {
        public boolean isAuthorizedLoser(@Nullable UUID uuid) {
            return uuid != null && (uuid.equals(loserOwnerUuid) || (target != null && target.isSameOwner(uuid)));
        }
    }
}
