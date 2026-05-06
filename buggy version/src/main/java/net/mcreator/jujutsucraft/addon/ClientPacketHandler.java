package net.mcreator.jujutsucraft.addon;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryScreen;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.SkillWheelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

/**
 * Client-side packet sink and cache hub used by addon networking. Incoming sync packets update lightweight caches that GUI screens and HUD overlays can read without touching server logic.
 */
public final class ClientPacketHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Creates a new client packet handler instance and initializes its addon state.
     */
    private ClientPacketHandler() {
    }

    /**
     * Opens skill wheel on the appropriate client screen or workflow.
     * @param pages pages used by this method.
     * @param currentSelect current select used by this method.
     */
    public static void openSkillWheel(List<List<ModNetworking.WheelTechniqueEntry>> pages, double currentSelect) {
        Minecraft.getInstance().setScreen((Screen)new SkillWheelScreen(pages, currentSelect));
    }

    /**
     * Updates cooldowns using the latest available data.
     * @param techRemaining tech remaining used by this method.
     * @param techMax tech max used by this method.
     * @param combatRemaining combat remaining used by this method.
     * @param combatMax combat max used by this method.
     */
    public static void updateCooldowns(int techRemaining, int techMax, int combatRemaining, int combatMax) {
        ClientCooldownCache.updateTechnique(techRemaining, techMax);
        ClientCooldownCache.updateCombat(combatRemaining, combatMax);
    }

    /**
     * Updates black flash using the latest available data.
     * @param bfPercent bf percent used by this method.
     * @param mastery mastery used by this method.
     * @param charging charging used by this method.
     */
    public static void updateBlackFlash(float bfPercent, boolean mastery, boolean charging) {
        ClientBlackFlashCache.bfPercent = bfPercent;
        ClientBlackFlashCache.mastery = mastery;
        ClientBlackFlashCache.charging = charging;
    }

    /**
     * Updates near death cooldown using the latest available data.
     * @param cdRemaining cd remaining used by this method.
     * @param cdMax cd max used by this method.
     * @param rctLevel3Unlocked rct level 3 unlocked used by this method.
     */
    public static void updateNearDeathCooldown(int cdRemaining, int cdMax, boolean rctLevel3Unlocked) {
        ClientNearDeathCdCache.cdRemaining = cdRemaining;
        ClientNearDeathCdCache.cdMax = cdMax;
        ClientNearDeathCdCache.rctLevel3Unlocked = rctLevel3Unlocked;
    }

    /**
     * Opens domain mastery screen on the appropriate client screen or workflow.
     */
    public static void openDomainMasteryScreen() {
        Minecraft.getInstance().setScreen((Screen)new DomainMasteryScreen());
    }

    /**
     * Synchronizes domain mastery with the client or server side copy.
     * @param xp xp used by this method.
     * @param level level value used by this operation.
     * @param form form used by this method.
     * @param points points used by this method.
     * @param propLevels prop levels used by this method.
     * @param negativeProperty property identifier involved in this operation.
     * @param negativeLevel level value used by this operation.
     * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
     */
    public static void syncDomainMastery(double xp, int level, int form, int points, int[] propLevels, String negativeProperty, int negativeLevel, boolean hasOpenBarrierAdvancement) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> data.applySync(xp, level, form, points, propLevels, negativeProperty, negativeLevel, hasOpenBarrierAdvancement));
    }

    public static void updateDomainClash(float casterPower, int casterDomainId,
                                          int casterForm, String casterName,
                                          boolean active, long syncedGameTime,
                                          List<ModNetworking.DomainClashOpponentPayload> opponents) {
        boolean cacheWasActive = ClientDomainClashCache.active;
        int previousOpponentCount = ClientDomainClashCache.opponents.size();
        LOGGER.info("[DomainClashDebug][ClientSync] recv active={} caster={} power={} syncedTick={} packetOpponents={} cacheWasActive={} prevOpponents={}",
                active, casterName, casterPower, syncedGameTime,
                opponents == null ? 0 : opponents.size(), cacheWasActive, previousOpponentCount);
        if (!active) {
            ClientDomainClashCache.clear(syncedGameTime);
            LOGGER.info("[DomainClashDebug][ClientSync] applied authoritative clear syncedTick={}", syncedGameTime);
            return;
        }
        if (opponents == null || opponents.isEmpty()) {
            LOGGER.warn("[DomainClashDebug][ClientSync] ignored active packet without opponents syncedTick={} cacheWasActive={}",
                    syncedGameTime, cacheWasActive);
            ClientDomainClashCache.clear(syncedGameTime);
            return;
        }
        ClientDomainClashCache.casterPower = Math.max(0.0f, casterPower);
        ClientDomainClashCache.casterDomainId = casterDomainId;
        ClientDomainClashCache.casterForm = casterForm;
        ClientDomainClashCache.casterName = casterName == null ? "" : casterName;
        ClientDomainClashCache.active = true;
        ClientDomainClashCache.syncedGameTime = syncedGameTime;
        ClientDomainClashCache.opponents.clear();
        for (ModNetworking.DomainClashOpponentPayload opponent : opponents) {
            if (opponent == null || opponent.domainId() <= 0 || isInvalidOpponentName(opponent.name())) {
                continue;
            }
            ClientDomainClashCache.opponents.add(new ClientDomainClashCache.OpponentCacheEntry(
                    opponent.power(), opponent.form(), opponent.domainId(), opponent.name()));
        }
        if (ClientDomainClashCache.opponents.isEmpty()) {
            LOGGER.warn("[DomainClashDebug][ClientSync] cleared active packet after opponent filter syncedTick={} cacheWasActive={}",
                    syncedGameTime, cacheWasActive);
            ClientDomainClashCache.clear(syncedGameTime);
            return;
        }
        LOGGER.info("[DomainClashDebug][ClientSync] applied active cacheOpponentCount={} syncedTick={}",
                ClientDomainClashCache.opponents.size(), ClientDomainClashCache.syncedGameTime);
    }

    private static boolean isInvalidOpponentName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("Caster")) {
            return true;
        }
        try {
            java.util.UUID.fromString(trimmed);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * Lightweight client cache for the most recent technique and combat cooldown sync values.
     */
    public static final class ClientCooldownCache {
        // Remaining ticks on the currently relevant technique cooldown.
        private static int techRemaining = 0;
        // Largest technique cooldown value seen for the current cooldown cycle.
        private static int techMax = 0;
        // Remaining ticks on the combat cooldown used by physical attacks and combat lockouts.
        private static int combatRemaining = 0;
        // Largest combat cooldown value seen for the current cooldown cycle.
        private static int combatMax = 0;

        /**
         * Creates a new client cooldown cache instance and initializes its addon state.
         */
        private ClientCooldownCache() {
        }

        /**
         * Updates technique using the latest available data.
         * @param remaining remaining used by this method.
         * @param max max used by this method.
         */
        public static void updateTechnique(int remaining, int max) {
            techRemaining = Math.max(0, remaining);
            techMax = Math.max(techRemaining, Math.max(0, max));
        }

        /**
         * Updates combat using the latest available data.
         * @param remaining remaining used by this method.
         * @param max max used by this method.
         */
        public static void updateCombat(int remaining, int max) {
            combatRemaining = Math.max(0, remaining);
            combatMax = Math.max(combatRemaining, Math.max(0, max));
        }

        /**
         * Returns remaining for the current addon state.
         * @param physical physical used by this method.
         * @return the resolved remaining.
         */
        public static int getRemaining(boolean physical) {
            return physical ? Math.max(0, combatRemaining) : Math.max(0, techRemaining);
        }

        /**
         * Returns max for the current addon state.
         * @param physical physical used by this method.
         * @return the resolved max.
         */
        public static int getMax(boolean physical) {
            return physical ? Math.max(0, combatMax) : Math.max(0, techMax);
        }

        /**
         * Advances decay by one tick.
         */
        public static void tickDecay() {
            if (techRemaining > 0 && --techRemaining <= 0) {
                techMax = 0;
            }
            if (combatRemaining > 0 && --combatRemaining <= 0) {
                combatMax = 0;
            }
        }
    }

    /**
     * Client cache for Black Flash chance, mastery status, and charge state used by overlays.
     */
    public static final class ClientBlackFlashCache {
        // Most recently synced Black Flash percentage for client rendering.
        public static float bfPercent = 0.0f;
        // Whether the client should treat Black Flash mastery as unlocked.
        public static boolean mastery = false;
        // Whether the Black Flash charge state is currently active for HUD effects.
        public static boolean charging = false;

        /**
         * Creates a new client black flash cache instance and initializes its addon state.
         */
        private ClientBlackFlashCache() {
        }
    }

    /**
     * Client cache for the near-death cooldown ring and unlock visibility state.
     */
    public static final class ClientNearDeathCdCache {
        // Remaining near-death cooldown ticks synced from the server.
        public static int cdRemaining = 0;
        // Maximum near-death cooldown duration used to normalize ring progress.
        public static int cdMax = 6000;
        // Whether the client should render near-death UI because RCT level 3 has been unlocked.
        public static boolean rctLevel3Unlocked = false;

        /**
         * Creates a new client near death cd cache instance and initializes its addon state.
         */
        private ClientNearDeathCdCache() {
        }
    }

    public static final class ClientDomainClashCache {
        private static final long ACTIVE_PACKET_TTL_TICKS = 30L;
        public static float casterPower = 0.0f;
        public static int casterDomainId = 0;
        public static int casterForm = 1;
        public static String casterName = "";
        public static boolean active = false;
        public static long syncedGameTime = Long.MIN_VALUE;
        public static final List<OpponentCacheEntry> opponents = new java.util.ArrayList<>();

        private ClientDomainClashCache() {
        }

        public static boolean isActive() {
            if (!active || opponents.isEmpty()) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && syncedGameTime != Long.MIN_VALUE) {
                long age = mc.level.getGameTime() - syncedGameTime;
                if (age > ACTIVE_PACKET_TTL_TICKS) {
                    clear(mc.level.getGameTime());
                    return false;
                }
            }
            return true;
        }

        public static void clear(long syncedGameTime) {
            casterPower = 0.0f;
            casterDomainId = 0;
            casterForm = 1;
            casterName = "";
            active = false;
            ClientDomainClashCache.syncedGameTime = syncedGameTime;
            opponents.clear();
        }

        public static final class OpponentCacheEntry {
            public final float power;
            public final int form;
            public final int domainId;
            public final String name;

            public OpponentCacheEntry(float power, int form, int domainId, String name) {
                this.power = Math.max(0.0f, power);
                this.form = form;
                this.domainId = domainId;
                this.name = name == null ? "" : name;
            }
        }
    }
}


