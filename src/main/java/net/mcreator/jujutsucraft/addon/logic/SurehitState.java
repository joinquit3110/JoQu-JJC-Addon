package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free value model of the Incomplete Domain Shrine "surehit"
 * state for Sukuna ({@code charId == 1}).
 *
 * <p>The base-mod bug this models is that surehit vanishes a few ticks after the
 * Incomplete Domain Shrine activates because incidental cleanup/runtime-reset
 * paths strip the surehit flags while the domain effect is still live. This record
 * captures the exact inputs that {@code DomainAddonUtils.isActiveSukunaIncompleteShrine}
 * reads from persistent NBT and the three lifecycle transitions, so the state
 * machine can be unit- and property-tested without a running client or server.</p>
 *
 * <p>The fields mirror the live NBT/runtime reads at the call sites:</p>
 * <ul>
 *   <li>{@code incompleteForm} — {@code DomainAddonUtils.isIncompleteDomainState}</li>
 *   <li>{@code runtimeDomainId} — {@code DomainAddonUtils.resolveRuntimeDomainId}</li>
 *   <li>{@code sessionFlag} — {@code jjkbrp_sukuna_incomplete_surehit_session}</li>
 *   <li>{@code activeFlag} — {@code jjkbrp_sukuna_incomplete_surehit_active}</li>
 *   <li>{@code hadDomainFlag} — {@code jjkbrp_sukuna_incomplete_surehit_had_domain}</li>
 *   <li>{@code liveDomainEffect} — {@code DomainAddonUtils.hasLiveDomainEffect}</li>
 * </ul>
 *
 * <p>Instances are immutable; every transition returns a new {@code SurehitState}.</p>
 *
 * @param incompleteForm   whether the caster is in the incomplete domain form
 * @param runtimeDomainId  the resolved runtime domain id (the shrine requires {@code 1})
 * @param sessionFlag      the {@code jjkbrp_sukuna_incomplete_surehit_session} flag
 * @param activeFlag       the {@code jjkbrp_sukuna_incomplete_surehit_active} flag
 * @param hadDomainFlag    the {@code jjkbrp_sukuna_incomplete_surehit_had_domain} flag
 * @param liveDomainEffect whether a live domain effect is present on the caster
 */
public record SurehitState(
        boolean incompleteForm,
        int runtimeDomainId,
        boolean sessionFlag,
        boolean activeFlag,
        boolean hadDomainFlag,
        boolean liveDomainEffect) {

    /** The runtime domain id that identifies Sukuna's Malevolent Shrine. */
    public static final int SHRINE_DOMAIN_ID = 1;

    /**
     * Mirrors {@code DomainAddonUtils.isActiveSukunaIncompleteShrine}.
     *
     * <p>Surehit is recognized as an active Incomplete Domain Shrine when a live domain
     * effect is present and either:</p>
     * <ul>
     *   <li>the full form check passes — the caster is in the incomplete domain form, the
     *       runtime domain id is {@code 1}, and at least one surehit flag is set; <b>or</b></li>
     *   <li>the surehit session has been <b>latched</b> — both the {@code session} and
     *       {@code active} flags are set. These are only ever set together by the verified
     *       activation gate (and the per-tick re-assert) and are only cleared together on
     *       genuine domain end, so a latched session plus a live domain effect reliably
     *       means "an activated shrine that is still live", independent of the volatile
     *       incomplete-form / runtime-domain-id NBT that base-mod and other mixin paths
     *       rewrite during combat ticks.</li>
     * </ul>
     *
     * <p>The latch disjunct is the core Issue 2 fix: it stops a one-tick flicker of
     * {@code incompleteForm} or {@code runtimeDomainId} from deactivating surehit and
     * causing the RangeAttack mixin to cancel the shrine's sure-hit attack.</p>
     *
     * @return {@code true} iff a live domain effect is present and either the form check
     *         passes or the surehit session is latched
     */
    public boolean isActiveSukunaIncompleteShrine() {
        if (!liveDomainEffect) {
            return false;
        }
        boolean formCheck = incompleteForm
                && runtimeDomainId == SHRINE_DOMAIN_ID
                && (sessionFlag || activeFlag);
        // The session flag is the durable latch: it is only ever set by the verified
        // activation / cast gate and is only cleared on a genuine domain end, death, or
        // login reconcile — never by combat, incidental cleanup, or the base mod's brief
        // mid-tick effect removals. The `active` flag, by contrast, IS stripped by
        // clearIncompleteDomainData whenever the live-effect read momentarily flickers, so
        // requiring `active` here would let a one-tick flicker permanently deactivate the
        // shrine (which then never recovers, because the base re-stamp path is skipped once
        // the predicate is false). Latching on `session` alone + a live domain effect is the
        // robust Issue 2 fix.
        boolean sessionLatched = sessionFlag;
        return formCheck || sessionLatched;
    }

    /**
     * Activation-tick transition: sets all three surehit flags ({@code active},
     * {@code session}, and {@code had_domain}) so the shrine is recognized as a
     * surehit shrine from the very first tick rather than vanishing shortly after
     * the domain starts.
     *
     * @return a copy with {@code activeFlag}, {@code sessionFlag}, and
     *         {@code hadDomainFlag} all {@code true}; all other fields unchanged
     */
    public SurehitState onActivate() {
        return new SurehitState(
                incompleteForm,
                runtimeDomainId,
                true,
                true,
                true,
                liveDomainEffect);
    }

    /**
     * Incidental cleanup / runtime-state-reset transition.
     *
     * <p>This path must never strip surehit while the shrine's domain effect is still
     * present: that is the root cause of the early-vanish bug. Preserving the flags is
     * required while {@link #isActiveSukunaIncompleteShrine()} or {@link #liveDomainEffect()}
     * holds, and clearing the flags is reserved exclusively for {@link #onDomainEnd()}
     * (the genuine domain-end path). Cleanup therefore preserves the surehit flags in
     * all cases and returns this state unchanged.</p>
     *
     * @return this state unchanged (flags preserved)
     */
    public SurehitState onCleanup() {
        // Preserve surehit through incidental cleanup. Only onDomainEnd() clears flags,
        // so this is a no-op; it explicitly never strips surehit while the domain is live.
        return this;
    }

    /**
     * Combat-event transition: applied for every hit, attack, or other combat event
     * that occurs while the Incomplete Domain Shrine is active.
     *
     * <p>This is the core of the Issue 2 fix. No combat/hit/attack path may cancel,
     * gate, or deactivate surehit: the number of hits or attacks must never end
     * surehit before the live domain effect ends. Clearing the surehit flags is
     * reserved exclusively for {@link #onDomainEnd()}. Combat is therefore an
     * identity transition that preserves all flags, so applying any sequence of
     * combat events to an active shrine leaves {@link #isActiveSukunaIncompleteShrine()}
     * unchanged.</p>
     *
     * @return this state unchanged (flags preserved across hits/attacks)
     */
    public SurehitState onCombatEvent() {
        // No combat/hit/attack event may strip surehit while the domain is live.
        // Only onDomainEnd() clears flags, so this is an explicit no-op.
        return this;
    }

    /**
     * Domain-effect-end transition: clears the {@code session}, {@code active}, and
     * {@code had_domain} flags so surehit deactivates cleanly and cannot carry over
     * into a later, unrelated domain. After this transition
     * {@link #isActiveSukunaIncompleteShrine()} returns {@code false}.
     *
     * @return a copy with {@code sessionFlag}, {@code activeFlag}, and
     *         {@code hadDomainFlag} all {@code false}; all other fields unchanged
     */
    public SurehitState onDomainEnd() {
        return new SurehitState(
                incompleteForm,
                runtimeDomainId,
                false,
                false,
                false,
                liveDomainEffect);
    }
}
