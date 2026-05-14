package net.mcreator.jujutsucraft.addon.clash;

import java.util.Objects;

import net.mcreator.jujutsucraft.addon.clash.detect.ClashDetector;
import net.mcreator.jujutsucraft.addon.clash.resolve.ClashResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge event subscriber that drives the Domain Clash subsystem once per server tick.
 *
 * <p>The handler subscribes to {@link TickEvent.ServerTickEvent} and acts only on
 * {@link TickEvent.Phase#END}. On each end-of-tick, it walks every loaded
 * {@link ServerLevel} and invokes the two server-authoritative stages of the pipeline in
 * order: {@link ClashDetector#tick(ServerLevel, long)} first (candidate registration and
 * session creation), then {@link ClashResolver#tickSessions(ServerLevel, long)}
 * (session decrement, sampling, and terminal transitions). Running the detector before the
 * resolver on the same tick ensures a newly-overlapping pair gets a session created this
 * tick and its initial duration decrement is deferred to the next tick, matching the
 * invariants the resolver's idempotency guards assume.
 *
 * <h2>Server tick source</h2>
 * The tick counter passed into both stages is {@link MinecraftServer#getTickCount()}, read
 * from the event's {@code getServer()}. When the event does not carry a server reference
 * (should not happen in practice but the API marks it {@code @Nullable}), the handler
 * falls back to {@link ServerLifecycleHooks#getCurrentServer()} so the tick counter is
 * still consistent across the detector and resolver on the same tick. If neither source
 * resolves a server, the handler short-circuits; there is nothing to tick without a live
 * server.
 *
 * <h2>Threading</h2>
 * Forge posts {@code ServerTickEvent} on the server-tick thread. All subsystem state is
 * single-threaded by design, so no synchronization is performed here; this handler and
 * every downstream collaborator rely on that invariant.
 *
 * <h2>Wiring</h2>
 * This class is instantiated and registered onto the Forge event bus by the addon's main
 * class (task 12.3). This class only <em>implements</em> the handler; it does not register
 * itself.
 *
 * <p>Requirements: 1.1, 1.2, 3.2.
 */
public final class ClashTickHandler {

    private final ClashDetector detector;
    private final ClashResolver resolver;

    /**
     * Creates a handler bound to the given detector and resolver. Both collaborators are
     * required; this class does not own a singleton for either so that tests and the
     * subsystem facade can drive it with stubs or alternate wirings.
     *
     * @param detector the {@link ClashDetector} to invoke first on each server-tick end
     * @param resolver the {@link ClashResolver} to invoke after the detector on each
     *                 server-tick end
     */
    public ClashTickHandler(ClashDetector detector, ClashResolver resolver) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Single Forge-subscribed entry point. Runs the detector and resolver over every loaded
     * {@link ServerLevel} on {@link TickEvent.Phase#END}; all other phases are ignored.
     *
     * <p>Requirements: 1.1, 1.2, 3.2.
     *
     * @param event the incoming {@link TickEvent.ServerTickEvent}; only the {@code END}
     *              phase is processed
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
        }
        if (server == null) {
            return;
        }
        long serverTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            detector.tick(level, serverTick);
            resolver.tickSessions(level, serverTick);
        }
    }
}
