package net.mcreator.jujutsucraft.addon.clash.model;

import java.lang.ref.WeakReference;
import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.util.DomainClashConstants;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative state for a single in-flight domain clash between two casters.
 *
 * <p>A {@code ClashSession} is created by {@code ClashDetector} when two candidates that each
 * hold {@code DOMAIN_EXPANSION} first overlap, and is driven by {@code ClashResolver} one tick
 * at a time until it either expires, is cancelled, or is resolved by a dead caster. The two
 * participants are held through {@link WeakReference} so the session does not keep removed or
 * GC-eligible entities alive; the detector and resolver cancel the session as soon as either
 * reference resolves to {@code null} (Requirement 14.4).
 *
 * <p>The pair key, session id, caster references, initial duration, and creation tick are all
 * {@code final} and set at construction. The remaining fields &mdash; {@code remainingTicks},
 * the two {@code Clash_Power} samples, the consecutive-non-overlap counter, the outcome, the
 * resolved flag, and the last sampled tick &mdash; are mutable. Every mutator on this class is
 * documented as "server-tick thread only"; no synchronization is performed because the entire
 * clash pipeline runs from {@code TickEvent.ServerTickEvent} phase {@code END}.
 *
 * <p>Requirements: 3.1, 3.2, 3.5, 5.6, 11.3, 14.2.
 */
public final class ClashSession {

    /** Random UUID used as an idempotency key for XP grants and packet identity. */
    public final UUID sessionId;

    /** Unordered participant pair key. Also the key used by {@code ClashRegistry}. */
    public final ParticipantPair pair;

    /** Weak reference to the first caster (whose UUID equals {@link ParticipantPair#a()}). */
    public final WeakReference<LivingEntity> casterA;

    /** Weak reference to the second caster (whose UUID equals {@link ParticipantPair#b()}). */
    public final WeakReference<LivingEntity> casterB;

    /**
     * The clash duration in server ticks at creation time, including the per-pair mastery bonus
     * computed by {@link #create(ParticipantPair, LivingEntity, LivingEntity, long, int)}.
     */
    public final int initialDurationTicks;

    /** Server tick at which this session was created. Used for the stale-session backstop. */
    public final long createdAtServerTick;

    private int remainingTicks;
    private double clashPowerA;
    private double clashPowerB;
    private int outsideOverlapTicks;
    @Nullable
    private ClashOutcome outcome;
    private boolean resolved;
    private long lastSampledTick;
    private long lastTickedServerTick;

    private ClashSession(
        UUID sessionId,
        ParticipantPair pair,
        WeakReference<LivingEntity> casterA,
        WeakReference<LivingEntity> casterB,
        int initialDurationTicks,
        long createdAtServerTick
    ) {
        this.sessionId = sessionId;
        this.pair = pair;
        this.casterA = casterA;
        this.casterB = casterB;
        this.initialDurationTicks = initialDurationTicks;
        this.createdAtServerTick = createdAtServerTick;
        this.remainingTicks = initialDurationTicks;
        this.clashPowerA = 0.0;
        this.clashPowerB = 0.0;
        this.outsideOverlapTicks = 0;
        this.outcome = null;
        this.resolved = false;
        this.lastSampledTick = createdAtServerTick;
        this.lastTickedServerTick = Long.MIN_VALUE;
    }

    /** Builds a new session for the given participant pair and fixed form-pair duration. */
    public static ClashSession create(
        ParticipantPair pair,
        LivingEntity a,
        LivingEntity b,
        long serverTick,
        int durationTicks
    ) {
        int initial = durationTicks > 0 ? durationTicks : DomainClashConstants.CLASH_DURATION_TICKS_DEFAULT;
        return new ClashSession(
            UUID.randomUUID(),
            pair,
            new WeakReference<>(a),
            new WeakReference<>(b),
            initial,
            serverTick
        );
    }

    /**
     * Returns the number of ticks remaining before this session expires by timer. Decremented
     * exactly once per server tick by {@link #decrementTick()} and clamped at zero so it never
     * becomes negative (Requirement 3.2).
     */
    public int remainingTicks() {
        return remainingTicks;
    }

    /** Returns the last computed {@code Clash_Power} for the caster at {@code pair.a()}. */
    public double clashPowerA() {
        return clashPowerA;
    }

    /** Returns the last computed {@code Clash_Power} for the caster at {@code pair.b()}. */
    public double clashPowerB() {
        return clashPowerB;
    }

    /**
     * Returns the resolved outcome, or {@code null} when the session has not yet been resolved
     * or cancelled.
     */
    @Nullable
    public ClashOutcome outcome() {
        return outcome;
    }

    /**
     * Returns {@code true} once {@link #markResolved(ClashOutcome)} or {@link #markCancelled()}
     * has been called. Subsequent calls to either mutator are no-ops (Requirement 14.2).
     */
    public boolean resolved() {
        return resolved;
    }

    /** Returns the current consecutive-non-overlap tick counter used by the overlap watchdog. */
    public int outsideOverlapTicks() {
        return outsideOverlapTicks;
    }

    /** Returns the server tick at which both powers were last recomputed and broadcast. */
    public long lastSampledTick() {
        return lastSampledTick;
    }

    public boolean markTicked(long serverTick) {
        if (this.lastTickedServerTick == serverTick) {
            return false;
        }
        this.lastTickedServerTick = serverTick;
        return true;
    }

    /**
     * Decrements {@link #remainingTicks()} by one, clamped at zero so repeated calls past
     * expiry cannot drive the timer negative.
     *
     * <p>Server-tick thread only; not synchronized.
     *
     * <p>Requirement 3.2.
     */
    public void decrementTick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /**
     * Overwrites both sampled {@code Clash_Power} values with the results of the latest
     * {@code PowerCalculator.compute} pass.
     *
     * <p>Server-tick thread only; not synchronized.
     *
     * @param a new {@code Clash_Power} for the caster at {@code pair.a()}
     * @param b new {@code Clash_Power} for the caster at {@code pair.b()}
     */
    public void setClashPower(double a, double b) {
        this.clashPowerA = a;
        this.clashPowerB = b;
    }

    /**
     * Increments the consecutive-non-overlap counter by one. Used by the detector to cancel a
     * session when the two domains stay apart for a full sampling interval (Requirement 1.4).
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void incrementOutsideOverlap() {
        outsideOverlapTicks++;
    }

    /**
     * Resets the consecutive-non-overlap counter to zero. Called by the detector as soon as the
     * pair resumes overlap.
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void resetOutsideOverlap() {
        outsideOverlapTicks = 0;
    }

    /**
     * Records the server tick at which the last sampled-sync packet was dispatched for this
     * session.
     *
     * <p>Server-tick thread only; not synchronized.
     *
     * @param tick server tick at which the sampled sync was sent
     */
    public void setLastSampledTick(long tick) {
        this.lastSampledTick = tick;
    }

    /**
     * Flips {@code resolved = true} and stores the supplied outcome. The call is idempotent:
     * once the session is resolved, subsequent calls to this method and to
     * {@link #markCancelled()} are no-ops and leave the stored outcome unchanged
     * (Requirement 14.2).
     *
     * <p>Server-tick thread only; not synchronized.
     *
     * @param outcome the terminal outcome; must be non-null
     */
    public void markResolved(ClashOutcome outcome) {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        this.outcome = outcome;
    }

    /**
     * Convenience mutator equivalent to {@code markResolved(ClashOutcome.CANCELLED)}. The call
     * is idempotent: once the session is resolved (by either cancellation or normal resolution)
     * subsequent calls are no-ops (Requirement 14.2).
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void markCancelled() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        this.outcome = ClashOutcome.CANCELLED;
    }
}
