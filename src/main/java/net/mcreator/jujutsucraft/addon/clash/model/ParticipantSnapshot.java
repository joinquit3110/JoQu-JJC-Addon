package net.mcreator.jujutsucraft.addon.clash.model;

import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.clash.power.GradePower;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Immutable per-tick snapshot of a single clash participant's state.
 *
 * <p>This record is the input shape consumed by {@code PowerCalculator} and indirectly by
 * {@code ClashResolver}. It is always produced by a single entry point,
 * {@link #capture(LivingEntity, double)}, so that every field is read in a consistent order
 * from the same live {@link LivingEntity} without any intermediate mutation.
 *
 * <p>Field meanings (see {@code design.md} &sect; "Data Models" and Requirements 4.3-4.5,
 * 6.1, 6.5, 14.4):
 * <ul>
 *   <li>{@link #uuid} &mdash; the caster's {@link UUID} at the moment of capture.</li>
 *   <li>{@link #isPlayer} &mdash; {@code true} iff the caster is a {@link Player}; drives the
 *       mastery and XP branches of the clash pipeline.</li>
 *   <li>{@link #domainId} &mdash; the caster's active domain id, resolved from the persistent
 *       data keys {@code skill_domain}, {@code select}, and {@code jjkbrp_domain_id_runtime}
 *       in that order of preference.</li>
 *   <li>{@link #form} &mdash; the caster's {@link DomainForm}, decoded from the
 *       {@code DOMAIN_EXPANSION} mob-effect amplifier via
 *       {@link DomainForm#fromAmplifier(int)}.</li>
 *   <li>{@link #clashRadius} &mdash; the effective domain radius used for overlap detection and
 *       the {@code Radius_Factor} term of {@code Clash_Power}. Players multiply the caller-
 *       supplied {@code mapRadius} by
 *       {@link DomainMasteryData#getRadiusRuntimeMultiplier()}; non-players use
 *       {@code mapRadius} directly.</li>
 *   <li>{@link #maxHealth} &mdash; the caster's current max health.</li>
 *   <li>{@link #totalDamage} &mdash; the value stored by the base mod in the persistent data
 *       key {@code totalDamage}.</li>
 *   <li>{@link #remainingEffectTicks} &mdash; the {@code DOMAIN_EXPANSION} mob effect's
 *       remaining duration in ticks.</li>
 *   <li>{@link #strengthAmplifier} &mdash; the amplifier of the base-mod Strength
 *       ({@link MobEffects#DAMAGE_BOOST}) effect, or {@code 0} when absent. The clash
 *       {@code Base_Strength} is then {@code strengthAmplifier + 10} per Requirement 4.3.</li>
 *   <li>{@link #mastery} &mdash; the player's {@link DomainMasteryData}, or {@code null} for
 *       non-player casters and for players without the capability attached. The
 *       {@code Mastery_Multiplier} and {@code Mastery_Flat_Bonus} terms collapse to
 *       {@code 1.0} and {@code 0.0} respectively when this is {@code null} (Req 4.12).</li>
 * </ul>
 *
 * <p>The capability reference is captured purely for read-only inspection by the power
 * calculator; nothing in this subsystem mutates the mastery capability through this record
 * (Requirement 6.1).
 *
 * <p>Requirements: 4.3, 4.4, 4.5, 6.1, 6.5, 14.4.
 */
public record ParticipantSnapshot(
    UUID uuid,
    boolean isPlayer,
    int domainId,
    DomainForm form,
    double clashRadius,
    float maxHealth,
    double totalDamage,
    int remainingEffectTicks,
    int strengthAmplifier,
    double gradeMultiplier,
    @Nullable DomainMasteryData mastery
) {

    /**
     * Builds a {@link ParticipantSnapshot} from the live state of {@code caster}.
     *
     * <p>Returns {@code null} when the caster is no longer a valid clash participant, i.e. any of
     * the following holds (Requirement 14.4):
     * <ul>
     *   <li>{@code caster == null};</li>
     *   <li>{@link LivingEntity#isRemoved()} returns {@code true};</li>
     *   <li>{@link LivingEntity#isDeadOrDying()} returns {@code true};</li>
     *   <li>the caster no longer holds the base-mod {@code DOMAIN_EXPANSION} mob effect.</li>
     * </ul>
     *
     * <p>Otherwise every field of the returned snapshot is read from the caster in a single
     * pass, in the order documented above, from the {@code DOMAIN_EXPANSION} mob effect, the
     * base-mod Strength effect, the caster's persistent data, and &mdash; for players &mdash;
     * the {@code jjkblueredpurple:domain_mastery} capability (Requirement 6.1).
     *
     * @param caster    the participant entity; may be {@code null}
     * @param mapRadius the pre-computed {@code DomainExpansionRadius} map variable for the
     *                  caster's level. For player casters the snapshot multiplies this by
     *                  {@link DomainMasteryData#getRadiusRuntimeMultiplier()}; for non-player
     *                  casters the {@code clashRadius} field equals {@code mapRadius} directly
     *                  (Requirement 6.5).
     * @return a freshly populated snapshot, or {@code null} when the caster is no longer a
     *         valid clash participant
     */
    @Nullable
    public static ParticipantSnapshot capture(LivingEntity caster, double mapRadius) {
        if (caster == null || caster.isRemoved() || caster.isDeadOrDying()) {
            return null;
        }
        MobEffect domainEffect = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        MobEffectInstance domainInstance = caster.getEffect(domainEffect);
        if (domainInstance == null) {
            return null;
        }

        int remainingEffectTicks = domainInstance.getDuration();
        int amplifier = domainInstance.getAmplifier();
        DomainForm form = DomainForm.fromAmplifier(amplifier);

        MobEffectInstance strengthInstance = caster.getEffect(MobEffects.DAMAGE_BOOST);
        int strengthAmplifier = strengthInstance == null ? 0 : strengthInstance.getAmplifier();

        CompoundTag nbt = caster.getPersistentData();
        double totalDamage = nbt.getDouble("totalDamage");
        float maxHealth = caster.getMaxHealth();

        // Domain id resolution preference: skill_domain -> select -> jjkbrp_domain_id_runtime.
        int domainId = (int) Math.round(nbt.getDouble("skill_domain"));
        if (domainId == 0) {
            domainId = (int) Math.round(nbt.getDouble("select"));
        }
        if (domainId == 0) {
            domainId = (int) Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        }

        boolean isPlayer = caster instanceof Player;
        DomainMasteryData mastery = null;
        double clashRadius = mapRadius;
        if (isPlayer) {
            Player player = (Player) caster;
            mastery = player
                .getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY)
                .resolve()
                .orElse(null);
            if (mastery != null) {
                clashRadius = mapRadius * mastery.getRadiusRuntimeMultiplier();
            }
        }

        return new ParticipantSnapshot(
            caster.getUUID(),
            isPlayer,
            domainId,
            form,
            clashRadius,
            maxHealth,
            totalDamage,
            remainingEffectTicks,
            strengthAmplifier,
            GradePower.multiplier(caster),
            mastery
        );
    }
}
