package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;

/**
 * Immutable snapshot of a single pairwise clash session between two domains.
 *
 * <p>Each session tracks two participants (A and B), their forms at session
 * creation, accumulated pressure on each side, and the current outcome state.
 * This is the data-model counterpart of the full mutable {@code ClashSession}
 * that will be introduced in Phase 2.</p>
 *
 * <p><b>Phase 1 note:</b> this class exists as a read-only view for future
 * query APIs (e.g. {@code DomainClashRegistry.getSessionsFor()}).  It is
 * not yet populated by any production path.</p>
 */
public final class ClashSessionSnapshot {

    private final UUID sessionId;
    private final UUID participantA;
    private final UUID participantB;
    private final DomainForm formA;
    private final DomainForm formB;
    private final ClashType sessionType;
    private final double accumulatedPressureA;
    private final double accumulatedPressureB;
    private final long startTick;
    private final long lastActiveTick;
    private final boolean resolved;
    private final ClashOutcome outcome;

    /**
     * Creates a new clash session snapshot.
     *
     * @param sessionId            unique session identifier
     * @param participantA         UUID of the first caster
     * @param participantB         UUID of the second caster
     * @param formA                domain form of participant A at session creation
     * @param formB                domain form of participant B at session creation
     * @param sessionType          derived clash type for this form pairing
     * @param accumulatedPressureA pressure dealt to A by B
     * @param accumulatedPressureB pressure dealt to B by A
     * @param startTick            game time when the session was created
     * @param lastActiveTick       last tick both participants were confirmed alive
     * @param resolved             whether the outcome has been determined
     * @param outcome              the current clash outcome
     */
    public ClashSessionSnapshot(
            UUID sessionId,
            UUID participantA,
            UUID participantB,
            DomainForm formA,
            DomainForm formB,
            ClashType sessionType,
            double accumulatedPressureA,
            double accumulatedPressureB,
            long startTick,
            long lastActiveTick,
            boolean resolved,
            ClashOutcome outcome
    ) {
        this.sessionId = sessionId;
        this.participantA = participantA;
        this.participantB = participantB;
        this.formA = formA;
        this.formB = formB;
        this.sessionType = sessionType;
        this.accumulatedPressureA = accumulatedPressureA;
        this.accumulatedPressureB = accumulatedPressureB;
        this.startTick = startTick;
        this.lastActiveTick = lastActiveTick;
        this.resolved = resolved;
        this.outcome = outcome;
    }

    /** Returns the unique session identifier. */
    public UUID getSessionId() {
        return this.sessionId;
    }

    /** Returns the UUID of participant A. */
    public UUID getParticipantA() {
        return this.participantA;
    }

    /** Returns the UUID of participant B. */
    public UUID getParticipantB() {
        return this.participantB;
    }

    /** Returns participant A's domain form at session creation. */
    public DomainForm getFormA() {
        return this.formA;
    }

    /** Returns participant B's domain form at session creation. */
    public DomainForm getFormB() {
        return this.formB;
    }

    /** Returns the derived clash type for this pairing. */
    public ClashType getSessionType() {
        return this.sessionType;
    }

    /** Returns pressure accumulated on participant A (dealt by B). */
    public double getAccumulatedPressureA() {
        return this.accumulatedPressureA;
    }

    /** Returns pressure accumulated on participant B (dealt by A). */
    public double getAccumulatedPressureB() {
        return this.accumulatedPressureB;
    }

    /** Returns the game time when the session started. */
    public long getStartTick() {
        return this.startTick;
    }

    /** Returns the last tick both participants were confirmed alive. */
    public long getLastActiveTick() {
        return this.lastActiveTick;
    }

    /** Returns whether the outcome has been determined. */
    public boolean isResolved() {
        return this.resolved;
    }

    /** Returns the current clash outcome. */
    public ClashOutcome getOutcome() {
        return this.outcome;
    }

    /**
     * Checks whether the given UUID is one of the two participants.
     *
     * @param uuid the UUID to check
     * @return {@code true} if the UUID matches participant A or B
     */
    public boolean involves(UUID uuid) {
        return this.participantA.equals(uuid) || this.participantB.equals(uuid);
    }

    /**
     * Returns the UUID of the opponent for the given participant.
     *
     * @param selfUUID the UUID of one participant
     * @return the UUID of the other participant, or {@code null} if not a participant
     */
    public UUID getOpponent(UUID selfUUID) {
        if (this.participantA.equals(selfUUID)) {
            return this.participantB;
        }
        if (this.participantB.equals(selfUUID)) {
            return this.participantA;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ClashSessionSnapshot{" +
                "id=" + this.sessionId +
                ", type=" + this.sessionType +
                ", A=" + this.participantA +
                ", B=" + this.participantB +
                ", outcome=" + this.outcome +
                '}';
    }
}
