package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.procedures.AttackWeakProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Charge-window tuning mixin for `AttackWeakProcedure.execute()` that shrinks the original charge thresholds to make the addon Black Flash timing window stricter.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={AttackWeakProcedure.class}, remap=false)
public class AttackWeakProcedureMixin {
    /**
     * Replaces the higher original charge-window constant with the addon nerfed value.
     * @param original original used by this method.
     * @return the resulting slow charge high value.
     */
    // Intercepts a literal constant inside the target method and replaces only that numeric value with the addon-tuned value.
    @ModifyConstant(method={"execute"}, constant={@Constant(doubleValue=0.25)}, remap=false)
    private static double jjkblueredpurple$slowChargeHigh(double original) {
        // Tighten the higher charge window so players must commit much closer to the final timing band.
        return 0.075;
    }

    /**
     * Replaces the lower original charge-window constant with the addon nerfed value.
     * @param original original used by this method.
     * @return the resulting slow charge low value.
     */
    // Intercepts a literal constant inside the target method and replaces only that numeric value with the addon-tuned value.
    @ModifyConstant(method={"execute"}, constant={@Constant(doubleValue=0.125)}, remap=false)
    private static double jjkblueredpurple$slowChargeLow(double original) {
        // Tighten the lower companion window as part of the same overall charge nerf.
        return 0.04;
    }
}
