package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Startup radius bridge for every reviewed OG domain-specific startup procedure that reads the shared radius. */
@Mixin(targets={"net.mcreator.jujutsucraft.procedures.AngelDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.AuthenticMutualLoveProcedure", "net.mcreator.jujutsucraft.procedures.CeremonialSeaOfLightProcedure", "net.mcreator.jujutsucraft.procedures.ChimeraShadowGardenProcedure", "net.mcreator.jujutsucraft.procedures.ChosoDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.DeadlySentencingProcedure", "net.mcreator.jujutsucraft.procedures.GraveyardDomainProcedure", "net.mcreator.jujutsucraft.procedures.HorizonOfTheCaptivatingSkandhaProcedure", "net.mcreator.jujutsucraft.procedures.IdleDeathGambleProcedure", "net.mcreator.jujutsucraft.procedures.ItadoriDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.IshigoriDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.InumakiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.JinichiDomainProcedure", "net.mcreator.jujutsucraft.procedures.KashimoDomainProcedure", "net.mcreator.jujutsucraft.procedures.KugisakiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.KurourushiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure", "net.mcreator.jujutsucraft.procedures.MeimeiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.NanamiDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.RozetsuDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.SelfEmbodimentOfPerfectionProcedure", "net.mcreator.jujutsucraft.procedures.ThreefoldAfflictionProcedure", "net.mcreator.jujutsucraft.procedures.TimeCellMoonPalaceProcedure", "net.mcreator.jujutsucraft.procedures.TsukumoDomainExpansionProcedure", "net.mcreator.jujutsucraft.procedures.UnlimitedVoidProcedure", "net.mcreator.jujutsucraft.procedures.WombProfusionProcedure"}, remap=false)
public abstract class DomainStartupRadiusMixin {
    @Redirect(method={"execute"}, at=@At(value="FIELD", target="Lnet/mcreator/jujutsucraft/network/JujutsucraftModVariables$MapVariables;DomainExpansionRadius:D", opcode=180), remap=false)
    private static double jjkbrp$readEffectiveStartupRadius(JujutsucraftModVariables.MapVariables mapVariables, LevelAccessor world, double x, double y, double z, Entity entity) {
        if (world == null || world.isClientSide() || !(entity instanceof Player)) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains("jjkbrp_base_domain_radius")) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        return DomainRadiusUtils.computeOgInputRadiusForForm(world, nbt, nbt.getInt("jjkbrp_domain_form_effective"));
    }
}
