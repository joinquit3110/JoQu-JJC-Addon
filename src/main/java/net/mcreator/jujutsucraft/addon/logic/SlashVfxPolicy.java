package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free policy for the Malevolent Shrine radius-scaled
 * {@code jujutsucraft:particle_slash_large} "slash splash" VFX.
 *
 * <p>This class mirrors, byte-for-byte, the cadence + radius-derived scale/count/spread math that
 * currently lives inside
 * {@code DomainMasteryMixin.jjkbrp$supplementMalevolentShrineVFX} and
 * {@code DomainMasteryMixin.jjkbrp$sendMalevolentShrineSlashVFX}. Extracting it here lets the
 * "should this tick emit the slash" + "how many particles / how wide" decision be unit- and
 * property-tested with jqwik, with <b>no</b> running client or server, exactly like
 * {@link SurehitState} and {@link DustOverlayFormat}.</p>
 *
 * <p>The Minecraft-facing emission (the {@code particle} command broadcast and the
 * {@code CanSeeSukunaSlashProcedure} per-viewer visibility filter) stays in the mixin / util layer;
 * only the deterministic numeric decision lives here, so the Closed/Open call-site path and the new
 * latch-driven incomplete sure-hit path produce an identical visual at equal radius.</p>
 *
 * <p>The scaling reproduced here is, exactly as in {@code supplementMalevolentShrineVFX}:</p>
 * <pre>
 *   normalizedRadiusMul = max(0.25, radiusMul)
 *   scaledRadius        = max(1.0, baseRadius * normalizedRadiusMul)
 *   range               = scaledRadius * 2.0
 * </pre>
 * <p>and the slash count/spread, exactly as in {@code sendMalevolentShrineSlashVFX} (which is
 * invoked with {@code normalizedRadiusMul} as its {@code radiusMul} argument):</p>
 * <pre>
 *   spread        = max(0.35, range * 0.25)
 *   densityScale  = max(0.08, min(1.0, radiusMul * radiusMul))
 *   softenedCount = 4.0 * range * densityScale / sqrt(max(0.25, radiusMul))
 *   minCount      = radiusMul &lt; 0.5 ? 3 : (radiusMul &lt; 0.85 ? 6 : 16)
 *   maxCount      = radiusMul &lt; 1.0 ? 36 : 132
 *   count         = round(max(minCount, min(maxCount, softenedCount)))
 * </pre>
 *
 * <p>This class is final, stateless, and exposes only static pure functions; it holds no Minecraft
 * types and performs no I/O.</p>
 */
public final class SlashVfxPolicy {

    /** The runtime domain id that identifies Sukuna's Malevolent Shrine. */
    public static final int SHRINE_DOMAIN_ID = 1;

    /** Sukuna's character id. */
    public static final int SUKUNA_CHAR_ID = 1;

    /** The slash cadence (in ticks) shared by the Closed/Open and incomplete sure-hit paths. */
    public static final long SLASH_CADENCE_TICKS = 5L;

    private SlashVfxPolicy() {
        // Pure utility class; no instances.
    }

    /**
     * Whether the given server game time is a slash cadence tick.
     *
     * <p>Mirrors the {@code gameTime % 5L == 0L} guard in {@code supplementMalevolentShrineVFX}; the
     * slash splash is emitted only on these ticks, for both Closed/Open and the incomplete sure-hit
     * form.</p>
     *
     * @param gameTime the server game time tick
     * @return {@code true} iff {@code gameTime % 5 == 0}
     */
    public static boolean isCadenceTick(long gameTime) {
        return gameTime % SLASH_CADENCE_TICKS == 0L;
    }

    /**
     * The radius-derived emission range, reproducing the normalization in
     * {@code supplementMalevolentShrineVFX}.
     *
     * <pre>
     *   normalizedRadiusMul = max(0.25, radiusMul)
     *   scaledRadius        = max(1.0, baseRadius * normalizedRadiusMul)
     *   range               = scaledRadius * 2.0
     * </pre>
     *
     * @param baseRadius the base domain radius ({@code jjkbrp_base_domain_radius}, default 16.0)
     * @param radiusMul  the radius multiplier ({@code jjkbrp_radius_multiplier})
     * @return the emission range used as the slash spread/count input
     */
    public static double scaledRange(double baseRadius, double radiusMul) {
        double normalizedRadiusMul = normalizedRadiusMul(radiusMul);
        double scaledRadius = Math.max(1.0, baseRadius * normalizedRadiusMul);
        return scaledRadius * 2.0;
    }

    /**
     * The normalized radius multiplier, reproducing
     * {@code normalizedRadiusMul = Math.max(0.25, radiusMul)} from
     * {@code supplementMalevolentShrineVFX}.
     *
     * <p>This is the value the call site passes as the {@code radiusMul} argument to
     * {@code sendMalevolentShrineSlashVFX}, so {@link #slashParticleCount(double, double)} must be
     * called with this (already-normalized) value to match Closed/Open exactly.</p>
     *
     * @param radiusMul the raw radius multiplier
     * @return {@code max(0.25, radiusMul)}
     */
    public static double normalizedRadiusMul(double radiusMul) {
        return Math.max(0.25, radiusMul);
    }

    /**
     * The slash particle spread, reproducing
     * {@code spread = Math.max(0.35, range * 0.25)} from {@code sendMalevolentShrineSlashVFX}.
     *
     * @param range the emission range (from {@link #scaledRange(double, double)})
     * @return {@code max(0.35, range * 0.25)}
     */
    public static double slashSpread(double range) {
        return Math.max(0.35, range * 0.25);
    }

    /**
     * The slash particle count, reproducing the {@code softenedCount} computation and the
     * {@code min}/{@code max} clamping from {@code sendMalevolentShrineSlashVFX} byte-for-byte.
     *
     * <pre>
     *   densityScale  = max(0.08, min(1.0, radiusMul * radiusMul))
     *   softenedCount = 4.0 * range * densityScale / sqrt(max(0.25, radiusMul))
     *   minCount      = radiusMul &lt; 0.5 ? 3 : (radiusMul &lt; 0.85 ? 6 : 16)
     *   maxCount      = radiusMul &lt; 1.0 ? 36 : 132
     *   count         = round(max(minCount, min(maxCount, softenedCount)))
     * </pre>
     *
     * <p><b>Note:</b> {@code radiusMul} here is the {@link #normalizedRadiusMul(double) normalized}
     * multiplier, because the call site invokes the emitter with {@code normalizedRadiusMul}.</p>
     *
     * @param range     the emission range (from {@link #scaledRange(double, double)})
     * @param radiusMul the (normalized) radius multiplier passed to the emitter
     * @return the clamped, rounded particle count
     */
    public static int slashParticleCount(double range, double radiusMul) {
        double densityScale = Math.max(0.08, Math.min(1.0, radiusMul * radiusMul));
        double softenedCount = 4.0 * range * densityScale / Math.sqrt(Math.max(0.25, radiusMul));
        int minCount = radiusMul < 0.5 ? 3 : (radiusMul < 0.85 ? 6 : 16);
        int maxCount = radiusMul < 1.0 ? 36 : 132;
        return (int) Math.round(Math.max(minCount, Math.min(maxCount, softenedCount)));
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
     *   <li>and the tick is a cadence tick ({@code gameTime % 5 == 0}).</li>
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
