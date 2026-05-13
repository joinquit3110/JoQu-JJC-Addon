package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.entity.EntityItemEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModItems;
import net.mcreator.jujutsucraft.procedures.AuthenticMutualLoveProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Authentic Mutual Love incomplete-domain visuals intact, then removes only sword decorations.
 */
@Mixin(value = {AuthenticMutualLoveProcedure.class}, remap = false)
public class AuthenticMutualLoveProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "RETURN")}, remap = false)
    private static void jjkbrp$removeIncompleteYutaDecorationSwords(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (!YutaCopyStore.isActiveYuta(player) || !DomainAddonUtils.isIncompleteDomainState(living)) {
            return;
        }
        double centerX = entity.getPersistentData().getDouble("x_pos_doma");
        double centerY = entity.getPersistentData().getDouble("y_pos_doma");
        double centerZ = entity.getPersistentData().getDouble("z_pos_doma");
        if (centerX == 0.0D && centerY == 0.0D && centerZ == 0.0D) {
            centerX = x;
            centerY = y;
            centerZ = z;
        }
        AABB box = new AABB(centerX - 80.0D, centerY - 24.0D, centerZ - 80.0D, centerX + 80.0D, centerY + 24.0D, centerZ + 80.0D);
        for (Entity candidate : world.getEntitiesOfClass(Entity.class, box, AuthenticMutualLoveProcedureMixin::jjkbrp$isSwordDecoration)) {
            if (entity.getUUID().toString().equals(candidate.getPersistentData().getString("OWNER_UUID"))) {
                candidate.discard();
            }
        }
    }

    private static boolean jjkbrp$isSwordDecoration(Entity entity) {
        if (!(entity instanceof EntityItemEntity itemEntity)) {
            return false;
        }
        if (!itemEntity.getEntityData().get(EntityItemEntity.DATA_domain_decoration)) {
            return false;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return living.getItemBySlot(EquipmentSlot.HEAD).is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get());
    }
}
