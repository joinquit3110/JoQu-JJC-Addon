package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.procedures.CursedTechniqueOkkotsuProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents player Yuta from activating the original hardcoded Copy slots while leaving mobs and addon Rika Copy bridge untouched.
 */
@Mixin(value = {CursedTechniqueOkkotsuProcedure.class}, remap = false)
public class CursedTechniqueOkkotsuProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$blockPlayerYutaVanillaHardcodedCopyRuntime(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || !YutaCopyStore.isYuta(player)) {
            return;
        }
        double skill = player.getPersistentData().getDouble("skill");
        if (!YutaCopyStore.isVanillaHardcodedCopyRuntime(skill)) {
            return;
        }
        double selectedCustomSkill = player.getPersistentData().getDouble(YutaCopyStore.KEY_SELECTED);
        if (Math.abs(selectedCustomSkill - skill) <= 0.001D) {
            return;
        }
        YutaCopyStore.sanitizeVanillaHardcodedCopyState(player);
        ci.cancel();
    }
}
