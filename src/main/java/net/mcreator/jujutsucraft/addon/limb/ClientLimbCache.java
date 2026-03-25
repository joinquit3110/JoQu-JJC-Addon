package net.mcreator.jujutsucraft.addon.limb;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientLimbCache {
    private static final Map<Integer, EntityLimbSnapshot> CACHE = new ConcurrentHashMap<>();

    public static void update(int entityId, Map<LimbType, LimbState> states, Map<LimbType, Float> regenProgress) {
        CACHE.put(entityId, new EntityLimbSnapshot(new EnumMap<>(states), new EnumMap<>(regenProgress)));
    }

    public static EntityLimbSnapshot get(int entityId) {
        return CACHE.get(entityId);
    }

    public static void remove(int entityId) {
        CACHE.remove(entityId);
    }

    public static void clear() {
        CACHE.clear();
    }

    public record EntityLimbSnapshot(EnumMap<LimbType, LimbState> states, EnumMap<LimbType, Float> regenProgress) {
        public LimbState getState(LimbType type) {
            return this.states.getOrDefault(type, LimbState.INTACT);
        }

        public float getRegenProgress(LimbType type) {
            return this.regenProgress.getOrDefault(type, 0.0f);
        }

        public boolean isLimbMissing(LimbType type) {
            LimbState state = this.getState(type);
            return state == LimbState.SEVERED || state == LimbState.REVERSING;
        }
    }
}
