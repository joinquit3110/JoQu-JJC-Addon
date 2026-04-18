package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
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

        UUID casterUUID = caster.getUUID();

        // If already registered, just update existing entry instead of buffering a duplicate.
        DomainEntry existing = DOMAIN_ENTRIES.get(casterUUID);
        if (existing != null) {
            existing.setForm(form);
            existing.setCenter(center);
            existing.setBodyPosition(getEntityBodyPosition(caster));
            existing.setRadius(radius);
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

        PENDING_REGISTRATIONS.add(entry);
        LOGGER.debug("[DomainClashRegistry] Buffered pending registration for {} form={} radius={}",
                caster.getName().getString(), form, String.format("%.1f", radius));
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

        // 1. Flush pending registrations and detect startup overlaps.
        if (!PENDING_REGISTRATIONS.isEmpty()) {
            flushPendingRegistrations(currentTick);
        }

        // 2. Rescan active registered entries so moving domains can start clashing later.
        scanNewOverlaps(currentTick, serverLevel);

        // 3. Resolve pending tie / win / lose state before any stale-domain cleanup.
        evaluateSessionOutcomes(currentTick, serverLevel);

        // 4. Update active session timestamps and clean up stale entries.
        cleanupStaleEntries(currentTick, serverLevel);

        // 5. Write NBT bridge keys for legacy compatibility.
        if (serverLevel != null) {
            writeLegacyNbtBridge(serverLevel, currentTick);
        }
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

        for (DomainEntry newEntry : batch) {
            UUID newUUID = newEntry.getCasterUUID();

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
                if (existingEntry.isDefeated()) {
                    continue; // Don't create sessions with defeated domains
                }
                if (!isSameDimension(newEntry, existingEntry)) {
                    continue; // Never clash across dimensions
                }

                // Check if a session already exists for this pair
                if (hasSessionForPair(newUUID, existingUUID)) {
                    continue;
                }

                // Check spatial overlap
                if (isWithinClashRange(newEntry, existingEntry)) {
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

        double clashRange = Math.max(1.0, a.getRadius()) + Math.max(1.0, b.getRadius());
        return minimumDistanceSq <= clashRange * clashRange;
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

    /**
     * Rescans all active registered pairs so domains that move into overlap after
     * startup still receive a clash session.
     */
    private static void scanNewOverlaps(long currentTick, @Nullable ServerLevel serverLevel) {
        if (DOMAIN_ENTRIES.size() < 2) {
            return;
        }

        List<DomainEntry> entries = new ArrayList<>(DOMAIN_ENTRIES.values());
        for (int i = 0; i < entries.size(); i++) {
            DomainEntry entryA = entries.get(i);
            if (!isEntryActiveForOverlap(entryA, serverLevel)) {
                continue;
            }

            for (int j = i + 1; j < entries.size(); j++) {
                DomainEntry entryB = entries.get(j);
                if (!isEntryActiveForOverlap(entryB, serverLevel)) {
                    continue;
                }
                if (!isSameDimension(entryA, entryB)) {
                    continue;
                }
                if (hasSessionForPair(entryA.getCasterUUID(), entryB.getCasterUUID())) {
                    continue;
                }
                if (isWithinClashRange(entryA, entryB)) {
                    createSession(entryA, entryB, currentTick);
                }
            }
        }
    }

    private static boolean isEntryActiveForOverlap(DomainEntry entry, @Nullable ServerLevel serverLevel) {
        if (entry == null || entry.isDefeated()) {
            return false;
        }
        if (serverLevel == null) {
            return true;
        }

        LivingEntity live = resolveEntity(serverLevel, entry.getCasterUUID(), entry.getDimensionId());
        if (live == null || !live.isAlive() || live.isRemoved()) {
            return false;
        }
        return DomainAddonUtils.hasActiveDomainExpansion(live)
                || DomainAddonUtils.isDomainBuildOrActive(live);
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
        LOGGER.info("[DomainClashRegistry] Created clash session {} type={} A={} B={}",
                session.getSessionId(), session.getSessionType(),
                participantA, participantB);
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
                LOGGER.debug("[DomainClashRegistry] resolving session={} as EXPIRED because entry lookup failed A_present={} B_present={} tick={}",
                        session.getSessionId(), entryA != null, entryB != null, currentTick);
                session.resolve(ClashOutcome.EXPIRED, currentTick);
                continue;
            }

            LivingEntity entityA = resolveEntity(serverLevel, session.getParticipantA(), entryA.getDimensionId());
            LivingEntity entityB = resolveEntity(serverLevel, session.getParticipantB(), entryB.getDimensionId());
            if (entityA == null || entityB == null) {
                LOGGER.debug("[DomainClashRegistry] resolving session={} as EXPIRED because entity lookup failed A_present={} B_present={} tick={}",
                        session.getSessionId(), entityA != null, entityB != null, currentTick);
                session.resolve(ClashOutcome.EXPIRED, currentTick);
                continue;
            }

            CompoundTag nbtA = entityA.getPersistentData();
            CompoundTag nbtB = entityB.getPersistentData();
            boolean lossA = isLossState(nbtA) || entryA.isDefeated();
            boolean lossB = isLossState(nbtB) || entryB.isDefeated();
            long clashAge = currentTick - session.getStartTick();

            if (!lossA && !lossB) {
                if (session.hasPendingOutcome()) {
                    LOGGER.debug("[DomainClashRegistry] cleared pending session={} because both sides recovered tick={}",
                            session.getSessionId(), currentTick);
                }
                session.clearPending();
                continue;
            }

            if (lossA && lossB) {
                if (clashAge >= 0L && clashAge < DomainClashConstants.MIN_CLASH_DURATION_TICKS) {
                    LOGGER.debug("[DomainClashRegistry] session={} delaying double-loss tie because clashAge={} < minDuration={} tick={}",
                            session.getSessionId(), clashAge, DomainClashConstants.MIN_CLASH_DURATION_TICKS, currentTick);
                    continue;
                }
                if (!session.hasPendingOutcome()) {
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as TIE because both sides lost simultaneously tick={}",
                            session.getSessionId(), currentTick);
                    session.resolve(ClashOutcome.TIE, currentTick);
                    continue;
                }
                long pendingAge = currentTick - session.getPendingTick();
                if (pendingAge >= 0L && pendingAge <= DomainClashConstants.TIE_WINDOW_TICKS) {
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as TIE because second loss landed inside tie window pendingAge={} tick={}",
                            session.getSessionId(), pendingAge, currentTick);
                    session.resolve(ClashOutcome.TIE, currentTick);
                } else {
                    UUID pendingLoser = session.getPendingLoser();
                    if (pendingLoser == null) {
                        LOGGER.debug("[DomainClashRegistry] resolving session={} as TIE because pending loser was null tick={}",
                                session.getSessionId(), currentTick);
                        session.resolve(ClashOutcome.TIE, currentTick);
                    } else if (pendingLoser.equals(session.getParticipantA())) {
                        LOGGER.debug("[DomainClashRegistry] resolving session={} as B_WINS after pending loser A agedOut={} tick={}",
                                session.getSessionId(), pendingAge, currentTick);
                        session.resolve(ClashOutcome.B_WINS, currentTick);
                    } else if (pendingLoser.equals(session.getParticipantB())) {
                        LOGGER.debug("[DomainClashRegistry] resolving session={} as A_WINS after pending loser B agedOut={} tick={}",
                                session.getSessionId(), pendingAge, currentTick);
                        session.resolve(ClashOutcome.A_WINS, currentTick);
                    } else {
                        LOGGER.debug("[DomainClashRegistry] resolving session={} as TIE because pending loser did not match participants tick={}",
                                session.getSessionId(), currentTick);
                        session.resolve(ClashOutcome.TIE, currentTick);
                    }
                }
                continue;
            }

            if (clashAge >= 0L && clashAge < DomainClashConstants.MIN_CLASH_DURATION_TICKS) {
                LOGGER.debug("[DomainClashRegistry] session={} delaying outcome because clashAge={} < minDuration={} tick={}",
                        session.getSessionId(), clashAge, DomainClashConstants.MIN_CLASH_DURATION_TICKS, currentTick);
                continue;
            }

            UUID loserUuid = lossA ? session.getParticipantA() : session.getParticipantB();
            if (!session.hasPendingOutcome() || !loserUuid.equals(session.getPendingLoser())) {
                LOGGER.debug("[DomainClashRegistry] session={} starting pending loser={} clashAge={} tick={}",
                        session.getSessionId(), loserUuid, clashAge, currentTick);
                session.startPending(loserUuid, currentTick);
                continue;
            }

            long pendingAge = currentTick - session.getPendingTick();
            if (pendingAge > DomainClashConstants.TIE_WINDOW_TICKS) {
                if (loserUuid.equals(session.getParticipantA())) {
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as B_WINS after pendingAge={} tick={}",
                            session.getSessionId(), pendingAge, currentTick);
                    session.resolve(ClashOutcome.B_WINS, currentTick);
                } else {
                    LOGGER.debug("[DomainClashRegistry] resolving session={} as A_WINS after pendingAge={} tick={}",
                            session.getSessionId(), pendingAge, currentTick);
                    session.resolve(ClashOutcome.A_WINS, currentTick);
                }
            }
        }
    }

    private static boolean isLossState(CompoundTag nbt) {
        if (nbt == null) {
            return false;
        }
        return nbt.getBoolean("Failed")
                || nbt.getBoolean("DomainDefeated")
                || nbt.getBoolean("jjkbrp_was_failed")
                || nbt.getBoolean("jjkbrp_was_domain_defeated");
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
                LivingEntity live = resolveEntity(serverLevel, entry.getKey(), entry.getValue().getDimensionId());
                if (live == null || !live.isAlive() || live.isRemoved()) {
                    staleCasters.add(entry.getKey());
                    continue;
                }
                CompoundTag liveNbt = live.getPersistentData();
                boolean defeated = isLossState(liveNbt);
                boolean activeDomain = DomainAddonUtils.hasActiveDomainExpansion(live)
                        || DomainAddonUtils.isDomainBuildOrActive(live);
                if ((defeated || !activeDomain) && !hasUnresolvedSessions(entry.getKey())) {
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
                // One or both participants are gone — resolve as expired.
                session.resolve(ClashOutcome.EXPIRED, currentTick);
                LOGGER.debug("[DomainClashRegistry] Session {} expired (participant missing)",
                        session.getSessionId());
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

    // ==================== Internal: Legacy NBT Bridge ====================

    /**
     * Writes minimal NBT keys to entity persistent data so that
     * downstream systems that haven't been migrated to read from the
     * registry can still function.
     *
     * <p>This is the "additive bridge" — the registry populates the same
     * keys that the old scattered mixin logic used to write.  The bridge
     * does NOT remove old keys; it only ensures they reflect the current
     * registry state.</p>
     *
     * <p>Keys written per participant:</p>
     * <ul>
     *   <li>{@code jjkbrp_last_clash_contact_tick} — updated each tick if in a session</li>
     *   <li>{@code jjkbrp_is_eroding_barrier} / {@code jjkbrp_erosion_target_uuid} — for open-vs-closed</li>
     *   <li>{@code jjkbrp_barrier_under_attack} / {@code jjkbrp_open_attacker_uuid} — for closed being eroded</li>
     *   <li>{@code jjkbrp_incomplete_wrap_active} / {@code jjkbrp_incomplete_wrap_target_uuid} — for incomplete wrapping</li>
     *   <li>{@code jjkbrp_wrapped_by_incomplete} / {@code jjkbrp_incomplete_wrapper_uuid} — for being wrapped</li>
     *   <li>{@code jjkbrp_barrier_clash_active} — for closed-vs-closed</li>
     *   <li>{@code jjkbrp_open_clash_active} — for open-vs-open</li>
     * </ul>
     */
    private static void writeLegacyNbtBridge(ServerLevel serverLevel, long currentTick) {
        for (DomainEntry entry : DOMAIN_ENTRIES.values()) {
            LivingEntity entity = resolveEntity(serverLevel, entry.getCasterUUID(), entry.getDimensionId());
            if (entity == null) {
                continue;
            }
            clearRegistryManagedBridgeKeys(entity.getPersistentData());
        }
        for (ClashSession session : ACTIVE_SESSIONS) {
            if (session.isResolved()) continue;

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

            // Always update contact tick and generic opponent bridge keys for both participants.
            nbtA.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            nbtB.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            double priorityAB = computeBridgePriority(entryA, entryB);
            double priorityBA = computeBridgePriority(entryB, entryA);
            updatePrimaryOpponentBridge(nbtA, session.getParticipantB(), priorityAB);
            updatePrimaryOpponentBridge(nbtB, session.getParticipantA(), priorityBA);
            if (session.hasPendingOutcome()) {
                UUID pendingLoser = session.getPendingLoser();
                if (pendingLoser != null) {
                    if (pendingLoser.equals(session.getParticipantA())) {
                        nbtA.putLong("jjkbrp_clash_pending_tick", session.getPendingTick());
                    } else if (pendingLoser.equals(session.getParticipantB())) {
                        nbtB.putLong("jjkbrp_clash_pending_tick", session.getPendingTick());
                    }
                }
            }

            // Write type-specific bridge keys based on session type
            switch (session.getSessionType()) {
                case OPEN_VS_CLOSED -> {
                    // A = open, B = closed (by canonical ordering)
                    bridgeOpenVsClosed(nbtA, nbtB,
                            session.getParticipantA(), session.getParticipantB(), entryA, entryB);
                }
                case OPEN_VS_OPEN -> {
                    nbtA.putBoolean("jjkbrp_open_clash_active", true);
                    nbtB.putBoolean("jjkbrp_open_clash_active", true);
                    appendIndexedBridgeTarget(nbtA, "jjkbrp_open_clash_opponent_uuid",
                            "jjkbrp_open_clash_opponent_count", session.getParticipantB(), priorityAB);
                    appendIndexedBridgeTarget(nbtB, "jjkbrp_open_clash_opponent_uuid",
                            "jjkbrp_open_clash_opponent_count", session.getParticipantA(), priorityBA);
                }
                case OPEN_VS_INCOMPLETE -> {
                    // A = open, B = incomplete
                    bridgeOpenVsClosed(nbtA, nbtB,
                            session.getParticipantA(), session.getParticipantB(), entryA, entryB);
                    bridgeIncompleteWrap(nbtB, nbtA,
                            session.getParticipantB(), session.getParticipantA(), entryB, entryA);
                }
                case CLOSED_VS_CLOSED -> {
                    nbtA.putBoolean("jjkbrp_barrier_clash_active", true);
                    nbtB.putBoolean("jjkbrp_barrier_clash_active", true);
                    appendIndexedBridgeTarget(nbtA, "jjkbrp_barrier_clash_opponent_uuid",
                            "jjkbrp_barrier_clash_opponent_count", session.getParticipantB(), priorityAB);
                    appendIndexedBridgeTarget(nbtB, "jjkbrp_barrier_clash_opponent_uuid",
                            "jjkbrp_barrier_clash_opponent_count", session.getParticipantA(), priorityBA);
                }
                case CLOSED_VS_INCOMPLETE -> {
                    // A = closed, B = incomplete
                    bridgeIncompleteWrap(nbtB, nbtA,
                            session.getParticipantB(), session.getParticipantA(), entryB, entryA);
                }
                case INCOMPLETE_VS_INCOMPLETE -> {
                    // Both sides get mutual wrap markers
                    bridgeIncompleteWrap(nbtA, nbtB,
                            session.getParticipantA(), session.getParticipantB(), entryA, entryB);
                    bridgeIncompleteWrap(nbtB, nbtA,
                            session.getParticipantB(), session.getParticipantA(), entryB, entryA);
                }
            }
        }
    }

    /**
     * Writes open-vs-closed erosion bridge keys.
     * openNbt gets the attacker markers, closedNbt gets the defender markers.
     */
    private static void bridgeOpenVsClosed(CompoundTag openNbt, CompoundTag closedNbt,
                                            UUID openUUID, UUID closedUUID,
                                            @Nullable DomainEntry openEntry,
                                            @Nullable DomainEntry closedEntry) {
        openNbt.putBoolean("jjkbrp_is_eroding_barrier", true);
        appendIndexedBridgeTarget(openNbt, "jjkbrp_erosion_target_uuid",
                "jjkbrp_erosion_target_count", closedUUID,
                computeBridgePriority(openEntry, closedEntry));
        closedNbt.putBoolean("jjkbrp_barrier_under_attack", true);
        updatePrimaryBridgeTarget(closedNbt, "jjkbrp_open_attacker_uuid", openUUID,
                computeBridgePriority(closedEntry, openEntry));
        if (!closedNbt.contains("jjkbrp_barrier_erosion_total")) {
            closedNbt.putDouble("jjkbrp_barrier_erosion_total", 0.0);
        }
        double barrierRefinement = closedEntry != null
                ? closedEntry.getBarrierRefinement()
                : closedNbt.getDouble("jjkbrp_barrier_refinement");
        if (barrierRefinement <= 0.0 && !closedNbt.contains("jjkbrp_barrier_refinement")) {
            barrierRefinement = DomainClashConstants.NPC_DEFAULT_BARRIER_REFINEMENT;
        }
        closedNbt.putDouble("jjkbrp_barrier_refinement", barrierRefinement);
    }

    /**
     * Writes incomplete wrap bridge keys.
     * incompleteNbt gets the wrapper markers, targetNbt gets the wrapped markers.
     */
    private static void bridgeIncompleteWrap(CompoundTag incompleteNbt, CompoundTag targetNbt,
                                              UUID incompleteUUID, UUID targetUUID,
                                              @Nullable DomainEntry incompleteEntry,
                                              @Nullable DomainEntry targetEntry) {
        incompleteNbt.putBoolean("jjkbrp_incomplete_wrap_active", true);
        appendIndexedBridgeTarget(incompleteNbt, "jjkbrp_incomplete_wrap_target_uuid",
                "jjkbrp_incomplete_wrap_target_count", targetUUID,
                computeBridgePriority(incompleteEntry, targetEntry));
        targetNbt.putBoolean("jjkbrp_wrapped_by_incomplete", true);
        updatePrimaryBridgeTarget(targetNbt, "jjkbrp_incomplete_wrapper_uuid", incompleteUUID,
                computeBridgePriority(targetEntry, incompleteEntry));
    }

    private static void appendIndexedBridgeTarget(CompoundTag nbt, String singularKey,
                                                  String countKey, UUID targetUuid,
                                                  double priorityScore) {
        if (nbt == null || targetUuid == null) {
            return;
        }
        int count = Math.max(0, nbt.getInt(countKey));
        nbt.putString(singularKey + "_" + count, targetUuid.toString());
        nbt.putInt(countKey, count + 1);
        updatePrimaryBridgeTarget(nbt, singularKey, targetUuid, priorityScore);
    }

    private static void updatePrimaryBridgeTarget(CompoundTag nbt, String singularKey,
                                                  UUID targetUuid, double priorityScore) {
        if (nbt == null || singularKey == null || targetUuid == null) {
            return;
        }
        String priorityKey = singularKey + "_priority";
        if (!nbt.contains(singularKey) || !nbt.contains(priorityKey)
                || priorityScore < nbt.getDouble(priorityKey)) {
            nbt.putString(singularKey, targetUuid.toString());
            nbt.putDouble(priorityKey, priorityScore);
        }
    }

    private static void updatePrimaryOpponentBridge(CompoundTag nbt, UUID opponentUuid,
                                                    double priorityScore) {
        updatePrimaryBridgeTarget(nbt, "jjkbrp_last_clash_opponent_uuid", opponentUuid, priorityScore);
        updatePrimaryBridgeTarget(nbt, "jjkbrp_clash_opponent", opponentUuid, priorityScore);
    }

    private static double computeBridgePriority(@Nullable DomainEntry sourceEntry,
                                                @Nullable DomainEntry targetEntry) {
        if (sourceEntry == null || targetEntry == null) {
            return Double.MAX_VALUE;
        }
        Vec3 sourceCenter = sourceEntry.getCenter();
        Vec3 targetCenter = targetEntry.getCenter();
        double dx = sourceCenter.x - targetCenter.x;
        double dy = sourceCenter.y - targetCenter.y;
        double dz = sourceCenter.z - targetCenter.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void clearIndexedBridgeKeys(CompoundTag nbt, String singularKey, String countKey) {
        int count = nbt.contains(countKey) ? Math.max(0, nbt.getInt(countKey)) : 0;
        nbt.remove(countKey);
        nbt.remove(singularKey);
        nbt.remove(singularKey + "_priority");
        for (int i = 0; i < count; i++) {
            nbt.remove(singularKey + "_" + i);
        }
        int scanIndex = count;
        while (nbt.contains(singularKey + "_" + scanIndex)) {
            nbt.remove(singularKey + "_" + scanIndex);
            scanIndex++;
        }
    }

    /**
     * Clears registry-managed legacy bridge keys before the active sessions
     * of the current tick are reapplied.
     */
    private static void clearRegistryManagedBridgeKeys(CompoundTag nbt) {
        nbt.remove("jjkbrp_is_eroding_barrier");
        clearIndexedBridgeKeys(nbt, "jjkbrp_erosion_target_uuid", "jjkbrp_erosion_target_count");
        nbt.remove("jjkbrp_barrier_under_attack");
        nbt.remove("jjkbrp_open_attacker_uuid");
        nbt.remove("jjkbrp_open_attacker_uuid_priority");
        nbt.remove("jjkbrp_incomplete_wrap_active");
        clearIndexedBridgeKeys(nbt, "jjkbrp_incomplete_wrap_target_uuid", "jjkbrp_incomplete_wrap_target_count");
        nbt.remove("jjkbrp_wrapped_by_incomplete");
        nbt.remove("jjkbrp_incomplete_wrapper_uuid");
        nbt.remove("jjkbrp_incomplete_wrapper_uuid_priority");
        nbt.remove("jjkbrp_barrier_clash_active");
        nbt.remove("jjkbrp_open_clash_active");
        clearIndexedBridgeKeys(nbt, "jjkbrp_barrier_clash_opponent_uuid", "jjkbrp_barrier_clash_opponent_count");
        clearIndexedBridgeKeys(nbt, "jjkbrp_open_clash_opponent_uuid", "jjkbrp_open_clash_opponent_count");
        nbt.remove("jjkbrp_last_clash_opponent_uuid");
        nbt.remove("jjkbrp_last_clash_opponent_uuid_priority");
        nbt.remove("jjkbrp_clash_opponent");
        nbt.remove("jjkbrp_clash_opponent_priority");
        nbt.remove("jjkbrp_clash_pending_tick");
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
        entry.setForm(DomainAddonUtils.resolveDomainForm(caster));
        entry.setCenter(DomainAddonUtils.getDomainCenter((Entity) caster));
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

        boolean defeated = isLossState(nbt);
        entry.setDefeated(defeated);
    }
}
