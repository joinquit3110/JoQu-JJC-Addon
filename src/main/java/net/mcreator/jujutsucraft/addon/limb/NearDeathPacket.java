package net.mcreator.jujutsucraft.addon.limb;

import java.util.function.Supplier;
import net.mcreator.jujutsucraft.addon.NearDeathClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Network payload that synchronizes the near-death UI state to the client.
 *
 * <p>The payload is intentionally small: a boolean flag indicating whether near-death is active and an
 * integer countdown showing how many ticks remain in the current window.</p>
 */
public class NearDeathPacket {
    /** Whether the client should currently display the near-death state. */
    private final boolean active;
    /** Remaining near-death ticks to display on the client. */
    private final int ticksRemaining;

    /**
     * Creates a near-death sync packet.
     *
     * @param active whether near-death is active
     * @param ticksRemaining remaining countdown in ticks
     */
    public NearDeathPacket(boolean active, int ticksRemaining) {
        this.active = active;
        this.ticksRemaining = ticksRemaining;
    }

    /**
     * Writes the packet payload into the network buffer.
     *
     * @param pkt packet being encoded
     * @param buf target network buffer
     */
    public static void encode(NearDeathPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.active);
        buf.writeInt(pkt.ticksRemaining);
    }

    /**
     * Reads the packet payload from the network buffer.
     *
     * @param buf source network buffer
     * @return decoded near-death packet
     */
    public static NearDeathPacket decode(FriendlyByteBuf buf) {
        return new NearDeathPacket(buf.readBoolean(), buf.readInt());
    }

    /**
     * Applies the payload on the receiving side by updating the client near-death state holder.
     *
     * @param pkt decoded packet
     * @param ctxSupplier network context supplier
     */
    public static void handle(NearDeathPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> NearDeathClientState.update(pkt.active, pkt.ticksRemaining)));
        ctx.setPacketHandled(true);
    }
}
