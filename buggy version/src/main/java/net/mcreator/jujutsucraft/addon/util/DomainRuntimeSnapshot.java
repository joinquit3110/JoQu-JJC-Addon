package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** Immutable addon-owned runtime snapshot captured before OG/domain mutation. */
public final class DomainRuntimeSnapshot {
    private final UUID runtimeId;
    private final UUID ownerUUID;
    private final String dimensionId;
    private final Vec3 center;
    private final double radius;
    private final DomainForm form;
    private final DomainForm effectiveForm;
    private final int domainId;
    private final boolean hasBarrierBlocks;
    private final boolean hasFloorBlocks;
    private final boolean open;
    private final String source;
    private final long capturedTick;

    public DomainRuntimeSnapshot(UUID runtimeId, UUID ownerUUID, String dimensionId, Vec3 center, double radius,
                                 DomainForm form, DomainForm effectiveForm, int domainId, boolean hasBarrierBlocks,
                                 boolean hasFloorBlocks, boolean open, String source, long capturedTick) {
        this.runtimeId = runtimeId != null ? runtimeId : UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.dimensionId = dimensionId != null ? dimensionId : "";
        this.center = center != null ? center : Vec3.ZERO;
        this.radius = Math.max(1.0, radius);
        this.form = form != null ? form : DomainForm.CLOSED;
        this.effectiveForm = effectiveForm != null ? effectiveForm : this.form;
        this.domainId = domainId;
        this.hasBarrierBlocks = hasBarrierBlocks;
        this.hasFloorBlocks = hasFloorBlocks;
        this.open = open || this.effectiveForm == DomainForm.OPEN;
        this.source = source != null ? source : "unknown";
        this.capturedTick = capturedTick;
    }

    public static DomainRuntimeSnapshot fromGeometry(DomainGeometrySnapshot geometry, boolean hasFloorBlocks, String source) {
        if (geometry == null) return null;
        return new DomainRuntimeSnapshot(UUID.randomUUID(), geometry.getOwnerUUID(), geometry.getDimensionId(), geometry.getCenter(),
                geometry.getRadius(), geometry.getForm(), geometry.getForm(), geometry.getDomainId(), geometry.hasBarrierBlocks(),
                hasFloorBlocks, geometry.isOpenForm(), source, geometry.getTick());
    }

    public DomainGeometrySnapshot toGeometrySnapshot(String sourceOverride) {
        return DomainGeometrySnapshot.of(ownerUUID, dimensionId, center, radius, effectiveForm, domainId, open,
                hasBarrierBlocks, capturedTick, sourceOverride != null ? sourceOverride : source);
    }

    public boolean isValid() { return ownerUUID != null && center != null && radius > 0.0; }
    public boolean isSameOwner(@Nullable UUID uuid) { return ownerUUID != null && ownerUUID.equals(uuid); }
    public UUID getRuntimeId() { return runtimeId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getDimensionId() { return dimensionId; }
    public Vec3 getCenter() { return center; }
    public double getRadius() { return radius; }
    public DomainForm getForm() { return form; }
    public DomainForm getEffectiveForm() { return effectiveForm; }
    public int getDomainId() { return domainId; }
    public boolean hasBarrierBlocks() { return hasBarrierBlocks; }
    public boolean hasFloorBlocks() { return hasFloorBlocks; }
    public boolean isOpen() { return open; }
    public String getSource() { return source; }
    public long getCapturedTick() { return capturedTick; }
}
