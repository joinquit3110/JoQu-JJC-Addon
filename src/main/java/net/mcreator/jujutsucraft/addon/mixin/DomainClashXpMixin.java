package net.mcreator.jujutsucraft.addon.mixin;

import java.util.UUID;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clash-resolution mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()` that decides win, tie, or loss outcomes and grants the corresponding domain mastery XP rewards once the clash state settles.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public class DomainClashXpMixin {
    // Mixin-local runtime state used for winner xp.
    private static final int JJKBRP$WINNER_XP = 50;
    // Mixin-local runtime state used for tie xp.
    private static final int JJKBRP$TIE_XP = 30;
    // Mixin-local runtime state used for loser xp.
    private static final int JJKBRP$LOSER_XP = 10;
    // Mixin-local runtime state used for tie window ticks.
    private static final long JJKBRP$TIE_WINDOW_TICKS = 5L;
    // Mixin-local runtime state used for recent clash contact ticks.
    private static final long JJKBRP$RECENT_CLASH_CONTACT_TICKS = 40L;


    // ===== CLASH OUTCOME RESOLUTION =====
    /**
     * Injects after the active domain tick to resolve finished clash outcomes and award win, tie, or loss XP once the pending window has matured.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$grantClashXp(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        LivingEntity tieEntity;
        if (world.isClientSide()) {
            return;
        }
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)world;
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity sourceEntity = (LivingEntity)entity;
        CompoundTag nbt = sourceEntity.getPersistentData();
        long currentTick = sl.getGameTime();
        boolean lossState = DomainClashXpMixin.jjkbrp$isLossState(nbt);
        AABB searchBox = DomainClashXpMixin.jjkbrp$buildClashSearchBox(world, sourceEntity);
        if (!lossState) {
            DomainClashXpMixin.jjkbrp$clearOutcomeTracking(nbt);
            if (!DomainClashXpMixin.jjkbrp$isActiveClashParticipant(sourceEntity) || !DomainClashXpMixin.jjkbrp$isDomainCasterState(sourceEntity)) {
                return;
            }
            LivingEntity pendingLoser = DomainClashXpMixin.jjkbrp$findPendingLoserForWinner(world, sourceEntity, searchBox, currentTick);
            if (pendingLoser != null) {
                    // Once the pending clash window matures, resolve a decisive winner or loser outcome before awarding XP.
                DomainClashXpMixin.jjkbrp$resolveWinLose(sourceEntity, pendingLoser, currentTick);
            }
            return;
        }
        if (nbt.contains("jjkbrp_clash_result_tick")) {
            return;
        }
        if (!DomainClashXpMixin.jjkbrp$hasClashContext(world, sourceEntity, searchBox, currentTick)) {
            if (nbt.contains("jjkbrp_clash_pending_tick")) {
                nbt.remove("jjkbrp_clash_pending_tick");
            }
            return;
        }
        if (!nbt.contains("jjkbrp_clash_pending_tick")) {
            nbt.putLong("jjkbrp_clash_pending_tick", currentTick);
        }
        if ((tieEntity = DomainClashXpMixin.jjkbrp$findPendingTieEntity(world, sourceEntity, searchBox, currentTick)) != null) {
                    // If neither side establishes a decisive lead, the clash falls back to the shared tie-resolution path.
            DomainClashXpMixin.jjkbrp$resolveTie(sourceEntity, tieEntity, currentTick);
            return;
        }
        long pendingTick = nbt.getLong("jjkbrp_clash_pending_tick");
        long pendingAge = currentTick - pendingTick;
        if (pendingAge >= 0L && pendingAge <= 5L) {
            return;
        }
        LivingEntity winnerEntity = DomainClashXpMixin.jjkbrp$findWinnerEntity(world, sourceEntity, searchBox, currentTick);
        if (winnerEntity != null) {
                    // Once the pending clash window matures, resolve a decisive winner or loser outcome before awarding XP.
            DomainClashXpMixin.jjkbrp$resolveWinLose(winnerEntity, sourceEntity, currentTick);
        }
    }

    /**
     * Performs build clash search box for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @return the resulting build clash search box value.
     */
    private static AABB jjkbrp$buildClashSearchBox(LevelAccessor world, LivingEntity source) {
        CompoundTag nbt = source.getPersistentData();
        double cx = nbt.getDouble("x_pos_doma");
        double cy = nbt.getDouble("y_pos_doma");
        double cz = nbt.getDouble("z_pos_doma");
        if (!nbt.contains("x_pos_doma")) {
            cx = source.getX();
            cy = source.getY();
            cz = source.getZ();
        }
        double actualRange = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
        double clashHalfRange = Math.max(6.0, DomainClashXpMixin.jjkbrp$baseClashRange(world, source, nbt) * 0.5);
        double searchRange = Math.max(actualRange * 2.5, clashHalfRange * 1.35);
        searchRange = Math.min(128.0, Math.max(searchRange, actualRange * 2.5));
        return new AABB(cx - searchRange, cy - searchRange, cz - searchRange, cx + searchRange, cy + searchRange, cz + searchRange);
    }

    /**
     * Performs has clash context for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param box box used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether has clash context is true for the current runtime state.
     */
    private static boolean jjkbrp$hasClashContext(LevelAccessor world, LivingEntity source, AABB box, long currentTick) {
        if (DomainClashXpMixin.jjkbrp$hasRecentClashContact(source.getPersistentData(), currentTick)) {
            return true;
        }
        LivingEntity trackedOpponent = DomainClashXpMixin.jjkbrp$resolveTrackedOpponent(world, source, currentTick);
        if (trackedOpponent != null) {
            return true;
        }
        for (Entity e : world.getEntities(null, box)) {
            LivingEntity candidate;
            if (e == source || !(e instanceof LivingEntity) || !DomainClashXpMixin.jjkbrp$isDomainCasterState(candidate = (LivingEntity)e) || !DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, source, candidate)) continue;
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(source, candidate);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(source, candidate, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, source, candidate);
            if (!explicitPair && !recentPair && !fallbackPair) continue;
            if (explicitPair || recentPair) {
                return true;
            }
            if (!fallbackPair || !DomainClashXpMixin.jjkbrp$hasRecentClashContact(candidate.getPersistentData(), currentTick)) continue;
            return true;
        }
        return false;
    }

    /**
     * Finds pending tie entity needed by this mixin.
     * @param world world access used by the current mixin callback.
     * @param sourceEntity source entity used by this method.
     * @param box box used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return the resulting find pending tie entity value.
     */
    private static LivingEntity jjkbrp$findPendingTieEntity(LevelAccessor world, LivingEntity sourceEntity, AABB box, long currentTick) {
        LivingEntity nearest = null;
        double bestScore = Double.MAX_VALUE;
        CompoundTag sourceNbt = sourceEntity.getPersistentData();
        LivingEntity trackedOpponent = DomainClashXpMixin.jjkbrp$resolveTrackedOpponent(world, sourceEntity, currentTick);
        if (trackedOpponent != null) {
            CompoundTag trackedNbt = trackedOpponent.getPersistentData();
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(sourceEntity, trackedOpponent);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(sourceEntity, trackedOpponent, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, sourceEntity, trackedOpponent);
            if (!trackedNbt.contains("jjkbrp_clash_result_tick") && DomainClashXpMixin.jjkbrp$isLossState(trackedNbt) && DomainClashXpMixin.jjkbrp$hasPendingWithinWindow(trackedNbt, currentTick) && DomainClashXpMixin.jjkbrp$isDomainCasterState(trackedOpponent) && DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, sourceEntity, trackedOpponent) && DomainClashXpMixin.jjkbrp$canResolveTie(sourceNbt, trackedNbt, explicitPair, recentPair, fallbackPair, currentTick)) {
                return trackedOpponent;
            }
        }
        for (Entity e : world.getEntities(null, box)) {
            double dist;
            boolean fallbackPair;
            boolean recentPair;
            boolean explicitPair;
            LivingEntity candidate;
            CompoundTag candNbt;
            if (e == sourceEntity || !(e instanceof LivingEntity) || (candNbt = (candidate = (LivingEntity)e).getPersistentData()).contains("jjkbrp_clash_result_tick") || !DomainClashXpMixin.jjkbrp$isLossState(candNbt) || !DomainClashXpMixin.jjkbrp$hasPendingWithinWindow(candNbt, currentTick) || !DomainClashXpMixin.jjkbrp$isDomainCasterState(candidate) || !DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, sourceEntity, candidate) || !DomainClashXpMixin.jjkbrp$canResolveTie(sourceNbt, candNbt, explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(sourceEntity, candidate), recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(sourceEntity, candidate, currentTick), fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, sourceEntity, candidate), currentTick)) continue;
            double score = dist = candidate.distanceToSqr((Entity)sourceEntity);
            if (explicitPair) {
                score -= 4096.0;
            } else if (recentPair) {
                score -= 1024.0;
            } else if (fallbackPair) {
                score -= 128.0;
            }
            if (!(score < bestScore)) continue;
            bestScore = score;
            nearest = candidate;
        }
        return nearest;
    }

    /**
     * Performs can resolve tie for this mixin.
     * @param sourceNbt source nbt used by this method.
     * @param candidateNbt candidate nbt used by this method.
     * @param explicitPair explicit pair used by this method.
     * @param recentPair recent pair used by this method.
     * @param fallbackPair fallback pair used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether can resolve tie is true for the current runtime state.
     */
    private static boolean jjkbrp$canResolveTie(CompoundTag sourceNbt, CompoundTag candidateNbt, boolean explicitPair, boolean recentPair, boolean fallbackPair, long currentTick) {
        boolean candidateRecent;
        boolean sourceRecent = DomainClashXpMixin.jjkbrp$hasPendingWithinWindow(sourceNbt, currentTick) || DomainClashXpMixin.jjkbrp$hasRecentClashContact(sourceNbt, currentTick);
        boolean bl = candidateRecent = DomainClashXpMixin.jjkbrp$hasPendingWithinWindow(candidateNbt, currentTick) || DomainClashXpMixin.jjkbrp$hasRecentClashContact(candidateNbt, currentTick);
        if (!sourceRecent || !candidateRecent) {
            return false;
        }
        if (explicitPair || recentPair) {
            return true;
        }
        return fallbackPair;
    }

    /**
     * Finds pending loser for winner needed by this mixin.
     * @param world world access used by the current mixin callback.
     * @param winnerEntity winner entity used by this method.
     * @param box box used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return the resulting find pending loser for winner value.
     */
    private static LivingEntity jjkbrp$findPendingLoserForWinner(LevelAccessor world, LivingEntity winnerEntity, AABB box, long currentTick) {
        LivingEntity nearest = null;
        double bestScore = Double.MAX_VALUE;
        LivingEntity trackedOpponent = DomainClashXpMixin.jjkbrp$resolveTrackedOpponent(world, winnerEntity, currentTick);
        if (trackedOpponent != null) {
            CompoundTag trackedNbt = trackedOpponent.getPersistentData();
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(trackedOpponent, winnerEntity);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(trackedOpponent, winnerEntity, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, trackedOpponent, winnerEntity);
            if (!trackedNbt.contains("jjkbrp_clash_result_tick") && DomainClashXpMixin.jjkbrp$isLossState(trackedNbt) && trackedNbt.contains("jjkbrp_clash_pending_tick") && DomainClashXpMixin.jjkbrp$isPendingExpired(trackedNbt, currentTick) && DomainClashXpMixin.jjkbrp$isDomainCasterState(trackedOpponent) && DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, trackedOpponent, winnerEntity) && (explicitPair || recentPair || fallbackPair)) {
                return trackedOpponent;
            }
        }
        for (Entity e : world.getEntities(null, box)) {
            double dist;
            LivingEntity loserEntity;
            CompoundTag loserNbt;
            if (e == winnerEntity || !(e instanceof LivingEntity) || (loserNbt = (loserEntity = (LivingEntity)e).getPersistentData()).contains("jjkbrp_clash_result_tick") || !DomainClashXpMixin.jjkbrp$isLossState(loserNbt) || !loserNbt.contains("jjkbrp_clash_pending_tick") || !DomainClashXpMixin.jjkbrp$isPendingExpired(loserNbt, currentTick) || !DomainClashXpMixin.jjkbrp$isDomainCasterState(loserEntity) || !DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, loserEntity, winnerEntity)) continue;
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(loserEntity, winnerEntity);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(loserEntity, winnerEntity, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, loserEntity, winnerEntity);
            if (!explicitPair && !recentPair && !fallbackPair || !explicitPair && !recentPair && (!DomainClashXpMixin.jjkbrp$hasRecentClashContact(loserNbt, currentTick) || !DomainClashXpMixin.jjkbrp$hasRecentClashContact(winnerEntity.getPersistentData(), currentTick))) continue;
            double score = dist = loserEntity.distanceToSqr((Entity)winnerEntity);
            if (explicitPair) {
                score -= 4096.0;
            } else if (recentPair) {
                score -= 1024.0;
            } else if (fallbackPair) {
                score -= 128.0;
            }
            if (!(score < bestScore)) continue;
            bestScore = score;
            nearest = loserEntity;
        }
        return nearest;
    }

    /**
     * Finds winner entity needed by this mixin.
     * @param world world access used by the current mixin callback.
     * @param loserEntity loser entity used by this method.
     * @param box box used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return the resulting find winner entity value.
     */
    private static LivingEntity jjkbrp$findWinnerEntity(LevelAccessor world, LivingEntity loserEntity, AABB box, long currentTick) {
        LivingEntity nearest = null;
        double bestScore = Double.MAX_VALUE;
        LivingEntity trackedOpponent = DomainClashXpMixin.jjkbrp$resolveTrackedOpponent(world, loserEntity, currentTick);
        if (trackedOpponent != null) {
            CompoundTag trackedNbt = trackedOpponent.getPersistentData();
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(loserEntity, trackedOpponent);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(loserEntity, trackedOpponent, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, loserEntity, trackedOpponent);
            if (!trackedNbt.contains("jjkbrp_clash_result_tick") && !DomainClashXpMixin.jjkbrp$isLossState(trackedNbt) && DomainClashXpMixin.jjkbrp$isActiveClashParticipant(trackedOpponent) && DomainClashXpMixin.jjkbrp$isDomainCasterState(trackedOpponent) && DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, loserEntity, trackedOpponent) && (explicitPair || recentPair || fallbackPair)) {
                return trackedOpponent;
            }
        }
        for (Entity e : world.getEntities(null, box)) {
            double dist;
            LivingEntity livingCandidate;
            CompoundTag candNbt;
            if (e == loserEntity || !(e instanceof LivingEntity) || (candNbt = (livingCandidate = (LivingEntity)e).getPersistentData()).contains("jjkbrp_clash_result_tick") || DomainClashXpMixin.jjkbrp$isLossState(candNbt) || !DomainClashXpMixin.jjkbrp$isActiveClashParticipant(livingCandidate) || !DomainClashXpMixin.jjkbrp$isDomainCasterState(livingCandidate) || !DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, loserEntity, livingCandidate)) continue;
            boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(loserEntity, livingCandidate);
            boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(loserEntity, livingCandidate, currentTick);
            boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, loserEntity, livingCandidate);
            if (!explicitPair && !recentPair && !fallbackPair) continue;
            double score = dist = livingCandidate.distanceToSqr((Entity)loserEntity);
            if (explicitPair) {
                score -= 4096.0;
            } else if (recentPair) {
                score -= 1024.0;
            } else if (fallbackPair) {
                score -= 128.0;
            }
            if (!(score < bestScore)) continue;
            bestScore = score;
            nearest = livingCandidate;
        }
        return nearest;
    }

    /**
     * Performs is plausible local clash opponent for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param candidate candidate used by this method.
     * @return whether is plausible local clash opponent is true for the current runtime state.
     */
    private static boolean jjkbrp$isPlausibleLocalClashOpponent(LevelAccessor world, LivingEntity source, LivingEntity candidate) {
        double candidateRange;
        double sourceRange;
        double maxCenterDistance;
        double cz;
        double centerDz;
        double cy;
        double centerDy;
        if (source == null || candidate == null) {
            return false;
        }
        CompoundTag sourceNbt = source.getPersistentData();
        CompoundTag candNbt = candidate.getPersistentData();
        double sx = sourceNbt.contains("x_pos_doma") ? sourceNbt.getDouble("x_pos_doma") : source.getX();
        double sy = sourceNbt.contains("y_pos_doma") ? sourceNbt.getDouble("y_pos_doma") : source.getY();
        double sz = sourceNbt.contains("z_pos_doma") ? sourceNbt.getDouble("z_pos_doma") : source.getZ();
        double cx = candNbt.contains("x_pos_doma") ? candNbt.getDouble("x_pos_doma") : candidate.getX();
        double centerDx = sx - cx;
        if (centerDx * centerDx + (centerDy = sy - (cy = candNbt.contains("y_pos_doma") ? candNbt.getDouble("y_pos_doma") : candidate.getY())) * centerDy + (centerDz = sz - (cz = candNbt.contains("z_pos_doma") ? candNbt.getDouble("z_pos_doma") : candidate.getZ())) * centerDz > (maxCenterDistance = Math.max(16.0, (sourceRange = Math.max(DomainAddonUtils.getActualDomainRadius(world, sourceNbt), DomainAddonUtils.getOpenDomainRange(world, (Entity)source) * 0.45)) + (candidateRange = Math.max(DomainAddonUtils.getActualDomainRadius(world, candNbt), DomainAddonUtils.getOpenDomainRange(world, (Entity)candidate) * 0.45)))) * maxCenterDistance) {
            return false;
        }
        double maxBodyDistance = Math.max(16.0, Math.max(sourceRange, candidateRange) * 1.75);
        return source.distanceToSqr((Entity)candidate) <= maxBodyDistance * maxBodyDistance;
    }

    /**
     * Performs is fallback direct clash neighbor for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param candidate candidate used by this method.
     * @return whether is fallback direct clash neighbor is true for the current runtime state.
     */
    private static boolean jjkbrp$isFallbackDirectClashNeighbor(LevelAccessor world, LivingEntity source, LivingEntity candidate) {
        if (!DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, source, candidate)) {
            return false;
        }
        if (!DomainClashXpMixin.jjkbrp$isWithinBaseClashWindow(world, source, candidate)) {
            return false;
        }
        return DomainClashXpMixin.jjkbrp$isWithinBaseClashWindow(world, candidate, source);
    }

    /**
     * Performs is within base clash window for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param target entity involved in the current mixin operation.
     * @return whether is within base clash window is true for the current runtime state.
     */
    private static boolean jjkbrp$isWithinBaseClashWindow(LevelAccessor world, LivingEntity source, LivingEntity target) {
        if (source == null || target == null) {
            return false;
        }
        CompoundTag sourceNbt = source.getPersistentData();
        double sx = sourceNbt.contains("x_pos_doma") ? sourceNbt.getDouble("x_pos_doma") : source.getX();
        double sy = sourceNbt.contains("y_pos_doma") ? sourceNbt.getDouble("y_pos_doma") : source.getY();
        double sz = sourceNbt.contains("z_pos_doma") ? sourceNbt.getDouble("z_pos_doma") : source.getZ();
        double tx = target.getX();
        double ty = target.getY() + (double)target.getBbHeight() * 0.5;
        double tz = target.getZ();
        double dx = sx - tx;
        double dy = sy - ty;
        double dz = sz - tz;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double threshold = Math.max(2.0, DomainClashXpMixin.jjkbrp$baseClashRange(world, source, sourceNbt) * 0.5);
        return distanceSq < threshold * threshold;
    }

    /**
     * Performs base clash range for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param sourceNbt source nbt used by this method.
     * @return the resulting base clash range value.
     */
    private static double jjkbrp$baseClashRange(LevelAccessor world, LivingEntity source, CompoundTag sourceNbt) {
        double radius = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(world, sourceNbt));
        return radius * (DomainClashXpMixin.jjkbrp$isOpenDomainState(source, sourceNbt) ? 18.0 : 2.0);
    }

    /**
     * Performs is open domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return whether is open domain state is true for the current runtime state.
     */
    private static boolean jjkbrp$isOpenDomainState(LivingEntity entity, CompoundTag nbt) {
        return DomainAddonUtils.isOpenDomainState(entity);
    }

    /**
     * Performs is explicit clash pair for this mixin.
     * @param a a used by this method.
     * @param b b used by this method.
     * @return whether is explicit clash pair is true for the current runtime state.
     */
    private static boolean jjkbrp$isExplicitClashPair(LivingEntity a, LivingEntity b) {
        if (a == null || b == null) {
            return false;
        }
        CompoundTag aNbt = a.getPersistentData();
        CompoundTag bNbt = b.getPersistentData();
        return DomainClashXpMixin.jjkbrp$matchesTargetUuid(aNbt, "jjkbrp_erosion_target_uuid", b) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(bNbt, "jjkbrp_erosion_target_uuid", a) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(aNbt, "jjkbrp_open_attacker_uuid", b) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(bNbt, "jjkbrp_open_attacker_uuid", a) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(aNbt, "jjkbrp_incomplete_wrap_target_uuid", b) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(bNbt, "jjkbrp_incomplete_wrap_target_uuid", a) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(aNbt, "jjkbrp_incomplete_wrapper_uuid", b) || DomainClashXpMixin.jjkbrp$matchesTargetUuid(bNbt, "jjkbrp_incomplete_wrapper_uuid", a);
    }

    /**
     * Performs matches target uuid for this mixin.
     * @param nbt persistent data container used by this helper.
     * @param key key used by this method.
     * @param target entity involved in the current mixin operation.
     * @return whether matches target uuid is true for the current runtime state.
     */
    private static boolean jjkbrp$matchesTargetUuid(CompoundTag nbt, String key, LivingEntity target) {
        if (nbt == null || target == null || key == null || key.isEmpty()) {
            return false;
        }
        if (!nbt.contains(key)) {
            return false;
        }
        String uuid = nbt.getString(key);
        return !uuid.isEmpty() && uuid.equals(target.getStringUUID());
    }

    /**
     * Resolves tracked opponent from the currently available runtime data.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return the resulting resolve tracked opponent value.
     */
    private static LivingEntity jjkbrp$resolveTrackedOpponent(LevelAccessor world, LivingEntity source, long currentTick) {
        LivingEntity trackedLiving;
        UUID uuid;
        ServerLevel sl;
        block13: {
            block12: {
                if (!(world instanceof ServerLevel)) break block12;
                sl = (ServerLevel)world;
                if (source != null) break block13;
            }
            return null;
        }
        CompoundTag sourceNbt = source.getPersistentData();
        if (!sourceNbt.contains("jjkbrp_last_clash_opponent_uuid")) {
            return null;
        }
        if (!DomainClashXpMixin.jjkbrp$hasRecentClashContact(sourceNbt, currentTick)) {
            return null;
        }
        String uuidStr = sourceNbt.getString("jjkbrp_last_clash_opponent_uuid");
        if (uuidStr == null || uuidStr.isEmpty()) {
            return null;
        }
        try {
            uuid = UUID.fromString(uuidStr);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
        Entity tracked = sl.getEntity(uuid);
        if (!(tracked instanceof LivingEntity) || (trackedLiving = (LivingEntity)tracked) == source) {
            return null;
        }
        if (!DomainClashXpMixin.jjkbrp$isDomainCasterState(trackedLiving)) {
            return null;
        }
        if (!DomainClashXpMixin.jjkbrp$isPlausibleLocalClashOpponent(world, source, trackedLiving)) {
            return null;
        }
        boolean explicitPair = DomainClashXpMixin.jjkbrp$isExplicitClashPair(source, trackedLiving);
        boolean recentPair = DomainClashXpMixin.jjkbrp$hasRecentMutualClashContact(source, trackedLiving, currentTick);
        boolean fallbackPair = DomainClashXpMixin.jjkbrp$isFallbackDirectClashNeighbor(world, source, trackedLiving);
        if (!(explicitPair || recentPair || fallbackPair)) {
            return null;
        }
        return trackedLiving;
    }

    /**
     * Performs has recent mutual clash contact for this mixin.
     * @param a a used by this method.
     * @param b b used by this method.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether has recent mutual clash contact is true for the current runtime state.
     */
    private static boolean jjkbrp$hasRecentMutualClashContact(LivingEntity a, LivingEntity b, long currentTick) {
        if (a == null || b == null) {
            return false;
        }
        CompoundTag aNbt = a.getPersistentData();
        CompoundTag bNbt = b.getPersistentData();
        if (!aNbt.contains("jjkbrp_last_clash_contact_tick") || !bNbt.contains("jjkbrp_last_clash_contact_tick")) {
            return false;
        }
        long aTick = aNbt.getLong("jjkbrp_last_clash_contact_tick");
        long bTick = bNbt.getLong("jjkbrp_last_clash_contact_tick");
        if (aTick <= 0L || bTick <= 0L) {
            return false;
        }
        long aDelta = currentTick - aTick;
        long bDelta = currentTick - bTick;
        if (aDelta < 0L || bDelta < 0L) {
            return false;
        }
        return aDelta <= 60L && bDelta <= 60L;
    }

    /**
     * Performs is domain caster state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is domain caster state is true for the current runtime state.
     */
    private static boolean jjkbrp$isDomainCasterState(LivingEntity entity) {
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getDouble("select") != 0.0 || nbt.getDouble("skill_domain") != 0.0 || nbt.getDouble("cnt_cover") > 0.0 || nbt.contains("x_pos_doma");
    }

    /**
     * Performs is active clash participant for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is active clash participant is true for the current runtime state.
     */
    private static boolean jjkbrp$isActiveClashParticipant(LivingEntity entity) {
        if (entity.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        return entity.getPersistentData().getDouble("cnt_cover") > 0.0;
    }

    /**
     * Performs is loss state for this mixin.
     * @param nbt persistent data container used by this helper.
     * @return whether is loss state is true for the current runtime state.
     */
    private static boolean jjkbrp$isLossState(CompoundTag nbt) {
        if (nbt == null) {
            return false;
        }
        return nbt.getBoolean("Failed") || nbt.getBoolean("DomainDefeated") || nbt.getBoolean("jjkbrp_was_failed") || nbt.getBoolean("jjkbrp_was_domain_defeated");
    }

    /**
     * Performs has pending within window for this mixin.
     * @param nbt persistent data container used by this helper.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether has pending within window is true for the current runtime state.
     */
    private static boolean jjkbrp$hasPendingWithinWindow(CompoundTag nbt, long currentTick) {
        if (nbt == null || !nbt.contains("jjkbrp_clash_pending_tick")) {
            return false;
        }
        long pendingTick = nbt.getLong("jjkbrp_clash_pending_tick");
        if (pendingTick <= 0L) {
            return false;
        }
        long delta = currentTick - pendingTick;
        return delta >= 0L && delta <= 5L;
    }

    /**
     * Performs is pending expired for this mixin.
     * @param nbt persistent data container used by this helper.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether is pending expired is true for the current runtime state.
     */
    private static boolean jjkbrp$isPendingExpired(CompoundTag nbt, long currentTick) {
        if (nbt == null || !nbt.contains("jjkbrp_clash_pending_tick")) {
            return false;
        }
        long pendingTick = nbt.getLong("jjkbrp_clash_pending_tick");
        if (pendingTick <= 0L) {
            return false;
        }
        long delta = currentTick - pendingTick;
        return delta > 5L;
    }

    /**
     * Performs has recent clash contact for this mixin.
     * @param nbt persistent data container used by this helper.
     * @param currentTick tick-based timing value used by this helper.
     * @return whether has recent clash contact is true for the current runtime state.
     */
    private static boolean jjkbrp$hasRecentClashContact(CompoundTag nbt, long currentTick) {
        if (nbt == null || !nbt.contains("jjkbrp_last_clash_contact_tick")) {
            return false;
        }
        long contactTick = nbt.getLong("jjkbrp_last_clash_contact_tick");
        if (contactTick <= 0L) {
            return false;
        }
        long delta = currentTick - contactTick;
        return delta >= 0L && delta <= 40L;
    }

    /**
     * Performs clear outcome tracking for this mixin.
     * @param nbt persistent data container used by this helper.
     */
    private static void jjkbrp$clearOutcomeTracking(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        if (nbt.contains("jjkbrp_clash_result_tick")) {
            nbt.remove("jjkbrp_clash_result_tick");
        }
        if (nbt.contains("jjkbrp_clash_pending_tick")) {
            nbt.remove("jjkbrp_clash_pending_tick");
        }
    }

    /**
     * Performs mark resolved for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @param tick tick-based timing value used by this helper.
     */
    private static void jjkbrp$markResolved(LivingEntity entity, long tick) {
        CompoundTag nbt = entity.getPersistentData();
        nbt.putLong("jjkbrp_clash_result_tick", tick);
        if (nbt.contains("jjkbrp_clash_pending_tick")) {
            nbt.remove("jjkbrp_clash_pending_tick");
        }
    }

                    // Once the pending clash window matures, resolve a decisive winner or loser outcome before awarding XP.
    private static void jjkbrp$resolveWinLose(LivingEntity winnerEntity, LivingEntity loserEntity, long currentTick) {
        if (winnerEntity == null || loserEntity == null) {
            return;
        }
        if (winnerEntity == loserEntity) {
            return;
        }
        DomainClashXpMixin.jjkbrp$markResolved(winnerEntity, currentTick);
        DomainClashXpMixin.jjkbrp$markResolved(loserEntity, currentTick);
        if (DomainAddonUtils.isIncompleteDomainState(winnerEntity)) {
            CompoundTag winData = winnerEntity.getPersistentData();
            winData.putBoolean("jjkbrp_incomplete_form_active", true);
            winData.putBoolean("jjkbrp_incomplete_session_active", true);
            winData.putBoolean("DomainAttack", false);
        }
        if (winnerEntity instanceof Player) {
            Player winnerPlayer = (Player)winnerEntity;
            boolean winnerGranted = DomainClashXpMixin.grantXpIfNotMax(winnerPlayer, 50);
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.
            DomainClashXpMixin.sendOutcomeMessage(winnerPlayer, loserEntity, "Domain Clash WIN!", ChatFormatting.GOLD, 50, winnerGranted);
        }
        if (loserEntity instanceof Player) {
            Player loserPlayer = (Player)loserEntity;
            boolean loserGranted = DomainClashXpMixin.grantXpIfNotMax(loserPlayer, 10);
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.
            DomainClashXpMixin.sendOutcomeMessage(loserPlayer, winnerEntity, "Domain Clash LOST.", ChatFormatting.GRAY, 10, loserGranted);
        }
    }

                    // If neither side establishes a decisive lead, the clash falls back to the shared tie-resolution path.
    private static void jjkbrp$resolveTie(LivingEntity a, LivingEntity b, long currentTick) {
        if (a == null || b == null) {
            return;
        }
        if (a == b) {
            return;
        }
        DomainClashXpMixin.jjkbrp$markResolved(a, currentTick);
        DomainClashXpMixin.jjkbrp$markResolved(b, currentTick);
        if (a instanceof Player) {
            Player playerA = (Player)a;
            boolean grantedA = DomainClashXpMixin.grantXpIfNotMax(playerA, 30);
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.
            DomainClashXpMixin.sendOutcomeMessage(playerA, b, "Domain Clash TIED.", ChatFormatting.YELLOW, 30, grantedA);
        }
        if (b instanceof Player) {
            Player playerB = (Player)b;
            boolean grantedB = DomainClashXpMixin.grantXpIfNotMax(playerB, 30);
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.
            DomainClashXpMixin.sendOutcomeMessage(playerB, a, "Domain Clash TIED.", ChatFormatting.YELLOW, 30, grantedB);
        }
    }

    /**
     * Sends the formatted clash result message to a player, including the awarded XP when applicable.
     * @param player entity involved in the current mixin operation.
     * @param opponent entity involved in the current mixin operation.
     * @param title title used by this method.
     * @param color color used by this method.
     * @param xpAmount xp amount used by this method.
     * @param xpGranted xp granted used by this method.
     */
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.

        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.

    // ===== PLAYER FEEDBACK =====
        // Push a formatted result message to the player so the XP award and clash outcome are visible immediately.
    private static void sendOutcomeMessage(Player player, LivingEntity opponent, String title, ChatFormatting color, int xpAmount, boolean xpGranted) {
        String icon = DomainClashXpMixin.jjkbrp$clashIcon(title);
        Object xpLabel = xpGranted ? "+" + xpAmount + " XP" : "MAX Lv";
        ChatFormatting xpColor = xpGranted ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
        String opponentName = opponent == null ? "Unknown" : opponent.getName().getString();
        MutableComponent message = Component.literal((String)(icon + " ")).withStyle(new ChatFormatting[]{color, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Clash] ").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)title).withStyle(new ChatFormatting[]{color, ChatFormatting.BOLD})).append((Component)Component.literal((String)" vs ").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)opponentName).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
        if (title != null && title.contains("TIE")) {
            message = message.append((Component)Component.literal((String)" \u2022 Both domains collapsed").withStyle(ChatFormatting.GOLD));
        }
        message = message.append((Component)Component.literal((String)" \u2022 ").withStyle(ChatFormatting.DARK_GRAY)).append((Component)Component.literal((String)xpLabel).withStyle(new ChatFormatting[]{xpColor, ChatFormatting.BOLD}));
        player.displayClientMessage((Component)message, false);
    }

    /**
     * Performs clash icon for this mixin.
     * @param title title used by this method.
     * @return the resulting clash icon value.
     */
    private static String jjkbrp$clashIcon(String title) {
        if (title == null) {
            return "\u2726";
        }
        if (title.contains("WIN")) {
            return "\u2604";
        }
        if (title.contains("LOST")) {
            return "\u2716";
        }
        if (title.contains("TIE")) {
            return "\u2694";
        }
        return "\u2726";
    }

    /**
     * Awards clash XP only when the player is not already at the mastery cap.
     * @param player entity involved in the current mixin operation.
     * @param amount amount used by this method.
     * @return whether grant xp if not max is true for the current runtime state.
     */

    // ===== XP HELPERS =====
    private static boolean grantXpIfNotMax(Player player, int amount) {
        boolean[] granted = new boolean[]{false};
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            if (data.getDomainMasteryLevel() >= 5) {
                return;
            }
            data.addDomainXP(amount);
            granted[0] = true;
            if (player instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)player;
                data.syncToClient(sp);
            }
        });
        return granted[0];
    }
}
