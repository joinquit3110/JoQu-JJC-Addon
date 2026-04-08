package net.mcreator.jujutsucraft.addon.limb;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbType;

/**
 * Client-side cache of server-authoritative limb snapshots.
 *
 * <p>Rendering code reads from this cache instead of querying capability data directly, because the
 * authoritative limb state lives on the server and is synchronized through custom packets.</p>
 */
public final class ClientLimbCache {
    /** Cached snapshot per entity id for rendering and client-only limb visual logic. */
    private static final Map<Integer, EntityLimbSnapshot> CACHE = new ConcurrentHashMap<Integer, EntityLimbSnapshot>();

    /**
     * Stores or replaces the client snapshot for one entity.
     *
     * @param entityId runtime entity id
     * @param states per-limb state map received from the server
     * @param regenProgress per-limb regeneration progress map received from the server
     */
    public static void update(int entityId, Map<LimbType, LimbState> states, Map<LimbType, Float> regenProgress) {
        // Defensive EnumMap copies prevent later external mutation from affecting the cached snapshot.
        CACHE.put(entityId, new EntityLimbSnapshot(new EnumMap<LimbType, LimbState>(states), new EnumMap<LimbType, Float>(regenProgress)));
    }

    /**
     * Returns the cached limb snapshot for one entity.
     *
     * @param entityId runtime entity id
     * @return cached snapshot, or {@code null} if none is present
     */
    public static EntityLimbSnapshot get(int entityId) {
        return CACHE.get(entityId);
    }

    /**
     * Removes the cached snapshot for one entity.
     *
     * @param entityId runtime entity id
     */
    public static void remove(int entityId) {
        CACHE.remove(entityId);
    }

    /**
     * Clears all cached limb snapshots.
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Immutable client-side snapshot of one entity's limb state.
     *
     * @param states copied per-limb state map
     * @param regenProgress copied per-limb regeneration progress map
     */
    public record EntityLimbSnapshot(EnumMap<LimbType, LimbState> states, EnumMap<LimbType, Float> regenProgress) {
        /**
         * Returns the current state of one limb in this snapshot.
         *
         * @param type limb being queried
         * @return stored state, defaulting to {@link net.mcreator.jujutsucraft.addon.limb.LimbState#INTACT}
         */
        public LimbState getState(LimbType type) {
            return this.states.getOrDefault((Object)type, LimbState.INTACT);
        }

        /**
         * Returns the cached regeneration progress for one limb.
         *
         * @param type limb being queried
         * @return progress value in the range {@code 0.0f-1.0f}
         */
        public float getRegenProgress(LimbType type) {
            return this.regenProgress.getOrDefault((Object)type, Float.valueOf(0.0f)).floatValue();
        }

        /**
         * Checks whether a limb should currently be treated as unavailable on the client.
         *
         * @param type limb being queried
         * @return {@code true} when the limb is severed or still reversing
         */
        public boolean isLimbMissing(LimbType type) {
            LimbState state = this.getState(type);
            return state == LimbState.SEVERED || state == LimbState.REVERSING;
        }
    }
}
