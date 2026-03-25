package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.StartCursedTechniqueProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures skill cooldown info whenever a technique is started.
 * This is used to display cooldowns in the skill wheel UI.
 */
@Mixin(value = StartCursedTechniqueProcedure.class, remap = false)
public class StartCursedTechniqueProcedureMixin {

    @Inject(method = "execute", at = @At(value = "RETURN"), remap = false)
    private static void jjkblueredpurple$captureCooldown(LevelAccessor world, double x, double y, double z,
                                                         Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer)) return;

        ServerPlayer player = (ServerPlayer) entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(
            JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
            .orElse(new JujutsucraftModVariables.PlayerVariables());

        ModNetworking.captureActiveSkillCooldown(player, vars);
    }
}
