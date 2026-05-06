package net.mcreator.jujutsucraft.addon.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/** Addon orchestration layer for immutable runtime snapshots and queued rebuilds. */
public final class DomainRuntimeManager {
    private static final Map<UUID, DomainRuntimeSnapshot> ACTIVE = new HashMap<>();
    private DomainRuntimeManager() {}

    @Nullable public static DomainRuntimeSnapshot updateFromEntity(LivingEntity entity, LevelAccessor world, long tick, String source) {
        if (entity == null || world == null) return null;
        CompoundTag nbt = entity.getPersistentData();
        DomainForm form = DomainAddonUtils.resolveOgLikeDomainForm(entity);
        boolean hasBarrier = nbt.getBoolean("jjkbrp_barrier_blocks_placed") || form != DomainForm.OPEN;
        boolean hasFloor = nbt.getBoolean("jjkbrp_floor_blocks_placed") || nbt.getBoolean("jjkbrp_incomplete_form_active") || form != DomainForm.OPEN;
        UUID runtimeId = nbt.hasUUID("jjkbrp_domain_runtime_id") ? nbt.getUUID("jjkbrp_domain_runtime_id") : UUID.randomUUID();
        nbt.putUUID("jjkbrp_domain_runtime_id", runtimeId);
        DomainRuntimeSnapshot snap = new DomainRuntimeSnapshot(runtimeId, entity.getUUID(), world instanceof ServerLevel sl ? sl.dimension().location().toString() : "",
                DomainAddonUtils.getOgLikeDomainCenter(entity), DomainAddonUtils.getActualDomainRadius(world, nbt), form, form,
                DomainAddonUtils.resolveOgLikeDomainId(entity), hasBarrier, hasFloor, form == DomainForm.OPEN || DomainAddonUtils.isOpenDomainState(entity), source, tick);
        ACTIVE.put(entity.getUUID(), snap);
        return snap;
    }

    @Nullable public static DomainRuntimeSnapshot snapshotBeforeMutation(LivingEntity entity, LevelAccessor world, long tick, String source) {
        DomainRuntimeSnapshot live = updateFromEntity(entity, world, tick, source);
        return live != null ? live : ACTIVE.get(entity != null ? entity.getUUID() : null);
    }

    @Nullable static DomainRuntimeSnapshot fromEntry(DomainEntry entry, @Nullable LivingEntity entity, @Nullable ServerLevel level, long tick, String source) {
        if (entity != null) return snapshotBeforeMutation(entity, level, tick, source);
        if (entry == null) return null;
        DomainForm form = entry.getForm();
        return new DomainRuntimeSnapshot(UUID.randomUUID(), entry.getCasterUUID(), entry.getDimensionId(), entry.getCenter(), entry.getRadius(),
                form, form, entry.getDomainId(), form != DomainForm.OPEN, form != DomainForm.OPEN, form == DomainForm.OPEN, source, tick);
    }

    @Nullable public static DomainRuntimeSnapshot getActive(UUID owner) { return owner != null ? ACTIVE.get(owner) : null; }
    public static void unregister(UUID owner) { if (owner != null) ACTIVE.remove(owner); }
    public static void tick(ServerLevel level) { DomainRebuildQueue.tick(level); }
    public static void clear() { ACTIVE.clear(); DomainRebuildQueue.clear(); }
}
