package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Startup-phase radius scaling mixin applied to multiple domain startup procedures. It temporarily restores the addon-calculated domain radius immediately after barrier creation and then restores the shared map radius on return.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(targets={"net.mcreator.jujutsucraft.procedures.AngelDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.AuthenticMutualLoveProcedure", "net.mcreator.jujutsucraft.procedures.CeremonialSeaOfLightProcedure", "net.mcreator.jujutsucraft.procedures.ChimeraShadowGardenProcedure", "net.mcreator.jujutsucraft.procedures.ChosoDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.DeadlySentencingProcedure", "net.mcreator.jujutsucraft.procedures.GraveyardDomainProcedure", "net.mcreator.jujutsucraft.procedures.HorizonOfTheCaptivatingSkandhaProcedure", "net.mcreator.jujutsucraft.procedures.IdleDeathGambleProcedure", "net.mcreator.jujutsucraft.procedures.ItadoriDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.IshigoriDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.InumakiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.JinichiDomainProcedure", "net.mcreator.jujutsucraft.procedures.KugisakiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.KurourushiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure", "net.mcreator.jujutsucraft.procedures.MeimeiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.NanamiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.RozetsuDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.ThreefoldAfflictionProcedure", "net.mcreator.jujutsucraft.procedures.TimeCellMoonPalaceProcedure", "net.mcreator.jujutsucraft.procedures.TsukumoDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.UnlimitedVoidProcedure", "net.mcreator.jujutsucraft.procedures.WombProfusionProcedure"}, remap=false)
public abstract class DomainStartupRadiusMixin {
    // Thread-local backup of the shared domain radius during startup-phase scaling.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Double> JJKBRP$originalRadius = new ThreadLocal();

    /**
     * Injects immediately after barrier creation inside each startup procedure so the shared radius reflects the addon-scaled domain size during the rest of startup logic.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects around a specific invocation point so the addon can react at the exact stage where the original method reaches that call.
    @Inject(method={"execute"}, at={@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", shift=At.Shift.AFTER)}, remap=false)
    private static void jjkbrp$scalePostBarrierStartup(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        JJKBRP$originalRadius.remove();
        if (world.isClientSide()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        CompoundTag nbt = player.getPersistentData();
        // Startup scaling only makes sense after the barrier mixin has already stored the original base radius in runtime NBT.
        if (!nbt.contains("jjkbrp_base_domain_radius")) {
            return;
        }
        if (Math.abs(nbt.getDouble("jjkbrp_radius_multiplier") - 1.0) < 1.0E-4) {
            return;
        }
        try {
            JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
            double original = mapVars.DomainExpansionRadius;
            double scaled = DomainAddonUtils.getActualDomainRadius(world, nbt);
            if (Math.abs(original - scaled) < 1.0E-4) {
                return;
            }
            JJKBRP$originalRadius.set(original);
            // Temporarily expose the actual domain radius to the remainder of the startup procedure, then restore it on method return.
            mapVars.DomainExpansionRadius = scaled;
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /**
     * Injects on return from each startup procedure to restore the original shared radius after startup processing is done.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$restorePostBarrierStartup(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
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
}
