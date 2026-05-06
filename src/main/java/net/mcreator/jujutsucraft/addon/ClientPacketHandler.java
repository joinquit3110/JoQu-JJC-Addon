package net.mcreator.jujutsucraft.addon;

import java.util.List;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryScreen;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.SkillWheelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client-side packet sink and cache hub used by addon networking. Incoming sync packets update lightweight caches that GUI screens and HUD overlays can read without touching server logic.
 */
public final class ClientPacketHandler {
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
    public static void updateBlackFlash(float bfPercent, boolean mastery, boolean charging, long timingStartTick, float timingPeriodTicks, float timingRedStart, float timingRedSize, long timingNonce, int flow, int flowCooldown) {
        Minecraft mc = Minecraft.getInstance();
        long previousNonce = ClientBlackFlashCache.timingNonce;
        boolean wasAwaitingReleaseAck = ClientBlackFlashCache.awaitingReleaseAck;
        boolean sameLocalReleaseNonce = ClientBlackFlashCache.localReleaseResolved && ClientBlackFlashCache.localReleaseNonce == timingNonce;
        boolean localReleasedThisSession = sameLocalReleaseNonce;
        boolean newSession = charging && !localReleasedThisSession && (!ClientBlackFlashCache.charging || ClientBlackFlashCache.timingNonce != timingNonce);
        ClientBlackFlashCache.bfPercent = bfPercent;
        ClientBlackFlashCache.mastery = mastery;
        ClientBlackFlashCache.charging = charging && !localReleasedThisSession;
        ClientBlackFlashCache.timingStartTick = timingStartTick;
        ClientBlackFlashCache.timingPeriodTicks = Math.max(1.0f, timingPeriodTicks);
        ClientBlackFlashCache.timingRedStart = timingRedStart;
        ClientBlackFlashCache.timingRedSize = timingRedSize;
        ClientBlackFlashCache.timingNonce = timingNonce;
        ClientBlackFlashCache.flow = Math.max(0, Math.min(BlueRedPurpleNukeMod.BF_FLOW_MAX, flow));
        ClientBlackFlashCache.flowCooldown = Math.max(0, flowCooldown);
        if (newSession || ClientBlackFlashCache.clientTimingStartTick == Long.MIN_VALUE) {
            long clientNow = mc.level != null ? mc.level.getGameTime() : timingStartTick;
            // Render from the authoritative server start tick. Using packet-arrival/clientNow as phase zero makes the HUD needle lag the server by network latency.
            ClientBlackFlashCache.clientServerTickOffset = clientNow - timingStartTick;
            ClientBlackFlashCache.clientTimingStartTick = timingStartTick;
        }
        if (newSession) {
            ClientBlackFlashCache.awaitingReleaseAck = false;
            ClientBlackFlashCache.localReleaseResolved = false;
            ClientBlackFlashCache.localReleaseNonce = 0L;
            ClientBlackFlashCache.localReleaseNeedle = 0.0f;
        }
        if (!charging) {
            ClientBlackFlashCache.clientTimingStartTick = Long.MIN_VALUE;
            ClientBlackFlashCache.timingStartTick = 0L;
            ClientBlackFlashCache.timingRedStart = 0.0f;
            ClientBlackFlashCache.timingRedSize = 0.0f;
            if (sameLocalReleaseNonce || ClientBlackFlashCache.localReleaseResolved && ClientBlackFlashCache.localReleaseNonce != 0L) {
                // A charging=false sync with the same nonce is the server acknowledgement that the release
                // left the ring phase. Close the local RELEASED/ring state immediately; explicit feedback
                // packets may then show RELEASED, BLACK FLASH, or FAILED without leaving a pending timeout path.
                System.out.println("[BlackFlashTimingDiag] CLIENT_RELEASE_ACK_SYNC nonce=" + ClientBlackFlashCache.localReleaseNonce + " previousNonce=" + previousNonce);
                ClientBlackFlashCache.awaitingReleaseAck = false;
                ClientBlackFlashCache.localReleaseResolved = false;
                ClientBlackFlashCache.localReleaseNonce = 0L;
                ClientBlackFlashCache.localReleaseNeedle = 0.0f;
            } else {
                ClientBlackFlashCache.awaitingReleaseAck = false;
                ClientBlackFlashCache.localReleaseResolved = false;
                ClientBlackFlashCache.localReleaseNonce = 0L;
                ClientBlackFlashCache.localReleaseNeedle = 0.0f;
            }
        }
    }

    public static boolean markBlackFlashReleasedLocally(float needle, long timingNonce) {
        if (timingNonce == 0L || ClientBlackFlashCache.localReleaseResolved && ClientBlackFlashCache.localReleaseNonce == timingNonce) {
            return false;
        }
        ClientBlackFlashCache.localReleaseResolved = true;
        ClientBlackFlashCache.localReleaseNonce = timingNonce;
        ClientBlackFlashCache.localReleaseNeedle = frac(needle);
        ClientBlackFlashCache.localReleaseStartMs = System.currentTimeMillis();
        ClientBlackFlashCache.awaitingReleaseAck = true;
        ClientBlackFlashCache.charging = false;
        ClientBlackFlashCache.clientTimingStartTick = Long.MIN_VALUE;
        System.out.println("[BlackFlashTimingDiag] LOCAL_RELEASE nonce=" + timingNonce + " needle=" + ClientBlackFlashCache.localReleaseNeedle);
        showBlackFlashPendingFeedback();
        return true;
    }

    public static void showBlackFlashPendingFeedback() {
        applyBlackFlashFeedback(false, false, true);
    }

    public static void showBlackFlashFeedback(boolean success, boolean confirmedHit) {
        ClientBlackFlashCache.awaitingReleaseAck = false;
        applyBlackFlashFeedback(success, confirmedHit, false);
    }

    private static void applyBlackFlashFeedback(boolean success, boolean confirmedHit, boolean pending) {
        long now = System.currentTimeMillis();
        int feedbackType = pending ? 0 : (success ? (confirmedHit ? 3 : 2) : 1);
        boolean duplicateBurst = ClientBlackFlashCache.lastFeedbackType == feedbackType && now - ClientBlackFlashCache.lastFeedbackUpdateMs < ClientBlackFlashCache.FEEDBACK_DEBOUNCE_MS;
        boolean suppressWaitingTimedRefresh = feedbackType == 2 && ClientBlackFlashCache.lastFeedbackType == 2 && now - ClientBlackFlashCache.feedbackStartMs < ClientBlackFlashCache.TIMED_REFRESH_SUPPRESS_MS;
        ClientBlackFlashCache.feedbackSuccess = success;
        ClientBlackFlashCache.feedbackConfirmedHit = confirmedHit;
        ClientBlackFlashCache.feedbackPending = pending;
        ClientBlackFlashCache.lastFeedbackType = feedbackType;
        ClientBlackFlashCache.lastFeedbackUpdateMs = now;
        if (duplicateBurst || suppressWaitingTimedRefresh) {
            return;
        }
        ClientBlackFlashCache.feedbackStartMs = now;
        ClientBlackFlashCache.feedbackNonce++;
    }

    public static void failBlackFlashPendingFeedback() {
        if (ClientBlackFlashCache.awaitingReleaseAck || ClientBlackFlashCache.localReleaseResolved) {
            System.out.println("[BlackFlashTimingDiag] CLIENT_PENDING_TIMEOUT nonce=" + ClientBlackFlashCache.localReleaseNonce);
            showBlackFlashFeedback(false, false);
            ClientBlackFlashCache.awaitingReleaseAck = false;
            ClientBlackFlashCache.localReleaseResolved = false;
            ClientBlackFlashCache.localReleaseNonce = 0L;
            ClientBlackFlashCache.localReleaseNeedle = 0.0f;
        }
    }

    public static float getBlackFlashClientNeedle(float partialTick) {
        if (ClientBlackFlashCache.localReleaseResolved && ClientBlackFlashCache.awaitingReleaseAck) {
            return ClientBlackFlashCache.localReleaseNeedle;
        }
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        long startTick = ClientBlackFlashCache.clientTimingStartTick != Long.MIN_VALUE ? ClientBlackFlashCache.clientTimingStartTick : ClientBlackFlashCache.timingStartTick;
        return frac(((float)(gameTime - startTick) + partialTick) / Math.max(1.0f, ClientBlackFlashCache.timingPeriodTicks));
    }

    private static float frac(float value) {
        return value - (float)Math.floor(value);
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

    public static void spawnGojoTeleportGhost(double x, double y, double z, float yaw, int lifetime) {
        // Gojo teleport ghost renderer is optional in this build; server-side teleport, particles, and sound remain authoritative.
    }

    public static void updateDomainClash(float casterPower, int casterDomainId,
                                          int casterForm, String casterName,
                                          boolean active, long syncedGameTime,
                                          List<ModNetworking.DomainClashOpponentPayload> opponents) {
        ClientDomainClashCache.casterPower = Math.max(0.0f, casterPower);
        ClientDomainClashCache.casterDomainId = casterDomainId;
        ClientDomainClashCache.casterForm = casterForm;
        ClientDomainClashCache.casterName = casterName == null ? "" : casterName;
        ClientDomainClashCache.active = active;
        ClientDomainClashCache.syncedGameTime = syncedGameTime;
        ClientDomainClashCache.opponents.clear();
        if (opponents != null) {
            for (ModNetworking.DomainClashOpponentPayload opponent : opponents) {
                if (opponent == null) {
                    continue;
                }
                ClientDomainClashCache.opponents.add(new ClientDomainClashCache.OpponentCacheEntry(
                        opponent.power(), opponent.form(), opponent.domainId(), opponent.name()));
            }
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
            if (ClientBlackFlashCache.flowCooldown > 0) {
                ClientBlackFlashCache.flowCooldown--;
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
        // Server game tick when the current timing ring started.
        public static long timingStartTick = 0L;
        // Client game tick when the HUD received the current timing session, used as the rendered phase anchor.
        public static long clientTimingStartTick = Long.MIN_VALUE;
        // Difference between local client game time and the synced server timing start; render math uses this bridge but never packet-arrival time as phase zero.
        public static long clientServerTickOffset = 0L;
        // Server-authoritative needle lap duration for the current timing session.
        public static float timingPeriodTicks = BlueRedPurpleNukeMod.BF_TIMING_PERIOD_TICKS;
        // Randomized red target arc start, normalized to the 0..1 ring interval.
        public static float timingRedStart = 0.0f;
        // Randomized red target arc size, normalized to the 0..1 ring interval.
        public static float timingRedSize = 0.0f;
        // Monotonic session id from the server; release packets echo it to reject stale sessions.
        public static long timingNonce = 0L;
        public static final long FEEDBACK_DEBOUNCE_MS = 220L;
        public static final long TIMED_REFRESH_SUPPRESS_MS = 1400L;
        public static boolean feedbackSuccess = false;
        public static boolean feedbackConfirmedHit = false;
        public static boolean feedbackPending = false;
        public static long feedbackStartMs = 0L;
        public static int feedbackNonce = 0;
        public static int lastFeedbackType = -1;
        public static long lastFeedbackUpdateMs = 0L;
        public static boolean localReleaseResolved = false;
        public static long localReleaseNonce = 0L;
        public static float localReleaseNeedle = 0.0f;
        public static long localReleaseStartMs = 0L;
        public static boolean awaitingReleaseAck = false;
        public static int flow = 0;
        public static int flowCooldown = 0;

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
        private static final long STALE_TICK_WINDOW = 40L;
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
            Minecraft mc = Minecraft.getInstance();
            if (!active || mc.level == null) {
                return false;
            }
            if (syncedGameTime == Long.MIN_VALUE) {
                return false;
            }
            long age = mc.level.getGameTime() - syncedGameTime;
            return age >= 0L && age <= STALE_TICK_WINDOW && !opponents.isEmpty();
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

