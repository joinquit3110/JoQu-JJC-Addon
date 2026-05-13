package net.mcreator.jujutsucraft.addon;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.MOD)
public class BlackFlashHudOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean lastAnyUseDown = false;
    private static boolean lastOgStartTechniqueDown = false;
    private static long lastReleasePacketMs = 0L;
    private static volatile boolean rawMainSkillDown = false;
    private static final int DEFAULT_OG_SKILL_USE_KEY = GLFW.GLFW_KEY_Z;
    private static final long RELEASE_ACK_TIMEOUT_MS = 3000L;
    private static final long FEEDBACK_DURATION_MS = 1450L;
    private static final long BLACK_FLASH_FEEDBACK_DURATION_MS = 3200L;
    private static int lastFeedbackNonce = 0;
    private static long localFeedbackStartMs = 0L;
    private static boolean localFeedbackSuccess = false;
    private static boolean localFeedbackPending = false;
    private static long worldBillboardStartMs = 0L;
    private static int worldBillboardFlow = 0;
    private static int worldBillboardEntityId = -1;
    private static float lastRenderedNeedle = 0.0f;
    private static long lastRenderedNeedleMs = 0L;
    private static long lastRenderedNeedleNonce = 0L;

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("black_flash_chance", BlackFlashHudOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        float bfPercent = ClientPacketHandler.ClientBlackFlashCache.bfPercent;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        boolean pendingRelease = ClientPacketHandler.ClientBlackFlashCache.awaitingReleaseAck;
        boolean localReleased = ClientPacketHandler.ClientBlackFlashCache.localReleaseResolved;
        boolean charging = ClientPacketHandler.ClientBlackFlashCache.mastery && ClientPacketHandler.ClientBlackFlashCache.charging && !localReleased;
        if (charging || pendingRelease) {
            BlackFlashHudOverlay.renderTimingRing(graphics, mc, pendingRelease || localReleased ? 0.0f : partialTick, cx, cy);
        }
        BlackFlashHudOverlay.renderFeedback(graphics, mc.font, screenWidth, screenHeight, cx, cy);
        if (bfPercent < 0.01f) return;
        BlackFlashHudOverlay.renderChanceText(graphics, mc.font, bfPercent, cx, cy);
    }

    public static void showWorldBillboard(int entityId, int flow) {
        worldBillboardEntityId = -1;
        worldBillboardFlow = 0;
        worldBillboardStartMs = 0L;
    }

    public static void renderWorldBillboard(RenderLevelStageEvent event) {
        // World-space BLACK FLASH billboard intentionally disabled: text is HUD-only again.
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlackFlashHudOverlay.detectClientRelease(mc);
        BlackFlashHudOverlay.tickPendingReleaseAckTimeout();
    }

    public static void onRawKeyInput(int keyCode, int action) {
        boolean isSkillKey = BlackFlashHudOverlay.isJujutsuSkillUseKey(keyCode);
        boolean releaseSessionValid = BlackFlashHudOverlay.hasValidLocalReleaseSession();
        if (isSkillKey) {
            LOGGER.warn("[BlackFlashKeyDiag] rawKeyInput keyCode={} action={} isSkill={} releaseSessionValid={} cacheCharging={} cacheMastery={} localResolved={}", new Object[]{keyCode, action, isSkillKey, releaseSessionValid, ClientPacketHandler.ClientBlackFlashCache.charging, ClientPacketHandler.ClientBlackFlashCache.mastery, ClientPacketHandler.ClientBlackFlashCache.localReleaseResolved});
        }
        if (action == GLFW.GLFW_PRESS && isSkillKey) rawMainSkillDown = true;
        if (action == GLFW.GLFW_RELEASE && isSkillKey) {
            rawMainSkillDown = false;
            BlackFlashHudOverlay.tryReleaseFromLocalInput(releaseSessionValid ? "onRawKeyInput_RELEASE" : "onRawKeyInput_RELEASE_FORCED");
        }
    }

    private static void detectClientRelease(Minecraft mc) {
        boolean wasDown = lastAnyUseDown;
        boolean isDown = BlackFlashHudOverlay.isAnyUseInputDown(mc);
        lastAnyUseDown = isDown;
        boolean ogWasDown = lastOgStartTechniqueDown;
        boolean ogIsDown = BlackFlashHudOverlay.isOgStartTechniqueDown(mc);
        lastOgStartTechniqueDown = ogIsDown;
        if (ogWasDown && !ogIsDown) {
            BlackFlashHudOverlay.tryReleaseFromLocalInput("detectClientRelease_KEY_START_TECHNIQUE");
            return;
        }
        if (wasDown && !isDown) BlackFlashHudOverlay.tryReleaseFromLocalInput("detectClientRelease");
    }

    private static void tryReleaseFromLocalInput(String source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean charging = BlackFlashHudOverlay.hasValidLocalReleaseSession();
        long nonce = ClientPacketHandler.ClientBlackFlashCache.timingNonce;
        long now = System.currentTimeMillis();
        LOGGER.warn("[BlackFlashReleaseDiag] tryRelease source={} mastery={} cacheCharging={} awaitingAck={} localResolved={} charging={} nonce={} debounceOk={}", new Object[]{source, ClientPacketHandler.ClientBlackFlashCache.mastery, ClientPacketHandler.ClientBlackFlashCache.charging, ClientPacketHandler.ClientBlackFlashCache.awaitingReleaseAck, ClientPacketHandler.ClientBlackFlashCache.localReleaseResolved, charging, nonce, now - lastReleasePacketMs > 80L});
        if (!charging || nonce == 0L || now - lastReleasePacketMs <= 80L) return;
        float computedNeedle = ClientPacketHandler.getBlackFlashClientNeedle();
        float clientElapsedTicks = ClientPacketHandler.getBlackFlashClientElapsedTicks();
        boolean renderedNeedleFresh = lastRenderedNeedleNonce == nonce && now - lastRenderedNeedleMs <= 45L;
        float needle = computedNeedle;
        float redStart = frac(ClientPacketHandler.ClientBlackFlashCache.timingRedStart);
        float redSize = Math.max(0.01f, ClientPacketHandler.ClientBlackFlashCache.timingRedSize);
        float clientReleaseTolerance = Math.max(BlueRedPurpleNukeMod.BF_TIMING_EDGE_TOLERANCE, 0.018f);
        boolean clientTimingSuccess = BlueRedPurpleNukeMod.isBlackFlashNeedleInRedArc(needle, redStart, redSize, clientReleaseTolerance);
        if (ClientPacketHandler.markBlackFlashReleasedLocally(needle, nonce)) {
            LOGGER.warn("[BlackFlashReleaseDiag] local release detected source={} nonce={} needle={} computedNeedle={} clientElapsedTicks={} renderedNeedleFresh={} renderedNeedle={} clientTimingSuccess={} redStart={} redSize={} tolerance={} renderedAgeMs={} rawSkillUseDown={} reverseTechniqueKeyDown={} fallbackMainSkillKeyDown={} keyUseDown={} mouseRightDown={}", new Object[]{source, nonce, needle, computedNeedle, clientElapsedTicks, renderedNeedleFresh, lastRenderedNeedle, clientTimingSuccess, redStart, redSize, clientReleaseTolerance, now - lastRenderedNeedleMs, rawMainSkillDown, BlackFlashHudOverlay.isNamedKeyMappingDown(mc, "key.jujutsucraft.key_reverse_cursed_technique"), BlackFlashHudOverlay.isNamedKeyMappingDown(mc, "key.jujutsucraft.key_use_main_skill"), mc.options.keyUse.isDown(), BlackFlashHudOverlay.isMouseButtonDown(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT)});
            ModNetworking.sendBlackFlashRelease(needle, clientElapsedTicks, nonce, clientTimingSuccess);
            lastReleasePacketMs = now;
        }
    }

    private static boolean hasValidLocalReleaseSession() {
        return ClientPacketHandler.ClientBlackFlashCache.mastery && ClientPacketHandler.ClientBlackFlashCache.flowCooldown <= 0 && ClientPacketHandler.ClientBlackFlashCache.charging && BlackFlashHudOverlay.hasRecentlyVisibleTimingSession() && !ClientPacketHandler.ClientBlackFlashCache.awaitingReleaseAck && !ClientPacketHandler.ClientBlackFlashCache.localReleaseResolved && ClientPacketHandler.ClientBlackFlashCache.timingNonce != 0L;
    }

    private static boolean hasRecentlyVisibleTimingSession() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        if (ClientPacketHandler.ClientBlackFlashCache.timingStartTick <= 0L || ClientPacketHandler.ClientBlackFlashCache.timingRedSize <= 0.0f) return false;
        float age = ClientPacketHandler.getBlackFlashClientElapsedTicks();
        return age >= (float)BlueRedPurpleNukeMod.BF_TIMING_MIN_RELEASE_AGE_TICKS && age <= 80.0f;
    }

    private static void tickPendingReleaseAckTimeout() {
        if ((ClientPacketHandler.ClientBlackFlashCache.awaitingReleaseAck || ClientPacketHandler.ClientBlackFlashCache.localReleaseResolved) && ClientPacketHandler.ClientBlackFlashCache.localReleaseStartMs > 0L && System.currentTimeMillis() - ClientPacketHandler.ClientBlackFlashCache.localReleaseStartMs > RELEASE_ACK_TIMEOUT_MS) {
            LOGGER.warn("[BlackFlashTimingDiag] CLIENT_PENDING_TIMEOUT nonce={}", new Object[]{ClientPacketHandler.ClientBlackFlashCache.localReleaseNonce});
            LOGGER.warn("[BlackFlashReleaseDiag] client release ack timeout nonce={} timeoutMs={} cacheCharging={} timingNonce={}", new Object[]{ClientPacketHandler.ClientBlackFlashCache.localReleaseNonce, RELEASE_ACK_TIMEOUT_MS, ClientPacketHandler.ClientBlackFlashCache.charging, ClientPacketHandler.ClientBlackFlashCache.timingNonce});
            ClientPacketHandler.failBlackFlashPendingFeedback();
        }
    }

    private static boolean isAnyUseInputDown(Minecraft mc) {
        return rawMainSkillDown || BlackFlashHudOverlay.isJujutsuSkillUseKeyDown(mc) || BlackFlashHudOverlay.isKeyMappingPhysicallyDown(mc.options.keyUse, mc);
    }

    private static boolean isJujutsuSkillUseKeyDown(Minecraft mc) {
        if (mc.getWindow() == null) return false;
        return BlackFlashHudOverlay.isOgStartTechniqueDown(mc);
    }

    private static boolean isJujutsuSkillUseKey(int keyCode) {
        return keyCode == DEFAULT_OG_SKILL_USE_KEY || BlackFlashHudOverlay.isNamedKeyMappingKeyCode("key.jujutsucraft.key_start_technique", keyCode) || BlackFlashHudOverlay.isNamedKeyMappingKeyCode("key.jujutsucraft.start_technique", keyCode) || BlackFlashHudOverlay.isLikelyJujutsuStartTechniqueKeyCode(keyCode);
    }

    private static boolean isOgStartTechniqueDown(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        return BlackFlashHudOverlay.isNamedKeyMappingDown(mc, "key.jujutsucraft.key_start_technique") || BlackFlashHudOverlay.isNamedKeyMappingDown(mc, "key.jujutsucraft.start_technique") || InputConstants.isKeyDown(window, DEFAULT_OG_SKILL_USE_KEY);
    }

    private static boolean isNamedKeyMappingDown(Minecraft mc, String mappingName) {
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null && mappingName.equals(mapping.getName()) && BlackFlashHudOverlay.isKeyMappingPhysicallyDown(mapping, mc)) return true;
        }
        return false;
    }

    private static boolean isNamedKeyMappingKeyCode(String mappingName, int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null && mappingName.equals(mapping.getName()) && BlackFlashHudOverlay.isKeyMappingBoundToKeyCode(mapping, keyCode)) return true;
        }
        return false;
    }

    private static boolean isLikelyJujutsuStartTechniqueKeyCode(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null || !BlackFlashHudOverlay.isKeyMappingBoundToKeyCode(mapping, keyCode)) continue;
            String name = mapping.getName();
            if (name != null && name.startsWith("key.jujutsucraft.") && (name.contains("start_technique") || name.contains("key_start_technique"))) return true;
        }
        return false;
    }

    private static boolean isKeyMappingBoundToKeyCode(KeyMapping mapping, int keyCode) {
        try {
            InputConstants.Key boundKey = mapping.getKey();
            return boundKey.getType() == InputConstants.Type.KEYSYM && boundKey.getValue() == keyCode;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isKeyMappingPhysicallyDown(KeyMapping mapping, Minecraft mc) {
        if (mapping == null || mc.getWindow() == null) return false;
        try {
            InputConstants.Key key = mapping.getKey();
            if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
            if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
        }
        return false;
    }

    private static boolean isMouseButtonDown(Minecraft mc, int button) {
        if (mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    private static void renderChanceText(GuiGraphics graphics, Font font, float bfPercent, int cx, int cy) {
        int shadowColor;
        int color;
        long now = System.currentTimeMillis();
        String text = bfPercent < 1.0f ? String.format("%.1f%%", Float.valueOf(bfPercent)) : String.format("%.0f%%", Float.valueOf(bfPercent));
        int textWidth = font.width(text);
        String statusIcon = ClientPacketHandler.ClientBlackFlashCache.flowCooldown > 0 ? "\u23F1" : (ClientPacketHandler.ClientBlackFlashCache.flow > 0 || ClientPacketHandler.ClientBlackFlashCache.charging ? "\u26A1" : "");
        int iconWidth = statusIcon.isEmpty() ? 0 : font.width(statusIcon);
        int drawX = cx + 14 + (statusIcon.isEmpty() ? 0 : iconWidth + 3);
        Objects.requireNonNull(font);
        int drawY = cy - 4;
        float pulse = (float)(Math.sin((double)now * 0.006) * 0.5 + 0.5);
        boolean isMax = bfPercent >= 10.0f;
        if (isMax) {
            float maxPulse = (float)(Math.sin((double)now * 0.012) * 0.5 + 0.5);
            int r = (int)(200.0f + 55.0f * maxPulse);
            int g = (int)(20.0f * (1.0f - maxPulse));
            int b = (int)(20.0f * (1.0f - maxPulse));
            color = 0xEE000000 | r << 16 | g << 8 | b;
            shadowColor = -871301120;
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
        if (!statusIcon.isEmpty()) {
            int iconColor = ClientPacketHandler.ClientBlackFlashCache.flowCooldown > 0 ? 0xFFB8B8B8 : BlackFlashHudOverlay.getFlowIconColor(ClientPacketHandler.ClientBlackFlashCache.flow);
            graphics.drawString(font, statusIcon, drawX - iconWidth - 3, drawY - 1, iconColor, false);
        }
        int sparkOffset = 2;
        if (isMax) {
            float sparkPulse = (float)(Math.sin((double)now * 0.01) * 0.5 + 0.5);
            if (sparkPulse > 0.5f) graphics.drawString(font, "\u2726", drawX + textWidth + sparkOffset, drawY - 1, -1140907486, false);
        } else if (bfPercent >= 1.0f && pulse > 0.7f) {
            graphics.drawString(font, "\u2726", drawX + textWidth + sparkOffset, drawY - 1, 0x55FFFFFF, false);
        }
    }

    private static int getFlowIconColor(int flow) {
        int[] colors = new int[]{0xFFFFE066, 0xFFFFC247, 0xFFFFA033, 0xFFFF6A2A, 0xFFFF3D2E, 0xFFE01818, 0xFFB00000, 0xFF220000};
        int index = Math.max(1, Math.min(BlueRedPurpleNukeMod.BF_FLOW_MAX, flow)) - 1;
        return colors[index];
    }

    private static void renderTimingRing(GuiGraphics graphics, Minecraft mc, float partialTick, int cx, int cy) {
        long now = System.currentTimeMillis();
        int flow = Math.max(0, Math.min(BlueRedPurpleNukeMod.BF_FLOW_MAX, ClientPacketHandler.ClientBlackFlashCache.flow));
        float flowPower = (float)flow / (float)Math.max(1, BlueRedPurpleNukeMod.BF_FLOW_MAX);
        int radius = 22 + Math.round(flowPower * 5.0f);
        float redStart = ClientPacketHandler.ClientBlackFlashCache.timingRedStart;
        float redSize = Math.max(0.01f, ClientPacketHandler.ClientBlackFlashCache.timingRedSize);
        float breath = (float)(Math.sin(now * (0.010 + flowPower * 0.006)) * 0.5 + 0.5);
        int ringCx = cx;
        int ringCy = cy;
        int red = flow >= 7 ? 0xFFFF1010 : 0xFFFF3030;
        int gold = flow >= 5 ? 0xFFFFD060 : 0xFFFFF0B0;
        drawArc(graphics, ringCx, ringCy, radius + 10, 0.0f, 1.0f, alphaColor(0x44FF2020, 0.26f + breath * 0.32f + flowPower * 0.22f), 1 + flow / 4);
        drawArc(graphics, ringCx, ringCy, radius + 6, redStart - 0.012f, redSize + 0.024f, alphaColor(0xAA000000 | (red & 0x00FFFFFF), 0.35f + flowPower * 0.35f), 8);
        drawArc(graphics, ringCx, ringCy, radius + 2, 0.0f, 1.0f, 0x55101010, 2);
        drawArc(graphics, ringCx, ringCy, radius, 0.0f, 1.0f, 0x9940FF90, 1);
        drawArc(graphics, ringCx, ringCy, radius + 1, redStart, redSize, 0xFF000000 | (red & 0x00FFFFFF), 5 + flow / 3);
        drawArc(graphics, ringCx, ringCy, radius + 7, redStart, redSize, alphaColor(0xAAFF2020, 0.55f + breath * 0.28f), 2 + flow / 3);
        boolean pendingRelease = ClientPacketHandler.ClientBlackFlashCache.awaitingReleaseAck;
        float needle = pendingRelease ? ClientPacketHandler.ClientBlackFlashCache.localReleaseNeedle : ClientPacketHandler.getBlackFlashClientNeedle();
        if (!pendingRelease) {
            lastRenderedNeedle = frac(needle);
            lastRenderedNeedleMs = System.currentTimeMillis();
            lastRenderedNeedleNonce = ClientPacketHandler.ClientBlackFlashCache.timingNonce;
        }
        double angle = needle * Math.PI * 2.0 - Math.PI / 2.0;
        int x1 = ringCx + (int)Math.round(Math.cos(angle) * 3.0);
        int y1 = ringCy + (int)Math.round(Math.sin(angle) * 3.0);
        int x2 = ringCx + (int)Math.round(Math.cos(angle) * (double)(radius + 12));
        int y2 = ringCy + (int)Math.round(Math.sin(angle) * (double)(radius + 12));
        int needleColor = pendingRelease ? 0xFF80D8FF : (0xFF000000 | (gold & 0x00FFFFFF));
        drawLine(graphics, x1, y1, x2, y2, 0xCC000000, 5);
        drawLine(graphics, x1, y1, x2, y2, alphaColor(needleColor, 0.42f), 4);
        drawLine(graphics, x1, y1, x2, y2, needleColor, 1 + flow / 5);
        graphics.fill(ringCx - 2, ringCy - 2, ringCx + 3, ringCy + 3, 0xCC000000);
        graphics.fill(ringCx - 1, ringCy - 1, ringCx + 2, ringCy + 2, needleColor);
        if (flow > 0) {
            String flowText = flow + "/" + BlueRedPurpleNukeMod.BF_FLOW_MAX;
            graphics.drawString(mc.font, flowText, ringCx - mc.font.width(flowText) / 2, ringCy + radius + 12, alphaColor(0xFFFFD060, 0.62f + breath * 0.28f), false);
        }
    }

    private static void renderFeedback(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int cx, int cy) {
        int nonce = ClientPacketHandler.ClientBlackFlashCache.feedbackNonce;
        if (nonce != lastFeedbackNonce) {
            lastFeedbackNonce = nonce;
            localFeedbackStartMs = ClientPacketHandler.ClientBlackFlashCache.feedbackStartMs;
            localFeedbackSuccess = ClientPacketHandler.ClientBlackFlashCache.feedbackSuccess;
            localFeedbackPending = ClientPacketHandler.ClientBlackFlashCache.feedbackPending;
        }
        boolean confirmedHit = ClientPacketHandler.ClientBlackFlashCache.feedbackConfirmedHit;
        boolean blackFlash = localFeedbackSuccess && confirmedHit;
        long duration = blackFlash ? BLACK_FLASH_FEEDBACK_DURATION_MS : FEEDBACK_DURATION_MS;
        long age = System.currentTimeMillis() - localFeedbackStartMs;
        if (localFeedbackStartMs <= 0L || age < 0L || age > duration) return;
        float t = (float)age / (float)duration;
        float fade = clamp01(t < 0.18f ? t / 0.18f : 1.0f - (t - 0.18f) / 0.82f);
        if (blackFlash) {
            String text = "\u9ED2\u9583 BLACK FLASH";
            float flicker = getFeedbackFlicker(age, true);
            int alpha = (int)(255.0f * fade * flicker);
            if (alpha > 4) {
                int w = font.width(text);
                renderBlackFlashFeedbackText(graphics, font, text, cx - w / 2, cy - 42, alpha, age);
            }
            return;
        }
        String text = localFeedbackPending || localFeedbackSuccess && !blackFlash ? "RELEASED" : "FAILED";
        float flicker = getFeedbackFlicker(age, false);
        int alpha = (int)(255.0f * fade * flicker);
        if (alpha <= 4) return;
        int w = font.width(text);
        int baseX = cx - w / 2;
        int baseY = blackFlash ? cy - 42 : cy - 34;
        if (blackFlash) renderBlackFlashFeedbackText(graphics, font, text, baseX, baseY, alpha, age);
        else renderCompactFeedbackText(graphics, font, text, baseX, baseY, alpha, localFeedbackPending ? 0x8FDFFF : (localFeedbackSuccess ? 0xFFD060 : 0xC8C8C8));
    }

    private static void renderBlackFlashFeedbackText(GuiGraphics graphics, Font font, String text, int x, int y, int alpha, long age) {
        int flow = Math.max(1, Math.min(BlueRedPurpleNukeMod.BF_FLOW_MAX, ClientPacketHandler.ClientBlackFlashCache.flow));
        float power = (float)flow / (float)Math.max(1, BlueRedPurpleNukeMod.BF_FLOW_MAX);
        int goldAlpha = (int)(alpha * (0.66f + power * 0.22f));
        int redAlpha = (int)(alpha * 0.98f);
        int blurAlpha = (int)(alpha * (0.08f + power * 0.12f));
        int shadowAlpha = (int)(alpha * 0.48f);
        int[][] blurOffsets = new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1 - flow / 3, 1}, {1 + flow / 3, -1}};
        for (int[] offset : blurOffsets) graphics.drawString(font, text, x + offset[0], y + offset[1], (blurAlpha << 24) | 0xFF3030, false);
        graphics.drawString(font, text, x + 2, y + 1, (shadowAlpha << 24), false);
        graphics.drawString(font, text, x - 1, y - 1, (goldAlpha << 24) | 0xFFE072, false);
        graphics.drawString(font, text, x + 1, y + 1, (shadowAlpha << 24), false);
        graphics.drawString(font, text, x, y, (redAlpha << 24) | (flow >= 8 ? 0xFF0000 : 0xB00000), false);
    }

    private static void renderCompactFeedbackText(GuiGraphics graphics, Font font, String text, int x, int y, int alpha, int rgb) {
        int glowAlpha = (int)(alpha * 0.08f);
        int shadowAlpha = (int)(alpha * 0.34f);
        graphics.drawString(font, text, x + 1, y + 1, (shadowAlpha << 24), false);
        graphics.drawString(font, text, x, y - 1, (glowAlpha << 24) | rgb, false);
        graphics.drawString(font, text, x, y + 1, (glowAlpha << 24) | rgb, false);
        graphics.drawString(font, text, x, y, (alpha << 24) | rgb, false);
    }

    private static float getFeedbackFlicker(long age, boolean blackFlash) {
        float fast = (float)(0.5 + 0.5 * Math.sin(age * (blackFlash ? 0.055 : 0.038)));
        float slow = (float)(0.5 + 0.5 * Math.sin(age * 0.017 + 1.7));
        float min = blackFlash ? 0.58f : 0.72f;
        return clamp01(min + (1.0f - min) * (0.65f * fast + 0.35f * slow));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int alphaColor(int rgb, float alphaMul) {
        int origA = rgb >>> 24 & 0xFF;
        int a = Math.min(255, Math.max(0, (int)(origA * alphaMul)));
        return a << 24 | rgb & 0x00FFFFFF;
    }

    private static void drawArc(GuiGraphics graphics, int cx, int cy, int radius, float start, float size, int color, int thickness) {
        int segments = Math.max(8, (int)Math.ceil(128.0f * size));
        for (int i = 0; i < segments; ++i) {
            float a0 = frac(start + size * (float)i / (float)segments);
            float a1 = frac(start + size * (float)(i + 1) / (float)segments);
            double r0 = a0 * Math.PI * 2.0 - Math.PI / 2.0;
            double r1 = a1 * Math.PI * 2.0 - Math.PI / 2.0;
            int x0 = cx + (int)Math.round(Math.cos(r0) * (double)radius);
            int y0 = cy + (int)Math.round(Math.sin(r0) * (double)radius);
            int x1 = cx + (int)Math.round(Math.cos(r1) * (double)radius);
            int y1 = cy + (int)Math.round(Math.sin(r1) * (double)radius);
            drawLine(graphics, x0, y0, x1, y1, color, thickness);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            graphics.fill(x0, y0, x0 + thickness, y0 + thickness, color);
            return;
        }
        for (int i = 0; i <= steps; ++i) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            graphics.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }

    private static float frac(float value) {
        return value - (float)Math.floor(value);
    }
}
