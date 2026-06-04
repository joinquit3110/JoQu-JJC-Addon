package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free state machine for the Sukuna Incomplete Domain Shrine
 * Fuga (Divine Flame Arrow) dust reward.
 *
 * <p>This record models the reward lifecycle that the addon drives through
 * persistent NBT and the base-mod overlay capability, so the high-risk decisions
 * (one-time consumption, hard-clear, overlay-iff-reward, persistence past domain
 * end, and reconcile idempotency) can be unit- and property-tested without a
 * running client or server. The fields mirror the live reads at the call sites:</p>
 *
 * <ul>
 *   <li>{@code rewardFlag} — {@code jjkbrp_sukuna_fuga_dust_locked_full}: a confirmed
 *       Fuga dust reward is present.</li>
 *   <li>{@code dust} — the base-mod {@code dust_amount} ({@code [0, 200]}) that backs
 *       the overlay bar and the projectile {@code cnt6} boost.</li>
 *   <li>{@code overlayShown} — whether {@code OVERLAY1 == "DUST"} (the dust overlay is
 *       displayed via {@code syncPlayerVariables}).</li>
 *   <li>{@code fugaUsedInDomain} — {@code jjkbrp_sukuna_incomplete_fuga_used}: Fuga was
 *       fired during the active domain (suppresses the post-domain cooldown reset).</li>
 *   <li>{@code domainActive} — the Incomplete Domain Shrine is currently active.</li>
 * </ul>
 *
 * <p><b>Overlay invariant.</b> The overlay is shown exactly when a confirmed reward is
 * present or the shrine is active: {@code overlayShown == (rewardFlag || domainActive)}.
 * Individual transitions may transiently set {@code overlayShown} directly (for example,
 * {@link #onFireFuga(int, double)} clears it on consumption); the invariant is
 * (re)established each server tick by {@link #reconcileTick()}, which is the single
 * authority for the overlay-visibility rule and is idempotent so a divergent client
 * always converges to the server value.</p>
 *
 * <p>Instances are immutable; every transition returns a new {@code FugaRewardState}.</p>
 *
 * @param rewardFlag       {@code jjkbrp_sukuna_fuga_dust_locked_full}; a confirmed reward is present
 * @param dust             the {@code dust_amount} backing the overlay bar and {@code cnt6} boost
 * @param overlayShown     whether the dust overlay ({@code OVERLAY1 == "DUST"}) is displayed
 * @param fugaUsedInDomain whether Fuga was fired during the active domain
 * @param domainActive     whether the Incomplete Domain Shrine is currently active
 */
public record FugaRewardState(
        boolean rewardFlag,
        double dust,
        boolean overlayShown,
        boolean fugaUsedInDomain,
        boolean domainActive) {

    /**
     * The clean starting state: no reward, no dust, overlay hidden, Fuga unused, and
     * no active domain. Satisfies the overlay invariant
     * ({@code overlayShown == (rewardFlag || domainActive) == false}).
     *
     * @return a fresh idle state
     */
    public static FugaRewardState idle() {
        return new FugaRewardState(false, 0.0, false, false, false);
    }

    /**
     * Domain-end transition when Fuga was <em>not</em> used during the domain: the
     * earned reward persists. Sets {@code rewardFlag} and {@code overlayShown} to
     * {@code true}, fills {@code dust} to the full reward
     * ({@link DustOverlayFormat#DUST_MAX}, 200), marks the domain inactive, and clears
     * {@code fugaUsedInDomain}.
     *
     * <p>This mirrors the production {@code ModNetworking.fillSukunaFugaDust}, which the
     * domain-end mixin ({@code DomainExpireBarrierFixMixin}) calls after setting
     * {@code jjkbrp_sukuna_fuga_dust_locked_full}: the granted reward is the full dust
     * boost, so the persisted {@code dust_amount} is filled to {@code 200} (a full overlay
     * bar) rather than left at whatever residual value it held at the end tick.</p>
     *
     * <p>This is the "reward persisted" branch (Requirement 5.3): the dust and overlay
     * survive past domain end with no time-based expiry, ending only on Fuga consumption
     * ({@link #onFireFuga(int, double)}) or a hard clear ({@link #onHardClear()}). Because
     * the reset eligibility is {@code hadDomain && !fugaUsed}, reaching this branch (Fuga
     * unused) is exactly when the post-domain Fuga cooldown reset is granted.</p>
     *
     * @return a copy with the reward persisted, dust filled to {@code 200}, and the domain ended
     */
    public FugaRewardState onDomainEndUnused() {
        return new FugaRewardState(true, DustOverlayFormat.DUST_MAX, true, false, false);
    }

    /**
     * Domain-end transition when Fuga <em>was</em> used during the domain: no reward
     * persists. Clears the reward flag, zeroes the dust, hides the overlay, and marks the
     * domain inactive (equivalent to {@link #idle()}).
     *
     * <p>Because Fuga was already fired during the domain, the post-domain cooldown reset
     * is not granted for this session (Requirement 4.5).</p>
     *
     * @return a copy with all reward state cleared
     */
    public FugaRewardState onDomainEndUsed() {
        return idle();
    }

    /**
     * Fire-Fuga transition: consumes a present reward exactly once.
     *
     * <p>If the {@link FugaDustLogic#isFugaIdentity(int, double) Sukuna + Fuga identity}
     * does not hold, or no reward flag is present, this transfers no addon dust and
     * returns this state unchanged ({@code transferredDust == 0.0}). This guarantees the
     * reward can boost at most one Fuga: a second fire (now with {@code rewardFlag} false)
     * transfers nothing.</p>
     *
     * <p>When the identity holds and a reward is present, the real clamped dust
     * ({@link FugaDustLogic#resolveTransferDust(double, double)} against a zero baseline) is
     * transferred and the next state has {@code rewardFlag == false}, {@code dust == 0.0},
     * and {@code overlayShown == false}; {@code fugaUsedInDomain} and {@code domainActive}
     * are left unchanged.</p>
     *
     * @param charId   the active character id
     * @param selectId the selected technique id (rounded before comparison)
     * @return the transferred dust and the resulting state
     */
    public FugaFireResult onFireFuga(int charId, double selectId) {
        if (!FugaDustLogic.isFugaIdentity(charId, selectId) || !rewardFlag) {
            return new FugaFireResult(0.0, this);
        }
        double transferred = FugaDustLogic.resolveTransferDust(dust, 0.0);
        FugaRewardState next = new FugaRewardState(false, 0.0, false, fugaUsedInDomain, domainActive);
        return new FugaFireResult(transferred, next);
    }

    /**
     * Hard-clear transition used by death, logout, zone-exit, and respawn: removes all
     * reward state so nothing survives. Zeroes the dust, hides the overlay, clears the
     * reward flag, and marks the domain inactive (equivalent to {@link #idle()}).
     *
     * <p>The three surehit flags are modeled separately by {@link SurehitState}; this
     * transition clears the reward-side state.</p>
     *
     * @return a fully cleared state
     */
    public FugaRewardState onHardClear() {
        return idle();
    }

    /**
     * Per-tick reconciliation that (re)establishes the overlay-visibility invariant:
     * {@code overlayShown == (rewardFlag || domainActive)}. The {@code dust} value is left
     * unchanged (the overlay mirrors the real server-side dust) and no time-based decay is
     * applied, so a persisted reward stays alive across ticks.
     *
     * <p>This transition is idempotent — {@code reconcileTick(reconcileTick(s))} equals
     * {@code reconcileTick(s)} — because it derives {@code overlayShown} solely from
     * {@code rewardFlag} and {@code domainActive}, which it does not modify. Re-syncing
     * therefore always yields the same server-authoritative overlay value and a divergent
     * client converges on the next sync.</p>
     *
     * @return a copy whose {@code overlayShown} matches {@code rewardFlag || domainActive}
     */
    public FugaRewardState reconcileTick() {
        boolean shouldShow = rewardFlag || domainActive;
        if (shouldShow == overlayShown) {
            return this;
        }
        return new FugaRewardState(rewardFlag, dust, shouldShow, fugaUsedInDomain, domainActive);
    }
}
