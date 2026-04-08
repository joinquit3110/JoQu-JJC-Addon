package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.procedures.DomainExpansionEntityOnInitialEntitySpawnProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleanup-entity spawn mixin for `DomainExpansionEntityOnInitialEntitySpawnProcedure.execute()` that seeds the spawned cleanup entity with the real domain center and scaled range.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionEntityOnInitialEntitySpawnProcedure.class}, remap=false)
public class DomainCleanupEntitySpawnMixin {
    /**
     * Injects after cleanup-entity spawn so the entity inherits the real center, radius, and dormant cleanup state of the matching live domain.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$seedCleanupEntityRange(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof DomainExpansionEntityEntity)) {
            return;
        }
        DomainExpansionEntityEntity cleanupEntity = (DomainExpansionEntityEntity)entity;
        Level level = cleanupEntity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        LivingEntity owner = DomainAddonUtils.findMatchingLiveDomainCaster(serverLevel, cleanupEntity.position(), 4.0);
        if (owner == null) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)owner);
        double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)serverLevel, owner.getPersistentData());
        CompoundTag cleanupNbt = cleanupEntity.getPersistentData();
        cleanupNbt.putDouble("x_pos", center.x);
        cleanupNbt.putDouble("y_pos", center.y);
        cleanupNbt.putDouble("z_pos", center.z);
        cleanupNbt.putDouble("range", actualRadius);
        cleanupNbt.putBoolean("Break", false);
        cleanupNbt.putDouble("cnt_break", 0.0);
        cleanupNbt.putDouble("cnt_life2", 0.0);
        cleanupEntity.setDeltaMovement(Vec3.ZERO);
        // Move the cleanup entity directly onto the real domain center so future cleanup scans start from the correct location.
        cleanupEntity.setPos(center.x, center.y, center.z);
    }
}
