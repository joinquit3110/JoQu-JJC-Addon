package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.TechniqueImitation1Procedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the original Yuta copied Cursed Speech path from granting loudspeakers to player Yuta.
 */
@Mixin(value = {TechniqueImitation1Procedure.class}, remap = false)
public class TechniqueImitation1ProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$disablePlayerYutaVanillaCursedSpeechLoudspeaker(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            if (YutaCopyStore.isActiveYuta(player)) {
                YutaCopyStore.cleanupVanillaPlayerCopy(player);
                ci.cancel();
            }
        }
    }
}
