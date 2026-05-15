package net.mcreator.jujutsucraft.addon.clash.detect;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.STALE_SESSION_EXTRA_TICKS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.mcreator.jujutsucraft.addon.clash.ClashRegistry;
import net.mcreator.jujutsucraft.addon.clash.ClashDurationRules;
import net.mcreator.jujutsucraft.addon.clash.ClashSubsystem;
import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.clash.model.ParticipantPair;
import net.mcreator.jujutsucraft.addon.clash.model.ParticipantSnapshot;
import net.mcreator.jujutsucraft.addon.clash.power.PowerCalculator;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ClashDetector {
    private final ClashRegistry registry;

    public ClashDetector(ClashRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void tick(ServerLevel level, long serverTick) {
        Objects.requireNonNull(level, "level");

        List<LivingEntity> activeCandidates = new ArrayList<>();
        Set<UUID> activeUuids = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!isActiveDomain(living)) {
                continue;
            }
            activeCandidates.add(living);
            activeUuids.add(living.getUUID());
            registry.registerCandidate(living);
        }

        List<UUID> previouslyTracked = new ArrayList<>(registry.candidates());
        for (UUID prev : previouslyTracked) {
            if (activeUuids.contains(prev)) {
                continue;
            }
            if (registry.sessionsContaining(prev).isEmpty()) {
                registry.unregisterCandidate(prev);
            }
        }

        double mapRadius = readMapRadius(level);

        int n = activeCandidates.size();
        List<UUID> uuids = new ArrayList<>(n);
        List<Vec3> centers = new ArrayList<>(n);
        List<Double> radii = new ArrayList<>(n);
        List<LivingEntity> casters = new ArrayList<>(n);
        for (LivingEntity candidate : activeCandidates) {
            uuids.add(candidate.getUUID());
            centers.add(domainCenter(candidate));
            radii.add(clashRadius(candidate, mapRadius));
            casters.add(candidate);
        }
        createSessionsForOverlappingPairs(uuids, centers, radii, casters, serverTick);

        for (ClashSession session : new ArrayList<>(registry.activeSessions())) {
            if (session.resolved()) {
                continue;
            }
            LivingEntity casterA = session.casterA.get();
            LivingEntity casterB = session.casterB.get();
            if (casterA == null || casterB == null) {
                cancelSession(session);
                continue;
            }
            if (!isActiveDomain(casterA) || !isActiveDomain(casterB)) {
                cancelSession(session);
                continue;
            }
            if (!OverlapCalculator.overlaps(domainCenter(casterA), clashRadius(casterA, mapRadius), domainCenter(casterB), clashRadius(casterB, mapRadius))) {
                cancelSession(session);
                continue;
            }
            if (serverTick - session.createdAtServerTick > (long) session.initialDurationTicks + STALE_SESSION_EXTRA_TICKS) {
                cancelSession(session);
                continue;
            }
        }
    }

    void createSessionsForOverlappingPairs(
        List<UUID> uuids,
        List<Vec3> centers,
        List<Double> radii,
        List<LivingEntity> casters,
        long serverTick
    ) {
        int n = uuids.size();
        for (int i = 0; i < n; i++) {
            UUID idA = uuids.get(i);
            for (int j = i + 1; j < n; j++) {
                UUID idB = uuids.get(j);
                ParticipantPair pair = ParticipantPair.of(idA, idB);
                if (registry.hasSession(pair)) {
                    continue;
                }
                if (!OverlapCalculator.overlaps(centers.get(i), radii.get(i), centers.get(j), radii.get(j))) {
                    continue;
                }
                int durationTicks = ClashDurationRules.durationTicks(DomainForm.resolve(casters.get(i)), DomainForm.resolve(casters.get(j)));
                ClashSession created = registry.create(pair, casters.get(i), casters.get(j), serverTick, durationTicks);
                if (created != null) {
                    relocateParticipantsIntoSharedDomain(casters.get(i), casters.get(j), centers.get(i), centers.get(j));
                    ParticipantSnapshot snapA = ParticipantSnapshot.capture(casters.get(i), radii.get(i));
                    ParticipantSnapshot snapB = ParticipantSnapshot.capture(casters.get(j), radii.get(j));
                    if (snapA != null && snapB != null) {
                        created.setClashPower(PowerCalculator.compute(snapA, snapB.form()), PowerCalculator.compute(snapB, snapA.form()));
                    }
                    ClashSubsystem.getInstance().syncNetwork().sendInitial(created);
                }
            }
        }
    }


    private void relocateParticipantsIntoSharedDomain(LivingEntity a, LivingEntity b, Vec3 centerA, Vec3 centerB) {
        if (a == null || b == null || centerA == null || centerB == null || a.level().isClientSide()) {
            return;
        }
        double physicalRadiusA = physicalDomainRadius(a);
        double physicalRadiusB = physicalDomainRadius(b);
        Vec3 smallCenter = physicalRadiusA <= physicalRadiusB ? centerA : centerB;
        double smallRadius = Math.max(2.0, Math.min(physicalRadiusA, physicalRadiusB));
        Vec3 direction = centerB.subtract(centerA);
        if (direction.lengthSqr() < 1.0E-4) {
            direction = new Vec3(1.0, 0.0, 0.0);
        } else {
            direction = direction.normalize();
        }
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        } else {
            side = side.normalize();
        }
        double spacing = Math.max(0.5, Math.min(1.5, smallRadius * 0.10));
        Vec3 anchor = smallCenter;
        teleportIntoClash(a, anchor.add(side.scale(spacing)));
        teleportIntoClash(b, anchor.subtract(side.scale(spacing)));
    }

    private double physicalDomainRadius(LivingEntity entity) {
        if (entity == null) {
            return 22.0;
        }
        try {
            return Math.max(2.0, DomainAddonUtils.getActualDomainRadius(entity.level(), entity.getPersistentData()));
        } catch (Exception ignored) {
            return 22.0;
        }
    }
    private void teleportIntoClash(LivingEntity entity, Vec3 pos) {
        if (entity == null || pos == null) {
            return;
        }
        entity.stopRiding();
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
        } else {
            entity.teleportTo(pos.x, pos.y, pos.z);
        }
        entity.fallDistance = 0.0F;
    }

    private boolean isActiveDomain(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        MobEffect domainEffect = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        return isActiveDomainFromFlags(entity.hasEffect(domainEffect), entity.isRemoved(), entity.isDeadOrDying());
    }

    static boolean isActiveDomainFromFlags(boolean hasDomainExpansion, boolean isRemoved, boolean isDeadOrDying) {
        return hasDomainExpansion && !isRemoved && !isDeadOrDying;
    }

    private double clashRadius(LivingEntity entity, double mapRadius) {
        if (entity != null && DomainForm.resolve(entity) == DomainForm.OPEN) {
            return Math.max(mapRadius, DomainAddonUtils.getOpenDomainRange(entity.level(), entity));
        }
        ParticipantSnapshot snap = ParticipantSnapshot.capture(entity, mapRadius);
        return snap == null ? mapRadius : snap.clashRadius();
    }

    private Vec3 domainCenter(LivingEntity entity) {
        return DomainAddonUtils.getDomainCenter(entity);
    }

    private double readMapRadius(ServerLevel level) {
        try {
            return JujutsucraftModVariables.MapVariables.get(level).DomainExpansionRadius;
        } catch (Exception ignored) {
            return 22.0;
        }
    }

    private void cancelSession(ClashSession session) {
        if (session.resolved()) {
            return;
        }
        LivingEntity casterA = session.casterA.get();
        LivingEntity casterB = session.casterB.get();
        ServerLevel sessionLevel = null;
        if (casterA != null && casterA.level() instanceof ServerLevel aLevel) {
            sessionLevel = aLevel;
        } else if (casterB != null && casterB.level() instanceof ServerLevel bLevel) {
            sessionLevel = bLevel;
        }
        if (sessionLevel != null) {
            ClashSubsystem.getInstance().resolver().cancel(session, sessionLevel);
            return;
        }
        session.markCancelled();
        registry.remove(session.pair);
    }

    void runStaleSessionWatchdog(long serverTick) {
        for (ClashSession session : new ArrayList<>(registry.activeSessions())) {
            if (session.resolved()) {
                continue;
            }
            if (serverTick - session.createdAtServerTick > (long) session.initialDurationTicks + STALE_SESSION_EXTRA_TICKS) {
                cancelSession(session);
            }
        }
    }
}








