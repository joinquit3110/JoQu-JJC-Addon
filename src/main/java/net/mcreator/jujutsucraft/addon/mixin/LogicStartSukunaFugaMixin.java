package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.LogicStartProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LogicStartProcedure.class, remap = false)
public abstract class LogicStartSukunaFugaMixin {
    @Inject(method = {"execute"}, at = {@At(value = "RETURN")}, cancellable = true, remap = false)
    private static void jjkbrp$allowSpecialIncompleteFuga(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int)Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        if (!cir.getReturnValueZ() && ModNetworking.canUseSukunaIncompleteFugaOverride(player, charId, vars.PlayerSelectCurseTechnique)) {
            cir.setReturnValue(true);
        }
    }
}
