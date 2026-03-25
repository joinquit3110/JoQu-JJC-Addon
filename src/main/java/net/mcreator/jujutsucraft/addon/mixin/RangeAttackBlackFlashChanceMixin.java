package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Adjusts the base Black Flash roll chance constant from 0.998 to 0.9988.
 * This increases the likelihood of Black Flash occurring.
 */
@Mixin(value = RangeAttackProcedure.class, remap = false)
public class RangeAttackBlackFlashChanceMixin {

    @ModifyConstant(method = "execute",
                    constant = @Constant(doubleValue = 0.998),
                    remap = false)
    private static double jjkblueredpurple$bfRollChance(double original) {
        return 0.9988;
    }
}
