package net.mcreator.jujutsucraft.addon;

/**
 * Circular cooldown indicator rendered around the screen crosshair, showing
 * when the RCT Level 3 near-death ability will be available again.
 *
 * <h2>Display conditions</h2>
 * Always visible once RCT Level 3 mastery is unlocked (checked via
 * {@link ModNetworking.ClientNearDeathCdCache#rctLevel3Unlocked}), regardless
 * of whether the cooldown is active or fully recharged.
 *
 * <h2>Visual states</h2>
 * The ring colour shifts from red → orange → green as the cooldown fills.
 * The icon in the centre switches between ☠ (on cooldown) and ♥ (ready).
 * A glowing dot sweeps around the ring edge when recharging.
 *
 * @see NearDeathOverlay
 * @see NearDeathClientState
 * @see ModNetworking.ClientNearDeathCdCache
 */

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.NearDeathClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Objects;

/**
 * Circular cooldown indicator for near-death ability.
 * Displayed as a ring around the screen crosshair.
 */
@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NearDeathCdOverlay {

    private static final int RADIUS = 8;
    private static final int RING_THICKNESS = 2;
    private static final int SEGMENTS = 48;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("near_death_cd", NearDeathCdOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick,
                                      int screenWidth, int screenHeight) {
        if (!ModNetworking.ClientNearDeathCdCache.rctLevel3Unlocked) return;
        int cdRemaining = ModNetworking.ClientNearDeathCdCache.cdRemaining;
        int cdMax = ModNetworking.ClientNearDeathCdCache.cdMax;
        if (cdRemaining <= 0 && cdMax <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        int cx = screenWidth / 2 - 14 - RADIUS;
        int cy = screenHeight / 2;

        long now = System.currentTimeMillis();
        float progress = cdRemaining > 0
            ? 1.0f - (float) cdRemaining / Math.max(cdMax, 1)
            : 1.0f;

        gfx.pose().pushPose();
        drawFilledCircle(gfx, cx, cy, RADIUS, -1726934767);

        if (progress > 0.001f) {
            int sweepColor = progress < 0.33f ? -1714670797
                : (progress < 0.66f ? -1714649037 : -1724667188);
            drawArc(gfx, cx, cy, RADIUS, 0.0f, progress, sweepColor);
        }

        int ringColor = progress < 0.33f ? -1144241084
            : (progress < 0.66f ? -1143100860
                : (progress < 0.999f ? -1153119011 : -1153106057));
        drawRingShape(gfx, cx, cy, RADIUS, RADIUS + RING_THICKNESS, ringColor);

        if (progress > 0.005f && progress < 0.995f) {
            float edgeAngle = (float) (Math.PI * 2 * progress) - (float) (Math.PI / 2);
            float ex = cx + (float) Math.cos(edgeAngle) * (RADIUS + RING_THICKNESS + 1);
            float ey = cy + (float) Math.sin(edgeAngle) * (RADIUS + RING_THICKNESS + 1);
            float glow = (float) (Math.sin(now * 0.006) * 0.3 + 0.7);
            int glowAlpha = (int) (240.0f * glow);
            drawFilledCircleF(gfx, ex, ey, 2.0f, glowAlpha << 24 | 0xFFFFFF);
        }

        String icon = cdRemaining > 0 ? "\u2620" : "\u2665";
        int iconW = font.width(icon);
        float pulse = (float) (Math.sin(now * 0.004) * 0.1 + 0.9);
        int iconAlpha = (int) (230.0f * pulse);

        int iconRgb = cdRemaining > 0
            ? (progress < 0.33f ? 0xCC4444
                : (progress < 0.66f ? 0xDDAA44
                    : (progress < 0.999f ? 0x44CCDD : 0x44FF77)))
            : 0x55FF77;

        int iconColor = iconAlpha << 24 | (iconRgb & 0xFFFFFF);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gfx.drawString(font, icon, cx - iconW / 2, cy - 9 / 2, iconColor, false);

        gfx.pose().popPose();
    }

    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int radius, int color) {
        drawArc(gfx, cx, cy, radius, 0.0f, 1.0f, color);
    }

    private static void drawFilledCircleF(GuiGraphics gfx, float cx, float cy, float radius, int color) {
        float a = (float) (color >> 24 & 0xFF) / 255.0f;
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        if (a <= 0.01f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();

        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, cx, cy, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= 16; i++) {
            double angle = Math.PI * 2 * i / 16.0;
            buf.vertex(mat, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0)
                .color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawArc(GuiGraphics gfx, int cx, int cy, int radius,
                                 float startFraction, float fraction, int color) {
        float a = (float) (color >> 24 & 0xFF) / 255.0f;
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        if (a <= 0.01f) return;

        double startAngle = -Math.PI / 2 + Math.PI * 2 * startFraction;
        double endAngle = startAngle + Math.PI * 2 * fraction;
        int steps = Math.max(4, (int) (SEGMENTS * fraction));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();

        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, cx, cy, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + (endAngle - startAngle) * i / steps;
            buf.vertex(mat, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0)
                .color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawRingShape(GuiGraphics gfx, int cx, int cy,
                                      int innerR, int outerR, int color) {
        float a = (float) (color >> 24 & 0xFF) / 255.0f;
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        if (a <= 0.01f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();

        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buf.vertex(mat, cx + cos * innerR, cy + sin * innerR, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, cx + cos * outerR, cy + sin * outerR, 0).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
