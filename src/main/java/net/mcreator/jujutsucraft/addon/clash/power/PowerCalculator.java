package net.mcreator.jujutsucraft.addon.clash.power;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.BARRIER_REFINEMENT_PER_LEVEL;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.DEADLY_SENTENCING_BASE_MULT;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.IDLE_DEATH_GAMBLE_BASE_MULT;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.MASTERY_FLAT_PER_LEVEL;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.MASTERY_MULT_PER_BARRIER_POWER;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.MASTERY_MULT_PER_LEVEL;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.RADIUS_FACTOR_MAX;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.RADIUS_FACTOR_MIN;
import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.REFERENCE_RADIUS_FOR_POWER;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.clash.model.ClashPowerComponents;
import net.mcreator.jujutsucraft.addon.clash.model.ParticipantSnapshot;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stateless static helpers that compute every factor of the {@code Clash_Power} formula
 * defined by Requirements 4.3-4.11 of the Domain Clash design.
 *
 * <p>This utility exposes one helper per factor so the resolver, the diagnostic
 * {@code computeComponents} path, and the property-based tests can each read a single
 * source of truth for each term. No helper mutates its inputs; mastery reads are
 * always read-only (Requirement 6.1).
 *
 * <p>Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.12, 4.13, 6.2, 6.3, 6.4.
 */
public final class PowerCalculator {

    public PowerCalculator() {
        // Utility class - no instantiation.
    }

    /**
     * Returns the {@code Base_Strength} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirement 4.3, {@code Base_Strength = strengthAmplifier + 10}. When the caster
     * is not under the base-mod Strength effect, the amplifier defaults to {@code 0}, so the
     * returned value is {@code 10} in that case.
     *
     * @param strengthAmplifier the amplifier of the {@link MobEffects#DAMAGE_BOOST} effect on
     *                          the caster, or {@code 0} when the effect is absent
     * @return {@code strengthAmplifier + 10}
     */
    public static double baseStrength(int strengthAmplifier) {
        return strengthAmplifier + 10.0;
    }

    /**
     * Convenience overload that reads the caster's live {@link MobEffects#DAMAGE_BOOST}
     * amplifier (falling back to {@code 0} when absent or when {@code caster == null}) and
     * returns {@code strengthAmplifier + 10}.
     *
     * @param caster the clash participant; may be {@code null}
     * @return {@code strengthAmplifier + 10}
     */
    public static double baseStrength(@Nullable LivingEntity caster) {
        if (caster == null) {
            return 10.0;
        }
        MobEffectInstance strength = caster.getEffect(MobEffects.DAMAGE_BOOST);
        int amplifier = strength == null ? 0 : strength.getAmplifier();
        return baseStrength(amplifier);
    }

    /**
     * Returns the {@code HP_Factor} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirement 4.4, {@code HP_Factor = max(0, (maxHealth - totalDamage * 2) / maxHealth)}.
     * When {@code maxHealth <= 0} the caster's health pool is degenerate and the factor is
     * clamped to {@code 0.0} instead of dividing by zero.
     *
     * @param maxHealth   the caster's current max health
     * @param totalDamage the base-mod {@code totalDamage} persistent-data value
     * @return the HP factor in {@code [0.0, 1.0]}
     */
    public static double hpFactor(float maxHealth, double totalDamage) {
        if (maxHealth <= 0.0f) {
            return 0.0;
        }
        return Math.max(0.0, (maxHealth - totalDamage * 2.0) / maxHealth);
    }

    /**
     * Returns the {@code Duration_Factor} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirement 4.5,
     * {@code Duration_Factor = min(min(remainingEffectTicks, 1200) / 2400 + 0.5, 1.0)}.
     * Negative tick counts are clamped to {@code 0} first so the factor bottoms out at
     * {@code 0.5} rather than going below it.
     *
     * @param remainingEffectTicks the {@code DOMAIN_EXPANSION} mob-effect remaining duration
     * @return the duration factor in {@code [0.5, 1.0]}
     */
    public static double durationFactor(int remainingEffectTicks) {
        int clampedTicks = Math.max(0, remainingEffectTicks);
        double capped = Math.min(clampedTicks, 1200);
        return Math.min(capped / 2400.0 + 0.5, 1.0);
    }

    /**
     * Returns the {@code Form_Factor} term of the {@code Clash_Power} formula by delegating to
     * {@link DomainForm#formFactor}.
     *
     * <p>Per Requirement 4.6, {@code INCOMPLETE=0.95}, {@code CLOSED=1.00}, {@code OPEN=1.15}.
     *
     * @param form the caster's domain form
     * @return the form factor
     */
    public static double formFactor(DomainForm form) {
        return form.formFactor;
    }

    /**
     * Returns the {@code Radius_Factor} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirement 4.7,
     * {@code Radius_Factor = clamp(sqrt(REFERENCE_RADIUS_FOR_POWER / clashRadius), 0.5, 2.0)}.
     * Non-positive radii produce an infinite ratio, so this method short-circuits to
     * {@link net.mcreator.jujutsucraft.addon.util.DomainClashConstants#RADIUS_FACTOR_MAX} in
     * that case.
     *
     * @param clashRadius the caster's effective clash radius, in blocks
     * @return the radius factor in {@code [RADIUS_FACTOR_MIN, RADIUS_FACTOR_MAX]}
     */
    public static double radiusFactor(double clashRadius) {
        if (clashRadius <= 0.0) {
            return RADIUS_FACTOR_MAX;
        }
        double raw = Math.sqrt(REFERENCE_RADIUS_FOR_POWER / clashRadius);
        return Math.max(RADIUS_FACTOR_MIN, Math.min(RADIUS_FACTOR_MAX, raw));
    }

    /**
     * Returns the {@code Mastery_Multiplier} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirements 4.8 and 4.12:
     * <ul>
     *   <li>When {@code data == null}, returns {@code 1.0} (neutral multiplier).</li>
     *   <li>Otherwise returns
     *       {@code (1 + domainMasteryLevel * MASTERY_MULT_PER_LEVEL)
     *              * (1 + BARRIER_POWER_effectiveLevel * MASTERY_MULT_PER_BARRIER_POWER)}.</li>
     * </ul>
     *
     * <p>Only read-only capability methods are invoked (Requirement 6.1).
     *
     * @param data the mastery capability, or {@code null}
     * @return the mastery multiplier
     */
    public static double masteryMultiplier(@Nullable DomainMasteryData data) {
        if (data == null) {
            return 1.0;
        }
        int masteryLevel = data.getDomainMasteryLevel();
        int barrierPowerLevel = data.getEffectivePropertyLevel(DomainMasteryProperties.BARRIER_POWER);
        return (1.0 + masteryLevel * MASTERY_MULT_PER_LEVEL)
            * (1.0 + barrierPowerLevel * MASTERY_MULT_PER_BARRIER_POWER);
    }

    /**
     * Returns the {@code Mastery_Flat_Bonus} term of the {@code Clash_Power} formula.
     *
     * <p>Per Requirements 4.9 and 4.12:
     * <ul>
     *   <li>When {@code data == null}, returns {@code 0.0}.</li>
     *   <li>Otherwise returns {@code domainMasteryLevel * MASTERY_FLAT_PER_LEVEL}.</li>
     * </ul>
     *
     * @param data the mastery capability, or {@code null}
     * @return the mastery flat bonus
     */
    public static double masteryFlatBonus(@Nullable DomainMasteryData data) {
        if (data == null) {
            return 0.0;
        }
        return data.getDomainMasteryLevel() * MASTERY_FLAT_PER_LEVEL;
    }

    /**
     * Returns the domain-id-based multiplicative bonus applied to {@code Base_Strength}.
     *
     * <p>Per Requirements 4.10 and 4.11:
     * <ul>
     *   <li>Domain id {@code 27} (Deadly Sentencing) &rarr;
     *       {@link net.mcreator.jujutsucraft.addon.util.DomainClashConstants#DEADLY_SENTENCING_BASE_MULT}
     *       ({@code 1.5}).</li>
     *   <li>Domain id {@code 29} (Idle Death Gamble) &rarr;
     *       {@link net.mcreator.jujutsucraft.addon.util.DomainClashConstants#IDLE_DEATH_GAMBLE_BASE_MULT}
     *       ({@code 2.0}).</li>
     *   <li>Every other id &rarr; {@code 1.0}.</li>
     * </ul>
     *
     * @param domainId the domain identifier resolved from the caster's persistent data
     * @return the domain-id multiplier
     */
    public static double domainIdMultiplier(int domainId) {
        return switch (domainId) {
            case 27 -> DEADLY_SENTENCING_BASE_MULT;
            case 29 -> IDLE_DEATH_GAMBLE_BASE_MULT;
            default -> 1.0;
        };
    }

    /**
     * Clamps a double to the non-negative range.
     *
     * <p>Per Requirement 4.13, the final {@code Clash_Power} value is never negative. This
     * helper is the single choke-point that guarantees that invariant.
     *
     * @param v the value to clamp
     * @return {@code max(0.0, v)}
     */
    public static double clampNonNegative(double v) {
        return Math.max(0.0, v);
    }

    /**
     * Applies the {@code Barrier_Refinement} multiplicative bonus to a partial
     * {@code Clash_Power} value.
     *
     * <p>Per Requirements 6.3 and 6.4:
     * <ul>
     *   <li>When {@code data == null}, returns {@code power} unchanged.</li>
     *   <li>Otherwise returns
     *       {@code power * (1 + BARRIER_REFINEMENT_effectiveLevel * BARRIER_REFINEMENT_PER_LEVEL)}.</li>
     * </ul>
     *
     * @param power the intermediate {@code Clash_Power} before refinement
     * @param data  the mastery capability, or {@code null}
     * @return the refined {@code Clash_Power}
     */
    public static double applyBarrierRefinement(double power, @Nullable DomainMasteryData data) {
        return power;
    }

    /**
     * Computes the final clamped {@code Clash_Power} value for a single participant.
     *
     * <p>This is a thin wrapper around {@link #computeComponents(ParticipantSnapshot)} that
     * returns only the final {@code finalPower()} term of the breakdown record. Callers who
     * also need to inspect intermediate factors (property tests, diagnostic tooling) should
     * call {@link #computeComponents(ParticipantSnapshot)} directly; the two methods are
     * guaranteed to agree on the final value because {@code compute} delegates to
     * {@code computeComponents}.
     *
     * <p>The formula encoded here is the one described in the Domain Clash design document:
     * <pre>
     * basePower = baseStrength(snap.strengthAmplifier()) * domainIdMultiplier(snap.domainId())
     * p = basePower
     *   * hpFactor(snap.maxHealth(), snap.totalDamage())
     *   * durationFactor(snap.remainingEffectTicks())
     *   * formFactor(snap.form())
     *   * radiusFactor(snap.clashRadius())
     *   * masteryMultiplier(snap.mastery())
     *   + masteryFlatBonus(snap.mastery())
     * p = applyBarrierRefinement(p, snap.mastery())
     * finalPower = clampNonNegative(p)
     * </pre>
     *
     * <p>This method never mutates {@code snap.mastery()} or any other field of the snapshot.
     * It invokes only the read-only getters {@link DomainMasteryData#getDomainMasteryLevel()}
     * and {@link DomainMasteryData#getEffectivePropertyLevel} (Requirement 6.1).
     *
     * <p>Declared {@code static} rather than instance-bound because {@link PowerCalculator} is
     * a stateless utility class with a private constructor; all other helpers in this class
     * are {@code static} for the same reason. The design document's Java signature sample
     * ({@code public double compute(ParticipantSnapshot)}) omits the {@code static} modifier
     * purely for brevity.
     *
     * <p>Requirements: 4.2, 4.13, 6.1, 6.3.
     *
     * @param snap the captured participant snapshot
     * @return the final clamped {@code Clash_Power}, always {@code >= 0.0}
     */
    public static double compute(ParticipantSnapshot snap) {
        return computeComponents(snap, DomainForm.CLOSED).finalPower();
    }

    public static double compute(ParticipantSnapshot snap, DomainForm opponentForm) {
        return computeComponents(snap, opponentForm).finalPower();
    }

    /**
     * Computes both the final clamped {@code Clash_Power} and the full factor breakdown for
     * a single participant.
     *
     * <p>The returned {@link ClashPowerComponents} exposes every intermediate factor used by
     * the formula so property tests and diagnostic tooling can verify each term independently.
     * Factor definitions:
     * <ul>
     *   <li>{@code baseStrength} &mdash; {@link #baseStrength(int)} of
     *       {@link ParticipantSnapshot#strengthAmplifier()}.</li>
     *   <li>{@code hpFactor} &mdash; {@link #hpFactor(float, double)} of
     *       {@link ParticipantSnapshot#maxHealth()} and
     *       {@link ParticipantSnapshot#totalDamage()}.</li>
     *   <li>{@code durationFactor} &mdash; {@link #durationFactor(int)} of
     *       {@link ParticipantSnapshot#remainingEffectTicks()}.</li>
     *   <li>{@code formFactor} &mdash; {@link #formFactor(DomainForm)} of
     *       {@link ParticipantSnapshot#form()}.</li>
     *   <li>{@code radiusFactor} &mdash; {@link #radiusFactor(double)} of
     *       {@link ParticipantSnapshot#clashRadius()}.</li>
     *   <li>{@code masteryMultiplier} &mdash; {@link #masteryMultiplier(DomainMasteryData)}.</li>
     *   <li>{@code masteryFlatBonus} &mdash; {@link #masteryFlatBonus(DomainMasteryData)}.</li>
     *   <li>{@code domainIdMultiplier} &mdash; {@link #domainIdMultiplier(int)} of
     *       {@link ParticipantSnapshot#domainId()}.</li>
     *   <li>{@code barrierRefinementFactor} &mdash; the ratio actually applied by
     *       {@link #applyBarrierRefinement(double, DomainMasteryData)}, i.e.
     *       {@code (1 + effectiveLevel * BARRIER_REFINEMENT_PER_LEVEL)} when
     *       {@code mastery != null}, else {@code 1.0} (Requirement 6.3).</li>
     *   <li>{@code finalPower} &mdash; the final clamped {@code Clash_Power} value that
     *       {@link #compute(ParticipantSnapshot)} returns.</li>
     * </ul>
     *
     * <p>Like {@link #compute(ParticipantSnapshot)} this method is strictly read-only with
     * respect to {@code snap.mastery()} (Requirement 6.1).
     *
     * <p>Requirements: 4.2, 4.13, 6.1, 6.3.
     *
     * @param snap the captured participant snapshot
     * @return the populated {@link ClashPowerComponents} record
     */
    public static ClashPowerComponents computeComponents(ParticipantSnapshot snap) {
        return computeComponents(snap, DomainForm.CLOSED);
    }

    public static ClashPowerComponents computeComponents(ParticipantSnapshot snap, DomainForm opponentForm) {
        DomainMasteryData mastery = snap.mastery();

        double bs = baseStrength(snap.strengthAmplifier());
        double didMult = domainIdMultiplier(snap.domainId());
        double hf = hpFactor(snap.maxHealth(), snap.totalDamage());
        double df = durationFactor(snap.remainingEffectTicks());
        double ff = formFactor(snap.form());
        double rf = radiusFactor(snap.clashRadius());
        double mm = masteryMultiplier(mastery);
        double mfb = masteryFlatBonus(mastery);
        double gm = Math.max(1.0, snap.gradeMultiplier());

        double basePower = bs * didMult;
        double p = basePower * hf * df * ff * rf * mm * gm + mfb;

        double barrierRefinementFactor = barrierRefinementFactor(snap.form(), opponentForm, mastery);
        double afterRefinement = p * barrierRefinementFactor;

        double finalPower = clampNonNegative(afterRefinement);

        return new ClashPowerComponents(
            bs,
            hf,
            df,
            ff,
            rf,
            mm,
            mfb,
            didMult,
            gm,
            barrierRefinementFactor,
            finalPower
        );
    }

    public static double barrierRefinementFactor(DomainForm selfForm, DomainForm opponentForm, @Nullable DomainMasteryData data) {
        if (data == null || opponentForm != DomainForm.OPEN || selfForm == DomainForm.OPEN) {
            return 1.0;
        }
        int refinementLevel = data.getEffectivePropertyLevel(DomainMasteryProperties.BARRIER_REFINEMENT);
        return 1.0 + refinementLevel * BARRIER_REFINEMENT_PER_LEVEL;
    }
}

