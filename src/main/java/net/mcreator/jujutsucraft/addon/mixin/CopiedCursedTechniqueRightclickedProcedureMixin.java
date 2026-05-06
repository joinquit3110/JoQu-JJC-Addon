package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.CopiedCursedTechniqueRightclickedProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the original copied cursed technique item/sword activation for player Yuta.
 */
@Mixin(value = {CopiedCursedTechniqueRightclickedProcedure.class}, remap = false)
public class CopiedCursedTechniqueRightclickedProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$disableVanillaPlayerYutaCopiedUse(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && YutaCopyStore.isYuta(player)) {
            YutaCopyStore.cleanupVanillaPlayerCopy(player);
            ci.cancel();
        }
    }
}
