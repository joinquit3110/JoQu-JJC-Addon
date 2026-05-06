package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Small combat tuning mixin for `RangeAttackProcedure.execute()` that replaces the original Black Flash probability constant with a rarer addon-specific value.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={RangeAttackProcedure.class}, remap=false)
public class RangeAttackBlackFlashChanceMixin {
    /**
     * Replaces the base Black Flash probability constant with the addon rarity value.
     * @param original original used by this method.
     * @return the resulting bf roll chance value.
     */
    // Intercepts a literal constant inside the target method and replaces only that numeric value with the addon-tuned value.
    @ModifyConstant(method={"execute"}, constant={@Constant(doubleValue=0.998)}, remap=false)
    private static double jjkblueredpurple$bfRollChance(double original) {
        // Increase the exponent base slightly so the resulting Black Flash chance curve becomes rarer than the original value.
        return 0.9988;
    }
}
