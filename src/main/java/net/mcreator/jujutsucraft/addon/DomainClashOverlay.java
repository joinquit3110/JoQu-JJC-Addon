package net.mcreator.jujutsucraft.addon;

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
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_Y_OFFSET = 40;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("domain_clash", DomainClashOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int sw, int sh) {
        if (!ClientPacketHandler.ClientDomainClashCache.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        float ratio = ClientPacketHandler.ClientDomainClashCache.powerRatio;
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        int casterForm = ClientPacketHandler.ClientDomainClashCache.casterForm;
        int opponentForm = ClientPacketHandler.ClientDomainClashCache.opponentForm;

        int barX = (sw - BAR_WIDTH) / 2;
        int barY = BAR_Y_OFFSET;

        gfx.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xAA000000);

        int casterColor = formColor(casterForm);
        int opponentColor = formColor(opponentForm);

        int splitX = barX + (int)(BAR_WIDTH * ratio);
        gfx.fill(barX, barY, splitX, barY + BAR_HEIGHT, casterColor);
        gfx.fill(splitX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, opponentColor);

        int centerMarkerX = barX + BAR_WIDTH / 2;
        gfx.fill(centerMarkerX, barY - 1, centerMarkerX + 1, barY + BAR_HEIGHT + 1, 0xAAFFFFFF);

        String casterLabel = buildLabel(
                ClientPacketHandler.ClientDomainClashCache.casterName,
                ClientPacketHandler.ClientDomainClashCache.casterDomainId);
        String opponentLabel = buildLabel(
                ClientPacketHandler.ClientDomainClashCache.opponentName,
                ClientPacketHandler.ClientDomainClashCache.opponentDomainId);

        int pctInt = (int)(ratio * 100);
        String pctText = pctInt + "% vs " + (100 - pctInt) + "%";
        int pctWidth = mc.font.width(pctText);
        int centerReserve = Math.max(56, pctWidth + 18);
        int maxLabelWidth = Math.max(22, (BAR_WIDTH - centerReserve) / 2 - 2);

        casterLabel = fitLabel(mc, casterLabel, maxLabelWidth);
        opponentLabel = fitLabel(mc, opponentLabel, maxLabelWidth);

        int labelY = barY + BAR_HEIGHT + 3;
        gfx.drawString(mc.font, casterLabel, barX, labelY, 0xFFFFFFFF, true);

        int oppLabelWidth = mc.font.width(opponentLabel);
        gfx.drawString(mc.font, opponentLabel, barX + BAR_WIDTH - oppLabelWidth, labelY, 0xFFFFFFFF, true);

        gfx.drawString(mc.font, pctText, (sw - pctWidth) / 2, barY - 11, 0xFFFFFF00, true);
    }

    private static int formColor(int form) {
        return switch (form) {
            case 0 -> 0xDD888888;
            case 1 -> 0xDD4488FF;
            case 2 -> 0xDDFF4444;
            default -> 0xDDCCCCCC;
        };
    }

    private static String buildLabel(String playerName, int domainId) {
        String domainName = DomainAddonUtils.resolveDomainName(domainId);
        if (domainName.isEmpty()) {
            return playerName + "'s Domain Expansion";
        }
        return playerName + "'s " + domainName;
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
