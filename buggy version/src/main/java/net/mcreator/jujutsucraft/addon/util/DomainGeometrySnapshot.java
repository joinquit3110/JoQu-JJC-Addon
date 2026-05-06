package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/** Immutable geometry snapshot captured before domain mutation/cleanup. */
public final class DomainGeometrySnapshot {
    private final UUID ownerUUID;
    private final String dimensionId;
    private final Vec3 center;
    private final double radius;
    private final DomainForm form;
    private final int domainId;
    private final boolean openForm;
    private final boolean hasBarrierBlocks;
    private final boolean valid;
    private final String source;
    private final long tick;

    private DomainGeometrySnapshot(UUID ownerUUID, String dimensionId, Vec3 center, double radius, DomainForm form,
                                   int domainId, boolean openForm, boolean hasBarrierBlocks, boolean valid,
                                   String source, long tick) {
        this.ownerUUID = ownerUUID;
        this.dimensionId = dimensionId != null ? dimensionId : "";
        this.center = center != null ? center : Vec3.ZERO;
        this.radius = Math.max(1.0, radius);
        this.form = form != null ? form : DomainForm.CLOSED;
        this.domainId = domainId;
        this.openForm = openForm;
        this.hasBarrierBlocks = hasBarrierBlocks;
        this.valid = valid;
        this.source = source != null ? source : "unknown";
        this.tick = tick;
    }

    public static DomainGeometrySnapshot fromEntry(@Nullable DomainEntry entry, @Nullable LivingEntity liveEntity,
                                                   @Nullable ServerLevel level, long tick, String source) {
        if (entry == null && liveEntity == null) {
            return invalid(level, source, tick);
        }
        UUID owner = liveEntity != null ? liveEntity.getUUID() : entry.getCasterUUID();
        String dimension = entry != null && entry.getDimensionId() != null && !entry.getDimensionId().isBlank()
                ? entry.getDimensionId()
                : dimensionId(level);
        Vec3 center = liveEntity != null ? liveCenter(liveEntity) : (entry != null ? entry.getCenter() : Vec3.ZERO);
        double radius = liveEntity != null ? liveRadius(level, liveEntity) : (entry != null ? entry.getRadius() : 16.0);
        DomainForm form = liveEntity != null ? liveForm(liveEntity) : (entry != null ? entry.getForm() : DomainForm.CLOSED);
        int domainId = liveEntity != null ? liveDomainId(liveEntity) : (entry != null ? entry.getDomainId() : 0);
        boolean open = form == DomainForm.OPEN || (liveEntity != null && DomainAddonUtils.isOpenDomainState(liveEntity));
        boolean barrier = liveEntity != null && liveEntity.getPersistentData().getBoolean("jjkbrp_barrier_blocks_placed");
        if (entry != null && form != DomainForm.OPEN) {
            barrier = true;
        }
        boolean valid = owner != null && center != null && radius > 0.0;
        return new DomainGeometrySnapshot(owner, dimension, center, radius, form, domainId, open, barrier, valid, source, tick);
    }

    public static DomainGeometrySnapshot fromEntity(@Nullable LivingEntity entity, @Nullable LevelAccessor world,
                                                    long tick, String source) {
        if (entity == null) {
            return invalid(world instanceof ServerLevel sl ? sl : null, source, tick);
        }
        CompoundTag nbt = entity.getPersistentData();
        DomainForm form = DomainAddonUtils.resolveOgLikeDomainForm(entity);
        return new DomainGeometrySnapshot(
                entity.getUUID(),
                world instanceof ServerLevel sl ? sl.dimension().location().toString() : "",
                DomainAddonUtils.getOgLikeDomainCenter(entity),
                DomainAddonUtils.getActualDomainRadius(world, nbt),
                form,
                DomainAddonUtils.resolveOgLikeDomainId(entity),
                form == DomainForm.OPEN || DomainAddonUtils.isOpenDomainState(entity),
                nbt.getBoolean("jjkbrp_barrier_blocks_placed") || form != DomainForm.OPEN,
                true,
                source,
                tick
        );
    }

    public static DomainGeometrySnapshot of(UUID ownerUUID, String dimensionId, Vec3 center, double radius,
                                            DomainForm form, int domainId, boolean openForm, boolean hasBarrierBlocks,
                                            long tick, String source) {
        return new DomainGeometrySnapshot(ownerUUID, dimensionId, center, radius, form, domainId, openForm,
                hasBarrierBlocks, center != null && radius > 0.0, source, tick);
    }

    private static DomainGeometrySnapshot invalid(@Nullable ServerLevel level, String source, long tick) {
        return new DomainGeometrySnapshot(null, dimensionId(level), Vec3.ZERO, 1.0, DomainForm.CLOSED, 0,
                false, false, false, source, tick);
    }

    private static String dimensionId(@Nullable ServerLevel level) {
        return level != null ? level.dimension().location().toString() : "";
    }

    private static Vec3 liveCenter(@Nullable LivingEntity entity) {
        return entity != null ? DomainAddonUtils.getOgLikeDomainCenter(entity) : Vec3.ZERO;
    }

    private static double liveRadius(@Nullable LevelAccessor level, @Nullable LivingEntity entity) {
        return entity != null ? DomainAddonUtils.getActualDomainRadius(level, entity.getPersistentData()) : 16.0;
    }

    private static DomainForm liveForm(@Nullable LivingEntity entity) {
        return entity != null ? DomainAddonUtils.resolveOgLikeDomainForm(entity) : DomainForm.CLOSED;
    }

    private static int liveDomainId(@Nullable LivingEntity entity) {
        return entity != null ? DomainAddonUtils.resolveOgLikeDomainId(entity) : 0;
    }

    public boolean contains(BlockPos pos, double padding) {
        return pos != null && contains(Vec3.atCenterOf(pos), padding);
    }

    public boolean contains(Vec3 pos, double padding) {
        if (!valid || pos == null) {
            return false;
        }
        double effectiveRadius = Math.max(0.0, radius + padding);
        return center.distanceToSqr(pos) <= effectiveRadius * effectiveRadius;
    }

    public boolean isSameOwner(@Nullable UUID uuid) {
        return ownerUUID != null && ownerUUID.equals(uuid);
    }

    public boolean isSameOwner(@Nullable LivingEntity entity) {
        return entity != null && isSameOwner(entity.getUUID());
    }

    public boolean isSameDimension(@Nullable ServerLevel level) {
        if (level == null || dimensionId == null || dimensionId.isEmpty()) {
            return true;
        }
        return dimensionId.equals(level.dimension().location().toString());
    }

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getDimensionId() { return dimensionId; }
    public Vec3 getCenter() { return center; }
    public double getRadius() { return radius; }
    public DomainForm getForm() { return form; }
    public int getDomainId() { return domainId; }
    public boolean isOpenForm() { return openForm; }
    public boolean hasBarrierBlocks() { return hasBarrierBlocks; }
    public boolean isValid() { return valid; }
    public String getSource() { return source; }
    public long getTick() { return tick; }
}
