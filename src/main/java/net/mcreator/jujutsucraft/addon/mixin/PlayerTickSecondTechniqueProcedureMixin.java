package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.init.JujutsucraftModItems;
import net.mcreator.jujutsucraft.procedures.PlayerTickSecondTechniqueProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents unused loudspeakers from forcing active player Yuta into vanilla Cursed Speech second-technique state.
 */
@Mixin(value = {PlayerTickSecondTechniqueProcedure.class}, remap = false)
public class PlayerTickSecondTechniqueProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$blockActiveYutaLoudspeakerSecondTechnique(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            if (YutaCopyStore.isActiveYuta(player) && (player.getMainHandItem().is(JujutsucraftModItems.LOUDSPEAKER.get()) || player.getOffhandItem().is(JujutsucraftModItems.LOUDSPEAKER.get()))) {
                YutaCopyStore.cleanupVanillaPlayerCopy(player);
                ci.cancel();
            }
        }
    }
}
