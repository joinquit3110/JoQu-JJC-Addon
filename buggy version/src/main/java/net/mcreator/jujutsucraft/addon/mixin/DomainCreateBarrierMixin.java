package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.mcreator.jujutsucraft.addon.DomainFormPolicy;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Domain startup mixin for `DomainExpansionCreateBarrierProcedure.execute()` that sanitizes form selection, writes addon runtime NBT, applies mastery-driven duration and radius tuning, and announces half-charge domain callouts during startup.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionCreateBarrierProcedure.class}, remap=false)
public abstract class DomainCreateBarrierMixin {
    // Logger used for mastery-driven duration diagnostics during barrier startup.
    private static final Logger LOGGER = LogUtils.getLogger();


    // ===== BARRIER STARTUP INJECTION =====
    /**
     * Injects at barrier startup to sanitize domain form selection, write runtime form metadata, apply policy multipliers, and snapshot the real radius for later phases.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$applyDomainMasteryToBarrier(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (world.isClientSide()) {
            return;
        }
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            CompoundTag nbt;
            double domainId;
            int masteryLevel = data.getDomainMasteryLevel();
            boolean hasOpenAdvancement = DomainCreateBarrierMixin.hasOpenBarrierAdvancement(player);
            data.setOpenBarrierAdvancementUnlocked(hasOpenAdvancement);
            int originalForm = data.getDomainTypeSelected();
            // Sanitize the selected form at cast time so stale client selections cannot force forms the player has not actually unlocked.
            int form = DomainMasteryData.sanitizeFormSelection(originalForm, masteryLevel, hasOpenAdvancement);
            if (form != originalForm) {
                data.setDomainTypeSelected(form, hasOpenAdvancement);
                if (player instanceof ServerPlayer) {
                    ServerPlayer sp = (ServerPlayer)player;
                    data.syncToClient(sp);
                }
            }
            nbt = player.getPersistentData();
            double selectId = nbt.getDouble("select");
            double skillDomainId = nbt.getDouble("skill_domain");
            double runtimeId = nbt.getDouble("jjkbrp_domain_id_runtime");
            domainId = selectId > 0.0 ? selectId : (skillDomainId > 0.0 ? skillDomainId : runtimeId);
            if (runtimeId > 0.0 && selectId > 0.0 && Math.round(runtimeId) != Math.round(selectId)) {
                nbt.remove("jjkbrp_domain_id_runtime");
            }
            double policyDomainId = domainId > 0.0 ? domainId : 1.0;
            // Resolve the policy record once and store every runtime multiplier in NBT so later mixins can reuse the exact same tuned values.
            DomainFormPolicy.Policy policy = DomainFormPolicy.policyOf(policyDomainId);
            double openRangeMultiplier = Math.max(2.5, policy.openRangeMultiplier());
            DomainAddonUtils.copyDomainRadiusSnapshotToCleanupMarker(world, player);
            // Cast overwrite cleanup must preserve the previous radius snapshot until OG cleanup has observed the marker footprint.
            DomainAddonUtils.cleanupDomainRuntimeState(player);
            nbt.putDouble("jjkbrp_open_range_multiplier", openRangeMultiplier);
            nbt.putDouble("jjkbrp_open_surehit_multiplier", policy.openSureHitMultiplier());
            nbt.putDouble("jjkbrp_open_ce_drain_multiplier", policy.openCeDrainMultiplier());
            nbt.putDouble("jjkbrp_open_duration_multiplier", policy.openDurationMultiplier());
            nbt.putString("jjkbrp_domain_archetype", policy.archetype().name());
            if (domainId > 0.0) {
                nbt.putDouble("jjkbrp_domain_id_runtime", (double)Math.round(domainId));
            }
            nbt.putDouble("jjkbrp_barrier_refinement", policy.barrierRefinement());
            double radiusBonus = data.getRadiusRuntimeMultiplier();
            try {
                JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
                double baseRadius = Math.max(1.0, mapVars.DomainExpansionRadius);
                double actualRadius = DomainRadiusUtils.computeEffectiveRadius(baseRadius, radiusBonus);
                nbt.putDouble("jjkbrp_base_domain_radius", baseRadius);
                nbt.putDouble("jjkbrp_radius_multiplier", radiusBonus);
                nbt.putDouble("jjkbrp_actual_domain_radius", actualRadius);
            }
            catch (Exception e) {
                nbt.putDouble("jjkbrp_radius_multiplier", 1.0);
                nbt.putDouble("jjkbrp_base_domain_radius", 22.0);
                nbt.putDouble("jjkbrp_actual_domain_radius", 22.0);
            }
            nbt.putDouble("jjkbrp_caster_x_at_cast", player.getX());
            nbt.putDouble("jjkbrp_caster_y_at_cast", player.getY());
            nbt.putDouble("jjkbrp_caster_z_at_cast", player.getZ());
            nbt.remove("jjkbrp_expected_domain_duration");
            nbt.remove("jjkbrp_opening_vfx_fired");
            nbt.remove("jjkbrp_opening_prefire_fired");
            nbt.remove("jjkbrp_open_domain_cx");
            nbt.remove("jjkbrp_open_domain_cy");
            nbt.remove("jjkbrp_open_domain_cz");
            nbt.remove("jjkbrp_open_darkness_stage");
            nbt.remove("jjkbrp_open_darkness_start");
            nbt.remove("jjkbrp_open_cancelled");
            nbt.remove("jjkbrp_open_center_locked");
            nbt.remove("jjkbrp_open_cast_game_time");
            nbt.putBoolean("jjkbrp_duration_extended", false);
            boolean openAllowed = DomainMasteryData.isOpenFormUnlocked(masteryLevel, hasOpenAdvancement) && policy.openAllowed();
            // Open form is validated by mastery progression and the domain policy only; every registered domain policy now supports open form.
            if (form == 2) {
                if (!openAllowed) {
                    form = 1;
                    nbt.putBoolean("jjkbrp_open_denied_no_adv", true);
                    nbt.putBoolean("jjkbrp_open_denied_incompatible", false);
                    nbt.putInt("jjkbrp_domain_form_effective", 1);
                    nbt.putDouble("cnt2", 0.0);
                    nbt.putBoolean("jjkbrp_open_form_active", false);
                    nbt.putBoolean("jjkbrp_open_base_range_path", false);
                    nbt.remove("jjkbrp_open_cast_game_time");
                    DomainCreateBarrierMixin.promoteDomainAmplifier(player, 0);
                } else {
                    nbt.putDouble("cnt2", 1.0);
                    nbt.putBoolean("jjkbrp_open_form_active", true);
                    nbt.putBoolean("jjkbrp_open_denied_incompatible", false);
                    nbt.putBoolean("jjkbrp_open_denied_no_adv", false);
                    nbt.putInt("jjkbrp_domain_form_effective", 2);
                    nbt.putDouble("jjkbrp_open_domain_cx", player.getX());
                    nbt.putDouble("jjkbrp_open_domain_cy", player.getY());
                    nbt.putDouble("jjkbrp_open_domain_cz", player.getZ());
                    if (world instanceof ServerLevel) {
                        ServerLevel serverLevel = (ServerLevel)world;
                        nbt.putLong("jjkbrp_open_cast_game_time", serverLevel.getGameTime());
                    }
                    nbt.putBoolean("jjkbrp_open_base_range_path", true);
                    // Open form is represented uniformly by the OG amplifier; radius mixins normalize the shared radius so no form or domain id bypasses the addon modify-radius system.
                    DomainCreateBarrierMixin.promoteDomainAmplifier(player, 1);
                }
            } else {
                nbt.putDouble("cnt2", form == 0 ? -1.0 : 0.0);
                nbt.putBoolean("jjkbrp_open_form_active", false);
                nbt.putBoolean("jjkbrp_open_denied_incompatible", false);
                nbt.putBoolean("jjkbrp_open_denied_no_adv", false);
                nbt.putBoolean("jjkbrp_open_base_range_path", false);
                nbt.putInt("jjkbrp_domain_form_effective", form);
                nbt.remove("jjkbrp_open_cast_game_time");
                DomainCreateBarrierMixin.promoteDomainAmplifier(player, 0);
            }
            int effectiveForm = nbt.getInt("jjkbrp_domain_form_effective");
            nbt.putBoolean("jjkbrp_incomplete_form_active", effectiveForm == 0);
            nbt.remove("jjkbrp_incomplete_session_active");
            nbt.putInt("jjkbrp_domain_form_cast_locked", effectiveForm);
            if (effectiveForm == 0) {
                nbt.putDouble("cnt2", -1.0);
                nbt.putBoolean("DomainAttack", false);
            } else if (effectiveForm == 1) {
                nbt.putDouble("cnt2", 0.0);
            } else {
                nbt.putDouble("cnt2", 1.0);
            }
            // Fire the charge callout near half charge so the caster still gets the domain-name announcement during the startup hold window.
            DomainCreateBarrierMixin.jjkbrp$announceHalfChargeCallout(world, player, domainId);
            nbt.putBoolean("jjkbrp_domain_just_opened", true);
            nbt.putInt("jjkbrp_domain_grace_ticks", 10);
        });
    }

    @Redirect(method={"execute"}, at=@At(value="FIELD", target="Lnet/mcreator/jujutsucraft/network/JujutsucraftModVariables$MapVariables;DomainExpansionRadius:D", opcode=180), remap=false)
    private static double jjkbrp$readEffectiveCreateBarrierRadius(JujutsucraftModVariables.MapVariables mapVariables, LevelAccessor world, double x, double y, double z, Entity entity) {
        // Startup timing, entity repositioning, and the OG builder must all see the same per-cast radius.
        if (world == null || world.isClientSide() || entity == null) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        return DomainRadiusUtils.resolveActualRadius(world, entity.getPersistentData());
    }


    // ===== DURATION REDIRECT =====
    /**
     * Redirects the domain-effect application call so duration and amplifier are adjusted to the expected mastery-controlled form result before the effect lands.
     * @param livingEntity entity involved in the current mixin operation.
     * @param effectInstance effect instance processed by this helper.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @return whether extend domain expansion duration is true for the current runtime state.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z"), remap=false)
    private static boolean jjkbrp$extendDomainExpansionDuration(LivingEntity livingEntity, MobEffectInstance effectInstance, LevelAccessor world, double x, double y, double z, Entity entity) {
        Player player;
        block16: {
            block15: {
                if (!(entity instanceof Player)) break block15;
                player = (Player)entity;
                if (!world.isClientSide()) break block16;
            }
            return livingEntity.addEffect(effectInstance);
        }
        if (effectInstance == null || effectInstance.getEffect() != JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) {
            return livingEntity.addEffect(effectInstance);
        }
        int expectedDuration = effectInstance.getDuration();
        CompoundTag nbt = player.getPersistentData();
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            int baseDuration = effectInstance.getDuration();
            int bonusTicks = data.getDurationBonusTicks();
            int finalDuration = DomainCreateBarrierMixin.jjkbrp$resolveDomainDurationTicks(player, data, baseDuration, effectInstance.getAmplifier());
            nbt.putInt("jjkbrp_expected_domain_duration", finalDuration);
            LOGGER.info("[DomainMastery] Duration: player={}, base={}, bonus={}, final={}", new Object[]{player.getScoreboardName(), baseDuration, bonusTicks, finalDuration});
        });
        int effectiveForm = nbt.contains("jjkbrp_domain_form_cast_locked") ? nbt.getInt("jjkbrp_domain_form_cast_locked") : nbt.getInt("jjkbrp_domain_form_effective");
        int targetAmplifier = effectiveForm == 2 ? 1 : 0;
        int storedExpectedDuration = nbt.getInt("jjkbrp_expected_domain_duration");
        if (storedExpectedDuration > 0) {
            expectedDuration = storedExpectedDuration;
        }
        boolean durationChanged = expectedDuration != effectInstance.getDuration();
        boolean amplifierChanged = targetAmplifier != effectInstance.getAmplifier();
        // Rebuild the effect only when duration or amplifier actually changed so the redirect stays as conservative as possible.
        MobEffectInstance adjustedEffect = !durationChanged && !amplifierChanged ? effectInstance : new MobEffectInstance(effectInstance.getEffect(), expectedDuration, targetAmplifier, effectInstance.isAmbient(), effectInstance.isVisible());
        boolean applied = livingEntity.addEffect(adjustedEffect);
        if (applied) {
            nbt.putInt("jjkbrp_expected_domain_duration", adjustedEffect.getDuration());
            nbt.putBoolean("jjkbrp_duration_extended", adjustedEffect.getDuration() != effectInstance.getDuration());
            boolean openEffect = effectiveForm == 2;
            nbt.putBoolean("jjkbrp_open_form_active", openEffect);
            if (openEffect) {
                if (nbt.contains("x_pos_doma") && nbt.getDouble("cnt1") > 0.0) {
                    nbt.putDouble("jjkbrp_open_domain_cx", nbt.getDouble("x_pos_doma"));
                    nbt.putDouble("jjkbrp_open_domain_cy", nbt.getDouble("y_pos_doma"));
                    nbt.putDouble("jjkbrp_open_domain_cz", nbt.getDouble("z_pos_doma"));
                    nbt.putBoolean("jjkbrp_open_center_locked", true);
                } else if (!nbt.contains("jjkbrp_open_domain_cx")) {
                    nbt.putDouble("jjkbrp_open_domain_cx", player.getX());
                    nbt.putDouble("jjkbrp_open_domain_cy", player.getY());
                    nbt.putDouble("jjkbrp_open_domain_cz", player.getZ());
                }
                if (world instanceof ServerLevel) {
                    ServerLevel serverLevel = (ServerLevel)world;
                    if (!nbt.contains("jjkbrp_open_cast_game_time")) {
                        nbt.putLong("jjkbrp_open_cast_game_time", serverLevel.getGameTime());
                    }
                }
            }
        }
        return applied;
    }

    /**
     * Updates the active domain effect amplifier to match the selected domain form without rebuilding the entire effect instance.
     * @param player entity involved in the current mixin operation.
     * @param targetAmplifier target amplifier used by this method.
     */

    // ===== AMPLIFIER TUNING =====
    private static void promoteDomainAmplifier(Player player, int targetAmplifier) {
        MobEffect domainEff = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!player.hasEffect(domainEff)) {
            return;
        }
        MobEffectInstance inst = player.getEffect(domainEff);
        if (inst == null) {
            return;
        }
        if (inst.getAmplifier() == targetAmplifier) {
            return;
        }
        Field resolved = null;
        for (String candidate : new String[]{"amplifier", "f_19513_", "f_216888_"}) {
            try {
                resolved = MobEffectInstance.class.getDeclaredField(candidate);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (resolved == null) {
            return;
        }
        try {
            resolved.setAccessible(true);
            resolved.setInt(inst, targetAmplifier);
        } catch (Exception ignored) {}
    }

    // ===== FORM AND POLICY HELPERS =====
    /**
     * Announces the domain name once the caster reaches the half-charge window during startup.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     * @param rawDomainId identifier used to resolve runtime state for this operation.
     */
    private static void jjkbrp$announceHalfChargeCallout(LevelAccessor world, Player player, double rawDomainId) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        CompoundTag nbt = player.getPersistentData();
        double cnt3 = nbt.getDouble("cnt3");
        if (cnt3 < 10.0) {
            nbt.remove("jjkbrp_half_charge_announced");
            return;
        }
        if (cnt3 >= 20.0) {
            return;
        }
        if (nbt.getBoolean("jjkbrp_half_charge_announced")) {
            return;
        }
        int domainId = (int)Math.round(rawDomainId);
        String domainName = DomainCreateBarrierMixin.jjkbrp$resolveDomainName(domainId);
        Component msg = DomainCreateBarrierMixin.jjkbrp$buildHalfChargeMessage(player, domainName);
        if (serverLevel.getServer() != null) {
            for (ServerPlayer online : serverLevel.getServer().getPlayerList().getPlayers()) {
                online.displayClientMessage(msg, false);
            }
        } else {
            player.displayClientMessage(msg, false);
        }
        nbt.putBoolean("jjkbrp_half_charge_announced", true);
    }

    /**
     * Performs build half charge message for this mixin.
     * @param player entity involved in the current mixin operation.
     * @param domainName domain name used by this method.
     * @return the resulting build half charge message value.
     */
    private static Component jjkbrp$buildHalfChargeMessage(Player player, String domainName) {
        MutableComponent castText = domainName == null || domainName.isBlank() ? Component.literal((String)"Domain Expansion!").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD}) : Component.literal((String)"Domain Expansion: ").withStyle(ChatFormatting.LIGHT_PURPLE).append((Component)Component.literal((String)domainName).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).append((Component)Component.literal((String)"!").withStyle(ChatFormatting.LIGHT_PURPLE));
        return Component.literal((String)player.getName().getString()).withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}).append((Component)Component.literal((String)": ").withStyle(ChatFormatting.GRAY)).append((Component)castText);
    }

    /**
     * Resolves domain name from the currently available runtime data.
     * @param domainId identifier used to resolve runtime state for this operation.
     * @return the resulting resolve domain name value.
     */
    private static String jjkbrp$resolveDomainName(int domainId) {
        return switch (domainId) {
            case 1 -> "Malevolent Shrine";
            case 2 -> "Unlimited Void";
            case 4 -> "Coffin of the Iron Mountain";
            case 5 -> "Authentic Mutual Love";
            case 6 -> "Chimera Shadow Garden";
            case 7 -> "Kashimo Domain";
            case 8 -> "Horizon of the Captivating Skandha";
            case 9 -> "Tsukumo Domain";
            case 10 -> "Choso Domain";
            case 11 -> "Mei Mei Domain";
            case 13 -> "Nanami Domain";
            case 14 -> "Ceremonial Sea of Light";
            case 15 -> "Self-Embodiment of Perfection";
            case 18 -> "Womb Profusion";
            case 19 -> "Time Cell Moon Palace";
            case 21 -> "Itadori Domain";
            case 23 -> "Kurourushi Domain";
            case 24 -> "Uraume Domain";
            case 25 -> "Graveyard Domain";
            case 26 -> "Ogi Domain";
            case 27 -> "Deadly Sentencing";
            case 29 -> "Idle Death Gamble";
            case 35 -> "Junpei Domain";
            case 36 -> "Nishimiya Domain";
            case 40 -> "Takuma Ino Domain";
            default -> "";
        };
    }

    /**
     * Performs has open barrier advancement for this mixin.
     * @param player entity involved in the current mixin operation.
     * @return whether has open barrier advancement is true for the current runtime state.
     */

    // ===== ADVANCEMENT HELPERS =====
    private static boolean hasOpenBarrierAdvancement(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer sp = (ServerPlayer)player;
        if (sp.server == null) {
            return false;
        }
        try {
            Advancement adv = sp.server.getAdvancements().getAdvancement(new ResourceLocation("jujutsucraft", "mastery_open_barrier_type_domain"));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = sp.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves domain duration ticks from the currently available runtime data.
     * @param player entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param baseDuration base duration used by this method.
     * @param effectAmplifier effect amplifier used by this method.
     * @return the resulting resolve domain duration ticks value.
     */
    private static int jjkbrp$resolveDomainDurationTicks(Player player, DomainMasteryData data, int baseDuration, int effectAmplifier) {
        double openDurationMultiplier;
        boolean openEffect;
        int finalDuration = data.resolveFinalDurationTicks(baseDuration);
        CompoundTag nbt = player.getPersistentData();
        int effectiveForm = nbt.contains("jjkbrp_domain_form_cast_locked") ? nbt.getInt("jjkbrp_domain_form_cast_locked") : nbt.getInt("jjkbrp_domain_form_effective");
        boolean bl = openEffect = effectiveForm == 2;
        if (openEffect && (openDurationMultiplier = player.getPersistentData().getDouble("jjkbrp_open_duration_multiplier")) > 0.0) {
            finalDuration = Math.max(1, (int)Math.round((double)finalDuration * openDurationMultiplier));
        }
        return Math.max(1, finalDuration);
    }
}
