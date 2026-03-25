package net.mcreator.jujutsucraft.addon;

import java.util.Objects;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD overlay that displays the player's current Black Flash activation chance
 * as a percentage next to the crosshair.
 *
 * <h2>Visibility</h2>
 * Only shown when {@link ModNetworking.ClientBlackFlashCache#bfPercent} is
 * above 0.01%. The colour and behaviour scale with the percentage:
 * <ul>
 *   <li>&lt;0.3%: dim gray — low chance</li>
 *   <li>0.3–0.6%: yellow-green — building up</li>
 *   <li>0.6–1.0%: cyan — decent chance</li>
 *   <li>1–5%: orange — strong chance</li>
 *   <li>5–10%: red — high chance</li>
 *   <li>≥10%: pulsing red/gold — maximum ("MAX")</li>
 * </ul>
 *
 * The text shakes when the percentage changes rapidly (e.g. entering combat)
 * and a small spark glyph (✦) appears when near maximum.
 *
 * @see ModNetworking.ClientBlackFlashCache
 */

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BlackFlashHudOverlay {
    private static float lastPercent = 0.0f;
    private static float shakeIntensity = 0.0f;
    private static long lastChangeTime = 0L;

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("black_flash_chance", BlackFlashHudOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        int shadowColor;
        int color;
        boolean isMax;
        float bfPercent = ModNetworking.ClientBlackFlashCache.bfPercent;
        if (bfPercent < 0.01f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        long now = System.currentTimeMillis();
        float delta = Math.abs(bfPercent - lastPercent);
        if (delta > 0.1f) {
            shakeIntensity = Math.min(4.0f, delta * 2.0f);
            lastChangeTime = now;
        }
        lastPercent = bfPercent;
        long timeSinceChange = now - lastChangeTime;
        shakeIntensity = timeSinceChange < 300L ? Math.max(0.0f, shakeIntensity * (1.0f - (float)timeSinceChange / 300.0f)) : 0.0f;
        String text = bfPercent < 1.0f ? String.format("%.1f%%", Float.valueOf(bfPercent)) : String.format("%.0f%%", Float.valueOf(bfPercent));
        int textWidth = font.width(text);
        int drawX = cx + 14;
        Objects.requireNonNull(font);
        int drawY = cy - 9 / 2;
        if (shakeIntensity > 0.1f) {
            drawX += (int)((Math.random() - 0.5) * (double)shakeIntensity * 2.0);
            drawY += (int)((Math.random() - 0.5) * (double)shakeIntensity * 2.0);
        }
        float pulse = (float)(Math.sin((double)now * 0.006) * 0.5 + 0.5);
        boolean bl = isMax = bfPercent >= 10.0f;
        if (isMax) {
            float maxPulse = (float)(Math.sin((double)now * 0.012) * 0.5 + 0.5);
            int r = (int)(200.0f + 55.0f * maxPulse);
            int g = (int)(20.0f * (1.0f - maxPulse));
            int b = (int)(20.0f * (1.0f - maxPulse));
            color = 0xEE000000 | r << 16 | g << 8 | b;
            shadowColor = -871301120;
            if (shakeIntensity < 1.5f) {
                drawX += (int)(Math.sin((double)now * 0.015) * 1.5);
                drawY += (int)(Math.cos((double)now * 0.018) * 1.0);
            }
        } else if (bfPercent < 0.3f) {
            color = -2006555034;
            shadowColor = 0x33222222;
        } else if (bfPercent < 0.6f) {
            color = -1433892728;
            shadowColor = 0x44333333;
        } else if (bfPercent < 1.0f) {
            color = -570434236;
            shadowColor = 0x66886600;
        } else if (bfPercent < 5.0f) {
            int orangeAlpha = (int)(200.0f + 55.0f * pulse);
            color = orangeAlpha << 24 | 0xFF8833;
            shadowColor = -2004335616;
        } else {
            int redAlpha = (int)(180.0f + 75.0f * pulse);
            color = redAlpha << 24 | 0xFF3333;
            shadowColor = -1433927680;
        }
        graphics.drawString(font, text, drawX + 1, drawY + 1, shadowColor, false);
        graphics.drawString(font, text, drawX, drawY, color, false);
        if (isMax) {
            float sparkPulse = (float)(Math.sin((double)now * 0.01) * 0.5 + 0.5);
            if (sparkPulse > 0.5f) {
                graphics.drawString(font, "\u2726", drawX + textWidth + 2, drawY - 1, -1140907486, false);
            }
        } else if (bfPercent >= 1.0f && pulse > 0.7f) {
            graphics.drawString(font, "\u2726", drawX + textWidth + 2, drawY - 1, 0x55FFFFFF, false);
        }
    }
}
