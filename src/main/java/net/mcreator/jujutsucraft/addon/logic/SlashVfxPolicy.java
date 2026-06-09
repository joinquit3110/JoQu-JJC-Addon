package net.mcreator.jujutsucraft.addon.logic;

/** Pure numeric policy for the Malevolent Shrine slash splash VFX. */
public final class SlashVfxPolicy {

    /** The runtime domain id that identifies Sukuna's Malevolent Shrine. */
    public static final int SHRINE_DOMAIN_ID = 1;

    /** Sukuna's character id. */
    public static final int SUKUNA_CHAR_ID = 1;

    /** The slash cadence (in ticks) shared by the Closed/Open and incomplete sure-hit paths. */
    public static final long SLASH_CADENCE_TICKS = 1L;

    private static final double ORIGINAL_SLASH_RANGE_FACTOR = 18.0D;
    private static final double ORIGINAL_SLASH_SPREAD_FACTOR = 0.25D;
    private static final double ORIGINAL_SLASH_COUNT_FACTOR = 4.0D;

    private SlashVfxPolicy() {
    }

    /**
     * Whether the given server game time is a slash cadence tick.
     *
     * <p>The slash splash is emitted only on these ticks, for both Closed/Open and the incomplete
     * sure-hit form.</p>
     *
     * @param gameTime the server game time tick
     * @return {@code true} iff the game time matches the shared slash cadence
     */
    public static boolean isCadenceTick(long gameTime) {
        return gameTime % SLASH_CADENCE_TICKS == 0L;
    }

    /**
     * The radius-derived emission range, reproducing the normalization in
     * {@code supplementMalevolentShrineVFX}.
     *
     * <pre>
     *   normalizedRadiusMul = max(0.1, radiusMul)
     *   scaledRadius        = max(1.0, baseRadius * normalizedRadiusMul)
     *   range               = scaledRadius * 18.0
     * </pre>
     *
     * @param baseRadius the base domain radius ({@code jjkbrp_base_domain_radius}, default 16.0)
     * @param radiusMul  the radius multiplier ({@code jjkbrp_radius_multiplier})
     * @return the emission range used as the slash spread/count input
     */
    public static double scaledRange(double baseRadius, double radiusMul) {
        double normalizedRadiusMul = normalizedRadiusMul(radiusMul);
        double scaledRadius = Math.max(1.0, baseRadius * normalizedRadiusMul);
        return scaledRadius * ORIGINAL_SLASH_RANGE_FACTOR;
    }

    /**
     * The normalized radius multiplier, reproducing
     * {@code normalizedRadiusMul = Math.max(0.1, radiusMul)} from
     * {@code supplementMalevolentShrineVFX}.
     *
     * <p>This is the value the call site passes as the {@code radiusMul} argument to
     * {@code sendMalevolentShrineSlashVFX}, so {@link #slashParticleCount(double)} must be
     * called with this (already-normalized) value to match Closed/Open exactly.</p>
     *
     * @param radiusMul the raw radius multiplier
     * @return {@code max(0.1, radiusMul)}
     */
    public static double normalizedRadiusMul(double radiusMul) {
        return Math.max(0.1, radiusMul);
    }

    public static double slashSpread(double range) {
        return Math.max(0.35D, range * ORIGINAL_SLASH_SPREAD_FACTOR);
    }

    public static int slashParticleCount(double range) {
        return Math.max(1, (int)Math.round(range * ORIGINAL_SLASH_COUNT_FACTOR));
    }

    /**
     * The {@code Failed}-independent decision of whether the incomplete sure-hit Malevolent Shrine
     * should emit the slash splash on this tick.
     *
     * <p>This is the {@code isBugCondition} "shrine is still up" core (the same
     * {@code Failed}-tolerant latch signal {@code CooldownTrackerEvents.handleSukunaIncompleteSureHitReward}
     * uses to drive the ambient sure-hit VFX and keep the cleave alive), <b>minus the buggy
     * {@code Failed} gate</b>, AND on a cadence tick:</p>
     * <ul>
     *   <li>Sukuna ({@code charId == 1});</li>
     *   <li>the surehit / Cleave Covenant upgrade is purchased;</li>
     *   <li>incomplete domain form;</li>
     *   <li>runtime domain id {@code 1} (Malevolent Shrine);</li>
     *   <li>the surehit session latch is set ({@code sessionFlag && activeFlag});</li>
     *   <li>the {@code DOMAIN_EXPANSION} effect is present;</li>
     *   <li>the domain is not defeated ({@code !domainDefeatedFlag});</li>
     *   <li>and the tick is a cadence tick.</li>
     * </ul>
     *
     * <p>{@link SurehitLatchInputs} deliberately carries no {@code Failed} field: the slash decision
     * must render for the full domain duration regardless of the base mod stamping
     * {@code Failed = true} on the incomplete shell (design Property 4, Failed-independence).</p>
     *
     * @param inputs the latch inputs for this tick
     * @return {@code true} iff the incomplete sure-hit shrine should emit the slash this tick
     */
    public static boolean shouldEmitIncompleteSurehitSlash(SurehitLatchInputs inputs) {
        boolean latchedAndUp = inputs.charId() == SUKUNA_CHAR_ID
                && inputs.surehitPurchased()
                && inputs.incompleteForm()
                && inputs.runtimeDomainId() == SHRINE_DOMAIN_ID
                && (inputs.sessionFlag() && inputs.activeFlag())
                && inputs.hasDomainEffect()
                && !inputs.domainDefeatedFlag();
        return latchedAndUp && isCadenceTick(inputs.gameTime());
    }

    /**
     * Immutable, Minecraft-free inputs to {@link #shouldEmitIncompleteSurehitSlash(SurehitLatchInputs)}.
     *
     * <p>These mirror the live NBT / runtime reads that the reward handler uses to decide the
     * {@code Failed}-tolerant "shrine is still up" latch signal. There is intentionally <b>no</b>
     * {@code failedFlag} field: the incomplete sure-hit slash decision is independent of the base
     * mod's {@code Failed} stamp.</p>
     *
     * @param charId             the caster's character id (Sukuna is {@code 1})
     * @param incompleteForm     whether the caster is in the incomplete domain form
     *                           ({@code DomainAddonUtils.isIncompleteDomainState})
     * @param runtimeDomainId    the resolved runtime domain id (Malevolent Shrine is {@code 1})
     * @param surehitPurchased   whether the Cleave Covenant / sure-hit upgrade is purchased
     * @param sessionFlag        {@code jjkbrp_sukuna_incomplete_surehit_session}
     * @param activeFlag         {@code jjkbrp_sukuna_incomplete_surehit_active}
     * @param hasDomainEffect    whether the {@code DOMAIN_EXPANSION} effect is present on the caster
     * @param domainDefeatedFlag the base-mod {@code DomainDefeated} flag
     * @param gameTime           the server game time tick (used for the cadence gate)
     */
    public record SurehitLatchInputs(
            int charId,
            boolean incompleteForm,
            int runtimeDomainId,
            boolean surehitPurchased,
            boolean sessionFlag,
            boolean activeFlag,
            boolean hasDomainEffect,
            boolean domainDefeatedFlag,
            long gameTime) {
    }
}
