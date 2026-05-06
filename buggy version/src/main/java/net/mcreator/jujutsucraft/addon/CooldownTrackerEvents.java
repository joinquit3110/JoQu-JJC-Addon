package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.RCTLevel3Handler;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", bus=Mod.EventBusSubscriber.Bus.FORGE)
/**
 * Server-side combat tracker for Black Flash systems. It calculates live proc chance, unlocks Black Flash mastery milestones, synchronizes HUD values, and applies near-death cooldown reductions after successful procs.
 */
public class CooldownTrackerEvents {
    // Total Black Flash hit threshold required to unlock mastery for most characters.
    private static final int BF_MASTERY_THRESHOLD = 500;
    // Reduced mastery threshold used for Yuji because his Black Flash identity is stronger.
    private static final int BF_MASTERY_THRESHOLD_YUJI = 200;
    // Legacy probability constant retained alongside the newer percentage-based Black Flash calculation.
    private static final double BF_ROLL_CHANCE = 0.0012;
    // Combat tag timeout in ticks before accumulated Black Flash combat bonus starts decaying.
    private static final int BF_COMBAT_TIMEOUT = 100;
    // Base amount of Black Flash combat bonus gained per tick while actively fighting.
    private static final double BF_COMBAT_RATE = 0.004;
    // Amount of stored combat bonus removed per tick once the player leaves combat.
    private static final double BF_COMBAT_DECAY = 0.06;
    // Maximum combat-generated bonus percentage for standard characters.
    private static final double BF_COMBAT_MAX_NORMAL = 15.0;
    // Maximum combat-generated bonus percentage for Yuji.
    private static final double BF_COMBAT_MAX_YUJI = 28.0;
    // Final Black Flash percentage cap applied to standard characters.
    private static final double BF_PERCENT_CAP_NORMAL = 15.0;
    // Final Black Flash percentage cap applied to Yuji.
    private static final double BF_PERCENT_CAP_YUJI = 30.0;
    // Near-death cooldown reduction, in ticks, granted when Black Flash procs in the supported path.
    private static final int ND_COOLDOWN_REDUCTION = 120;

    @SubscribeEvent
    /**
     * Updates live Black Flash data for each server player, including chance calculation, mastery unlock checks, and HUD sync packets.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        boolean isYuji;
        int amp;
        boolean hasBfExperience;
        int amp2;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        CompoundTag data = player2.getPersistentData();
        int bfCd = data.getInt("jjkbrp_bf_cd");
        if (bfCd > 0) {
            data.putInt("jjkbrp_bf_cd", bfCd - 1);
        }
        // Build the live Black Flash chance multiplier from advancements, effects, character bonuses, and current risk state.
        double bonusMultiplier = 1.0;
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get())) {
            amp2 = player2.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get()).getAmplifier();
            bonusMultiplier += 2.0 + (double)amp2;
        }
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.DEEP_CONCENTRATION.get())) {
            amp2 = player2.getEffect((MobEffect)JujutsucraftModMobEffects.DEEP_CONCENTRATION.get()).getAmplifier();
            bonusMultiplier += 75.0 + 5.0 * (double)(amp2 + 1);
        }
        if (hasBfExperience = CooldownTrackerEvents.hasAdvancement(player2, "jujutsucraft:black_flash")) {
            bonusMultiplier += 1.0;
        }
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.SPECIAL.get()) && (amp = player2.getEffect((MobEffect)JujutsucraftModMobEffects.SPECIAL.get()).getAmplifier()) > 0) {
            bonusMultiplier += 5.0;
        }
        JujutsucraftModVariables.PlayerVariables vars = player2.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double activeTech = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        boolean bl = isYuji = (int)Math.round(activeTech) == 21;
        if (isYuji) {
            bonusMultiplier += 3.0;
        }
        float hp = player2.getHealth();
        float maxHp = player2.getMaxHealth();
        double hpFactor = 1.0 - (double)Math.max(hp, 1.0f) / (double)Math.max(maxHp, 1.0f);
        // Convert the accumulated multiplier into a bounded percentage curve so bonuses scale smoothly instead of linearly exploding.
        double basePercent = (1.0 - Math.pow(0.9988, bonusMultiplier * (1.0 + hpFactor * 2.0))) * 100.0;
        boolean hasMastery = data.getBoolean("addon_bf_mastery");
        long lastCombatTick = data.getLong("addon_bf_last_combat_tick");
        boolean inCombat = (long)player2.tickCount - lastCombatTick < 100L;
        double combatBonus = data.getDouble("addon_bf_combat_bonus");
        double rate = 0.004;
        if (hasBfExperience) {
            rate *= 1.2;
        }
        if (hasMastery) {
            rate *= 1.4;
        }
        if (isYuji) {
            rate *= 1.6;
        }
        double combatMax = isYuji ? 28.0 : 15.0;
        double percentCap = isYuji ? 30.0 : 15.0;
        // Combat gradually ramps the proc rate up, then decays it once the player falls out of the recent-combat window.
        combatBonus = inCombat ? Math.min(combatMax, combatBonus + rate) : Math.max(0.0, combatBonus - 0.06);
        data.putDouble("addon_bf_combat_bonus", combatBonus);
        double domainBonus = Math.max(0.0, data.getDouble("jjkbrp_domain_bf_bonus"));
        double bfPercent = basePercent + combatBonus + domainBonus;
        if (bfPercent > percentCap) {
            bfPercent = percentCap;
        }
        data.putDouble("addon_bf_chance", bfPercent);
        if (!hasMastery) {
            if (CooldownTrackerEvents.hasAdvancement(player2, "jjkblueredpurple:black_flash_mastery")) {
                data.putBoolean("addon_bf_mastery", true);
                hasMastery = true;
            } else {
                int threshold;
                int totalHits = data.getInt("addon_bf_total_hits");
                int n = threshold = isYuji ? 200 : 500;
                // Mastery unlocks automatically after enough successful Black Flash-related combat milestones, with a lower threshold for Yuji.
                if (totalHits >= threshold) {
                    data.putBoolean("addon_bf_mastery", true);
                    CooldownTrackerEvents.grantAdvancement(player2, "jjkblueredpurple:black_flash_mastery");
                    player2.displayClientMessage((Component)Component.literal((String)"\u00a7d\u00a7l\u2605 Black Flash Mastery Achieved! \u2605"), false);
                }
            }
        }
        if (player2.tickCount % 10 == 0) {
            ModNetworking.sendBlackFlashSync(player2);
        }
        if (player2.tickCount % 20 == 0) {
            ModNetworking.sendNearDeathCdSync(player2);
        }
        if (player2.tickCount % 40 == 0) {
            RCTLevel3Handler.checkAndGrantRCTLevel3(player2);
        }
    }

    @SubscribeEvent
    /**
     * Tracks recent combat activity and applies Black Flash near-death cooldown interactions when valid hits land.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onLivingHurt(LivingHurtEvent event) {
        ServerPlayer sp;
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getDirectEntity();
        if (attacker instanceof ServerPlayer) {
            sp = (ServerPlayer)attacker;
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", (long)sp.tickCount);
            MobEffectInstance zone = sp.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
            boolean bfJustProcced = zone != null && zone.getDuration() >= 5990;
            boolean rangedHandled = sp.getPersistentData().getBoolean("jjkbrp_bf_nd_handled");
            if (bfJustProcced) {
                int currentNdCd;
                // A confirmed Black Flash proc shaves time off the near-death cooldown so clutch plays feed back into survival tools.
                if (!rangedHandled && (currentNdCd = sp.getPersistentData().getInt("jjkbrp_near_death_cd")) > 0) {
                    int newCd = Math.max(0, currentNdCd - 120);
                    sp.getPersistentData().putInt("jjkbrp_near_death_cd", newCd);
                }
                sp.getPersistentData().putBoolean("jjkbrp_bf_regen_boost", true);
                sp.getPersistentData().putBoolean("jjkbrp_bf_nd_handled", false);
            }
        }
        if (event.getEntity() instanceof ServerPlayer) {
            sp = (ServerPlayer)event.getEntity();
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", (long)sp.tickCount);
        }
    }

    static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Awards every remaining criterion on the requested advancement when the player qualifies for mastery unlocks.
     * @param player player instance involved in this operation.
     * @param advancementId identifier used to resolve the requested entry or state.
     */
    private static void grantAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
            if (adv == null) {
                return;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (!progress.isDone()) {
                for (String criteria : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(adv, criteria);
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

