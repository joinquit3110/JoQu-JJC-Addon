package net.mcreator.jujutsucraft.addon.clash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.clash.model.ParticipantPair;
import net.minecraft.world.entity.LivingEntity;

/**
 * Authoritative server-side store of in-flight {@link ClashSession} instances, keyed by an
 * unordered {@link ParticipantPair}.
 *
 * <p>The registry has two responsibilities. The {@code sessions} map holds every active clash
 * session in a pair-keyed form so that the detector can enforce "one session per unordered pair"
 * without any secondary bookkeeping (Requirements 10.1 and 14.1), while the {@code candidates}
 * set tracks every caster that currently holds {@code DOMAIN_EXPANSION} so the detector can
 * enumerate candidate pairs without re-scanning the world twice per tick.
 *
 * <p>A single participant UUID is allowed to appear in multiple active sessions &mdash; one per
 * opponent &mdash; which matches Requirement 10.2. Look-ups for that case go through
 * {@link #sessionsContaining(UUID)}, which walks {@link #sessions} in {@code O(n)}. Because the
 * expected active-session population is small (a handful at most in normal play), the linear
 * scan is preferred over maintaining a second reverse index.
 *
 * <p>This class runs entirely on the server-tick thread inside {@code TickEvent.ServerTickEvent}
 * phase {@code END}; every mutator on it is documented "server-tick thread only; not
 * synchronized". No synchronization is performed because the whole clash pipeline is
 * single-threaded.
 *
 * <p>Requirements: 10.1, 10.2, 14.1.
 */
public final class ClashRegistry {

    private final Map<ParticipantPair, ClashSession> sessions = new HashMap<>();
    private final Set<UUID> candidates = new HashSet<>();

    /**
     * Returns {@code true} iff an active session exists for the given unordered participant
     * pair.
     */
    public boolean hasSession(ParticipantPair pair) {
        return sessions.containsKey(pair);
    }

    /**
     * Returns the active session for the given unordered participant pair, or {@code null} if
     * none is registered.
     */
    @Nullable
    public ClashSession getSession(ParticipantPair pair) {
        return sessions.get(pair);
    }

    /**
     * Returns an unmodifiable view of every active session. The returned collection reflects
     * subsequent registry changes; callers that iterate while the registry is being mutated on
     * the same thread should copy it first.
     */
    public Collection<ClashSession> activeSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Returns a freshly-allocated snapshot list of every active session whose
     * {@link ParticipantPair} contains the given participant UUID.
     *
     * <p>This is the hook used by the resolver when one caster loses a session to cascade-cancel
     * every other session that caster is in (Requirements 10.3 and 10.4). The implementation is
     * {@code O(n)} over {@link #sessions}; the expected active-session population makes this the
     * right trade-off versus maintaining a reverse index.
     *
     * @param participantId UUID to filter by; a {@code null} argument returns an empty list
     */
    public Collection<ClashSession> sessionsContaining(UUID participantId) {
        if (participantId == null) {
            return Collections.emptyList();
        }
        List<ClashSession> out = new ArrayList<>();
        for (ClashSession s : sessions.values()) {
            if (participantId.equals(s.pair.a()) || participantId.equals(s.pair.b())) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Creates and registers a new {@link ClashSession} for the given unordered pair, or returns
     * {@code null} if a session for that pair already exists.
     *
     * <p>The {@code null} return path is the uniqueness enforcement for Requirement 14.1: the
     * detector will see it as "already tracked" and skip re-creating the session on the same
     * tick. The session's initial duration is selected by the detector from the two domain forms.
     *
     * <p>Server-tick thread only; not synchronized.
     *
     * @param pair                      unordered participant key; must be non-null
     * @param a                         live caster matching {@code pair.a()}
     * @param b                         live caster matching {@code pair.b()}
     * @param serverTick                the tick at which the session is being created
     * @param durationTicks fixed clash duration selected from the form-pair duration table
     * @return the newly registered session, or {@code null} if a session for {@code pair}
     *         already exists
     */
    @Nullable
    public ClashSession create(
        ParticipantPair pair,
        LivingEntity a,
        LivingEntity b,
        long serverTick,
        int durationTicks
    ) {
        if (sessions.containsKey(pair)) {
            return null;
        }
        ClashSession session = ClashSession.create(pair, a, b, serverTick, durationTicks);
        sessions.put(pair, session);
        return session;
    }

    /**
     * Removes the session for the given unordered pair if one is registered. Intended to be
     * called by the resolver after a session has been marked resolved or cancelled.
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void remove(ParticipantPair pair) {
        sessions.remove(pair);
    }

    /**
     * Registers the given caster as an active-domain candidate. A {@code null} caster is
     * ignored. Candidate registration is idempotent because the backing store is a {@link Set}.
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void registerCandidate(LivingEntity caster) {
        if (caster == null) {
            return;
        }
        candidates.add(caster.getUUID());
    }

    /**
     * Removes the given caster UUID from the candidate set. Intended to be called when a caster
     * drops {@code DOMAIN_EXPANSION} or leaves the world. Does not touch {@link #sessions} on
     * its own; the detector is responsible for cancelling sessions containing the removed UUID.
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void unregisterCandidate(UUID casterId) {
        if (casterId == null) {
            return;
        }
        candidates.remove(casterId);
    }

    /**
     * Returns an unmodifiable view of the current candidate UUID set. The returned collection
     * reflects subsequent registry changes.
     */
    public Collection<UUID> candidates() {
        return Collections.unmodifiableCollection(candidates);
    }

    /**
     * Test helper that drops every session and every candidate. Not part of the public runtime
     * contract; production code goes through {@link #remove(ParticipantPair)} and
     * {@link #unregisterCandidate(UUID)}.
     *
     * <p>Server-tick thread only; not synchronized.
     */
    public void clear() {
        sessions.clear();
        candidates.clear();
    }
}
