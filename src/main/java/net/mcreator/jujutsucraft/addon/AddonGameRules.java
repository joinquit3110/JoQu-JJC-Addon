package net.mcreator.jujutsucraft.addon;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Runtime gamerule registry and safe access helpers for addon behavior.
 *
 * <p>Mixins are loaded before a world exists, so gamerules cannot decide whether a mixin is
 * applied. They decide whether the injected behavior runs, and whether numeric addon constants are
 * scaled at runtime.</p>
 */
public final class AddonGameRules {
    public enum RuleKind {
        BOOLEAN,
        INTEGER
    }

    public record RuleSnapshot(String id, String fieldName, String tabId, String tabName, int tabOrder, String label, String description, RuleKind kind, String value, String defaultValue) {
    }

    private record RuleHandle(String id, String fieldName, String tabId, String tabName, int tabOrder, String label, String description, GameRules.Key<?> key) {
    }

    private static volatile List<RuleHandle> cachedRuleHandles;
    private static volatile Map<String, RuleHandle> cachedRuleHandleById;
    private static volatile Map<String, String> clientSyncedValues = Collections.emptyMap();

    private AddonGameRules() {
    }

    public static final GameRules.Key<GameRules.BooleanValue> ADDON_ENABLED = bool("jjkbrpAddonEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> GOJO_ENABLED = bool("jjkbrpGojoEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> GOJO_RED_ENABLED = bool("jjkbrpGojoRedEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> GOJO_BLUE_ENABLED = bool("jjkbrpGojoBlueEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> GOJO_PURPLE_FUSION_ENABLED = bool("jjkbrpGojoPurpleFusionEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> INFINITY_CRUSHER_ENABLED = bool("jjkbrpInfinityCrusherEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> GOJO_TELEPORT_ENABLED = bool("jjkbrpGojoTeleportEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> GOJO_RANK_DAMAGE_SCALING_ENABLED = bool("jjkbrpGojoRankDamageScalingEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_ENABLED = bool("jjkbrpBlackFlashEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_TIMING_ENABLED = bool("jjkbrpBlackFlashTimingEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_FLOW_ENABLED = bool("jjkbrpBlackFlashFlowEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_MASTERY_ENABLED = bool("jjkbrpBlackFlashMasteryEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_BLESSING_ENABLED = bool("jjkbrpBlackFlashBlessingEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_MASTERY_ENABLED = bool("jjkbrpDomainMasteryEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_FORMS_ENABLED = bool("jjkbrpDomainFormsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_COST_RULES_ENABLED = bool("jjkbrpDomainCostRulesEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_RADIUS_RULES_ENABLED = bool("jjkbrpDomainRadiusRulesEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_DURATION_RULES_ENABLED = bool("jjkbrpDomainDurationRulesEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_PROPERTY_EFFECTS_ENABLED = bool("jjkbrpDomainPropertyEffectsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_BARRIER_FIXES_ENABLED = bool("jjkbrpDomainBarrierFixesEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_OPEN_VFX_ENABLED = bool("jjkbrpDomainOpenVfxEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_CLASH_ENABLED = bool("jjkbrpDomainClashEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_CLASH_XP_ENABLED = bool("jjkbrpDomainClashXpEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> DOMAIN_CLASH_HUD_ENABLED = bool("jjkbrpDomainClashHudEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> SUKUNA_INCOMPLETE_SUREHIT_ENABLED = bool("jjkbrpSukunaIncompleteSurehitEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SUKUNA_FUGA_REWARD_ENABLED = bool("jjkbrpSukunaFugaRewardEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SUKUNA_FUGA_COOLDOWN_BYPASS_ENABLED = bool("jjkbrpSukunaFugaCooldownBypassEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SUKUNA_STALE_STATE_FIX_ENABLED = bool("jjkbrpSukunaStaleStateFixEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> LIMB_SYSTEM_ENABLED = bool("jjkbrpLimbSystemEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> LIMB_SEVERING_ENABLED = bool("jjkbrpLimbSeveringEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> LIMB_DEBUFFS_ENABLED = bool("jjkbrpLimbDebuffsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> LIMB_REGENERATION_ENABLED = bool("jjkbrpLimbRegenerationEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> LIMB_DROPS_ENABLED = bool("jjkbrpLimbDropsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> NEAR_DEATH_ENABLED = bool("jjkbrpNearDeathEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> RCT_LEVEL3_ENABLED = bool("jjkbrpRctLevel3Enabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> YUTA_COPY_SYSTEM_ENABLED = bool("jjkbrpYutaCopySystemEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> YUTA_LIMB_COPY_ENABLED = bool("jjkbrpYutaLimbCopyEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> YUTA_MOB_COPY_ENABLED = bool("jjkbrpYutaMobCopyEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> YUTA_FAKE_PLAYER_ENABLED = bool("jjkbrpYutaFakePlayerEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> YUTA_DOMAIN_SWORD_ENABLED = bool("jjkbrpYutaDomainSwordEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> RIKA_OWNER_GUARD_ENABLED = bool("jjkbrpRikaOwnerGuardEnabled", true);

    public static final GameRules.Key<GameRules.BooleanValue> HUD_OVERLAYS_ENABLED = bool("jjkbrpHudOverlaysEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SKILL_WHEEL_ENABLED = bool("jjkbrpSkillWheelEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SKILL_WHEEL_SOUNDS_ENABLED = bool("jjkbrpSkillWheelSoundsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> SKILL_WHEEL_VISUAL_EFFECTS_ENABLED = bool("jjkbrpSkillWheelVisualEffectsEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> COOLDOWN_HUD_ENABLED = bool("jjkbrpCooldownHudEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> BLACK_FLASH_HUD_ENABLED = bool("jjkbrpBlackFlashHudEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> NEAR_DEATH_HUD_ENABLED = bool("jjkbrpNearDeathHudEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> LIMB_RENDERING_ENABLED = bool("jjkbrpLimbRenderingEnabled", true);

    public static final GameRules.Key<GameRules.IntegerValue> SKILL_WHEEL_SOUND_VOLUME_PERCENT = integer("jjkbrpSkillWheelSoundVolumePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> SKILL_WHEEL_PARTICLE_COUNT_PERCENT = integer("jjkbrpSkillWheelParticleCountPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> SKILL_WHEEL_ANIMATION_SPEED_PERCENT = integer("jjkbrpSkillWheelAnimationSpeedPercent", 100);

    public static final GameRules.Key<GameRules.IntegerValue> GOJO_RED_DAMAGE_PERCENT = integer("jjkbrpGojoRedDamagePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_RED_RADIUS_PERCENT = integer("jjkbrpGojoRedRadiusPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_RED_KNOCKBACK_PERCENT = integer("jjkbrpGojoRedKnockbackPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_RED_SPEED_PERCENT = integer("jjkbrpGojoRedSpeedPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_RED_RANGE_PERCENT = integer("jjkbrpGojoRedRangePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_DAMAGE_PERCENT = integer("jjkbrpGojoBlueDamagePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_PULL_RADIUS_PERCENT = integer("jjkbrpGojoBluePullRadiusPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_PULL_STRENGTH_PERCENT = integer("jjkbrpGojoBluePullStrengthPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_AIM_DURATION_TICKS = integer("jjkbrpGojoBlueAimDurationTicks", 90);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_LINGER_DURATION_TICKS = integer("jjkbrpGojoBlueLingerDurationTicks", 200);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_BLUE_BLOCK_BREAK_INTERVAL_TICKS = integer("jjkbrpGojoBlueBlockBreakIntervalTicks", 8);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_PURPLE_CE_THRESHOLD = integer("jjkbrpGojoPurpleCeThreshold", 2000);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_PURPLE_CE_COST = integer("jjkbrpGojoPurpleCeCost", 2000);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_PURPLE_HP_THRESHOLD_PERCENT = integer("jjkbrpGojoPurpleHpThresholdPercent", 30);
    public static final GameRules.Key<GameRules.IntegerValue> INFINITY_CRUSHER_RADIUS_PERCENT = integer("jjkbrpInfinityCrusherRadiusPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> INFINITY_CRUSHER_DAMAGE_PERCENT = integer("jjkbrpInfinityCrusherDamagePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> INFINITY_CRUSHER_CE_DRAIN_PERCENT = integer("jjkbrpInfinityCrusherCeDrainPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> INFINITY_CRUSHER_LOCK_THRESHOLD_TICKS = integer("jjkbrpInfinityCrusherLockThresholdTicks", 15);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_TELEPORT_CE_COST = integer("jjkbrpGojoTeleportCeCost", 60);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_TELEPORT_COOLDOWN_TICKS = integer("jjkbrpGojoTeleportCooldownTicks", 60);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_TELEPORT_RANGE_BLOCKS = integer("jjkbrpGojoTeleportRangeBlocks", 18);
    public static final GameRules.Key<GameRules.IntegerValue> GOJO_TELEPORT_DELAY_TICKS = integer("jjkbrpGojoTeleportDelayTicks", 5);

    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_TIMING_RED_SIZE_BASIS = integer("jjkbrpBlackFlashTimingRedSizeBasis", 450);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_YUJI_RED_SIZE_BASIS = integer("jjkbrpBlackFlashYujiRedSizeBasis", 750);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_TIMING_PERIOD_TICKS = integer("jjkbrpBlackFlashTimingPeriodTicks", 18);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_YUJI_PERIOD_TICKS = integer("jjkbrpBlackFlashYujiPeriodTicks", 13);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_GUARANTEE_TIMEOUT_TICKS = integer("jjkbrpBlackFlashGuaranteeTimeoutTicks", 60);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_FLOW_MAX = integer("jjkbrpBlackFlashFlowMax", 8);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_FLOW_COOLDOWN_TICKS = integer("jjkbrpBlackFlashFlowCooldownTicks", 1200);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_PROC_CHANCE_PERCENT = integer("jjkbrpBlackFlashProcChancePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> BLACK_FLASH_MASTERY_THRESHOLD_PERCENT = integer("jjkbrpBlackFlashMasteryThresholdPercent", 100);

    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_INCOMPLETE_COST_PERCENT = integer("jjkbrpDomainIncompleteCostPercent", 55);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_CLOSED_COST_PERCENT = integer("jjkbrpDomainClosedCostPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_OPEN_COST_PERCENT = integer("jjkbrpDomainOpenCostPercent", 160);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_RADIUS_PERCENT = integer("jjkbrpDomainRadiusPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_DURATION_PERCENT = integer("jjkbrpDomainDurationPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_OPEN_RANGE_PERCENT = integer("jjkbrpDomainOpenRangePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_OPEN_SUREHIT_PERCENT = integer("jjkbrpDomainOpenSurehitPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_PROPERTY_EFFECT_PERCENT = integer("jjkbrpDomainPropertyEffectPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_INCOMPLETE_COOLDOWN_CAP_TICKS = integer("jjkbrpDomainIncompleteCooldownCapTicks", 200);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_RANGE_CANCEL_PERCENT = integer("jjkbrpDomainRangeCancelPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_CLASH_DURATION_TICKS = integer("jjkbrpDomainClashDurationTicks", 0);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_CLASH_SAMPLE_INTERVAL_TICKS = integer("jjkbrpDomainClashSampleIntervalTicks", 10);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_CLASH_TIE_THRESHOLD_PERCENT = integer("jjkbrpDomainClashTieThresholdPercent", 400);
    public static final GameRules.Key<GameRules.IntegerValue> DOMAIN_CLASH_XP_PERCENT = integer("jjkbrpDomainClashXpPercent", 100);

    public static final GameRules.Key<GameRules.IntegerValue> SUKUNA_FUGA_DUST_REWARD_PERCENT = integer("jjkbrpSukunaFugaDustRewardPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> SUKUNA_INCOMPLETE_SUREHIT_RANGE_PERCENT = integer("jjkbrpSukunaIncompleteSurehitRangePercent", 100);

    public static final GameRules.Key<GameRules.IntegerValue> LIMB_SEVER_CHANCE_PERCENT = integer("jjkbrpLimbSeverChancePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_SEVER_COOLDOWN_TICKS = integer("jjkbrpLimbSeverCooldownTicks", 40);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_BLOOD_DRIP_TICKS = integer("jjkbrpLimbBloodDripTicks", 200);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_REGEN_RATE_PERCENT = integer("jjkbrpLimbRegenRatePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_ZONE_REGEN_PERCENT = integer("jjkbrpLimbZoneRegenPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_ARM_DAMAGE_PENALTY_PERCENT = integer("jjkbrpLimbArmDamagePenaltyPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> LIMB_LEG_SLOW_PENALTY_PERCENT = integer("jjkbrpLimbLegSlowPenaltyPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> NEAR_DEATH_WINDOW_TICKS = integer("jjkbrpNearDeathWindowTicks", 20);
    public static final GameRules.Key<GameRules.IntegerValue> NEAR_DEATH_COOLDOWN_TICKS = integer("jjkbrpNearDeathCooldownTicks", 18000);
    public static final GameRules.Key<GameRules.IntegerValue> NEAR_DEATH_HEAL_THRESHOLD_HALF_HEARTS = integer("jjkbrpNearDeathHealThresholdHalfHearts", 8);
    public static final GameRules.Key<GameRules.IntegerValue> RCT_LEVEL3_CLOSE_CALLS_REQUIRED = integer("jjkbrpRctLevel3CloseCallsRequired", 20);
    public static final GameRules.Key<GameRules.IntegerValue> RCT_LEVEL3_CLOSE_CALL_COOLDOWN_TICKS = integer("jjkbrpRctLevel3CloseCallCooldownTicks", 200);

    public static final GameRules.Key<GameRules.IntegerValue> YUTA_MOB_COPY_USES = integer("jjkbrpYutaMobCopyUses", 5);
    public static final GameRules.Key<GameRules.IntegerValue> YUTA_COPY_COOLDOWN_PERCENT = integer("jjkbrpYutaCopyCooldownPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> YUTA_COPY_COST_PERCENT = integer("jjkbrpYutaCopyCostPercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> YUTA_HAND_DROP_CHANCE_PERCENT = integer("jjkbrpYutaHandDropChancePercent", 100);
    public static final GameRules.Key<GameRules.IntegerValue> YUTA_MOB_COPY_DAMAGE_SHARE_PERCENT = integer("jjkbrpYutaMobCopyDamageSharePercent", 20);
    public static final GameRules.Key<GameRules.IntegerValue> YUTA_COPY_SUCCESS_CHANCE_PERCENT = integer("jjkbrpYutaCopySuccessChancePercent", 100);

    public static void init() {
        // Forces static rule registration during mod construction.
    }

    private static GameRules.Key<GameRules.BooleanValue> bool(String name, boolean defaultValue) {
        return GameRules.register(name, GameRules.Category.PLAYER, GameRules.BooleanValue.create(defaultValue));
    }

    private static GameRules.Key<GameRules.IntegerValue> integer(String name, int defaultValue) {
        return GameRules.register(name, GameRules.Category.PLAYER, GameRules.IntegerValue.create(defaultValue));
    }

    public static boolean addonEnabled(LevelAccessor world) {
        return rawBoolean(world, ADDON_ENABLED, true);
    }

    public static boolean addonEnabled(Entity entity) {
        return entity == null || addonEnabled(entity.level());
    }

    public static boolean enabled(LevelAccessor world, GameRules.Key<GameRules.BooleanValue> rule) {
        return rawBoolean(world, ADDON_ENABLED, true) && rawBoolean(world, rule, true);
    }

    public static boolean enabled(Entity entity, GameRules.Key<GameRules.BooleanValue> rule) {
        return entity == null || enabled(entity.level(), rule);
    }

    @SafeVarargs
    public static boolean enabled(LevelAccessor world, GameRules.Key<GameRules.BooleanValue> first, GameRules.Key<GameRules.BooleanValue>... rest) {
        if (!enabled(world, first)) {
            return false;
        }
        for (GameRules.Key<GameRules.BooleanValue> rule : rest) {
            if (!rawBoolean(world, rule, true)) {
                return false;
            }
        }
        return true;
    }

    @SafeVarargs
    public static boolean enabled(Entity entity, GameRules.Key<GameRules.BooleanValue> first, GameRules.Key<GameRules.BooleanValue>... rest) {
        return entity == null || enabled(entity.level(), first, rest);
    }

    public static boolean gojoRed(Entity entity) {
        return enabled(entity, GOJO_ENABLED, GOJO_RED_ENABLED);
    }

    public static boolean gojoBlue(Entity entity) {
        return enabled(entity, GOJO_ENABLED, GOJO_BLUE_ENABLED);
    }

    public static boolean gojoPurpleFusion(Entity entity) {
        return enabled(entity, GOJO_ENABLED, GOJO_PURPLE_FUSION_ENABLED);
    }

    public static boolean infinityCrusher(Entity entity) {
        return enabled(entity, GOJO_ENABLED, INFINITY_CRUSHER_ENABLED);
    }

    public static boolean gojoTeleport(Entity entity) {
        return enabled(entity, GOJO_ENABLED, GOJO_TELEPORT_ENABLED);
    }

    public static boolean blackFlash(Entity entity) {
        return enabled(entity, BLACK_FLASH_ENABLED);
    }

    public static boolean blackFlashTiming(Entity entity) {
        return enabled(entity, BLACK_FLASH_ENABLED, BLACK_FLASH_TIMING_ENABLED);
    }

    public static boolean blackFlashFlow(Entity entity) {
        return enabled(entity, BLACK_FLASH_ENABLED, BLACK_FLASH_FLOW_ENABLED);
    }

    public static boolean domainMastery(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED);
    }

    public static boolean domainForms(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED, DOMAIN_FORMS_ENABLED);
    }

    public static boolean domainCostRules(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED, DOMAIN_COST_RULES_ENABLED);
    }

    public static boolean domainRadiusRules(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_RADIUS_RULES_ENABLED);
    }

    public static boolean domainDurationRules(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED, DOMAIN_DURATION_RULES_ENABLED);
    }

    public static boolean domainPropertyEffects(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED, DOMAIN_PROPERTY_EFFECTS_ENABLED);
    }

    public static boolean domainBarrierFixes(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_BARRIER_FIXES_ENABLED);
    }

    public static boolean domainClash(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_CLASH_ENABLED);
    }

    public static boolean domainClashXp(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_CLASH_ENABLED, DOMAIN_CLASH_XP_ENABLED);
    }

    public static boolean domainClashHud(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_CLASH_ENABLED, HUD_OVERLAYS_ENABLED, DOMAIN_CLASH_HUD_ENABLED);
    }

    public static boolean domainOpenVfx(LevelAccessor world) {
        return enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_OPEN_VFX_ENABLED);
    }

    public static boolean sukunaIncompleteSurehit(Entity entity) {
        return enabled(entity, DOMAIN_MASTERY_ENABLED, SUKUNA_INCOMPLETE_SUREHIT_ENABLED);
    }

    public static boolean sukunaFugaReward(Entity entity) {
        return enabled(entity, SUKUNA_FUGA_REWARD_ENABLED);
    }

    public static boolean sukunaFugaCooldownBypass(Entity entity) {
        return enabled(entity, SUKUNA_FUGA_REWARD_ENABLED, SUKUNA_FUGA_COOLDOWN_BYPASS_ENABLED);
    }

    public static boolean limbSystem(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED);
    }

    public static boolean limbSevering(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, LIMB_SEVERING_ENABLED);
    }

    public static boolean limbDebuffs(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, LIMB_DEBUFFS_ENABLED);
    }

    public static boolean limbRegeneration(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, LIMB_REGENERATION_ENABLED);
    }

    public static boolean limbDrops(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, LIMB_DROPS_ENABLED);
    }

    public static boolean nearDeath(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, NEAR_DEATH_ENABLED);
    }

    public static boolean rctLevel3(Entity entity) {
        return enabled(entity, LIMB_SYSTEM_ENABLED, RCT_LEVEL3_ENABLED);
    }

    public static boolean yutaCopy(Entity entity) {
        return enabled(entity, YUTA_COPY_SYSTEM_ENABLED);
    }

    public static boolean yutaLimbCopy(Entity entity) {
        return enabled(entity, YUTA_COPY_SYSTEM_ENABLED, YUTA_LIMB_COPY_ENABLED);
    }

    public static boolean yutaMobCopy(Entity entity) {
        return enabled(entity, YUTA_COPY_SYSTEM_ENABLED, YUTA_MOB_COPY_ENABLED);
    }

    public static boolean yutaDomainSword(Entity entity) {
        return enabled(entity, YUTA_COPY_SYSTEM_ENABLED, YUTA_DOMAIN_SWORD_ENABLED);
    }

    public static boolean yutaFakePlayer(LevelAccessor world) {
        return enabled(world, YUTA_COPY_SYSTEM_ENABLED, YUTA_FAKE_PLAYER_ENABLED);
    }

    public static boolean rikaOwnerGuard(LevelAccessor world) {
        return enabled(world, RIKA_OWNER_GUARD_ENABLED);
    }

    public static boolean blackFlashHud(LevelAccessor world) {
        return enabled(world, BLACK_FLASH_ENABLED, HUD_OVERLAYS_ENABLED, BLACK_FLASH_HUD_ENABLED);
    }

    public static boolean nearDeathHud(LevelAccessor world) {
        return enabled(world, LIMB_SYSTEM_ENABLED, NEAR_DEATH_ENABLED, HUD_OVERLAYS_ENABLED, NEAR_DEATH_HUD_ENABLED);
    }

    public static boolean limbRendering(LevelAccessor world) {
        return enabled(world, LIMB_SYSTEM_ENABLED, LIMB_RENDERING_ENABLED);
    }

    public static boolean skillWheelSounds(LevelAccessor world) {
        return enabled(world, SKILL_WHEEL_ENABLED, SKILL_WHEEL_SOUNDS_ENABLED);
    }

    public static boolean skillWheelVisualEffects(LevelAccessor world) {
        return enabled(world, SKILL_WHEEL_ENABLED, SKILL_WHEEL_VISUAL_EFFECTS_ENABLED);
    }

    public static List<RuleSnapshot> snapshots(LevelAccessor world) {
        if (!(world instanceof Level level)) {
            return Collections.emptyList();
        }
        GameRules current = level.getGameRules();
        GameRules defaults = new GameRules();
        List<RuleSnapshot> out = new ArrayList<>();
        for (RuleHandle handle : ruleHandles()) {
            GameRules.Value<?> value = current.getRule(rawKey(handle.key()));
            GameRules.Value<?> defaultValue = defaults.getRule(rawKey(handle.key()));
            RuleKind kind = value instanceof GameRules.BooleanValue ? RuleKind.BOOLEAN : RuleKind.INTEGER;
            out.add(new RuleSnapshot(handle.id(), handle.fieldName(), handle.tabId(), handle.tabName(), handle.tabOrder(), handle.label(), handle.description(), kind, value.serialize(), defaultValue.serialize()));
        }
        return out;
    }

    public static boolean setRule(MinecraftServer server, LevelAccessor world, String id, String serializedValue) {
        if (server == null || !(world instanceof Level level) || id == null || serializedValue == null) {
            return false;
        }
        RuleHandle handle = ruleHandleById().get(id);
        if (handle == null) {
            return false;
        }
        GameRules.Value<?> value = level.getGameRules().getRule(rawKey(handle.key()));
        String trimmed = serializedValue.trim();
        try {
            if (value instanceof GameRules.BooleanValue booleanValue) {
                if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                    return false;
                }
                booleanValue.set(Boolean.parseBoolean(trimmed), server);
                return true;
            }
            if (value instanceof GameRules.IntegerValue integerValue) {
                integerValue.set(Integer.parseInt(trimmed), server);
                return true;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
    }

    private static List<RuleHandle> ruleHandles() {
        List<RuleHandle> cached = cachedRuleHandles;
        if (cached != null) {
            return cached;
        }
        List<RuleHandle> built = new ArrayList<>();
        for (Field field : AddonGameRules.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !GameRules.Key.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                GameRules.Key<?> key = (GameRules.Key<?>)field.get(null);
                if (key == null || key.getId() == null || !key.getId().startsWith("jjkbrp")) {
                    continue;
                }
                TabInfo tab = tabFor(key.getId(), field.getName());
                String label = humanizeRuleName(key.getId());
                built.add(new RuleHandle(key.getId(), field.getName(), tab.id(), tab.name(), tab.order(), label, descriptionFor(label, key.getId()), key));
            } catch (IllegalAccessException ignored) {
            }
        }
        built.sort((a, b) -> {
            int tabCmp = Integer.compare(a.tabOrder(), b.tabOrder());
            return tabCmp != 0 ? tabCmp : a.label().compareToIgnoreCase(b.label());
        });
        Map<String, RuleHandle> byId = new LinkedHashMap<>();
        for (RuleHandle handle : built) {
            byId.put(handle.id(), handle);
        }
        cachedRuleHandleById = Collections.unmodifiableMap(byId);
        cachedRuleHandles = Collections.unmodifiableList(built);
        return cachedRuleHandles;
    }

    private static Map<String, RuleHandle> ruleHandleById() {
        Map<String, RuleHandle> cached = cachedRuleHandleById;
        if (cached == null) {
            ruleHandles();
            cached = cachedRuleHandleById;
        }
        return cached == null ? Collections.emptyMap() : cached;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static GameRules.Key rawKey(GameRules.Key<?> key) {
        return (GameRules.Key)key;
    }

    private record TabInfo(String id, String name, int order) {
    }

    private static TabInfo tabFor(String id, String fieldName) {
        String lower = (id + " " + fieldName).toLowerCase(Locale.ROOT);
        if (lower.contains("addon")) return new TabInfo("core", "Core", 0);
        if (lower.contains("hud") || lower.contains("skillwheel") || lower.contains("cooldownhud") || lower.contains("rendering")) return new TabInfo("interface", "Interface", 1);
        if (lower.contains("gojo") || lower.contains("infinity")) return new TabInfo("gojo", "Gojo", 2);
        if (lower.contains("blackflash")) return new TabInfo("black_flash", "Black Flash", 3);
        if (lower.contains("domainclash")) return new TabInfo("domain_clash", "Domain Clash", 4);
        if (lower.contains("domain")) return new TabInfo("domain", "Domain", 5);
        if (lower.contains("sukuna") || lower.contains("fuga")) return new TabInfo("sukuna", "Sukuna / Fuga", 6);
        if (lower.contains("limb")) return new TabInfo("limb", "Limb", 7);
        if (lower.contains("neardeath") || lower.contains("rct")) return new TabInfo("rct", "Near Death / RCT", 8);
        if (lower.contains("yuta") || lower.contains("rika")) return new TabInfo("yuta", "Yuta / Rika", 9);
        return new TabInfo("misc", "Misc", 99);
    }

    private static String humanizeRuleName(String id) {
        String name = id == null ? "Rule" : id.replaceFirst("^jjkbrp", "");
        String spaced = name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ").trim();
        if (spaced.isEmpty()) {
            return "Rule";
        }
        StringBuilder out = new StringBuilder();
        for (String part : spaced.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            String word = switch (lower) {
                case "ce" -> "CE";
                case "xp" -> "XP";
                case "hud" -> "HUD";
                case "vfx" -> "VFX";
                case "rct" -> "RCT";
                case "ai" -> "AI";
                case "hp" -> "HP";
                default -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1);
            };
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word);
        }
        return out.toString();
    }

    private static String descriptionFor(String label, String id) {
        if (id == null) {
            return "Runtime tuning rule for " + label + ".";
        }
        return switch (id) {
            case "jjkbrpAddonEnabled" -> "Master switch for JoQu's JJC Addon runtime changes. False leaves base mod behavior wherever the addon can step aside.";
            case "jjkbrpGojoEnabled" -> "Enables every addon-side Gojo adjustment, including Red, Blue, Purple, Infinity Crusher, teleport, and scaling hooks.";
            case "jjkbrpGojoRedEnabled" -> "Allows addon tuning for Gojo Red projectile damage, radius, knockback, speed, and travel range.";
            case "jjkbrpGojoBlueEnabled" -> "Allows addon tuning for Gojo Blue damage, pull behavior, aiming time, lingering time, and block breaking.";
            case "jjkbrpGojoPurpleFusionEnabled" -> "Enables the addon's Hollow Purple fusion behavior and its CE, HP, and damage checks.";
            case "jjkbrpInfinityCrusherEnabled" -> "Enables the Infinity Crusher ability adjustments, including radius, damage, CE drain, and lock timing.";
            case "jjkbrpGojoTeleportEnabled" -> "Enables the addon teleport rule set for Gojo, including cost, cooldown, range, and startup delay.";
            case "jjkbrpGojoRankDamageScalingEnabled" -> "Allows Gojo ranged attack damage to scale through the addon's rank-aware damage rules.";
            case "jjkbrpBlackFlashEnabled" -> "Master switch for addon Black Flash behavior, timing windows, flow state, mastery, blessing, and related HUD.";
            case "jjkbrpBlackFlashTimingEnabled" -> "Enables the custom Black Flash timing window system for normal and Yuji timing checks.";
            case "jjkbrpBlackFlashFlowEnabled" -> "Enables the flow streak system that tracks repeated Black Flash success and its cooldown.";
            case "jjkbrpBlackFlashMasteryEnabled" -> "Enables mastery progression checks tied to Black Flash performance thresholds.";
            case "jjkbrpBlackFlashBlessingEnabled" -> "Enables the post-Black Flash blessing effects and related player rewards.";
            case "jjkbrpDomainMasteryEnabled" -> "Master switch for addon domain mastery, forms, costs, radius, duration, clash, and barrier behavior.";
            case "jjkbrpDomainFormsEnabled" -> "Allows incomplete, closed, and open domain form handling to use the addon's form rules.";
            case "jjkbrpDomainCostRulesEnabled" -> "Enables CE cost multipliers for incomplete, closed, and open domain forms.";
            case "jjkbrpDomainRadiusRulesEnabled" -> "Enables addon scaling for domain radius and open-domain interaction range.";
            case "jjkbrpDomainDurationRulesEnabled" -> "Enables addon scaling for domain lifetime, incomplete-domain caps, and duration related behavior.";
            case "jjkbrpDomainPropertyEffectsEnabled" -> "Enables extra domain property effect scaling, including sure-hit and environment effect strength.";
            case "jjkbrpDomainBarrierFixesEnabled" -> "Enables addon guard logic for domain barrier creation, restore, cleanup, and expiration edge cases.";
            case "jjkbrpDomainOpenVfxEnabled" -> "Enables visual changes for open-domain casting and domain startup effects.";
            case "jjkbrpDomainClashEnabled" -> "Enables the addon domain clash subsystem when two or more domains contest control.";
            case "jjkbrpDomainClashXpEnabled" -> "Allows mastery XP rewards to be granted from domain clash participation and resolution.";
            case "jjkbrpDomainClashHudEnabled" -> "Shows the domain clash HUD overlay with contest state, score, and timing information.";
            case "jjkbrpSukunaIncompleteSurehitEnabled" -> "Enables Sukuna incomplete-domain sure-hit adjustments and range scaling.";
            case "jjkbrpSukunaFugaRewardEnabled" -> "Enables the Sukuna Fuga reward logic, including dust reward scaling.";
            case "jjkbrpSukunaFugaCooldownBypassEnabled" -> "Allows the addon's Fuga reward path to bypass or relax the original cooldown gate.";
            case "jjkbrpSukunaStaleStateFixEnabled" -> "Enables cleanup for stale Sukuna/Fuga player state so old flags do not leak into later casts.";
            case "jjkbrpLimbSystemEnabled" -> "Master switch for limb loss, severing, debuffs, regeneration, drops, near-death, and limb rendering.";
            case "jjkbrpLimbSeveringEnabled" -> "Allows attacks to sever tracked limbs using the addon's chance and cooldown rules.";
            case "jjkbrpLimbDebuffsEnabled" -> "Applies gameplay penalties from missing arms or legs, including damage and movement modifiers.";
            case "jjkbrpLimbRegenerationEnabled" -> "Allows RCT and recovery logic to regenerate missing limbs using the addon regen rates.";
            case "jjkbrpLimbDropsEnabled" -> "Allows severed limb item/entity drops and related limb drop behavior.";
            case "jjkbrpNearDeathEnabled" -> "Enables near-death close-call detection, cooldown, heal threshold, and related RCT progression.";
            case "jjkbrpRctLevel3Enabled" -> "Enables RCT level 3 unlock/progression checks based on close calls.";
            case "jjkbrpYutaCopySystemEnabled" -> "Master switch for Yuta copy mechanics, copied skills, mob copy, fake player support, and copy costs.";
            case "jjkbrpYutaLimbCopyEnabled" -> "Allows Yuta to gain copy progress from severed limb or hand related interactions.";
            case "jjkbrpYutaMobCopyEnabled" -> "Allows Yuta copy entries to be learned from mobs with limited uses and damage-share behavior.";
            case "jjkbrpYutaFakePlayerEnabled" -> "Enables the addon's fake-player support used by Yuta copy commands and automation safeguards.";
            case "jjkbrpYutaDomainSwordEnabled" -> "Enables Yuta domain sword copy behavior inside the addon's domain/copy systems.";
            case "jjkbrpRikaOwnerGuardEnabled" -> "Prevents Rika ownership edge cases by enforcing addon owner guard checks.";
            case "jjkbrpHudOverlaysEnabled" -> "Master switch for addon HUD overlays such as cooldown, Black Flash, near-death, and domain clash UI.";
            case "jjkbrpSkillWheelEnabled" -> "Enables the radial skill wheel and choose-skill packet flow. False blocks opening and selection through the wheel.";
            case "jjkbrpSkillWheelSoundsEnabled" -> "Master switch for all skill wheel sounds: open, hover, page turn, choose type, and chime.";
            case "jjkbrpSkillWheelVisualEffectsEnabled" -> "Master switch for all skill wheel visuals: animation, backdrop, cooldown overlay, flash, and particles.";
            case "jjkbrpCooldownHudEnabled" -> "Shows the addon cooldown HUD overlay for tracked skill cooldowns.";
            case "jjkbrpBlackFlashHudEnabled" -> "Shows Black Flash timing, flow, and feedback HUD elements.";
            case "jjkbrpNearDeathHudEnabled" -> "Shows near-death and close-call HUD feedback when those systems are active.";
            case "jjkbrpLimbRenderingEnabled" -> "Enables client-side limb loss rendering and visual limb state sync.";
            case "jjkbrpSkillWheelSoundVolumePercent" -> "Scales every skill wheel sound. 100 is default volume; 0 mutes wheel sounds without disabling the UI.";
            case "jjkbrpSkillWheelParticleCountPercent" -> "Scales all choose-skill particle counts. 100 keeps 24 dust, 8 enchant, and 6 end-rod particles.";
            case "jjkbrpSkillWheelAnimationSpeedPercent" -> "Scales skill wheel opening and pulse animation speed. 100 is default; higher is faster.";
            case "jjkbrpGojoRedDamagePercent" -> "Scales Gojo Red damage. 100 keeps the base addon value.";
            case "jjkbrpGojoRedRadiusPercent" -> "Scales the Red explosion or hit radius used by the addon.";
            case "jjkbrpGojoRedKnockbackPercent" -> "Scales Red knockback strength on affected targets.";
            case "jjkbrpGojoRedSpeedPercent" -> "Scales Red projectile movement speed.";
            case "jjkbrpGojoRedRangePercent" -> "Scales Red projectile travel range before it expires.";
            case "jjkbrpGojoBlueDamagePercent" -> "Scales Gojo Blue damage from the addon's Blue logic.";
            case "jjkbrpGojoBluePullRadiusPercent" -> "Scales the radius in which Blue pulls entities.";
            case "jjkbrpGojoBluePullStrengthPercent" -> "Scales Blue attraction force applied to nearby entities.";
            case "jjkbrpGojoBlueAimDurationTicks" -> "Sets how long Blue can stay in the aiming/startup state, in server ticks.";
            case "jjkbrpGojoBlueLingerDurationTicks" -> "Sets how long Blue lingers after activation, in server ticks.";
            case "jjkbrpGojoBlueBlockBreakIntervalTicks" -> "Sets the tick interval between Blue block-break attempts. Lower breaks more often.";
            case "jjkbrpGojoPurpleCeThreshold" -> "Required CE threshold before Hollow Purple fusion is allowed.";
            case "jjkbrpGojoPurpleCeCost" -> "CE consumed when the addon's Hollow Purple fusion succeeds.";
            case "jjkbrpGojoPurpleHpThresholdPercent" -> "Minimum HP percent condition used by the addon's Hollow Purple fusion gate.";
            case "jjkbrpInfinityCrusherRadiusPercent" -> "Scales Infinity Crusher area of effect radius.";
            case "jjkbrpInfinityCrusherDamagePercent" -> "Scales Infinity Crusher damage.";
            case "jjkbrpInfinityCrusherCeDrainPercent" -> "Scales the CE drain caused by Infinity Crusher.";
            case "jjkbrpInfinityCrusherLockThresholdTicks" -> "Ticks a target must remain locked before Infinity Crusher reaches its lock threshold.";
            case "jjkbrpGojoTeleportCeCost" -> "CE cost paid when Gojo teleport is used.";
            case "jjkbrpGojoTeleportCooldownTicks" -> "Cooldown after Gojo teleport, in server ticks.";
            case "jjkbrpGojoTeleportRangeBlocks" -> "Maximum Gojo teleport distance in blocks.";
            case "jjkbrpGojoTeleportDelayTicks" -> "Startup delay before Gojo teleport resolves, in server ticks.";
            case "jjkbrpBlackFlashTimingRedSizeBasis" -> "Normal Black Flash red timing window size in basis points. 10000 means a full period.";
            case "jjkbrpBlackFlashYujiRedSizeBasis" -> "Yuji-specific Black Flash red timing window size in basis points.";
            case "jjkbrpBlackFlashTimingPeriodTicks" -> "Length of the normal Black Flash timing cycle, in server ticks.";
            case "jjkbrpBlackFlashYujiPeriodTicks" -> "Length of Yuji's Black Flash timing cycle, in server ticks.";
            case "jjkbrpBlackFlashGuaranteeTimeoutTicks" -> "How long the guaranteed Black Flash opportunity may wait before expiring, in ticks.";
            case "jjkbrpBlackFlashFlowMax" -> "Maximum Black Flash flow stack or streak value tracked by the addon.";
            case "jjkbrpBlackFlashFlowCooldownTicks" -> "Cooldown before Black Flash flow can be refreshed or reused, in server ticks.";
            case "jjkbrpBlackFlashProcChancePercent" -> "Scales the chance for eligible Black Flash processing. 100 keeps the addon default.";
            case "jjkbrpBlackFlashMasteryThresholdPercent" -> "Scales the threshold needed for Black Flash mastery progression.";
            case "jjkbrpDomainIncompleteCostPercent" -> "CE cost multiplier for incomplete domain casts. 55 means 55 percent of the base cost.";
            case "jjkbrpDomainClosedCostPercent" -> "CE cost multiplier for closed domain casts.";
            case "jjkbrpDomainOpenCostPercent" -> "CE cost multiplier for open domain casts.";
            case "jjkbrpDomainRadiusPercent" -> "Scales domain radius for addon-managed domain forms.";
            case "jjkbrpDomainDurationPercent" -> "Scales active domain duration for addon-managed domain behavior.";
            case "jjkbrpDomainOpenRangePercent" -> "Scales open-domain range and interaction distance.";
            case "jjkbrpDomainOpenSurehitPercent" -> "Scales open-domain sure-hit reach or strength used by the addon.";
            case "jjkbrpDomainPropertyEffectPercent" -> "Scales extra domain property effects applied by addon logic.";
            case "jjkbrpDomainIncompleteCooldownCapTicks" -> "Caps incomplete-domain cooldown at this many server ticks.";
            case "jjkbrpDomainRangeCancelPercent" -> "Scales the range check used when cancelling or cleaning domain behavior.";
            case "jjkbrpDomainClashDurationTicks" -> "Overrides domain clash duration in ticks. 0 lets the addon choose its default duration.";
            case "jjkbrpDomainClashSampleIntervalTicks" -> "Ticks between domain clash strength samples. Lower updates contest state more often.";
            case "jjkbrpDomainClashTieThresholdPercent" -> "Percent threshold used to decide whether a domain clash is close enough to count as a tie.";
            case "jjkbrpDomainClashXpPercent" -> "Scales mastery XP granted by domain clash results.";
            case "jjkbrpSukunaFugaDustRewardPercent" -> "Scales the dust reward granted by the addon's Sukuna Fuga reward path.";
            case "jjkbrpSukunaIncompleteSurehitRangePercent" -> "Scales Sukuna incomplete-domain sure-hit range.";
            case "jjkbrpLimbSeverChancePercent" -> "Scales the chance that eligible hits sever a limb.";
            case "jjkbrpLimbSeverCooldownTicks" -> "Cooldown between limb sever attempts, in server ticks.";
            case "jjkbrpLimbBloodDripTicks" -> "Duration or interval budget for blood drip effects after limb damage, in ticks.";
            case "jjkbrpLimbRegenRatePercent" -> "Scales general limb regeneration speed.";
            case "jjkbrpLimbZoneRegenPercent" -> "Scales limb regeneration gained from zone or environment based recovery.";
            case "jjkbrpLimbArmDamagePenaltyPercent" -> "Scales damage penalty from missing or damaged arms.";
            case "jjkbrpLimbLegSlowPenaltyPercent" -> "Scales movement slow penalty from missing or damaged legs.";
            case "jjkbrpNearDeathWindowTicks" -> "Time window for detecting a near-death close call, in server ticks.";
            case "jjkbrpNearDeathCooldownTicks" -> "Cooldown before the same player can gain another near-death close call, in ticks.";
            case "jjkbrpNearDeathHealThresholdHalfHearts" -> "Heal threshold for near-death handling, measured in half-hearts.";
            case "jjkbrpRctLevel3CloseCallsRequired" -> "Number of close calls required before RCT level 3 progression unlocks.";
            case "jjkbrpRctLevel3CloseCallCooldownTicks" -> "Cooldown between close calls counted toward RCT level 3, in ticks.";
            case "jjkbrpYutaMobCopyUses" -> "Number of uses granted to a copied mob technique.";
            case "jjkbrpYutaCopyCooldownPercent" -> "Scales cooldowns applied to Yuta copied techniques.";
            case "jjkbrpYutaCopyCostPercent" -> "Scales CE costs for Yuta copied techniques.";
            case "jjkbrpYutaHandDropChancePercent" -> "Scales the chance for hand or limb drops used by Yuta copy progress.";
            case "jjkbrpYutaMobCopyDamageSharePercent" -> "Percent of damage sharing applied by Yuta mob copy behavior.";
            case "jjkbrpYutaCopySuccessChancePercent" -> "Scales the chance that an eligible Yuta copy attempt succeeds.";
            default -> genericDescriptionFor(label, id);
        };
    }

    private static String genericDescriptionFor(String label, String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.endsWith("enabled")) return "Enables or disables " + label + " at runtime.";
        if (lower.endsWith("percent")) return "Scales " + label + ". 100 keeps the default addon value.";
        if (lower.endsWith("ticks")) return "Adjusts " + label + " in server ticks.";
        if (lower.endsWith("basis")) return "Adjusts " + label + " in basis points.";
        if (lower.endsWith("blocks")) return "Adjusts " + label + " in blocks.";
        return "Runtime tuning rule for " + label + ".";
    }

    public static int intValue(LevelAccessor world, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        if (world instanceof Level level) {
            try {
                return level.getGameRules().getInt(rule);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static void updateClientSyncedRules(List<RuleSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            clientSyncedValues = Collections.emptyMap();
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.id() != null && snapshot.value() != null) {
                values.put(snapshot.id(), snapshot.value());
            }
        }
        clientSyncedValues = Collections.unmodifiableMap(values);
    }

    public static boolean clientEnabled(LevelAccessor fallbackWorld, GameRules.Key<GameRules.BooleanValue> rule) {
        return clientBoolean(fallbackWorld, ADDON_ENABLED, true) && clientBoolean(fallbackWorld, rule, true);
    }

    @SafeVarargs
    public static boolean clientEnabled(LevelAccessor fallbackWorld, GameRules.Key<GameRules.BooleanValue> first, GameRules.Key<GameRules.BooleanValue>... rest) {
        if (!clientEnabled(fallbackWorld, first)) {
            return false;
        }
        for (GameRules.Key<GameRules.BooleanValue> rule : rest) {
            if (!clientBoolean(fallbackWorld, rule, true)) {
                return false;
            }
        }
        return true;
    }

    public static int clientIntValue(LevelAccessor fallbackWorld, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        String cached = clientSyncedValues.get(rule.getId());
        if (cached != null) {
            try {
                return Integer.parseInt(cached.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return intValue(fallbackWorld, rule, fallback);
    }

    private static boolean clientBoolean(LevelAccessor fallbackWorld, GameRules.Key<GameRules.BooleanValue> rule, boolean fallback) {
        String cached = clientSyncedValues.get(rule.getId());
        if (cached != null) {
            return Boolean.parseBoolean(cached.trim());
        }
        return rawBoolean(fallbackWorld, rule, fallback);
    }

    public static int intValue(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        return entity == null ? fallback : intValue(entity.level(), rule, fallback);
    }

    public static double percent(LevelAccessor world, GameRules.Key<GameRules.IntegerValue> rule, int fallbackPercent) {
        return Math.max(0, intValue(world, rule, fallbackPercent)) / 100.0D;
    }

    public static double percent(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallbackPercent) {
        return Math.max(0, intValue(entity, rule, fallbackPercent)) / 100.0D;
    }

    public static float percentFloat(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallbackPercent) {
        return (float)percent(entity, rule, fallbackPercent);
    }

    public static double basis(LevelAccessor world, GameRules.Key<GameRules.IntegerValue> rule, int fallbackBasis) {
        return Math.max(0, intValue(world, rule, fallbackBasis)) / 10000.0D;
    }

    public static double basis(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallbackBasis) {
        return entity == null ? fallbackBasis / 10000.0D : basis(entity.level(), rule, fallbackBasis);
    }

    public static int positiveInt(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        return Math.max(1, intValue(entity, rule, fallback));
    }

    public static int positiveInt(LevelAccessor world, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        return Math.max(1, intValue(world, rule, fallback));
    }

    public static int nonNegativeInt(Entity entity, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        return Math.max(0, intValue(entity, rule, fallback));
    }

    public static int nonNegativeInt(LevelAccessor world, GameRules.Key<GameRules.IntegerValue> rule, int fallback) {
        return Math.max(0, intValue(world, rule, fallback));
    }

    public static double scaled(Entity entity, double value, GameRules.Key<GameRules.IntegerValue> percentRule, int fallbackPercent) {
        return value * percent(entity, percentRule, fallbackPercent);
    }

    public static float scaled(Entity entity, float value, GameRules.Key<GameRules.IntegerValue> percentRule, int fallbackPercent) {
        return (float)(value * percent(entity, percentRule, fallbackPercent));
    }

    public static double scaled(LevelAccessor world, double value, GameRules.Key<GameRules.IntegerValue> percentRule, int fallbackPercent) {
        return value * percent(world, percentRule, fallbackPercent);
    }

    public static double domainFormCostMultiplier(Player player, int form) {
        if (player == null || !domainCostRules(player)) {
            return 1.0D;
        }
        int percent = switch (form) {
            case 2 -> intValue(player, DOMAIN_OPEN_COST_PERCENT, 160);
            case 1 -> intValue(player, DOMAIN_CLOSED_COST_PERCENT, 100);
            default -> intValue(player, DOMAIN_INCOMPLETE_COST_PERCENT, 55);
        };
        return Math.max(0, percent) / 100.0D;
    }

    public static double domainFormCostMultiplier(LevelAccessor world, int form) {
        if (!enabled(world, DOMAIN_MASTERY_ENABLED, DOMAIN_COST_RULES_ENABLED)) {
            return 1.0D;
        }
        int percent = switch (form) {
            case 2 -> intValue(world, DOMAIN_OPEN_COST_PERCENT, 160);
            case 1 -> intValue(world, DOMAIN_CLOSED_COST_PERCENT, 100);
            default -> intValue(world, DOMAIN_INCOMPLETE_COST_PERCENT, 55);
        };
        return Math.max(0, percent) / 100.0D;
    }

    public static boolean isGojo(Player player) {
        if (player == null) {
            return false;
        }
        try {
            var vars = player.getCapability(net.mcreator.jujutsucraft.network.JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                    .orElse(new net.mcreator.jujutsucraft.network.JujutsucraftModVariables.PlayerVariables());
            double activeTechnique = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
            return Math.abs(activeTechnique - 2.0D) < 0.001D;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean rawBoolean(LevelAccessor world, GameRules.Key<GameRules.BooleanValue> rule, boolean fallback) {
        if (world instanceof Level level) {
            try {
                return level.getGameRules().getBoolean(rule);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

}
