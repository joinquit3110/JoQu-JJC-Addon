package net.mcreator.jujutsucraft.addon;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import org.joml.Matrix4f;

public class SkillWheelScreen extends net.minecraft.client.gui.screens.Screen {
    private static final float WHEEL_RADIUS = 90.0f;
    private static final float INNER_RADIUS = 35.0f;
    private static final float ICON_RADIUS = 65.0f;
    private static final float CENTER_DOT_RADIUS = 6.0f;
    private static final int SPIRIT_SELECT_OFFSET = 100;

    // ── Confirm flash / particles / sounds (from NEW addon) ──────────────────────
    private float confirmFlash = 0f;
    private final Random rng = new Random();

    // ── Standard old-wheel state ───────────────────────────────────────────────
    private final List<List<ModNetworking.WheelTechniqueEntry>> pages;
    private final double currentSelectId;
    private int currentPage = 0;
    private int hoveredIndex = -1;
    private int lastHoveredTickIndex = -1;
    private int selectedIndex = -1;
    private long openTime;
    private long openTick;
    private float animProgress = 0.0f;
    private float pulseTime = 0.0f;
    private boolean selectionConfirmed = false;
    private boolean closing = false;

    public SkillWheelScreen(List<List<ModNetworking.WheelTechniqueEntry>> pages, double currentSelectId) {
        super(Component.translatable("Skill Wheel"));
        this.pages = pages;
        this.currentSelectId = currentSelectId;
        if (!pages.isEmpty()) {
            List<ModNetworking.WheelTechniqueEntry> firstPage = pages.get(0);
            for (int i = 0; i < firstPage.size(); ++i) {
                if (firstPage.get(i).selectId() != currentSelectId) continue;
                this.selectedIndex = i;
                break;
            }
        }
    }

    private List<ModNetworking.WheelTechniqueEntry> currentTechniques() {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            return this.pages.get(this.currentPage);
        }
        return List.of();
    }

    private boolean isMultiPage() {
        return this.pages.size() > 1;
    }

    private boolean isSpiritEntry(ModNetworking.WheelTechniqueEntry entry) {
        return entry.selectId() >= 100.0;
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        this.openTick = mc.level != null ? mc.level.getGameTime() : 0L;
        this.selectionConfirmed = false;
        this.closing = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.pulseTime += 0.15f;
        if (this.confirmFlash > 0f) this.confirmFlash -= 0.05f;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount) {
        if (!this.isMultiPage()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount);
        }
        int oldPage = this.currentPage;
        if (horizontalAmount > 0) {
            this.currentPage = (this.currentPage + 1) % this.pages.size();
        } else if (horizontalAmount < 0) {
            this.currentPage = (this.currentPage - 1 + this.pages.size()) % this.pages.size();
        }
        if (oldPage != this.currentPage) {
            this.hoveredIndex = -1;
            this.lastHoveredTickIndex = -1;
            this.selectedIndex = -1;
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.15f, 0.95f + this.currentPage * 0.04f));
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float elapsed = System.currentTimeMillis() - this.openTime;
        this.animProgress = Math.min(elapsed / 250.0f, 1.0f);
        float ease = this.easeOutBack(this.animProgress);
        int cx = this.width / 2;
        int cy = this.height / 2;
        List<ModNetworking.WheelTechniqueEntry> techniques = this.currentTechniques();

        double dx = mouseX - cx, dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int prevHovered = this.hoveredIndex;
        if (dist > (double)(35.0f * ease) && !techniques.isEmpty()) {
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360;
            angle = (angle + 90) % 360;
            this.hoveredIndex = (int)(angle / (360.0 / techniques.size()));
            if (this.hoveredIndex >= techniques.size()) this.hoveredIndex = techniques.size() - 1;
            if (this.hoveredIndex >= 0 && this.isCooldownBlocked(techniques.get(this.hoveredIndex))) {
                this.hoveredIndex = -1;
            }
        } else {
            this.hoveredIndex = -1;
        }

        if (this.hoveredIndex != prevHovered) {
            // Only play navigation sound for real (non-closing) hover changes.
            // This prevents a phantom sound when the wheel closes and hoveredIndex
            // transitions to -1: the guard above already blocks the play, but we
            // also guard lastHoveredTickIndex here so a second close attempt
            // (e.g. rapid TAB press) does not re-trigger the condition.
            if (this.hoveredIndex != -1 && !this.closing) {
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT, 0.008f, 1.0f + this.hoveredIndex * 0.015f));
            }
            this.lastHoveredTickIndex = this.hoveredIndex;
        }

        graphics.fill(0, 0, this.width, this.height, 0x55000000);
        this.drawWheel(graphics, cx, cy, ease, techniques);
        this.drawLabels(graphics, cx, cy, ease, techniques);
        this.drawCenterInfo(graphics, cx, cy, ease, techniques);
        if (this.isMultiPage()) this.drawPageIndicator(graphics, cx, cy, ease);
        this.drawConfirmFlash(graphics, cx, cy);
    }

    private void drawWheel(GuiGraphics graphics, int cx, int cy, float ease, List<ModNetworking.WheelTechniqueEntry> techniques) {
        if (techniques.isEmpty()) return;
        int count = techniques.size();
        float sliceAngle = 360.0f / (float)count;
        float currentRadius = 90.0f * ease;
        float currentInner = 35.0f * ease;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (int i = 0; i < count; ++i) {
            ModNetworking.WheelTechniqueEntry tech = techniques.get(i);
            int skillCooldown = this.getEntryCooldownRemaining(tech);
            int skillCooldownMax = this.getEntryCooldownMax(tech);
            boolean cooldownBlocked = this.isCooldownBlocked(tech);
            boolean isHovered = i == this.hoveredIndex;
            boolean isSelected = i == this.selectedIndex;
            float startAngle = -90.0f + (float)i * sliceAngle;
            float endAngle = startAngle + sliceAngle;
            float hoverPulse = isHovered ? (float)(Math.sin(this.pulseTime * 3.0f) * 2.0 + 12.0) : 0.0f;
            float outerR = currentRadius + hoverPulse;

            int baseColor = tech.color();
            int r = (baseColor >> 16) & 0xFF;
            int g = (baseColor >> 8) & 0xFF;
            int b = baseColor & 0xFF;
            if (cooldownBlocked) {
                int avg = (r + g + b) / 3;
                r = (int)((double)avg * 0.22 + (double)r * 0.12);
                g = (int)((double)avg * 0.22 + (double)g * 0.12);
                b = (int)((double)avg * 0.22 + (double)b * 0.12);
            }
            int alpha = cooldownBlocked ? 170 : (isHovered ? 200 : (isSelected ? 160 : 90));
            alpha = (int)((float)alpha * ease);

            this.drawArcSlice(pose, cx, cy, currentInner, outerR, startAngle, endAngle, r, g, b, alpha);

            if (cooldownBlocked && skillCooldownMax > 0) {
                float cooldownRatio = Math.max(0.0f, Math.min(1.0f, (float)skillCooldown / (float)skillCooldownMax));
                float sweepEnd = startAngle + sliceAngle * cooldownRatio;
                this.drawArcSlice(pose, cx, cy, currentInner + 1.0f, outerR - 1.0f, startAngle, sweepEnd, 0, 0, 0, (int)(110.0f * ease));
                if (cooldownRatio > 0.03f && cooldownRatio < 0.97f) {
                    float edgeRad = (float)Math.toRadians(sweepEnd);
                    float ex1 = (float)cx + (float)Math.cos(edgeRad) * (currentInner + 2.0f);
                    float ey1 = (float)cy + (float)Math.sin(edgeRad) * (currentInner + 2.0f);
                    float ex2 = (float)cx + (float)Math.cos(edgeRad) * (outerR - 2.0f);
                    float ey2 = (float)cy + (float)Math.sin(edgeRad) * (outerR - 2.0f);
                    int origR = (baseColor >> 16) & 0xFF;
                    int origG = (baseColor >> 8) & 0xFF;
                    int origB = baseColor & 0xFF;
                    this.drawLine(pose, ex1, ey1, ex2, ey2, origR, origG, origB, (int)(220.0f * ease));
                }
                float cdSecs = (float)skillCooldown / 20.0f;
                if (cdSecs < 1.5f && ((int)(this.pulseTime * 4.0f) % 2 == 0)) {
                    int origR = (baseColor >> 16) & 0xFF;
                    int origG = (baseColor >> 8) & 0xFF;
                    int origB = baseColor & 0xFF;
                    this.drawArcSlice(pose, cx, cy, currentInner + 1.0f, outerR - 1.0f, sweepEnd, endAngle, origR, origG, origB, (int)(55.0f * ease));
                }
            }

            if (isHovered || isSelected) {
                int borderAlpha = isHovered ? (int)(255.0f * ease) : (int)(180.0f * ease);
                this.drawArcOutline(pose, cx, cy, currentInner, outerR, startAngle, endAngle, r, g, b, borderAlpha, isHovered ? 2.5f : 1.5f);
            }

            if (count > 1) {
                float rad = (float)Math.toRadians(startAngle);
                float x1 = (float)cx + (float)Math.cos(rad) * currentInner;
                float y1 = (float)cy + (float)Math.sin(rad) * currentInner;
                float x2 = (float)cx + (float)Math.cos(rad) * currentRadius;
                float y2 = (float)cy + (float)Math.sin(rad) * currentRadius;
                this.drawLine(pose, x1, y1, x2, y2, 255, 255, 255, (int)(40.0f * ease));
            }
        }

        this.drawRingOutline(pose, cx, cy, currentRadius, 255, 255, 255, (int)(60.0f * ease), 1.5f);
        this.drawRingOutline(pose, cx, cy, currentInner, 255, 255, 255, (int)(40.0f * ease), 1.0f);
        this.drawFilledCircle(pose, cx, cy, 6.0f * ease, 200, 200, 200, (int)(120.0f * ease));
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void drawLabels(GuiGraphics graphics, int cx, int cy, float ease, List<ModNetworking.WheelTechniqueEntry> techniques) {
        if (techniques.isEmpty()) return;
        int count = techniques.size();
        float sliceAngle = 360.0f / (float)count;
        float iconR = 65.0f * ease;
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < count; ++i) {
            ModNetworking.WheelTechniqueEntry tech = techniques.get(i);
            boolean cooldownBlocked = this.isCooldownBlocked(tech);
            boolean isHovered = i == this.hoveredIndex;
            boolean isSelected = i == this.selectedIndex;
            float midAngle = -90.0f + (float)i * sliceAngle + sliceAngle / 2.0f;
            float rad = (float)Math.toRadians(midAngle);
            float hoverOffset = isHovered ? 6.0f : 0.0f;
            float tx = (float)cx + (float)Math.cos(rad) * (iconR + hoverOffset);
            float ty = (float)cy + (float)Math.sin(rad) * (iconR + hoverOffset);

            String name = tech.displayName();
            if (name.length() > 14) name = name.substring(0, 12) + "..";
            int textColor = cooldownBlocked ? 0xFF8B8B8B : (isHovered ? 0xFFFFFFFF : (isSelected ? (tech.color() | 0xFF000000) : 0xFFAAC8D4));
            int textAlpha = (int)((float)((textColor >> 24) & 0xFF) * ease);
            textColor = (textAlpha << 24) | (textColor & 0xFFFFFF);

            PoseStack pose = graphics.pose();
            pose.pushPose();
            float scale = isHovered ? 1.0f : 0.75f;
            pose.translate(tx, ty, 0.0f);
            pose.scale(scale, scale, 1.0f);
            int textWidth = font.width(name);
            graphics.drawString(font, name, -textWidth / 2, -4, textColor, true);

            if (cooldownBlocked) {
                int cd = this.getEntryCooldownRemaining(tech);
                float secs = (float)cd / 20.0f;
                String timerStr = secs >= 10.0f ? String.format("%.0fs", secs) : String.format("%.1fs", secs);
                int timerWidth = font.width(timerStr);
                int timerAlpha = (int)(220.0f * ease);
                int timerColor = (timerAlpha << 24) | (secs < 1.5f ? 0xFFFF6B6B : 0xFFFF9933);
                graphics.drawString(font, timerStr, -timerWidth / 2, 7, timerColor, true);
            }

            if (isHovered) {
                if (this.isSpiritEntry(tech)) {
                    String tag = "\u2726 SUMMON";
                    int tagWidth = font.width(tag);
                    graphics.drawString(font, tag, -tagWidth / 2, 8, (textAlpha << 24) | 0xFFC850C0, true);
                } else {
                    String cost = "CE: " + (int)tech.finalCost();
                    int costWidth = font.width(cost);
                    graphics.drawString(font, cost, -costWidth / 2, 8, (textAlpha << 24) | 0xFFAAAAAA, true);
                    String typeTag = tech.passive() && tech.physical() ? "PASSIVE+PHYSICAL" : (tech.passive() ? "PASSIVE" : (tech.physical() ? "PHYSICAL" : "TECHNIQUE"));
                    int typeWidth = font.width(typeTag);
                    graphics.drawString(font, typeTag, -typeWidth / 2, 18, (textAlpha << 24) | 0xFFFFD580, true);
                }
            }
            pose.popPose();
        }
    }

    private void drawCenterInfo(GuiGraphics graphics, int cx, int cy, float ease, List<ModNetworking.WheelTechniqueEntry> techniques) {
        String title;
        if (this.isMultiPage()) {
            String[] pageTitles = new String[]{"Techniques", "Cursed Spirits (Lower)", "Cursed Spirits (Upper)"};
            title = this.currentPage < pageTitles.length ? pageTitles[this.currentPage] : "Cursed Spirits (Page " + (this.currentPage + 1) + ")";
            title = "\u272a " + title + " \u272a";
        } else {
            title = "\u272a Select Technique \u272a";
        }
        Font font = Minecraft.getInstance().font;
        int titleWidth = font.width(title);
        int titleAlpha = (int)(220.0f * ease);
        graphics.drawString(font, title, cx - titleWidth / 2, (int)((float)cy - 90.0f * ease - 30.0f), (titleAlpha << 24) | 0xFFFFCC00, true);

        if (this.hoveredIndex >= 0 && this.hoveredIndex < techniques.size()) {
            ModNetworking.WheelTechniqueEntry hoveredTech = techniques.get(this.hoveredIndex);
            String fullName = hoveredTech.displayName();
            int nameWidth = font.width(fullName);
            int alpha = (int)(255.0f * ease);
            graphics.drawString(font, fullName, cx - nameWidth / 2, (int)((float)cy + 90.0f * ease + 16.0f), (alpha << 24) | 0xFFFFFFFF, true);

            if (this.isSpiritEntry(hoveredTech)) {
                String summonStr = "Click to summon this cursed spirit";
                int sWidth = font.width(summonStr);
                int sAlpha = (int)(180.0f * ease);
                graphics.drawString(font, summonStr, cx - sWidth / 2, (int)((float)cy + 90.0f * ease + 30.0f), (sAlpha << 24) | 0xFFAADDFF, true);
            } else {
                String costStr = "Curse Energy Cost: " + (int)hoveredTech.finalCost() + " (Base " + (int)hoveredTech.baseCost() + ")";
                int costWidth = font.width(costStr);
                int costAlpha = (int)(200.0f * ease);
                graphics.drawString(font, costStr, cx - costWidth / 2, (int)((float)cy + 90.0f * ease + 30.0f), (costAlpha << 24) | 0xFFAADDFF, true);
                String effectStr = hoveredTech.passive() && hoveredTech.physical() ? "Effect: Passive + Physical"
                    : (hoveredTech.passive() ? "Effect: Passive" : (hoveredTech.physical() ? "Effect: Physical" : "Effect: Technique"));
                int effectWidth = font.width(effectStr);
                graphics.drawString(font, effectStr, cx - effectWidth / 2, (int)((float)cy + 90.0f * ease + 44.0f), (costAlpha << 24) | 0xFFFFD580, true);
            }
        }
    }

    private void drawPageIndicator(GuiGraphics graphics, int cx, int cy, float ease) {
        int totalPages = this.pages.size();
        int dotSpacing = 12;
        int totalWidth = (totalPages - 1) * dotSpacing;
        int startX = cx - totalWidth / 2;
        int baseY = (int)((float)cy + 90.0f * ease + 62.0f);
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < totalPages; ++i) {
            int dotX = startX + i * dotSpacing;
            boolean active = i == this.currentPage;
            int color = active ? (int)(255.0f * ease) << 24 | 0xFFFFCC00 : (int)(120.0f * ease) << 24 | 0xFF888888;
            int radius = active ? 4 : 2;
            graphics.fill(dotX - radius, baseY - radius, dotX + radius, baseY + radius, color);
        }
        int scrollAlpha = (int)(150.0f * ease);
        String scrollHint = "\u25c0 Scroll \u25b6";
        int scrollWidth = font.width(scrollHint);
        graphics.drawString(font, scrollHint, cx - scrollWidth / 2, baseY + 8, (scrollAlpha << 24) | 0xFFAAAAAA, true);
    }

    private int getEntryCooldownRemaining(ModNetworking.WheelTechniqueEntry tech) {
        if (this.isSpiritEntry(tech)) return 0;
        Minecraft mc = Minecraft.getInstance();
        long elapsed = mc.level != null ? mc.level.getGameTime() - this.openTick : 0L;
        return Math.max(0, tech.cooldownRemainingTicks() - (int)elapsed);
    }

    private int getEntryCooldownMax(ModNetworking.WheelTechniqueEntry tech) {
        if (this.isSpiritEntry(tech)) return 0;
        return tech.cooldownMaxTicks();
    }

    private boolean isCooldownBlocked(ModNetworking.WheelTechniqueEntry tech) {
        return this.getEntryCooldownRemaining(tech) > 0;
    }

    // ── Primitive drawing helpers ──────────────────────────────────────────────

    private void drawArcSlice(PoseStack pose, float cx, float cy, float innerR, float outerR, float startDeg, float endDeg, int r, int g, int b, int a) {
        int segments = 24;
        float step = (endDeg - startDeg) / (float)segments;
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();
        for (int i = 0; i <= segments; ++i) {
            float angle = (float)Math.toRadians(startDeg + step * (float)i);
            float cosA = (float)Math.cos(angle);
            float sinA = (float)Math.sin(angle);
            buf.vertex(matrix, cx + cosA * outerR, cy + sinA * outerR, 0.0f).color(r, g, b, a).endVertex();
            buf.vertex(matrix, cx + cosA * innerR, cy + sinA * innerR, 0.0f).color(r, g, b, (int)((float)a * 0.6f)).endVertex();
        }
        t.end();
    }

    private void drawArcOutline(PoseStack pose, float cx, float cy, float innerR, float outerR, float startDeg, float endDeg, int r, int g, int b, int a, float width) {
        int segments = 24;
        float step = (endDeg - startDeg) / (float)segments;
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(width);
        buf.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();
        for (int i = 0; i <= segments; ++i) {
            float angle = (float)Math.toRadians(startDeg + step * (float)i);
            buf.vertex(matrix, cx + (float)Math.cos(angle) * outerR, cy + (float)Math.sin(angle) * outerR, 0.0f).color(r, g, b, a).endVertex();
        }
        t.end();
    }

    private void drawRingOutline(PoseStack pose, float cx, float cy, float radius, int r, int g, int b, int a, float width) {
        int segments = 48;
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(width);
        buf.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();
        for (int i = 0; i <= segments; ++i) {
            float angle = (float)Math.toRadians(360.0 * (double)i / (double)segments);
            buf.vertex(matrix, cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius, 0.0f).color(r, g, b, a).endVertex();
        }
        t.end();
    }

    private void drawFilledCircle(PoseStack pose, float cx, float cy, float radius, int r, int g, int b, int a) {
        int segments = 24;
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();
        buf.vertex(matrix, cx, cy, 0.0f).color(r, g, b, a).endVertex();
        for (int i = 0; i <= segments; ++i) {
            float angle = (float)Math.toRadians(360.0 * (double)i / (double)segments);
            buf.vertex(matrix, cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius, 0.0f).color(r, g, b, (int)((float)a * 0.5f)).endVertex();
        }
        t.end();
    }

    private void drawLine(PoseStack pose, float x1, float y1, float x2, float y2, int r, int g, int b, int a) {
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(1.0f);
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();
        buf.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a).endVertex();
        t.end();
    }

    // ── Confirm flash overlay (from NEW addon) ────────────────────────────────

    private void drawConfirmFlash(GuiGraphics graphics, int cx, int cy) {
        if (this.confirmFlash <= 0f) return;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = pose.last().pose();
        int alpha = (int)(this.confirmFlash * 100 * this.animProgress);
        buf.vertex(mat, 0, 0, 0).color(255, 240, 200, alpha).endVertex();
        buf.vertex(mat, this.width, 0, 0).color(255, 240, 200, alpha).endVertex();
        buf.vertex(mat, this.width, this.height, 0).color(255, 240, 200, alpha).endVertex();
        buf.vertex(mat, 0, this.height, 0).color(255, 240, 200, alpha).endVertex();
        t.end();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    // ── Confirm particles (from NEW addon) ─────────────────────────────────────

    private void spawnConfirmParticles(ModNetworking.WheelTechniqueEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int color = entry.color();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        for (int i = 0; i < 24; i++) {
            float angle = (float)(i * Math.PI * 2.0 / 24.0);
            float speed = 0.04f + rng.nextFloat() * 0.06f;
            float px = (float)mc.player.getX() + (float)Math.cos(angle) * 0.5f;
            float py = (float)mc.player.getY() + mc.player.getEyeHeight() * 0.5f;
            float pz = (float)mc.player.getZ() + (float)Math.sin(angle) * 0.5f;
            mc.level.addParticle(new DustParticleOptions(new org.joml.Vector3f(r, g, b), 1.0f),
                px, py, pz,
                (float)Math.cos(angle) * speed, rng.nextFloat() * 0.04f, (float)Math.sin(angle) * speed);
        }
        for (int i = 0; i < 8; i++) {
            float px = (float)mc.player.getX() + (rng.nextFloat() - 0.5f) * 0.6f;
            float py = (float)mc.player.getY() + mc.player.getEyeHeight() * 0.5f;
            float pz = (float)mc.player.getZ() + (rng.nextFloat() - 0.5f) * 0.6f;
            mc.level.addParticle(ParticleTypes.ENCHANT, px, py, pz, 0, 0.04f, 0);
        }
        for (int i = 0; i < 6; i++) {
            float px = (float)mc.player.getX() + (rng.nextFloat() - 0.5f) * 0.3f;
            float py = (float)mc.player.getY() + mc.player.getEyeHeight() * 0.5f + rng.nextFloat() * 0.3f;
            float pz = (float)mc.player.getZ() + (rng.nextFloat() - 0.5f) * 0.3f;
            mc.level.addParticle(ParticleTypes.END_ROD, px, py, pz, rng.nextGaussian() * 0.02, 0.02, rng.nextGaussian() * 0.02);
        }
    }

    // ── Confirm sound (from NEW addon) ─────────────────────────────────────────

    private void playConfirmSound(ModNetworking.WheelTechniqueEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (this.isSpiritEntry(entry)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, 0.15f, 1.25f));
        } else if (entry.physical()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHAIN_PLACE, 0.18f, 1.45f));
        } else if (entry.passive()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.15f, 1.55f));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 0.15f, 1.30f));
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.18f, 1.50f));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.12f, 1.70f));
    }

    // ── Easing ─────────────────────────────────────────────────────────────────

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float)Math.pow(t - 1.0f, 3.0) + c1 * (float)Math.pow(t - 1.0f, 2.0);
    }

    // ── Confirmation ──────────────────────────────────────────────────────────

    public void confirmSelection() {
        if (this.selectionConfirmed) return;
        this.selectionConfirmed = true;

        List<ModNetworking.WheelTechniqueEntry> techniques = this.currentTechniques();
        if (this.hoveredIndex < 0 || this.hoveredIndex >= techniques.size()) {
            this.onClose();
            return;
        }
        ModNetworking.WheelTechniqueEntry tech = techniques.get(this.hoveredIndex);
        if (this.isCooldownBlocked(tech)) {
            this.onClose();
            return;
        }

        // ── NEW addon effects ─────────────────────────────────────────────────
        this.confirmFlash = 1.0f;
        this.spawnConfirmParticles(tech);
        this.playConfirmSound(tech);
        // ──────────────────────────────────────────────────────────────────────

        if (this.isSpiritEntry(tech)) {
            int spiritSlot = (int)Math.round(tech.selectId()) - 100;
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.SelectSpiritPacket(spiritSlot));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY).ifPresent(cap -> {
                    cap.PlayerSelectCurseTechnique = 12.0;
                    cap.PlayerSelectCurseTechniqueName = tech.displayName();
                });
            }
        } else {
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.SelectTechniquePacket(tech.selectId()));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY).ifPresent(cap -> {
                    cap.PlayerSelectCurseTechnique = tech.selectId();
                    cap.PlayerSelectCurseTechniqueName = tech.displayName();
                    cap.PlayerSelectCurseTechniqueCost = tech.finalCost();
                    cap.PlayerSelectCurseTechniqueCostOrgin = tech.baseCost();
                });
            }
        }
        this.onClose();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.isWheelKey(keyCode, scanCode) && !this.selectionConfirmed) {
            this.closing = true;
            this.confirmSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.selectionConfirmed && !this.closing) {
            this.confirmSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
