package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Mutable server-side runtime object representing one active domain in the
 * {@link DomainClashRegistry}.
 *
 * <p>Unlike the immutable {@link DomainParticipantSnapshot} (which is the
 * public query view), this class is the internal storage type that the
 * registry updates on every tick.  It is package-private by design —
 * external callers should use the snapshot returned by
 * {@link DomainClashRegistry#getEntry(UUID)}.</p>
 *
 * <p>Fields are updated in-place by the registry tick to track dynamic
 * state such as current effective power, radius changes, and defeat status.</p>
 */
public final class DomainEntry {

    private final UUID casterUUID;
    private final DomainForm formAtCast;
    private final long startTick;
    private final int domainId;

    // Mutable fields updated per tick
    private String dimensionId;
    private DomainForm form;
    private Vec3 center;
    private Vec3 bodyPosition;
    private double radius;
    private double effectivePower;
    private double barrierRefinement;
    private double sureHitMultiplier;
    private boolean defeated;

    /**
     * Creates a new domain entry from initial registration state.
     *
     * @param casterUUID       unique ID of the caster entity
     * @param form             current domain form
     * @param formAtCast       form locked at cast time
     * @param center           domain center position
     * @param radius           actual addon-modified radius
     * @param effectivePower   initial computed clash power
     * @param startTick        game time when the domain was registered
     * @param domainId         base-mod domain skill ID
     * @param barrierRefinement barrier refinement value
     * @param sureHitMultiplier open-form sure-hit multiplier
     * @param defeated         whether the domain has been defeated
     */
    public DomainEntry(
            UUID casterUUID,
            DomainForm form,
            DomainForm formAtCast,
            Vec3 center,
            double radius,
            double effectivePower,
            long startTick,
            int domainId,
            String dimensionId,
            double barrierRefinement,
            double sureHitMultiplier,
            boolean defeated
    ) {
        this.casterUUID = casterUUID;
        this.form = form;
        this.formAtCast = formAtCast;
        this.center = center;
        this.bodyPosition = center;
        this.radius = radius;
        this.effectivePower = effectivePower;
        this.startTick = startTick;
        this.domainId = domainId;
        this.dimensionId = dimensionId;
        this.barrierRefinement = barrierRefinement;
        this.sureHitMultiplier = sureHitMultiplier;
        this.defeated = defeated;
    }

    /**
     * Constructs a DomainEntry from a {@link DomainParticipantSnapshot}.
     *
     * @param snapshot the immutable snapshot to copy into this mutable entry
     */
    public DomainEntry(DomainParticipantSnapshot snapshot) {
        this(
                snapshot.getCasterUUID(),
                snapshot.getForm(),
                snapshot.getFormAtCast(),
                snapshot.getCenter(),
                snapshot.getRadius(),
                snapshot.getEffectivePower(),
                snapshot.getStartTick(),
                snapshot.getDomainId(),
                "",
                snapshot.getBarrierRefinement(),
                snapshot.getSureHitMultiplier(),
                snapshot.isDefeated()
        );
    }

    // ==================== Getters ====================

    public UUID getCasterUUID() { return casterUUID; }
    public DomainForm getForm() { return form; }
    public DomainForm getFormAtCast() { return formAtCast; }
    public Vec3 getCenter() { return center; }
    public Vec3 getBodyPosition() { return bodyPosition; }
    public double getRadius() { return radius; }
    public double getEffectivePower() { return effectivePower; }
    public long getStartTick() { return startTick; }
    public int getDomainId() { return domainId; }
    public String getDimensionId() { return dimensionId; }
    public double getBarrierRefinement() { return barrierRefinement; }
    public double getSureHitMultiplier() { return sureHitMultiplier; }
    public boolean isDefeated() { return defeated; }

    // ==================== Setters (for tick updates) ====================

    public void setForm(DomainForm form) { this.form = form; }
    public void setCenter(Vec3 center) { this.center = center; }
    public void setBodyPosition(Vec3 bodyPosition) { this.bodyPosition = bodyPosition; }
    public void setRadius(double radius) { this.radius = radius; }
    public void setEffectivePower(double effectivePower) { this.effectivePower = effectivePower; }
    public void setDimensionId(String dimensionId) { this.dimensionId = dimensionId; }
    public void setBarrierRefinement(double barrierRefinement) { this.barrierRefinement = barrierRefinement; }
    public void setSureHitMultiplier(double sureHitMultiplier) { this.sureHitMultiplier = sureHitMultiplier; }
    public void setDefeated(boolean defeated) { this.defeated = defeated; }

    /**
     * Creates an immutable {@link DomainParticipantSnapshot} view of the
     * current mutable state.
     *
     * @return a new snapshot reflecting the current entry state
     */
    public DomainParticipantSnapshot toSnapshot() {
        return new DomainParticipantSnapshot(
                casterUUID, form, formAtCast, center, radius,
                effectivePower, startTick, domainId, barrierRefinement,
                sureHitMultiplier, defeated
        );
    }

    /**
     * Computes the power/scoring clash range for this domain entry, using the centralized
     * constants from {@link DomainClashConstants}.
     *
     * @return the base clash range in blocks
     */
    public double computeClashRange() {
        double r = Math.max(1.0, radius);
        boolean isOpen = (form == DomainForm.OPEN);
        boolean isIncomplete = (form == DomainForm.INCOMPLETE);
        double baseRange = r * (isOpen
                ? DomainClashConstants.OPEN_CLASH_RANGE_MULTIPLIER
                : DomainClashConstants.CLOSED_CLASH_RANGE_MULTIPLIER);
        // Incomplete domains get a minimum clash range floor to ensure
        // Inc-vs-Inc overlap detection works even for very small domains.
        if (isIncomplete) {
            baseRange = Math.max(baseRange, 16.0);
        }
        return baseRange;
    }

    /**
     * Computes the practical OG-like scan range for eligibility/overlap only.
     *
     * <p>Closed and incomplete domains scan around their actual radius. Open domains mirror the
     * original {@code range = radius * 18} with AABB inflate {@code range / 2}, i.e. radius * 9.</p>
     */
    public double computePracticalScanRange() {
        double r = Math.max(1.0, radius);
        return form == DomainForm.OPEN ? r * 9.0 : r;
    }

    @Override
    public String toString() {
        return "DomainEntry{" +
                "caster=" + casterUUID +
                ", form=" + form +
                ", power=" + String.format("%.2f", effectivePower) +
                ", radius=" + String.format("%.1f", radius) +
                ", defeated=" + defeated +
                '}';
    }
}
