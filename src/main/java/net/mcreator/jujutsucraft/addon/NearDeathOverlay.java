package net.mcreator.jujutsucraft.addon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Full-screen vignette overlay shown during the RCT Level 3 near-death window.
 * Only rendered when:
 * <ul>
 *   <li>RCT Level 3 mastery is unlocked (via {@link ModNetworking.ClientNearDeathCdCache#rctLevel3Unlocked})</li>
 *   <li>The near-death state is active (server-set via {@link NearDeathClientState})</li>
 * </ul>
 * The vignette pulses faster as the window runs out, and a color-coded timer bar
 * sits at the bottom of the screen.
 *
 * @see NearDeathClientState
 * @see NearDeathCdOverlay
 * @see ModNetworking.ClientNearDeathCdCache
 */

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NearDeathOverlay {
    private static final int MAX_TICKS = 20;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("near_death", NearDeathOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int sw, int sh) {
        if (!ModNetworking.ClientNearDeathCdCache.rctLevel3Unlocked) return;
        if (!NearDeathClientState.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int ticksLeft = NearDeathClientState.getTicksRemaining();
        float progress = Math.max(0.0f, (float) ticksLeft / 20.0f);
        float urgency = 1.0f - progress;
        float pulse = (float) (0.85 + 0.15 * Math.sin(System.currentTimeMillis() * 0.011));
        int vignetteAlpha = (int) ((160.0f + 80.0f * urgency) * pulse);
        vignetteAlpha = Math.min(245, vignetteAlpha);
        NearDeathOverlay.drawVignette(gfx, sw, sh, vignetteAlpha);
        NearDeathOverlay.drawTimerBar(gfx, sw, sh, progress);
    }

    private static void drawVignette(GuiGraphics gfx, int sw, int sh, int baseAlpha) {
        int layers = 8;
        int unit = Math.min(sw, sh) / 14;
        for (int i = 0; i < layers; ++i) {
            float t = (float) (layers - i) / (float) layers;
            int a = (int) ((float) baseAlpha * t * t);
            if (a <= 0) continue;
            int color = a << 24;
            int m = i * unit;
            gfx.fill(0, 0, sw, m, color);
            gfx.fill(0, sh - m, sw, sh, color);
            gfx.fill(0, m, m, sh - m, color);
            gfx.fill(sw - m, m, sw, sh - m, color);
        }
    }

    private static void drawTimerBar(GuiGraphics gfx, int sw, int sh, float progress) {
        int barH = 4;
        int barY = sh - barH - 2;
        gfx.fill(0, barY, sw, barY + barH, 0x55000000);
        int barWidth = Math.max(1, (int) ((float) sw * progress));
        int color = progress > 0.5f ? 0xFF00FF00 : (progress > 0.25f ? 0xFFFFAA00 : 0xFFFF0000);
        gfx.fill(0, barY, barWidth, barY + barH, color);
    }
}
