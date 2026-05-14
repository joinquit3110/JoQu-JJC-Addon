package net.mcreator.jujutsucraft.addon.clash;

import java.util.ArrayList;
import java.util.Objects;

import net.mcreator.jujutsucraft.addon.clash.detect.ClashDetector;
import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.clash.net.ClashSyncNetwork;
import net.mcreator.jujutsucraft.addon.clash.power.PowerCalculator;
import net.mcreator.jujutsucraft.addon.clash.resolve.ClashResolver;
import net.mcreator.jujutsucraft.addon.clash.resolve.MasteryXpGrant;
import net.mcreator.jujutsucraft.addon.clash.resolve.OutcomeDelivery;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Singleton facade that owns and wires the Domain Clash subsystem.
 */
public final class ClashSubsystem {

    private static final ClashSubsystem INSTANCE = new ClashSubsystem();

    private final ClashRegistry registry;
    private final ClashDetector detector;
    private final OutcomeDelivery delivery;
    private final MasteryXpGrant masteryXpGrant;
    private final ClashSyncNetwork syncNetwork;
    private final ClashResolver resolver;
    private final PowerCalculator powerCalculator;
    private final ClashTickHandler tickHandler;

    private ClashSubsystem() {
        this.registry = new ClashRegistry();
        this.detector = new ClashDetector(this.registry);
        this.delivery = new OutcomeDelivery();
        this.masteryXpGrant = new MasteryXpGrant();
        this.syncNetwork = new ClashSyncNetwork(() -> {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            return server == null ? 0L : server.getTickCount();
        });
        this.resolver = new ClashResolver(this.registry, this.delivery, this.masteryXpGrant, this.syncNetwork);
        this.powerCalculator = new PowerCalculator();
        this.tickHandler = new ClashTickHandler(this.detector, this.resolver);
    }

    public static ClashSubsystem getInstance() {
        return INSTANCE;
    }

    public ClashRegistry registry() {
        return registry;
    }

    public ClashDetector detector() {
        return detector;
    }

    public ClashResolver resolver() {
        return resolver;
    }

    public PowerCalculator powerCalculator() {
        return powerCalculator;
    }

    public ClashSyncNetwork syncNetwork() {
        return syncNetwork;
    }

    public ClashTickHandler tickHandler() {
        return tickHandler;
    }

    public void onServerTickEnd(ServerLevel level, long serverTick) {
        Objects.requireNonNull(level, "level");
        detector.tick(level, serverTick);
        resolver.tickSessions(level, serverTick);
    }

    public void onServerStopping(MinecraftServer server) {
        for (ClashSession session : new ArrayList<>(registry.activeSessions())) {
            ServerLevel level = resolveLevel(server, session);
            if (level != null) {
                resolver.cancel(session, level);
            }
        }
        registry.clear();
        syncNetwork.reset();
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (ClashSession session : new ArrayList<>(registry.sessionsContaining(player.getUUID()))) {
            resolver.cancel(session, level);
        }
    }

    public void onLivingDeath(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        for (ClashSession session : new ArrayList<>(registry.sessionsContaining(entity.getUUID()))) {
            resolver.resolveDeath(session, level, entity);
        }
    }

    private static ServerLevel resolveLevel(MinecraftServer server, ClashSession session) {
        if (server == null || session == null) {
            return null;
        }
        LivingEntity casterA = session.casterA.get();
        if (casterA != null && casterA.level() instanceof ServerLevel level) {
            return level;
        }
        LivingEntity casterB = session.casterB.get();
        if (casterB != null && casterB.level() instanceof ServerLevel level) {
            return level;
        }
        return server.getAllLevels().iterator().hasNext() ? server.getAllLevels().iterator().next() : null;
    }
}
