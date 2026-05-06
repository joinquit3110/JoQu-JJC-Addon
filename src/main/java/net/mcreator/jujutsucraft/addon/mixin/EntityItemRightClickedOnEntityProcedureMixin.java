package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.EntityItemRightClickedOnEntityProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the original Authentic Mutual Love decoration interaction from generating vanilla copied items for player Yuta.
 */
@Mixin(value = {EntityItemRightClickedOnEntityProcedure.class}, remap = false)
public class EntityItemRightClickedOnEntityProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$disableVanillaDomainDecorationCopy(LevelAccessor world, Entity entity, Entity sourceentity, CallbackInfo ci) {
        if (sourceentity instanceof ServerPlayer player && YutaCopyStore.isYuta(player)) {
            YutaCopyStore.cleanupVanillaPlayerCopy(player);
            ci.cancel();
        }
    }
}
