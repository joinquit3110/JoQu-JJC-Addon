package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.procedures.AttackWeakProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Adjusts the attack slow charge rate constants.
 * Reduces slow charge penalties for a better gameplay feel.
 */
@Mixin(value = AttackWeakProcedure.class, remap = false)
public class AttackWeakProcedureMixin {

    @ModifyConstant(method = "execute",
                    constant = @Constant(doubleValue = 0.25),
                    remap = false)
    private static double jjkblueredpurple$slowChargeHigh(double original) {
        return 0.075;
    }

    @ModifyConstant(method = "execute",
                    constant = @Constant(doubleValue = 0.125),
                    remap = false)
    private static double jjkblueredpurple$slowChargeLow(double original) {
        return 0.04;
    }
}
