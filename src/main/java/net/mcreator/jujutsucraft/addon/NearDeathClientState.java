package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.limb.NearDeathPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side state holder for the RCT Level 3 near-death window.
 *
 * <p>Only meaningful on the physical client (not the server).
 * Updated via {@link NearDeathPacket} sent from the server when the player
 * enters or exits the near-death window.
 *
 * @see NearDeathPacket
 * @see NearDeathOverlay
 * @see RCTLevel3Handler
 */

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT)
public class NearDeathClientState {
    private static boolean active = false;
    private static int ticksRemaining = 0;

    public static void update(boolean newActive, int ticks) {
        active = newActive;
        ticksRemaining = ticks;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getTicksRemaining() {
        return ticksRemaining;
    }

    public static void clientTick() {
        if (active && ticksRemaining > 0) {
            --ticksRemaining;
        }
    }
}
