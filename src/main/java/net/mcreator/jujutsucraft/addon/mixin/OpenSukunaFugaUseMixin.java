package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.logic.FugaDustLogic;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.OpenProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenProcedure.class, remap = false)
public abstract class OpenSukunaFugaUseMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, remap = false)
    private static void jjkbrp$markActiveIncompleteFugaUse(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (world == null || world.isClientSide() || !(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int)Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        double selectId = vars.PlayerSelectCurseTechnique;
        CompoundTag data = player.getPersistentData();
        data.remove("jjkbrp_sukuna_fuga_no_cooldown_casting");
        if (ModNetworking.hasSukunaFugaDustReward(player, charId, selectId)) {
            // A confirmed Fuga dust reward is present: this cast consumes it.
            // The reward flag and dust_amount are intentionally KEPT ALIVE here so
            // AIFlameArrowSukunaDustMixin (the projectile @HEAD) can transfer the
            // real stored dust into the flame arrow's cnt6 before the base mod
            // zeroes dust_amount (Req 3.3, 5.5). The reward-flag/dust/overlay
            // consumption (mirroring FugaRewardState.onFireFuga) is performed once
            // the transfer has had its chance to run, in jjkbrp$clearSpecialFugaCooldown
            // at RETURN, so the reward is consumed exactly once.
            data.putBoolean("jjkbrp_sukuna_fuga_reward_casting", true);
            data.putBoolean("jjkbrp_sukuna_fuga_no_cooldown_casting", true);
            // Record that Fuga was fired while a domain is still active so the
            // post-domain cooldown reset is suppressed for this session (Req 4.6).
            if (FugaDustLogic.isFugaIdentity(charId, selectId)
                    && DomainAddonUtils.isActiveSukunaIncompleteShrine(player)) {
                data.putBoolean("jjkbrp_sukuna_incomplete_fuga_used", true);
            }
            ModNetworking.fillSukunaFugaDust(player);
            return;
        }
        if (!ModNetworking.canUseActiveSukunaIncompleteFugaOverride(player, charId, selectId)) {
            return;
        }
        // Firing Fuga during the active Incomplete Domain Shrine (no reward yet):
        // mark it used so the post-domain cooldown reset is suppressed (Req 4.6).
        data.putBoolean("jjkbrp_sukuna_fuga_no_cooldown_casting", true);
        data.putBoolean("jjkbrp_sukuna_incomplete_fuga_used", true);
        data.remove("jjkbrp_sukuna_fuga_dust_locked_full");
        ModNetworking.clearSukunaFugaDustOverlay(player);
    }

    @Inject(method = {"execute"}, at = {@At(value = "RETURN")}, remap = false)
    private static void jjkbrp$clearSpecialFugaCooldown(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (world == null || world.isClientSide() || !(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        CompoundTag data = player.getPersistentData();
        boolean rewardCasting = data.getBoolean("jjkbrp_sukuna_fuga_reward_casting");
        boolean noCooldown = rewardCasting || data.getBoolean("jjkbrp_sukuna_fuga_no_cooldown_casting");
        if (noCooldown) {
            ModNetworking.clearSukunaFugaCooldown(player);
            if (!rewardCasting && !data.getBoolean("jjkbrp_sukuna_fuga_dust_locked_full")) {
                ModNetworking.clearSukunaFugaDustOverlay(player);
            }
        }
        if (data.getDouble("skill") != 107.0 && data.getDouble("cnt1") > 45.0) {
            // The reward-bearing Fuga cast has completed (the flame arrow spawned at
            // cnt1 == 15 and has had its ticks to run AIFlameArrowSukunaDustMixin's
            // dust -> cnt6 transfer). Consume the reward on the use path as the
            // single coordination point, mirroring FugaRewardState.onFireFuga:
            // remove jjkbrp_sukuna_fuga_dust_locked_full, set dust_amount = 0, and
            // clear the overlay (Req 3.4, 5.5). This runs only when the reward flag
            // is STILL present, so if the projectile path already consumed it
            // (applySukunaFugaDustReward removes the flag), this is a no-op and the
            // reward is consumed exactly once with no double-handling.
            if (rewardCasting && data.getBoolean("jjkbrp_sukuna_fuga_dust_locked_full")) {
                data.remove("jjkbrp_sukuna_fuga_dust_locked_full");
                // clearSukunaFugaDustOverlay sets dust_amount = 0 and clears OVERLAY1/OVERLAY2.
                ModNetworking.clearSukunaFugaDustOverlay(player);
            }
            data.remove("jjkbrp_sukuna_fuga_reward_casting");
            data.remove("jjkbrp_sukuna_fuga_no_cooldown_casting");
        }
    }
}
