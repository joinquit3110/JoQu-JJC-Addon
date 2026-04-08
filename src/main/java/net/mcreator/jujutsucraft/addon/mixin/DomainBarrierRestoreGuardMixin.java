package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.procedures.JujutsuBarrierUpdateTickProcedure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Barrier-restore guard mixin for `JujutsuBarrierUpdateTickProcedure.execute()` that cancels premature restoration sweeps while an addon-managed domain is still building or active.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={JujutsuBarrierUpdateTickProcedure.class}, remap=false)
public class DomainBarrierRestoreGuardMixin {
    /**
     * Injects at the head of the barrier-restore tick and cancels restoration when the scanned block still belongs to a live addon-managed domain.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkbrp$blockPrematureRestore(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        Vec3 blockPos = new Vec3(x, y, z);
        double scanRange = 96.0;
        AABB scanBox = new AABB(x - scanRange, y - scanRange, z - scanRange, x + scanRange, y + scanRange, z + scanRange);
        // Scan nearby living casters and cancel restoration as soon as the target block is proven to still belong to a live addon-managed domain.
        for (LivingEntity caster : serverLevel.getEntitiesOfClass(LivingEntity.class, scanBox, e -> true)) {
            if (!DomainAddonUtils.isDomainBuildOrActive(serverLevel, caster)) continue;
            Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
            double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)serverLevel, caster.getPersistentData());
            double allowedRadius = actualRadius + 1.75;
            if (center.distanceToSqr(blockPos) > allowedRadius * allowedRadius) continue;
            ci.cancel();
            return;
        }
    }
}
