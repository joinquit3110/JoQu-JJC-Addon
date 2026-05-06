package net.mcreator.jujutsucraft.addon.util;

import java.util.UUID;
import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.DomainBarrierCleanup;
import net.minecraft.server.level.ServerLevel;

/** Cleanup plan based on immutable snapshots, not live mutated NBT. */
public final class DomainCleanupPlan {
    private final DomainRuntimeSnapshot target;
    private final DomainRuntimeSnapshot protectedWinner;
    private final UUID authorizedOwner;
    private final String reason;

    public DomainCleanupPlan(DomainRuntimeSnapshot target, @Nullable DomainRuntimeSnapshot protectedWinner, UUID authorizedOwner, String reason) {
        this.target = target;
        this.protectedWinner = protectedWinner;
        this.authorizedOwner = authorizedOwner;
        this.reason = reason != null ? reason : "unknown";
    }

    public static DomainCleanupPlan of(DomainRuntimeSnapshot target, @Nullable DomainRuntimeSnapshot protectedWinner, String reason) {
        return new DomainCleanupPlan(target, protectedWinner, target != null ? target.getOwnerUUID() : null, reason);
    }

    public void schedule(ServerLevel level) {
        if (level == null || target == null || !target.isValid()) return;
        if (target.isOpen() && !target.hasBarrierBlocks() && !target.hasFloorBlocks()) return;
        DomainBarrierCleanup.scheduleSweeps(level, target.toGeometrySnapshot("cleanup-plan-" + reason), authorizedOwner,
                protectedWinner != null ? protectedWinner.toGeometrySnapshot("cleanup-protected-winner") : null);
    }
}
