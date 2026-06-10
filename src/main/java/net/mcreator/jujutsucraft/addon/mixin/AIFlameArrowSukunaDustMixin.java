package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.logic.FugaDustLogic;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.AIFlameArrowProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers the Incomplete Domain Shrine's stored Fuga dust reward into the
 * flame-arrow projectile's {@code cnt6} boost at the {@code HEAD} of
 * {@link AIFlameArrowProcedure#execute}, before the base mod zeroes the owner's
 * {@code dust_amount} later in {@code execute}. The {@code entity} argument is
 * the flame-arrow projectile; its living owner is resolved via the addon's
 * shared {@code OWNER_UUID} lookup. The boost is gated on the Sukuna + Fuga
 * identity and applied at most once per projectile.
 */
@Mixin(value = AIFlameArrowProcedure.class, remap = false)
public abstract class AIFlameArrowSukunaDustMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, remap = false)
    private static void jjkbrp$applyStoredSukunaDust(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (world == null || world.isClientSide() || entity == null) {
            return;
        }
        // The boost may apply to at most one projectile (Req 3.5).
        CompoundTag projectileData = entity.getPersistentData();
        if (projectileData.getBoolean("jjkbrp_sukuna_fuga_dust_applied")) {
            return;
        }
        LivingEntity owner = DomainAddonUtils.resolveOwnerEntity(world, entity);
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if (!AddonGameRules.sukunaFugaReward(player)) {
            return;
        }
        // Gate on the Sukuna (charId == 1) + Fuga (select id 7) identity (Req 3.7),
        // using the same capability access pattern as OpenSukunaFugaUseMixin.
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int) Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        if (!FugaDustLogic.isFugaIdentity(charId, vars.PlayerSelectCurseTechnique)) {
            return;
        }
        // applySukunaFugaDustReward checks the jjkbrp_sukuna_fuga_dust_locked_full
        // reward flag, transfers resolveTransferDust(...) into cnt6, marks the
        // projectile applied, removes the reward flag, zeroes dust, clears the
        // overlay, and syncs the cooldown. Mark the projectile here too so the
        // "applied once" guard holds regardless of the reward path taken.
        if (ModNetworking.applySukunaFugaDustReward(player, entity)) {
            projectileData.putBoolean("jjkbrp_sukuna_fuga_dust_applied", true);
        }
    }
}
