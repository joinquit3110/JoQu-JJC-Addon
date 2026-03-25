package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.procedures.AIBlueProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects BlueEntity AI for the addon's aim mechanics.
 * Handles the crouch-aim Blue orb behavior without disrupting
 * the base lingering Blue logic.
 */
@Mixin(value = BlueEntity.class, remap = false)
public class BlueEntityMixin {

    @Redirect(method = "m_6075_", // tick
              at = @At(value = "INVOKE",
                      target = "Lnet/mcreator/jujutsucraft/procedures/AIBlueProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
                      remap = false),
              remap = false)
    private void jjkblueredpurple$redirectAIBlue(LevelAccessor world, double x, double y, double z, Entity entity) {
        // Skip if lingering active (addon handles it separately)
        if (entity.getPersistentData().getBoolean("linger_active")) {
            return;
        }

        // Cap the aim distance at 35 blocks
        boolean aimActive = entity.getPersistentData().getBoolean("addon_aim_active");
        if (aimActive) {
            double cnt1 = entity.getPersistentData().getDouble("cnt1");
            if (cnt1 > 35.0) {
                entity.getPersistentData().putDouble("cnt1", 35.0);
            }
        }

        AIBlueProcedure.execute(world, x, y, z, entity);

        // Cancel movement if aiming
        if (aimActive) {
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }
}
