package net.mcreator.jujutsucraft.addon.limb;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.LimbCapabilityProvider;
import net.mcreator.jujutsucraft.addon.limb.LimbLossHandler;
import net.mcreator.jujutsucraft.addon.limb.LimbParticles;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.mcreator.jujutsucraft.addon.limb.NearDeathPacket;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;

/**
 * RCT Level 3 handler.
 * Manages near-death mechanic where players with RCT 3 can survive lethal hits
 * by passing a 20-tick near-death window. If they heal enough with RCT, they survive.
 */
@Mod.EventBusSubscriber(modid = "jjkblueredpurple")
public class RCTLevel3Handler {

    public static final int NEAR_DEATH_WINDOW = 20;
    private static final int NEAR_DEATH_COOLDOWN = 18000;
    private static final float HEAL_THRESHOLD = 4.0f;
    private static final int RCT_L3_UNLOCK_COUNT = 20;
    private static final int CLOSE_CALL_COOLDOWN = 200;

    private static final String KEY_NEAR_DEATH = "jjkbrp_near_death";
    private static final String KEY_ND_TICKS = "jjkbrp_near_death_ticks";
    private static final String KEY_ND_COOLDOWN = "jjkbrp_near_death_cd";
    private static final String KEY_CLOSE_CALL = "jjkbrp_rct_close_call";
    private static final String KEY_FINAL_DEATH = "jjkbrp_final_death";
    /** True last tick: RCT active and HP below 10% max (critical). */
    private static final String KEY_PREV_CRIT_RCT = "jjkbrp_prev_crit_rct";

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null || !player.getServer().isRunning()) return;

        CompoundTag data = player.getPersistentData();

        if (data.getBoolean(KEY_FINAL_DEATH)) {
            data.putBoolean(KEY_FINAL_DEATH, false);
            return;
        }

        if (data.getBoolean(KEY_NEAR_DEATH)) {
            event.setCanceled(true);
            player.setHealth(1.0f);
            return;
        }

        if (data.getInt(KEY_ND_COOLDOWN) > 0) return;

        if (!hasAdvancement(player, "jjkblueredpurple:rct_level_3")) return;

        event.setCanceled(true);
        player.setHealth(1.0f);
        try {
            Field portalTimeField = LivingEntity.class.getDeclaredField("portalTime");
            portalTimeField.setAccessible(true);
            portalTimeField.setInt(player, 40);
        } catch (Exception ignored) {}
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.ABSORPTION);

        data.putBoolean(KEY_NEAR_DEATH, true);
        data.putInt(KEY_ND_TICKS, NEAR_DEATH_WINDOW);

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));

        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new NearDeathPacket(true, NEAR_DEATH_WINDOW));

        player.displayClientMessage(Component.literal("\u00a7c\u00a7l\u2665 Near Death"), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null || !player.getServer().isRunning()) return;

        CompoundTag data = player.getPersistentData();

        int cd = data.getInt(KEY_ND_COOLDOWN);
        if (cd > 0) {
            data.putInt(KEY_ND_COOLDOWN, cd - 1);
        }

        if (!data.getBoolean(KEY_NEAR_DEATH)) {
            float maxHp = player.getMaxHealth();
            float hp = player.getHealth();
            boolean rct = player.hasEffect(JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());
            boolean nowCritRct = rct && hp > 0.0f && hp < maxHp * 0.1f;
            boolean prevCritRct = data.getBoolean(KEY_PREV_CRIT_RCT);

            // Only count close-call for players who have RCT level 2 + special grade, but not yet RCT level 3
            if (!hasAdvancement(player, "jjkblueredpurple:rct_level_3")
                && hasAdvancement(player, "jujutsucraft:reverse_cursed_technique_2")
                && hasAdvancement(player, "jujutsucraft:sorcerer_grade_special")
                && prevCritRct && rct && hp >= HEAL_THRESHOLD
                && player.tickCount - data.getInt("jjkbrp_last_close_call_tick") > CLOSE_CALL_COOLDOWN) {
                int closeCallCount = data.getInt(KEY_CLOSE_CALL) + 1;
                data.putInt(KEY_CLOSE_CALL, closeCallCount);
                data.putInt("jjkbrp_last_close_call_tick", player.tickCount);
                RCTLevel3Handler.notifyCloseCall(player, closeCallCount);
                checkAndGrantRCTLevel3(player);
            }

            data.putBoolean(KEY_PREV_CRIT_RCT, nowCritRct);
            return;
        }

        if (player.getHealth() >= HEAL_THRESHOLD
            && player.hasEffect(JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get())) {
            exitNearDeath(player, true);
            return;
        }

        int ticks = data.getInt(KEY_ND_TICKS) - 1;
        data.putInt(KEY_ND_TICKS, ticks);

        LimbCapabilityProvider.get(player).ifPresent(limbData -> {
            LimbParticles.spawnBloodDrip(player, LimbType.HEAD);
            if (player.tickCount % 2 == 0) {
                LimbParticles.spawnSeverBloodBurst(player, LimbType.HEAD);
            }
        });

        if (player.tickCount % 2 == 0) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NearDeathPacket(true, ticks));
        }

        if (ticks <= 0) {
            exitNearDeath(player, false);
            data.putBoolean(KEY_FINAL_DEATH, true);
            LimbCapabilityProvider.get(player).ifPresent(limbData ->
                LimbLossHandler.severLimb(player, limbData, LimbType.HEAD,
                    player.level().damageSources().generic()));
        }
    }

    public static void checkAndGrantRCTLevel3(ServerPlayer player) {
        if (hasAdvancement(player, "jjkblueredpurple:rct_level_3")) return;
        if (!hasAdvancement(player, "jujutsucraft:reverse_cursed_technique_2")) return;
        if (!hasAdvancement(player, "jujutsucraft:sorcerer_grade_special")) return;

        int count = player.getPersistentData().getInt(KEY_CLOSE_CALL);
        if (count >= RCT_L3_UNLOCK_COUNT) {
            grantAdvancement(player, "jjkblueredpurple:rct_level_3");
            player.displayClientMessage(
                Component.literal("\u00a7b\u00a7l\u2605 RCT Mastery Unlocked! \u2605"), false);
        }
    }

    /**
     * Called when a close-call is registered. Sends a styled chat notification
     * showing the current count and how many remain until RCT Level 3 unlocks.
     * Milestones (25%, 50%, 75%, 90%) trigger slightly more prominent messages.
     */
    public static void notifyCloseCall(ServerPlayer player, int currentCount) {
        int remaining = RCT_L3_UNLOCK_COUNT - currentCount;
        int milestone = (currentCount * 100) / RCT_L3_UNLOCK_COUNT;

        String bar = buildProgressBar(currentCount, RCT_L3_UNLOCK_COUNT);
        String msg;
        if (currentCount == RCT_L3_UNLOCK_COUNT - 1) {
            msg = "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a76" + RCT_L3_UNLOCK_COUNT
                + " \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f\u2014 \u00a7c\u2620 LAST CHANCE!";
        } else if (milestone >= 90) {
            msg = "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a76" + RCT_L3_UNLOCK_COUNT
                + " \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f\u2014 \u00a7a" + remaining + " more!";
        } else if (milestone >= 75) {
            msg = "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a76" + RCT_L3_UNLOCK_COUNT
                + " \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f\u2014 \u00a7e" + remaining + " more until RCT Mastery";
        } else if (milestone >= 50) {
            msg = "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a76" + RCT_L3_UNLOCK_COUNT
                + " \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f\u2014 \u00a7e" + remaining + " more";
        } else {
            msg = "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a76" + RCT_L3_UNLOCK_COUNT
                + " \u00a7e[\u00a76" + bar + "\u00a7e]";
        }

        player.displayClientMessage(Component.literal(msg), false);
    }

    private static String buildProgressBar(int current, int max) {
        int total = 20;
        int filled = Math.min(total, (current * total) / max);
        int empty = total - filled;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++)   bar.append('\u2588');
        for (int i = 0; i < empty; i++)    bar.append('\u2591');
        return bar.toString();
    }

    private static void exitNearDeath(ServerPlayer player, boolean survived) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(KEY_NEAR_DEATH, false);
        data.putInt(KEY_ND_TICKS, 0);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new NearDeathPacket(false, 0));

        if (survived) {
            data.putInt(KEY_ND_COOLDOWN, NEAR_DEATH_COOLDOWN);
            player.displayClientMessage(
                Component.literal("\u00a7a\u2665 Reversed! \u00a7eSurvived Near-Death!"), true);
        }

        ModNetworking.sendNearDeathCdSync(player);
    }

    public static boolean isInNearDeath(Player player) {
        return player.getPersistentData().getBoolean(KEY_NEAR_DEATH);
    }

    public static boolean hasAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(id));
            if (adv == null) return false;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onActualDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (event.getEntity().getServer() == null || !event.getEntity().getServer().isRunning()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        CompoundTag data = player.getPersistentData();

        if (!data.getBoolean(KEY_NEAR_DEATH)) {
            data.putInt(KEY_ND_COOLDOWN, 0);
        }
    }

    static void grantAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(id));
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



