package net.mcreator.jujutsucraft.addon.limb;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.ClientLimbCache;
import net.mcreator.jujutsucraft.addon.limb.LimbData;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network packet that syncs limb state (INTACT / SEVERED / REVERSING) and
 * regeneration progress from server to all nearby tracking clients and to
 * the owning player themselves.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Server-side event fires (limb severed, regen starts/updates, regen completes).</li>
 *   <li>{@link #sendToTrackingPlayers} broadcasts to every player within 128 blocks.</li>
 *   <li>{@link #sendToPlayer} delivers the same data to the owner.</li>
 *   <li>Client receives via {@link #handle} and stores in {@link ClientLimbCache}.</li>
 * </ol>
 *
 * <h2>Culling</h2>
 * Tracking range is capped at 128 blocks; entities outside this range receive
 * no updates and rely on their own cached state (which will not be fresh).
 *
 * @see ClientLimbCache
 * @see ModNetworking#CHANNEL
 */

public class LimbSyncPacket {
    private final int entityId;
    private final Map<LimbType, LimbState> states;
    private final Map<LimbType, Float> regenProgress;

    public LimbSyncPacket(int entityId, LimbData data) {
        this.entityId = entityId;
        this.states = data.getAllStates();
        this.regenProgress = data.getAllRegenProgress();
    }

    private LimbSyncPacket(int entityId, Map<LimbType, LimbState> states, Map<LimbType, Float> regenProgress) {
        this.entityId = entityId;
        this.states = states;
        this.regenProgress = regenProgress;
    }

    public static void encode(LimbSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
        for (LimbType type : LimbType.values()) {
            buf.writeByte(pkt.states.getOrDefault(type, LimbState.INTACT).ordinal());
            buf.writeFloat(pkt.regenProgress.getOrDefault(type, 0.0f));
        }
    }

    public static LimbSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        EnumMap<LimbType, LimbState> states = new EnumMap<>(LimbType.class);
        EnumMap<LimbType, Float> regen = new EnumMap<>(LimbType.class);
        for (LimbType type : LimbType.values()) {
            states.put(type, LimbState.fromOrdinal(buf.readByte()));
            regen.put(type, buf.readFloat());
        }
        return new LimbSyncPacket(entityId, states, regen);
    }

    public static void handle(LimbSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLimbCache.update(pkt.entityId, pkt.states, pkt.regenProgress)));
        ctx.setPacketHandled(true);
    }

    public static void sendToTrackingPlayers(LivingEntity entity, LimbData data) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        LimbSyncPacket packet = new LimbSyncPacket(entity.getId(), data);
        SimpleChannel channel = ModNetworking.CHANNEL;
        for (ServerPlayer tracking : serverLevel.getPlayers(p -> p != entity && p.distanceToSqr(entity) < 128.0 * 128.0)) {
            channel.send(PacketDistributor.PLAYER.with(() -> tracking), packet);
        }
    }

    public static void sendToPlayer(ServerPlayer target, LivingEntity entity, LimbData data) {
        SimpleChannel channel = ModNetworking.CHANNEL;
        channel.send(PacketDistributor.PLAYER.with(() -> target), new LimbSyncPacket(entity.getId(), data));
    }
}
