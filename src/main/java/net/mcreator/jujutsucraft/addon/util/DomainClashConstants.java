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
    public static final long MIN_CLASH_DURATION_TICKS = 60L;

    /**
     * Cooldown after a resolved clash result before the same entity can resolve again.
     * Source: {@code DomainClashXpMixin.JJKBRP$RESULT_COOLDOWN_TICKS}.
     */
    public static final long RESULT_COOLDOWN_TICKS = 200L;

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

    // ==================== Pressure / Erosion Rates ====================

    /**
     * Base erosion rate when an open domain pressures a closed barrier.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$BASE_EROSION_RATE}.
     */
    public static final double BASE_EROSION_RATE = 0.2;

    /**
     * Maximum per-tick erosion that can be applied in a single open-vs-closed tick.
     */
    public static final double MAX_EROSION_PER_TICK = 2.0;

    /**
     * Maximum attacker-to-defender power ratio used when normalizing clash calculations.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$MAX_POWER_RATIO}.
     */
    public static final double MAX_POWER_RATIO = 3.0;

    /**
     * Base wrap-pressure rate for incomplete domains against complete domains.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$INCOMPLETE_WRAP_PRESSURE}.
     */
    public static final double INCOMPLETE_WRAP_PRESSURE = 0.1;

    /**
     * Stability damping factor for incomplete wrap-pressure calculations.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$INCOMPLETE_WRAP_STABILITY}.
     */
    public static final double INCOMPLETE_WRAP_STABILITY = 0.45;

    /**
     * Maximum wrap pressure applied per tick to avoid run-away damage.
     */
    public static final double MAX_WRAP_PRESSURE_PER_TICK = 0.18;

    /**
     * Mutual barrier pressure rate for closed-vs-closed clashes.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$CLOSED_VS_CLOSED_RATE}.
     */
    public static final double CLOSED_VS_CLOSED_RATE = 0.06;

    /**
     * Maximum per-tick pressure in closed-vs-closed pairings.
     */
    public static final double MAX_CLOSED_VS_CLOSED_PER_TICK = 0.8;

    /**
     * Mutual sure-hit erosion rate for open-vs-open clashes.
     * Source: {@code DomainClashPenaltyMixin.JJKBRP$OPEN_VS_OPEN_RATE}.
     */
    public static final double OPEN_VS_OPEN_RATE = 0.15;

    /**
     * Maximum per-tick erosion in open-vs-open pairings.
     */
    public static final double MAX_OPEN_VS_OPEN_PER_TICK = 1.5;

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
     * Fallback value used when a closed defender's effective power is
     * missing during erosion calculations.
     */
    public static final double DEFAULT_CLOSED_DEFENDER_POWER = 100.0;

    // ==================== Incomplete Domain ====================

    /**
     * Default per-tick penalty for incomplete domains when no explicit
     * penalty value is stored.
     */
    public static final double DEFAULT_INCOMPLETE_PENALTY_PER_TICK = 0.01;

    /**
     * Multiplier applied to the incomplete penalty when a wrap is active,
     * reducing the self-damage rate during wrapping.
     */
    public static final double INCOMPLETE_WRAP_ACTIVE_PENALTY_FACTOR = 0.45;

    /**
     * Erosion multiplier when the erosion target is itself an incomplete domain.
     */
    public static final double INCOMPLETE_TARGET_EROSION_MULTIPLIER = 2.0;

    /**
     * Wrap pressure multiplier when the wrap target is an open domain.
     */
    public static final double WRAP_VS_OPEN_FACTOR = 0.7;

    /**
     * Wrap pressure multiplier when the wrap target is itself incomplete.
     */
    public static final double WRAP_VS_INCOMPLETE_FACTOR = 0.5;
}
