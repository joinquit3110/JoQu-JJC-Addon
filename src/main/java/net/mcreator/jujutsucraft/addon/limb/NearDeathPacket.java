package net.mcreator.jujutsucraft.addon.limb;

/**
 * Network packet that notifies the client when the RCT Level 3 near-death
 * window begins or ends.
 *
 * @see NearDeathClientState
 * @see RCTLevel3Handler
 */

import java.util.function.Supplier;
import net.mcreator.jujutsucraft.addon.NearDeathClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class NearDeathPacket {
    private final boolean active;
    private final int ticksRemaining;

    public NearDeathPacket(boolean active, int ticksRemaining) {
        this.active = active;
        this.ticksRemaining = ticksRemaining;
    }

    public static void encode(NearDeathPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.active);
        buf.writeInt(pkt.ticksRemaining);
    }

    public static NearDeathPacket decode(FriendlyByteBuf buf) {
        return new NearDeathPacket(buf.readBoolean(), buf.readInt());
    }

    public static void handle(NearDeathPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NearDeathClientState.update(pkt.active, pkt.ticksRemaining)));
        ctx.setPacketHandled(true);
    }
}
