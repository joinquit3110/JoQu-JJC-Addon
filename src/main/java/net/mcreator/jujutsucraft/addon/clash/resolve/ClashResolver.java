package net.mcreator.jujutsucraft.addon.clash.resolve;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.POWER_DIFF_TIE_THRESHOLD;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.SAMPLING_INTERVAL_TICKS_DEFAULT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.clash.ClashRegistry;
import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.clash.model.ParticipantSnapshot;
import net.mcreator.jujutsucraft.addon.clash.power.PowerCalculator;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative resolver for the in-flight {@link ClashSession} population.
 *
 * <p>This class drives every session once per server tick through
 * {@link #tickSessions(ServerLevel, long)}, and owns the three terminal transitions
 * {@link #resolveTimerExpiry(ClashSession, ServerLevel)},
 * {@link #resolveDeath(ClashSession, ServerLevel, LivingEntity)}, and
 * {@link #cancel(ClashSession, ServerLevel)}. Only this class writes terminal state to
 * {@link ClashSession} (other than the detector, which calls {@link #cancel} for its own
 * overlap-lost / stale-session / dead-reference paths).
 *
 * <h2>Networking indirection</h2>
 * The sampled/final/cancelled packet dispatch is pushed through the nested {@link SyncSink}
 * interface rather than a direct dependency on {@code ClashSyncNetwork}. That class is
 * introduced later (task 11.3) and implements {@link SyncSink}; until then the
 * {@link SyncSink#NOOP} stub is used so this class compiles standalone and can be unit tested
 * without a live Forge network channel. Callers that wire the real dispatcher pass a
 * {@code ClashSyncNetwork} instance through the four-arg constructor.
 *
 * <h2>Threading</h2>
 * Invoked exclusively from {@code TickEvent.ServerTickEvent} phase {@code END}. This class
 * holds no synchronization; every method assumes the server-tick thread.
 *
 * <p>Requirements: 3.2, 3.3, 4.1, 5.1, 9.2, 14.2, 14.4.
 */
public final class ClashResolver {

    /**
     * Decouples {@link ClashResolver} from the concrete {@code ClashSyncNetwork} (task 11.3).
     *
     * <p>Implementations dispatch each call to the two participating {@code ServerPlayer}s of
     * a session; non-participants never receive these packets (Requirement 9.3). The
     * {@link #NOOP} instance is used when no dispatcher has been wired (pre-task-11.3 builds,
     * unit tests that only need the core resolver contract).
     */
    public interface SyncSink {
        /** Dispatched when a session is first created; see Requirement 9.1. */
        void sendInitial(ClashSession session);

        /** Dispatched at each sampling tick; see Requirements 4.1 and 9.2. */
        void sendSampled(ClashSession session);

        /** Dispatched when a session resolves with a terminal outcome; see Requirement 5.7. */
        void sendFinal(ClashSession session);

        /** Dispatched when a session is cancelled; see Requirement 11.4. */
        void sendCancelled(ClashSession session);

        /** No-op sink used when no network dispatcher has been wired yet. */
        SyncSink NOOP = new SyncSink() {
            @Override public void sendInitial(ClashSession session) {}
            @Override public void sendSampled(ClashSession session) {}
            @Override public void sendFinal(ClashSession session) {}
            @Override public void sendCancelled(ClashSession session) {}
        };
    }

    private final ClashRegistry registry;
    private final OutcomeDelivery delivery;
    private final MasteryXpGrant xpGrant;
    private final SyncSink syncSink;

    /**
     * Constructs a resolver backed by the given registry, delivery, and XP-grant collaborators
     * and a {@link SyncSink#NOOP} network stub. This overload lets the class be wired into
     * {@link ClashRegistry}-dependent code paths before the real {@code ClashSyncNetwork} exists
     * (task 11.3).
     */
    public ClashResolver(ClashRegistry registry, OutcomeDelivery delivery, MasteryXpGrant xpGrant) {
        this(registry, delivery, xpGrant, SyncSink.NOOP);
    }

    /**
     * Constructs a resolver with an explicit {@link SyncSink}. Task 15.1 wires the real
     * {@code ClashSyncNetwork} through this constructor; callers that pass {@code null} get the
     * {@link SyncSink#NOOP} stub automatically so tick-time network calls are never null-checked
     * at the call site.
     */
    public ClashResolver(
        ClashRegistry registry,
        OutcomeDelivery delivery,
        MasteryXpGrant xpGrant,
        @Nullable SyncSink syncSink
    ) {
        this.registry = registry;
        this.delivery = delivery;
        this.xpGrant = xpGrant;
        this.syncSink = syncSink == null ? SyncSink.NOOP : syncSink;
    }

    /**
     * Drives every active session one server tick forward.
     *
     * <h2>Algorithm</h2>
     * For each active unresolved session (Requirement 14.2 idempotency guard, Requirement 14.4
     * liveness guard):
     * <ol>
     *   <li>If either {@code WeakReference} has been cleared or resolves to a removed or
     *       dead entity, cancel the session (Requirement 14.4) and continue.</li>
     *   <li>Decrement {@code remainingTicks} by one (Requirement 3.2).</li>
     *   <li>If {@code remainingTicks == 0}, run {@link #resolveTimerExpiry} immediately
     *       (Requirements 3.3 and 5.1). Same-tick timer expiry wins over any subsequent
     *       cancellation check that would otherwise run this tick.</li>
     *   <li>Otherwise, if {@code remainingTicks % SAMPLING_INTERVAL_TICKS_DEFAULT == 0},
     *       recompute both {@code Clash_Power} values via
     *       {@link PowerCalculator#compute(ParticipantSnapshot)} on fresh snapshots and emit a
     *       sampled sync packet through {@link SyncSink#sendSampled} (Requirements 4.1 and
     *       9.2). If either snapshot returns {@code null} (the caster has since dropped
     *       {@code DOMAIN_EXPANSION} or died), cancel the session — the participant is no
     *       longer eligible for a clash.</li>
     * </ol>
     *
     * <p>The active-session collection is snapshotted into an {@link ArrayList} before
     * iteration so that the terminal transitions invoked inside the loop (cancellation on
     * dead reference, timer expiry) can mutate {@link ClashRegistry#remove(net.mcreator.jujutsucraft.addon.clash.model.ParticipantPair)}
     * without tripping a {@link java.util.ConcurrentModificationException}.
     *
     * <p>Requirements: 3.2, 3.3, 4.1, 5.1, 9.2, 14.2, 14.4.
     *
     * @param level      the server level whose sessions are being ticked; used to read the
     *                   current {@code DomainExpansionRadius} map variable when capturing
     *                   snapshots and to supply a {@code ServerLevel} parameter to the terminal
     *                   transitions
     * @param serverTick the current server tick, forwarded into
     *                   {@link ClashSession#setLastSampledTick(long)} when a sampling pass runs
     */
    public void tickSessions(ServerLevel level, long serverTick) {
        Collection<ClashSession> active = registry.activeSessions();
        if (active.isEmpty()) {
            return;
        }

        // Snapshot the session collection so terminal transitions (cancel / resolveTimerExpiry)
        // can mutate the registry inside the loop without CME.
        List<ClashSession> snapshot = new ArrayList<>(active);
        double mapRadius = readMapRadius(level);

        for (ClashSession session : snapshot) {
            // Req 14.2: idempotency guard - a session resolved earlier this tick by a
            // synchronous event callback (e.g. resolveDeath) is skipped here.
            if (session.resolved()) {
                continue;
            }
            if (!session.markTicked(serverTick)) {
                continue;
            }

            // Req 14.4: cancel on dead WeakReference before any other work.
            LivingEntity casterA = session.casterA.get();
            LivingEntity casterB = session.casterB.get();
            if (casterA == null || casterB == null) {
                cancel(session, level);
                continue;
            }

            // Step 1: decrement (Req 3.2).
            session.decrementTick();

            // Step 2: expiry takes precedence over sampling (Req 3.3 + Req 5.1).
            if (session.remainingTicks() == 0) {
                resolveTimerExpiry(session, level);
                continue;
            }

            // Step 3: sample at the configured cadence (Req 4.1 + Req 9.2).
            if (session.remainingTicks() % SAMPLING_INTERVAL_TICKS_DEFAULT == 0) {
                ParticipantSnapshot snapA = ParticipantSnapshot.capture(casterA, mapRadius);
                ParticipantSnapshot snapB = ParticipantSnapshot.capture(casterB, mapRadius);
                // A null snapshot means the caster lost DOMAIN_EXPANSION or died between the
                // liveness guard above and the capture call; treat it as a liveness failure
                // (Req 14.4) rather than continuing to sample stale state.
                if (snapA != null && snapB != null) {
                    double powerA = PowerCalculator.compute(snapA, snapB.form());
                    double powerB = PowerCalculator.compute(snapB, snapA.form());
                    session.setClashPower(powerA, powerB);
                }
                session.setLastSampledTick(serverTick);
                syncSink.sendSampled(session);
            }
        }
    }

    /**
     * Resolves {@code session} as a timer expiry.
     *
     * <h2>Algorithm</h2>
     * <ol>
     *   <li>Short-circuit when {@link ClashSession#resolved()} is {@code true} so a session
     *       that was already resolved or cancelled earlier this tick (e.g. by a synchronous
     *       death callback) is not resolved a second time (Requirement 14.2 idempotency).</li>
     *   <li>Re-check the two {@code WeakReference}s; if either has been cleared, route through
     *       {@link #cancel} instead (Requirement 14.4). No outcome is emitted and no loser
     *       effects are applied on this path.</li>
     *   <li>Capture fresh {@link ParticipantSnapshot}s for both casters and recompute
     *       {@link PowerCalculator#compute(ParticipantSnapshot)}. These are stored on the
     *       session via {@link ClashSession#setClashPower(double, double)} so the final sync
     *       packet reflects the values actually used for the outcome decision
     *       (Requirement 5.1). A {@code null} snapshot means the caster lost
     *       {@code DOMAIN_EXPANSION} or died between the tick-loop liveness guard and this
     *       call; fall back to {@link #cancel} for the same reason as the dead-reference path.</li>
     *   <li>Compute {@code diff = clashPowerA - clashPowerB} and select the outcome:
     *       {@link ClashOutcome#TIE} when {@code |diff| < POWER_DIFF_TIE_THRESHOLD} (Requirement
     *       5.3), {@link ClashOutcome#WINNER_A} when {@code diff >= POWER_DIFF_TIE_THRESHOLD},
     *       {@link ClashOutcome#WINNER_B} otherwise (Requirement 5.2). The tie branch wins over
     *       a strictly-equal {@code diff} because the comparison is strictly less-than on the
     *       threshold.</li>
     *   <li>Apply {@link OutcomeDelivery#applyLoserEffects(LivingEntity, long)} to the loser,
     *       or to both casters for {@link ClashOutcome#TIE} (Requirements 5.4 and 5.5). For
     *       non-tie outcomes, stamp the winner's {@code jjkbrp_clash_result_tick} via
     *       {@link OutcomeDelivery#markResultTick(LivingEntity, long)} so downstream addon
     *       subsystems observe the resolution tick on both participants (Requirement 7.7
     *       delegate from the caller, keeping the winner's {@code Failed}/{@code Cover}/
     *       {@code DomainDefeated}/{@code select}/{@code skill_domain}/{@code x_pos_doma}/
     *       {@code y_pos_doma}/{@code z_pos_doma} untouched per Requirement 7.4).</li>
     *   <li>Grant mastery XP via {@link MasteryXpGrant#grant(ClashSession, ClashOutcome)}
     *       (Requirements 6.6 and 6.7). The grant class handles per-(session, participant)
     *       idempotency and the non-player short-circuit internally.</li>
     *   <li>Flip {@link ClashSession#markResolved(ClashOutcome)} (Requirement 5.6), emit
     *       {@link SyncSink#sendFinal(ClashSession)} (Requirement 5.7), and remove the pair
     *       from the registry.</li>
     *   <li>For a non-tie outcome only, walk
     *       {@link ClashRegistry#sessionsContaining(UUID)} for the loser's UUID and cascade-
     *       cancel every <em>other</em> session the loser is still in, on the same tick
     *       (Requirement 10.3). The loser's own session was already removed from the registry
     *       above, so the cascade cannot re-enter this method. {@code TIE} outcomes do not
     *       cascade (Requirement 10.4): both participants are losers, and the base mod's
     *       expire procedure will take care of their other domain mobs on the next tick.</li>
     * </ol>
     *
     * <p>Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 10.3, 10.4, 14.2.
     *
     * @param session the session whose timer reached zero
     * @param level   the server level the session belongs to
     */
    public void resolveTimerExpiry(ClashSession session, ServerLevel level) {
        // Req 14.2: idempotency - a session resolved earlier this tick is a no-op.
        if (session.resolved()) {
            return;
        }

        // Req 14.4: cancel on dead WeakReference; no outcome, no loser effects.
        LivingEntity casterA = session.casterA.get();
        LivingEntity casterB = session.casterB.get();
        if (casterA == null || casterB == null) {
            cancel(session, level);
            return;
        }

        // Req 5.1: recompute both Clash_Power values from fresh snapshots so the outcome
        // decision and the final sync packet use the values computed at resolution time.
        double mapRadius = readMapRadius(level);
        ParticipantSnapshot snapA = ParticipantSnapshot.capture(casterA, mapRadius);
        ParticipantSnapshot snapB = ParticipantSnapshot.capture(casterB, mapRadius);
        double powerA = session.clashPowerA();
        double powerB = session.clashPowerB();
        if (snapA != null && snapB != null) {
            powerA = PowerCalculator.compute(snapA, snapB.form());
            powerB = PowerCalculator.compute(snapB, snapA.form());
            session.setClashPower(powerA, powerB);
        }

        // Req 5.2 + 5.3: select outcome using the tie threshold. Strictly-less-than means a
        // diff that equals the threshold resolves as a decisive winner, not a tie.
        ClashOutcome outcome = selectOutcomeByPowerDiff(powerA, powerB);

        // Req 5.4 + 5.5: apply loser effects. TIE applies to both casters; decisive outcomes
        // stamp the winner's result tick via markResultTick to preserve Requirement 7.4
        // (winner's other NBT keys untouched).
        long serverTick = level.getServer() != null ? level.getServer().getTickCount() : 0L;
        switch (outcome) {
            case WINNER_A -> {
                delivery.applyLoserEffects(casterB, serverTick);
                delivery.markResultTick(casterA, serverTick);
                delivery.preserveOpenWinnerSureHit(casterA, serverTick);
            }
            case WINNER_B -> {
                delivery.applyLoserEffects(casterA, serverTick);
                delivery.markResultTick(casterB, serverTick);
                delivery.preserveOpenWinnerSureHit(casterB, serverTick);
            }
            case TIE -> {
                delivery.applyLoserEffects(casterA, serverTick);
                delivery.applyLoserEffects(casterB, serverTick);
            }
            case CANCELLED -> {
                // Unreachable: cancel() is the only path that yields CANCELLED, and it
                // returns before reaching this switch.
            }
        }

        // Req 6.6 + 6.7: grant mastery XP through the idempotent grant sink.
        xpGrant.grant(session, outcome);

        // Req 5.6 + 5.7: mark resolved, emit the final sync packet, drop from the registry.
        session.markResolved(outcome);
        syncSink.sendFinal(session);
        registry.remove(session.pair);

        // Req 10.3: cascade-cancel every other session the loser is still in, on the same
        // tick. TIE does not cascade (Req 10.4) - both participants are losers and the base
        // mod's expire procedure will handle any subsequent mob clean-up.
        if (outcome == ClashOutcome.WINNER_A || outcome == ClashOutcome.WINNER_B) {
            UUID loserUuid = outcome == ClashOutcome.WINNER_A ? session.pair.b() : session.pair.a();
            // Snapshot the "other sessions" list: cancel() mutates the registry, and the
            // registry's sessionsContaining() view is a fresh ArrayList already, but we copy
            // defensively so this code is robust to future reverse-index optimizations.
            List<ClashSession> others = new ArrayList<>(registry.sessionsContaining(loserUuid));
            for (ClashSession other : others) {
                cancel(other, level);
            }
        }
    }

    /**
     * Resolves {@code session} because one (or both) casters have reached
     * {@code isRemoved() == true} or {@code isDeadOrDying() == true}.
     *
     * <h2>Algorithm</h2>
     * <ol>
     *   <li>Short-circuit when {@link ClashSession#resolved()} is {@code true} so a death event
     *       that races a same-tick timer expiry or cancellation does not resolve a second time
     *       (Requirement 14.2 idempotency).</li>
     *   <li>Re-derive {@code aDead}/{@code bDead} from both casters independently of
     *       {@code deadCaster}: either {@link WeakReference} may resolve to {@code null} (the
     *       entity has already been unloaded) and either live reference may report dead via
     *       {@link LivingEntity#isRemoved()} or {@link LivingEntity#isDeadOrDying()}. The
     *       parameter {@code deadCaster} is accepted for caller context only; the decision is
     *       driven by actual liveness of both participants so Requirement 12.3 (both dead same
     *       tick &rarr; {@code TIE}) is detected regardless of which {@code LivingDeathEvent}
     *       arrived first.</li>
     *   <li>If neither caster reports dead (a spurious event; for example the base mod recycled
     *       the reference before this method ran), fall back to {@link #cancel}. No outcome is
     *       emitted and no loser effects are applied on this path.</li>
     *   <li>Select the outcome from the liveness flags:
     *       <ul>
     *         <li>Both dead &rarr; {@link ClashOutcome#TIE} (Requirement 12.3); apply
     *             {@link OutcomeDelivery#applyLoserEffects(LivingEntity, long)} to each caster
     *             that still resolves non-null so {@code DomainDefeated}, {@code Failed}, the
     *             {@code DOMAIN_EXPANSION} removal, and the result-tick stamp all still fire on
     *             the dead caster's NBT (Requirement 12.4). A caster whose {@link WeakReference}
     *             is already {@code null} is skipped &mdash; nothing can be written to an
     *             already-GC'd entity.</li>
     *         <li>Caster A dead, B alive &rarr; {@link ClashOutcome#WINNER_B} (Requirements 12.1
     *             and 12.2). Apply loser effects to A; stamp the surviving winner's result tick
     *             for cross-subsystem observability (Requirement 7.7 mirrors the timer-expiry
     *             path). The winner's {@code Failed}/{@code Cover}/{@code DomainDefeated}/
     *             {@code select}/{@code skill_domain}/{@code x_pos_doma}/{@code y_pos_doma}/
     *             {@code z_pos_doma} keys are never touched (Requirements 7.4 and 12.4).</li>
     *         <li>Caster B dead, A alive &rarr; {@link ClashOutcome#WINNER_A}, mirrored.</li>
     *       </ul>
     *   </li>
     *   <li>Grant mastery XP through {@link MasteryXpGrant#grant(ClashSession, ClashOutcome)}.
     *       The grant class handles per-(session, participant) idempotency and the non-player
     *       short-circuit internally, matching the timer-expiry path.</li>
     *   <li>Flip {@link ClashSession#markResolved(ClashOutcome)}, emit
     *       {@link SyncSink#sendFinal(ClashSession)}, and remove the pair from the registry so
     *       the detector and tick loop stop seeing this session (Requirements 5.6 and 5.7
     *       delegate).</li>
     *   <li>For a non-tie outcome only, cascade-cancel every <em>other</em> session the loser is
     *       still in on the same tick, matching the timer-expiry cascade path (Requirement 10.3).
     *       The {@code TIE} branch does not cascade.</li>
     * </ol>
     *
     * <p>Requirements: 7.4, 12.1, 12.2, 12.3, 12.4.
     *
     * @param session    the session whose participant(s) died
     * @param level      the server level the session belongs to
     * @param deadCaster the caster whose death triggered the call; accepted for logging/context
     *                   but not used for the outcome decision, which re-derives liveness from
     *                   both casters so same-tick dual deaths are handled correctly
     */
    public void resolveDeath(ClashSession session, ServerLevel level, LivingEntity deadCaster) {
        // Req 14.2: idempotency - a session resolved earlier this tick is a no-op.
        if (session.resolved()) {
            return;
        }

        LivingEntity casterA = session.casterA.get();
        LivingEntity casterB = session.casterB.get();

        // A cleared WeakReference is treated as "dead" for outcome purposes: the entity is
        // already gone, so the opposing caster (if alive) should win. Both refs clear + no live
        // caster still maps to TIE below.
        boolean aDead = casterA == null || casterA.isRemoved() || casterA.isDeadOrDying();
        boolean bDead = casterB == null || casterB.isRemoved() || casterB.isDeadOrDying();

        // Spurious death event: neither caster actually reports dead. Fall back to cancel so
        // participant NBT is not mutated (Requirement 11.3) and the session does not silently
        // apply loser effects to a live caster.
        if (!aDead && !bDead) {
            cancel(session, level);
            return;
        }

        long serverTick = level.getServer() != null ? level.getServer().getTickCount() : 0L;
        ClashOutcome outcome;

        if (aDead && bDead) {
            // Req 12.3: both dead same tick -> TIE. Apply loser effects to every caster whose
            // WeakReference still resolves; a null ref can neither receive NBT writes nor lose
            // an already-removed mob effect.
            outcome = ClashOutcome.TIE;
            if (casterA != null) {
                delivery.applyLoserEffects(casterA, serverTick);
            }
            if (casterB != null) {
                delivery.applyLoserEffects(casterB, serverTick);
            }
        } else if (aDead) {
            // Req 12.1 + 12.2: caster A is dead, B is the sole survivor -> WINNER_B. Loser
            // effects fire only on the dead caster (Req 12.4). Mirror the timer-expiry
            // markResultTick call on the winner so downstream subsystems observe a consistent
            // resolution tick on both participants; this write is an addon-owned key
            // (jjkbrp_clash_result_tick) and is not among the winner-protected keys enumerated
            // by Requirement 7.4.
            outcome = ClashOutcome.WINNER_B;
            if (casterA != null) {
                delivery.applyLoserEffects(casterA, serverTick);
            }
            if (casterB != null) {
                delivery.markResultTick(casterB, serverTick);
                delivery.preserveOpenWinnerSureHit(casterB, serverTick);
            }
        } else {
            // Mirror of the WINNER_B branch with A surviving.
            outcome = ClashOutcome.WINNER_A;
            if (casterB != null) {
                delivery.applyLoserEffects(casterB, serverTick);
            }
            if (casterA != null) {
                delivery.markResultTick(casterA, serverTick);
                delivery.preserveOpenWinnerSureHit(casterA, serverTick);
            }
        }

        // Req 6.6 + 6.7 delegate: grant mastery XP through the idempotent grant sink. The grant
        // class short-circuits non-player casters and skips dead participants whose
        // WeakReference has cleared without touching the capability system.
        xpGrant.grant(session, outcome);

        // Req 5.6 + 5.7 delegate: mark resolved, emit the final sync packet, drop from the
        // registry so the tick loop and detector stop seeing this session.
        session.markResolved(outcome);
        syncSink.sendFinal(session);
        registry.remove(session.pair);

        // Req 10.3: cascade-cancel every other session the loser is still in on the same tick.
        // TIE does not cascade (Requirement 10.4) - both participants are losers and the base
        // mod's expire procedure will handle any subsequent mob clean-up on the next tick.
        if (outcome == ClashOutcome.WINNER_A || outcome == ClashOutcome.WINNER_B) {
            UUID loserUuid = outcome == ClashOutcome.WINNER_A ? session.pair.b() : session.pair.a();
            List<ClashSession> others = new ArrayList<>(registry.sessionsContaining(loserUuid));
            for (ClashSession other : others) {
                cancel(other, level);
            }
        }
    }

    /**
     * Cancels {@code session} with {@link ClashOutcome#CANCELLED} and removes it from the
     * registry. This is the single cancellation path reached from every non-resolution source:
     * dead {@link WeakReference}s and null snapshots from {@link #tickSessions}, the detector's
     * overlap-lost / simple-domain-only-loss / effect-removed paths, the subsystem's
     * dimension-change and server-shutdown hooks, and the cascade-cancel loops inside
     * {@link #resolveTimerExpiry} and {@link #resolveDeath}.
     *
     * <p>Per Requirement 11.3 this method intentionally does <em>not</em> touch
     * {@code DomainDefeated}, {@code Failed}, or {@code jjkbrp_clash_result_tick} on either
     * participant; those writes are reserved for {@link #resolveTimerExpiry} and
     * {@link #resolveDeath}. The {@link SyncSink#sendCancelled} call is forwarded so
     * participating clients clear their HUD snapshot (Requirement 11.4).
     *
     * <p>The method is idempotent: a call on an already-resolved or already-cancelled session
     * is a no-op (Requirement 14.2), which keeps the cascade-cancel loops inside the two
     * resolution methods safe even when another session independently cancels the same pair on
     * the same tick.
     *
     * <p>Requirements: 11.3, 11.4, 14.2.
     *
     * @param session the session to cancel; a call on an already-resolved session is a no-op
     *                (Requirement 14.2)
     * @param level   the server level the session belongs to; currently unused by this method
     *                but kept in the signature so hooks registered with
     *                {@code BiConsumer<ClashSession, ServerLevel>} can target it uniformly with
     *                {@link #resolveTimerExpiry} and {@link #resolveDeath}
     */
    public void cancel(ClashSession session, ServerLevel level) {
        if (session.resolved()) {
            return;
        }
        session.markCancelled();
        syncSink.sendCancelled(session);
        registry.remove(session.pair);
    }

    /**
     * Pure tie-threshold decision helper extracted from {@link #resolveTimerExpiry}.
     *
     * <p>Selects the {@link ClashOutcome} from a pair of {@code Clash_Power} values using the
     * same rules the live resolver applies on timer expiry:
     * <ul>
     *   <li>{@link ClashOutcome#TIE} when
     *       {@code Math.abs(powerA - powerB) < POWER_DIFF_TIE_THRESHOLD} (Requirement 5.3;
     *       strictly-less-than, so a diff that <em>equals</em> the threshold is a decisive
     *       winner, not a tie).</li>
     *   <li>{@link ClashOutcome#WINNER_A} when
     *       {@code powerA - powerB >= POWER_DIFF_TIE_THRESHOLD} (Requirement 5.2).</li>
     *   <li>{@link ClashOutcome#WINNER_B} otherwise (the symmetric
     *       {@code powerB - powerA >= POWER_DIFF_TIE_THRESHOLD} branch).</li>
     * </ul>
     *
     * <p>The helper is pure (no registry mutation, no NBT writes, no packets) so it can be
     * exercised from unit / property tests without a live {@link ServerLevel} or
     * {@link LivingEntity}. It is package-private on purpose: only the resolver and its tests
     * should depend on this decision.
     *
     * <p>Requirements: 5.2, 5.3.
     *
     * @param powerA {@code Clash_Power} for the session's caster A; expected to be finite and
     *               non-negative in production (see {@link PowerCalculator#compute})
     * @param powerB {@code Clash_Power} for the session's caster B; same contract as
     *               {@code powerA}
     * @return the outcome dictated by the tie threshold
     */
    static ClashOutcome selectOutcomeByPowerDiff(double powerA, double powerB) {
        double diff = powerA - powerB;
        if (Math.abs(diff) < POWER_DIFF_TIE_THRESHOLD) {
            return ClashOutcome.TIE;
        }
        return diff >= 0.0 ? ClashOutcome.WINNER_A : ClashOutcome.WINNER_B;
    }

    /**
     * Reads the current {@code DomainExpansionRadius} map variable for {@code level}, falling
     * back to {@link net.mcreator.jujutsucraft.addon.util.DomainClashConstants#REFERENCE_RADIUS_FOR_POWER}
     * when the map variable cannot be resolved (e.g. in early-boot integration tests).
     *
     * @param level the server level to read from
     * @return the effective radius in blocks
     */
    private static double readMapRadius(ServerLevel level) {
        try {
            return JujutsucraftModVariables.MapVariables.get(level).DomainExpansionRadius;
        } catch (Exception ignored) {
            return 22.0;
        }
    }

    /** Accessor for the {@link ClashRegistry} backing this resolver. Used by task 9.2. */
    ClashRegistry registry() {
        return registry;
    }

    /** Accessor for the {@link OutcomeDelivery} collaborator. Used by task 9.2. */
    OutcomeDelivery delivery() {
        return delivery;
    }

    /** Accessor for the {@link MasteryXpGrant} collaborator. Used by task 9.2. */
    MasteryXpGrant xpGrant() {
        return xpGrant;
    }

    /** Accessor for the {@link SyncSink} used by this resolver. Used by task 9.2. */
    SyncSink syncSink() {
        return syncSink;
    }
}

