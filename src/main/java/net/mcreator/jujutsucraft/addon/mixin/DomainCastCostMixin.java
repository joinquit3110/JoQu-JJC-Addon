package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.procedures.ChangeCurseEnergyProcedure;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Domain cast-cost mixin targeting `DomainExpansionCreateBarrierProcedure.execute()`. It redirects cursed energy consumption during domain startup so incomplete, closed, and open forms use their addon multipliers.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionCreateBarrierProcedure.class}, remap=false)
public abstract class DomainCastCostMixin {
    /**
     * Redirects the cursed-energy change call made during domain startup so the base cost is multiplied by the selected domain form.
     * @param entity entity involved in the current mixin operation.
     * @param energyChange energy change used by this method.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/ChangeCurseEnergyProcedure;execute(Lnet/minecraft/world/entity/Entity;D)V", remap=false), remap=false)
    private static void jjkbrp$adjustDomainCastCost(Entity entity, double energyChange) {
        double adjustedChange = energyChange;
        if (entity instanceof Player) {
            Player player = (Player)entity;
            // Only scale actual cursed-energy spending; positive or neutral changes should remain untouched.
            if (energyChange < 0.0) {
                int form = DomainCastCostMixin.jjkbrp$resolveEffectiveForm(player);
                double multiplier = DomainCastCostMixin.jjkbrp$formMultiplier(form);
                adjustedChange = energyChange * multiplier;
                double baseCost = Math.abs(energyChange);
                double effectiveCost = Math.abs(adjustedChange);
                double delta = effectiveCost - baseCost;
                player.getPersistentData().putDouble("jjkbrp_domain_cast_cost_base", baseCost);
                player.getPersistentData().putDouble("jjkbrp_domain_cast_cost_multiplier", multiplier);
                player.getPersistentData().putDouble("jjkbrp_domain_cast_cost_delta", delta);
                // Persist the resolved cost breakdown so overlays and later diagnostics can explain how the final domain cost was derived.
                player.getPersistentData().putDouble("jjkbrp_domain_cast_cost_effective", effectiveCost);
            }
        }
        ChangeCurseEnergyProcedure.execute((Entity)entity, (double)adjustedChange);
    }

    /**
     * Resolves the active domain form from runtime NBT first and then falls back to the mastery capability when necessary.
     * @param player entity involved in the current mixin operation.
     * @return the resulting resolve effective form value.
     */
    private static int jjkbrp$resolveEffectiveForm(Player player) {
        CompoundTag nbt = player.getPersistentData();
        if (nbt.contains("jjkbrp_domain_form_effective")) {
            return nbt.getInt("jjkbrp_domain_form_effective");
        }
        return player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).map(data -> data.getDomainTypeSelected()).orElse(0);
    }

    /**
     * Returns the cast-cost multiplier for the resolved incomplete, closed, or open domain form.
     * @param form form used by this method.
     * @return the resulting form multiplier value.
     */
    private static double jjkbrp$formMultiplier(int form) {
        return switch (form) {
            case 2 -> 1.6;
            case 1 -> 1.0;
            default -> 0.55;
        };
    }
}
