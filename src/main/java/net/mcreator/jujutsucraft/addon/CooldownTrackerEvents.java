package net.mcreator.jujutsucraft.addon;

/**
 * Server-side event handler that manages Black Flash charge mechanics and
 * coordinates near-death tracking across the addon.
 *
 * <h2>Black Flash system</h2>
 * Each player accumulates a BF chance percentage (base + combat bonus).
 * The combat bonus increases while in combat and decays when out of combat.
 * At high enough hit counts, the player earns Black Flash Mastery.
 *
 * <h2>Near-death cooldown</h2>
 * BF hits reduce the near-death cooldown, allowing RCT Level 3 to be
 * available more frequently for active players.
 *
 * <h2>Tick scheduling</h2>
 * BF sync packets are sent every 10 ticks; ND-CD sync every 20 ticks;
 * and the close-call grant check every 40 ticks.
 *
 * @see ModNetworking
 * @see RCTLevel3Handler
 */

import net.mcreator.jujutsucraft.addon.limb.RCTLevel3Handler;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tracks Black Flash charge mechanics server-side.
 * Manages BF chance calculation, combat bonuses, mastery tracking,
 * and sync packets to client.
 */
@Mod.EventBusSubscriber(modid = "jjkblueredpurple", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CooldownTrackerEvents {

    private static final int BF_MASTERY_THRESHOLD = 500;
    private static final int BF_MASTERY_THRESHOLD_YUJI = 200;

    /** Base roll chance (0.0012 = 0.12% per tick) */
    private static final double BF_ROLL_CHANCE = 0.0012;

    /** Combat timeout before BF combat bonus decays (100 ticks = 5s) */
    private static final int BF_COMBAT_TIMEOUT = 100;

    /** BF combat bonus accumulation rate per tick */
    private static final double BF_COMBAT_RATE = 0.004;

    /** Decay rate for BF combat bonus when out of combat */
    private static final double BF_COMBAT_DECAY = 0.06;

    /** Max combat bonus (stacked toward cap; Yuji has higher ceiling) */
    private static final double BF_COMBAT_MAX_NORMAL = 15.0;
    private static final double BF_COMBAT_MAX_YUJI = 28.0;

    /** Cap on total BF percent (15% normal, 30% Yuji Itadori) */
    private static final double BF_PERCENT_CAP_NORMAL = 15.0;
    private static final double BF_PERCENT_CAP_YUJI = 30.0;

    /** Near-death cooldown reduction per successful Black Flash hit on melee (ticks = 6s) */
    private static final int ND_COOLDOWN_REDUCTION = 120;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        CompoundTag data = player.getPersistentData();

        // Tick BF cooldown
        int bfCd = data.getInt("jjkbrp_bf_cd");
        if (bfCd > 0) {
            data.putInt("jjkbrp_bf_cd", bfCd - 1);
        }

        double bonusMultiplier = 1.0;

        // Zone effect: +2 + amplifier
        if (player.hasEffect(JujutsucraftModMobEffects.ZONE.get())) {
            int amp = player.getEffect(JujutsucraftModMobEffects.ZONE.get()).getAmplifier();
            bonusMultiplier += 2.0 + amp;
        }

        // Deep Concentration: +75 + 5*(amp+1)
        if (player.hasEffect(JujutsucraftModMobEffects.DEEP_CONCENTRATION.get())) {
            int amp = player.getEffect(JujutsucraftModMobEffects.DEEP_CONCENTRATION.get()).getAmplifier();
            bonusMultiplier += 75.0 + 5.0 * (amp + 1);
        }

        // Black Flash achievement: +1
        boolean hasBfExperience = hasAdvancement(player, "jujutsucraft:black_flash");
        if (hasBfExperience) {
            bonusMultiplier += 1.0;
        }

        // Special grade effect: +5
        if (player.hasEffect(JujutsucraftModMobEffects.SPECIAL.get())) {
            int amp = player.getEffect(JujutsucraftModMobEffects.SPECIAL.get()).getAmplifier();
            if (amp > 0) bonusMultiplier += 5.0;
        }

        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(
            JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
            .orElse(new JujutsucraftModVariables.PlayerVariables());

        double activeTech = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        boolean isYuji = (int) Math.round(activeTech) == 21;
        if (isYuji) {
            bonusMultiplier += 3.0;
        }

        float hp = player.getHealth();
        float maxHp = player.getMaxHealth();

        // HP factor: more damage = more BF chance
        double hpFactor = 1.0 - (double) Math.max(hp, 1) / Math.max(maxHp, 1);
        double basePercent = (1.0 - Math.pow(0.9988, bonusMultiplier * (1.0 + hpFactor * 2.0))) * 100.0;

        boolean hasMastery = data.getBoolean("addon_bf_mastery");
        long lastCombatTick = data.getLong("addon_bf_last_combat_tick");
        boolean inCombat = player.tickCount - lastCombatTick < BF_COMBAT_TIMEOUT;

        double combatBonus = data.getDouble("addon_bf_combat_bonus");

        double rate = BF_COMBAT_RATE;
        if (hasBfExperience) rate *= 1.2;
        if (hasMastery) rate *= 1.4;
        if (isYuji) rate *= 1.6;

        double combatMax = isYuji ? BF_COMBAT_MAX_YUJI : BF_COMBAT_MAX_NORMAL;
        double percentCap = isYuji ? BF_PERCENT_CAP_YUJI : BF_PERCENT_CAP_NORMAL;

        if (inCombat) {
            combatBonus = Math.min(combatMax, combatBonus + rate);
        } else {
            combatBonus = Math.max(0.0, combatBonus - BF_COMBAT_DECAY);
        }
        data.putDouble("addon_bf_combat_bonus", combatBonus);

        double bfPercent = basePercent + combatBonus;
        if (bfPercent > percentCap) bfPercent = percentCap;
        data.putDouble("addon_bf_chance", bfPercent);

        // Mastery check
        if (!hasMastery) {
            if (hasAdvancement(player, "jjkblueredpurple:black_flash_mastery")) {
                data.putBoolean("addon_bf_mastery", true);
                hasMastery = true;
            } else {
                int totalHits = data.getInt("addon_bf_total_hits");
                int threshold = isYuji ? BF_MASTERY_THRESHOLD_YUJI : BF_MASTERY_THRESHOLD;
                if (totalHits >= threshold) {
                    data.putBoolean("addon_bf_mastery", true);
                    grantAdvancement(player, "jjkblueredpurple:black_flash_mastery");
                    player.displayClientMessage(
                        Component.literal("\u00a7d\u00a7l\u2605 Black Flash Mastery Achieved! \u2605"), false);
                }
            }
        }

        if (player.tickCount % 10 == 0) {
            ModNetworking.sendBlackFlashSync(player);
        }
        if (player.tickCount % 20 == 0) {
            ModNetworking.sendNearDeathCdSync(player);
        }
        if (player.tickCount % 40 == 0) {
            RCTLevel3Handler.checkAndGrantRCTLevel3(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) attacker;
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", sp.tickCount);

            // JujutsuCraft Black Flash is detected by Zone effect duration spike: >= 5990
            MobEffectInstance zone = sp.getEffect(JujutsucraftModMobEffects.ZONE.get());
            boolean bfJustProcced = zone != null && zone.getDuration() >= 5990;
            // Ranged BF already handled ND reduction + regen boost in RangeAttackProcedureMixin
            boolean rangedHandled = sp.getPersistentData().getBoolean("jjkbrp_bf_nd_handled");

            if (bfJustProcced) {
                if (!rangedHandled) {
                    int currentNdCd = sp.getPersistentData().getInt("jjkbrp_near_death_cd");
                    if (currentNdCd > 0) {
                        int newCd = Math.max(0, currentNdCd - ND_COOLDOWN_REDUCTION);
                        sp.getPersistentData().putInt("jjkbrp_near_death_cd", newCd);
                    }
                }
                sp.getPersistentData().putBoolean("jjkbrp_bf_regen_boost", true);
                sp.getPersistentData().putBoolean("jjkbrp_bf_nd_handled", false);
            }
        }

        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) event.getEntity();
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", sp.tickCount);
        }
    }

    static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(advancementId));
            if (adv == null) return false;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        } catch (Exception e) {
            return false;
        }
    }

    private static void grantAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(advancementId));
            if (adv == null) return;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (!progress.isDone()) {
                for (String criteria : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(adv, criteria);
                }
            }
        } catch (Exception ignored) {}
    }
}
