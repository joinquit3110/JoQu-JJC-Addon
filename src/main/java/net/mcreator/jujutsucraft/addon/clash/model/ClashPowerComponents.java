package net.mcreator.jujutsucraft.addon.clash.model;

/**
 * Diagnostic breakdown of a single participant's {@code Clash_Power} computation.
 *
 * <p>This record is produced by {@code PowerCalculator.computeComponents(ParticipantSnapshot)}
 * alongside the final clamped {@code Clash_Power} value so property tests and debug tooling can
 * observe every intermediate factor in the formula described by
 * <a href="file:../../../../../../../../../../../.kiro/specs/domain-clash-system/design.md">
 * {@code design.md}</a> (&sect; "Data Models") and Requirements 4.3&ndash;4.13.
 *
 * <p>The fields correspond one-for-one to the named factors used by
 * {@code PowerCalculator}:
 * <ul>
 *   <li>{@link #baseStrength} &mdash; {@code strengthAmplifier + 10} (default {@code 10}).</li>
 *   <li>{@link #hpFactor} &mdash; {@code max(0, (maxHealth - totalDamage * 2) / maxHealth)}.</li>
 *   <li>{@link #durationFactor} &mdash;
 *       {@code min(min(remainingEffectTicks, 1200) / 2400 + 0.5, 1.0)}.</li>
 *   <li>{@link #formFactor} &mdash; the {@code DomainForm.formFactor} column
 *       ({@code 0.95} / {@code 1.00} / {@code 1.15}).</li>
 *   <li>{@link #radiusFactor} &mdash; {@code clamp(sqrt(22.0 / clashRadius), 0.5, 2.0)}.</li>
 *   <li>{@link #masteryMultiplier} &mdash; mastery-derived multiplicative bonus; {@code 1.0} when
 *       no mastery capability is available.</li>
 *   <li>{@link #masteryFlatBonus} &mdash; mastery-derived additive bonus; {@code 0.0} when no
 *       mastery capability is available.</li>
 *   <li>{@link #domainIdMultiplier} &mdash; domain-id based multiplier ({@code 1.5} for id 27,
 *       {@code 2.0} for id 29, {@code 1.0} otherwise).</li>
 *   <li>{@link #barrierRefinementFactor} &mdash; the {@code (1 + BARRIER_REFINEMENT_PER_LEVEL * L)}
 *       factor applied after the base formula.</li>
 *   <li>{@link #finalPower} &mdash; the final non-negative {@code Clash_Power} value.</li>
 * </ul>
 *
 * <p>This record is purely diagnostic; it is never persisted, synced, or used to drive gameplay
 * logic. All fields are plain {@code double}s.
 *
 * <p>Requirements: 4.2.
 */
public record ClashPowerComponents(
    double baseStrength,
    double hpFactor,
    double durationFactor,
    double formFactor,
    double radiusFactor,
    double masteryMultiplier,
    double masteryFlatBonus,
    double domainIdMultiplier,
    double gradeMultiplier,
    double barrierRefinementFactor,
    double finalPower
) {
}
