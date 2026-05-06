package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainCostUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.OCostProcedure;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overlay mixin for `OCostProcedure.execute()` that replaces the displayed cursed energy cost for domain techniques with the effective form-adjusted cast cost.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={OCostProcedure.class}, remap=false)
public class OCostProcedureMixin {
    /**
     * Injects at the head of the overlay cost procedure and replaces the shown text with the effective domain cast cost for the currently selected form.
     * @param entity entity involved in the current mixin operation.
     * @param cir callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkbrp$overrideDomainCostText(Entity entity, CallbackInfoReturnable<String> cir) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (!DomainCostUtils.isDomainTechniqueSelected(vars)) {
            return;
        }
        // Replace the overlay cost with the same effective cast value that the startup mixin and cast-cost redirect will use.
        double expected = DomainCostUtils.resolveExpectedDomainCastCost(player, vars);
        String label = Component.translatable((String)"jujutsu.overlay.cost").getString();
        cir.setReturnValue(label + ": " + Math.round(expected));
    }
}
