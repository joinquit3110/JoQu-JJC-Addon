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
 * Network payload used to synchronize limb state from server to client.
 *
 * <p>The packet contains the target entity id plus, for each limb, a compact state ordinal byte and a
 * floating-point regeneration progress value. Clients store the result in
 * {@link net.mcreator.jujutsucraft.addon.limb.ClientLimbCache} for rendering.</p>
 */
public class LimbSyncPacket {
    /** Runtime id of the entity whose limb state is being synchronized. */
    private final int entityId;
    /** Snapshot of each limb's current state. */
    private final Map<LimbType, LimbState> states;
    /** Snapshot of each limb's current regeneration progress. */
    private final Map<LimbType, Float> regenProgress;

    /**
     * Builds a sync packet directly from live limb capability data.
     *
     * @param entityId runtime entity id
     * @param data server-side limb data snapshot source
     */
    public LimbSyncPacket(int entityId, LimbData data) {
        this.entityId = entityId;
        this.states = data.getAllStates();
        this.regenProgress = data.getAllRegenProgress();
    }

    /**
     * Internal constructor used by the decode path.
     *
     * @param entityId runtime entity id
     * @param states decoded state map
     * @param regenProgress decoded regeneration map
     */
    private LimbSyncPacket(int entityId, Map<LimbType, LimbState> states, Map<LimbType, Float> regenProgress) {
        this.entityId = entityId;
        this.states = states;
        this.regenProgress = regenProgress;
    }

    /**
     * Encodes this packet into the network buffer.
     *
     * @param pkt packet being written
     * @param buf target byte buffer
     */
    public static void encode(LimbSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
        for (LimbType type : LimbType.values()) {
            // Each limb contributes one ordinal byte and one float progress value.
            buf.writeByte(pkt.states.getOrDefault((Object)type, LimbState.INTACT).ordinal());
            buf.writeFloat(pkt.regenProgress.getOrDefault((Object)type, Float.valueOf(0.0f)).floatValue());
        }
    }

    /**
     * Decodes a limb sync packet from the network buffer.
     *
     * @param buf source byte buffer
     * @return decoded limb sync packet
     */
    public static LimbSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        EnumMap<LimbType, LimbState> states = new EnumMap<LimbType, LimbState>(LimbType.class);
        EnumMap<LimbType, Float> regen = new EnumMap<LimbType, Float>(LimbType.class);
        for (LimbType type : LimbType.values()) {
            states.put(type, LimbState.fromOrdinal(buf.readByte()));
            regen.put(type, Float.valueOf(buf.readFloat()));
        }
        return new LimbSyncPacket(entityId, states, regen);
    }

    /**
     * Handles the packet on receipt and updates the client-side limb cache.
     *
     * @param pkt decoded packet payload
     * @param ctxSupplier network context supplier
     */
    public static void handle(LimbSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientLimbCache.update(pkt.entityId, pkt.states, pkt.regenProgress)));
        ctx.setPacketHandled(true);
    }

    /**
     * Sends limb state to every nearby player tracking the given entity.
     *
     * @param entity entity whose limbs changed
     * @param data current limb data snapshot
     */
    public static void sendToTrackingPlayers(LivingEntity entity, LimbData data) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        LimbSyncPacket packet = new LimbSyncPacket(entity.getId(), data);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), (Object)packet);
    }

    /**
     * Sends limb state directly to one player.
     *
     * @param target player who should receive the update
     * @param entity entity whose limb state is being synchronized
     * @param data current limb data snapshot
     */
    public static void sendToPlayer(ServerPlayer target, LivingEntity entity, LimbData data) {
        SimpleChannel channel = ModNetworking.CHANNEL;
        channel.send(PacketDistributor.PLAYER.with(() -> target), (Object)new LimbSyncPacket(entity.getId(), data));
    }
}
