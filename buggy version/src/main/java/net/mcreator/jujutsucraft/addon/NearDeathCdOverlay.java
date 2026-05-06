package net.mcreator.jujutsucraft.addon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.mcreator.jujutsucraft.addon.ClientPacketHandler;
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

@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.MOD)
/**
 * Client HUD overlay that displays near-death cooldown progress as a circular ring around the combat reticle once RCT level 3 is unlocked.
 */
public class NearDeathCdOverlay {
    // Inner ring radius, in screen pixels, for the near-death cooldown widget.
    private static final int RADIUS = 8;
    // Configured ring thickness constant retained for the circular cooldown drawing code.
    private static final int RING_THICKNESS = 2;
    // Configured circle subdivision count retained for smooth near-death ring rendering.
    private static final int SEGMENTS = 48;

    @SubscribeEvent
    /**
     * Registers overlay with the appropriate Forge or client system.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("near_death_cd", NearDeathCdOverlay::renderOverlay);
    }

    /**
     * Renders overlay for the current frame.
     * @param gui render context used to draw the current frame.
     * @param gfx render context used to draw the current frame.
     * @param partialTick tick-based timing value used by this operation.
     * @param screenWidth screen width used by this method.
     * @param screenHeight screen height used by this method.
     */
    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientPacketHandler.ClientNearDeathCdCache.rctLevel3Unlocked) {
            return;
        }
        int cdRemaining = ClientPacketHandler.ClientNearDeathCdCache.cdRemaining;
        int cdMax = ClientPacketHandler.ClientNearDeathCdCache.cdMax;
        if (cdRemaining <= 0 && cdMax <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int cx = screenWidth / 2 - 14 - 8;
        int cy = screenHeight / 2;
        long now = System.currentTimeMillis();
        float progress = cdRemaining > 0 ? 1.0f - (float)cdRemaining / (float)Math.max(cdMax, 1) : 1.0f;
        gfx.pose().pushPose();
        NearDeathCdOverlay.drawFilledCircle(gfx, cx, cy, 8, -1726934767);
        if (progress > 0.001f) {
            int sweepColor = progress < 0.33f ? -1714670797 : (progress < 0.66f ? -1714649037 : -1724667188);
            NearDeathCdOverlay.drawArc(gfx, cx, cy, 8, 0.0f, progress, sweepColor);
        }
        int ringColor = progress < 0.33f ? -1144241084 : (progress < 0.66f ? -1143100860 : (progress < 0.999f ? -1153119011 : -1153106057));
        NearDeathCdOverlay.drawRingShape(gfx, cx, cy, 8, 10, ringColor);
        if (progress > 0.005f && progress < 0.995f) {
            float edgeAngle = (float)(Math.PI * 2 * (double)progress) - 1.5707964f;
            float ex = (float)cx + (float)Math.cos(edgeAngle) * 11.0f;
            float ey = (float)cy + (float)Math.sin(edgeAngle) * 11.0f;
            float glow = (float)(Math.sin((double)now * 0.006) * 0.3 + 0.7);
            int glowAlpha = (int)(240.0f * glow);
            NearDeathCdOverlay.drawFilledCircleF(gfx, ex, ey, 2.0f, glowAlpha << 24 | 0xFFFFFF);
        }
        String icon = cdRemaining > 0 ? "\u2620" : "\u2665";
        int iconW = font.width(icon);
        float pulse = (float)(Math.sin((double)now * 0.004) * 0.1 + 0.9);
        int iconAlpha = (int)(230.0f * pulse);
        int iconRgb = cdRemaining > 0 ? (progress < 0.33f ? 0xCC4444 : (progress < 0.66f ? 0xDDAA44 : (progress < 0.999f ? 0x44CCDD : 0x44FF77))) : 0x55FF77;
        int iconColor = iconAlpha << 24 | iconRgb & 0xFFFFFF;
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        gfx.drawString(font, icon, cx - iconW / 2, cy - 4, iconColor, false);
        gfx.pose().popPose();
    }

    /**
     * Draws filled circle as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param cx screen or world coordinate used by this calculation.
     * @param cy screen or world coordinate used by this calculation.
     * @param radius radius used by this method.
     * @param color color used by this method.
     */
    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int radius, int color) {
        NearDeathCdOverlay.drawArc(gfx, cx, cy, radius, 0.0f, 1.0f, color);
    }

    /**
     * Draws filled circle f as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param cx screen or world coordinate used by this calculation.
     * @param cy screen or world coordinate used by this calculation.
     * @param radius radius used by this method.
     * @param color color used by this method.
     */
    private static void drawFilledCircleF(GuiGraphics gfx, float cx, float cy, float radius, int color) {
        float a = (float)(color >> 24 & 0xFF) / 255.0f;
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        if (a <= 0.01f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, cx, cy, 0.0f).color(r, g, b, a).endVertex();
        for (int i = 0; i <= 16; ++i) {
            double angle = Math.PI * 2 * (double)i / 16.0;
            buf.vertex(mat, cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius, 0.0f).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader((BufferBuilder.RenderedBuffer)buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Draws arc as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param cx screen or world coordinate used by this calculation.
     * @param cy screen or world coordinate used by this calculation.
     * @param radius radius used by this method.
     * @param startFraction start fraction used by this method.
     * @param fraction fraction used by this method.
     * @param color color used by this method.
     */
    private static void drawArc(GuiGraphics gfx, int cx, int cy, int radius, float startFraction, float fraction, int color) {
        float a = (float)(color >> 24 & 0xFF) / 255.0f;
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        if (a <= 0.01f) {
            return;
        }
        double startAngle = -1.5707963267948966 + Math.PI * 2 * (double)startFraction;
        double endAngle = startAngle + Math.PI * 2 * (double)fraction;
        int steps = Math.max(4, (int)(48.0f * fraction));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, (float)cx, (float)cy, 0.0f).color(r, g, b, a).endVertex();
        for (int i = 0; i <= steps; ++i) {
            double angle = startAngle + (endAngle - startAngle) * (double)i / (double)steps;
            buf.vertex(mat, (float)cx + (float)Math.cos(angle) * (float)radius, (float)cy + (float)Math.sin(angle) * (float)radius, 0.0f).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader((BufferBuilder.RenderedBuffer)buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Draws ring shape as part of the addon presentation layer.
     * @param gfx render context used to draw the current frame.
     * @param cx screen or world coordinate used by this calculation.
     * @param cy screen or world coordinate used by this calculation.
     * @param innerR inner r used by this method.
     * @param outerR outer r used by this method.
     * @param color color used by this method.
     */
    private static void drawRingShape(GuiGraphics gfx, int cx, int cy, int innerR, int outerR, int color) {
        float a = (float)(color >> 24 & 0xFF) / 255.0f;
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        if (a <= 0.01f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gfx.pose().last().pose();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 48; ++i) {
            double angle = Math.PI * 2 * (double)i / 48.0;
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            buf.vertex(mat, (float)cx + cos * (float)innerR, (float)cy + sin * (float)innerR, 0.0f).color(r, g, b, a).endVertex();
            buf.vertex(mat, (float)cx + cos * (float)outerR, (float)cy + sin * (float)outerR, 0.0f).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader((BufferBuilder.RenderedBuffer)buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}

