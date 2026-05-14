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
