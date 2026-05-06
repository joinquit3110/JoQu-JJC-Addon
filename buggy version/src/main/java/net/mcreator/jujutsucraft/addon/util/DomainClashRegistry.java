package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.DomainBarrierCleanup;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Server-side singleton that is the authoritative source of truth for all
 * active domain clashes.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #registerDomain} — called at domain startup from
 *       {@code DomainOpenClashCancelMixin}; buffers into
 *       {@link #PENDING_REGISTRATIONS}</li>
 *   <li>{@link #tick} — called once per server tick; flushes pending
 *       registrations, creates pairwise {@link ClashSession} objects for
 *       every overlapping pair, and writes NBT bridge keys</li>
 *   <li>{@link #unregisterDomain} — called when a domain expires, is
 *       defeated, or the entity is removed; resolves related sessions</li>
 *   <li>{@link #clear} — called on server stop / dimension unload</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is <b>not</b> thread-safe by design; all callers are
 * expected to operate on the server thread.</p>
 *
 * <h3>Legacy Compatibility</h3>
 * <p>The registry writes legacy NBT keys back to entity persistent data
 * so that downstream systems (clash penalty, XP, HUD packets) that have
 * not yet been migrated to read from the registry can continue to
 * function.  This is the "additive bridge" strategy described in the
 * refactor plan.</p>
 */
public final class DomainClashRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_RESOLVED_SESSION_AGE_TICKS = 200L;
    private static final boolean ENABLE_REGISTRY_PHYSICAL_RESOLUTION = true;

    // ==================== Storage ====================

    /** Active domain entries keyed by caster UUID. */
    private static final Map<UUID, DomainEntry> DOMAIN_ENTRIES = new HashMap<>();

    /** Active pairwise clash sessions. */
    private static final List<ClashSession> ACTIVE_SESSIONS = new ArrayList<>();

    /**
     * Pending registrations buffered within a single tick.
     * Flushed at the start of each {@link #tick} call to handle
     * near-simultaneous startups.
     */
    private static final List<DomainEntry> PENDING_REGISTRATIONS = new ArrayList<>();

    /** Players that received an active registry HUD packet on the previous sync pass. */
    private static final Set<UUID> LAST_HUD_SYNC_PLAYERS = new HashSet<>();

    /** Recently resolved canonical UUID pairs and their cooldown expiry tick. */
    private static final Map<String, Long> RECENTLY_RESOLVED_PAIRS = new HashMap<>();

    /**
     * Reference to the current server level, set during tick.
     * Used for entity lookups when writing NBT bridges.
     */
    @Nullable
    private static ServerLevel cachedServerLevel = null;

    private DomainClashRegistry() {
        // Static utility — no instantiation.
    }

    // ==================== Lifecycle Methods ====================

    /**
     * Registers a new domain in the registry.
     *
     * <p>Creates a {@link DomainEntry} from the caster's current state and
     * buffers it in {@link #PENDING_REGISTRATIONS}.  The actual overlap
     * detection and session creation happens in the next {@link #tick} call,
     * which ensures near-simultaneous startups are handled correctly.</p>
     *
     * @param caster the living entity casting the domain
     * @param form   the domain form at cast time
     * @param center the domain center position
     * @param radius the actual addon-modified radius
     * @param world  the level accessor for map-variable lookups
     */
    public static void registerDomain(LivingEntity caster, DomainForm form, Vec3 center,
                                       double radius, LevelAccessor world) {
        if (!DomainClashConstants.USE_REGISTRY) {
            return;
        }
        if (caster == null || world == null) {
            return;
        }

        // Clear stale defeat flags only when the previous clash result cooldown is over.
        // During resolved cooldown, preserving Failed/DomainDefeated prevents an immediate
        // winner/loser recast loop from stale OG state.
        CompoundTag nbt = caster.getPersistentData();
        long registerTick = world instanceof ServerLevel registerLevel ? registerLevel.getGameTime() : -1L;
        boolean inResolvedCooldown = registerTick >= 0L && nbt.getLong("jjkbrp_clash_resolved_until") > registerTick;
        if (!inResolvedCooldown) {
            nbt.remove("Failed");
            nbt.remove("DomainDefeated");
            nbt.remove("jjkbrp_was_failed");
            nbt.remove("jjkbrp_was_domain_defeated");
        }

        UUID casterUUID = caster.getUUID();

        // If already registered, just update existing entry instead of buffering a duplicate.
        DomainEntry existing = DOMAIN_ENTRIES.get(casterUUID);
        if (existing != null) {
            if (!inResolvedCooldown) {
                nbt.remove("Failed");
                nbt.remove("DomainDefeated");
                nbt.remove("jjkbrp_was_failed");
                nbt.remove("jjkbrp_was_domain_defeated");
                existing.setDefeated(false);
            }
            existing.setForm(form);
            existing.setCenter(center);
            existing.setBodyPosition(getEntityBodyPosition(caster));
            existing.setRadius(radius);
            if (world instanceof ServerLevel sl) {
                existing.setDimensionId(sl.dimension().location().toString());
            }
            LOGGER.debug("[DomainClashRegistry] Updated existing entry for {} form={}",
                    caster.getName().getString(), form);
            return;
        }

        // Build snapshot, then convert to mutable entry
        long startTick = 0L;
        if (world instanceof ServerLevel sl) {
            startTick = sl.getGameTime();
            cachedServerLevel = sl;
        }

        DomainParticipantSnapshot snapshot = DomainAddonUtils.createParticipantSnapshot(
                caster, world, startTick);
        if (snapshot == null) {
            return;
        }

        // Override form/center/radius with the explicit values passed in,
        // since they may be more accurate than what the snapshot resolved.
        DomainEntry entry = new DomainEntry(snapshot);
        entry.setForm(form);
        entry.setCenter(center);
        entry.setBodyPosition(getEntityBodyPosition(caster));
        entry.setRadius(radius);
        if (world instanceof ServerLevel sl2) {
            entry.setDimensionId(sl2.dimension().location().toString());
        }

        if (world instanceof ServerLevel runtimeLevel) {
            DomainRuntimeManager.updateFromEntity(caster, world, runtimeLevel.getGameTime(), "registry-register");
        }

        PENDING_REGISTRATIONS.add(entry);
        LOGGER.info("[DomainClashRegistry] Buffered pending registration for {} form={} radius={} center={}",
                caster.getName().getString(), form, String.format("%.1f", radius), center);
    }

    /**
     * Removes a domain from the registry and resolves any sessions it
     * participated in as {@link ClashOutcome#EXPIRED}.
     *
     * @param casterUUID the UUID of the entity whose domain is being removed
     */
    public static void unregisterDomain(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY) {
            return;
        }
        if (casterUUID == null) {
            return;
        }

        long currentTick = cachedServerLevel != null ? cachedServerLevel.getGameTime() : -1L;
        if (cachedServerLevel != null && DOMAIN_ENTRIES.containsKey(casterUUID)) {
            evaluateSessionOutcomes(currentTick, cachedServerLevel);
        }

        DomainEntry removed = DOMAIN_ENTRIES.remove(casterUUID);
        if (removed != null) {
            LOGGER.debug("[DomainClashRegistry] Unregistered domain for {}", casterUUID);
        }
        DomainRuntimeManager.unregister(casterUUID);

        // Also remove from pending in case it hasn't been flushed yet
        PENDING_REGISTRATIONS.removeIf(e -> casterUUID.equals(e.getCasterUUID()));

        // Resolve only the sessions that are still genuinely unresolved after
        // one last outcome evaluation pass while the entry was still present.
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() && session.involves(casterUUID)) {
                session.resolve(ClashOutcome.EXPIRED, currentTick);
                LOGGER.debug("[DomainClashRegistry] Session {} expired due to unregister of {}",
                        session.getSessionId(), casterUUID);
            }
        }

        // Clear registry-managed bridge flags on the live entity immediately
        // so stale single-target markers do not linger after unregister.
        if (cachedServerLevel != null) {
            LivingEntity liveEntity = resolveEntity(cachedServerLevel, casterUUID,
                    removed != null ? removed.getDimensionId() : null);
            if (liveEntity != null) {
                clearRegistryManagedBridgeKeys(liveEntity.getPersistentData());
            }
        }

        // Keep resolved sessions alive until both participants have consumed them.
        ACTIVE_SESSIONS.removeIf(session -> session.isResolved() && session.isFullyDelivered());
    }

    /**
     * Per-server-tick update.  Flushes pending registrations, detects
     * pairwise overlaps, creates sessions, and writes NBT bridge keys.
     *
     * @param currentTick the current server game time
     * @param serverLevel the server level for entity lookups
     */
    public static void tick(long currentTick, @Nullable ServerLevel serverLevel) {
        if (!DomainClashConstants.USE_REGISTRY) {
            return;
        }
        if (serverLevel != null) {
            cachedServerLevel = serverLevel;
        }

        // 1. Scanner catches OG active/pending domain runtime from players and NPCs.
        scanOgLikeRuntimeDomains(currentTick, serverLevel);

        // 2. Flush pending registrations and detect startup overlaps.
        if (!PENDING_REGISTRATIONS.isEmpty()) {
            flushPendingRegistrations(currentTick);
        }

        // 3. Rescan active registered entries so moving domains can start clashing later.
        scanNewOverlaps(currentTick, serverLevel);

        // 4. Apply unified pressure based on power comparison.
        if (!ACTIVE_SESSIONS.isEmpty()) {
            LOGGER.info("[DomainClashRegistry] tick: entries={} sessions={} (unresolved={}) tick={}",
                    DOMAIN_ENTRIES.size(), ACTIVE_SESSIONS.size(), getActiveSessionCount(), currentTick);
        }
        applyPressureToSessions(currentTick, serverLevel);

        // 5. Resolve pending tie / win / lose state before any stale-domain cleanup.
        evaluateSessionOutcomes(currentTick, serverLevel);

        // 6. Write NBT bridge keys for legacy compatibility and pressure delivery.
        //    This must happen before cleanup so that defeated domains still have their
        //    entries available to receive final pressure and cleanup flags.
        if (serverLevel != null) {
            writeLegacyNbtBridge(serverLevel, currentTick);
        }

        // 7. Send registry-authored HUD snapshots per player perspective.
        syncHudForActiveSessions(currentTick, serverLevel);

        // 8. Drive addon-owned runtime lifecycle queues before stale cleanup.
        if (serverLevel != null) {
            DomainRuntimeManager.tick(serverLevel);
        }

        // 9. Update active session timestamps and clean up stale entries.
        cleanupStaleEntries(currentTick, serverLevel);
    }

    /**
     * Convenience overload for callers that don't have a ServerLevel ref.
     *
     * @param currentTick the current server game time
     */
    public static void tick(long currentTick) {
        tick(currentTick, cachedServerLevel);
    }

    /**
     * Clears all registry state.  Called on server stop or when all domains
     * should be force-expired (e.g. dimension unload).
     */
    public static void clear() {
        DOMAIN_ENTRIES.clear();
        ACTIVE_SESSIONS.clear();
        PENDING_REGISTRATIONS.clear();
        LAST_HUD_SYNC_PLAYERS.clear();
        RECENTLY_RESOLVED_PAIRS.clear();
        DomainRuntimeManager.clear();
        cachedServerLevel = null;
        LOGGER.debug("[DomainClashRegistry] Registry cleared");
    }

    // ==================== Query Methods ====================

    /**
     * Returns the domain entry snapshot for a given caster, if registered.
     *
     * @param casterUUID the UUID to look up
     * @return the participant snapshot, or {@code null} if not registered
     */
    @Nullable
    public static DomainParticipantSnapshot getEntry(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY || casterUUID == null) {
            return null;
        }
        DomainEntry entry = DOMAIN_ENTRIES.get(casterUUID);
        return entry != null ? entry.toSnapshot() : null;
    }

    /**
     * Returns the mutable domain entry for internal use.
     * Package-private so only registry internals can mutate state.
     */
    @Nullable
    static DomainEntry getEntryMutable(UUID casterUUID) {
        return DOMAIN_ENTRIES.get(casterUUID);
    }

    /**
     * Returns all active clash session snapshots involving the given caster.
     *
     * @param casterUUID the UUID to filter by
     * @return an unmodifiable list of session snapshots (empty if none)
     */
    public static List<ClashSessionSnapshot> getSessionsFor(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY || casterUUID == null) {
            return Collections.emptyList();
        }
        List<ClashSessionSnapshot> result = new ArrayList<>();
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() && session.involves(casterUUID)) {
                result.add(session.toSnapshot());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Claims all resolved sessions for one participant exactly once.
     *
     * <p>This is the Phase 3 bridge used by the XP / message layer. Resolved
     * sessions remain in the registry until both participants claim them, after
     * which stale cleanup may safely discard them.</p>
     *
     * @param casterUUID the participant consuming any resolved outcomes
     * @return immutable snapshots of resolved sessions newly claimed by that participant
     */
    public static List<ClashSessionSnapshot> claimResolvedSessions(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY || casterUUID == null) {
            return Collections.emptyList();
        }
        List<ClashSessionSnapshot> result = new ArrayList<>();
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() || !session.involves(casterUUID)) {
                continue;
            }
            if (session.claimResolvedDelivery(casterUUID)) {
                result.add(session.toSnapshot());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the number of active (unresolved) clash sessions involving
     * the given caster.
     */
    public static int getSessionCount(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY || casterUUID == null) {
            return 0;
        }
        int count = 0;
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() && session.involves(casterUUID)) {
                count++;
            }
        }
        return count;
    }

    /** Returns an unmodifiable view of all currently registered domain entries. */
    public static Map<UUID, DomainParticipantSnapshot> getAllEntries() {
        Map<UUID, DomainParticipantSnapshot> result = new HashMap<>();
        for (Map.Entry<UUID, DomainEntry> e : DOMAIN_ENTRIES.entrySet()) {
            result.put(e.getKey(), e.getValue().toSnapshot());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Returns snapshots of all active (unresolved) clash sessions. */
    public static List<ClashSessionSnapshot> getAllSessions() {
        List<ClashSessionSnapshot> result = new ArrayList<>();
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved()) {
                result.add(session.toSnapshot());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Checks whether the given caster currently has at least one active session. */
    public static boolean isInClash(UUID casterUUID) {
        return getSessionCount(casterUUID) > 0;
    }

    /** Checks whether the given caster is currently registered. */
    public static boolean isRegistered(UUID casterUUID) {
        if (!DomainClashConstants.USE_REGISTRY || casterUUID == null) {
            return false;
        }
        return DOMAIN_ENTRIES.containsKey(casterUUID);
    }

    /**
     * Returns the total number of registered domain entries.
     * Useful for diagnostics and tests.
     */
    public static int getRegisteredCount() {
        return DOMAIN_ENTRIES.size();
    }

    /**
     * Returns the total number of active (unresolved) sessions.
     */
    public static int getActiveSessionCount() {
        int count = 0;
        for (ClashSession s : ACTIVE_SESSIONS) {
            if (!s.isResolved()) count++;
        }
        return count;
    }

    // ==================== Internal: Pending Registration Flush ====================

    /**
     * Commits all pending registrations, then cross-checks each new entry
     * against all existing entries (including other entries from the same
     * flush batch) for pairwise overlap.
     */
    private static void flushPendingRegistrations(long currentTick) {
        List<DomainEntry> batch = new ArrayList<>(PENDING_REGISTRATIONS);
        PENDING_REGISTRATIONS.clear();
        LOGGER.info("[DomainClashRegistry] Flushing {} pending registrations, existing entries={} tick={}",
                batch.size(), DOMAIN_ENTRIES.size(), currentTick);

        for (DomainEntry newEntry : batch) {
            UUID newUUID = newEntry.getCasterUUID();
            refreshEntryGeometryFromLiveEntity(newEntry, cachedServerLevel, currentTick);
            if (!isValidLiveParticipant(newEntry, cachedServerLevel, currentTick, true)) {
                continue;
            }

            // Skip if already registered (e.g. double-fire)
            if (DOMAIN_ENTRIES.containsKey(newUUID)) {
                continue;
            }

            DOMAIN_ENTRIES.put(newUUID, newEntry);
            LOGGER.debug("[DomainClashRegistry] Committed entry for {} form={}",
                    newUUID, newEntry.getForm());

            // Check against ALL other registered entries for overlap
            for (Map.Entry<UUID, DomainEntry> existing : DOMAIN_ENTRIES.entrySet()) {
                UUID existingUUID = existing.getKey();
                if (existingUUID.equals(newUUID)) {
                    continue; // Don't clash with yourself
                }

                DomainEntry existingEntry = existing.getValue();
                refreshEntryGeometryFromLiveEntity(existingEntry, cachedServerLevel, currentTick);
                refreshEntryGeometryFromLiveEntity(newEntry, cachedServerLevel, currentTick);
                if (!isValidLiveParticipant(existingEntry, cachedServerLevel, currentTick, true)) {
                    continue;
                }
                if (!isSameDimension(newEntry, existingEntry)) {
                    continue; // Never clash across dimensions
                }

                // Check if a session already exists for this pair or was just resolved
                if (hasSessionForPair(newUUID, existingUUID) || isPairRecentlyResolved(newUUID, existingUUID, currentTick)) {
                    continue;
                }

                // Check spatial overlap
                boolean inRange = isWithinClashRange(newEntry, existingEntry);
                LOGGER.info("[DomainClashRegistry] Overlap check {} vs {} inRange={} radiusA={:.1f} radiusB={:.1f} clashRangeA={:.1f} clashRangeB={:.1f}",
                        newEntry.getCasterUUID(), existingUUID, inRange,
                        newEntry.getRadius(), existingEntry.getRadius(),
                        newEntry.computeClashRange(), existingEntry.computeClashRange());
                if (inRange) {
                    createSession(newEntry, existingEntry, currentTick);
                }
            }
        }
    }

    // ==================== Internal: Overlap Detection ====================

    /**
     * Determines whether two domain entries are spatially close enough to
     * trigger a clash session.
     *
     * <p>This mirrors the distance sampling used by
     * {@link DomainAddonUtils#isWithinBaseClashWindow}: compare center-to-center,
     * center-A to body-B, and center-B to body-A, then use the minimum.
     * The registry overlap window itself remains based on the combined shell radii.</p>
     */
    private static boolean isWithinClashRange(DomainEntry a, DomainEntry b) {
        Vec3 centerA = a.getCenter();
        Vec3 centerB = b.getCenter();
        Vec3 bodyA = a.getBodyPosition() != null ? a.getBodyPosition() : centerA;
        Vec3 bodyB = b.getBodyPosition() != null ? b.getBodyPosition() : centerB;

        double distCenterToCenterSq = centerA.distanceToSqr(centerB);
        double distCenterAToBodyBSq = centerA.distanceToSqr(bodyB);
        double distCenterBToBodyASq = centerB.distanceToSqr(bodyA);
        double minimumDistanceSq = Math.min(distCenterToCenterSq,
                Math.min(distCenterAToBodyBSq, distCenterBToBodyASq));

        double clashRange = a.computePracticalScanRange() + b.computePracticalScanRange();
        return minimumDistanceSq <= clashRange * clashRange;
    }

    private static void scanOgLikeRuntimeDomains(long currentTick, @Nullable ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : serverLevel.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                scanLivingCandidate(player, level, currentTick, seen);
                double radius = Math.max(160.0, DomainAddonUtils.getActualDomainRadius(level, player.getPersistentData()) * 9.0 + 64.0);
                AABB box = player.getBoundingBox().inflate(radius);
                for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isRemoved())) {
                    scanLivingCandidate(candidate, level, currentTick, seen);
                }
            }
        }
    }

    private static void scanLivingCandidate(LivingEntity entity, ServerLevel level, long currentTick, Set<UUID> seen) {
        if (entity == null || level == null || !seen.add(entity.getUUID())) {
            return;
        }
        if (isEntityInResolvedCooldown(entity, currentTick)) {
            return;
        }
        if (!DomainAddonUtils.hasOgLikeDomainClashRuntime(entity)) {
            return;
        }
        upsertScannerEntry(entity, level, currentTick);
    }

    private static void upsertScannerEntry(LivingEntity caster, ServerLevel level, long currentTick) {
        CompoundTag nbt = caster.getPersistentData();
        if (nbt.getLong("jjkbrp_clash_resolved_until") > currentTick) {
            return;
        }
        UUID uuid = caster.getUUID();
        DomainForm form = DomainAddonUtils.resolveOgLikeDomainForm(caster);
        Vec3 center = DomainAddonUtils.getOgLikeDomainCenter(caster);
        double radius = DomainAddonUtils.getActualDomainRadius(level, nbt);
        String dimensionId = level.dimension().location().toString();
        boolean defeated = false;
        DomainEntry existing = DOMAIN_ENTRIES.get(uuid);
        if (existing != null) {
            existing.setForm(form);
            existing.setCenter(center);
            existing.setBodyPosition(getEntityBodyPosition(caster));
            existing.setRadius(radius);
            existing.setDimensionId(dimensionId);
            existing.setBarrierRefinement(nbt.contains("jjkbrp_barrier_refinement") ? nbt.getDouble("jjkbrp_barrier_refinement") : existing.getBarrierRefinement());
            existing.setSureHitMultiplier(nbt.contains("jjkbrp_open_surehit_multiplier") ? nbt.getDouble("jjkbrp_open_surehit_multiplier") : existing.getSureHitMultiplier());
            if (!hasUnresolvedSessions(uuid)) { existing.setDefeated(isScannerLossTrusted(nbt, existing.getStartTick(), currentTick)); }
            return;
        }
        DomainParticipantSnapshot snapshot = DomainAddonUtils.createParticipantSnapshot(caster, level, currentTick);
        if (snapshot == null) {
            return;
        }
        DomainEntry entry = new DomainEntry(snapshot);
        entry.setForm(form);
        entry.setCenter(center);
        entry.setBodyPosition(getEntityBodyPosition(caster));
        entry.setRadius(radius);
        entry.setDimensionId(dimensionId);
        entry.setDefeated(defeated);
        DOMAIN_ENTRIES.put(uuid, entry);
        LOGGER.debug("[DomainClashRegistry] scanner upserted OG-like domain caster={} form={} radius={} center={} defeated={}",
                caster.getName().getString(), form, String.format("%.1f", radius), center, defeated);
    }

    /**
     * Checks whether two entries belong to the same dimension.
     */
    private static boolean isSameDimension(DomainEntry a, DomainEntry b) {
        String dimA = a.getDimensionId();
        String dimB = b.getDimensionId();
        if (dimA == null || dimA.isBlank() || dimB == null || dimB.isBlank()) {
            return true;
        }
        return dimA.equals(dimB);
    }

    /**
     * Checks whether a session already exists for the given pair of UUIDs.
     */
    private static boolean hasSessionForPair(UUID a, UUID b) {
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() && session.involves(a) && session.involves(b)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalPairKey(UUID a, UUID b) {
        String first = a == null ? "" : a.toString();
        String second = b == null ? "" : b.toString();
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private static boolean isPairRecentlyResolved(UUID a, UUID b, long currentTick) {
        String key = canonicalPairKey(a, b);
        Long expireTick = RECENTLY_RESOLVED_PAIRS.get(key);
        if (expireTick == null) {
            return false;
        }
        if (currentTick >= expireTick) {
            RECENTLY_RESOLVED_PAIRS.remove(key);
            return false;
        }
        return true;
    }

    private static boolean isEntityInResolvedCooldown(LivingEntity entity, long currentTick) {
        return entity != null && entity.getPersistentData().getLong("jjkbrp_clash_resolved_until") > currentTick;
    }

    private static void markRecentlyResolved(ClashSession session, long currentTick) {
        if (session == null) {
            return;
        }
        String pairKey = canonicalPairKey(session.getParticipantA(), session.getParticipantB());
        long expireTick = currentTick + DomainClashConstants.RESULT_COOLDOWN_TICKS;
        RECENTLY_RESOLVED_PAIRS.put(pairKey, expireTick);
        writeResolvedCooldown(session.getParticipantA(), session.getParticipantB(), session.getSessionId().toString(), pairKey, expireTick);
        writeResolvedCooldown(session.getParticipantB(), session.getParticipantA(), session.getSessionId().toString(), pairKey, expireTick);
    }

    private static void writeResolvedCooldown(UUID participant, UUID opponent, String sessionId, String pairKey, long expireTick) {
        if (cachedServerLevel == null || participant == null) {
            return;
        }
        DomainEntry entry = DOMAIN_ENTRIES.get(participant);
        LivingEntity entity = resolveEntity(cachedServerLevel, participant, entry != null ? entry.getDimensionId() : null);
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.putLong("jjkbrp_clash_resolved_until", expireTick);
        nbt.putString("jjkbrp_last_resolved_clash_pair", pairKey);
        if (opponent != null) {
            nbt.putString("jjkbrp_last_resolved_clash_opponent_uuid", opponent.toString());
        }
    }

    private static void cleanupResolvedPairEntries(ClashSession session, boolean removeA, boolean removeB) {
        if (session == null) {
            return;
        }
        if (removeA) {
            DOMAIN_ENTRIES.remove(session.getParticipantA());
            PENDING_REGISTRATIONS.removeIf(e -> session.getParticipantA().equals(e.getCasterUUID()));
        } else {
            DomainEntry entryA = DOMAIN_ENTRIES.get(session.getParticipantA());
            if (entryA != null) entryA.setDefeated(false);
        }
        if (removeB) {
            DOMAIN_ENTRIES.remove(session.getParticipantB());
            PENDING_REGISTRATIONS.removeIf(e -> session.getParticipantB().equals(e.getCasterUUID()));
        } else {
            DomainEntry entryB = DOMAIN_ENTRIES.get(session.getParticipantB());
            if (entryB != null) entryB.setDefeated(false);
        }
    }

    /**
     * Rescans all active registered pairs so domains that move into overlap after
     * startup still receive a clash session.
     */
    private static void scanNewOverlaps(long currentTick, @Nullable ServerLevel serverLevel) {
        if (DOMAIN_ENTRIES.size() < 2) {
            return;
        }

        List<DomainEntry> entries = new ArrayList<>(DOMAIN_ENTRIES.values());
        int overlapChecks = 0;
        int overlapsFound = 0;
        for (int i = 0; i < entries.size(); i++) {
            DomainEntry entryA = entries.get(i);
            refreshEntryGeometryFromLiveEntity(entryA, serverLevel, currentTick);
            if (!isValidLiveParticipant(entryA, serverLevel, currentTick, true)) {
                continue;
            }

            for (int j = i + 1; j < entries.size(); j++) {
                DomainEntry entryB = entries.get(j);
                overlapChecks++;
                refreshEntryGeometryFromLiveEntity(entryB, serverLevel, currentTick);
                if (!isValidLiveParticipant(entryB, serverLevel, currentTick, true)) {
                    continue;
                }
                if (!isSameDimension(entryA, entryB)) {
                    continue;
                }
                if (hasSessionForPair(entryA.getCasterUUID(), entryB.getCasterUUID())
                        || isPairRecentlyResolved(entryA.getCasterUUID(), entryB.getCasterUUID(), currentTick)
                        || isWinnerSessionSuppressed(entryA, currentTick)
                        || isWinnerSessionSuppressed(entryB, currentTick)) {
                    continue;
                }
                if (isWithinClashRange(entryA, entryB)) {
                    overlapsFound++;
                    createSession(entryA, entryB, currentTick);
                }
            }
        }
        if (overlapChecks > 0) {
            LOGGER.debug("[DomainClashRegistry] scanNewOverlaps: checks={} overlapsFound={} entries={} tick={}",
                    overlapChecks, overlapsFound, entries.size(), currentTick);
        }
    }

    /**
     * Computes and applies pressure for all active clash sessions using the
     * unified power-based system. This replaces the erosion/wrap mechanics.
     *
     * <p>For each unresolved session:
     * <ol>
     *   <li>Calculate effective power for both participants using OG formula</li>
     *   <li>Compute power difference, subtract tie threshold</li>
     *   <li>Apply pressure to the weaker side</li>
     *   <li>Write unified NBT bridge keys for the penalty mixin to consume</li>
     * </ol></p>
     */
    private static void applyPressureToSessions(long currentTick, @Nullable ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }

        for (ClashSession session : ACTIVE_SESSIONS) {
            if (session.isResolved()) {
                continue;
            }

            // Reset per-tick pressure accumulators
            session.setPressureThisTickA(0.0);
            session.setPressureThisTickB(0.0);

            DomainEntry entryA = DOMAIN_ENTRIES.get(session.getParticipantA());
            DomainEntry entryB = DOMAIN_ENTRIES.get(session.getParticipantB());
            if (entryA == null || entryB == null) {
                continue;
            }

            LivingEntity entityA = resolveEntity(serverLevel, session.getParticipantA(), entryA.getDimensionId());
            LivingEntity entityB = resolveEntity(serverLevel, session.getParticipantB(), entryB.getDimensionId());
            if (entityA == null || entityB == null) {
                continue;
            }

            CompoundTag nbtA = entityA.getPersistentData();
            CompoundTag nbtB = entityB.getPersistentData();

            // Calculate effective power for both participants
            double powerA = computeEffectivePower(entityA, entryA, nbtA, session.getFormB(), currentTick);
            double powerB = computeEffectivePower(entityB, entryB, nbtB, session.getFormA(), currentTick);

            // Update entry power for UI/HUD
            entryA.setEffectivePower(powerA);
            entryB.setEffectivePower(powerB);

            // Determine who is weaker (higher power wins)
            double powerDiff = powerA - powerB;
            UUID weakerUuid;
            double weakerPower;
            double strongerPower;
            DomainEntry weakerEntry;
            DomainEntry strongerEntry;
            LivingEntity weakerEntity;
            LivingEntity strongerEntity;
            if (Math.abs(powerDiff) < DomainClashConstants.POWER_TIE_THRESHOLD) {
                // Tie - no pressure this tick
                continue;
            } else if (powerDiff > 0) {
                // A is stronger, B is weaker
                weakerUuid = session.getParticipantB();
                weakerPower = powerB;
                strongerPower = powerA;
                weakerEntry = entryB;
                strongerEntry = entryA;
                weakerEntity = entityB;
                strongerEntity = entityA;
            } else {
                // B is stronger, A is weaker
                weakerUuid = session.getParticipantA();
                weakerPower = powerA;
                strongerPower = powerB;
                weakerEntry = entryA;
                strongerEntry = entryB;
                weakerEntity = entityA;
                strongerEntity = entityB;
            }

            // Compute base pressure: (stronger - weaker - threshold) * rate
            double rawPressure = (strongerPower - weakerPower - DomainClashConstants.POWER_TIE_THRESHOLD) * DomainClashConstants.PRESSURE_RATE;
            double pressure = Math.min(Math.max(0.0, rawPressure), DomainClashConstants.MAX_PRESSURE_PER_TICK);

            // Apply asymmetric position modifier (position-based pressure scaling)
            if (pressure > 0 && strongerEntity != null && weakerEntity != null && strongerEntry != null) {
                double positionMultiplier = computePositionPressureMultiplier(strongerEntity, strongerEntry, weakerEntity);
                pressure *= positionMultiplier;
            }

            // Apply pressure to the weaker participant
            if (pressure > 0) {
                session.addPressureTo(weakerUuid, pressure);
            }

            // Debug logging
            if (pressure > 0.01) {
                LOGGER.debug("[DomainClashRegistry] session={} powerA={} powerB={} rawPressure={} pressure={} accumulatedA={} accumulatedB={} weaker={}",
                        session.getSessionId(), String.format("%.1f", powerA), String.format("%.1f", powerB),
                        String.format("%.3f", rawPressure), String.format("%.3f", pressure),
                        String.format("%.3f", session.getAccumulatedPressureA()),
                        String.format("%.3f", session.getAccumulatedPressureB()), weakerUuid);
            }
        }
    }

    /**
     * Computes effective clash power for a participant.
     *
     * <p>Formula:
     * <pre>
     * strength = (WitherLevel + 10) * domainMultipliers
     * healthRatio = (maxHP - totalDamage*2) / maxHP  (clamped 0-1)
     * durationFactor = clamp(min(age,1200)/2400 + 0.5, 0.5, 1.0)
     * basePower = strength * healthRatio * durationFactor
     *
     * effectivePower = basePower
     *     * formMultiplier (Incomplete=0.85, Closed=1.0, Open=1.15)
     *     * radiusMultiplier = sqrt(REFERENCE_RADIUS / actualRadius)
     *     * barrierResistanceMultiplier (if NOT open AND opponent IS open)
     *         = 1.0 + barrierRefinement * 5.0
     * </pre></p>
     *
     * @param entity the living entity
     * @param entry the domain entry with cached state
     * @param nbt the entity's persistent data
     * @param opponentForm the opponent's domain form (for barrier resistance)
     * @param currentTick current game time
     * @return the computed effective power
     */
    private static double computeEffectivePower(LivingEntity entity, DomainEntry entry, CompoundTag nbt,
                                                DomainForm opponentForm, long currentTick) {
        if (entity == null || entry == null || nbt == null) {
            return 0.0;
        }

        // 1. Base strength: Strength (DAMAGE_BOOST) amplifier + 10
        //    Matches the PenaltyMixin formula. Previously this incorrectly used
        //    Wither level which most entities don't have.
        int strengthAmplifier = 0;
        if (entity.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)) {
            strengthAmplifier = entity.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST).getAmplifier();
        }
        double baseStrength = strengthAmplifier + 10;

        // Apply domain-specific multipliers (from OG)
        int domainId = entry.getDomainId();
        if (domainId == 29) { // Idle Death Gamble
            baseStrength *= 2.0;
        } else if (domainId == 27) { // Deadly Sentencing
            baseStrength *= 1.5;
        }

        // Apply 1.15x if domain effect amplifier > 0 (from OG)
        if (entity.hasEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            int amplifier = entity.getEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()).getAmplifier();
            if (amplifier > 0) {
                baseStrength *= 1.15;
            }
        }

        // 2. Health ratio: use totalDamage from NBT
        //    Cap maxHP at 40 (2x player HP) to prevent high-HP NPCs (200-300+)
        //    from being essentially immune to totalDamage degradation.
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0) maxHealth = 20.0f;
        float normalizedMaxHP = Math.min(maxHealth, DomainClashConstants.HEALTH_NORMALIZATION_CAP);
        float currentHealth = entity.getHealth();
        double totalDamage = nbt.getDouble("totalDamage");
        double effectiveMaxHP = Math.max(normalizedMaxHP - (float)totalDamage * 2.0f, 1.0f);
        double effectiveCurrentHP = Math.max(Math.min(currentHealth, normalizedMaxHP) - (float)totalDamage * 2.0f, 0.0f);
        double healthRatio = Math.min(1.0, Math.max(0.0, effectiveCurrentHP / effectiveMaxHP));

        // 3. Duration factor: clamp(min(tick, 1200)/2400 + 0.5, 0.5, 1.0)
        long clashAge = currentTick - entry.getStartTick();
        double durationFactor = Math.min(Math.min(clashAge, 1200L) / 2400.0 + 0.5, 1.0);
        if (durationFactor < 0.5) durationFactor = 0.5;

        double basePower = baseStrength * healthRatio * durationFactor;

        // 4. Form multiplier
        DomainForm form = entry.getForm();
        double formMultiplier = switch (form) {
            case INCOMPLETE -> DomainClashConstants.INCOMPLETE_FORM_MULTIPLIER;
            case CLOSED -> DomainClashConstants.CLOSED_FORM_MULTIPLIER;
            case OPEN -> DomainClashConstants.OPEN_FORM_MULTIPLIER;
        };

        // 5. Radius multiplier: sqrt(REFERENCE_RADIUS / actualRadius)
        double radius = Math.max(1.0, entry.getRadius());
        double radiusMultiplier = Math.sqrt(DomainClashConstants.REFERENCE_RADIUS / radius);
        radiusMultiplier = Math.max(0.5, Math.min(radiusMultiplier, 2.0));

        // 6. Barrier resistance multiplier
        double barrierResistanceMultiplier = 1.0;
        boolean isOpen = form == DomainForm.OPEN;
        boolean opponentIsOpen = opponentForm == DomainForm.OPEN;
        if (!isOpen) {
            double barrierRefinement = entry.getBarrierRefinement();
            if (opponentIsOpen) {
                // Incomplete or Closed vs Open gets massive boost from barrier refinement
                barrierResistanceMultiplier = 1.0 + barrierRefinement * 5.0;
            } else {
                // Non-open mirror clashes still benefit from refinement modestly.
                barrierResistanceMultiplier = 1.0 + barrierRefinement * 0.20;
            }
        }

        double effectivePower = basePower * formMultiplier * radiusMultiplier * barrierResistanceMultiplier;

        // 7. Clash power bonus: Player mastery scales the whole runtime power,
        // then CLASH_POWER remains a flat additive investment.
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            var capOpt = player.getCapability(net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve();
            if (capOpt.isPresent()) {
                net.mcreator.jujutsucraft.addon.DomainMasteryData data = capOpt.get();
                double pointMultiplier = data.getPowerPointMultiplier();
                effectivePower *= data.getMasteryClashMultiplier();
                effectivePower *= data.getClashRuntimeMultiplier();
                effectivePower += data.getClashPowerBonus() * pointMultiplier;
            }
        } else {
            // NPC: Dynamic power based on tier, health, cursed energy, and special domains
            effectivePower += computeNpcClashPower(entity, entry, nbt);
        }

        return effectivePower;
    }

    /**
     * Computes clash power for NPCs dynamically based on entity tier, max health,
     * cursed energy level, and special domain detection (Sukuna, Gojo, etc.).
     *
     * <p>Formula:
     * <pre>
     * basePower = tierPower + (maxHealth / 20.0) * 5 + cursedEnergyLevel * 3
     * specialDomainBonus = domainId == SUKUNA ? 25.0 : domainId == GOJO ? 20.0 : 0.0
     * finalPower = basePower + specialDomainBonus
     * </pre></p>
     *
     * @param entity the NPC entity
     * @param entry the domain entry with cached state
     * @param nbt the entity's persistent data
     * @return the computed NPC clash power
     */
    private static double computeNpcClashPower(LivingEntity entity, DomainEntry entry, CompoundTag nbt) {
        if (entity == null || entry == null) {
            return DomainClashConstants.NPC_BASELINE_CLASH_POWER;
        }

        // Determine entity tier from various sources
        double tierPower = DomainClashConstants.NPC_BASELINE_CLASH_POWER; // default fair NPC baseline
        double maxHealth = entity.getMaxHealth();
        double cursedEnergyLevel = 0.0;

        // Try to get cursed energy from entity NBT (some NPCs store it)
        if (nbt.contains("jjkbrp_cursed_energy_level")) {
            cursedEnergyLevel = nbt.getDouble("jjkbrp_cursed_energy_level");
        } else if (nbt.contains("cnt6")) {
            // cnt6 often represents CE pool for vanilla mobs with domain
            cursedEnergyLevel = nbt.getDouble("cnt6") / 100.0;
        } else if (entity instanceof net.minecraft.world.entity.monster.Monster monster) {
            // Rough estimate based on health for vanilla mobs
            cursedEnergyLevel = maxHealth / 5.0;
        }

        // Check for special domain IDs (Sukuna=1, Gojo=2 based on common mappings)
        int domainId = entry.getDomainId();
        double specialDomainBonus = 0.0;
        if (domainId == 1) { // Sukuna's Malevolent Shrine
            specialDomainBonus = 12.0;
            tierPower = 18.0; // Sukuna-tier, still dangerous but not overwhelming
        } else if (domainId == 2) { // Gojo's Unlimited Void
            specialDomainBonus = 10.0;
            tierPower = 18.0; // Gojo-tier
        } else if (domainId == 4 || domainId == 5) { // High-tier domains
            specialDomainBonus = 7.0;
            tierPower = 12.0;
        } else if (maxHealth > 100.0) {
            // High HP indicates special/enhanced entity, with strong diminishing returns.
            tierPower = 10.0 + Math.min(8.0, (maxHealth - 100.0) * 0.04);
        } else if (maxHealth > 40.0) {
            tierPower = 8.0 + (maxHealth - 40.0) * 0.03;
        }

        // Formula: tierPower + capped health term + capped CE term + tuned special bonus.
        double npcPower = tierPower +
                         (Math.min(maxHealth, 80.0) / 20.0) * 1.0 +
                         Math.min(cursedEnergyLevel, 10.0) * 0.5 +
                         specialDomainBonus;

        // Clamp to a tighter range so NPCs remain viable without crushing players.
        double cap = entry.getForm() == DomainForm.INCOMPLETE ? 20.0 : 35.0;
        String name = entity.getName().getString().toLowerCase(java.util.Locale.ROOT);
        if (entry.getForm() == DomainForm.INCOMPLETE && (name.contains("megumi") || name.contains("fushiguro"))) {
            cap = 18.0;
        }
        return Math.max(4.0, Math.min(cap, npcPower));
    }

    /**
     * Computes an asymmetric position-based pressure modifier based on the
     * spatial relationship between the weaker participant and the stronger
     * participant's domain.
     *
     * <p>The modifier reflects the lore-accurate principle that domains are
     * extremely strong against internal attacks but vulnerable to external
     * attacks. The effect scales with domain radius (smaller domains have
     * stronger positional asymmetry).</p>
     *
     * @param strongerEntity the entity with higher effective power
     * @param strongerEntry the domain entry of the stronger participant
     * @param weakerEntity the entity with lower effective power
     * @return position multiplier (typically 0.3-1.2 range)
     */
    private static double computePositionPressureMultiplier(LivingEntity strongerEntity, DomainEntry strongerEntry,
                                                            LivingEntity weakerEntity) {
        Vec3 strongerCenter = strongerEntry.getCenter();
        double strongerRadius = Math.max(DomainClashConstants.MIN_ASYMMETRIC_RADIUS, strongerEntry.getRadius());
        Vec3 weakerPos = getEntityBodyPosition(weakerEntity);

        // Distance from weaker entity to stronger domain center
        double dx = weakerPos.x() - strongerCenter.x;
        double dy = weakerPos.y() - strongerCenter.y;
        double dz = weakerPos.z() - strongerCenter.z;
        double distanceSq = dx*dx + dy*dy + dz*dz;

        // Check if weaker is inside the stronger's domain (with buffer)
        double thresholdSq = (strongerRadius + DomainClashConstants.DOMAIN_BOUNDARY_BUFFER) *
                             (strongerRadius + DomainClashConstants.DOMAIN_BOUNDARY_BUFFER);
        boolean isInside = distanceSq <= thresholdSq;

        // Radius scaling: smaller domains have stronger asymmetry
        double radiusScale = Math.sqrt(DomainClashConstants.ASYMMETRY_REFERENCE_RADIUS / strongerRadius);
        radiusScale = Math.max(0.5, Math.min(radiusScale, 2.0));

        DomainForm strongerForm = strongerEntry.getForm();

        if (isInside) {
            // Inside the domain: defensive advantage (reduced pressure)
            double baseMultiplier = (strongerForm == DomainForm.OPEN)
                    ? DomainClashConstants.INSIDE_DEFENSE_MULTIPLIER_OPEN
                    : DomainClashConstants.INSIDE_DEFENSE_MULTIPLIER_CLOSED;
            // Apply radius scaling: smaller domain = stronger reduction
            return baseMultiplier / radiusScale;
        } else {
            // Outside the domain: attacker advantage (increased pressure)
            return DomainClashConstants.OUTSIDE_ATTACK_MULTIPLIER * radiusScale;
        }
    }

    // ==================== Internal: Session Creation ====================

    /**
     * Creates a new {@link ClashSession} for a pair of overlapping domains.
     *
     * <p>The canonical ordering places the higher-form participant as A
     * when forms differ.  When forms are equal, the first-registered
     * entry is A.</p>
     *
     * <p>All 6 pairings are supported by {@link ClashType#derive}.</p>
     */
    private static void createSession(DomainEntry entryA, DomainEntry entryB, long currentTick) {
        if (entryA == null || entryB == null) {
            return;
        }
        if (entryA.getCasterUUID() == null || entryB.getCasterUUID() == null || entryA.getCasterUUID().equals(entryB.getCasterUUID())) {
            return;
        }
        refreshEntryGeometryFromLiveEntity(entryA, cachedServerLevel, currentTick);
        refreshEntryGeometryFromLiveEntity(entryB, cachedServerLevel, currentTick);
        if (cachedServerLevel != null
                && (!isValidLiveParticipant(entryA, cachedServerLevel, currentTick, true)
                || !isValidLiveParticipant(entryB, cachedServerLevel, currentTick, true))) {
            return;
        }
        if (isPairRecentlyResolved(entryA.getCasterUUID(), entryB.getCasterUUID(), currentTick)
                || isWinnerSessionSuppressed(entryA, currentTick)
                || isWinnerSessionSuppressed(entryB, currentTick)) {
            return;
        }
        DomainForm formA = entryA.getForm();
        DomainForm formB = entryB.getForm();

        // Canonical ordering: higher form ID goes first
        UUID participantA, participantB;
        DomainForm sessionFormA, sessionFormB;

        if (formA.getId() >= formB.getId()) {
            participantA = entryA.getCasterUUID();
            participantB = entryB.getCasterUUID();
            sessionFormA = formA;
            sessionFormB = formB;
        } else {
            participantA = entryB.getCasterUUID();
            participantB = entryA.getCasterUUID();
            sessionFormA = formB;
            sessionFormB = formA;
        }

        ClashSession session = new ClashSession(
                participantA, participantB,
                sessionFormA, sessionFormB,
                currentTick
        );

        ACTIVE_SESSIONS.add(session);
        LOGGER.info("[DomainClashRegistry] Created clash session {} type={} A={}({}) B={}({}) tick={}",
                session.getSessionId(), session.getSessionType(),
                participantA, sessionFormA, participantB, sessionFormB, currentTick);
    }

    // ==================== Internal: Outcome Evaluation ====================

    /**
     * Evaluates pending tie / win / lose state for every unresolved pairwise session.
     *
     * <p>Phase 3 keeps the original penalty system in place, but moves the actual
     * outcome truth into the registry so all callers share the same timing rules.
     * A session only resolves after an observed loss state and the unified
     * {@link DomainClashConstants#TIE_WINDOW_TICKS} has elapsed, unless both sides
     * are already down in the same evaluation window.</p>
     */
    private static void evaluateSessionOutcomes(long currentTick, @Nullable ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (session.isResolved()) {
                continue;
            }

            DomainEntry entryA = DOMAIN_ENTRIES.get(session.getParticipantA());
            DomainEntry entryB = DOMAIN_ENTRIES.get(session.getParticipantB());
            if (entryA == null || entryB == null) {
                LOGGER.debug("[DomainClashRegistry] Skip outcome eval: missing entry A={} B={}", entryA != null, entryB != null);
                continue;
            }

            LivingEntity entityA = resolveEntity(serverLevel, session.getParticipantA(), entryA.getDimensionId());
            LivingEntity entityB = resolveEntity(serverLevel, session.getParticipantB(), entryB.getDimensionId());
            if (entityA == null || entityB == null) {
                LOGGER.debug("[DomainClashRegistry] Skip outcome eval: missing entity A={} B={}", entityA != null, entityB != null);
                continue;
            }

            // Outcome single source of truth: only registry pressure decides win/loss.
            // Raw OG flags are teardown/intermediate signals and must not award results.
            boolean pressureLossA = session.getAccumulatedPressureA() >= DomainClashConstants.PRESSURE_LOSS_THRESHOLD;
            boolean pressureLossB = session.getAccumulatedPressureB() >= DomainClashConstants.PRESSURE_LOSS_THRESHOLD;
            long clashAge = currentTick - session.getStartTick();

            if (clashAge >= 0L && clashAge < DomainClashConstants.MIN_CLASH_DURATION_TICKS) {
                if (pressureLossA || pressureLossB) {
                    LOGGER.debug("[DomainClashRegistry] session={} delaying pressure outcome because clashAge={} < minDuration={} tick={}",
                            session.getSessionId(), clashAge, DomainClashConstants.MIN_CLASH_DURATION_TICKS, currentTick);
                }
                continue;
            }

            if (!pressureLossA && !pressureLossB) {
                if (session.hasPendingOutcome()) {
                    LOGGER.debug("[DomainClashRegistry] cleared pressure-only pending session={} tick={}",
                            session.getSessionId(), currentTick);
                }
                session.clearPending();
                continue;
            }

            if (pressureLossA && pressureLossB) {
                LOGGER.debug("[DomainClashRegistry] resolving session={} as TIE because both pressure thresholds crossed tick={}",
                        session.getSessionId(), currentTick);
                session.resolve(ClashOutcome.TIE, currentTick);
                executePhysicalResolution(session, serverLevel, currentTick);
                continue;
            }

            UUID loserUuid = pressureLossA ? session.getParticipantA() : session.getParticipantB();

            if (!session.hasPendingOutcome() || !loserUuid.equals(session.getPendingLoser())) {
                LOGGER.debug("[DomainClashRegistry] session={} starting pending loser={} clashAge={} tick={}",
                        session.getSessionId(), loserUuid, clashAge, currentTick);
                session.startPending(loserUuid, currentTick);
                continue;
            }

            long pendingAge = currentTick - session.getPendingTick();
            if (pendingAge > DomainClashConstants.TIE_WINDOW_TICKS) {
                ClashOutcome resolvedOutcome;
                if (loserUuid.equals(session.getParticipantA())) {
                    resolvedOutcome = ClashOutcome.B_WINS;
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as B_WINS after pendingAge={} tick={}",
                            session.getSessionId(), pendingAge, currentTick);
                } else {
                    resolvedOutcome = ClashOutcome.A_WINS;
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as A_WINS after pendingAge={} tick={}",
                            session.getSessionId(), pendingAge, currentTick);
                }
                session.resolve(resolvedOutcome, currentTick);
                // ATOMIC: apply physical collapse + barrier lock in the SAME tick
                executePhysicalResolution(session, serverLevel, currentTick);
            }
        }
    }

    // ==================== Internal: Atomic Physical Resolution ====================

    /**
     * Applies the physical consequences of a resolved clash session in the
     * SAME tick as the mathematical outcome.  This eliminates phantom
     * resolutions where the notification fires but the domain doesn't
     * actually collapse.
     *
     * <p>For decisive outcomes (A_WINS / B_WINS):
     * <ul>
     *   <li>Loser: {@code DomainDefeated=true}, {@code Failed=true}</li>
     *   <li>Winner: barrier-lock guard written for
     *       {@link DomainClashConstants#BARRIER_LOCK_DURATION_TICKS} so the
     *       global tick loop can't overwrite the form NBT.</li>
     *   <li>Winner restore/lock uses only the winner entry/snapshot; loser cleanup
     *       uses only the loser snapshot while protecting the winner snapshot.</li>
     * </ul></p>
     *
     * <p>For TIE: both sides get {@code DomainDefeated=true},
     * {@code Failed=true}.</p>
     *
     * <p><b>BONUS - Immediate Reward Distribution:</b> This method also awards
     * clash XP and sends chat notifications to both participants immediately upon
     * resolution to guarantee players see results even if their domain tick
     * procedure doesn't fire in the same tick.</p>
     */
    private static void executePhysicalResolution(ClashSession session, ServerLevel serverLevel, long currentTick) {
        if (session == null || serverLevel == null || session.isPhysicalResolved()) {
            LOGGER.info("[DomainClashRegistry] executePhysicalResolution skipped session={} serverLevelPresent={} physicalResolved={}",
                    session != null ? session.getSessionId() : "null", serverLevel != null,
                    session != null && session.isPhysicalResolved());
            return;
        }
        ClashOutcome outcome = session.getOutcome();
        if (outcome == ClashOutcome.PENDING || outcome == ClashOutcome.EXPIRED) {
            LOGGER.info("[DomainClashRegistry] executePhysicalResolution skipped session={} outcome={} pressureA={} pressureB={} pendingLoser={} tick={}",
                    session.getSessionId(), outcome,
                    String.format("%.3f", session.getAccumulatedPressureA()),
                    String.format("%.3f", session.getAccumulatedPressureB()),
                    session.getPendingLoser(), currentTick);
            return;
        }
        session.setPhysicalResolved(true);
        markRecentlyResolved(session, currentTick);

        DomainEntry entryA = DOMAIN_ENTRIES.get(session.getParticipantA());
        DomainEntry entryB = DOMAIN_ENTRIES.get(session.getParticipantB());
        LivingEntity entityA = resolveEntity(serverLevel, session.getParticipantA(), entryA != null ? entryA.getDimensionId() : null);
        LivingEntity entityB = resolveEntity(serverLevel, session.getParticipantB(), entryB != null ? entryB.getDimensionId() : null);

        grantClashXpAndNotify(entityA, entityB, outcome, currentTick, serverLevel, session);
        grantClashXpAndNotify(entityB, entityA, outcome, currentTick, serverLevel, session);

        if (!ENABLE_REGISTRY_PHYSICAL_RESOLUTION) {
            LOGGER.info("[DomainClashRegistry] metadata-only resolution session={} outcome={} tick={}",
                    session.getSessionId(), outcome, currentTick);
            return;
        }

        DomainResolutionCoordinator.resolve(serverLevel, session, entryA, entryB, entityA, entityB, currentTick);
        if (outcome == ClashOutcome.TIE) {
            cleanupResolvedPairEntries(session, true, true);
        } else {
            boolean aWins = outcome == ClashOutcome.A_WINS;
            boolean removeA = !aWins;
            boolean removeB = aWins;
            cleanupResolvedPairEntries(session, removeA, removeB);
            LivingEntity winner = aWins ? entityA : entityB;
            DomainForm winnerForm = aWins ? session.getFormA() : session.getFormB();
            if (winner != null) {
                lockWinnerBarrier(winner, winnerForm, currentTick);
            }
        }
        LOGGER.info("[DomainClashRegistry] coordinator physically resolved session={} outcome={} tick={}",
                session.getSessionId(), outcome, currentTick);
    }

    /**
     * Awards clash XP and sends chat notification immediately upon resolution.
     * This bypasses the tick-procedure-based XP grant to ensure both participants
     * receive their rewards even if their domain tick doesn't fire in the same tick.
     */
    private static void grantClashXpAndNotify(LivingEntity recipient, @Nullable LivingEntity opponent,
                                              ClashOutcome outcome, long currentTick, ServerLevel serverLevel,
                                              ClashSession session) {
        if (!(recipient instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag playerNbt = player.getPersistentData();
        String sessionId = session.getSessionId().toString();
        String pairKey = canonicalPairKey(session.getParticipantA(), session.getParticipantB());
        if (sessionId.equals(playerNbt.getString("jjkbrp_last_notified_clash_session"))) {
            return;
        }
        if (pairKey.equals(playerNbt.getString("jjkbrp_last_notified_clash_pair"))
                && playerNbt.getLong("jjkbrp_last_notified_clash_until") > currentTick) {
            return;
        }

        int xpAmount = switch (outcome) {
            case TIE -> DomainClashConstants.TIE_XP;
            case A_WINS, B_WINS -> {
                // Determine if this recipient is the winner
                boolean isWinner = (outcome == ClashOutcome.A_WINS && player.getUUID().equals(session.getParticipantA())) ||
                                  (outcome == ClashOutcome.B_WINS && player.getUUID().equals(session.getParticipantB()));
                yield isWinner ? DomainClashConstants.WINNER_XP : DomainClashConstants.LOSER_XP;
            }
            default -> 0;
        };

        if (xpAmount <= 0) {
            return;
        }

        boolean[] masteryMaxed = new boolean[] {false};
        boolean[] xpGranted = new boolean[] {false};
        player.getCapability(net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null)
                .ifPresent(data -> {
                    if (data.getDomainMasteryLevel() >= 5) {
                        masteryMaxed[0] = true;
                        data.syncToClient(player);
                        return;
                    }
                    data.addDomainXP(xpAmount);
                    data.syncToClient(player);
                    xpGranted[0] = true;
                });
        String xpText = masteryMaxed[0] ? "MAX" : (xpGranted[0] ? "+" + xpAmount + " XP" : "+0 XP");

        // Send chat notification
        String opponentName = resolveOpponentDisplayName(player.getUUID(), opponent, session, serverLevel);
        Component message;
        if (outcome == ClashOutcome.TIE) {
            message = Component.literal("⚠ ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("[Domain Clash] ").withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal("Tie against " + opponentName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  " + xpText).withStyle(masteryMaxed[0] ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        } else {
            boolean isWinner = (outcome == ClashOutcome.A_WINS && player.getUUID().equals(session.getParticipantA())) ||
                              (outcome == ClashOutcome.B_WINS && player.getUUID().equals(session.getParticipantB()));
            if (isWinner) {
                message = Component.literal("◆ ")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("[Domain Clash] ").withStyle(ChatFormatting.DARK_AQUA))
                        .append(Component.literal("Victory over " + opponentName).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("  " + xpText).withStyle(masteryMaxed[0] ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
            } else {
                message = Component.literal("◆ ")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("[Domain Clash] ").withStyle(ChatFormatting.DARK_AQUA))
                        .append(Component.literal("Defeat by " + opponentName).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  " + xpText).withStyle(masteryMaxed[0] ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
            }
        }

        playerNbt.putString("jjkbrp_last_notified_clash_session", sessionId);
        playerNbt.putString("jjkbrp_last_notified_clash_pair", pairKey);
        playerNbt.putLong("jjkbrp_last_notified_clash_until", currentTick + DomainClashConstants.RESULT_COOLDOWN_TICKS);
        player.displayClientMessage(message, false);
        LOGGER.debug("[DomainClashRegistry] sent clash outcome notification to {} outcome={} opponent={} XP={} tick={}",
                player.getName().getString(), outcome, opponentName, xpAmount, currentTick);
    }

    private static void notifyInconclusiveParticipant(UUID recipientUuid, UUID opponentUuid, ClashSession session,
                                                       long currentTick, ServerLevel serverLevel) {
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(recipientUuid);
        if (player == null) {
            return;
        }
        CompoundTag playerNbt = player.getPersistentData();
        String sessionId = session.getSessionId().toString();
        String pairKey = canonicalPairKey(session.getParticipantA(), session.getParticipantB());
        if (sessionId.equals(playerNbt.getString("jjkbrp_last_notified_clash_session"))) {
            return;
        }
        if (pairKey.equals(playerNbt.getString("jjkbrp_last_notified_clash_pair"))
                && playerNbt.getLong("jjkbrp_last_notified_clash_until") > currentTick) {
            return;
        }
        String opponentName = resolveDisplayName(opponentUuid, null, session, serverLevel);
        Component message = Component.literal("◇ ")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD)
                .append(Component.literal("[Domain Clash] ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("Clash ended inconclusively against " + opponentName).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  +0 XP").withStyle(ChatFormatting.DARK_GRAY));
        playerNbt.putString("jjkbrp_last_notified_clash_session", sessionId);
        playerNbt.putString("jjkbrp_last_notified_clash_pair", pairKey);
        playerNbt.putLong("jjkbrp_last_notified_clash_until", currentTick + DomainClashConstants.RESULT_COOLDOWN_TICKS);
        player.displayClientMessage(message, false);
    }

    /**
     * Forces the physical defeat of a domain caster by setting the NBT flags
     * that the base mod uses for domain collapse.
     */
    private static void forceDefeat(LivingEntity entity, long currentTick) {
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean("DomainDefeated", true);
        nbt.putBoolean("Failed", true);
        nbt.putLong("jjkbrp_clash_result_tick", currentTick);
        nbt.putLong("jjkbrp_clash_resolved_until", currentTick + DomainClashConstants.RESULT_COOLDOWN_TICKS);
        LOGGER.debug("[DomainClashRegistry] forceDefeat entity={} tick={}", entity.getName().getString(), currentTick);
    }

    private static void removeDomainExpansionEffect(LivingEntity entity) {
        if (entity == null || !entity.hasEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return;
        }
        entity.removeEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        LOGGER.debug("[DomainClashRegistry] removed DOMAIN_EXPANSION effect for defeated entity={}", entity.getName().getString());
    }

    private static void clearDefeatFlags(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean("Failed", false);
        nbt.putBoolean("DomainDefeated", false);
        nbt.putBoolean("Cover", false);
        nbt.remove("jjkbrp_was_failed");
        nbt.remove("jjkbrp_was_domain_defeated");
        nbt.remove("jjkbrp_pending_loss");
        nbt.remove("jjkbrp_pending_loser");
        nbt.remove("jjkbrp_clash_pending_loss");
        nbt.remove("jjkbrp_clash_pending_loser");
    }

    private static boolean isOwnedValidSnapshot(@Nullable DomainGeometrySnapshot snapshot, @Nullable UUID expectedOwner) {
        return snapshot != null && snapshot.isValid() && expectedOwner != null && snapshot.isSameOwner(expectedOwner);
    }

    private static void restoreWinnerDomainAfterClash(ServerLevel level, LivingEntity winner, @Nullable DomainEntry winnerEntry,
                                                      @Nullable DomainGeometrySnapshot winnerSnapshot,
                                                      DomainForm winnerForm, long currentTick) {
        if (level == null || winner == null) {
            return;
        }
        clearDefeatFlags(winner);
        CompoundTag nbt = winner.getPersistentData();
        boolean useSnapshot = isOwnedValidSnapshot(winnerSnapshot, winner.getUUID());
        Vec3 center = useSnapshot
                ? winnerSnapshot.getCenter()
                : (winnerEntry != null && winner.getUUID().equals(winnerEntry.getCasterUUID()) ? winnerEntry.getCenter() : DomainAddonUtils.getDomainCenter(winner));
        double radius = useSnapshot
                ? winnerSnapshot.getRadius()
                : (winnerEntry != null && winner.getUUID().equals(winnerEntry.getCasterUUID()) ? winnerEntry.getRadius() : DomainAddonUtils.getActualDomainRadius(level, nbt));
        if (center != null) {
            nbt.putDouble("x_pos_doma", center.x);
            nbt.putDouble("y_pos_doma", center.y);
            nbt.putDouble("z_pos_doma", center.z);
        }
        nbt.putDouble("cnt6", radius);
        nbt.putDouble("range", radius);
        DomainForm liveWinnerForm = resolveLiveWinnerFormForLock(winner, winnerForm);
        nbt.putLong("jjkbrp_clash_winner_session_suppress_until", currentTick + 200L);
        clearDefeatFlags(winner);
        if (!winner.hasEffect(JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) || center == null) {
            LOGGER.info("[DomainClashRegistry] skipped winner rebuild winner={} hasEffect={} centerPresent={} tick={}",
                    winner.getName().getString(), winner.hasEffect(JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()), center != null, currentTick);
            return;
        }
        try (DomainRadiusUtils.Scope scope = DomainRadiusUtils.pushRadius(level, radius, "clash-winner-rebuild")) {
            DomainExpansionBattleProcedure.execute(level, center.x, center.y, center.z, winner);
            clearDefeatFlags(winner);
        } catch (Exception ex) {
            LOGGER.warn("[DomainClashRegistry] winner rebuild failed winner={} radius={} center={} tick={}",
                    winner.getName().getString(), String.format("%.1f", radius), center, currentTick, ex);
        }
        LOGGER.info("[DomainClashRegistry] restored winner domain after clash winner={} form={} radius={} center={} tick={}",
                winner.getName().getString(), liveWinnerForm, String.format("%.1f", radius), center, currentTick);
    }

    private static String resolveOpponentDisplayName(UUID recipientUuid, @Nullable LivingEntity opponent,
                                                     ClashSession session, ServerLevel serverLevel) {
        UUID opponentUuid = session != null ? session.getOpponent(recipientUuid) : (opponent != null ? opponent.getUUID() : null);
        return resolveDisplayName(opponentUuid, opponent, session, serverLevel);
    }

    private static String resolveDisplayName(@Nullable UUID uuid, @Nullable LivingEntity liveEntity,
                                             @Nullable ClashSession session, @Nullable ServerLevel serverLevel) {
        if (liveEntity != null) {
            return liveEntity.getName().getString();
        }
        if (serverLevel != null && uuid != null) {
            LivingEntity resolved = resolveEntity(serverLevel, uuid, null);
            if (resolved != null) {
                return resolved.getName().getString();
            }
        }
        return "Opponent";
    }

    private static DomainForm resolveLiveWinnerFormForLock(LivingEntity winner, DomainForm sessionForm) {
        if (winner == null) {
            return sessionForm != null ? sessionForm : DomainForm.CLOSED;
        }
        CompoundTag nbt = winner.getPersistentData();
        DomainForm liveForm = null;
        if (nbt.contains("jjkbrp_domain_form_cast_locked")) {
            liveForm = DomainForm.fromId(nbt.getInt("jjkbrp_domain_form_cast_locked"));
        } else if (nbt.contains("jjkbrp_domain_form_effective")) {
            liveForm = DomainForm.fromId(nbt.getInt("jjkbrp_domain_form_effective"));
        } else {
            liveForm = DomainAddonUtils.resolveOgLikeDomainForm(winner);
        }
        if (liveForm == null) {
            liveForm = sessionForm != null ? sessionForm : DomainForm.CLOSED;
        }
        if (sessionForm != null && liveForm != sessionForm) {
            LOGGER.debug("[DomainClashRegistry] resolved live winner form mismatch winner={} sessionForm={} liveForm={}",
                    winner.getName().getString(), sessionForm, liveForm);
        }
        return liveForm;
    }

    /**
     * Locks the winner's barrier/form state so the global tick loop cannot
     * overwrite it for {@link DomainClashConstants#BARRIER_LOCK_DURATION_TICKS}.
     * Also applies form-specific preservation rules:
     * <ul>
     *   <li>OPEN winner → stays open</li>
     *   <li>CLOSED winner → keeps closed barrier</li>
     *   <li>INCOMPLETE winner → keeps its own center/radius/domain identity</li>
     *   <li>All others → lock to winner's original form</li>
     * </ul>
     */
    private static void lockWinnerBarrier(LivingEntity winner, DomainForm winnerForm, long currentTick) {
        CompoundTag winnerNbt = winner.getPersistentData();

        // Write the barrier lock guard
        winnerNbt.putBoolean("jjkbrp_clash_won_barrier_locked", true);
        winnerNbt.putLong("jjkbrp_clash_won_barrier_lock_tick", currentTick);
        winnerNbt.putInt("jjkbrp_clash_won_original_form", winnerForm.getId());

        // Preserve/rewrite the form identity keys
        winnerNbt.putInt("jjkbrp_domain_form_cast_locked", winnerForm.getId());
        winnerNbt.putInt("jjkbrp_domain_form_effective", winnerForm.getId());

        switch (winnerForm) {
            case OPEN -> {
                winnerNbt.putBoolean("jjkbrp_open_form_active", true);
                winnerNbt.remove("jjkbrp_incomplete_form_active");
                winnerNbt.remove("jjkbrp_incomplete_session_active");
            }
            case INCOMPLETE -> {
                winnerNbt.putBoolean("jjkbrp_incomplete_form_active", true);
                winnerNbt.remove("jjkbrp_incomplete_session_active");
                winnerNbt.putBoolean("DomainAttack", false);
                winnerNbt.remove("jjkbrp_open_form_active");
                // Do not copy loser center/radius into an incomplete winner.
                // Preserving the winner's own domain center avoids cleanup/protection drift.
                winnerNbt.remove("jjkbrp_adopted_closed_shell");
            }
            case CLOSED -> {
                winnerNbt.remove("jjkbrp_open_form_active");
                winnerNbt.remove("jjkbrp_incomplete_form_active");
                winnerNbt.remove("jjkbrp_incomplete_session_active");
            }
        }

        LOGGER.debug("[DomainClashRegistry] lockWinnerBarrier winner={} form={} tick={}",
                winner.getName().getString(), winnerForm, currentTick);
    }

    private static boolean isLossState(CompoundTag nbt) {
        if (nbt == null) {
            return false;
        }
        return nbt.getBoolean("Failed")
                || nbt.getBoolean("DomainDefeated");
    }

    private static boolean hasStaleLossBackup(CompoundTag nbt) {
        if (nbt == null) {
            return false;
        }
        return nbt.getBoolean("jjkbrp_was_failed")
                || nbt.getBoolean("jjkbrp_was_domain_defeated");
    }

    private static boolean isScannerLossTrusted(CompoundTag nbt, long startTick, long currentTick) {
        long age = currentTick - startTick;
        return age >= DomainClashConstants.LOSS_FLAG_GRACE_TICKS && isLossState(nbt);
    }

    private static void syncHudForActiveSessions(long currentTick, @Nullable ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        Set<UUID> syncedPlayers = new HashSet<>();
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (session.isResolved()) {
                long resolvedAge = currentTick - session.getResolvedTick();
                if (session.getResolvedTick() < 0L || resolvedAge < 0L || resolvedAge > DomainClashConstants.RESOLVED_HUD_GRACE_TICKS) {
                    continue;
                }
            }
            syncHudForParticipant(session.getParticipantA(), currentTick, serverLevel, syncedPlayers);
            syncHudForParticipant(session.getParticipantB(), currentTick, serverLevel, syncedPlayers);
        }
        for (UUID previous : new HashSet<>(LAST_HUD_SYNC_PLAYERS)) {
            if (!syncedPlayers.contains(previous)) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(previous);
                if (player != null) {
                    ModNetworking.sendDomainClashSync(player, 0.0f, 0, 1,
                            player.getName().getString(), false, currentTick, new ArrayList<>());
                }
            }
        }
        LAST_HUD_SYNC_PLAYERS.clear();
        LAST_HUD_SYNC_PLAYERS.addAll(syncedPlayers);
    }

    private static void syncHudForParticipant(UUID participant, long currentTick, ServerLevel serverLevel, Set<UUID> syncedPlayers) {
        if (participant == null || !syncedPlayers.add(participant)) {
            return;
        }
        DomainEntry entry = DOMAIN_ENTRIES.get(participant);
        if (entry == null) {
            return;
        }
        LivingEntity living = resolveEntity(serverLevel, participant, entry.getDimensionId());
        if (!(living instanceof ServerPlayer player)) {
            return;
        }
        DomainClashHudSnapshot snapshot = DomainClashHudSnapshot.fromRegistry(player, currentTick);
        if (snapshot == null || !snapshot.isActive() || snapshot.getOpponents().isEmpty()) {
            return;
        }
        List<ModNetworking.DomainClashOpponentPayload> payloads = new ArrayList<>();
        for (DomainClashHudSnapshot.OpponentSnapshot opponent : snapshot.getOpponents()) {
            payloads.add(new ModNetworking.DomainClashOpponentPayload(opponent.power(), opponent.form(), opponent.domainId(), opponent.name()));
        }
        ModNetworking.sendDomainClashSync(player, snapshot.getCasterPower(), snapshot.getCasterDomainId(),
                snapshot.getCasterForm(), snapshot.getCasterName(), true, snapshot.getSyncedGameTime(), payloads);
    }

    // ==================== Internal: Stale Entry Cleanup ====================

    /**
     * Removes sessions that have been resolved and entries for domains
     * that are no longer valid.
     */
    private static void cleanupStaleEntries(long currentTick, @Nullable ServerLevel serverLevel) {
        // First remove domain entries that no longer map to a live active caster.
        if (serverLevel != null) {
            List<UUID> staleCasters = new ArrayList<>();
            for (Map.Entry<UUID, DomainEntry> entry : DOMAIN_ENTRIES.entrySet()) {
                if (!isValidLiveParticipant(entry.getValue(), serverLevel, currentTick, false) && !hasUnresolvedSessions(entry.getKey())) {
                    staleCasters.add(entry.getKey());
                }
            }
            for (UUID staleCaster : staleCasters) {
                unregisterDomain(staleCaster);
            }
        }

        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() || session.isFullyDelivered() || session.getResolvedTick() < 0L) {
                continue;
            }
            long resolvedAge = currentTick - session.getResolvedTick();
            if (resolvedAge >= MAX_RESOLVED_SESSION_AGE_TICKS) {
                session.markFullyDelivered();
                LOGGER.debug("[DomainClashRegistry] Session {} auto-cleaned after resolvedAge={} ticks",
                        session.getSessionId(), resolvedAge);
            }
        }

        // Clean up resolved sessions only after both participants have claimed them.
        ACTIVE_SESSIONS.removeIf(session -> session.isResolved() && session.isFullyDelivered());

        // Update last-active-tick for all unresolved sessions where both
        // participants are still registered.
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (session.isResolved()) {
                continue;
            }

            boolean aAlive = DOMAIN_ENTRIES.containsKey(session.getParticipantA());
            boolean bAlive = DOMAIN_ENTRIES.containsKey(session.getParticipantB());

            if (aAlive && bAlive) {
                session.setLastActiveTick(currentTick);
            } else {
                // One or both participants are gone — only no-op expire if the clash never became meaningful.
                session.resolve(ClashOutcome.EXPIRED, currentTick);
                LOGGER.debug("[DomainClashRegistry] Session {} expired: A_alive={} B_alive={}",
                        session.getSessionId(), aAlive, bAlive);
            }
        }

        // Second pass to remove newly resolved sessions only after delivery is complete.
        ACTIVE_SESSIONS.removeIf(session -> session.isResolved() && session.isFullyDelivered());
    }

    private static boolean hasUnresolvedSessions(UUID casterUUID) {
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (!session.isResolved() && session.involves(casterUUID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given entry is active and can participate in overlap detection.
     * An entry is active if the caster is alive, has an active domain expansion or is building a domain,
     * and is not marked defeated.
     */
    private static boolean isEntryActiveForOverlap(DomainEntry entry, @Nullable ServerLevel serverLevel) {
        long tick = serverLevel != null ? serverLevel.getGameTime() : -1L;
        return isValidLiveParticipant(entry, serverLevel, tick, true);
    }

    private static boolean isValidLiveParticipant(DomainEntry entry, @Nullable ServerLevel serverLevel, long currentTick, boolean strict) {
        if (entry == null || entry.getCasterUUID() == null || entry.getDomainId() <= 0 || entry.isDefeated()) {
            return false;
        }
        if (serverLevel == null) {
            return !strict;
        }
        LivingEntity entity = resolveEntity(serverLevel, entry.getCasterUUID(), entry.getDimensionId());
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (currentTick >= 0L && nbt.getLong("jjkbrp_clash_resolved_until") > currentTick) {
            return false;
        }
        if (!hasUnresolvedSessions(entry.getCasterUUID()) && isScannerLossTrusted(nbt, entry.getStartTick(), currentTick)) {
            return false;
        }
        return hasStrongDomainRuntime(entity, nbt, strict);
    }

    private static boolean hasStrongDomainRuntime(LivingEntity entity, CompoundTag nbt, boolean strict) {
        boolean hasClosedCenter = nbt.contains("x_pos_doma");
        boolean hasOpenCenter = nbt.contains("jjkbrp_open_domain_cx");
        boolean hasRealCenter = hasClosedCenter || hasOpenCenter;
        boolean activeEffect = entity.hasEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        boolean activeSelect = nbt.getDouble("select") != 0.0;
        boolean activeCover = nbt.getBoolean("Cover") && hasRealCenter;
        boolean addonOpen = nbt.getBoolean("jjkbrp_open_form_active") && hasOpenCenter;
        if (activeEffect || addonOpen || activeCover) {
            return true;
        }
        if (activeSelect && hasRealCenter) {
            return true;
        }
        return !strict && hasRealCenter && (DomainAddonUtils.hasActiveDomainExpansion(entity) || DomainAddonUtils.isDomainBuildOrActive(entity));
    }

    private static boolean isWinnerSessionSuppressed(DomainEntry entry, long currentTick) {
        if (entry == null || cachedServerLevel == null || currentTick < 0L) {
            return false;
        }
        LivingEntity entity = resolveEntity(cachedServerLevel, entry.getCasterUUID(), entry.getDimensionId());
        return entity != null && entity.getPersistentData().getLong("jjkbrp_clash_winner_session_suppress_until") > currentTick;
    }

    // ==================== Internal: Legacy NBT Bridge ====================

    /**
     * Writes minimal NBT keys to entity persistent data so that
     * downstream systems that haven't been migrated to read from the
     * registry can still function.
     *
     * <p>This bridge now uses the unified clash system keys:</p>
     * <ul>
     *   <li>{@code jjkbrp_last_clash_contact_tick} — updated each tick if in a session</li>
     *   <li>{@code jjkbrp_unified_clash_active} — set on the participant receiving pressure</li>
     *   <li>{@code jjkbrp_unified_clash_opponent_uuid} — opponent reference for pressure target</li>
     *   <li>{@code jjkbrp_unified_clash_pressure} — pressure (damage) to apply this tick</li>
     * </ul>
     *
     * <p>Additionally, it writes the legacy clash active flags and aggregated
     * opponent lists to trigger OG mechanics (sure-hit alteration, floor
     * encroachment, barrier resistance) across all clash permutations:</p>
     * <ul>
     *   <li>{@code jjkbrp_barrier_clash_active} — set when entity participates in any barrier-type clash
     *       (Closed vs Closed, Closed vs Incomplete, Incomplete vs Incomplete)</li>
     *   <li>{@code jjkbrp_open_clash_active} — set when entity participates in any open-type clash
     *       (Open vs Open, Open vs Closed, Open vs Incomplete)</li>
     *   <li>{@code jjkbrp_barrier_clash_opponent_count} and indexed UUIDs — all opponents from barrier clashes</li>
     *   <li>{@code jjkbrp_open_clash_opponent_count} and indexed UUIDs — all opponents from open clashes</li>
     * </ul>
     */
    private static void writeLegacyNbtBridge(ServerLevel serverLevel, long currentTick) {
        // First clear all bridge keys for all entries to avoid stale state
        for (DomainEntry entry : DOMAIN_ENTRIES.values()) {
            LivingEntity entity = resolveEntity(serverLevel, entry.getCasterUUID(), entry.getDimensionId());
            if (entity == null) {
                continue;
            }
            clearRegistryManagedBridgeKeys(entity.getPersistentData());
        }

        // Aggregation maps per entity UUID
        Map<UUID, Set<UUID>> barrierOpponents = new HashMap<>();
        Map<UUID, Set<UUID>> openOpponents = new HashMap<>();
        Map<UUID, Double> aggregatedPressure = new HashMap<>();
        Map<UUID, UUID> pressureOpponent = new HashMap<>();

        // Process all active unresolved sessions: collect data
        for (ClashSession session : ACTIVE_SESSIONS) {
            // Note: we include resolved sessions for pressure aggregation (pressure must be delivered),
            // but we skip them for opponent/active-flag aggregation because the clash is over.
            boolean isStillActive = !session.isResolved();

            DomainEntry entryA = DOMAIN_ENTRIES.get(session.getParticipantA());
            DomainEntry entryB = DOMAIN_ENTRIES.get(session.getParticipantB());
            if (entryA == null || entryB == null) {
                continue;
            }

            LivingEntity entityA = resolveEntity(serverLevel, session.getParticipantA(), entryA.getDimensionId());
            LivingEntity entityB = resolveEntity(serverLevel, session.getParticipantB(), entryB.getDimensionId());
            if (entityA == null || entityB == null) {
                continue;
            }

            // Update contact ticks immediately (safe to do multiple times)
            CompoundTag nbtA = entityA.getPersistentData();
            CompoundTag nbtB = entityB.getPersistentData();
            nbtA.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            nbtB.putLong("jjkbrp_last_clash_contact_tick", currentTick);

            // Accumulate unified pressure (always, even if session resolved this tick)
            double pressureA = session.getPressureThisTickA();
            double pressureB = session.getPressureThisTickB();

            if (pressureA > 0) {
                aggregatedPressure.merge(session.getParticipantA(), pressureA, Double::sum);
                pressureOpponent.put(session.getParticipantA(), session.getParticipantB());
            }
            if (pressureB > 0) {
                aggregatedPressure.merge(session.getParticipantB(), pressureB, Double::sum);
                pressureOpponent.put(session.getParticipantB(), session.getParticipantA());
            }

            // Only aggregate opponent lists and active flags for sessions still active (not resolved)
            if (!isStillActive) {
                continue;
            }

            // Determine clash type categories for aggregation
            ClashType type = session.getSessionType();
            boolean isBarrierClash = type == ClashType.CLOSED_VS_CLOSED ||
                                     type == ClashType.CLOSED_VS_INCOMPLETE ||
                                     type == ClashType.INCOMPLETE_VS_INCOMPLETE;
            boolean isOpenClash = !isBarrierClash; // All remaining types involve at least one Open

            // Aggregate opponent lists for both participants
            aggregateClashData(session.getParticipantA(), session.getParticipantB(),
                    isBarrierClash, isOpenClash, barrierOpponents, openOpponents);
            aggregateClashData(session.getParticipantB(), session.getParticipantA(),
                    isBarrierClash, isOpenClash, barrierOpponents, openOpponents);
        }

        // Write aggregated data to entities
        for (UUID uuid : DOMAIN_ENTRIES.keySet()) {
            CompoundTag nbt = resolveEntityNbt(serverLevel, uuid);
            if (nbt == null) {
                continue;
            }

            // Write unified pressure if any
            Double pressure = aggregatedPressure.get(uuid);
            if (pressure != null && pressure > 0) {
                nbt.putBoolean("jjkbrp_unified_clash_active", true);
                UUID opponent = pressureOpponent.get(uuid);
                if (opponent != null) {
                    nbt.putString("jjkbrp_unified_clash_opponent_uuid", opponent.toString());
                }
                nbt.putDouble("jjkbrp_unified_clash_pressure", pressure);
            }

            // Write barrier clash data if present
            Set<UUID> barrierSet = barrierOpponents.get(uuid);
            if (barrierSet != null && !barrierSet.isEmpty()) {
                nbt.putBoolean("jjkbrp_barrier_clash_active", true);
                writeIndexedOpponents(nbt, "jjkbrp_barrier_clash_opponent_uuid", "jjkbrp_barrier_clash_opponent_count", barrierSet);
            }

            // Write open clash data if present
            Set<UUID> openSet = openOpponents.get(uuid);
            if (openSet != null && !openSet.isEmpty()) {
                nbt.putBoolean("jjkbrp_open_clash_active", true);
                writeIndexedOpponents(nbt, "jjkbrp_open_clash_opponent_uuid", "jjkbrp_open_clash_opponent_count", openSet);
            }

            // Set primary clash opponent for compatibility with systems checking a single opponent
            if ((barrierSet != null && !barrierSet.isEmpty()) || (openSet != null && !openSet.isEmpty())) {
                // Prefer barrier opponent if present (traditional clash opponent)
                UUID primary = (barrierSet != null && !barrierSet.isEmpty())
                        ? barrierSet.iterator().next()
                        : openSet.iterator().next();
                nbt.putString("jjkbrp_clash_opponent", primary.toString());
                nbt.putString("jjkbrp_last_clash_opponent_uuid", primary.toString());
            }
        }
    }

    private static void aggregateClashData(UUID participant, UUID opponent,
                                            boolean isBarrierClash, boolean isOpenClash,
                                            Map<UUID, Set<UUID>> barrierOpponents,
                                            Map<UUID, Set<UUID>> openOpponents) {
        if (isBarrierClash) {
            barrierOpponents.computeIfAbsent(participant, k -> new HashSet<>()).add(opponent);
        }
        if (isOpenClash) {
            openOpponents.computeIfAbsent(participant, k -> new HashSet<>()).add(opponent);
        }
    }

    private static void writeIndexedOpponents(CompoundTag nbt, String uuidKey, String countKey, Set<UUID> uuids) {
        List<UUID> list = new ArrayList<>(uuids);
        nbt.putInt(countKey, list.size());
        for (int i = 0; i < list.size(); i++) {
            nbt.putString(uuidKey + "_" + i, list.get(i).toString());
        }
    }

    private static CompoundTag resolveEntityNbt(ServerLevel serverLevel, UUID uuid) {
        DomainEntry entry = DOMAIN_ENTRIES.get(uuid);
        if (entry == null) {
            return null;
        }
        LivingEntity entity = resolveEntity(serverLevel, uuid, entry.getDimensionId());
        return entity != null ? entity.getPersistentData() : null;
    }

    /**
     * Clears registry-managed legacy bridge keys before the active sessions
     * of the current tick are reapplied.
     */
    private static void clearRegistryManagedBridgeKeys(CompoundTag nbt) {
        nbt.remove("jjkbrp_barrier_clash_active");
        nbt.remove("jjkbrp_open_clash_active");
        // Remove indexed opponent lists for barrier clashes
        int barrierCount = nbt.contains("jjkbrp_barrier_clash_opponent_count") ? nbt.getInt("jjkbrp_barrier_clash_opponent_count") : 0;
        nbt.remove("jjkbrp_barrier_clash_opponent_count");
        nbt.remove("jjkbrp_barrier_clash_opponent_uuid");
        for (int i = 0; i < barrierCount; i++) {
            nbt.remove("jjkbrp_barrier_clash_opponent_uuid_" + i);
        }
        // Remove indexed opponent lists for open clashes
        int openCount = nbt.contains("jjkbrp_open_clash_opponent_count") ? nbt.getInt("jjkbrp_open_clash_opponent_count") : 0;
        nbt.remove("jjkbrp_open_clash_opponent_count");
        nbt.remove("jjkbrp_open_clash_opponent_uuid");
        for (int i = 0; i < openCount; i++) {
            nbt.remove("jjkbrp_open_clash_opponent_uuid_" + i);
        }
        // Remove other bridge keys
        nbt.remove("jjkbrp_last_clash_opponent_uuid");
        nbt.remove("jjkbrp_last_clash_opponent_uuid_priority");
        nbt.remove("jjkbrp_clash_opponent");
        nbt.remove("jjkbrp_clash_opponent_priority");
        nbt.remove("jjkbrp_clash_pending_tick");
        // Unified pressure bridge keys
        nbt.remove("jjkbrp_unified_clash_active");
        nbt.remove("jjkbrp_unified_clash_opponent_uuid");
        nbt.remove("jjkbrp_unified_clash_pressure");
    }

    /**
     * Resolves a UUID to a living entity, constrained to the expected dimension.
     */
    @Nullable
    private static LivingEntity resolveEntity(ServerLevel serverLevel, UUID uuid,
                                              @Nullable String expectedDimensionId) {
        if (serverLevel == null || uuid == null) {
            return null;
        }

        ServerLevel lookupLevel = resolveLevelForDimension(serverLevel, expectedDimensionId);
        if (lookupLevel == null) {
            return null;
        }

        Entity entity = lookupLevel.getEntity(uuid);
        if (entity instanceof LivingEntity living) {
            return living;
        }

        Player player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        if (!(player.level() instanceof ServerLevel playerLevel)) {
            return null;
        }
        return playerLevel == lookupLevel ? player : null;
    }

    @Nullable
    private static ServerLevel resolveLevelForDimension(ServerLevel serverLevel,
                                                        @Nullable String expectedDimensionId) {
        if (expectedDimensionId == null || expectedDimensionId.isBlank()) {
            return serverLevel;
        }
        for (ServerLevel level : serverLevel.getServer().getAllLevels()) {
            if (expectedDimensionId.equals(level.dimension().location().toString())) {
                return level;
            }
        }
        return null;
    }

    private static Vec3 getEntityBodyPosition(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
                entity.getX(),
                entity.getY() + (double) entity.getBbHeight() * 0.5,
                entity.getZ());
    }

    private static void refreshEntryGeometryFromLiveEntity(@Nullable DomainEntry entry, @Nullable ServerLevel serverLevel, long currentTick) {
        if (entry == null || serverLevel == null) {
            return;
        }
        LivingEntity caster = resolveEntity(serverLevel, entry.getCasterUUID(), entry.getDimensionId());
        if (caster == null) {
            return;
        }
        CompoundTag nbt = caster.getPersistentData();
        entry.setForm(DomainAddonUtils.resolveOgLikeDomainForm(caster));
        entry.setCenter(DomainAddonUtils.getOgLikeDomainCenter(caster));
        entry.setBodyPosition(getEntityBodyPosition(caster));
        entry.setRadius(DomainAddonUtils.getActualDomainRadius(serverLevel, nbt));
        entry.setDimensionId(((ServerLevel)caster.level()).dimension().location().toString());
        entry.setBarrierRefinement(nbt.contains("jjkbrp_barrier_refinement") ? nbt.getDouble("jjkbrp_barrier_refinement") : entry.getBarrierRefinement());
        entry.setSureHitMultiplier(nbt.contains("jjkbrp_open_surehit_multiplier") ? nbt.getDouble("jjkbrp_open_surehit_multiplier") : entry.getSureHitMultiplier());
        if (!hasUnresolvedSessions(entry.getCasterUUID())) {
            entry.setDefeated(isScannerLossTrusted(nbt, entry.getStartTick(), currentTick));
        }
    }

    // ==================== Public: Live Entity Update ====================

    /**
     * Updates the mutable state of a registered domain entry from the
     * current live entity state.  Called from the active tick mixin to
     * keep the registry in sync with runtime changes (radius scaling,
     * power updates, defeat detection).
     *
     * @param caster the live entity to read state from
     * @param world  the level accessor for radius lookups
     */
    public static void updateFromEntity(LivingEntity caster, LevelAccessor world) {
        if (!DomainClashConstants.USE_REGISTRY || caster == null) {
            return;
        }

        DomainEntry entry = DOMAIN_ENTRIES.get(caster.getUUID());
        if (entry == null) {
            return;
        }

        CompoundTag nbt = caster.getPersistentData();

        // Update dynamic fields
        entry.setForm(DomainAddonUtils.resolveOgLikeDomainForm(caster));
        entry.setCenter(DomainAddonUtils.getOgLikeDomainCenter((Entity) caster));
        entry.setBodyPosition(getEntityBodyPosition(caster));
        entry.setRadius(DomainAddonUtils.getActualDomainRadius(world, nbt));
        if (world instanceof ServerLevel sl) {
            entry.setDimensionId(sl.dimension().location().toString());
        }

        double power = nbt.contains("jjkbrp_effective_power")
                ? nbt.getDouble("jjkbrp_effective_power")
                : entry.getEffectivePower();
        entry.setEffectivePower(power);
        entry.setBarrierRefinement(nbt.contains("jjkbrp_barrier_refinement")
                ? nbt.getDouble("jjkbrp_barrier_refinement")
                : entry.getBarrierRefinement());
        entry.setSureHitMultiplier(nbt.contains("jjkbrp_open_surehit_multiplier")
                ? nbt.getDouble("jjkbrp_open_surehit_multiplier")
                : entry.getSureHitMultiplier());

        if (!hasUnresolvedSessions(caster.getUUID())) {
            entry.setDefeated(isLossState(nbt));
        }
    }
}







