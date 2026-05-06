package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.procedures.BreakDomainProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BreakDomainProcedure.class}, remap=false)
public abstract class DomainBreakRadiusSnapshotMixin {
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$copyCasterRadiusToCleanupMarker(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity) {
            DomainAddonUtils.copyDomainRadiusSnapshotToCleanupMarker(world, (LivingEntity)entity);
        }
    }
}
