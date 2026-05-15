package net.mcreator.jujutsucraft.addon.clash.net;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class DomainClashHudSnapshotPacket {
    private static volatile BiConsumer<DomainClashHudSnapshotPacket, Long> clientHandler = (pkt, tick) -> {};

    public final UUID sessionId;
    public final UUID casterSideUuid;
    public final UUID opponentSideUuid;
    public final float casterPower;
    public final float opponentPower;
    public final int remainingTicks;
    public final int casterFormId;
    public final int casterDomainId;
    public final int opponentFormId;
    public final int opponentDomainId;
    public final String casterName;
    public final String opponentName;
    @Nullable
    public final ClashOutcome outcome;

    public DomainClashHudSnapshotPacket(
        UUID sessionId,
        UUID casterSideUuid,
        UUID opponentSideUuid,
        float casterPower,
        float opponentPower,
        int remainingTicks,
        int casterFormId,
        int casterDomainId,
        int opponentFormId,
        int opponentDomainId,
        String casterName,
        String opponentName,
        @Nullable ClashOutcome outcome
    ) {
        this.sessionId = sessionId;
        this.casterSideUuid = casterSideUuid;
        this.opponentSideUuid = opponentSideUuid;
        this.casterPower = casterPower;
        this.opponentPower = opponentPower;
        this.remainingTicks = remainingTicks;
        this.casterFormId = casterFormId;
        this.casterDomainId = casterDomainId;
        this.opponentFormId = opponentFormId;
        this.opponentDomainId = opponentDomainId;
        this.casterName = casterName == null ? "" : casterName;
        this.opponentName = opponentName == null ? "" : opponentName;
        this.outcome = outcome;
    }

    public static void setClientHandler(BiConsumer<DomainClashHudSnapshotPacket, Long> handler) {
        clientHandler = handler == null ? (pkt, tick) -> {} : handler;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.sessionId);
        buf.writeUUID(this.casterSideUuid);
        buf.writeUUID(this.opponentSideUuid);
        buf.writeFloat(this.casterPower);
        buf.writeFloat(this.opponentPower);
        buf.writeVarInt(this.remainingTicks);
        buf.writeVarInt(this.casterFormId);
        buf.writeVarInt(this.casterDomainId);
        buf.writeVarInt(this.opponentFormId);
        buf.writeVarInt(this.opponentDomainId);
        buf.writeUtf(this.casterName, 64);
        buf.writeUtf(this.opponentName, 64);
        buf.writeByte(ClashOutcome.toByte(this.outcome));
    }

    public static DomainClashHudSnapshotPacket decode(FriendlyByteBuf buf) {
        UUID sessionId = buf.readUUID();
        UUID casterSideUuid = buf.readUUID();
        UUID opponentSideUuid = buf.readUUID();
        float casterPower = buf.readFloat();
        float opponentPower = buf.readFloat();
        int remainingTicks = buf.readVarInt();
        int casterFormId = buf.readVarInt();
        int casterDomainId = buf.readVarInt();
        int opponentFormId = buf.readVarInt();
        int opponentDomainId = buf.readVarInt();
        String casterName = buf.readUtf(64);
        String opponentName = buf.readUtf(64);
        ClashOutcome outcome = ClashOutcome.fromByte(buf.readByte());
        return new DomainClashHudSnapshotPacket(sessionId, casterSideUuid, opponentSideUuid, casterPower, opponentPower, remainingTicks, casterFormId, casterDomainId, opponentFormId, opponentDomainId, casterName, opponentName, outcome);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                clientHandler.accept(this, Long.valueOf(resolveClientTick()));
            }
        });
        ctx.setPacketHandled(true);
    }

    private static long resolveClientTick() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) return 0L;
            Object level = mcClass.getField("level").get(mc);
            if (level == null) return 0L;
            Object tick = level.getClass().getMethod("getGameTime").invoke(level);
            return tick instanceof Long l ? l.longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainClashHudSnapshotPacket that)) return false;
        return this.remainingTicks == that.remainingTicks
            && this.casterFormId == that.casterFormId
            && this.casterDomainId == that.casterDomainId
            && this.opponentFormId == that.opponentFormId
            && this.opponentDomainId == that.opponentDomainId
            && Float.compare(this.casterPower, that.casterPower) == 0
            && Float.compare(this.opponentPower, that.opponentPower) == 0
            && Objects.equals(this.sessionId, that.sessionId)
            && Objects.equals(this.casterSideUuid, that.casterSideUuid)
            && Objects.equals(this.opponentSideUuid, that.opponentSideUuid)
            && Objects.equals(this.casterName, that.casterName)
            && Objects.equals(this.opponentName, that.opponentName)
            && this.outcome == that.outcome;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sessionId, this.casterSideUuid, this.opponentSideUuid, Float.valueOf(this.casterPower), Float.valueOf(this.opponentPower), Integer.valueOf(this.remainingTicks), Integer.valueOf(this.casterFormId), Integer.valueOf(this.casterDomainId), Integer.valueOf(this.opponentFormId), Integer.valueOf(this.opponentDomainId), this.casterName, this.opponentName, this.outcome);
    }
}