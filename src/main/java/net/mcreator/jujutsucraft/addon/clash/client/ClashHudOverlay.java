package net.mcreator.jujutsucraft.addon.clash.client;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.OUTCOME_DISPLAY_HOLD_TICKS;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.client.gui.Font;

import net.mcreator.jujutsucraft.addon.clash.client.ClientClashCache.CachedSnapshot;
import net.mcreator.jujutsucraft.addon.clash.client.ClientClashCache.OutcomeBanner;
import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.mcreator.jujutsucraft.addon.clash.net.DomainClashHudSnapshotPacket;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

@OnlyIn(Dist.CLIENT)
public final class ClashHudOverlay implements IGuiOverlay {
    public static final ClashHudOverlay INSTANCE = new ClashHudOverlay();
    public static final String OVERLAY_ID = "clash_hud";
    private static final Pattern UUID_NAME = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int BAR_HEIGHT = 7;
    private static final int TEXT_MARQUEE_GAP = 22;
    private static final float TEXT_MARQUEE_SPEED = 0.55f;
    private static float displayedRatio = 0.5f;
    private static long lastActiveGameTime = -1L;

    private ClashHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics gfx, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) {
            return;
        }

        long gameTime = mc.level.getGameTime();
        CachedSnapshot snap = ClientClashCache.INSTANCE.chooseRenderable(gameTime, mc.player.getUUID());
        if (snap != null && snap.packet.outcome != ClashOutcome.CANCELLED) {
            renderOverlay(gfx, mc, partialTick, sw, snap, gameTime);
        }

        OutcomeBanner banner = ClientClashCache.INSTANCE.outcomeBanner(gameTime);
        if (banner != null && (snap == null || snap.packet.outcome == ClashOutcome.CANCELLED || gameTime - snap.receiptTick > OUTCOME_DISPLAY_HOLD_TICKS)) {
            renderOutcomeBanner(gfx, mc, sw, gameTime, banner);
        }
    }

    private static void renderOverlay(GuiGraphics gfx, Minecraft mc, float partialTick, int sw, CachedSnapshot snap, long gameTime) {
        DomainClashHudSnapshotPacket pkt = snap.packet;
        long elapsed = Math.max(0L, gameTime - snap.receiptTick);
        if (pkt.outcome != null && elapsed > OUTCOME_DISPLAY_HOLD_TICKS) {
            return;
        }

        float casterPower = Math.max(0.0f, pkt.casterPower);
        float opponentPower = Math.max(0.0f, pkt.opponentPower);
        float ratio = normalizeRatio(casterPower, opponentPower);
        if (lastActiveGameTime < 0L || gameTime - lastActiveGameTime > 8L) {
            displayedRatio = ratio;
        } else {
            displayedRatio += (ratio - displayedRatio) * 0.18f;
        }
        lastActiveGameTime = gameTime;

        float time = (float) gameTime + partialTick;
        float pulse = (float) (Math.sin(time * 0.16f) * 0.5f + 0.5f);
        int casterBaseColor = formColor(pkt.casterFormId);
        int opponentBaseColor = formColor(pkt.opponentFormId);
        int casterColor = withOpacity(selfPalette(casterBaseColor), 0.98f);
        int opponentColor = withOpacity(enemyPalette(opponentBaseColor, casterBaseColor), 0.98f);
        int panelWidth = Math.min(380, Math.max(300, (int) (sw * 0.38f)));
        int panelHeight = 86;

        int panelX = (sw - panelWidth) / 2;
        int panelY = 18;
        int pad = 10;

        drawPanel(gfx, panelX, panelY, panelWidth, panelHeight, pulse);

        String title = "\u2694 DOMAIN CLASH \u2694";
        int titleColor = lerpColor(0xFFFFD86B, 0xFF62E8FF, pulse);
        int titleX = panelX + (panelWidth - mc.font.width(title)) / 2;
        gfx.drawString(mc.font, title, titleX + 1, panelY + 5, 0x90000000, false);
        gfx.drawString(mc.font, title, titleX, panelY + 4, titleColor, false);
        drawCornerGlyphs(gfx, mc.font, panelX, panelY, panelWidth, pulse);

        int barX = panelX + pad;
        int barY = panelY + 28;
        int barWidth = panelWidth - pad * 2;
        int gap = 14;
        int colWidth = (barWidth - gap) / 2;

        int casterPct = Math.round(clamp(displayedRatio) * 100.0f);
        String leftPct = casterPct + "%";
        String rightPct = (100 - casterPct) + "%";
        gfx.drawString(mc.font, leftPct, barX, panelY + 16, casterColor, false);
        gfx.drawString(mc.font, rightPct, barX + barWidth - mc.font.width(rightPct), panelY + 16, opponentColor, false);
        drawBar(gfx, barX, barY, barWidth, BAR_HEIGHT, displayedRatio, casterColor, opponentColor, time);

        String leftName = compactParticipantName(pkt.casterName, "Caster") + " " + formBadge(pkt.casterFormId);
        String rightName = compactParticipantName(pkt.opponentName, "Enemy") + " " + formBadge(pkt.opponentFormId);
        drawMarqueeText(gfx, mc.font, leftName, barX, panelY + 41, colWidth, withOpacity(casterColor, 0.92f), time, false);
        drawMarqueeText(gfx, mc.font, rightName, barX + colWidth + gap, panelY + 41, colWidth, withOpacity(opponentColor, 0.92f), time + 17.0f, true);

        String leftDomain = compactDomainLabel(pkt.casterDomainId);
        String rightDomain = compactDomainLabel(pkt.opponentDomainId);
        drawMarqueeText(gfx, mc.font, leftDomain, barX, panelY + 54, colWidth, 0xFFEDEBED, time + 5.0f, false);
        drawMarqueeText(gfx, mc.font, rightDomain, barX + colWidth + gap, panelY + 54, colWidth, withOpacity(opponentColor, 0.95f), time + 23.0f, true);

        String timerText = resolveTimerText(pkt, Math.max(0, pkt.remainingTicks - (int) elapsed));
        int timerColor = withOpacity(0xFFFFD86B, 0.92f);
        gfx.drawString(mc.font, timerText, panelX + (panelWidth - mc.font.width(timerText)) / 2, panelY + 16, timerColor, false);

        String leftPower = String.format(Locale.ROOT, "\u2726 Power %.0f", casterPower);
        String rightPower = String.format(Locale.ROOT, "Power %.0f \u2726", opponentPower);
        drawMarqueeText(gfx, mc.font, leftPower, barX, panelY + 70, colWidth, 0xFFF4F2FF, time + 11.0f, false);
        drawMarqueeText(gfx, mc.font, rightPower, barX + colWidth + gap, panelY + 70, colWidth, 0xFFF4F2FF, time + 29.0f, true);
    }
    private static void renderOutcomeBanner(GuiGraphics gfx, Minecraft mc, int sw, long gameTime, OutcomeBanner banner) {
        long age = gameTime - banner.receiptTick - OUTCOME_DISPLAY_HOLD_TICKS;
        if (age < 0L || age > 72L) {
            return;
        }
        boolean tie = banner.outcome == ClashOutcome.TIE;
        boolean won = banner.localWon && !tie;
        String kicker = tie ? "EQUILIBRIUM BREAK" : (won ? "VERDICT: OVERWRITE" : "VERDICT: COLLAPSE");
        String title = tie ? "MUTUAL COLLAPSE" : (won ? "DOMAIN SUPREMACY" : "DOMAIN SHATTERED");
        String subtitle = tie ? "both wills burn out in the same instant" : (won ? "your technique paints the battlefield" : "the enemy domain devours your barrier");
        int hot = tie ? 0xFFFFD66E : (won ? 0xFF4DF7FF : 0xFFFF4E63);
        int warm = tie ? 0xFFFF8F3D : (won ? 0xFFFFD35B : 0xFFFF9D38);
        int cool = tie ? 0xFF91D7FF : (won ? 0xFF65FFB9 : 0xFFB34CFF);
        int deep = tie ? 0xFF211333 : (won ? 0xFF061E2A : 0xFF2A0710);
        float fade = clamp(1.0f - age / 72.0f);
        float reveal = clamp(age / 9.0f);
        float pulse = (float)(Math.sin((gameTime + age) * 0.22f) * 0.5f + 0.5f);
        float opacity = clamp(Math.min(fade * 1.18f, reveal));
        int alpha = Math.max(0, Math.min(255, Math.round(255.0f * opacity)));
        int panelW = Math.min(440, Math.max(320, (int)(sw * 0.36f)));
        int panelH = 76;
        int x = sw / 2 - panelW / 2;
        int y = 154 + Math.round((1.0f - reveal) * 10.0f);
        int inner = withOpacity(deep, 0.88f * opacity);
        int beamW = Math.max(24, Math.round(panelW * reveal));
        int beamX = sw / 2 - beamW / 2;

        gfx.fill(x - 18, y - 12, x + panelW + 18, y + panelH + 12, withOpacity(hot, 0.10f * opacity));
        gfx.fill(x - 12, y - 8, x + panelW + 12, y + panelH + 8, withOpacity(cool, 0.13f * opacity));
        gfx.fill(x - 5, y - 5, x + panelW + 5, y + panelH + 5, withOpacity(0xFF02030A, 0.86f * opacity));
        gfx.fill(x, y, x + panelW, y + panelH, inner);
        gfx.fill(x + 4, y + 4, x + panelW - 4, y + panelH - 4, withOpacity(lerpColor(deep, hot, 0.18f), 0.58f * opacity));

        gfx.fill(x, y, beamX, y + 3, withOpacity(cool, 0.25f * opacity));
        gfx.fill(beamX, y, beamX + beamW, y + 3, withOpacity(warm, 0.95f * opacity));
        gfx.fill(beamX, y + panelH - 3, beamX + beamW, y + panelH, withOpacity(hot, 0.95f * opacity));
        gfx.fill(x, y + panelH - 3, beamX, y + panelH, withOpacity(hot, 0.20f * opacity));
        gfx.fill(x, y, x + 4, y + panelH, withOpacity(hot, 0.82f * opacity));
        gfx.fill(x + panelW - 4, y, x + panelW, y + panelH, withOpacity(cool, 0.82f * opacity));

        int scanX = x + Math.floorMod((int)(gameTime * 5L), Math.max(1, panelW + 60)) - 30;
        gfx.fill(Math.max(x + 6, scanX), y + 7, Math.min(x + panelW - 6, scanX + 26), y + panelH - 7, withOpacity(0xFFFFFFFF, 0.12f * opacity));
        for (int i = 0; i < 12; i++) {
            int px = x + 16 + Math.floorMod((int)(gameTime * (i + 2)) + i * 31, Math.max(1, panelW - 32));
            int py = y + 9 + (i * 11 + (int)gameTime) % Math.max(1, panelH - 22);
            int spark = (i % 3 == 0) ? warm : (i % 3 == 1 ? hot : cool);
            gfx.fill(px, py, px + 2 + (i % 2), py + 1, withOpacity(spark, (0.28f + pulse * 0.22f) * opacity));
        }

        int kickerW = mc.font.width(kicker);
        gfx.drawString(mc.font, kicker, sw / 2 - kickerW / 2 + 1, y + 10, (Math.round(alpha * 0.55f) << 24), false);
        gfx.drawString(mc.font, kicker, sw / 2 - kickerW / 2, y + 9, (alpha << 24) | (warm & 0x00FFFFFF), false);

        float titleScale = 1.34f;
        int titleW = Math.round(mc.font.width(title) * titleScale);
        gfx.pose().pushPose();
        gfx.pose().translate(sw / 2 - titleW / 2, y + 25, 0);
        gfx.pose().scale(titleScale, titleScale, 1.0f);
        gfx.drawString(mc.font, title, 2, 2, (Math.round(alpha * 0.65f) << 24), false);
        gfx.drawString(mc.font, title, -1, 0, (Math.round(alpha * 0.45f) << 24) | (cool & 0x00FFFFFF), false);
        gfx.drawString(mc.font, title, 0, 0, (alpha << 24) | (hot & 0x00FFFFFF), false);
        gfx.pose().popPose();

        int subW = mc.font.width(subtitle);
        int subX = sw / 2 - subW / 2;
        gfx.fill(subX - 10, y + 55, subX + subW + 10, y + 66, withOpacity(0xFF000000, 0.30f * opacity));
        gfx.drawString(mc.font, subtitle, subX + 1, y + 57, (Math.round(alpha * 0.55f) << 24), false);
        gfx.drawString(mc.font, subtitle, subX, y + 56, (Math.round(alpha * 0.96f) << 24) | 0x00FFF6E8, false);
    }
    private static void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, float pulse) {
        gfx.fill(x - 3, y - 3, x + w + 3, y + h + 3, withOpacity(0xFF65E9FF, 0.08f + pulse * 0.05f));
        gfx.fill(x, y, x + w, y + h, 0xD0060A13);
        gfx.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xA40D1420);
        gfx.fill(x + 6, y + 6, x + w - 6, y + h - 6, 0x40172331);
        gfx.fill(x, y, x + w, y + 2, lerpColor(0xFFFF7A5E, 0xFF65E9FF, pulse));
        gfx.fill(x, y + h - 2, x + w, y + h, withOpacity(0xFF65E9FF, 0.70f));
        gfx.fill(x, y, x + 2, y + h, withOpacity(0xFFFFD86B, 0.78f));
        gfx.fill(x + w - 2, y, x + w, y + h, withOpacity(0xFF65E9FF, 0.82f));
        gfx.fill(x + 8, y + 25, x + 58, y + 26, withOpacity(0xFF65E9FF, 0.42f));
        gfx.fill(x + w - 58, y + 25, x + w - 8, y + 26, withOpacity(0xFFFF7A5E, 0.42f));
    }

    private static void drawBar(GuiGraphics gfx, int x, int y, int width, int height, float ratio, int leftColor, int rightColor, float time) {
        float clampedRatio = clamp(ratio);
        int splitX = x + Math.round(width * clampedRatio);
        gfx.fill(x - 2, y - 3, x + width + 2, y + height + 3, 0xE2020409);
        gfx.fill(x, y, x + width, y + height, 0xDD172033);
        gfx.fill(x, y, splitX, y + height, leftColor);
        // Enemy/negative pressure is anchored at the right edge and advances right-to-left.
        gfx.fill(splitX, y, x + width, y + height, rightColor);
        gfx.fill(x, y, x + width, y + 2, 0x55FFFFFF);
        gfx.fill(x, y + height - 2, x + width, y + height, 0x33000000);
        int midX = x + width / 2;
        gfx.fill(midX, y - 5, midX + 1, y + height + 5, 0xAAF4F2FF);
        int shineX = x + Math.floorMod((int)(time * 3.2f), Math.max(1, width + 42)) - 21;
        gfx.fill(Math.max(x, shineX), y, Math.min(splitX, shineX + 18), y + height, 0x32FFFFFF);
        int enemyShineX = x + width - Math.floorMod((int)(time * 3.2f), Math.max(1, width + 42)) + 3;
        gfx.fill(Math.max(splitX, enemyShineX - 18), y, Math.min(x + width, enemyShineX), y + height, 0x2BFFFFFF);
        int markerX = Math.max(x + 2, Math.min(x + width - 2, splitX));
        int markerColor = lerpColor(leftColor, rightColor, clampedRatio);
        int markerGold = lerpColor(0xFFFFC94A, 0xFFFFF0A3, 0.45f);
        gfx.fill(markerX - 5, y - 5, markerX + 6, y + height + 5, 0xB5120D03);
        gfx.fill(markerX - 4, y - 4, markerX + 5, y + height + 4, withOpacity(markerGold, 0.98f));
        gfx.fill(markerX - 3, y - 3, markerX + 4, y + height + 3, withOpacity(0xFFFFF7C2, 0.74f));
        gfx.fill(markerX - 2, y - 2, markerX + 3, y + height + 2, withOpacity(markerColor, 0.30f));
        for (int i = 0; i < 8; i++) {
            int offset = Math.floorMod((int)(time * (i + 3)) + i * 13, 42) - 21;
            int px = markerX + offset;
            int py = y - 5 + (i % 3) * Math.max(2, (height + 2) / 3);
            gfx.fill(px, py, px + 2, py + 1, withOpacity(i % 2 == 0 ? leftColor : rightColor, 0.52f));
        }
    }
    private static String resolveTimerText(DomainClashHudSnapshotPacket pkt, int liveRemainingTicks) {
        ClashOutcome outcome = pkt.outcome;
        if (outcome == ClashOutcome.TIE) {
            return "\u23F1 DRAW";
        }
        if (outcome == ClashOutcome.WINNER_A || outcome == ClashOutcome.WINNER_B) {
            return outcome == ClashOutcome.WINNER_A ? "\u23F1 VICTORY" : "\u23F1 DEFEAT";
        }
        if (liveRemainingTicks <= 0) {
            return "\u23F1 0:00";
        }
        int totalSeconds = Math.max(0, (liveRemainingTicks + 19) / 20);
        return String.format(Locale.ROOT, "\u23F1 %d:%02d", Integer.valueOf(totalSeconds / 60), Integer.valueOf(totalSeconds % 60));
    }

    private static float normalizeRatio(float casterPower, float opponentPower) {
        float safeCaster = Math.max(0.0f, casterPower);
        float safeOpponent = Math.max(0.0f, opponentPower);
        float total = safeCaster + safeOpponent;
        if (total <= 0.0f) {
            return 0.5f;
        }
        return clamp(safeCaster / total);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int formColor(int amplifier) {
        return switch (amplifier) {
            case -1 -> 0xFFB06CFF;
            case 0 -> 0xFF48D7FF;
            case 1 -> 0xFFFF6A3D;
            default -> 0xFFBDB7C7;
        };
    }

    private static int selfPalette(int color) {
        return lerpColor(color, 0xFF56DFFF, 0.28f);
    }

    private static int enemyPalette(int color, int selfColor) {
        int adjusted = lerpColor(color, 0xFFFF7A55, 0.36f);
        if ((color & 0x00FFFFFF) == (selfColor & 0x00FFFFFF) || colorDistance(adjusted, selfPalette(selfColor)) < 88) {
            adjusted = lerpColor(adjustBrightness(adjusted, 0.72f), 0xFFFF7A55, 0.44f);
        }
        return adjusted;
    }

    private static int colorDistance(int a, int b) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return Math.abs(ar - br) + Math.abs(ag - bg) + Math.abs(ab - bb);
    }

    private static int adjustBrightness(int color, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
        int b = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
    private static String formBadge(int amplifier) {
        return switch (amplifier) {
            case -1 -> "[INC]";
            case 1 -> "[OPEN]";
            default -> "[CLOSED]";
        };
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * clamp(opacity))));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp(t);
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static void drawCornerGlyphs(GuiGraphics gfx, Font font, int x, int y, int w, float pulse) {
        int left = withOpacity(0xFF56DFFF, 0.74f + pulse * 0.20f);
        int right = withOpacity(0xFFFF7A55, 0.74f + (1.0f - pulse) * 0.20f);
        drawScaledGlyph(gfx, font, "\u262F", x + 8, y + 3, 1.15f, left);
        drawScaledGlyph(gfx, font, "\u2694", x + w - 20, y + 3, 1.15f, right);
        gfx.fill(x + 25, y + 11, x + 52, y + 12, withOpacity(left, 0.42f));
        gfx.fill(x + w - 52, y + 11, x + w - 25, y + 12, withOpacity(right, 0.42f));
    }

    private static void drawScaledGlyph(GuiGraphics gfx, Font font, String glyph, int x, int y, float scale, int color) {
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        gfx.pose().scale(scale, scale, 1.0f);
        gfx.drawString(font, glyph, 0, 0, color, false);
        gfx.pose().popPose();
    }
    private static void drawMarqueeText(GuiGraphics gfx, Font font, String text, int x, int y, int width, int color, float time, boolean rightAligned) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        int textWidth = font.width(text);
        if (textWidth <= width) {
            int drawX = rightAligned ? x + width - textWidth : x;
            gfx.drawString(font, text, drawX, y, color, false);
            return;
        }

        gfx.enableScissor(x, y - 1, x + width, y + font.lineHeight + 1);
        int cycle = textWidth + TEXT_MARQUEE_GAP;
        int offset = Math.floorMod(Math.round(time * TEXT_MARQUEE_SPEED), cycle);
        int drawX = rightAligned ? x + width - textWidth + offset : x - offset;
        gfx.drawString(font, text, drawX, y, color, false);
        gfx.drawString(font, text, drawX + (rightAligned ? -cycle : cycle), y, color, false);
        gfx.disableScissor();
    }

    private static String compactParticipantName(String name, String fallback) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || UUID_NAME.matcher(trimmed).matches()) {
            return fallback;
        }
        return trimmed;
    }

    private static String compactDomainLabel(int domainId) {
        String label = DomainAddonUtils.resolveDomainName(domainId);
        if (label == null || label.trim().isEmpty()) {
            return "Domain";
        }
        return label.trim();
    }

}






