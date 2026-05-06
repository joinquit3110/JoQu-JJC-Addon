package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.ClientPacketHandler;
import net.mcreator.jujutsucraft.addon.NearDeathClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.MOD)
/**
 * Client overlay responsible for the near-death warning vignette and countdown bar that appear while the player is in the addon near-death survival state.
 */
public class NearDeathOverlay {
    // Maximum number of client ticks used to normalize the near-death warning bar.
    private static final int MAX_TICKS = 20;

    @SubscribeEvent
    /**
     * Registers overlay with the appropriate Forge or client system.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("near_death", NearDeathOverlay::renderOverlay);
    }

    /**
     * Renders overlay for the current frame.
     * @param gui render context used to draw the current frame.
     * @param gfx render context used to draw the current frame.
     * @param partialTick tick-based timing value used by this operation.
     * @param sw screen or world coordinate used by this calculation.
     * @param sh screen or world coordinate used by this calculation.
     */
    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int sw, int sh) {
        if (!ClientPacketHandler.ClientNearDeathCdCache.rctLevel3Unlocked) {
            return;
        }
        if (!NearDeathClientState.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int ticksLeft = NearDeathClientState.getTicksRemaining();
        float progress = Math.max(0.0f, (float)ticksLeft / 20.0f);
        float urgency = 1.0f - progress;
        float pulse = (float)(0.85 + 0.15 * Math.sin((double)System.currentTimeMillis() * 0.011));
        int vignetteAlpha = (int)((160.0f + 80.0f * urgency) * pulse);
        vignetteAlpha = Math.min(245, vignetteAlpha);
        NearDeathOverlay.drawVignette(gfx, sw, sh, vignetteAlpha);
        NearDeathOverlay.drawTimerBar(gfx, sw, sh, progress);
    }

    /**
     * Draws vignette as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param sw screen or world coordinate used by this calculation.
     * @param sh screen or world coordinate used by this calculation.
     * @param baseAlpha base alpha used by this method.
     */
    private static void drawVignette(GuiGraphics gfx, int sw, int sh, int baseAlpha) {
        int layers = 8;
        int unit = Math.min(sw, sh) / 14;
        for (int i = 0; i < layers; ++i) {
            float t = (float)(layers - i) / (float)layers;
            int a = (int)((float)baseAlpha * t * t);
            if (a <= 0) continue;
            int color = a << 24;
            int m = i * unit;
            gfx.fill(0, 0, sw, m, color);
            gfx.fill(0, sh - m, sw, sh, color);
            gfx.fill(0, m, m, sh - m, color);
            gfx.fill(sw - m, m, sw, sh - m, color);
        }
    }

    /**
     * Draws timer bar as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param sw screen or world coordinate used by this calculation.
     * @param sh screen or world coordinate used by this calculation.
     * @param progress progress used by this method.
     */
    private static void drawTimerBar(GuiGraphics gfx, int sw, int sh, float progress) {
        int barH = 4;
        int barY = sh - barH - 2;
        gfx.fill(0, barY, sw, barY + barH, 0x55000000);
        int barWidth = Math.max(1, (int)((float)sw * progress));
        int color = progress > 0.5f ? -16711936 : (progress > 0.25f ? -22016 : -65536);
        gfx.fill(0, barY, barWidth, barY + barH, color);
    }
}

