package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.AIDomainExpansionEntityProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleanup-entity tick mixin for `AIDomainExpansionEntityProcedure.execute()` that temporarily overrides cleanup radius with the stored real range and keeps the cleanup entity dormant while the owning domain is still alive.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={AIDomainExpansionEntityProcedure.class}, remap=false)
public class DomainCleanupEntityRangeMixin {
    // Thread-local backup of the global cleanup radius so the original map value can be restored after each cleanup tick.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Double> JJKBRP$originalRadius = new ThreadLocal();

    /**
     * Injects at the head of the cleanup tick to swap in the cleanup entity's stored range and optionally cancel the tick while the domain is still active.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkbrp$overrideCleanupRadius(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        JJKBRP$originalRadius.remove();
        if (world.isClientSide() || entity == null) {
            return;
        }
        // When the owning domain still exists, cancel cleanup entirely and keep the entity aligned to the active domain instead.
        if (DomainCleanupEntityRangeMixin.jjkbrp$keepCleanupDormantDuringActiveDomain(world, entity, ci)) {
            return;
        }
        // The cleanup entity carries the authoritative radius snapshot because the shared map value may already have been restored elsewhere.
        double storedRange = entity.getPersistentData().getDouble("range");
        if (storedRange <= 0.0) {
            return;
        }
        try {
            JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
            double original = mapVars.DomainExpansionRadius;
            double overrideRadius = Math.max(1.0, storedRange);
            if (Math.abs(original - overrideRadius) < 1.0E-4) {
                return;
            }
            JJKBRP$originalRadius.set(original);
            mapVars.DomainExpansionRadius = overrideRadius;
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /**
     * Injects after cleanup processing returns to restore the original shared domain radius.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$restoreCleanupRadius(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Double original = JJKBRP$originalRadius.get();
        if (original == null) {
            return;
        }
        JJKBRP$originalRadius.remove();
        try {
            JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius = original;
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /**
     * Keeps the cleanup entity centered and dormant when a matching live domain caster still exists, preventing premature cleanup logic.
     * @param world world access used by the current mixin callback.
     * @param cleanupEntity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     * @return whether keep cleanup dormant during active domain is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$keepCleanupDormantDuringActiveDomain(LevelAccessor world, Entity cleanupEntity, CallbackInfo ci) {
        if (!(world instanceof ServerLevel)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        Vec3 cleanupCenter = new Vec3(cleanupEntity.getPersistentData().getDouble("x_pos"), cleanupEntity.getPersistentData().getDouble("y_pos"), cleanupEntity.getPersistentData().getDouble("z_pos"));
        LivingEntity caster = DomainAddonUtils.findMatchingLiveDomainCaster(serverLevel, cleanupCenter, 4.0);
        if (caster == null) {
            return false;
        }
        Vec3 playerCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
        double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)serverLevel, caster.getPersistentData());
        CompoundTag cleanupNbt = cleanupEntity.getPersistentData();
        cleanupNbt.putDouble("x_pos", playerCenter.x);
        cleanupNbt.putDouble("y_pos", playerCenter.y);
        cleanupNbt.putDouble("z_pos", playerCenter.z);
        cleanupNbt.putDouble("range", actualRadius);
        cleanupNbt.putBoolean("Break", false);
        cleanupNbt.putDouble("cnt_break", 0.0);
        cleanupNbt.putDouble("cnt_life2", 0.0);
        cleanupEntity.setDeltaMovement(Vec3.ZERO);
        cleanupEntity.setPos(playerCenter.x, playerCenter.y, playerCenter.z);
        // Cancelling here prevents the cleanup procedure from restoring blocks while the corresponding domain is still alive.
        ci.cancel();
        return true;
    }
}
