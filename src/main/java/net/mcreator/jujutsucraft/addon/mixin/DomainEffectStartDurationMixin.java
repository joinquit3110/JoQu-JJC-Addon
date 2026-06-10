package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionEffectStartedappliedProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Effect-start duration fix mixin for `DomainExpansionEffectStartedappliedProcedure.execute()` that realigns the first applied domain-effect duration with mastery-controlled duration properties.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionEffectStartedappliedProcedure.class}, remap=false)
public class DomainEffectStartDurationMixin {
    // Logger used for diagnostics emitted by this mixin.
    /**
     * Injects after the first domain-effect application to correct the starting duration so it matches mastery-controlled duration bonuses.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$extendDurationOnEffectStart(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (player.level().isClientSide()) {
            return;
        }
        if (!AddonGameRules.domainDurationRules(player)) {
            return;
        }
        CompoundTag nbt = player.getPersistentData();
        if (nbt.getBoolean("jjkbrp_duration_extended")) {
            return;
        }
        MobEffectInstance inst = player.getEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        if (inst == null) {
            return;
        }
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            int expectedDuration = nbt.getInt("jjkbrp_expected_domain_duration");
            if (expectedDuration <= 0) {
                expectedDuration = DomainEffectStartDurationMixin.jjkbrp$resolveExpectedDuration(player, data, inst);
            }
            if (expectedDuration <= 0) {
                return;
            }
            int baseDuration = DomainEffectStartDurationMixin.jjkbrp$resolveBaseDurationTicks(player);
            if (expectedDuration == inst.getDuration()) {
                nbt.putBoolean("jjkbrp_duration_extended", true);
                nbt.putInt("jjkbrp_expected_domain_duration", expectedDuration);
                return;
            }
            if (DomainAddonUtils.setEffectDuration(inst, expectedDuration)) {
                nbt.putBoolean("jjkbrp_duration_extended", true);
                nbt.putInt("jjkbrp_expected_domain_duration", expectedDuration);
            }
        });
    }

    /**
     * Resolves expected duration from the currently available runtime data.
     * @param player entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param liveInstance live instance used by this method.
     * @return the resulting resolve expected duration value.
     */
    private static int jjkbrp$resolveExpectedDuration(Player player, DomainMasteryData data, MobEffectInstance liveInstance) {
        double openDurationMultiplier;
        boolean openEffect;
        int baseDuration = DomainEffectStartDurationMixin.jjkbrp$resolveBaseDurationTicks(player);
        int finalDuration = Math.max(1, (int)Math.round((double)data.resolveFinalDurationTicks(baseDuration) * AddonGameRules.percent(player, AddonGameRules.DOMAIN_DURATION_PERCENT, 100)));
        boolean bl = openEffect = liveInstance != null && liveInstance.getAmplifier() > 0 || player.getPersistentData().getBoolean("jjkbrp_open_form_active");
        if (openEffect && (openDurationMultiplier = player.getPersistentData().getDouble("jjkbrp_open_duration_multiplier")) > 0.0) {
            finalDuration = Math.max(1, (int)Math.round((double)finalDuration * openDurationMultiplier));
        }
        return Math.max(1, finalDuration);
    }

    /**
     * Resolves base duration ticks from the currently available runtime data.
     * @param player entity involved in the current mixin operation.
     * @return the resulting resolve base duration ticks value.
     */
    private static int jjkbrp$resolveBaseDurationTicks(Player player) {
        CompoundTag nbt = player.getPersistentData();
        double domainId = nbt.getDouble("jjkbrp_domain_id_runtime");
        if (domainId == 0.0) {
            domainId = nbt.getDouble("select");
        }
        if (domainId == 0.0) {
            domainId = nbt.getDouble("skill_domain");
        }
        return Math.round(domainId) == 29L ? 3600 : 1200;
    }
}
