package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
    private static final Pattern UUID_NAME = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int BAR_HEIGHT = 9;
    private static float displayedRatio = 0.5f;
    private static long lastActiveGameTime = -1L;

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

        float ratio = normalizeRatio(casterPower, totalOpponentPower);
        long gameTime = mc.level.getGameTime();
        if (lastActiveGameTime < 0L || gameTime - lastActiveGameTime > 8L) {
            displayedRatio = ratio;
        } else {
            displayedRatio += (ratio - displayedRatio) * 0.18f;
        }
        lastActiveGameTime = gameTime;

        float time = (float)gameTime + partialTick;
        float pulse = (float)(Math.sin(time * 0.16f) * 0.5f + 0.5f);
        int casterColor = withOpacity(formColor(ClientPacketHandler.ClientDomainClashCache.casterForm), 0.98f);
        int clusterColor = withOpacity(formColor(opponents.get(0).form), 0.98f);
        int panelWidth = Math.min(420, Math.max(330, (int)(sw * 0.42f)));
        int panelHeight = opponents.size() > 1 ? 92 : 86;
        int panelX = (sw - panelWidth) / 2;
        int panelY = 28;
        int pad = 10;

        drawPanel(gfx, panelX, panelY, panelWidth, panelHeight, pulse);

        String title = "DOMAIN CLASH";
        int titleColor = lerpColor(0xFFFFD76A, 0xFF62E8FF, pulse);
        gfx.drawString(mc.font, title, panelX + (panelWidth - mc.font.width(title)) / 2, panelY + 5, titleColor, false);

        int barX = panelX + pad;
        int barY = panelY + 31;
        int barWidth = panelWidth - pad * 2;
        int colGap = 18;
        int colWidth = (barWidth - colGap) / 2;

        int casterPct = Math.round(clamp(displayedRatio) * 100.0f);
        String leftPct = casterPct + "%";
        String rightPct = (100 - casterPct) + "%";
        gfx.drawString(mc.font, leftPct, barX, panelY + 18, casterColor, false);
        gfx.drawString(mc.font, rightPct, barX + barWidth - mc.font.width(rightPct), panelY + 18, clusterColor, false);

        drawBar(gfx, barX, barY, barWidth, BAR_HEIGHT, displayedRatio, casterColor, clusterColor, time);

        String casterType = formBadge(ClientPacketHandler.ClientDomainClashCache.casterForm);
        String rivalType = opponents.size() == 1 ? formBadge(opponents.get(0).form) : formBadge(opponents.get(0).form) + "+" + (opponents.size() - 1);
        gfx.drawString(mc.font, casterType, barX, panelY + 45, withOpacity(casterColor, 0.92f), false);
        gfx.drawString(mc.font, rivalType, barX + barWidth - mc.font.width(rivalType), panelY + 45, withOpacity(clusterColor, 0.92f), false);

        ClientPacketHandler.ClientDomainClashCache.OpponentCacheEntry topOpponent = opponents.get(0);
        String casterLabel = fitLabel(mc, compactLabel(ClientPacketHandler.ClientDomainClashCache.casterName,
                ClientPacketHandler.ClientDomainClashCache.casterDomainId), colWidth);
        String opponentLabel = fitLabel(mc, compactLabel(topOpponent.name, topOpponent.domainId), colWidth);
        int namesY = panelY + 58;
        gfx.drawString(mc.font, casterLabel, barX, namesY, 0xFFEDEDED, false);
        gfx.drawString(mc.font, opponentLabel, barX + barWidth - mc.font.width(opponentLabel), namesY, withOpacity(clusterColor, 0.95f), false);

        String leftPower = String.format(Locale.ROOT, "Power %.0f", casterPower);
        String rightPower = String.format(Locale.ROOT, "Power %.0f", totalOpponentPower);
        int powerY = panelY + 70;
        gfx.drawString(mc.font, fitLabel(mc, leftPower, colWidth), barX, powerY, 0xFFB8C0CC, false);
        String fittedRightPower = fitLabel(mc, rightPower, colWidth);
        gfx.drawString(mc.font, fittedRightPower, barX + barWidth - mc.font.width(fittedRightPower), powerY, 0xFFB8C0CC, false);
        if (opponents.size() > 1) {
            String more = fitLabel(mc, "Top rival shown • total " + opponents.size(), barWidth);
            gfx.drawString(mc.font, more, panelX + (panelWidth - mc.font.width(more)) / 2, panelY + 82, 0xFF6E7B8F, false);
        }
    }

    private static void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, float pulse) {
        gfx.fill(x - 4, y - 2, x + w + 4, y + h + 2, withOpacity(0xFF38D5FF, 0.04f + pulse * 0.03f));
        gfx.fill(x, y, x + w, y + h, 0xB80A0F18);
        gfx.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x96101622);
        gfx.fill(x + 1, y + 1, x + w - 1, y + 2, withOpacity(0xFFFFFFFF, 0.12f));
        gfx.fill(x, y, x + w, y + 1, lerpColor(0xFFFFD76A, 0xFF67E8F9, pulse));
        gfx.fill(x, y + h - 1, x + w, y + h, withOpacity(0xFF67E8F9, 0.55f));
        gfx.fill(x, y, x + 1, y + h, withOpacity(0xFFFFD76A, 0.65f));
        gfx.fill(x + w - 1, y, x + w, y + h, withOpacity(0xFF67E8F9, 0.65f));
    }

    private static void drawBar(GuiGraphics gfx, int x, int y, int width, int height, float ratio,
                                int leftColor, int rightColor, float time) {
        float clampedRatio = clamp(ratio);
        int splitX = x + Math.round(width * clampedRatio);
        gfx.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xE2020409);
        gfx.fill(x, y, x + width, y + height, 0xCC141A26);
        gfx.fill(x, y, splitX, y + height, leftColor);
        gfx.fill(splitX, y, x + width, y + height, rightColor);
        gfx.fill(x, y, x + width, y + 2, 0x44FFFFFF);
        gfx.fill(x + width / 2, y - 3, x + width / 2 + 1, y + height + 3, 0xCCFFFFFF);
        int shineX = x + Math.floorMod((int)(time * 3.0f), Math.max(1, width + 34)) - 17;
        gfx.fill(Math.max(x, shineX), y, Math.min(x + width, shineX + 14), y + height, 0x30FFFFFF);
        int markerX = Math.max(x, Math.min(x + width - 1, splitX));
        gfx.fill(markerX - 4, y - 4, markerX + 5, y + height + 4, 0x22FFFFFF);
        gfx.fill(markerX - 1, y - 4, markerX + 2, y + height + 4, 0xFFFFF0A8);
        for (int i = 0; i < 6; i++) {
            int drift = Math.floorMod((int)(time * (i + 2)) + i * 13, 42) - 21;
            int px = markerX + drift;
            int py = y - 7 + (i % 3) * (height + 1) / 2;
            int col = i % 2 == 0 ? withOpacity(leftColor, 0.62f) : withOpacity(rightColor, 0.62f);
            gfx.fill(px, py, px + 2 + i % 2, py + 1, col);
        }
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

    private static int formColor(int form) {
        return switch (form) {
            case 0 -> 0xFFB06CFF;
            case 1 -> 0xFF48D7FF;
            case 2 -> 0xFFFF6A3D;
            default -> 0xFFBDC3C7;
        };
    }

    private static String formLabel(int form) {
        return switch (form) {
            case 0 -> "INCOMPLETE";
            case 1 -> "CLOSED";
            case 2 -> "OPEN";
            default -> "UNKNOWN";
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

    private static String formBadge(int form) {
        return switch (form) {
            case 0 -> "[INC]";
            case 1 -> "[CLOSED]";
            case 2 -> "[OPEN]";
            default -> "[?]";
        };
    }

    private static String compactLabel(String playerName, int domainId) {
        String domainName = compactDomainName(DomainAddonUtils.resolveDomainName(domainId));
        String safePlayerName = playerName == null ? "" : playerName.trim();
        if (safePlayerName.isEmpty() || UUID_NAME.matcher(safePlayerName).matches()) {
            safePlayerName = "Caster";
        }
        if (safePlayerName.length() > 13) {
            safePlayerName = safePlayerName.substring(0, 11) + "…";
        }
        if (!domainName.isEmpty()) {
            return safePlayerName + " • " + domainName;
        }
        return safePlayerName + " • Domain";
    }

    private static String compactDomainName(String domainName) {
        if (domainName == null) {
            return "";
        }
        String safe = domainName.trim();
        if (safe.length() <= 18) {
            return safe;
        }
        return safe.substring(0, 16) + "…";
    }

    private static String fitLabel(Minecraft mc, String label, int maxWidth) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if (mc.font.width(label) <= maxWidth) {
            return label;
        }
        String ellipsis = "…";
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
