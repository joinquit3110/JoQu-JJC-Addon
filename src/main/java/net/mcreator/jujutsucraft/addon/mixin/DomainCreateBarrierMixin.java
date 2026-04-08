package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.mcreator.jujutsucraft.addon.DomainFormPolicy;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
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
            double cnt3;
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
            if ((domainId = (nbt = player.getPersistentData()).getDouble("jjkbrp_domain_id_runtime")) <= 0.0) {
                domainId = nbt.getDouble("select");
            }
            if (domainId <= 0.0) {
                domainId = nbt.getDouble("skill_domain");
            }
            double policyDomainId = domainId > 0.0 ? domainId : 1.0;
            // Resolve the policy record once and store every runtime multiplier in NBT so later mixins can reuse the exact same tuned values.
            DomainFormPolicy.Policy policy = DomainFormPolicy.policyOf(policyDomainId);
            boolean useBaseOpenRangePath = domainId > 0.0 && DomainCreateBarrierMixin.jjkbrp$useBaseOpenRangePath(domainId);
            double openRangeMultiplier = Math.max(2.5, policy.openRangeMultiplier());
            if (!useBaseOpenRangePath) {
                openRangeMultiplier = 2.5;
            }
            // Persist every startup multiplier into runtime NBT because downstream mixins read these keys during active ticks, clash handling, and cleanup.
            nbt.putDouble("jjkbrp_incomplete_penalty_per_tick", policy.incompletePenaltyPerTick());
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
            // Incomplete form uses a special surface multiplier and negative `cnt2` marker to signal the unstable partial-domain path to later mixins.
            if (form == 0) {
                double incompleteSurfaceMultiplier;
                if (nbt.contains("jjkbrp_incomplete_surface_multiplier")) {
                    incompleteSurfaceMultiplier = Math.max(1.0, nbt.getDouble("jjkbrp_incomplete_surface_multiplier"));
                } else {
                    incompleteSurfaceMultiplier = DomainCreateBarrierMixin.jjkbrp$resolveIncompleteSurfaceMultiplier(world, player);
                    nbt.putDouble("jjkbrp_incomplete_surface_multiplier", incompleteSurfaceMultiplier);
                }
                radiusBonus *= incompleteSurfaceMultiplier;
            } else {
                nbt.remove("jjkbrp_incomplete_surface_multiplier");
            }
            try {
                JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
                double origRadius = mapVars.DomainExpansionRadius;
                // Snapshot the original shared radius before scaling so later startup, active-tick, and cleanup paths can restore it safely.
                nbt.putDouble("jjkbrp_base_domain_radius", origRadius);
                nbt.putDouble("jjkbrp_radius_multiplier", radiusBonus);
                if (Math.abs(radiusBonus - 1.0) > 1.0E-4) {
                    nbt.putDouble("jjkbrp_orig_domain_radius", origRadius);
                    mapVars.DomainExpansionRadius = Math.max(1.0, origRadius * radiusBonus);
                }
            }
            catch (Exception e) {
                nbt.putDouble("jjkbrp_radius_multiplier", 1.0);
                nbt.putDouble("jjkbrp_base_domain_radius", 16.0);
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
            boolean openAllowed = DomainMasteryData.isOpenFormUnlocked(masteryLevel, hasOpenAdvancement);
            // Open form is validated separately because it requires both mastery progression and the extra advancement gate.
            if (form == 2) {
                if (!openAllowed) {
                    nbt.putBoolean("jjkbrp_open_denied_no_adv", true);
                    nbt.putBoolean("jjkbrp_open_denied_incompatible", false);
                    nbt.putInt("jjkbrp_domain_form_effective", 1);
                    nbt.putDouble("cnt2", 0.0);
                    nbt.putBoolean("jjkbrp_open_form_active", false);
                    nbt.putBoolean("jjkbrp_open_base_range_path", false);
                    nbt.remove("jjkbrp_open_cast_game_time");
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
                    int openAmplifier = 1;
                    nbt.putBoolean("jjkbrp_open_base_range_path", true);
                    // Promote the domain effect amplifier so the active effect itself advertises open-form runtime state to later systems.
                    DomainCreateBarrierMixin.promoteDomainAmplifier(player, openAmplifier);
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
            nbt.putBoolean("jjkbrp_incomplete_form_active", form == 0);
            nbt.putBoolean("jjkbrp_incomplete_session_active", form == 0);
            nbt.putInt("jjkbrp_domain_form_cast_locked", form);
            // Incomplete domains smooth the startup charge window slightly so the partial cast stabilizes fast enough to enter its special runtime path.
            if (form == 0 && (cnt3 = nbt.getDouble("cnt3")) > 0.0 && cnt3 < 20.0) {
                nbt.putDouble("cnt3", Math.min(20.0, cnt3 + 1.5));
            }
            // Fire the charge callout near half charge so the caster still gets the domain-name announcement during the startup hold window.
            DomainCreateBarrierMixin.jjkbrp$announceHalfChargeCallout(world, player, domainId);
            nbt.putBoolean("jjkbrp_domain_just_opened", true);
            nbt.putInt("jjkbrp_domain_grace_ticks", 10);
        });
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
        int targetAmplifier = effectInstance.getAmplifier();
        int effectiveForm = nbt.getInt("jjkbrp_domain_form_effective");
        if (effectiveForm == 2) {
            targetAmplifier = 1;
        } else if (effectiveForm == 1 && targetAmplifier > 0) {
            targetAmplifier = 0;
        }
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
            boolean openEffect = adjustedEffect.getAmplifier() > 0 || nbt.getInt("jjkbrp_domain_form_effective") == 2;
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
        try {
            Field ampField = MobEffectInstance.class.getDeclaredField("duration");
            ampField.setAccessible(true);
            ampField.setInt(inst, targetAmplifier);
        }
        catch (NoSuchFieldException e1) {
            try {
                Field ampField = MobEffectInstance.class.getDeclaredField("amplifier");
                ampField.setAccessible(true);
                ampField.setInt(inst, targetAmplifier);
            }
            catch (Exception exception) {}
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /**
     * Checks whether this domain id should keep its normal open-range growth path instead of being clamped to the fallback multiplier.
     * @param rawDomainId identifier used to resolve runtime state for this operation.
     * @return whether use base open range path is true for the current runtime state.
     */

    // ===== FORM AND POLICY HELPERS =====
    private static boolean jjkbrp$useBaseOpenRangePath(double rawDomainId) {
        int domainId = (int)Math.round(rawDomainId);
        return domainId != 1 && domainId != 15;
    }

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


    // ===== RUNTIME CLEANUP =====
    /**
     * Restores restore radius after barrier after temporary mixin changes.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$restoreRadiusAfterBarrier(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (world.isClientSide()) {
            return;
        }
        CompoundTag nbt = player.getPersistentData();
        if (nbt.contains("jjkbrp_orig_domain_radius")) {
            try {
                JujutsucraftModVariables.MapVariables mapVars = JujutsucraftModVariables.MapVariables.get((LevelAccessor)world);
                mapVars.DomainExpansionRadius = nbt.getDouble("jjkbrp_orig_domain_radius");
            }
            catch (Exception exception) {
                // empty catch block
            }
            nbt.remove("jjkbrp_orig_domain_radius");
        }
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
     * Resolves incomplete surface multiplier from the currently available runtime data.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     * @return the resulting resolve incomplete surface multiplier value.
     */
    private static double jjkbrp$resolveIncompleteSurfaceMultiplier(LevelAccessor world, Player player) {
        if (player == null) {
            return 1.2;
        }
        BlockPos origin = player.blockPosition();
        int baseX = origin.getX();
        int baseY = origin.getY();
        int baseZ = origin.getZ();
        int sampleRadius = 8;
        int sampleStep = 2;
        int samples = 0;
        int supportCount = 0;
        int roofCount = 0;
        int surfaceSamples = 0;
        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;
        for (int dx = -sampleRadius; dx <= sampleRadius; dx += sampleStep) {
            for (int dz = -sampleRadius; dz <= sampleRadius; dz += sampleStep) {
                if (dx * dx + dz * dz > sampleRadius * sampleRadius) continue;
                ++samples;
                BlockPos belowPos = new BlockPos(baseX + dx, baseY - 1, baseZ + dz);
                if (!world.getBlockState(belowPos).isAir()) {
                    ++supportCount;
                }
                for (int dy = 2; dy <= 6; ++dy) {
                    BlockPos roofPos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (world.getBlockState(roofPos).isAir()) continue;
                    ++roofCount;
                    break;
                }
                int foundSurfaceY = Integer.MIN_VALUE;
                for (int dy = 4; dy >= -4; --dy) {
                    BlockPos surfacePos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (world.getBlockState(surfacePos).isAir()) continue;
                    foundSurfaceY = baseY + dy;
                    break;
                }
                if (foundSurfaceY == Integer.MIN_VALUE) continue;
                ++surfaceSamples;
                minSurfaceY = Math.min(minSurfaceY, foundSurfaceY);
                maxSurfaceY = Math.max(maxSurfaceY, foundSurfaceY);
            }
        }
        if (samples <= 0) {
            return 1.2;
        }
        double supportRatio = (double)supportCount / (double)samples;
        double roofRatio = (double)roofCount / (double)samples;
        double terrainRoughness = 0.0;
        if (surfaceSamples > 0 && maxSurfaceY >= minSurfaceY) {
            terrainRoughness = Math.min(1.0, (double)(maxSurfaceY - minSurfaceY) / 6.0);
        }
        double multiplier = 1.2 + supportRatio * 0.3 + roofRatio * 0.22 + terrainRoughness * 0.28;
        return Math.max(1.2, Math.min(2.1, multiplier));
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
        boolean bl = openEffect = effectAmplifier > 0 || player.getPersistentData().getBoolean("jjkbrp_open_form_active");
        if (openEffect && (openDurationMultiplier = player.getPersistentData().getDouble("jjkbrp_open_duration_multiplier")) > 0.0) {
            finalDuration = Math.max(1, (int)Math.round((double)finalDuration * openDurationMultiplier));
        }
        return Math.max(1, finalDuration);
    }
}
