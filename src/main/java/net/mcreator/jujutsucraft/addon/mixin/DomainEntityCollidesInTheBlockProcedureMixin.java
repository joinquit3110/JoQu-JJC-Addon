package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.DomainEntityCollidesInTheBlockProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes vanilla Dhruv-copy advancement/item residue after domain/ranged-ammo damage affects player Yuta.
 */
@Mixin(value = {DomainEntityCollidesInTheBlockProcedure.class}, remap = false)
public class DomainEntityCollidesInTheBlockProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "TAIL")}, remap = false)
    private static void jjkbrp$cleanupVanillaYutaCopyUnlock(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && YutaCopyStore.isActiveYuta(player)) {
            YutaCopyStore.cleanupVanillaPlayerCopy(player);
        }
    }
}
