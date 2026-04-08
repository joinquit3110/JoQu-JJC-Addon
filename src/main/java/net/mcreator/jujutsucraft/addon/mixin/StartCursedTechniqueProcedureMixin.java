package net.mcreator.jujutsucraft.addon.mixin;

import java.util.UUID;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainCostUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.StartCursedTechniqueProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Technique-start mixin for `StartCursedTechniqueProcedure.execute()` that previews the real domain cast cost before the cast begins, blocks the cast when cursed energy is insufficient, and caps incomplete-domain cooldowns after the procedure completes.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={StartCursedTechniqueProcedure.class}, remap=false)
public class StartCursedTechniqueProcedureMixin {
    // Maximum cooldown duration allowed for incomplete-domain casts.
    private static final int JJKBRP$INCOMPLETE_DOMAIN_COOLDOWN_TICKS = 600;

    /**
     * Injects at the start of technique activation to preview the real domain cost, synchronize the overlay-facing preview values, and cancel the cast if cursed energy is insufficient.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private static void jjkbrp$syncDomainCostBeforeStart(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (world.isClientSide()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(vars -> {
            // Non-domain techniques should keep the original startup flow, so all preview keys are cleared and the mixin exits early.
            if (!DomainCostUtils.isDomainTechniqueSelected(vars)) {
                player.getPersistentData().remove("jjkbrp_domain_cast_cost_preview");
                player.getPersistentData().remove("jjkbrp_pending_incomplete_cd_tune");
                return;
            }
            double baseCost = DomainCostUtils.resolveTechniqueBaseCost(player, vars);
            // Preview the real form-adjusted domain cost here so both the cast gate and the UI can reference the same authoritative value.
            double expectedCost = DomainCostUtils.resolveExpectedDomainCastCost(player, vars);
            boolean incompleteForm = DomainCostUtils.resolveEffectiveForm(player) == 0;
            vars.PlayerSelectCurseTechniqueCost = baseCost;
            vars.syncPlayerVariables((Entity)player);
            player.getPersistentData().putDouble("jjkbrp_domain_cast_cost_preview", expectedCost);
            if (incompleteForm) {
                player.getPersistentData().putBoolean("jjkbrp_pending_incomplete_cd_tune", true);
            } else {
                player.getPersistentData().remove("jjkbrp_pending_incomplete_cd_tune");
            }
            // Block the cast before the base procedure spends anything when the player cannot afford the effective domain cost.
            if (!player.isCreative() && vars.PlayerCursePower < expectedCost) {
                player.displayClientMessage((Component)Component.translatable((String)"jujutsu.message.dont_use"), true);
                player.getPersistentData().remove("jjkbrp_pending_incomplete_cd_tune");
                ci.cancel();
            }
        });
    }

    /**
     * Injects after the technique-start procedure returns to capture the active cooldown state and then apply incomplete-domain cooldown caps.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkblueredpurple$captureCooldown(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        ModNetworking.captureActiveSkillCooldown(player, vars);
        // Post-process the captured cooldown because incomplete domains are intentionally capped at a much shorter recovery window.
        StartCursedTechniqueProcedureMixin.jjkbrp$applyIncompleteCooldownTuning(player);
    }

    /**
     * Clamps incomplete-domain cooldown effects to the addon maximum and schedules a one-tick retry if the effect was not ready immediately.
     * @param player entity involved in the current mixin operation.
     */
    private static void jjkbrp$applyIncompleteCooldownTuning(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        if (!nbt.getBoolean("jjkbrp_pending_incomplete_cd_tune")) {
            return;
        }
        nbt.remove("jjkbrp_pending_incomplete_cd_tune");
        boolean changedNow = false;
        changedNow |= StartCursedTechniqueProcedureMixin.jjkbrp$clampCooldown(player, (MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME.get(), 600);
        if ((changedNow |= StartCursedTechniqueProcedureMixin.jjkbrp$clampCooldown(player, (MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get(), 600)) || player.server == null) {
            return;
        }
        UUID playerId = player.getUUID();
        int runAt = player.server.getTickCount() + 1;
        // Retry one tick later if the cooldown effect was not yet available on the first return pass.
        player.server.tell(new TickTask(runAt, () -> {
            if (player.server == null) {
                return;
            }
            ServerPlayer retryPlayer = player.server.getPlayerList().getPlayer(playerId);
            if (retryPlayer == null) {
                return;
            }
            StartCursedTechniqueProcedureMixin.jjkbrp$clampCooldown(retryPlayer, (MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME.get(), 600);
            StartCursedTechniqueProcedureMixin.jjkbrp$clampCooldown(retryPlayer, (MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get(), 600);
        }));
    }

    /**
     * Short helper that truncates an active cooldown effect to a maximum duration without changing any other effect properties.
     * @param player entity involved in the current mixin operation.
     * @param effect effect instance processed by this helper.
     * @param maxDuration max duration used by this method.
     * @return whether clamp cooldown is true for the current runtime state.
     */
    private static boolean jjkbrp$clampCooldown(ServerPlayer player, MobEffect effect, int maxDuration) {
        if (player == null || effect == null || maxDuration <= 0) {
            return false;
        }
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null || instance.getDuration() <= maxDuration) {
            return false;
        }
        return DomainAddonUtils.setEffectDuration(instance, maxDuration);
    }
}
