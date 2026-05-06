package net.mcreator.jujutsucraft.addon.util;

/**
 * Centralized timing, rate, and threshold constants for the domain clash system.
 *
 * <p>All clash-related magic numbers that were previously scattered across
 * {@code DomainClashPenaltyMixin}, {@code DomainClashXpMixin}, and
 * {@code DomainOpenClashCancelMixin} are collected here so that tuning and
 * future registry-based phases can reference a single source of truth.</p>
 *
 * <p><b>Phase 1 note:</b> existing mixin constants are not yet fully migrated
 * to read from this class.  Phase 2+ will wire callers through these constants
 * progressively.</p>
 */
public final class DomainClashConstants {

    private DomainClashConstants() {
        // Utility class — no instantiation.
    }

    // ==================== Feature Flag ====================

    /**
     * Master feature flag for the registry-based clash system.
     * When {@code false}, the legacy NBT-scattered logic remains authoritative.
     * Phase 2 will flip this to {@code true} once the registry is fully wired.
     */
    public static final boolean USE_REGISTRY = true;

    // ==================== Timing Windows (ticks) ====================

    /**
     * How many ticks two entities' failure must occur within to qualify as a tie.
     * Source: {@code DomainClashXpMixin.JJKBRP$TIE_WINDOW_TICKS}.
     */
    public static final long TIE_WINDOW_TICKS = 80L;

    /**
     * Minimum number of ticks a clash must last before an outcome is evaluated.
     * Source: {@code DomainClashXpMixin.JJKBRP$MIN_CLASH_DURATION_TICKS}.
     */
    public static final long MIN_CLASH_DURATION_TICKS = 140L;

    /**
     * Cooldown after a resolved clash result before the same entity can resolve again.
     * Source: {@code DomainClashXpMixin.JJKBRP$RESULT_COOLDOWN_TICKS}.
     */
    public static final long RESULT_COOLDOWN_TICKS = 300L;

    /**
     * Recency window for the {@code jjkbrp_last_clash_contact_tick} check.
     * Source: {@code DomainClashXpMixin.JJKBRP$RECENT_CLASH_CONTACT_TICKS}.
     */
    public static final long RECENT_CLASH_CONTACT_TICKS = 40L;

    /**
     * Shorter recency window used by the actionbar sync path to decide
     * whether the clash HUD should still be displayed.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$ACTIONBAR_CONTACT_WINDOW_TICKS}.
     */
    public static final long ACTIONBAR_CONTACT_WINDOW_TICKS = 24L;

    /**
     * Hardcoded mutual-contact window used in
     * {@code DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact()}.
     * This was an inline {@code 60L} in the original code.
     */
    public static final long MUTUAL_CLASH_CONTACT_TICKS = 60L;

    // ==================== Unified Pressure System ====================

    /**
     * Base pressure rate applied per tick in the unified clash system.
     * This replaces the separate erosion and wrap rates with a single
     * pressure value derived from the power difference.
     *
     * <p>Pressure = (powerDifference - THRESHOLD) * PRESSURE_RATE, clamped to non-negative.
     * The threshold is 10.0 (matching OG's tie threshold).</p>
     */
    public static final double PRESSURE_RATE = 0.06;

    /**
     * Maximum pressure that can be applied in a single tick, regardless of power difference.
     * Prevents runaway damage from huge power disparities.
     */
    public static final double MAX_PRESSURE_PER_TICK = 0.55;

    /**
     * Power difference threshold for considering a tie. If the absolute
     * difference between two domains' power is less than this, no pressure
     * is applied and the clash continues without a pending outcome.
     */
    public static final double POWER_TIE_THRESHOLD = 4.0;

    /**
     * Accumulated pressure threshold that triggers a forced loss for the weaker
     * participant.  When a participant's accumulated pressure in a session
     * exceeds this value, the registry resolves the session in favour of
     * the opponent — regardless of what the base mod's hardcoded hierarchy
     * would have decided.  This decouples the outcome from the vanilla
     * Incomplete-always-loses-to-Closed rule.
     */
    public static final double PRESSURE_LOSS_THRESHOLD = 15.0;

    /**
     * Number of ticks after a clash resolution during which the winner's
     * barrier/form NBT keys are locked and cannot be overwritten by the
     * base mod's global tick loop.  This prevents the cast-order-dependent
     * barrier glitch.
     */
    public static final long BARRIER_LOCK_DURATION_TICKS = 200L;

    /**
     * Grace period before scanner/imported OG loss flags are trusted for resolving
     * a registry clash. This prevents startup flags from ending the HUD instantly.
     */
    public static final long LOSS_FLAG_GRACE_TICKS = 120L;

    /**
     * Extra HUD hold time after resolution before an inactive packet clears it.
     */
    public static final long RESOLVED_HUD_GRACE_TICKS = 10L;

    // ==================== Form Passives ====================

    /**
     * Per-tick barrier-refinement damage reduction for closed domains in a clash.
     * Applied as {@code 0.02 * barrierRefinement}.
     */
    public static final double CLOSED_PASSIVE_REDUCTION_RATE = 0.02;

    /**
     * Per-tick effective-power bonus for open domains in a clash.
     * Applied as {@code 0.005 * sureHitMultiplier}.
     */
    public static final double OPEN_PASSIVE_POWER_RATE = 0.005;

    /**
     * Per-tick totalDamage reduction for incomplete domains actively wrapping.
     */
    public static final double INCOMPLETE_PASSIVE_REDUCTION = 0.003;

    // ==================== Power Formula Reference Values ====================

    /**
     * Reference radius used for the radius power modifier. Domains smaller
     * than this get a power boost; larger domains get a power penalty.
     */
    public static final double REFERENCE_RADIUS = 16.0;

    /**
     * Maximum domain radius considered "normal" - beyond this, additional
     * radius provides diminishing returns to power.
     */
    public static final double MAX_NORMAL_RADIUS = 32.0;

    /**
     * Minimum domain radius - domains below this size get substantial power boost.
     */
    public static final double MIN_RADIUS = 4.0;

    // ==================== Form Hierarchy ====================

    /**
     * Base power multiplier for incomplete domains. Incomplete domains
     * are weaker than closed domains at baseline.
     */
    public static final double INCOMPLETE_FORM_MULTIPLIER = 0.95;

    /**
     * Base power multiplier for closed domains. This is the neutral baseline.
     */
    public static final double CLOSED_FORM_MULTIPLIER = 1.0;

    /**
     * Base power multiplier for open domains. Open domains are stronger
     * at baseline but lack barrier resistance against other open domains.
     */
    public static final double OPEN_FORM_MULTIPLIER = 1.15;

    // ==================== XP Rewards ====================

    /** XP awarded to the clash winner. */
    public static final int WINNER_XP = 50;

    /** XP awarded to both parties in a tie. */
    public static final int TIE_XP = 30;

    /** XP awarded to the clash loser. */
    public static final int LOSER_XP = 10;

    // ==================== Clash Range Multipliers ====================

    /**
     * Multiplier applied to the actual domain radius to compute the clash range
     * for open-form domains.  Open domains use a much larger effective range.
     */
    public static final double OPEN_CLASH_RANGE_MULTIPLIER = 18.0;

    /**
     * Multiplier applied to the actual domain radius to compute the clash range
     * for closed / incomplete domains.
     */
    public static final double CLOSED_CLASH_RANGE_MULTIPLIER = 2.0;

    /**
     * The fraction of combined range used as the clash-window spatial threshold.
     * Two domains are "within base clash window" when their distance² is less
     * than {@code (max(4, combinedRange * this))²}.
     */
    public static final double CLASH_WINDOW_THRESHOLD_FACTOR = 0.65;

    /**
     * Minimum clash-window spatial threshold in blocks.
     */
    public static final double CLASH_WINDOW_MINIMUM_THRESHOLD = 4.0;

    // ==================== NPC Defaults ====================

    /**
     * Default barrier refinement value used for non-Player entities that
     * lack the mastery capability.
     */
    public static final double NPC_DEFAULT_BARRIER_REFINEMENT = 0.3;

    /**
     * Default effective power assumed for opponents whose
     * {@code jjkbrp_effective_power} key is missing or zero.
     */
    public static final double DEFAULT_OPPONENT_POWER = 80.0;

    /**
     * Baseline clash power bonus applied to non-Player entities to compensate
     * for lacking the domain mastery system. Equivalent to roughly Mastery Lv.2
     * for a fair baseline against players who have invested in mastery.
     */
    public static final double NPC_BASELINE_CLASH_POWER = 4.0;

    /**
     * Maximum HP value used for the health ratio calculation in clash power.
     * This prevents high-HP NPCs (200-300+) from being essentially immune
     * to totalDamage degradation. Set to 2× player base HP.
     */
    public static final float HEALTH_NORMALIZATION_CAP = 40.0f;

    // ==================== Incomplete Domain ====================

    /**
     * Default per-tick penalty for incomplete domains when no explicit
     * penalty value is stored.
     */
    public static final double DEFAULT_INCOMPLETE_PENALTY_PER_TICK = 0.01;

    // ==================== Asymmetric Position Pressure ====================

    /**
     * Buffer distance (in blocks) around the domain boundary when determining
     * if an entity is "inside" the domain. Entities within this margin of the
     * edge are considered inside.
     */
    public static final double DOMAIN_BOUNDARY_BUFFER = 1.5;

    /**
     * Pressure multiplier when the weaker participant is inside the stronger
     * participant's domain. This represents the defensive advantage of being
     * inside your own domain (lore-accurate: domains are strong against internal
     * attacks).
     *
     * <p>Closed and Incomplete domains get a strong reduction (0.3 = 70% less
     * pressure). Open domains get moderate reduction (0.6 = 40% less pressure)
     * since they lack barrier integrity.</p>
     */
    public static final double INSIDE_DEFENSE_MULTIPLIER_CLOSED = 0.3;

    /**
     * Pressure multiplier when the weaker participant is inside an Open domain.
     * Open domains have less barrier cohesion so the defensive advantage is
     * smaller than for Closed/Incomplete forms.
     */
    public static final double INSIDE_DEFENSE_MULTIPLIER_OPEN = 0.6;

    /**
     * Pressure multiplier when the weaker participant is outside the stronger
     * participant's domain. The attacker advantage means pressure is slightly
     * amplified (1.2 = 20% more pressure).
     */
    public static final double OUTSIDE_ATTACK_MULTIPLIER = 1.2;

    /**
     * Minimum radius (blocks) for the asymmetric position effect to apply.
     * Very small domains (< 3 blocks) are treated as having minimum radius
     * to avoid excessive multipliers.
     */
    public static final double MIN_ASYMMETRIC_RADIUS = 3.0;

    /**
     * Radius factor for scaling the asymmetric position effect. The effective
     * pressure modifier is multiplied by sqrt(REFERENCE_RADIUS / actualRadius)
     * so that smaller domains have stronger positional asymmetry (more
     * concentrated power).
     */
    public static final double ASYMMETRY_REFERENCE_RADIUS = 16.0;

    // ==================== Legacy Fallback Constants (when USE_REGISTRY=false) ====================
    // These constants are used by the legacy system kept for fallback.
    // They are not used in the unified registry path.
    // NOTE: All erosion/wrap specific constants have been removed as the unified
    // pressure system replaces them completely.
}

