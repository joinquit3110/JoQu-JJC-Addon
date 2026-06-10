package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.MalevolentShrineActiveProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Activation mixin for {@code MalevolentShrineActiveProcedure.execute(LevelAccessor, Entity)}.
 *
 * <p>On the activation tick of the Incomplete Domain Shrine for Sukuna with the surehit
 * upgrade purchased, this re-asserts the three surehit state flags in the caster's
 * persistent NBT so surehit is recognized from the very first tick and survives a
 * save/reload while the domain effect is present (Req 2.5, 2.6).</p>
 *
 * <p>The sure-hit VFX and radius-bounded terrain pulverization are NOT driven from here: this
 * procedure is masked/redirected by the addon and only runs while {@code skill_domain == 1}, so
 * it is not a reliable per-tick hook. Those effects are instead driven from
 * {@code CooldownTrackerEvents.handleSukunaIncompleteSureHitReward} (which runs every server tick
 * while the shrine is live) via {@code SureHitShrineFx}.</p>
 */
@Mixin(value = MalevolentShrineActiveProcedure.class, remap = false)
public abstract class MalevolentShrineActiveSukunaIncompleteMixin {

    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, remap = false)
    private static void jjkbrp$prepareSpecialIncompleteShrine(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (world == null || world.isClientSide() || !(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        if (!AddonGameRules.sukunaIncompleteSurehit(caster)) {
            return;
        }
        if (!jjkbrp$isSpecialIncompleteShrine(caster)) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        data.putDouble("skill_domain", 1.0);
        data.putBoolean("DomainAttack", true);
        data.putBoolean("StartDomainAttack", true);
        // Set all three surehit flags on the activation tick so surehit is active from the
        // first tick (Req 2.5) and survives a save/reload via getPersistentData() (Req 2.6).
        data.putBoolean("jjkbrp_sukuna_incomplete_surehit_active", true);
        data.putBoolean("jjkbrp_sukuna_incomplete_surehit_session", true);
        data.putBoolean("jjkbrp_sukuna_incomplete_surehit_had_domain", true);
        if (caster instanceof ServerPlayer player && AddonGameRules.sukunaFugaReward(player)) {
            ModNetworking.fillSukunaFugaDust(player);
        }
    }

    /**
     * Non-circular activation gate for the special Sukuna Incomplete Domain Shrine.
     *
     * <p>Restricted to a server-side {@link ServerPlayer} (the surehit purchase lives in the
     * player-scoped DomainMastery capability). Returns {@code true} only when the caster is
     * Sukuna ({@code charId == 1}), has purchased the surehit upgrade
     * ({@link DomainMasteryData#isSukunaIncompleteSureHitUnlocked()}), and is currently in the
     * incomplete shrine form (incomplete domain state + runtime domain id 1 + a live domain
     * effect). It deliberately does not read the surehit flags themselves to avoid a circular
     * dependency on the state it is responsible for setting.</p>
     */
    @Unique
    private static boolean jjkbrp$isSpecialIncompleteShrine(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int)Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        if (charId != 1) {
            return false;
        }
        boolean surehitUnlocked = player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null)
                .map(DomainMasteryData::isSukunaIncompleteSureHitUnlocked)
                .orElse(false);
        if (!surehitUnlocked) {
            return false;
        }
        return DomainAddonUtils.isIncompleteDomainState(player)
                && DomainAddonUtils.resolveRuntimeDomainId(player) == 1
                && DomainAddonUtils.hasLiveDomainEffect(player);
    }
}
