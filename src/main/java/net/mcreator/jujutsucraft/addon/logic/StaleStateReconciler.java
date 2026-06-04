package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free model of the Issue 4 login-time stale-state
 * reconciliation for the Sukuna Incomplete Domain Shrine addon state.
 *
 * <p>The bug this models is that leftover Fuga dust, a persisted dust overlay,
 * surehit flags, or an addon-imposed Fuga cooldown from an earlier session can
 * leave a returning player stuck with a phantom reward or a cooldown indicator
 * that never clears. On a fresh session (world load / player login) with no
 * genuinely-active Incomplete Domain Shrine for Sukuna ({@code charId == 1}),
 * such stale addon state must reconcile to a clean baseline. A genuinely-active
 * shrine (a live domain effect with the surehit session in progress) must be
 * preserved and never treated as stale.</p>
 *
 * <p>The "what to clear vs. preserve" decision is extracted here, away from
 * Minecraft, so it can be unit- and property-tested without a running server.
 * The Forge login handler builds an {@link AddonSessionState} from the live
 * NBT/capability reads, calls {@link #reconcile(AddonSessionState)}, and — only
 * when the result differs from the input — writes the cleared values back via
 * the {@code ModNetworking} helpers and syncs.</p>
 *
 * <p>This class is the single authority for the {@code Clean_Baseline}
 * definition (Requirements 7.1, 7.2, 7.3, 7.4): reward flag off, dust zeroed,
 * overlay hidden, all three surehit flags off, and no addon-imposed Fuga
 * cooldown.</p>
 */
public final class StaleStateReconciler {

    /** The character id that identifies Sukuna; reconciliation is restricted to it. */
    public static final int SUKUNA_CHAR_ID = 1;

    private StaleStateReconciler() {
        // utility class; no instances
    }

    /**
     * Immutable snapshot of the addon-owned state read at world load / player
     * login. The fields mirror the live reads at the call site:
     *
     * <ul>
     *   <li>{@code charId} — the active character id (Sukuna is {@code 1}).</li>
     *   <li>{@code shrineActive} —
     *       {@code SurehitState.isActiveSukunaIncompleteShrine()}: a genuinely-active
     *       Incomplete Domain Shrine (live domain effect + surehit session).</li>
     *   <li>{@code rewardFlag} — {@code jjkbrp_sukuna_fuga_dust_locked_full}.</li>
     *   <li>{@code dust} — the base-mod {@code dust_amount}.</li>
     *   <li>{@code overlayShown} — whether {@code OVERLAY1 == "DUST"}.</li>
     *   <li>{@code surehitSession} — {@code jjkbrp_sukuna_incomplete_surehit_session}.</li>
     *   <li>{@code surehitActive} — {@code jjkbrp_sukuna_incomplete_surehit_active}.</li>
     *   <li>{@code surehitHadDomain} — {@code jjkbrp_sukuna_incomplete_surehit_had_domain}.</li>
     *   <li>{@code addonFugaCooldown} — whether any addon-imposed Fuga cooldown
     *       effect or cooldown-max NBT value is present.</li>
     * </ul>
     *
     * @param charId            the active character id
     * @param shrineActive      whether an Incomplete Domain Shrine is genuinely active
     * @param rewardFlag        the {@code jjkbrp_sukuna_fuga_dust_locked_full} flag
     * @param dust              the {@code dust_amount}
     * @param overlayShown      whether the dust overlay ({@code OVERLAY1 == "DUST"}) is shown
     * @param surehitSession    the {@code jjkbrp_sukuna_incomplete_surehit_session} flag
     * @param surehitActive     the {@code jjkbrp_sukuna_incomplete_surehit_active} flag
     * @param surehitHadDomain  the {@code jjkbrp_sukuna_incomplete_surehit_had_domain} flag
     * @param addonFugaCooldown whether an addon-imposed Fuga cooldown effect or cd-max value is present
     */
    public record AddonSessionState(
            int charId,
            boolean shrineActive,
            boolean rewardFlag,
            double dust,
            boolean overlayShown,
            boolean surehitSession,
            boolean surehitActive,
            boolean surehitHadDomain,
            boolean addonFugaCooldown) {
    }

    /**
     * Whether the given snapshot must be reconciled to the {@code Clean_Baseline}.
     *
     * <p>Reconciliation is required exactly when the player is Sukuna
     * ({@code charId == 1}), no Incomplete Domain Shrine is genuinely active, and
     * at least one piece of stale addon state is present (a reward flag, non-zero
     * dust, a shown overlay, any of the three surehit flags, or an addon-imposed
     * Fuga cooldown). Other characters and genuinely-active shrines never need
     * reconciliation (Requirements 7.6, 7.7).</p>
     *
     * @param s the session-state snapshot
     * @return {@code true} iff {@code s} is a Sukuna session with no active shrine
     *         and some stale state present
     */
    public static boolean needsReconcile(AddonSessionState s) {
        if (s.charId() != SUKUNA_CHAR_ID || s.shrineActive()) {
            return false;
        }
        return s.rewardFlag()
                || s.dust() != 0.0
                || s.overlayShown()
                || s.surehitSession()
                || s.surehitActive()
                || s.surehitHadDomain()
                || s.addonFugaCooldown();
    }

    /**
     * Reconciles the snapshot to the {@code Clean_Baseline} when no shrine is
     * active; otherwise returns the input unchanged.
     *
     * <ul>
     *   <li>{@code charId != 1}: returned unchanged — other characters are never
     *       altered (Requirement 7.6).</li>
     *   <li>{@code shrineActive}: returned unchanged — a genuine session is
     *       preserved, never treated as stale (Requirement 7.7).</li>
     *   <li>otherwise: returns the {@code Clean_Baseline} — {@code rewardFlag=false},
     *       {@code dust=0}, {@code overlayShown=false}, all three surehit flags
     *       {@code false}, and {@code addonFugaCooldown=false}, preserving only the
     *       {@code charId} and {@code shrineActive} fields (Requirements 7.1–7.4).</li>
     * </ul>
     *
     * @param s the session-state snapshot
     * @return the reconciled {@code Clean_Baseline}, or {@code s} unchanged when it
     *         is not a Sukuna session with no active shrine
     */
    public static AddonSessionState reconcile(AddonSessionState s) {
        if (s.charId() != SUKUNA_CHAR_ID || s.shrineActive()) {
            return s;
        }
        return new AddonSessionState(
                s.charId(),
                s.shrineActive(),
                false,
                0.0,
                false,
                false,
                false,
                false,
                false);
    }
}
