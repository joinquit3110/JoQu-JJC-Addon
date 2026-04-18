package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable snapshot of a single domain participant's state at a point in time.
 *
 * <p>This lightweight value object captures everything the clash registry needs
 * to know about one side of a clash without holding a live entity reference.
 * It is designed to be created once (at registration or per-tick refresh) and
 * then read by session logic.</p>
 *
 * <p><b>Phase 1 note:</b> this class is defined but not yet populated by
 * production code.  Phase 2 will create instances inside
 * {@code DomainClashRegistry.registerDomain()} and tick refreshes.</p>
 */
public final class DomainParticipantSnapshot {

    private final UUID casterUUID;
    private final DomainForm form;
    private final DomainForm formAtCast;
    private final Vec3 center;
    private final double radius;
    private final double effectivePower;
    private final long startTick;
    private final int domainId;
    private final double barrierRefinement;
    private final double sureHitMultiplier;
    private final boolean defeated;

    /**
     * Creates a new participant snapshot.
     *
     * @param casterUUID       unique ID of the caster entity
     * @param form             current domain form
     * @param formAtCast       form locked at cast time (never changes)
     * @param center           domain center position
     * @param radius           actual addon-modified radius
     * @param effectivePower   latest computed clash power
     * @param startTick        game time when the domain was registered
     * @param domainId         base-mod domain skill ID
     * @param barrierRefinement barrier refinement value (from mastery or NPC default)
     * @param sureHitMultiplier open-form sure-hit multiplier (ignored for non-open)
     * @param defeated         whether the domain has been defeated
     */
    public DomainParticipantSnapshot(
            UUID casterUUID,
            DomainForm form,
            DomainForm formAtCast,
            Vec3 center,
            double radius,
            double effectivePower,
            long startTick,
            int domainId,
            double barrierRefinement,
            double sureHitMultiplier,
            boolean defeated
    ) {
        this.casterUUID = casterUUID;
        this.form = form;
        this.formAtCast = formAtCast;
        this.center = center;
        this.radius = radius;
        this.effectivePower = effectivePower;
        this.startTick = startTick;
        this.domainId = domainId;
        this.barrierRefinement = barrierRefinement;
        this.sureHitMultiplier = sureHitMultiplier;
        this.defeated = defeated;
    }

    /** Returns the unique ID of the caster entity. */
    public UUID getCasterUUID() {
        return this.casterUUID;
    }

    /** Returns the current domain form. */
    public DomainForm getForm() {
        return this.form;
    }

    /** Returns the form that was locked at cast time. */
    public DomainForm getFormAtCast() {
        return this.formAtCast;
    }

    /** Returns the domain center position. */
    public Vec3 getCenter() {
        return this.center;
    }

    /** Returns the actual addon-modified radius. */
    public double getRadius() {
        return this.radius;
    }

    /** Returns the latest computed effective clash power. */
    public double getEffectivePower() {
        return this.effectivePower;
    }

    /** Returns the game time when the domain was registered. */
    public long getStartTick() {
        return this.startTick;
    }

    /** Returns the base-mod domain skill ID. */
    public int getDomainId() {
        return this.domainId;
    }

    /** Returns the barrier refinement value. */
    public double getBarrierRefinement() {
        return this.barrierRefinement;
    }

    /** Returns the open-form sure-hit multiplier. */
    public double getSureHitMultiplier() {
        return this.sureHitMultiplier;
    }

    /** Returns whether this domain has been defeated. */
    public boolean isDefeated() {
        return this.defeated;
    }

    @Override
    public String toString() {
        return "DomainParticipantSnapshot{" +
                "caster=" + this.casterUUID +
                ", form=" + this.form +
                ", power=" + String.format("%.2f", this.effectivePower) +
                ", radius=" + String.format("%.1f", this.radius) +
                ", defeated=" + this.defeated +
                '}';
    }
}
