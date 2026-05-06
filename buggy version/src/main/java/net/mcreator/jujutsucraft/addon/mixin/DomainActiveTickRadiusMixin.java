package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scoped radius bridge for OG active ticks; no radius value is left in the shared map variable. */
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public class DomainActiveTickRadiusMixin {
    @Redirect(method={"execute"}, at=@At(value="FIELD", target="Lnet/mcreator/jujutsucraft/network/JujutsucraftModVariables$MapVariables;DomainExpansionRadius:D", opcode=180), remap=false)
    private static double jjkbrp$readEffectiveRadius(JujutsucraftModVariables.MapVariables mapVariables, LevelAccessor world, double x, double y, double z, Entity entity) {
        if (world == null || world.isClientSide() || !(entity instanceof LivingEntity)) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains("jjkbrp_base_domain_radius")) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        return DomainRadiusUtils.computeOgInputRadiusForForm(world, nbt, nbt.getInt("jjkbrp_domain_form_effective"));
    }
}
