package net.mcreator.jujutsucraft.addon.clash.client;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.HUD_SNAPSHOT_TTL_TICKS_DEFAULT;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.mcreator.jujutsucraft.addon.clash.net.DomainClashHudSnapshotPacket;

public final class ClientClashCache {
    public static final ClientClashCache INSTANCE = new ClientClashCache();

    private final Map<UUID, CachedSnapshot> snapshots = new HashMap<>();
    private @Nullable OutcomeBanner banner;

    private ClientClashCache() {
    }

    public void accept(@Nullable DomainClashHudSnapshotPacket packet, long clientTick) {
        if (packet == null) {
            return;
        }
        long normalizedTick = resolveUsableClientTick(clientTick);
        CachedSnapshot previous = this.snapshots.get(packet.sessionId);
        this.snapshots.put(packet.sessionId, new CachedSnapshot(packet, normalizedTick));
        ClashOutcome outcome = packet.outcome;
        if (outcome == ClashOutcome.WINNER_A || outcome == ClashOutcome.WINNER_B || outcome == ClashOutcome.TIE) {
            boolean localWon = outcome == ClashOutcome.TIE || packet.casterPower > packet.opponentPower;
            this.banner = new OutcomeBanner(packet.sessionId, outcome, localWon, normalizedTick);
        } else if (outcome == ClashOutcome.CANCELLED && this.banner != null && this.banner.sessionId.equals(packet.sessionId)) {
            this.banner = null;
        }
    }

    public void pruneExpired(long clientTick) {
        Iterator<Map.Entry<UUID, CachedSnapshot>> it = this.snapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CachedSnapshot> entry = it.next();
            long receiptTick = entry.getValue().receiptTick;
            if (receiptTick > 0L && clientTick - receiptTick > HUD_SNAPSHOT_TTL_TICKS_DEFAULT) {
                it.remove();
            }
        }
        if (this.banner != null && this.banner.isExpired(clientTick)) {
            this.banner = null;
        }
    }

    @Nullable
    public CachedSnapshot chooseRenderable(long clientTick, UUID localPlayerId) {
        if (localPlayerId == null) {
            return null;
        }
        CachedSnapshot best = null;
        for (CachedSnapshot snap : this.snapshots.values()) {
            long receiptTick = snap.receiptTick;
            if (receiptTick > 0L && clientTick - receiptTick > HUD_SNAPSHOT_TTL_TICKS_DEFAULT) {
                continue;
            }
            DomainClashHudSnapshotPacket pkt = snap.packet;
            if (best == null) {
                best = snap;
                continue;
            }
            boolean snapIncludesLocal = localPlayerId.equals(pkt.casterSideUuid) || localPlayerId.equals(pkt.opponentSideUuid);
            boolean bestIncludesLocal = localPlayerId.equals(best.packet.casterSideUuid) || localPlayerId.equals(best.packet.opponentSideUuid);
            if (snapIncludesLocal && !bestIncludesLocal) {
                best = snap;
                continue;
            }
            if (!snapIncludesLocal && bestIncludesLocal) {
                continue;
            }
            int cmp = Integer.compare(pkt.remainingTicks, best.packet.remainingTicks);
            if (cmp < 0) {
                best = snap;
            } else if (cmp == 0 && snap.receiptTick > best.receiptTick) {
                best = snap;
            }
        }
        return best;
    }

    @Nullable
    public OutcomeBanner outcomeBanner(long clientTick) {
        if (this.banner == null || this.banner.isExpired(clientTick)) {
            return null;
        }
        return this.banner;
    }

    public void clear() {
        this.snapshots.clear();
        this.banner = null;
    }

    int size() {
        return this.snapshots.size();
    }

    public static void registerClientHandler() {
        DomainClashHudSnapshotPacket.setClientHandler((pkt, tick) -> INSTANCE.accept(pkt, tick.longValue()));
    }

    private static long resolveUsableClientTick(long clientTick) {
        if (clientTick > 0L) {
            return clientTick;
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                return mc.level.getGameTime();
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    public static final class CachedSnapshot {
        public final DomainClashHudSnapshotPacket packet;
        public final long receiptTick;
        public CachedSnapshot(DomainClashHudSnapshotPacket packet, long receiptTick) {
            this.packet = packet;
            this.receiptTick = receiptTick;
        }
    }

    public static final class OutcomeBanner {
        public final UUID sessionId;
        public final ClashOutcome outcome;
        public final boolean localWon;
        public final long receiptTick;

        public OutcomeBanner(UUID sessionId, ClashOutcome outcome, boolean localWon, long receiptTick) {
            this.sessionId = sessionId;
            this.outcome = outcome;
            this.localWon = localWon;
            this.receiptTick = receiptTick;
        }

        public boolean isExpired(long clientTick) {
            return this.receiptTick > 0L && clientTick - this.receiptTick > 60L;
        }
    }
}
