package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.BlueRedPurpleNukeMod;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.procedures.AIRedProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects RedEntity AI to addon logic when conditions are met.
 * Allows the addon to override base Red behavior with custom Red orb logic.
 */
@Mixin(value = RedEntity.class, remap = false)
public class RedEntityMixin {

    @Redirect(method = "m_6075_", // tick
              at = @At(value = "INVOKE",
                      target = "Lnet/mcreator/jujutsucraft/procedures/AIRedProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
                      remap = false),
              remap = false)
    private void jjkblueredpurple$redirectAIRed(LevelAccessor world, Entity entity) {
        LivingEntity livingRed;
        if (entity instanceof LivingEntity
            && BlueRedPurpleNukeMod.shouldOverrideBaseRed(livingRed = (LivingEntity) entity)) {
            BlueRedPurpleNukeMod.handleRedFromMixin(livingRed);
            return;
        }
        AIRedProcedure.execute(world, entity);
    }
}
