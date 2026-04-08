package net.mcreator.jujutsucraft.addon.limb;

import java.lang.reflect.Field;
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
import net.minecraft.world.effect.MobEffect;
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

/**
 * Handles the near-death survival window and RCT Level 3 progression.
 *
 * <p>When an eligible player would die, this handler can intercept the death, place the player into a
 * short near-death state, synchronize the countdown to the client, and reward close-call progression
 * that eventually unlocks the RCT Level 3 advancement.</p>
 */
@Mod.EventBusSubscriber(modid="jjkblueredpurple")
public class RCTLevel3Handler {
    /** Near-death survival window in ticks before failure becomes a real death. */
    public static final int NEAR_DEATH_WINDOW = 20;
    /** Cooldown in ticks before near-death protection can trigger again. */
    private static final int NEAR_DEATH_COOLDOWN = 18000;
    /** Health threshold required to escape near-death successfully. */
    private static final float HEAL_THRESHOLD = 4.0f;
    /** Number of close calls required to unlock RCT Level 3. */
    private static final int RCT_L3_UNLOCK_COUNT = 20;
    /** Minimum spacing in ticks between close-call credit events. */
    private static final int CLOSE_CALL_COOLDOWN = 200;
    /** Persistent flag indicating the player is currently in the near-death state. */
    private static final String KEY_NEAR_DEATH = "jjkbrp_near_death";
    /** Persistent countdown timer storing ticks remaining in near-death. */
    private static final String KEY_ND_TICKS = "jjkbrp_near_death_ticks";
    /** Persistent cooldown timer before near-death can trigger again. */
    private static final String KEY_ND_COOLDOWN = "jjkbrp_near_death_cd";
    /** Persistent counter tracking earned close calls toward RCT Level 3. */
    private static final String KEY_CLOSE_CALL = "jjkbrp_rct_close_call";
    /** Persistent flag used to allow the forced fatal head sever after a failed near-death. */
    private static final String KEY_FINAL_DEATH = "jjkbrp_final_death";
    /** Persistent flag remembering whether the previous tick was a critical-health RCT state. */
    private static final String KEY_PREV_CRIT_RCT = "jjkbrp_prev_crit_rct";

    // ===== NEAR-DEATH ENTRY =====

    /**
     * Intercepts fatal damage and starts the near-death window when the player qualifies.
     *
     * @param event living death event
     */
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        if (player.getServer() == null || !player.getServer().isRunning()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(KEY_FINAL_DEATH)) {
            // The follow-up death after a failed near-death should proceed normally exactly once.
            data.putBoolean(KEY_FINAL_DEATH, false);
            return;
        }
        if (data.getBoolean(KEY_NEAR_DEATH)) {
            // Repeated fatal events during near-death are ignored so the countdown can resolve cleanly.
            event.setCanceled(true);
            player.setHealth(1.0f);
            return;
        }
        if (data.getInt(KEY_ND_COOLDOWN) > 0) {
            return;
        }
        if (!RCTLevel3Handler.hasAdvancement(player, "jjkblueredpurple:rct_level_3")) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0f);
        try {
            // Reset portalTime to help suppress awkward death transition behavior on the client.
            Field portalTimeField = LivingEntity.class.getDeclaredField("portalTime");
            portalTimeField.setAccessible(true);
            portalTimeField.setInt(player, 40);
        }
        catch (Exception exception) {
            // empty catch block
        }
        // Strip helpful vanilla buffers so survival requires active healing.
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.ABSORPTION);
        data.putBoolean(KEY_NEAR_DEATH, true);
        data.putInt(KEY_ND_TICKS, 20);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new NearDeathPacket(true, 20));
        player.displayClientMessage((Component)Component.literal((String)"\u00a7c\u00a7l\u2665 Near Death"), true);
    }

    // ===== NEAR-DEATH TICKING AND CLOSE-CALL PROGRESSION =====

    /**
     * Updates the near-death countdown and tracks close-call progress toward RCT Level 3.
     *
     * @param event living tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        if (player.getServer() == null || !player.getServer().isRunning()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        int cd = data.getInt(KEY_ND_COOLDOWN);
        if (cd > 0) {
            data.putInt(KEY_ND_COOLDOWN, cd - 1);
        }
        if (!data.getBoolean(KEY_NEAR_DEATH)) {
            // Outside the active near-death state, watch for recovered critical-health RCT situations.
            float maxHp = player.getMaxHealth();
            float hp = player.getHealth();
            boolean rct = player.hasEffect((MobEffect)JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());
            boolean nowCritRct = rct && hp > 0.0f && hp < maxHp * 0.1f;
            boolean prevCritRct = data.getBoolean(KEY_PREV_CRIT_RCT);
            if (!RCTLevel3Handler.hasAdvancement(player, "jjkblueredpurple:rct_level_3") && RCTLevel3Handler.hasAdvancement(player, "jujutsucraft:reverse_cursed_technique_2") && RCTLevel3Handler.hasAdvancement(player, "jujutsucraft:sorcerer_grade_special") && prevCritRct && rct && hp >= 4.0f && player.tickCount - data.getInt("jjkbrp_last_close_call_tick") > 200) {
                int closeCallCount = data.getInt(KEY_CLOSE_CALL) + 1;
                data.putInt(KEY_CLOSE_CALL, closeCallCount);
                data.putInt("jjkbrp_last_close_call_tick", player.tickCount);
                RCTLevel3Handler.notifyCloseCall(player, closeCallCount);
                RCTLevel3Handler.checkAndGrantRCTLevel3(player);
            }
            data.putBoolean(KEY_PREV_CRIT_RCT, nowCritRct);
            return;
        }
        if (player.getHealth() >= 4.0f && player.hasEffect((MobEffect)JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get())) {
            // Reaching the heal threshold with active RCT successfully ends near-death.
            RCTLevel3Handler.exitNearDeath(player, true);
            return;
        }
        int ticks = data.getInt(KEY_ND_TICKS) - 1;
        data.putInt(KEY_ND_TICKS, ticks);
        LimbCapabilityProvider.get((LivingEntity)player).ifPresent(limbData -> {
            // Head blood effects make the near-death state visually intense even before final failure.
            LimbParticles.spawnBloodDrip((LivingEntity)player, LimbType.HEAD);
            if (player.tickCount % 2 == 0) {
                LimbParticles.spawnSeverBloodBurst((LivingEntity)player, LimbType.HEAD);
            }
        });
        if (player.tickCount % 2 == 0) {
            // The client countdown is refreshed every other tick to keep the UI responsive without spam.
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new NearDeathPacket(true, ticks));
        }
        if (ticks <= 0) {
            RCTLevel3Handler.exitNearDeath(player, false);
            data.putBoolean(KEY_FINAL_DEATH, true);
            // Failure forces a final head sever so death resolves through the limb system itself.
            LimbCapabilityProvider.get((LivingEntity)player).ifPresent(limbData -> LimbLossHandler.severLimb((LivingEntity)player, limbData, LimbType.HEAD, player.level().damageSources().generic()));
        }
    }

    // ===== RCT LEVEL 3 UNLOCK FLOW =====

    /**
     * Grants RCT Level 3 once all prerequisites and close-call counts are satisfied.
     *
     * @param player player being evaluated
     */
    public static void checkAndGrantRCTLevel3(ServerPlayer player) {
        if (RCTLevel3Handler.hasAdvancement(player, "jjkblueredpurple:rct_level_3")) {
            return;
        }
        if (!RCTLevel3Handler.hasAdvancement(player, "jujutsucraft:reverse_cursed_technique_2")) {
            return;
        }
        if (!RCTLevel3Handler.hasAdvancement(player, "jujutsucraft:sorcerer_grade_special")) {
            return;
        }
        int count = player.getPersistentData().getInt(KEY_CLOSE_CALL);
        if (count >= 20) {
            RCTLevel3Handler.grantAdvancement(player, "jjkblueredpurple:rct_level_3");
            player.displayClientMessage((Component)Component.literal((String)"\u00a7b\u00a7l\u2605 RCT Mastery Unlocked! \u2605"), false);
        }
    }

    /**
     * Sends a progress-style close-call message to the player.
     *
     * @param player target player
     * @param currentCount updated close-call count
     */
    public static void notifyCloseCall(ServerPlayer player, int currentCount) {
        int remaining = 20 - currentCount;
        int milestone = currentCount * 100 / 20;
        String bar = RCTLevel3Handler.buildProgressBar(currentCount, 20);
        // Message urgency ramps up as the player approaches the 20 close-call mastery requirement.
        String msg = currentCount == 19 ? "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a7620 \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f— \u00a7c\u2620 LAST CHANCE!" : (milestone >= 90 ? "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a7620 \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f— \u00a7a" + remaining + " more!" : (milestone >= 75 ? "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a7620 \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f— \u00a7e" + remaining + " more until RCT Mastery" : (milestone >= 50 ? "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a7620 \u00a7e[\u00a76" + bar + "\u00a7e] \u00a7f— \u00a7e" + remaining + " more" : "\u00a7e\u2620 \u00a7fClose Call \u00a76" + currentCount + "\u00a7e/\u00a7620 \u00a7e[\u00a76" + bar + "\u00a7e]")));
        player.displayClientMessage((Component)Component.literal((String)msg), false);
    }

    /**
     * Builds the text progress bar used by close-call notifications.
     *
     * @param current current progress count
     * @param max maximum progress count
     * @return fixed-width text progress bar made of block characters
     */
    private static String buildProgressBar(int current, int max) {
        int i;
        int total = 20;
        int filled = Math.min(total, current * total / max);
        int empty = total - filled;
        StringBuilder bar = new StringBuilder();
        for (i = 0; i < filled; ++i) {
            bar.append('\u2588');
        }
        for (i = 0; i < empty; ++i) {
            bar.append('\u2591');
        }
        return bar.toString();
    }

    // ===== NEAR-DEATH EXIT =====

    /**
     * Leaves the near-death state, optionally counting it as a successful survival.
     *
     * @param player player leaving near-death
     * @param survived whether the exit was a successful recovery
     */
    private static void exitNearDeath(ServerPlayer player, boolean survived) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(KEY_NEAR_DEATH, false);
        data.putInt(KEY_ND_TICKS, 0);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new NearDeathPacket(false, 0));
        if (survived) {
            data.putInt(KEY_ND_COOLDOWN, 18000);
            player.displayClientMessage((Component)Component.literal((String)"\u00a7a\u2665 Reversed! \u00a7eSurvived Near-Death!"), true);
        }
        ModNetworking.sendNearDeathCdSync(player);
    }

    // ===== HELPERS =====

    /**
     * Checks whether the given player is currently flagged as near-death.
     *
     * @param player player being queried
     * @return {@code true} when the near-death flag is active
     */
    public static boolean isInNearDeath(Player player) {
        return player.getPersistentData().getBoolean(KEY_NEAR_DEATH);
    }

    /**
     * Checks whether a player has completed an advancement.
     *
     * @param player player being queried
     * @param id full advancement id string
     * @return {@code true} when the advancement exists and is complete
     */
    public static boolean hasAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(id));
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
     * Clears near-death cooldown after ordinary deaths that did not involve the near-death state.
     *
     * @param event living death event
     */
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void onActualDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (event.getEntity().getServer() == null || !event.getEntity().getServer().isRunning()) {
            return;
        }
        ServerPlayer player = (ServerPlayer)event.getEntity();
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(KEY_NEAR_DEATH)) {
            data.putInt(KEY_ND_COOLDOWN, 0);
        }
    }

    /**
     * Awards every remaining criterion on the specified advancement.
     *
     * @param player player receiving the advancement
     * @param id full advancement id string
     */
    static void grantAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(id));
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
