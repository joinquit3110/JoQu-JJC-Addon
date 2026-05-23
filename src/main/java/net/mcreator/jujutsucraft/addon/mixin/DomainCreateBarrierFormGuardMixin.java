package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Form-guard mixin for {@code DomainExpansionCreateBarrierProcedure.execute()}.
 *
 * <p><b>Bug being fixed:</b> the base procedure contains a hard-coded "Open form
 * promotion" block that runs on the {@code cnt7 == 0} bootstrap tick. When the
 * caster's selected domain id is {@code 1} (Malevolent Shrine) or {@code 18}
 * (Womb Profusion) and a few base-mod conditions hold (open-barrier advancement
 * unlocked, no sneaking, or {@code SUKUNA_EFFECT}), the base mod forces
 * {@code cnt2 = 1.0} on the caster's persistent data. This overwrites whatever
 * {@code cnt2} the addon already wrote during {@code DomainCreateBarrierMixin}
 * HEAD injection, so the rest of the build phase observes
 * {@code close_type > 0} inside {@code DomainExpansionBattleProcedure} and
 * skips all barrier shell / floor / decoration block placement, never spawns a
 * {@code DomainExpansionEntityEntity} totem, and produces no closed-domain
 * surehit visual.</p>
 *
 * <p>The bug is invisible at the base 1.0× radius because the build window is
 * short (≈ 45 ticks). Once mastery scales the radius up the loop window grows
 * to {@code max(34, scaledRadius * 2 + 1)} ticks, exposing the missing block
 * placement as an aborted half-built barrier.</p>
 *
 * <p><b>Fix:</b> immediately before each invocation of
 * {@code DomainExpansionBattleProcedure.execute(...)} from inside the create
 * barrier procedure, this mixin re-writes {@code cnt2} so it matches the form
 * the addon committed in {@code jjkbrp_domain_form_effective}:
 * <ul>
 *   <li>incomplete (0) &rarr; {@code cnt2 = -1.0}</li>
 *   <li>closed (1) &rarr; {@code cnt2 = 0.0}</li>
 *   <li>open (2) &rarr; {@code cnt2 = 1.0}</li>
 * </ul>
 * This is safe because the base body's force only runs on a single bootstrap
 * tick, while the Battle invocation happens every build tick, so re-writing
 * {@code cnt2} right before each invocation is idempotent.</p>
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value = {DomainExpansionCreateBarrierProcedure.class}, remap = false)
public abstract class DomainCreateBarrierFormGuardMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Injects right before each {@code DomainExpansionBattleProcedure.execute(...)}
     * invocation from within the create barrier procedure to make sure the
     * {@code cnt2} value the Battle procedure reads matches the
     * addon-sanitized form. Without this guard, base-mod's bootstrap-tick
     * promotion can flip {@code cnt2} to {@code 1.0} on Shrine/Womb closed
     * casts and the Battle phase silently skips barrier placement.
     *
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle (not cancellable here, restoration only).
     */
    @Inject(
            method = {"execute"},
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
                            shift = At.Shift.BEFORE
                    )
            },
            remap = false
    )
    private static void jjkbrp$alignCnt2WithEffectiveFormBeforeBattle(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity == null) {
            return;
        }
        if (world.isClientSide()) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        // Only act when the addon has committed an explicit effective form; otherwise leave base behaviour untouched.
        if (!nbt.contains("jjkbrp_domain_form_effective")) {
            return;
        }
        int form = nbt.getInt("jjkbrp_domain_form_effective");
        double targetCnt2;
        switch (form) {
            case 0:
                // Incomplete domains follow the base mod's Megumi-style negative path inside Battle.
                targetCnt2 = -1.0;
                break;
            case 2:
                // Open domains keep the base mod's barrier-skip path active.
                targetCnt2 = 1.0;
                break;
            case 1:
            default:
                // Closed domains must enter the barrier-placement path inside Battle.
                targetCnt2 = 0.0;
                break;
        }
        double currentCnt2 = nbt.getDouble("cnt2");
        if (Math.abs(currentCnt2 - targetCnt2) <= 1.0E-4) {
            return;
        }
        nbt.putDouble("cnt2", targetCnt2);
        LOGGER.debug(
                "[DomainCreateBarrierFormGuard] aligned cnt2 entity={} form={} previous={} corrected={}",
                entity.getName().getString(), form, currentCnt2, targetCnt2);
    }
}
