package net.mcreator.jujutsucraft.addon.clash.resolve;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.util.DomainClashConstants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Grants domain-mastery XP to the player participants of a resolved {@code ClashSession} with
 * per-(session, participant) idempotency.
 *
 * <p>This is the single server-side choke point that feeds mastery XP into the existing
 * {@code jjkblueredpurple:domain_mastery} capability for clash outcomes. The amounts are fixed
 * by {@link DomainClashConstants#XP_WINNER}, {@link DomainClashConstants#XP_LOSER}, and
 * {@link DomainClashConstants#XP_TIE} (Requirement 6.7). The grant is performed through
 * {@link DomainMasteryData#addDomainXP(double)} only — no other mutation of the capability is
 * ever performed by this class (Requirement 6.1, 6.6).
 *
 * <p>Idempotency is enforced through an in-memory {@code Set<GrantKey>} where each key combines
 * {@link ClashSession#sessionId} with the participant's UUID. Once a participant has been
 * credited for a given session, subsequent calls to {@link #grant(ClashSession, ClashOutcome)}
 * with that session are no-ops for that participant (Requirement 14.5). This matches the
 * "exactly once per resolved {@code Clash_Session} per participant" contract in Requirement 6.6.
 *
 * <p>Non-player casters short-circuit before any capability lookup occurs (Requirement 6.8) and
 * are never recorded in the idempotency set; they simply never receive XP.
 *
 * <p>{@code CANCELLED} outcomes grant no XP. A {@code null} outcome or a {@code null} session are
 * also treated as no-ops for safety under the same rationale: no resolved clash, no XP.
 *
 * <p>Threading: like the rest of the clash pipeline this class is invoked only from the logical
 * server thread inside {@code TickEvent.ServerTickEvent} phase {@code END}; no synchronization is
 * performed and a plain {@link HashSet} is used for the idempotency store.
 *
 * <p>Requirements: 6.1, 6.6, 6.7, 6.8, 14.5.
 */
public final class MasteryXpGrant {

    /**
     * Compound idempotency key covering exactly one XP grant to a single participant of a single
     * session. Two grants for distinct sessions never collide even if they share a participant,
     * and two grants for the same participant in the same session are de-duplicated to the first.
     *
     * @param sessionId       the resolved session's identifier from {@link ClashSession#sessionId}
     * @param participantUuid the UUID of the player participant receiving XP
     */
    public record GrantKey(UUID sessionId, UUID participantUuid) {}

    /** In-memory set of {@link GrantKey} values already credited. Server-tick thread only. */
    private final Set<GrantKey> granted = new HashSet<>();

    /**
     * Grants domain-mastery XP to both participants of {@code session} according to
     * {@code outcome} via the {@code jjkblueredpurple:domain_mastery} capability. A participant
     * that is not a {@link Player} short-circuits without any capability lookup (Requirement 6.8).
     * A participant that has already been credited for this session is skipped (Requirement 14.5).
     *
     * <p>XP amounts per outcome (Requirement 6.7):
     * <ul>
     *   <li>{@link ClashOutcome#WINNER_A} — caster A receives {@link DomainClashConstants#XP_WINNER}
     *       and caster B receives {@link DomainClashConstants#XP_LOSER}.</li>
     *   <li>{@link ClashOutcome#WINNER_B} — caster A receives {@link DomainClashConstants#XP_LOSER}
     *       and caster B receives {@link DomainClashConstants#XP_WINNER}.</li>
     *   <li>{@link ClashOutcome#TIE} — both casters receive {@link DomainClashConstants#XP_TIE}.</li>
     *   <li>{@link ClashOutcome#CANCELLED} and {@code null} — no XP is granted.</li>
     * </ul>
     *
     * <p>If either caster's {@link java.lang.ref.WeakReference} has already been cleared, that
     * participant is silently skipped. This method never throws for missing or dead casters.
     *
     * @param session the resolved session whose participants should receive XP; may be
     *                {@code null}, in which case the call is a no-op
     * @param outcome the terminal outcome of {@code session}; may be {@code null} or
     *                {@link ClashOutcome#CANCELLED}, in which case no XP is granted
     */
    public void grant(@Nullable ClashSession session, @Nullable ClashOutcome outcome) {
        if (session == null || outcome == null || outcome == ClashOutcome.CANCELLED) {
            return;
        }

        LivingEntity a = session.casterA.get();
        LivingEntity b = session.casterB.get();

        switch (outcome) {
            case WINNER_A -> {
                grantXp(session, a, DomainClashConstants.XP_WINNER);
                grantXp(session, b, DomainClashConstants.XP_LOSER);
            }
            case WINNER_B -> {
                grantXp(session, a, DomainClashConstants.XP_LOSER);
                grantXp(session, b, DomainClashConstants.XP_WINNER);
            }
            case TIE -> {
                grantXp(session, a, DomainClashConstants.XP_TIE);
                grantXp(session, b, DomainClashConstants.XP_TIE);
            }
            case CANCELLED -> {
                // unreachable: filtered above
            }
        }
    }

    /**
     * Credits {@code xp} to {@code participant} when {@code participant} is a {@link Player} and
     * has not yet been credited for {@code session}. Non-player participants short-circuit
     * before any capability lookup occurs (Requirement 6.8). Already-credited participants
     * short-circuit before any capability lookup occurs (Requirement 14.5). The capability is
     * resolved through {@link DomainMasteryCapabilityProvider#DOMAIN_MASTERY_CAPABILITY} and
     * mutated only through {@link DomainMasteryData#addDomainXP(double)} (Requirement 6.1).
     *
     * <p>The pure idempotency-and-mutation logic is delegated to
     * {@link #tryGrantXp(UUID, UUID, DomainMasteryData, double)} which is package-private so
     * property tests can exercise the contract without constructing a real {@link Player}.
     *
     * @param session     the resolved session; its {@link ClashSession#sessionId} forms the first
     *                    half of the idempotency key
     * @param participant the participant to credit; a {@code null} or non-player value is a no-op
     * @param xp          the exact XP amount from the {@link DomainClashConstants} XP constants
     */
    private void grantXp(ClashSession session, @Nullable LivingEntity participant, double xp) {
        if (!(participant instanceof Player player)) {
            // Req 6.8: non-player casters short-circuit without any capability lookup.
            return;
        }
        GrantKey key = new GrantKey(session.sessionId, player.getUUID());
        if (granted.contains(key)) {
            // Req 14.5: already credited for this (session, participant) pair.
            return;
        }
        DomainMasteryData data = player
            .getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null)
            .resolve()
            .orElse(null);
        tryGrantXp(session.sessionId, player.getUUID(), data, xp);
    }

    /**
     * Package-private seam that exercises the core idempotency-and-mutation contract of
     * {@link #grantXp(ClashSession, LivingEntity, double)} using only primitive inputs, without
     * requiring a {@link Player} instance or a Forge capability lookup.
     *
     * <p>Production code reaches this method only through
     * {@link #grantXp(ClashSession, LivingEntity, double)}; tests call it directly to validate
     * the per-{@code (sessionId, participantUuid)} idempotency (Requirement 14.5), the exact
     * XP amounts (Requirement 6.7), and the no-mutation / no-key-recorded behavior for
     * participants with no mastery capability attached &mdash; which in production corresponds
     * to either the non-player short-circuit (Requirement 6.8) or a player whose capability is
     * absent.
     *
     * <p>Behavior (matches the production path exactly, minus the {@code instanceof Player}
     * gate which happens earlier):
     * <ul>
     *   <li>If a {@link GrantKey} for {@code (sessionId, participantUuid)} is already in the
     *       idempotency set, return {@code false} without mutation (Requirement 14.5).</li>
     *   <li>If {@code data == null}, return {@code false} without recording a key so a later
     *       call with the same UUIDs can still credit XP once the capability becomes
     *       available.</li>
     *   <li>Otherwise, call {@link DomainMasteryData#addDomainXP(double)} with {@code xp},
     *       record the {@link GrantKey}, and return {@code true}.</li>
     * </ul>
     *
     * @param sessionId       session UUID forming the first half of the idempotency key
     * @param participantUuid participant UUID forming the second half of the idempotency key
     * @param data            mastery capability instance to credit; {@code null} models either
     *                        the non-player short-circuit or a missing capability, and results
     *                        in a no-op with no key recorded
     * @param xp              exact XP amount to add to {@code data}
     * @return {@code true} iff this call actually added XP and recorded the idempotency key
     */
    boolean tryGrantXp(UUID sessionId, UUID participantUuid, @Nullable DomainMasteryData data, double xp) {
        GrantKey key = new GrantKey(sessionId, participantUuid);
        if (granted.contains(key)) {
            // Req 14.5: already credited for this (session, participant) pair.
            return false;
        }
        if (data == null) {
            // No capability attached (or non-player in the production path): grant nothing and
            // leave the key unset so a later call can still credit them.
            return false;
        }
        data.addDomainXP(xp);
        granted.add(key);
        return true;
    }

    /**
     * Clears every recorded idempotency key. Intended for test fixtures that re-use a single
     * {@code MasteryXpGrant} instance across independent property-test scenarios; production
     * code should never need to call this because the set is bounded by the number of resolved
     * sessions and each key is at most 32 bytes.
     */
    public void clear() {
        granted.clear();
    }
}
