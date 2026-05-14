package net.mcreator.jujutsucraft.addon.clash.resolve;

import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * Single choke-point for the {@code Closed_Equivalent_Behavior} loser-effect sequence applied
 * to a caster that has lost (or tied) a {@code Clash_Session}. This is the only place the addon
 * writes back to the base mod's participant state, so it is also the only place Requirement 7
 * is enforced end-to-end (Requirements 2.2, 2.3, 2.4, 7.1, 7.2, 7.3, 7.4, 7.6, 7.7, 12.4).
 *
 * <h2>Ordering contract</h2>
 * The ordering inside {@link #applyLoserEffects(LivingEntity, long)} is load-bearing and is
 * driven by how the base mod's {@code DomainExpansionEffectExpiresProcedure} reacts to the
 * {@code DomainDefeated} and {@code Failed} persistent-data flags when the
 * {@code DOMAIN_EXPANSION} mob effect is removed:
 * <ol>
 *   <li>Write {@code DomainDefeated=true} first (Requirement 7.2). The base mod's expire
 *       procedure reads this flag and skips the barrier-break path, which matches the original
 *       closed-vs-closed semantics required by Requirement 2.3.</li>
 *   <li>Write {@code Failed=true} (Requirement 7.3).</li>
 *   <li>Remove the {@code DOMAIN_EXPANSION} mob effect (Requirements 2.2 and 7.1). Removal of
 *       the effect invokes the base mod's {@code DomainExpansionEffectExpiresProcedure}
 *       naturally, which applies {@code UNSTABLE}, {@code FATIGUE}, and
 *       {@code COOLDOWN_TIME_SIMPLE_DOMAIN} for player casters at the durations the base mod
 *       chooses, without the addon duplicating those applications (Requirement 7.6).</li>
 *   <li>Stamp {@code jjkbrp_clash_result_tick} on the loser and on the opposing participant for
 *       observability by other addon subsystems (Requirement 7.7).</li>
 * </ol>
 *
 * <h2>Winner untouched</h2>
 * This class intentionally never reads or writes the winner's {@code Failed}, {@code Cover},
 * {@code DomainDefeated}, {@code select}, {@code skill_domain}, {@code x_pos_doma},
 * {@code y_pos_doma}, or {@code z_pos_doma} keys, and never removes a mob effect from the
 * winner. Callers are expected to pass only the losing participant to
 * {@link #applyLoserEffects(LivingEntity, long)}, and to call it twice (once per participant)
 * for {@code TIE} outcomes (Requirement 7.5 handled at the caller). Requirement 7.4 and
 * Requirement 12.4 are satisfied because the method body touches nothing on the winner.
 *
 * <h2>Cancellation</h2>
 * Cancelled sessions must <em>not</em> go through {@link #applyLoserEffects}. The
 * {@code ClashResolver} cancellation path deliberately does not call into this class so that
 * {@code DomainDefeated}, {@code Failed}, and {@code jjkbrp_clash_result_tick} remain untouched
 * on either participant, per Requirements 11.3 and 12.4.
 *
 * <h2>Threading</h2>
 * Called on the server-tick thread only. This class is stateless and safe for reuse across
 * sessions.
 */
public final class OutcomeDelivery {

    /**
     * Applies the {@code Closed_Equivalent_Behavior} loser-effect sequence to {@code loser}
     * (Requirements 2.2, 2.3, 2.4, 7.1, 7.2, 7.3, 7.6).
     *
     * <p>The NBT writes happen <em>before</em> the {@code DOMAIN_EXPANSION} mob effect is
     * removed so that the base mod's {@code DomainExpansionEffectExpiresProcedure} observes the
     * flags and skips {@code BreakDomainProcedure} (Requirements 7.2 and 2.3). After the effect
     * is removed, both the loser and the opposing participant receive the
     * {@code jjkbrp_clash_result_tick} stamp (Requirement 7.7 handled together via the caller's
     * second invocation of {@link #markResultTick(LivingEntity, long)}).
     *
     * <p>A {@code null} loser or a client-side entity is a no-op; the outcome sequence is a
     * server-authoritative operation (Requirement 14.1 thread model).
     *
     * @param loser      the losing participant; may be {@code null}, in which case the method
     *                   returns without side effects
     * @param serverTick the server tick on which the outcome is being applied; stored verbatim
     *                   into {@code jjkbrp_clash_result_tick} for downstream observability
     *                   (Requirement 7.7)
     */
    public void applyLoserEffects(LivingEntity loser, long serverTick) {
        if (loser == null) {
            return;
        }
        if (loser.level().isClientSide()) {
            return;
        }
        CompoundTag nbt = loser.getPersistentData();

        // Step 1 + 2 — DomainDefeated first, then Failed (Requirements 7.2 + 2.3, 7.3), so the
        // base mod's expire procedure observes the flags and skips the barrier-break path.
        writeLoserDefeatFlags(nbt);
        markResultTick(loser, serverTick);

        // Step 3 — remove DOMAIN_EXPANSION, which triggers DomainExpansionEffectExpiresProcedure
        // naturally and applies UNSTABLE/FATIGUE for player casters (Requirements 7.1, 7.6, 2.2).
        MobEffect domain = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (domain != null && loser.hasEffect(domain)) {
            loser.removeEffect(domain);
        }

    }

    /**
     * Pure-NBT helper that performs the two "loser defeat flag" writes in the canonical order
     * used by {@link #applyLoserEffects(LivingEntity, long)}: {@code DomainDefeated=true} first
     * (Requirements 7.2 + 2.3), then {@code Failed=true} (Requirement 7.3). This helper is
     * package-private to let the {@code OutcomeDeliveryPropertyTest} validate the loser
     * NBT post-condition without needing a live {@link LivingEntity}/{@code ServerLevel}
     * harness (see task 8.4). The helper is a no-op for a {@code null} tag.
     *
     * <p>Production callers: this method is invoked only by
     * {@link #applyLoserEffects(LivingEntity, long)} itself. Tests drive the method directly
     * against a freshly-created {@link CompoundTag} to prove the three target keys end up at
     * the expected values and that every unrelated prior NBT key remains untouched.
     *
     * @param nbt the loser's persistent-data tag; may be {@code null}, in which case the
     *            method returns without side effects
     */
    static void writeLoserDefeatFlags(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        nbt.putBoolean("DomainDefeated", true);
        nbt.putBoolean("Failed", true);
    }

    /**
     * Pure-NBT helper that stamps {@code jjkbrp_clash_result_tick} (Requirement 7.7) onto the
     * supplied {@link CompoundTag}. This is the entity-free core of
     * {@link #markResultTick(LivingEntity, long)} and is package-private so the task 8.4 test
     * can assert the loser NBT post-condition without bootstrapping Minecraft.
     *
     * @param nbt        the participant's persistent-data tag; may be {@code null}, in which
     *                   case the method returns without side effects
     * @param serverTick the server tick written verbatim into {@code jjkbrp_clash_result_tick}
     */
    static void writeResultTickTag(CompoundTag nbt, long serverTick) {
        if (nbt == null) {
            return;
        }
        nbt.putLong("jjkbrp_clash_result_tick", serverTick);
    }

    /**
     * Writes the {@code jjkbrp_clash_result_tick} persistent-data key on {@code participant}
     * (Requirement 7.7). This single write is the only side effect of the method, so it is safe
     * to call on the winning participant to record the resolution tick without violating
     * Requirement 7.4 &mdash; the winner's {@code Failed}, {@code Cover}, {@code DomainDefeated},
     * {@code select}, {@code skill_domain}, {@code x_pos_doma}, {@code y_pos_doma}, and
     * {@code z_pos_doma} keys are untouched, and {@code jjkbrp_clash_result_tick} is an
     * addon-owned key, not one of the winner-protected keys enumerated by Requirement 7.4.
     *
     * <p>A {@code null} participant is a no-op.
     *
     * @param participant the participant to stamp; may be {@code null}
     * @param serverTick  the server tick recorded verbatim into
     *                    {@code jjkbrp_clash_result_tick}
     */
    public void markResultTick(LivingEntity participant, long serverTick) {
        if (participant == null) {
            return;
        }
        writeResultTickTag(participant.getPersistentData(), serverTick);
    }
}
