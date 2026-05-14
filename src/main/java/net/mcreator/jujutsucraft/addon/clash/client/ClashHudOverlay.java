package net.mcreator.jujutsucraft.addon.clash.client;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.OUTCOME_DISPLAY_HOLD_TICKS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private static final int BAR_HEIGHT = 9;
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
        if (banner != null) {
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
        int casterColor = withOpacity(formColor(pkt.casterFormId), 0.98f);
        int opponentColor = withOpacity(formColor(pkt.opponentFormId), 0.98f);
        int panelWidth = Math.min(560, Math.max(430, (int) (sw * 0.58f)));
        int panelHeight = 128;
        int panelX = (sw - panelWidth) / 2;
        int panelY = 10;
        int pad = 16;

        drawPanel(gfx, panelX, panelY, panelWidth, panelHeight, pulse);

        String title = "+ DOMAIN CLASH +";
        int titleColor = lerpColor(0xFF65E9FF, 0xFFFFD86B, pulse);
        gfx.drawString(mc.font, title, panelX + (panelWidth - mc.font.width(title)) / 2, panelY + 9, titleColor, false);
        drawSparkIcon(gfx, panelX + panelWidth / 2 - mc.font.width(title) / 2 - 12, panelY + 14, 0xFF65E9FF, time, false);
        drawSparkIcon(gfx, panelX + panelWidth / 2 + mc.font.width(title) / 2 + 9, panelY + 14, 0xFF65E9FF, time, false);

        int barX = panelX + pad;
        int barY = panelY + 48;
        int barWidth = panelWidth - pad * 2;
        int colGap = 34;
        int colWidth = (barWidth - colGap) / 2;

        int casterPct = Math.round(clamp(displayedRatio) * 100.0f);
        String leftPct = casterPct + "%";
        String rightPct = (100 - casterPct) + "%";
        drawCornerIcon(gfx, barX + 5, panelY + 17, casterColor, time, false);
        drawCornerIcon(gfx, barX + barWidth - 8, panelY + 17, opponentColor, time, true);
        gfx.drawString(mc.font, leftPct, barX, panelY + 31, casterColor, false);
        gfx.drawString(mc.font, rightPct, barX + barWidth - mc.font.width(rightPct), panelY + 31, opponentColor, false);

        String timerText = resolveTimerText(pkt, Math.max(0, pkt.remainingTicks - (int) elapsed));
        drawTimer(gfx, mc, panelX + panelWidth / 2, panelY + 30, timerText, time);
        drawBar(gfx, barX, barY, barWidth, BAR_HEIGHT, displayedRatio, casterColor, opponentColor, time);

        String casterType = formBadge(pkt.casterFormId);
        String rivalType = formBadge(pkt.opponentFormId);
        int typeY = panelY + 70;
        drawFormIcon(gfx, barX + 8, typeY + 4, casterColor, time, false);
        gfx.drawString(mc.font, casterType, barX + 22, typeY, withOpacity(casterColor, 0.96f), false);
        int rightTypeX = barX + barWidth - mc.font.width(rivalType) - 22;
        drawFormIcon(gfx, rightTypeX - 14, typeY + 4, opponentColor, time, true);
        gfx.drawString(mc.font, rivalType, rightTypeX, typeY, withOpacity(opponentColor, 0.96f), false);

        String casterLabel = fitLabel(mc, DomainAddonUtils.resolveDomainName(pkt.casterDomainId), colWidth + 18);
        String opponentLabel = fitLabel(mc, DomainAddonUtils.resolveDomainName(pkt.opponentDomainId), colWidth + 18);
        int namesY = panelY + 86;
        gfx.drawString(mc.font, casterLabel, barX, namesY, 0xFFF4F2FF, false);
        gfx.drawString(mc.font, opponentLabel, barX + barWidth - mc.font.width(opponentLabel), namesY, withOpacity(opponentColor, 0.95f), false);

        String leftPower = String.format(Locale.ROOT, "Power %.0f", casterPower);
        String rightPower = String.format(Locale.ROOT, "Power %.0f", opponentPower);
        int powerY = panelY + 107;
        drawPowerText(gfx, mc, fitLabel(mc, leftPower, colWidth), barX, powerY, casterColor, false, time);
        String fittedRightPower = fitLabel(mc, rightPower, colWidth);
        drawPowerText(gfx, mc, fittedRightPower, barX + barWidth - mc.font.width(fittedRightPower), powerY, opponentColor, true, time);
    }
    private static void renderOutcomeBanner(GuiGraphics gfx, Minecraft mc, int sw, long gameTime, OutcomeBanner banner) {
        long age = Math.max(0L, gameTime - banner.receiptTick);
        if (age > 60L) {
            return;
        }
        String text;
        int color;
        if (banner.outcome == ClashOutcome.TIE) {
            text = "MUTUAL COLLAPSE";
            color = 0xFFF6E58D;
        } else if (banner.localWon) {
            text = "DOMAIN DOMINATES";
            color = 0xFF62E8FF;
        } else {
            text = "DOMAIN COLLAPSES";
            color = 0xFFFF9F7A;
        }
        int alpha = Math.max(80, 255 - (int) (age * 3L));
        int drawColor = (alpha << 24) | (color & 0x00FFFFFF);
        int x = sw / 2 - mc.font.width(text) / 2;
        int y = 122;
        gfx.drawString(mc.font, text, x + 1, y + 1, (alpha << 24), false);
        gfx.drawString(mc.font, text, x, y, drawColor, false);
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
        gfx.fill(x + 9, y + 30, x + 72, y + 32, withOpacity(0xFF65E9FF, 0.42f));
        gfx.fill(x + w - 72, y + 30, x + w - 9, y + 32, withOpacity(0xFFFF7A5E, 0.42f));
    }

    private static void drawSparkIcon(GuiGraphics gfx, int x, int y, int color, float time, boolean cross) {
        int glow = withOpacity(color, 0.50f + (float) Math.sin(time * 0.22f) * 0.16f);
        gfx.fill(x + 3, y - 5, x + 6, y + 8, glow);
        gfx.fill(x - 2, y, x + 11, y + 3, glow);
        gfx.fill(x + 1, y - 2, x + 8, y + 5, withOpacity(0xFFFFFFFF, 0.55f));
        if (cross) {
            gfx.fill(x - 2, y - 4, x, y - 2, 0xFFFF7A5E);
            gfx.fill(x + 9, y + 5, x + 11, y + 7, 0xFFFF7A5E);
        }
    }

    private static void drawCornerIcon(GuiGraphics gfx, int cx, int cy, int color, float time, boolean cross) {
        int ring = withOpacity(color, 0.70f + (float) Math.sin(time * 0.18f) * 0.14f);
        gfx.fill(cx - 5, cy - 5, cx + 6, cy - 3, ring);
        gfx.fill(cx - 5, cy + 4, cx + 6, cy + 6, ring);
        gfx.fill(cx - 5, cy - 5, cx - 3, cy + 6, ring);
        gfx.fill(cx + 4, cy - 5, cx + 6, cy + 6, ring);
        gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        if (cross) {
            gfx.fill(cx - 4, cy - 4, cx - 2, cy - 2, 0xFFFF7A5E);
            gfx.fill(cx + 2, cy + 2, cx + 4, cy + 4, 0xFFFF7A5E);
            gfx.fill(cx + 2, cy - 4, cx + 4, cy - 2, 0xFFFF7A5E);
            gfx.fill(cx - 4, cy + 2, cx - 2, cy + 4, 0xFFFF7A5E);
        }
    }

    private static void drawFormIcon(GuiGraphics gfx, int cx, int cy, int color, float time, boolean cross) {
        drawSparkIcon(gfx, cx - 4, cy - 1, color, time, cross);
    }

    private static void drawTimer(GuiGraphics gfx, Minecraft mc, int centerX, int y, String text, float time) {
        int color = lerpColor(0xFFFFD86B, 0xFFFFFFFF, (float) (Math.sin(time * 0.28f) * 0.5f + 0.5f));
        int textX = centerX - mc.font.width(text) / 2 + 5;
        gfx.fill(textX - 10, y - 1, textX - 8, y + 8, 0xFFFFD86B);
        gfx.fill(textX - 13, y + 1, textX - 5, y + 3, 0x88FFD86B);
        gfx.fill(textX - 12, y + 5, textX - 6, y + 7, 0x88FFD86B);
        gfx.drawString(mc.font, text, textX, y, color, false);
        for (int i = 0; i < 3; i++) {
            int px = centerX + 42 + i * 10;
            int py = y + 4 + Math.round((float) Math.sin(time * 0.18f + i) * 2.0f);
            gfx.fill(px, py, px + 4, py + 2, withOpacity(i % 2 == 0 ? 0xFF9C7CFF : 0xFF9EDDF2, 0.55f));
        }
    }

    private static void drawPowerText(GuiGraphics gfx, Minecraft mc, String text, int x, int y, int color, boolean right, float time) {
        gfx.drawString(mc.font, text, x, y, withOpacity(color, 0.95f), false);
        int sx = right ? x + mc.font.width(text) + 8 : x - 8;
        int pulse = withOpacity(0xFFFFFFFF, 0.55f + (float) Math.sin(time * 0.24f) * 0.22f);
        gfx.fill(sx - 1, y + 2, sx + 2, y + 5, pulse);
        gfx.fill(sx - 3, y + 3, sx + 4, y + 4, pulse);
    }
    private static void drawBar(GuiGraphics gfx, int x, int y, int width, int height, float ratio, int leftColor, int rightColor, float time) {
        float clampedRatio = clamp(ratio);
        int splitX = x + Math.round(width * clampedRatio);
        gfx.fill(x - 2, y - 3, x + width + 2, y + height + 3, 0xE2020409);
        gfx.fill(x, y, x + width, y + height, 0xDD172033);
        gfx.fill(x, y, splitX, y + height, leftColor);
        gfx.fill(splitX, y, x + width, y + height, rightColor);
        gfx.fill(x, y, x + width, y + 2, 0x55FFFFFF);
        gfx.fill(x, y + height - 2, x + width, y + height, 0x33000000);
        int midX = x + width / 2;
        gfx.fill(midX - 1, y - 6, midX + 1, y + height + 6, 0xCCF4F2FF);
        int shineX = x + Math.floorMod((int) (time * 3.2f), Math.max(1, width + 42)) - 21;
        gfx.fill(Math.max(x, shineX), y, Math.min(x + width, shineX + 18), y + height, 0x32FFFFFF);
        int markerX = Math.max(x, Math.min(x + width - 1, splitX));
        gfx.fill(markerX - 7, y - 7, markerX + 8, y + height + 7, 0x20FFFFFF);
        gfx.fill(markerX - 2, y - 8, markerX + 3, y + height + 8, 0xFFE9E5A7);
        gfx.fill(markerX - 5, y - 2, markerX + 6, y + height + 2, 0x66FFD86B);
        for (int i = 0; i < 18; i++) {
            float phase = ((time * (0.42f + i * 0.018f) + i * 9.0f) % 100.0f) / 100.0f;
            int direction = i % 2 == 0 ? -1 : 1;
            int travel = Math.round((12.0f + phase * 82.0f) * direction);
            int px = markerX + travel;
            if (px < x - 8 || px > x + width + 8) {
                continue;
            }
            int py = y - 12 + Math.floorMod(i * 7 + (int) (time * 0.8f), height + 24);
            float fade = 1.0f - phase;
            int base = i % 4 == 0 ? 0xFFFFD86B : (direction < 0 ? leftColor : rightColor);
            int col = withOpacity(base, 0.16f + fade * 0.60f);
            int blur = withOpacity(base, 0.08f + fade * 0.22f);
            int len = 2 + Math.round(fade * 7.0f);
            gfx.fill(px - direction * len, py, px, py + 1, blur);
            gfx.fill(px, py - 1, px + 2, py + 1, col);
        }
    }
    private static String resolveTimerText(DomainClashHudSnapshotPacket pkt, int liveRemainingTicks) {
        ClashOutcome outcome = pkt.outcome;
        if (outcome == ClashOutcome.TIE) {
            return "DRAW";
        }
        if (outcome == ClashOutcome.WINNER_A || outcome == ClashOutcome.WINNER_B) {
            return pkt.casterPower > pkt.opponentPower ? "VICTORY" : "DEFEAT";
        }
        if (liveRemainingTicks <= 0) {
            return "OVERTIME";
        }
        int totalSeconds = Math.max(0, Math.round(liveRemainingTicks / 20.0f));
        return String.format(Locale.ROOT, "%d:%02d", Integer.valueOf(totalSeconds / 60), Integer.valueOf(totalSeconds % 60));
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
            case 1 -> 0xFFFF6A3D;
            default -> 0xFF48D7FF;
        };
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

    private static String fitLabel(Minecraft mc, String label, int maxWidth) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if (mc.font.width(label) <= maxWidth) {
            return label;
        }
        String ellipsis = "...";
        int end = label.length();
        while (end > 0) {
            String candidate = label.substring(0, end) + ellipsis;
            if (mc.font.width(candidate) <= maxWidth) {
                return candidate;
            }
            end--;
        }
        return ellipsis;
    }
}



