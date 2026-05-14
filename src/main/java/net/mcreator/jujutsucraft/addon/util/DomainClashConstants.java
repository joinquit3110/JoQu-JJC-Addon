package net.mcreator.jujutsucraft.addon.util;

/**
 * Centralized timing, rate, and threshold constants for the domain clash system.
 *
 * <p>All clash-related magic numbers are collected here so that tuning and
 * the registry-based Domain Clash subsystem can reference a single source of truth.</p>
 *
 * <p><b>Layout:</b></p>
 * <ul>
 *   <li>The first block of constants holds the <em>new</em> defaults required by the
 *       server-authoritative Domain Clash subsystem described in the
 *       {@code domain-clash-system} design document (Requirements 3, 4, 5, 6, 8, 9, 14).
 *       New clash code reads from <em>these</em> constants only.</li>
 *   <li>The second block preserves the legacy unified-pressure / erosion-era constants
 *       from the previous iteration of the clash system. They are intentionally kept
 *       in place for any remaining mixin callers that have not yet been migrated, but
 *       <b>new clash code must not reference them</b>.</li>
 * </ul>
 */
public final class DomainClashConstants {

    private DomainClashConstants() {
        // Utility class — no instantiation.
    }

    // ================================================================================
    // ==================== Domain Clash Subsystem Defaults (new) =====================
    // ================================================================================
    // Source of truth for the server-authoritative clash pipeline
    // (ClashDetector / ClashResolver / ClashSyncNetwork / ClientClashCache / HUD).
    // See design.md → "Constants" section and the cited requirements.

    /**
     * Default clash duration in server ticks before timer-expiry resolution fires.
     * <p>Requirement 3.1.</p>
     */
    public static final int CLASH_DURATION_TICKS_DEFAULT = 200;

    /**
     * Interval (in server ticks) between {@code Clash_Power} re-sampling passes
     * and the cadence at which sampled HUD sync packets are dispatched.
     * <p>Requirements 4.1, 9.2.</p>
     */
    public static final int SAMPLING_INTERVAL_TICKS_DEFAULT = 10;

    /**
     * How long (in client ticks) a cached clash HUD snapshot remains renderable
     * after it was last received before it is considered stale and discarded.
     * <p>Requirements 8.1, 8.6.</p>
     */
    public static final int HUD_SNAPSHOT_TTL_TICKS_DEFAULT = 40;

    /**
     * Additional slack (in server ticks) beyond {@link #CLASH_DURATION_TICKS_DEFAULT}
     * after which a session that has somehow not resolved is force-cancelled by the
     * stale-session safety backstop.
     * <p>Requirement 14.3.</p>
     */
    public static final int STALE_SESSION_EXTRA_TICKS = 200;

    /**
     * Absolute {@code Clash_Power} difference below which the outcome is a {@code TIE}.
     * At or above this threshold the higher-powered participant wins.
     * <p>Requirements 5.2, 5.3.</p>
     */
    public static final double POWER_DIFF_TIE_THRESHOLD = 4.0;

    /**
     * Minimum per-participant power delta between successive sampled syncs that
     * qualifies as "significant" and forces an out-of-cadence HUD packet.
     * <p>Requirement 9.2.</p>
     */
    public static final double SAMPLED_SYNC_SIGNIFICANT_DELTA = 2.0;

    /**
     * Reference clash radius (in blocks) used by the {@code Radius_Factor} term of
     * the {@code Clash_Power} formula: {@code sqrt(REFERENCE_RADIUS_FOR_POWER / clashRadius)}.
     * <p>Requirement 4.7.</p>
     */
    public static final double REFERENCE_RADIUS_FOR_POWER = 22.0;

    /**
     * Lower clamp applied to the computed {@code Radius_Factor} before it feeds into
     * {@code Clash_Power}.
     * <p>Requirement 4.7.</p>
     */
    public static final double RADIUS_FACTOR_MIN = 0.5;

    /**
     * Upper clamp applied to the computed {@code Radius_Factor} before it feeds into
     * {@code Clash_Power}.
     * <p>Requirement 4.7.</p>
     */
    public static final double RADIUS_FACTOR_MAX = 2.0;

    /**
     * Domain-mastery XP granted to the winning player participant when a session
     * resolves with a decisive winner.
     * <p>Requirement 6.7.</p>
     */
    public static final double XP_WINNER = 50.0;

    /**
     * Domain-mastery XP granted to the losing player participant when a session
     * resolves with a decisive winner.
     * <p>Requirement 6.7.</p>
     */
    public static final double XP_LOSER = 20.0;

    /**
     * Domain-mastery XP granted to each player participant when a session resolves
     * as a {@code TIE}.
     * <p>Requirement 6.7.</p>
     */
    public static final double XP_TIE = 30.0;

    /**
     * Number of ticks the final-outcome HUD state ({@code VICTORY} / {@code DEFEAT} /
     * {@code DRAW}) remains visible on the client before the overlay hides itself.
     * <p>Requirement 8.5.</p>
     */
    public static final int OUTCOME_DISPLAY_HOLD_TICKS = 40;

    /**
     * Fraction of incoming clash pressure mitigated per level of the domain mastery
     * {@code Barrier Refinement} property inside the {@code applyBarrierRefinement}
     * helper of the {@code Clash_Power} formula.
     * <p>Requirement 6.3.</p>
     */
    public static final double BARRIER_REFINEMENT_PER_LEVEL = 0.04;

    /**
     * Multiplicative {@code Clash_Power} bonus contributed per level of the overall
     * {@code domainMasteryLevel}.
     * <p>Requirement 4.8.</p>
     */
    public static final double MASTERY_MULT_PER_LEVEL = 0.04;

    /**
     * Multiplicative {@code Clash_Power} bonus contributed per effective level of the
     * {@code Barrier Power} mastery property.
     * <p>Requirement 4.8.</p>
     */
    public static final double MASTERY_MULT_PER_BARRIER_POWER = 0.06;

    /**
     * Flat additive {@code Clash_Power} bonus contributed per level of the overall
     * {@code domainMasteryLevel}.
     * <p>Requirement 4.9.</p>
     */
    public static final double MASTERY_FLAT_PER_LEVEL = 1.0;

    /**
     * Base multiplicative bonus applied to {@code Clash_Power} for domain id 27
     * (Deadly Sentencing) before any other mastery/form modifiers.
     * <p>Requirement 4.10.</p>
     */
    public static final double DEADLY_SENTENCING_BASE_MULT = 1.5;

    /**
     * Base multiplicative bonus applied to {@code Clash_Power} for domain id 29
     * (Idle Death Gamble) before any other mastery/form modifiers.
     * <p>Requirement 4.11.</p>
     */
    public static final double IDLE_DEATH_GAMBLE_BASE_MULT = 2.0;

    // ================================================================================
    // ============ Legacy unified-pressure constants (retained, not used by  =========
    // ============ new clash code — preserved only for unmigrated callers)   =========
    // ================================================================================
    // These constants come from the previous iteration of the clash subsystem
    // (erosion/wrap + unified-pressure). They are intentionally left in place so
    // any remaining mixin caller continues to compile, but the registry-based
    // Domain Clash subsystem (design.md) MUST NOT reference them.

    /**
     * Master feature flag for the previous registry-based clash system.
     * @deprecated Superseded by the Domain Clash subsystem in {@code addon.clash}.
     *             The new subsystem is always active; this flag is unused by it.
     */
    @Deprecated
    public static final boolean USE_REGISTRY = true;

    // ==================== Timing Windows (ticks) ====================

    /** @deprecated Legacy tie window; the new subsystem uses {@link #POWER_DIFF_TIE_THRESHOLD}. */
    @Deprecated
    public static final long TIE_WINDOW_TICKS = 80L;

    /** @deprecated Legacy minimum clash duration; superseded by {@link #CLASH_DURATION_TICKS_DEFAULT}. */
    @Deprecated
    public static final long MIN_CLASH_DURATION_TICKS = 140L;

    /** @deprecated Legacy resolve cooldown window. */
    @Deprecated
    public static final long RESULT_COOLDOWN_TICKS = 300L;

    /** @deprecated Legacy clash-contact recency window. */
    @Deprecated
    public static final long RECENT_CLASH_CONTACT_TICKS = 40L;

    /** @deprecated Legacy actionbar-sync contact window. */
    @Deprecated
    public static final long ACTIONBAR_CONTACT_WINDOW_TICKS = 24L;

    /** @deprecated Legacy mutual-contact recency window. */
    @Deprecated
    public static final long MUTUAL_CLASH_CONTACT_TICKS = 60L;

    // ==================== Unified Pressure System (legacy) ====================

    /** @deprecated Legacy unified-pressure per-tick rate. */
    @Deprecated
    public static final double PRESSURE_RATE = 0.06;

    /** @deprecated Legacy maximum per-tick pressure clamp. */
    @Deprecated
    public static final double MAX_PRESSURE_PER_TICK = 0.55;

    /** @deprecated Legacy power-difference tie threshold; superseded by {@link #POWER_DIFF_TIE_THRESHOLD}. */
    @Deprecated
    public static final double POWER_TIE_THRESHOLD = 4.0;

    /** @deprecated Legacy accumulated-pressure loss threshold. */
    @Deprecated
    public static final double PRESSURE_LOSS_THRESHOLD = 15.0;

    /** @deprecated Legacy barrier-NBT lock window after resolution. */
    @Deprecated
    public static final long BARRIER_LOCK_DURATION_TICKS = 200L;

    /** @deprecated Legacy grace period before imported OG loss flags are trusted. */
    @Deprecated
    public static final long LOSS_FLAG_GRACE_TICKS = 120L;

    /** @deprecated Legacy HUD hold time after resolution. */
    @Deprecated
    public static final long RESOLVED_HUD_GRACE_TICKS = 10L;

    // ==================== Form Passives (legacy) ====================

    /** @deprecated Legacy closed-form per-tick barrier-refinement reduction rate. */
    @Deprecated
    public static final double CLOSED_PASSIVE_REDUCTION_RATE = 0.02;

    /** @deprecated Legacy open-form per-tick power rate. */
    @Deprecated
    public static final double OPEN_PASSIVE_POWER_RATE = 0.005;

    /** @deprecated Legacy incomplete-form per-tick damage reduction. */
    @Deprecated
    public static final double INCOMPLETE_PASSIVE_REDUCTION = 0.003;

    // ==================== Power Formula Reference Values (legacy) ====================

    /**
     * @deprecated Legacy reference radius for the radius modifier; the new
     *             {@code Clash_Power} formula uses {@link #REFERENCE_RADIUS_FOR_POWER}.
     */
    @Deprecated
    public static final double REFERENCE_RADIUS = 16.0;

    /** @deprecated Legacy max-normal-radius cap used by the old power curve. */
    @Deprecated
    public static final double MAX_NORMAL_RADIUS = 32.0;

    /** @deprecated Legacy minimum-radius boost cutoff used by the old power curve. */
    @Deprecated
    public static final double MIN_RADIUS = 4.0;

    // ==================== Form Hierarchy (legacy multipliers) ====================

    /** @deprecated Legacy incomplete-form baseline multiplier. The new system uses {@code DomainForm.formFactor}. */
    @Deprecated
    public static final double INCOMPLETE_FORM_MULTIPLIER = 0.95;

    /** @deprecated Legacy closed-form baseline multiplier. The new system uses {@code DomainForm.formFactor}. */
    @Deprecated
    public static final double CLOSED_FORM_MULTIPLIER = 1.0;

    /** @deprecated Legacy open-form baseline multiplier. The new system uses {@code DomainForm.formFactor}. */
    @Deprecated
    public static final double OPEN_FORM_MULTIPLIER = 1.15;

    // ==================== XP Rewards (legacy) ====================

    /** @deprecated Legacy winner XP; superseded by {@link #XP_WINNER}. */
    @Deprecated
    public static final int WINNER_XP = 50;

    /** @deprecated Legacy tie XP; superseded by {@link #XP_TIE}. */
    @Deprecated
    public static final int TIE_XP = 30;

    /** @deprecated Legacy loser XP; superseded by {@link #XP_LOSER}. */
    @Deprecated
    public static final int LOSER_XP = 10;

    // ==================== Clash Range Multipliers (legacy) ====================

    /** @deprecated Legacy open-form clash-range multiplier. */
    @Deprecated
    public static final double OPEN_CLASH_RANGE_MULTIPLIER = 18.0;

    /** @deprecated Legacy closed/incomplete clash-range multiplier. */
    @Deprecated
    public static final double CLOSED_CLASH_RANGE_MULTIPLIER = 2.0;

    /** @deprecated Legacy clash-window spatial threshold factor. */
    @Deprecated
    public static final double CLASH_WINDOW_THRESHOLD_FACTOR = 0.65;

    /** @deprecated Legacy minimum clash-window spatial threshold in blocks. */
    @Deprecated
    public static final double CLASH_WINDOW_MINIMUM_THRESHOLD = 4.0;

    // ==================== NPC Defaults (legacy) ====================

    /** @deprecated Legacy NPC default barrier-refinement value. */
    @Deprecated
    public static final double NPC_DEFAULT_BARRIER_REFINEMENT = 0.3;

    /** @deprecated Legacy default opponent power assumption. */
    @Deprecated
    public static final double DEFAULT_OPPONENT_POWER = 80.0;

    /** @deprecated Legacy NPC baseline clash-power compensation. */
    @Deprecated
    public static final double NPC_BASELINE_CLASH_POWER = 4.0;

    /** @deprecated Legacy max-HP cap used for the old health-ratio calc. */
    @Deprecated
    public static final float HEALTH_NORMALIZATION_CAP = 40.0f;

    // ==================== Incomplete Domain (legacy) ====================

    /** @deprecated Legacy default incomplete-domain per-tick penalty. */
    @Deprecated
    public static final double DEFAULT_INCOMPLETE_PENALTY_PER_TICK = 0.01;

    // ==================== Asymmetric Position Pressure (legacy) ====================

    /** @deprecated Legacy domain-boundary buffer used by the old pressure model. */
    @Deprecated
    public static final double DOMAIN_BOUNDARY_BUFFER = 1.5;

    /** @deprecated Legacy inside-defence multiplier for Closed/Incomplete under the old pressure model. */
    @Deprecated
    public static final double INSIDE_DEFENSE_MULTIPLIER_CLOSED = 0.3;

    /** @deprecated Legacy inside-defence multiplier for Open under the old pressure model. */
    @Deprecated
    public static final double INSIDE_DEFENSE_MULTIPLIER_OPEN = 0.6;

    /** @deprecated Legacy outside-attacker multiplier under the old pressure model. */
    @Deprecated
    public static final double OUTSIDE_ATTACK_MULTIPLIER = 1.2;

    /** @deprecated Legacy minimum radius for the asymmetric-position effect. */
    @Deprecated
    public static final double MIN_ASYMMETRIC_RADIUS = 3.0;

    /** @deprecated Legacy reference radius used to scale the asymmetric-position effect. */
    @Deprecated
    public static final double ASYMMETRY_REFERENCE_RADIUS = 16.0;
}
