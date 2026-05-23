package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aligns the {@code cnt1 == 34} active-phase trigger inside
 * {@code MalevolentShrineProcedure} and {@code WombProfusionProcedure} with the
 * scaled-radius build window length.
 *
 * <h2>Timing model</h2>
 * Every tick the technique calls one of these procedures, which first invokes
 * {@code DomainExpansionCreateBarrierProcedure}. {@code CreateBarrier} reads
 * {@code cnt1} from NBT, increments it by {@code +1} (when {@code cnt3 >= 20}),
 * runs the spherical-builder pass for ring {@code cnt2 = cnt1}, and writes the
 * incremented value back into NBT. Control returns to the parent procedure
 * which then runs:
 * <pre>
 *   if (cnt1 &gt; 0) {
 *       if (cnt1 == 1.0)  { ... initial pose / sounds ... }
 *       if (cnt1 &gt; 33 &amp;&amp; cnt1 == 34.0) { ... totem spawn / ground search ... }
 *   }
 *   PlayAnimationProcedure.execute(...);
 * </pre>
 *
 * <h2>Bug</h2>
 * The base procedure hard-codes the totem-spawn / ground-search trigger at
 * {@code cnt1 == 34}. The shared spherical builder finishes laying outer-shell
 * blocks at roughly {@code tick == ceil(range * 1.5) + 1} (player position is
 * offset from the dome center by {@code range * 0.5}; the farthest shell point
 * is at distance {@code range + range/2} from the player ring used by the
 * builder). At the unscaled radius {@code range = 22} that resolves to
 * {@code tick 34}, which is exactly when the base mod fires the trigger. When
 * mastery scales the radius up, the build phase finishes at a much later tick
 * ({@code range = 33 → tick 51}) but the trigger still fires at tick 34, so:
 * <ul>
 *   <li>The {@code ENTITY_MALEVOLENT_SHRINE} / {@code ENTITY_WOMB_PROFUSION}
 *       totem spawns mid-build, gets shoved onto the dome by collision with
 *       still-growing barrier blocks.</li>
 *   <li>The base body's {@code y_pos_doma} update at tick 34 shifts the
 *       reference floor for the remaining build ticks; the bone-ring band
 *       (whose key check is {@code y_pos == y_floor}) lands on a moving
 *       floor and is missing on the final shell.</li>
 * </ul>
 *
 * <h2>Fix</h2>
 * Re-time the {@code cnt1 == 34} check to fire at
 * {@code triggerTick = max(34, ceil(range * 1.5) + 1)} without disturbing the
 * shared spherical builder. We never tamper with {@code cnt1} around the
 * {@code CreateBarrier} call &mdash; the build pass uses the genuine
 * post-increment value, so every shell ring is laid down. We only swap
 * {@code cnt1} between {@code CreateBarrier} returning and the parent
 * procedure's two {@code cnt1 ==} checks, then restore it on procedure RETURN.
 *
 * <p>Two cases are handled:
 * <ul>
 *   <li><b>Suppress (during build, {@code cnt1 == 34}, scaled radius):</b>
 *       overwrite {@code cnt1 = 35} so the parent's {@code cnt1 == 34} check
 *       fails. The build pass already happened with the genuine
 *       {@code cnt1 = 34}, so ring 34 is built normally. The bone-ring band
 *       reads the unmodified {@code y_pos_doma}, so it's consistent across
 *       all build ticks. Restore {@code cnt1 = 34} on RETURN.</li>
 *   <li><b>Force (build done, {@code cnt1 == triggerTick}):</b> overwrite
 *       {@code cnt1 = 34} so the parent's check passes and the totem-spawn /
 *       ground-search branch fires once. The build pass already laid down the
 *       (empty) ring at the genuine cnt1 value &mdash; rings beyond the dome
 *       far edge place no blocks. Restore {@code cnt1 = triggerTick} on
 *       RETURN and persist a per-cast latch so the trigger never fires
 *       twice.</li>
 * </ul>
 * At the unscaled radius {@code triggerTick == 34}, the natural and forced
 * timings coincide and the mixin is a no-op aside from setting the latch.</p>
 *
 * <p>The {@code jjkbrp_active_phase_trigger_fired} latch is cleared by
 * {@code DomainExpireBarrierFixMixin} at domain expiry, so each fresh cast can
 * fire its trigger again.</p>
 */
// Binds this addon mixin to the original target classes so only the selected procedures are altered.
@Mixin(targets = {
        "net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure",
        "net.mcreator.jujutsucraft.procedures.WombProfusionProcedure"
}, remap = false)
public abstract class ShrineWombActivePhaseGuardMixin {

    @Unique
    private static final String JJKBRP$TRIGGER_FIRED_KEY = "jjkbrp_active_phase_trigger_fired";

    @Unique
    private static final ThreadLocal<Double> JJKBRP$savedCnt1 = new ThreadLocal<>();

    /**
     * Inject right after {@code CreateBarrier} returns and right before the parent
     * procedure reads {@code cnt1} for its {@code cnt1 == 1} / {@code cnt1 == 34}
     * branches. Rewrites {@code cnt1} so the parent's {@code cnt1 == 34} check
     * either misses (during scaled build) or hits (at the proper end-of-build
     * tick).
     *
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    @Inject(
            method = {"execute"},
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
                            shift = At.Shift.AFTER
                    )
            },
            remap = false
    )
    private static void jjkbrp$alignActivePhaseTriggerAfterCreateBarrier(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        JJKBRP$savedCnt1.remove();
        if (world.isClientSide() || entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains("cnt1")) {
            return;
        }
        double cnt1 = nbt.getDouble("cnt1");
        // The parent procedure's outer guard is `if (cnt1 > 0)`; we only need to act when the
        // tick-34 branch could meaningfully fire or be suppressed, which means cnt1 > 0.
        if (cnt1 < 1.0) {
            return;
        }
        boolean alreadyFired = nbt.getBoolean(JJKBRP$TRIGGER_FIRED_KEY);
        double scaledRange = ShrineWombActivePhaseGuardMixin.jjkbrp$resolveScaledRange(world, nbt);
        // First tick where the spherical builder has provably finished placing every shell block.
        // Player is offset from the dome center by ~range/2, so the farthest shell point is at
        // distance ~range + range/2 from the player ring driving the build. ceil(range * 1.5) + 1
        // is the first tick whose builder ring lies entirely outside the dome shell.
        double triggerTick = Math.max(34.0, Math.ceil(scaledRange * 1.5) + 1.0);
        int triggerTickInt = (int) Math.round(triggerTick);

        // Case A — Suppress: parent body would fire the natural tick-34 trigger this call, but
        // the scaled build window has not reached its proper trigger tick yet. Push cnt1 past
        // 34 so the `cnt1 == 34` check fails. The CreateBarrier build pass for ring cnt2 == 34
        // already ran with the genuine cnt1 == 34, so no shell blocks are skipped.
        if ((int) Math.round(cnt1) == 34 && triggerTickInt != 34 && !alreadyFired) {
            JJKBRP$savedCnt1.set(cnt1);
            nbt.putDouble("cnt1", 35.0);
            return;
        }

        // Case B — Force: build window has just finished and the trigger has not yet fired.
        // Pull cnt1 back to 34 so the parent's `cnt1 == 34` check passes and the totem spawn /
        // ground-search branch runs against a fully built shell. The CreateBarrier build pass
        // for the genuine cnt1 == triggerTick ring already ran, but rings beyond the dome far
        // edge place no blocks anyway, so nothing visible is lost.
        if ((int) Math.round(cnt1) == triggerTickInt && triggerTickInt != 34 && !alreadyFired) {
            JJKBRP$savedCnt1.set(cnt1);
            nbt.putDouble("cnt1", 34.0);
            nbt.putBoolean(JJKBRP$TRIGGER_FIRED_KEY, true);
            return;
        }

        // Unscaled radius (triggerTick == 34): natural timing already correct, just set the
        // latch when the trigger naturally fires this tick so subsequent ticks don't try to
        // re-fire.
        if ((int) Math.round(cnt1) == 34 && triggerTickInt == 34 && !alreadyFired) {
            nbt.putBoolean(JJKBRP$TRIGGER_FIRED_KEY, true);
        }
    }

    /**
     * Restores the genuine post-increment {@code cnt1} value after the parent
     * procedure body finishes, so subsequent ticks see the unaltered counter.
     *
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    @Inject(method = {"execute"}, at = {@At(value = "RETURN")}, remap = false)
    private static void jjkbrp$restoreCnt1OnReturn(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Double saved = JJKBRP$savedCnt1.get();
        JJKBRP$savedCnt1.remove();
        if (saved == null || entity == null) {
            return;
        }
        entity.getPersistentData().putDouble("cnt1", saved);
    }

    /**
     * Resolves the scaled domain range from runtime NBT or falls back to the
     * shared map-variable radius. Keeps integer rounding consistent with the
     * other addon radius-scaling mixins so the trigger tick lines up with the
     * actual end-of-build observed by the shared spherical builder.
     */
    @Unique
    private static double jjkbrp$resolveScaledRange(LevelAccessor world, CompoundTag nbt) {
        if (nbt != null && nbt.contains("jjkbrp_base_domain_radius")) {
            double base = nbt.getDouble("jjkbrp_base_domain_radius");
            double mul = nbt.getDouble("jjkbrp_radius_multiplier");
            if (Math.abs(mul) < 1.0E-4) {
                mul = 1.0;
            }
            return Math.max(1.0, Math.round(base * Math.max(0.5, mul)));
        }
        try {
            return Math.max(1.0, JujutsucraftModVariables.MapVariables.get(world).DomainExpansionRadius);
        } catch (Exception ignored) {
            return 22.0;
        }
    }
}
