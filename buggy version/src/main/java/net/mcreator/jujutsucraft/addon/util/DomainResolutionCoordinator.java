package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Coordinates physical resolution using immutable pre-mutation snapshots. */
public final class DomainResolutionCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private DomainResolutionCoordinator() {}

    public static void resolve(ServerLevel level, ClashSession session, DomainEntry entryA, DomainEntry entryB,
                               LivingEntity entityA, LivingEntity entityB, long tick) {
        if (level == null || session == null) return;
        DomainRuntimeSnapshot snapA = DomainRuntimeManager.fromEntry(entryA, entityA, level, tick, "resolution-A");
        DomainRuntimeSnapshot snapB = DomainRuntimeManager.fromEntry(entryB, entityB, level, tick, "resolution-B");
        ClashOutcome outcome = session.getOutcome();
        if (outcome == ClashOutcome.TIE) {
            defeat(entityA, tick); defeat(entityB, tick);
            DomainCleanupPlan.of(snapA, snapB, "tie-A").schedule(level);
            DomainCleanupPlan.of(snapB, snapA, "tie-B").schedule(level);
            DomainRuntimeManager.unregister(session.getParticipantA());
            DomainRuntimeManager.unregister(session.getParticipantB());
            return;
        }
        boolean aWins = outcome == ClashOutcome.A_WINS;
        LivingEntity winner = aWins ? entityA : entityB;
        LivingEntity loser = aWins ? entityB : entityA;
        DomainRuntimeSnapshot winnerSnap = aWins ? snapA : snapB;
        DomainRuntimeSnapshot loserSnap = aWins ? snapB : snapA;
        defeat(loser, tick);
        DomainCleanupPlan.of(loserSnap, winnerSnap, "decisive-loser").schedule(level);
        if (winner != null && winnerSnap != null && !winnerSnap.isOpen()) {
            seedWinnerForOgActiveTick(level, winner, winnerSnap, tick);
            DomainRebuildQueue.enqueueFallback(new DomainRebuildPlan(winnerSnap));
        }
        if (loserSnap != null) DomainRuntimeManager.unregister(loserSnap.getOwnerUUID());
        LOGGER.info("[DomainResolutionCoordinator] resolved session={} outcome={} winner={} loser={} tick={}", session.getSessionId(), outcome,
                winnerSnap != null ? winnerSnap.getOwnerUUID() : null, loserSnap != null ? loserSnap.getOwnerUUID() : null, tick);
    }

    private static void defeat(LivingEntity entity, long tick) {
        if (entity == null) return;
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean("DomainDefeated", true);
        nbt.putBoolean("Failed", true);
        nbt.putLong("jjkbrp_clash_result_tick", tick);
        nbt.putLong("jjkbrp_clash_resolved_until", tick + DomainClashConstants.RESULT_COOLDOWN_TICKS);
        entity.removeEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
    }

    private static void seedWinnerForOgActiveTick(ServerLevel level, LivingEntity entity, DomainRuntimeSnapshot snapshot, long tick) {
        if (entity == null || snapshot == null || !snapshot.isValid()) return;
        CompoundTag nbt = entity.getPersistentData();
        Vec3 c = snapshot.getCenter();
        nbt.putBoolean("DomainDefeated", false);
        // OG active tick observes Failed=true and transitions the surviving domain back through Cover.
        nbt.putBoolean("Failed", true);
        nbt.putBoolean("Cover", true);
        nbt.putDouble("cnt_cover", 1.0);
        nbt.putDouble("select", snapshot.getDomainId());
        nbt.putDouble("skill_domain", snapshot.getDomainId());
        nbt.putDouble("x_pos_doma", c.x);
        nbt.putDouble("y_pos_doma", c.y);
        nbt.putDouble("z_pos_doma", c.z);
        nbt.putDouble("cnt6", snapshot.getRadius());
        nbt.putDouble("range", snapshot.getRadius());
        nbt.putInt("jjkbrp_domain_form_cast_locked", snapshot.getEffectiveForm().getId());
        nbt.putInt("jjkbrp_domain_form_effective", snapshot.getEffectiveForm().getId());
        nbt.putLong("jjkbrp_clash_winner_session_suppress_until", tick + 200L);
        if (!entity.hasEffect(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            entity.addEffect(new MobEffectInstance(net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects.DOMAIN_EXPANSION.get(), 80, 0, false, false));
        }
        DomainRuntimeManager.updateFromEntity(entity, level, tick, "resolution-winner-og-seed");
    }
}
