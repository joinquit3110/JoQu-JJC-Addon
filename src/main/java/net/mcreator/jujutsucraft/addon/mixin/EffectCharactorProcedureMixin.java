package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.EffectCharactorProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the original passive/on-hit copy capture from giving player Yuta vanilla copied items or unlocks.
 */
@Mixin(value = {EffectCharactorProcedure.class}, remap = false)
public class EffectCharactorProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$disableVanillaPlayerYutaCopyCapture(LevelAccessor world, Entity entity, Entity entityiterator, CallbackInfo ci) {
        if (entityiterator instanceof ServerPlayer player && YutaCopyStore.isActiveYuta(player)) {
            YutaCopyStore.cleanupVanillaPlayerCopy(player);
            ci.cancel();
        }
    }
}
