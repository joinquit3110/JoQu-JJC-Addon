package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/** Small server-thread queue for winner domain rebuild waves. */
public final class DomainRebuildQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Queue<DomainRebuildPlan> QUEUE = new LinkedList<>();
    private static final Queue<DelayedPlan> FALLBACK_QUEUE = new LinkedList<>();
    private static final int FALLBACK_DELAY_TICKS = 12;
    private DomainRebuildQueue() {}
    public static void enqueue(DomainRebuildPlan plan) {
        if (plan != null && plan.getSnapshot() != null && !plan.getSnapshot().isOpen()) {
            QUEUE.add(plan);
            LOGGER.info("[DomainRebuildQueue] enqueued owner={} form={} radius={} waves={}",
                    plan.getSnapshot().getOwnerUUID(), plan.getSnapshot().getEffectiveForm(),
                    String.format("%.1f", plan.getSnapshot().getRadius()), plan.getTotalWaves());
        }
    }
    public static void enqueueFallback(DomainRebuildPlan plan) {
        if (plan != null && plan.getSnapshot() != null && !plan.getSnapshot().isOpen()) {
            FALLBACK_QUEUE.add(new DelayedPlan(plan, FALLBACK_DELAY_TICKS));
            LOGGER.info("[DomainRebuildQueue] fallback enqueued owner={} form={} radius={} waves={} delay={}",
                    plan.getSnapshot().getOwnerUUID(), plan.getSnapshot().getEffectiveForm(),
                    String.format("%.1f", plan.getSnapshot().getRadius()), plan.getTotalWaves(), FALLBACK_DELAY_TICKS);
        }
    }
    public static void tick(ServerLevel level) {
        if (level == null) return;
        promoteReadyFallbacks();
        if (QUEUE.isEmpty()) return;
        Iterator<DomainRebuildPlan> it = QUEUE.iterator();
        while (it.hasNext()) {
            DomainRebuildPlan plan = it.next();
            Entity e = level.getEntity(plan.getSnapshot().getOwnerUUID());
            LivingEntity owner = e instanceof LivingEntity living ? living : level.getServer().getPlayerList().getPlayer(plan.getSnapshot().getOwnerUUID());
            if (owner == null) {
                for (ServerLevel candidateLevel : level.getServer().getAllLevels()) {
                    Entity candidate = candidateLevel.getEntity(plan.getSnapshot().getOwnerUUID());
                    if (candidate instanceof LivingEntity living) {
                        owner = living;
                        break;
                    }
                }
            }
            if (owner != null && owner.level() instanceof ServerLevel ownerLevel) {
                level = ownerLevel;
            }
            if (plan.tick(level, owner)) it.remove();
        }
    }
    public static void clear() { QUEUE.clear(); FALLBACK_QUEUE.clear(); }

    private static void promoteReadyFallbacks() {
        if (FALLBACK_QUEUE.isEmpty()) return;
        Iterator<DelayedPlan> it = FALLBACK_QUEUE.iterator();
        while (it.hasNext()) {
            DelayedPlan delayed = it.next();
            delayed.delayTicks--;
            if (delayed.delayTicks <= 0) {
                QUEUE.add(delayed.plan);
                it.remove();
            }
        }
    }

    private static final class DelayedPlan {
        private final DomainRebuildPlan plan;
        private int delayTicks;
        private DelayedPlan(DomainRebuildPlan plan, int delayTicks) {
            this.plan = plan;
            this.delayTicks = Math.max(0, delayTicks);
        }
    }
}
