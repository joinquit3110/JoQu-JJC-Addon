package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Active-tick radius scaling mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()` that temporarily swaps in the actual mastery-scaled domain radius while the original tick procedure runs.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public class DomainActiveTickRadiusMixin {
    /**
     * Injects at the head of the active-tick procedure to replace the shared radius with the mastery-scaled domain radius for the duration of the base call.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$scaleRadiusForTick(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        double radiusMul;
        if (world.isClientSide()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        // Clear any stale scaling context first so an interrupted previous tick cannot leak its backup radius into the next execution.
        Double stale = DomainRadiusUtils.getOriginalRadiusIfScaling();
        if (stale != null) {
            DomainRadiusUtils.clearScalingContext();
        }
        radiusMul = player.getPersistentData().getDouble("jjkbrp_radius_multiplier");
        if (Math.abs(radiusMul) < 1.0E-4) {
            radiusMul = 1.0;
        }
        if (!player.getPersistentData().contains("jjkbrp_base_domain_radius")) {
            return;
        }
        try {
            JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
            double original = mapVars.DomainExpansionRadius;
            double scaled = Math.max(1.0, player.getPersistentData().getDouble("jjkbrp_base_domain_radius") * radiusMul);
            if (Math.abs(original - scaled) < 1.0E-4) {
                return;
            }
            mapVars.DomainExpansionRadius = scaled;
            DomainRadiusUtils.onScalingApplied(world, original);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /**
     * Injects after the active-tick procedure returns to restore the original shared domain radius.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$restoreRadiusAfterTick(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Double original = DomainRadiusUtils.getOriginalRadiusIfScaling();
        if (original == null) {
            return;
        }
        DomainRadiusUtils.clearScalingContext();
        try {
            JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
            mapVars.DomainExpansionRadius = original;
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}
