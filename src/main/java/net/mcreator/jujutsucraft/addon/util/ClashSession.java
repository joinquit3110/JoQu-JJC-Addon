package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;

/**
 * Mutable pairwise clash session tracking object used inside
 * {@link DomainClashRegistry}.
 *
 * <p>Each session represents one active clash between exactly two domain
 * participants. For N overlapping domains, the registry creates N*(N-1)/2
 * pairwise sessions, each with independent pressure tracking.</p>
 *
 * <p>The canonical ordering convention places the "higher" form as
 * participant A when forms differ (e.g. in OPEN_VS_CLOSED, A is the open
 * caster). When forms are equal, the first-registered participant is A.</p>
 *
 * <p>External callers read this state through {@link ClashSessionSnapshot}
 * returned by {@link #toSnapshot()}.</p>
 */
public final class ClashSession {

    private final UUID sessionId;
    private final UUID participantA;
    private final UUID participantB;
    private final DomainForm formA;
    private final DomainForm formB;
    private final ClashType sessionType;
    private final long startTick;

    // Mutable state updated per tick
    private double accumulatedPressureA;
    private double accumulatedPressureB;
    private long lastActiveTick;
    private boolean resolved;
    private ClashOutcome outcome;
    private long pendingTick;
    private UUID pendingLoser;
    private long resolvedTick;
    private boolean deliveredToA;
    private boolean deliveredToB;

    /**
     * Creates a new clash session between two participants.
     *
     * @param participantA UUID of the first caster (canonical ordering: higher form)
     * @param participantB UUID of the second caster
     * @param formA        domain form of participant A at session creation
     * @param formB        domain form of participant B at session creation
     * @param startTick    game time when the session was created
     */
    public ClashSession(
            UUID participantA,
            UUID participantB,
            DomainForm formA,
            DomainForm formB,
            long startTick
    ) {
        this.sessionId = UUID.randomUUID();
        this.participantA = participantA;
        this.participantB = participantB;
        this.formA = formA;
        this.formB = formB;
        this.sessionType = ClashType.derive(formA, formB);
        this.startTick = startTick;
        this.lastActiveTick = startTick;
        this.accumulatedPressureA = 0.0;
        this.accumulatedPressureB = 0.0;
        this.resolved = false;
        this.outcome = ClashOutcome.PENDING;
        this.pendingTick = -1L;
        this.pendingLoser = null;
        this.resolvedTick = -1L;
        this.deliveredToA = false;
        this.deliveredToB = false;
    }

    // ==================== Getters ====================

    public UUID getSessionId() { return sessionId; }
    public UUID getParticipantA() { return participantA; }
    public UUID getParticipantB() { return participantB; }
    public DomainForm getFormA() { return formA; }
    public DomainForm getFormB() { return formB; }
    public ClashType getSessionType() { return sessionType; }
    public long getStartTick() { return startTick; }
    public double getAccumulatedPressureA() { return accumulatedPressureA; }
    public double getAccumulatedPressureB() { return accumulatedPressureB; }
    public long getLastActiveTick() { return lastActiveTick; }
    public boolean isResolved() { return resolved; }
    public ClashOutcome getOutcome() { return outcome; }
    public long getPendingTick() { return pendingTick; }
    public long getResolvedTick() { return resolvedTick; }

    // ==================== Setters ====================

    public void setAccumulatedPressureA(double v) { this.accumulatedPressureA = v; }
    public void setAccumulatedPressureB(double v) { this.accumulatedPressureB = v; }
    public void addPressureToA(double delta) { this.accumulatedPressureA += delta; }
    public void addPressureToB(double delta) { this.accumulatedPressureB += delta; }
    public void setLastActiveTick(long tick) { this.lastActiveTick = tick; }

    /**
     * Starts or refreshes the pending outcome window for a provisional loser.
     *
     * @param loserUuid the participant currently considered the loser
     * @param tick      the game tick when the pending window began
     */
    public void startPending(UUID loserUuid, long tick) {
        this.pendingLoser = loserUuid;
        this.pendingTick = tick;
        if (!this.resolved) {
            this.outcome = ClashOutcome.PENDING;
        }
    }

    /** Clears any pending outcome state so the session can keep accumulating. */
    public void clearPending() {
        this.pendingLoser = null;
        this.pendingTick = -1L;
        if (!this.resolved) {
            this.outcome = ClashOutcome.PENDING;
        }
    }

    /** Returns whether the session is currently inside a pending-result window. */
    public boolean hasPendingOutcome() {
        return this.pendingTick >= 0L && this.pendingLoser != null && !this.resolved;
    }

    /**
     * Returns the provisional loser UUID for the current pending window.
     *
     * @return the provisional loser UUID, or {@code null} when not pending
     */
    public UUID getPendingLoser() {
        return this.pendingLoser;
    }

    /**
     * Marks this session as resolved with the given outcome.
     *
     * @param outcome the final clash outcome
     */
    public void resolve(ClashOutcome outcome) {
        this.resolve(outcome, -1L);
    }

    /**
     * Marks this session as resolved with the given outcome and resolution tick.
     *
     * @param outcome      the final clash outcome
     * @param resolvedTick the game tick when the session was resolved
     */
    public void resolve(ClashOutcome outcome, long resolvedTick) {
        this.resolved = true;
        this.outcome = outcome;
        this.resolvedTick = resolvedTick;
        this.pendingLoser = null;
        this.pendingTick = -1L;
    }

    /**
     * Claims delivery of this resolved session for the given participant.
     *
     * @param participantUuid the participant receiving XP/message processing
     * @return {@code true} only once per participant after the session is resolved
     */
    public boolean claimResolvedDelivery(UUID participantUuid) {
        if (!this.resolved || participantUuid == null) {
            return false;
        }
        if (participantA.equals(participantUuid)) {
            if (deliveredToA) {
                return false;
            }
            deliveredToA = true;
            return true;
        }
        if (participantB.equals(participantUuid)) {
            if (deliveredToB) {
                return false;
            }
            deliveredToB = true;
            return true;
        }
        return false;
    }

    /** Marks this resolved session as fully delivered so stale cleanup can discard it. */
    public void markFullyDelivered() {
        if (!this.resolved) {
            return;
        }
        this.deliveredToA = true;
        this.deliveredToB = true;
    }

    /**
     * Returns whether both participants have already consumed the resolved result.
     */
    public boolean isFullyDelivered() {
        return !this.resolved || (this.deliveredToA && this.deliveredToB);
    }

    // ==================== Query helpers ====================

    /**
     * Checks whether the given UUID is one of the two participants.
     *
     * @param uuid the UUID to check
     * @return {@code true} if the UUID matches participant A or B
     */
    public boolean involves(UUID uuid) {
        return participantA.equals(uuid) || participantB.equals(uuid);
    }

    /**
     * Returns the UUID of the opponent for the given participant.
     *
     * @param selfUUID the UUID of one participant
     * @return the UUID of the other participant, or {@code null} if not a participant
     */
    public UUID getOpponent(UUID selfUUID) {
        if (participantA.equals(selfUUID)) return participantB;
        if (participantB.equals(selfUUID)) return participantA;
        return null;
    }

    /**
     * Returns the accumulated pressure on the given participant (i.e. the
     * damage dealt TO them by their opponent in this session).
     *
     * @param uuid the participant whose received pressure is queried
     * @return the accumulated pressure, or 0.0 if not a participant
     */
    public double getPressureOn(UUID uuid) {
        if (participantA.equals(uuid)) return accumulatedPressureA;
        if (participantB.equals(uuid)) return accumulatedPressureB;
        return 0.0;
    }

    /**
     * Adds pressure to the given participant (i.e. the damage dealt TO them).
     *
     * @param uuid  the participant receiving pressure
     * @param delta the pressure delta to add
     */
    public void addPressureTo(UUID uuid, double delta) {
        if (participantA.equals(uuid)) {
            accumulatedPressureA += delta;
        } else if (participantB.equals(uuid)) {
            accumulatedPressureB += delta;
        }
    }

    /**
     * Returns the form of the given participant in this session.
     *
     * @param uuid the participant to query
     * @return the form at session creation, or {@code null} if not a participant
     */
    public DomainForm getFormOf(UUID uuid) {
        if (participantA.equals(uuid)) return formA;
        if (participantB.equals(uuid)) return formB;
        return null;
    }

    /**
     * Returns the form of the opponent of the given participant.
     *
     * @param selfUUID the participant whose opponent form is queried
     * @return the opponent form, or {@code null} if not a participant
     */
    public DomainForm getOpponentForm(UUID selfUUID) {
        if (participantA.equals(selfUUID)) return formB;
        if (participantB.equals(selfUUID)) return formA;
        return null;
    }

    /**
     * Creates an immutable {@link ClashSessionSnapshot} view of the
     * current session state.
     *
     * @return a new snapshot reflecting the current session state
     */
    public ClashSessionSnapshot toSnapshot() {
        return new ClashSessionSnapshot(
                sessionId, participantA, participantB,
                formA, formB, sessionType,
                accumulatedPressureA, accumulatedPressureB,
                startTick, lastActiveTick,
                resolved, outcome
        );
    }

    @Override
    public String toString() {
        return "ClashSession{" +
                "id=" + sessionId +
                ", type=" + sessionType +
                ", A=" + participantA +
                ", B=" + participantB +
                ", pressureA=" + String.format("%.2f", accumulatedPressureA) +
                ", pressureB=" + String.format("%.2f", accumulatedPressureB) +
                ", pendingTick=" + pendingTick +
                ", pendingLoser=" + pendingLoser +
                ", resolvedTick=" + resolvedTick +
                ", outcome=" + outcome +
                '}';
    }
}
