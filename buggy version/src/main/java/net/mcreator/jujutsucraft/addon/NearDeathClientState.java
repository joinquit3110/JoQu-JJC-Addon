package net.mcreator.jujutsucraft.addon;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT})
/**
 * Small client-only state container for the near-death vignette system. It stores whether the effect is active and how many client ticks remain on the warning timer.
 */
public class NearDeathClientState {
    // Whether the near-death warning overlay should currently be visible on the client.
    private static boolean active = false;
    // Remaining client ticks before the near-death warning state naturally expires.
    private static int ticksRemaining = 0;

    /**
     * Updates  using the latest available data.
     * @param newActive new active used by this method.
     * @param ticks tick-based timing value used by this operation.
     */
    public static void update(boolean newActive, int ticks) {
        active = newActive;
        ticksRemaining = ticks;
    }

    /**
     * Checks whether is active is true for the current addon state.
     * @return true when is active succeeds; otherwise false.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Returns ticks remaining for the current addon state.
     * @return the resolved ticks remaining.
     */
    public static int getTicksRemaining() {
        return ticksRemaining;
    }

    /**
     * Performs client tick for this addon component.
     */
    public static void clientTick() {
        if (active && ticksRemaining > 0) {
            --ticksRemaining;
        }
    }
}

