package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DomainClashOverlay {
    private static final int PANEL_WIDTH = 240;
    private static final int SUMMARY_BAR_WIDTH = 220;
    private static final int SUMMARY_BAR_HEIGHT = 12;
    private static final int ROW_BAR_WIDTH = 164;
    private static final int ROW_BAR_HEIGHT = 7;
    private static final int PANEL_Y = 28;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("domain_clash", DomainClashOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int sw, int sh) {
        if (!ClientPacketHandler.ClientDomainClashCache.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) {
            return;
        }

        List<ClientPacketHandler.ClientDomainClashCache.OpponentCacheEntry> opponents = new ArrayList<>(ClientPacketHandler.ClientDomainClashCache.opponents);
        if (opponents.isEmpty()) {
            return;
        }
        opponents.sort(Comparator.comparingDouble(entry -> -entry.power));

        float casterPower = Math.max(0.0f, ClientPacketHandler.ClientDomainClashCache.casterPower);
        float totalOpponentPower = 0.0f;
        for (ClientPacketHandler.ClientDomainClashCache.OpponentCacheEntry opponent : opponents) {
            totalOpponentPower += Math.max(0.0f, opponent.power);
        }

        float combinedRatio = normalizeRatio(casterPower, totalOpponentPower);
        int casterColor = withOpacity(formColor(ClientPacketHandler.ClientDomainClashCache.casterForm), 0.92f);
        int clusterColor = withOpacity(formColor(opponents.get(0).form), 0.92f);

        long age = Math.max(0L, mc.level.getGameTime() - ClientPacketHandler.ClientDomainClashCache.syncedGameTime);
        float freshness = clamp(1.0f - (float)age / 40.0f);
        float overlayAlpha = 0.6f + freshness * 0.4f;

        int panelHeight = 45 + opponents.size() * 18;
        int panelX = (sw - PANEL_WIDTH) / 2;
        int panelY = PANEL_Y;
        int summaryX = panelX + 10;
        int summaryY = panelY + 18;

        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, withOpacity(0xCC10131A, overlayAlpha));
        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, withOpacity(0xFFF2F2F2, overlayAlpha));
        gfx.fill(panelX, panelY + panelHeight - 1, panelX + PANEL_WIDTH, panelY + panelHeight, withOpacity(0x66FFFFFF, overlayAlpha));

        String title = opponents.size() > 1 ? "DOMAIN CLASH - VS " + opponents.size() + " DOMAINS" : "DOMAIN CLASH";
        gfx.drawString(mc.font, title, panelX + 8, panelY + 6, withOpacity(0xFFF6D365, overlayAlpha), true);

        String summaryText = percentText(combinedRatio);
        int summaryTextWidth = mc.font.width(summaryText);
        gfx.drawString(mc.font, summaryText, panelX + PANEL_WIDTH - summaryTextWidth - 8, panelY + 6, withOpacity(0xFFFFFFFF, overlayAlpha), true);

        drawBar(gfx, summaryX, summaryY, SUMMARY_BAR_WIDTH, SUMMARY_BAR_HEIGHT, combinedRatio,
                casterColor, clusterColor, overlayAlpha);

        String casterLabel = fitLabel(mc, buildLabel(
                ClientPacketHandler.ClientDomainClashCache.casterName,
                ClientPacketHandler.ClientDomainClashCache.casterDomainId), 108);
        String rivalsLabel = opponents.size() == 1
                ? fitLabel(mc, buildLabel(opponents.get(0).name, opponents.get(0).domainId), 108)
                : opponents.size() + " active rivals";
        gfx.drawString(mc.font, casterLabel, summaryX, summaryY + SUMMARY_BAR_HEIGHT + 3, withOpacity(0xFFFFFFFF, overlayAlpha), false);
        int rivalsWidth = mc.font.width(rivalsLabel);
        gfx.drawString(mc.font, rivalsLabel, summaryX + SUMMARY_BAR_WIDTH - rivalsWidth, summaryY + SUMMARY_BAR_HEIGHT + 3, withOpacity(0xFFFFFFFF, overlayAlpha), false);

        int rowY = panelY + 46;
        for (ClientPacketHandler.ClientDomainClashCache.OpponentCacheEntry opponent : opponents) {
            float ratio = normalizeRatio(casterPower, opponent.power);
            int opponentColor = withOpacity(formColor(opponent.form), 0.92f);
            String opponentLabel = fitLabel(mc, buildLabel(opponent.name, opponent.domainId), 140);
            String rowText = percentText(ratio);

            gfx.fill(panelX + 8, rowY - 1, panelX + PANEL_WIDTH - 8, rowY + 15, withOpacity(0x4419202A, overlayAlpha));
            gfx.fill(panelX + 10, rowY + 2, panelX + 14, rowY + 12, casterColor);
            gfx.fill(panelX + PANEL_WIDTH - 14, rowY + 2, panelX + PANEL_WIDTH - 10, rowY + 12, opponentColor);
            gfx.drawString(mc.font, opponentLabel, panelX + 18, rowY + 2, withOpacity(0xFFF7F7F7, overlayAlpha), false);
            int rowTextWidth = mc.font.width(rowText);
            gfx.drawString(mc.font, rowText, panelX + PANEL_WIDTH - rowTextWidth - 18, rowY + 2, withOpacity(0xFFE9D48A, overlayAlpha), false);
            drawBar(gfx, panelX + 38, rowY + 11, ROW_BAR_WIDTH, ROW_BAR_HEIGHT, ratio, casterColor, opponentColor, overlayAlpha);
            rowY += 18;
        }
    }

    private static void drawBar(GuiGraphics gfx, int x, int y, int width, int height, float ratio,
                                int leftColor, int rightColor, float alpha) {
        float clampedRatio = clamp(ratio);
        int splitX = x + Math.round(width * clampedRatio);
        gfx.fill(x - 1, y - 1, x + width + 1, y + height + 1, withOpacity(0xCC05070A, alpha));
        gfx.fill(x, y, x + width, y + height, withOpacity(0x55101010, alpha));
        gfx.fill(x, y, splitX, y + height, leftColor);
        gfx.fill(splitX, y, x + width, y + height, rightColor);
        gfx.fill(x + width / 2, y - 1, x + width / 2 + 1, y + height + 1, withOpacity(0xAAFFFFFF, alpha));
        gfx.fill(Math.max(x, splitX - 1), y - 1, Math.min(x + width, splitX + 1), y + height + 1, withOpacity(0x88FFF1A8, alpha));
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

    private static String percentText(float ratio) {
        int casterPct = Math.round(clamp(ratio) * 100.0f);
        return casterPct + "% vs " + (100 - casterPct) + "%";
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int formColor(int form) {
        return switch (form) {
            case 0 -> 0xFF9B59B6;
            case 1 -> 0xFF3498DB;
            case 2 -> 0xFFE74C3C;
            default -> 0xFFBDC3C7;
        };
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * clamp(opacity))));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static String buildLabel(String playerName, int domainId) {
        String safePlayerName = playerName == null || playerName.isEmpty() ? "Unknown" : playerName;
        String domainName = DomainAddonUtils.resolveDomainName(domainId);
        if (domainName.isEmpty()) {
            return safePlayerName + "'s Domain Expansion";
        }
        return safePlayerName + "'s " + domainName;
    }

    private static String fitLabel(Minecraft mc, String label, int maxWidth) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if (mc.font.width(label) <= maxWidth) {
            return label;
        }
        String ellipsis = "...";
        int ellipsisWidth = mc.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }
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
