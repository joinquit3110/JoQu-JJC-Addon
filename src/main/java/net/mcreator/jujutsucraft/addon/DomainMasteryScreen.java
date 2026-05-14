package net.mcreator.jujutsucraft.addon;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.mcreator.jujutsucraft.addon.ClientPacketHandler;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.clash.power.PowerCalculator;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Full custom client GUI for domain mastery management. The screen renders animated panels, XP and form widgets, eight property cards, negative modify controls, and mutation locks during combat or active domains.
 */
// ===== SCREEN LIFECYCLE =====
public class DomainMasteryScreen
extends Screen {
    // Base panel width in screen pixels before UI scaling is applied.
    private static final int PANEL_W = 640;
    // Base panel height in screen pixels before UI scaling is applied.
    private static final int PANEL_H = 588;
    // Base header height used by the animated mastery panel layout.
    private static final int HEADER_H = 154;
    // Base footer height reserved for reset and close buttons.
    private static final int FOOTER_H = 56;
    // Base height of a single property card row.
    private static final int PROP_ROW = 82;
    // Base width of a single property column.
    private static final int PROP_COL_W = 143;
    // Base horizontal gap between property columns.
    private static final int PROP_GAP_X = 16;
    // Base vertical gap between property rows.
    private static final int PROP_GAP_Y = 12;
    // Scaled panel width calculated for the current client resolution.
    private int drawW = 640;
    // Scaled panel height calculated for the current client resolution.
    private int drawH = 682;
    // Scaled header height used during rendering.
    private int drawHeaderH = 154;
    // Scaled footer height used during rendering.
    private int drawFooterH = 56;
    // Scaled property row height used during rendering.
    private int drawPropRow = 82;
    // Scaled property column width used during rendering.
    private int drawPropColW = 143;
    // Scaled horizontal property gap used during rendering.
    private int drawPropGapX = 16;
    // Scaled vertical property gap used during rendering.
    private int drawPropGapY = 12;
    // Packed color used for the main mastery panel background.
    private static final int PANEL_BG = 463645;
    // Packed color used for the mastery panel border.
    private static final int PANEL_BORDER = 3718648;
    // Packed color used for the header background block.
    private static final int HEADER_BG = 728106;
    // Accent color used for the cursed energy drain property card.
    private static final int C_CE_DRAIN = 3718648;
    // Accent color used for the Black Flash chance property card.
    private static final int C_BF_CHANCE = 16096779;
    // Accent color used for the reverse cursed technique healing property card.
    private static final int C_RCT_HEAL = 2278750;
    // Accent color used for the blind property card.
    private static final int C_BLIND = 16007006;
    // Accent color used for the slow property card.
    private static final int C_SLOW = 6333946;
    // Accent color used for the duration property card.
    private static final int C_DURATION = 16486972;
    // Accent color used for the radius property card.
    private static final int C_RADIUS = 1357990;
    // Accent color used for the incomplete form button.
    private static final int C_FORM_INC = 4674921;
    // Accent color used for the closed form button.
    private static final int C_FORM_CLS = 165063;
    // Accent color used for the open form button.
    private static final int C_FORM_OPN = 1483594;
    // Accent color used for locked form states.
    private static final int C_FORM_LCK = 2042167;
    // Primary bright text color used throughout the screen.
    private static final int TEXT_WHITE = 15857145;
    // Secondary text color used for neutral details.
    private static final int TEXT_MID = 9741240;
    // Low-contrast text color used for subdued labels.
    private static final int TEXT_DIM = 4674921;
    // Highlight text color used for XP and mastery emphasis.
    private static final int TEXT_GOLD = 16569165;
    // Muted text color used when controls are locked.
    private static final int TEXT_LOCK = 4937059;
    // Base color for the reset button.
    private static final int BTN_RESET_BG = 1013358;
    // Hover color for the reset button.
    private static final int BTN_RESET_HV = 1357990;
    // Base color for the close button.
    private static final int BTN_CLOSE_BG = 1976635;
    // Hover color for the close button.
    private static final int BTN_CLOSE_HV = 3359061;
    // Base color for plus buttons that spend points.
    private static final int BTN_PLUS_BG = 366185;
    // Hover color for plus buttons that spend points.
    private static final int BTN_PLUS_HV = 1096065;
    // Base color for minus buttons that refund or deepen negatives.
    private static final int BTN_MINUS_BG = 14427686;
    // Hover color for minus buttons that refund or deepen negatives.
    private static final int BTN_MINUS_HV = 0xEF4444;
    // Left edge of the scaled mastery panel.
    private int panelX;
    // Top edge of the scaled mastery panel.
    private int panelY;
    // Timestamp captured when the screen opens so the intro animation can be timed.
    private long openTimeMs;
    // Global UI scale derived from the current window size.
    private float fontScale = 1.0f;
    // Normalized open animation progress used for fade and panel transitions.
    private float openAnim = 0.0f;
    // Tick-like counter used to animate subtle UI pulsing.
    private float pulseTick = 0.0f;
    // Timestamp of the last pulse counter advance.
    private long lastPulse = 0L;
    // Currently hovered property index, or -1 when no property control is highlighted.
    private int hoveredPropIdx = -1;
    // Locally cached selected domain form shown by the form buttons.
    private int selectedForm = 0;
    // Whether the player currently has access to the closed form.
    private boolean closedUnlocked = false;
    // Whether the player currently has access to the open form.
    private boolean openUnlocked = false;
    // Tracks whether any form button is currently hovered.
    private boolean hoveredFormBtn = false;
    // Index of the currently hovered form button, or -1 when none is hovered.
    private int hoveredFormIdx = -1;
    // Whether the reset button is currently hovered.
    private boolean hoveredReset = false;
    // Whether the close button is currently hovered.
    private boolean hoveredClose = false;
    // Per-property hover flags for the plus buttons.
    private boolean[] hoveredPlus = new boolean[DomainMasteryProperties.values().length];
    // Per-property hover flags for the minus buttons.
    private boolean[] hoveredMinus = new boolean[DomainMasteryProperties.values().length];
    // Previously hovered control id so hover sounds only fire on transitions.
    private int lastHoveredControl = -1;
    // Whether domain mastery changes are currently blocked by combat or active domains.
    private boolean masteryMutationLocked = false;
    // Whether the cursor is currently over a control that is locked by mutation restrictions.
    private boolean hoveredMutationControl = false;
    // Human-readable explanation shown when mastery mutation is blocked.
    private String masteryMutationLockReason = "";
    // Random instance used for small decorative animation variations.
    private final Random rng = new Random();
    // Guard flag that prevents the opening sound from replaying unnecessarily.
    private boolean playedOpenSound = false;

    /**
     * Creates a new domain mastery screen instance and initializes its addon state.
     */
    public DomainMasteryScreen() {
        super((Component)Component.literal((String)"\u2726 DOMAIN MASTERY \u2726"));
        this.openTimeMs = System.currentTimeMillis();
    }

    /**
     * Performs init for this addon component.
     */
    protected void init() {
        super.init();
        int availW = (int)((float)this.width * 0.9f);
        int availH = (int)((float)this.height * 0.9f);
        this.fontScale = Math.min(1.0f, Math.min((float)availW / 640.0f, (float)availH / 682.0f));
        this.drawW = (int)(640.0f * this.fontScale);
        this.drawH = (int)(682.0f * this.fontScale);
        this.drawHeaderH = (int)(154.0f * this.fontScale);
        this.drawFooterH = (int)(56.0f * this.fontScale);
        this.drawPropRow = (int)(82.0f * this.fontScale);
        this.drawPropGapX = Math.max(6, (int)(16.0f * this.fontScale));
        this.drawPropGapY = Math.max(4, (int)(12.0f * this.fontScale));
        this.drawPropColW = (this.getPropertyAreaWidth() - this.drawPropGapX) / 2;
        this.panelX = (this.width - this.drawW) / 2;
        this.panelY = (this.height - this.drawH) / 2;
        this.refreshFormState();
        this.refreshMutationLockState();
        this.playUiOpenSound();
    }

    // ===== STATE REFRESH =====
    private void refreshFormState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            try {
                this.selectedForm = mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(DomainMasteryData::getDomainTypeSelected).orElse(0);
                this.closedUnlocked = mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(DomainMasteryData::isClosedFormUnlocked).orElse(false);
                this.openUnlocked = mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(DomainMasteryData::isOpenFormUnlocked).orElse(false);
            }
            catch (Exception e) {
                this.selectedForm = 0;
                this.openUnlocked = false;
                this.closedUnlocked = false;
            }
        }
    }

    /**
     * Determines whether mastery changes should be blocked because the player is in combat or already using a domain.
     */
    private void refreshMutationLockState() {
        boolean inCombat;
        Minecraft mc = Minecraft.getInstance();
        this.masteryMutationLocked = false;
        this.masteryMutationLockReason = "";
        if (mc.player == null) {
            return;
        }
        // Mutation locks intentionally mirror gameplay restrictions so players cannot reconfigure mastery in combat or while a domain is active.
        boolean hasActiveDomain = mc.player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) || mc.player.getPersistentData().getDouble("DomainExpansion") > 0.0;
        boolean bl = inCombat = mc.player.hasEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get()) || ClientPacketHandler.ClientCooldownCache.getRemaining(true) > 0;
        if (hasActiveDomain) {
            this.masteryMutationLocked = true;
            this.masteryMutationLockReason = "Cannot change Domain Mastery while Domain Expansion is active";
        } else if (inCombat) {
            this.masteryMutationLocked = true;
            this.masteryMutationLockReason = "Cannot change Domain Mastery while in combat";
        }
    }

    // ===== INPUT AND TICK FLOW =====
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Routes left-click input across form buttons, property controls, reset, and close actions.
     * @param mouseX mouse x used by this method.
     * @param mouseY mouse y used by this method.
     * @param button button used by this method.
     * @return true when mouse clicked succeeds; otherwise false.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i;
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float mx = (float)mouseX;
        float my = (float)mouseY;
        // Form buttons are processed before property cards so a direct form click always wins over overlapping hover state.
        for (i = 0; i < 3; ++i) {
            if (!this.isInRect(mx, my, this.getFormBtnBounds(i))) continue;
            if (this.masteryMutationLocked) {
                this.playUiLockSound();
                this.sendMutationLockMessage();
                return true;
            }
            if (i == 1 && !this.closedUnlocked) {
                this.playUiLockSound();
                this.sendFormLockMessage(i);
                return true;
            }
            if (i == 2 && !this.openUnlocked) {
                this.playUiLockSound();
                this.sendFormLockMessage(i);
                return true;
            }
            this.setForm(i);
            this.selectedForm = i;
            this.playFormSelectSound(i);
            return true;
        }
        // Each property card exposes both normal upgrades and the negative modify flow, so the click handler checks every property control pair each frame.
        for (i = 0; i < DomainMasteryProperties.values().length; ++i) {
            if (this.isInRect(mx, my, this.getPlusBtnBounds(i))) {
                if (this.masteryMutationLocked) {
                    this.playUiLockSound();
                    this.sendMutationLockMessage();
                } else if (this.isNegativeProperty(i) && this.canNegativeIncreaseProperty(i)) {
                    this.negativeIncrease(i);
                    this.playUpgradeSound();
                } else if (this.canUpgradeProperty(i)) {
                    this.upgradeProperty(i);
                    this.playUpgradeSound();
                } else {
                    this.playUiLockSound();
                }
                return true;
            }
            if (!this.isInRect(mx, my, this.getMinusBtnBounds(i))) continue;
            if (this.masteryMutationLocked) {
                this.playUiLockSound();
                this.sendMutationLockMessage();
            } else if (this.canRefundProperty(i)) {
                this.refundProperty(i);
                this.playRefundSound();
            } else if (this.canNegativeDecreaseProperty(i)) {
                this.negativeDecrease(i);
                this.playRefundSound();
            } else {
                this.playUiLockSound();
            }
            return true;
        }
        if (this.isInRect(mx, my, this.getResetBtnBounds())) {
            if (this.masteryMutationLocked) {
                this.playUiLockSound();
                this.sendMutationLockMessage();
            } else {
                this.resetAll();
                this.playResetSound();
            }
            return true;
        }
        if (this.isInRect(mx, my, this.getCloseBtnBounds())) {
            this.playCloseSound();
            this.onClose();
            return true;
        }
        return super.mouseClicked((double)mx, (double)my, button);
    }

    /**
     * Performs mouse dragged for this addon component.
     * @param mouseX mouse x used by this method.
     * @param mouseY mouse y used by this method.
     * @param button button used by this method.
     * @param dragX drag x used by this method.
     * @param dragY drag y used by this method.
     * @return true when mouse dragged succeeds; otherwise false.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Advances this state by one tick.
     */
    public void tick() {
        super.tick();
        this.refreshFormState();
        this.refreshMutationLockState();
        long now = System.currentTimeMillis();
        float elapsed = (float)(now - this.openTimeMs) / 300.0f;
        this.openAnim = Math.min(1.0f, elapsed);
        if (now - this.lastPulse > 80L) {
            this.pulseTick += 1.0f;
            this.lastPulse = now;
        }
    }

    // ===== PRIMARY RENDER FLOW =====
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int i;
        // Fade the full-screen backdrop in with the panel animation so the custom screen feels anchored instead of popping onto the client abruptly.
        int bgAlpha = (int)(170.0f * this.openAnim);
        graphics.fill(0, 0, this.width, this.height, bgAlpha << 24);
        float mx = mouseX;
        float my = mouseY;
        this.hoveredFormBtn = false;
        this.hoveredFormIdx = -1;
        this.hoveredMutationControl = false;
        for (i = 0; i < 3; ++i) {
            if (!this.isInRect(mx, my, this.getFormBtnBounds(i))) continue;
            this.hoveredFormBtn = true;
            this.hoveredFormIdx = i;
            break;
        }
        this.hoveredReset = this.isInRect(mx, my, this.getResetBtnBounds());
        this.hoveredClose = this.isInRect(mx, my, this.getCloseBtnBounds());
        // Locked hover tracking allows the screen to show an explanation tooltip even when the underlying action cannot be executed.
        this.hoveredMutationControl = this.masteryMutationLocked && (this.hoveredFormIdx >= 0 || this.hoveredReset);
        this.hoveredPropIdx = -1;
        for (i = 0; i < DomainMasteryProperties.values().length; ++i) {
            this.hoveredPlus[i] = this.isInRect(mx, my, this.getPlusBtnBounds(i));
            this.hoveredMinus[i] = this.isInRect(mx, my, this.getMinusBtnBounds(i));
            if (!this.hoveredPlus[i] && !this.hoveredMinus[i]) continue;
            this.hoveredPropIdx = i;
            if (!this.masteryMutationLocked) continue;
            this.hoveredMutationControl = true;
        }
        int hoveredControl = this.getHoveredControlId();
        if (hoveredControl != this.lastHoveredControl) {
            if (hoveredControl >= 0) {
                this.playUiHoverSound(hoveredControl);
            }
            this.lastHoveredControl = hoveredControl;
        }
        this.drawPanelBackground(graphics, mouseX, mouseY);
        this.drawHeader(graphics, mouseX, mouseY);
        this.drawPropertiesGrid(graphics, mouseX, mouseY);
        this.drawFooter(graphics);
        if (this.masteryMutationLocked && this.hoveredMutationControl) {
            this.drawMutationLockTooltip(graphics, mouseX, mouseY);
        } else if (this.hoveredPropIdx >= 0) {
            this.drawPropertyTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * Draws panel background as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param mx screen or world coordinate used by this calculation.
     * @param my screen or world coordinate used by this calculation.
     */
    private void drawPanelBackground(GuiGraphics graphics, int mx, int my) {
        float pulse = (float)(Math.sin((double)this.pulseTick * 0.5) * 0.5 + 0.5);
        int a = (int)(this.openAnim * 255.0f);
        int glowAlpha = (int)(this.openAnim * (20.0f + pulse * 30.0f));
        int glowCol = glowAlpha << 24 | 0x38BDF8;
        graphics.fill(this.panelX - 5, this.panelY - 5, this.panelX + this.drawW + 5, this.panelY + this.drawH + 5, glowCol);
        graphics.fill(this.panelX - 3, this.panelY - 3, this.panelX + this.drawW + 3, this.panelY + this.drawH + 3, glowCol >> 1);
        int bodyCol = a << 24 | 0x7131D;
        graphics.fill(this.panelX, this.panelY, this.panelX + this.drawW, this.panelY + this.drawH, bodyCol);
        int hdrBg = a << 24 | 0xB1C2A;
        graphics.fill(this.panelX, this.panelY, this.panelX + this.drawW, this.panelY + this.drawHeaderH, hdrBg);
        int ftrBg = a << 24 | 0xA0614;
        graphics.fill(this.panelX, this.panelY + this.drawH - this.drawFooterH, this.panelX + this.drawW, this.panelY + this.drawH, ftrBg);
        int borderCol = a << 24 | 0x38BDF8;
        graphics.fill(this.panelX, this.panelY, this.panelX + this.drawW, this.panelY + 2, borderCol);
        graphics.fill(this.panelX, this.panelY + this.drawH - 2, this.panelX + this.drawW, this.panelY + this.drawH, borderCol);
        graphics.fill(this.panelX, this.panelY, this.panelX + 2, this.panelY + this.drawH, borderCol);
        graphics.fill(this.panelX + this.drawW - 2, this.panelY, this.panelX + this.drawW, this.panelY + this.drawH, borderCol);
        graphics.fill(this.panelX, this.panelY + this.drawHeaderH - 1, this.panelX + this.drawW, this.panelY + this.drawHeaderH, borderCol);
        int ftrSep = (int)(this.openAnim * 64.0f) << 24 | 0x38BDF8;
        graphics.fill(this.panelX, this.panelY + this.drawH - this.drawFooterH, this.panelX + this.drawW, this.panelY + this.drawH - this.drawFooterH + 1, ftrSep);
        int gridSep = (int)(this.openAnim * 37.0f) << 24 | 0x38BDF8;
        int propStartX = this.getPropertyStartX();
        int propStartY = this.getPropertyStartY();
        int propGridW = this.drawPropColW * 2 + this.drawPropGapX;
        int propGridH = this.drawPropRow * 5 + this.drawPropGapY * 4;
        for (int i = 1; i <= 4; ++i) {
            int lineY = propStartY + i * this.drawPropRow + (i - 1) * this.drawPropGapY + this.drawPropGapY / 2;
            graphics.fill(propStartX, lineY, propStartX + propGridW, lineY + 1, gridSep);
        }
        int midX = propStartX + this.drawPropColW + this.drawPropGapX / 2;
        graphics.fill(midX, propStartY, midX + 1, propStartY + propGridH, gridSep);
    }

    // ===== HEADER AND FORM RENDERING =====
    private void drawHeader(GuiGraphics graphics, int mx, int my) {
        Object xpText;
        Font font = this.font;
        int padS = Math.max(2, (int)(this.fontScale * 14.0f));
        int padT = Math.max(4, (int)(this.fontScale * 12.0f));
        int gapS = Math.max(2, (int)(this.fontScale * 22.0f));
        int barS = Math.max(3, (int)(this.fontScale * 9.0f));
        String title = "DOMAIN MASTERY";
        float glow = (float)(Math.sin((double)this.pulseTick * 0.8) * 0.5 + 0.5);
        int titleShadow = (int)(this.openAnim * (100.0f + glow * 100.0f)) << 24 | 0x369A1;
        int titleCol = (int)(this.openAnim * 255.0f) << 24 | 0xE0F2FE;
        int titleX = this.panelX + (this.drawW - font.width(title)) / 2;
        int titleY = this.panelY + padT;
        this.drawLayeredItem(graphics, new ItemStack((ItemLike)Items.END_CRYSTAL), titleX - 22, titleY - 1, 180.0);
        this.drawLayeredItem(graphics, new ItemStack((ItemLike)Items.NETHER_STAR), titleX + font.width(title) + 6, titleY - 1, 180.0);
        this.drawForegroundText(graphics, font, title, titleX + 1, titleY + 1, titleShadow);
        this.drawForegroundText(graphics, font, title, titleX, titleY, titleCol);
        int level = this.getDomainMasteryLevel();
        this.drawLevelBadge(graphics, this.panelX + padS, this.panelY + Math.max(3, (int)(this.fontScale * 10.0f)), level);
        if (level >= 5) {
            xpText = "MAX XP";
        } else {
            int cur = (int)this.getDomainXP();
            int nxt = this.getXPToNext();
            xpText = cur + " / " + nxt + " XP";
        }
        int xpCol = (int)(this.openAnim * 255.0f) << 24 | (level >= 5 ? 16569165 : 9741240);
        graphics.drawString(font, (String)xpText, this.panelX + padS, this.panelY + Math.max(10, (int)(this.fontScale * 36.0f)), xpCol, false);
        this.drawXPBar(graphics, this.panelX + padS, this.panelY + Math.max(14, (int)(this.fontScale * 50.0f)), this.drawW - padS * 2, barS);
        int pp = this.getDomainPropertyPoints();
        String ppText = "\u2726 " + pp + " PP";
        int ppCol = (int)(this.openAnim * 255.0f) << 24 | (pp > 0 ? 3462041 : 4937059);
        graphics.drawString(font, ppText, this.panelX + this.drawW - font.width(ppText) - padS, this.panelY + Math.max(10, (int)(this.fontScale * 36.0f)), ppCol, false);
        this.drawFormButtons(graphics, mx, my);
        this.drawFormHint(graphics);
    }

    /**
     * Draws level badge as part of the addon presentation layer.
     * @param g render context used to draw the current frame.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param level level value used by this operation.
     */
    private void drawLevelBadge(GuiGraphics g, int x, int y, int level) {
        int bgCol;
        Font font = this.font;
        String text = "Lv." + level;
        int badgeW = font.width(text) + (int)(this.fontScale * 16.0f);
        int badgeH = Math.max(8, (int)(this.fontScale * 18.0f));
        int padX = Math.max(2, (int)(this.fontScale * 8.0f));
        int padY = Math.max(1, (int)(this.fontScale * 4.0f));
        int borderCol = switch (level) {
            case 5 -> {
                bgCol = -855846067;
                yield -208051;
            }
            case 4 -> {
                bgCol = -1434699027;
                yield -4160260;
            }
            case 3 -> {
                bgCol = -2006945327;
                yield -8286984;
            }
            case 2 -> {
                bgCol = 2000388852;
                yield -10443270;
            }
            case 1 -> {
                bgCol = 1712166016;
                yield -13315175;
            }
            default -> {
                bgCol = 1715160403;
                yield -9735552;
            }
        };
        int a = (int)(this.openAnim * 255.0f);
        int fillCol = a << 24 | bgCol & 0xFFFFFF;
        int bordCol = a << 24 | borderCol & 0xFFFFFF;
        g.fill(x, y, x + badgeW, y + badgeH, fillCol);
        g.fill(x, y, x + badgeW, y + 1, bordCol);
        g.fill(x, y + badgeH - 1, x + badgeW, y + badgeH, bordCol);
        g.fill(x, y, x + 1, y + badgeH, bordCol);
        g.fill(x + badgeW - 1, y, x + badgeW, y + badgeH, bordCol);
        g.drawString(font, text, x + padX, y + padY, -1, false);
    }

    /**
     * Draws xp bar as part of the addon presentation layer.
     * @param g render context used to draw the current frame.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param w screen or world coordinate used by this calculation.
     * @param h screen or world coordinate used by this calculation.
     */
    private void drawXPBar(GuiGraphics g, int x, int y, int w, int h) {
        Font font = this.font;
        float progress = (float)this.getXPProgress();
        float glow = (float)(Math.sin((double)this.pulseTick * 0.8) * 0.5 + 0.5);
        int a = (int)(this.openAnim * 255.0f);
        g.fill(x, y, x + w, y + h, -15720141);
        int fill = (int)((float)w * progress);
        if (fill > 0) {
            int topCol = a << 24 | 0x38BDF8;
            int botCol = a << 24 | 0x284C7;
            int tipCol = a << 24 | 0xE0F2FE;
            g.fill(x, y, x + fill, y + h / 2, topCol);
            g.fill(x, y + h / 2, x + fill, y + h, botCol);
            if (fill >= 2) {
                g.fill(x + fill - 2, y, x + fill, y + h, tipCol);
            }
        }
        int border = a << 24 | 0xF4C81;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
    }

    /**
     * Draws form hint as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     */
    private void drawFormHint(GuiGraphics graphics) {
        int color;
        String hint;
        Font font = this.font;
        if (this.masteryMutationLocked) {
            hint = this.masteryMutationLockReason;
            color = 0xEF4444;
        } else if (!this.closedUnlocked) {
            hint = "Only Incomplete is available before Domain Mastery Lv.1";
            color = 9741240;
        } else if (!this.openUnlocked) {
            hint = "Incomplete and Closed are available. Open needs Lv.5 + Open Domain achievement";
            color = 16096779;
        } else {
            hint = "All Domain forms are unlocked";
            color = 2278750;
        }
        int y = this.panelY + Math.max(20, (int)(this.fontScale * 122.0f));
        int x = this.panelX + (this.drawW - font.width(hint)) / 2;
        graphics.drawString(font, hint, x, y, (int)(this.openAnim * 255.0f) << 24 | color, false);
    }

    /**
     * Draws form buttons as part of the addon presentation layer.
     * @param g render context used to draw the current frame.
     * @param mx screen or world coordinate used by this calculation.
     * @param my screen or world coordinate used by this calculation.
     */
    private void drawFormButtons(GuiGraphics g, int mx, int my) {
        Font font = this.font;
        int[] formColors = new int[]{4674921, 165063, 1483594};
        String[] formNames = new String[]{"INCOMPLETE", "CLOSED", "OPEN"};
        int btnW = Math.max(50, (int)(this.fontScale * 130.0f));
        int btnH = Math.max(10, (int)(this.fontScale * 28.0f));
        int gap = Math.max(2, (int)(this.fontScale * 10.0f));
        int totalW = 3 * btnW + 2 * gap;
        int startX = this.panelX + (this.drawW - totalW) / 2;
        int btnY = this.panelY + Math.max(20, (int)(this.fontScale * 82.0f));
        int a = (int)(this.openAnim * 255.0f);
        for (int i = 0; i < 3; ++i) {
            int textAlpha;
            int borderAlpha;
            int bgAlpha;
            int baseColor;
            int bx = startX + i * (btnW + gap);
            boolean isSelected = i == this.selectedForm;
            boolean isHovered = i == this.hoveredFormIdx;
            boolean isLocked = i == 1 && !this.closedUnlocked || i == 2 && !this.openUnlocked;
            boolean isDisabled = isLocked || this.masteryMutationLocked;
            int n = baseColor = isDisabled ? 2042167 : formColors[i];
            if (isSelected) {
                bgAlpha = a;
                borderAlpha = a;
                textAlpha = a;
            } else if (isHovered && !isDisabled) {
                bgAlpha = (int)((float)a * 0.7f);
                borderAlpha = (int)((float)a * 0.9f);
                textAlpha = (int)((float)a * 0.9f);
            } else {
                bgAlpha = (int)((float)a * 0.35f);
                borderAlpha = (int)((float)a * 0.5f);
                textAlpha = (int)((float)a * 0.6f);
            }
            int bgCol = bgAlpha << 24 | baseColor & 0xFFFFFF;
            int borderCol = borderAlpha << 24 | baseColor & 0xFFFFFF;
            int textCol = textAlpha << 24 | (isSelected && !isDisabled ? -1 : (isDisabled ? -9735552 : -1906448));
            g.fill(bx, btnY, bx + btnW, btnY + btnH, bgCol);
            if (isSelected) {
                int hlCol = (int)((float)a * 0.2f) << 24 | 0xFFFFFF;
                g.fill(bx + 2, btnY + 2, bx + btnW - 2, btnY + Math.max(2, (int)(this.fontScale * 4.0f)), hlCol);
            }
            g.fill(bx, btnY, bx + btnW, btnY + 1, borderCol);
            g.fill(bx, btnY + btnH - 1, bx + btnW, btnY + btnH, borderCol);
            g.fill(bx, btnY, bx + 1, btnY + btnH, borderCol);
            g.fill(bx + btnW - 1, btnY, bx + btnW, btnY + btnH, borderCol);
            String label = formNames[i];
            int iconSize = Math.max(11, Math.min(14, Math.round((float)btnH * 0.5f)));
            int iconGap = Math.max(4, (int)(this.fontScale * 6.0f));
            int labelMaxW = Math.max(24, btnW - iconSize - iconGap - Math.max(8, (int)(this.fontScale * 10.0f)));
            float labelScale = this.resolveFittedTextScale(font, label, labelMaxW, 1.0f, 0.62f);
            int labelH = this.getScaledTextHeight(font, labelScale);
            int contentH = Math.max(iconSize, labelH);
            int contentW = iconSize + iconGap + this.getScaledTextWidth(font, label, labelScale);
            int iconX = bx + Math.max(4, (btnW - contentW) / 2);
            int contentY = btnY + Math.max(1, (btnH - contentH) / 2);
            int iconY = contentY + (contentH - iconSize) / 2;
            this.drawLayeredItemScaled(g, this.getFormItem(i), iconX, iconY, iconSize, 160.0);
            int labelX = iconX + iconSize + iconGap;
            int labelY = contentY + (contentH - labelH) / 2;
            this.drawScaledForegroundText(g, font, label, labelX, labelY, textCol, labelScale);
            if (!isDisabled) continue;
            int lockCol = a << 24 | (this.masteryMutationLocked ? 16096779 : 0xEF4444);
            g.fill(bx + btnW - 6, btnY + 4, bx + btnW - 3, btnY + btnH - 4, lockCol);
        }
        int selColor = this.selectedForm == 2 && !this.openUnlocked ? 2042167 : formColors[this.selectedForm];
        int indicatorCol = (int)(this.openAnim * 255.0f) << 24 | selColor & 0xFFFFFF;
        int indicatorY = btnY + btnH + Math.max(1, (int)(this.fontScale * 3.0f));
        int selBtnW = btnW;
        int selStartX = startX + this.selectedForm * (selBtnW + gap);
        g.fill(selStartX, indicatorY, selStartX + selBtnW, indicatorY + Math.max(1, (int)(this.fontScale * 2.0f)), indicatorCol);
    }

    // ===== PROPERTY CARD RENDERING =====
    private void drawPropertiesGrid(GuiGraphics g, int mx, int my) {
        Font font = this.font;
        DomainMasteryProperties[] props = DomainMasteryProperties.values();
        int masteryLevel = this.getDomainMasteryLevel();
        int propStartX = this.getPropertyStartX();
        int propStartY = this.getPropertyStartY();
        int a = (int)(this.openAnim * 255.0f);
        this.hoveredPropIdx = -1;
        int pipH = Math.max(2, (int)(this.fontScale * 5.0f));
        int cardHeaderY = Math.max(6, (int)(this.fontScale * 7.0f));
        int iconPad = Math.max(5, (int)(this.fontScale * 8.0f));
        int stripW = Math.max(2, (int)(this.fontScale * 5.0f));
        int cardPad = Math.max(1, (int)(this.fontScale * 2.0f));
        int topStripH = Math.max(1, (int)(this.fontScale * 3.0f));
        int controlLaneW = Math.max(32, (int)(this.fontScale * 42.0f));
        int slotSize = 18;
        int textGap = Math.max(7, (int)(this.fontScale * 8.0f));
        int valueGap = Math.max(5, (int)(this.fontScale * 5.0f));
        int headerToBarGap = Math.max(7, (int)(this.fontScale * 8.0f));
        int barToValueGap = Math.max(7, (int)(this.fontScale * 8.0f));
        int valueBottomPad = Math.max(3, (int)(this.fontScale * 4.0f));
        for (int i = 0; i < props.length; ++i) {
            int statColor;
            Object statText;
            String lvlText;
            boolean isHovered;
            int row = i / 2;
            int col = i % 2;
            int px = propStartX + col * (this.drawPropColW + this.drawPropGapX);
            int py = propStartY + row * (this.drawPropRow + this.drawPropGapY);
            DomainMasteryProperties prop = props[i];
            int propLevel = this.getPropertyLevel(prop);
            int effectiveLevel = this.getEffectivePropertyLevel(prop);
            boolean negativeActive = this.isNegativeProperty(i);
            boolean locked = prop.isLocked(masteryLevel);
            int propColor = this.getPropertyColor(i);
            ItemStack propItem = this.getPropertyItem(i);
            boolean bl = isHovered = mx >= px && mx < px + this.drawPropColW && my >= py && my < py + this.drawPropRow;
            if (isHovered) {
                this.hoveredPropIdx = i;
            }
            int bgA = isHovered ? (locked ? 8 : 55) : (locked ? 5 : 30);
            int cardBg = (int)((float)bgA * this.openAnim) << 24 | propColor & 0xFFFFFF;
            g.fill(px + cardPad, py + cardPad, px + this.drawPropColW - cardPad, py + this.drawPropRow - cardPad, cardBg);
            int stripCol = a << 24 | propColor & 0xFFFFFF;
            g.fill(px + cardPad, py + cardPad, px + stripW, py + this.drawPropRow - cardPad, stripCol);
            if (isHovered && !locked) {
                int hlCol = (int)(this.openAnim * 204.0f) << 24 | propColor & 0xFFFFFF;
                g.fill(px + stripW, py + cardPad, px + this.drawPropColW - cardPad, py + topStripH, hlCol);
            }
            int[] minusBounds = this.getMinusBtnBounds(i);
            int[] plusBounds = this.getPlusBtnBounds(i);
            int headerTextY = py + cardHeaderY + 2;
            int headerRight = px + this.drawPropColW - controlLaneW - Math.max(5, (int)(this.fontScale * 6.0f));
            int actionTop = minusBounds[1];
            int slotX = px + iconPad - 1;
            int slotY = py + cardHeaderY + 1;
            g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, -1072687072);
            g.fill(slotX, slotY, slotX + slotSize, slotY + 1, -10784379);
            g.fill(slotX, slotY + slotSize - 1, slotX + slotSize, slotY + slotSize, -13945276);
            g.fill(slotX, slotY, slotX + 1, slotY + slotSize, -10784379);
            g.fill(slotX + slotSize - 1, slotY, slotX + slotSize, slotY + slotSize, -13945276);
            this.drawLayeredItem(g, propItem, slotX + 1, slotY + 1, 160.0);
            int contentLeft = slotX + slotSize + textGap;
            int contentRight = minusBounds[0] - Math.max(6, (int)(this.fontScale * 8.0f));
            int headerMaxW = Math.max(24, headerRight - contentLeft);
            int valueMaxW = Math.max(24, contentRight - (px + iconPad));
            String name = this.getShortName(i);
            int nameCol = locked ? (int)(this.openAnim * 96.0f) << 24 | 0x94A3B8 : (int)(this.openAnim * 255.0f) << 24 | 0xF1F5F9;
            String tagline = this.getPropertyTagline(i);
            float nameScale = this.resolveFittedTextScale(font, name, headerMaxW, 1.0f, 0.7f);
            int nameLineH = this.getScaledTextHeight(font, nameScale);
            float tagScale = this.resolveFittedTextScale(font, tagline, headerMaxW, 0.92f, 0.62f);
            int tagLineH = this.getScaledTextHeight(font, tagScale);
            int taglineY = headerTextY + nameLineH - 1;
            this.drawScaledForegroundText(g, font, name, contentLeft, headerTextY, nameCol, nameScale);
            this.drawScaledForegroundText(g, font, tagline, contentLeft, taglineY, (int)(this.openAnim * 176.0f) << 24 | 0x8BA3B8, tagScale);
            String string = lvlText = negativeActive ? String.valueOf(effectiveLevel) : propLevel + "/" + prop.getMaxLevel();
            int lvlCol = negativeActive ? (int)(this.openAnim * 255.0f) << 24 | 0xFF4444 : (propLevel > 0 ? (int)(this.openAnim * 255.0f) << 24 | 0xFCD34D : (int)(this.openAnim * 112.0f) << 24 | 0x94A3B8);
            this.drawRightAlignedFittedForegroundText(g, font, lvlText, headerRight, headerTextY, lvlCol, Math.max(22, controlLaneW - Math.max(6, (int)(this.fontScale * 6.0f))), 0.92f, 0.7f);
            if (locked) {
                statText = "Unlock Lv." + prop.unlockLevel();
                statColor = (int)(this.openAnim * 255.0f) << 24 | 0xEF4444;
            } else if (negativeActive) {
                statText = prop.formatNegativeValue(Math.abs(effectiveLevel));
                statColor = (int)(this.openAnim * 255.0f) << 24 | 0xFF4444;
            } else if (propLevel > 0) {
                statText = prop.formatLevelValue(propLevel);
                statColor = (int)(this.openAnim * 255.0f) << 24 | propColor;
            } else {
                statText = prop.formatLevelValue(0);
                statColor = (int)(this.openAnim * 80.0f) << 24 | 0x475569;
            }
            float statScale = this.resolveFittedTextScale(font, (String)statText, valueMaxW, 0.78f, 0.5f);
            int statLineH = this.getScaledTextHeight(font, statScale);
            int statTopLimit = taglineY + tagLineH + headerToBarGap;
            int pipX = px + iconPad;
            int barW = Math.max(18, contentRight - pipX);
            int maxPipY = actionTop - statLineH - valueBottomPad - barToValueGap - pipH;
            int pipY = Math.max(statTopLimit, maxPipY);
            int statY = Math.max(statTopLimit + pipH + barToValueGap, pipY + pipH + barToValueGap + Math.max(1, Math.round(this.fontScale)));
            g.fill(pipX, pipY, pipX + barW, pipY + pipH, -14805966);
            int maxLevel = prop.getMaxLevel();
            for (int p = 0; p < maxLevel; ++p) {
                int filled = p < propLevel ? a : (int)((float)a * 0.125f);
                int pipCol = filled << 24 | propColor & 0xFFFFFF;
                int segEnd = pipX + (p + 1) * barW / maxLevel;
                int segStart = pipX + p * barW / maxLevel;
                if (segEnd - segStart <= 2) {
                    g.fill(segStart, pipY, segEnd, pipY + pipH, pipCol);
                    continue;
                }
                g.fill(segStart + 1, pipY, segEnd - 1, pipY + pipH, pipCol);
            }
            this.drawScaledForegroundText(g, font, (String)statText, px + iconPad, statY, statColor, statScale);
            if (locked) {
                g.fill(px + cardPad, py + cardPad, px + this.drawPropColW - cardPad, py + this.drawPropRow - cardPad, (int)(this.openAnim * 24.0f) << 24 | 0);
            }
            if (locked) continue;
            boolean canUpgrade = !this.masteryMutationLocked && (negativeActive ? this.canNegativeIncreaseProperty(i) : this.canUpgradeProperty(i));
            boolean canRefund = !this.masteryMutationLocked && (this.canRefundProperty(i) || this.canNegativeDecreaseProperty(i));
            int mbx = minusBounds[0];
            int mby = minusBounds[1];
            int mbw = minusBounds[2];
            int mbh = minusBounds[3];
            if (canRefund) {
                int minusBg = this.hoveredMinus[i] ? a << 24 | 0xEF4444 : a << 24 | 0xDC2626;
                int minusTextCol = this.hoveredMinus[i] ? -1 : (int)((float)a * 0.9f) << 24 | 0xFFFFFF;
                this.drawPropertyControlButton(g, mbx, mby, mbw, mbh, minusBg, minusTextCol, false);
            } else {
                int disabledBg = (int)((float)a * 0.2f) << 24 | 0x333333;
                this.drawPropertyControlButton(g, mbx, mby, mbw, mbh, disabledBg, (int)((float)a * 0.3f) << 24 | 0x888888, false);
            }
            int pbx = plusBounds[0];
            int pby = plusBounds[1];
            int pbw = plusBounds[2];
            int pbh = plusBounds[3];
            if (canUpgrade) {
                int plusBg = this.hoveredPlus[i] ? a << 24 | 0x10B981 : a << 24 | 0x59669;
                int plusTextCol = this.hoveredPlus[i] ? -1 : (int)((float)a * 0.9f) << 24 | 0xFFFFFF;
                this.drawPropertyControlButton(g, pbx, pby, pbw, pbh, plusBg, plusTextCol, true);
                continue;
            }
            int disabledBg = (int)((float)a * 0.2f) << 24 | 0x333333;
            this.drawPropertyControlButton(g, pbx, pby, pbw, pbh, disabledBg, (int)((float)a * 0.3f) << 24 | 0x888888, true);
        }
    }

    /**
     * Draws the expanded tooltip for the currently hovered property card or control.
     * @param g render context used to draw the current frame.
     * @param mx screen or world coordinate used by this calculation.
     * @param my screen or world coordinate used by this calculation.
     */
    private void drawPropertyTooltip(GuiGraphics g, int mx, int my) {
        Font font = this.font;
        DomainMasteryProperties prop = DomainMasteryProperties.values()[this.hoveredPropIdx];
        int masteryLevel = this.getDomainMasteryLevel();
        boolean locked = prop.isLocked(masteryLevel);
        String name = Component.translatable((String)prop.getNameKey()).getString();
        StringBuilder descBuilder = new StringBuilder((String)(locked ? "\ud83d\udd12 Locked until Domain Mastery Lv." + prop.unlockLevel() : Component.translatable((String)prop.getDescKey()).getString()));
        if (!locked && prop.supportsNegativeModify()) {
            if (this.isNegativeProperty(this.hoveredPropIdx)) {
                descBuilder.append("\nNegative Modify active \u2022 current ").append(prop.formatNegativeValue(Math.abs(this.getEffectivePropertyLevel(prop)))).append("\nPress [+] to move back toward 0");
            } else if (this.getPropertyLevel(prop) > 0) {
                descBuilder.append("\nRefund to 0 before applying Negative Modify");
            } else if (this.getDomainMasteryLevel() < 5) {
                descBuilder.append("\nNegative Modify unlocks at Domain Mastery Lv.5");
            } else if (!this.canSetNegativeProperty(prop)) {
                descBuilder.append("\nAnother property is already negative");
            } else {
                descBuilder.append("\nPress [\u2212] to apply Negative Modify \u2022 each level grants +1 point");
            }
        }
        ClashPowerPreview preview = this.buildClashPowerPreview(prop);
        int maxDescWidth = Math.max(120, Math.min(220, this.width / 3));
        List<FormattedCharSequence> descLines = font.split((FormattedText)Component.literal((String)descBuilder.toString()), maxDescWidth);
        int maxW = font.width(name);
        if (preview != null) {
            maxW = Math.max(maxW, font.width(preview.prefix) + font.width(preview.delta) + font.width(preview.suffix));
        }
        for (FormattedCharSequence line : descLines) {
            maxW = Math.max(maxW, font.width(line));
        }
        int tw = maxW + 20;
        Objects.requireNonNull(font);
        int n = descLines.size();
        Objects.requireNonNull(font);
        int th = 9 + n * 9 + 16 + (preview == null ? 0 : 14);
        int tx = mx + 16;
        int ty = my - th - 4;
        if (tx + tw > this.width) {
            tx = mx - tw - 4;
        }
        if (ty < 0) {
            ty = my + 16;
        }
        int propColor = this.getPropertyColor(this.hoveredPropIdx);
        g.pose().pushPose();
        g.pose().translate(0.0, 0.0, 420.0);
        g.fill(tx, ty, tx + tw, ty + th, -268106220);
        g.fill(tx, ty, tx + tw, ty + 1, -13058568);
        g.fill(tx, ty + th - 1, tx + tw, ty + th, -13058568);
        g.fill(tx, ty, tx + 1, ty + th, -13058568);
        g.fill(tx + tw - 1, ty, tx + tw, ty + th, -13058568);
        g.drawString(font, name, tx + 10, ty + 7, 0xFF000000 | propColor, false);
        for (int i = 0; i < descLines.size(); ++i) {
            FormattedCharSequence formattedCharSequence = (FormattedCharSequence)descLines.get(i);
            Objects.requireNonNull(font);
            Objects.requireNonNull(font);
            g.drawString(font, formattedCharSequence, tx + 10, ty + 7 + 9 + i * 9, -7035976, false);
        }
        if (preview != null) {
            int py = ty + 7 + 9 + descLines.size() * 9 + 4;
            int x = tx + 10;
            g.drawString(font, preview.prefix, x, py, 0xFFE5E7EB, false);
            x += font.width(preview.prefix);
            g.drawString(font, preview.delta, x, py, preview.deltaColor, false);
            x += font.width(preview.delta);
            g.drawString(font, preview.suffix, x, py, 0xFFFCD34D, false);
        }
        g.pose().popPose();
    }

    private ClashPowerPreview buildClashPowerPreview(DomainMasteryProperties prop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || prop == null) {
            return null;
        }
        double current = this.estimateRawClashPower(null, 0);
        int direction = this.hoveredPlus[this.hoveredPropIdx] ? 1 : (this.hoveredMinus[this.hoveredPropIdx] ? -1 : 0);
        if (direction == 0) {
            return new ClashPowerPreview("Raw Clash Power ", String.format(java.util.Locale.ROOT, "%.1f", current), "", 0xFFFCD34D);
        }
        double next = this.estimateRawClashPower(prop, direction);
        double delta = next - current;
        String sign = delta >= 0.0 ? "+" : "";
        int color = delta >= 0.0 ? 0xFF34D399 : 0xFFF87171;
        return new ClashPowerPreview(
            String.format(java.util.Locale.ROOT, "Raw Clash Power %.1f ", current),
            String.format(java.util.Locale.ROOT, "%s%.1f", sign, delta),
            String.format(java.util.Locale.ROOT, " = %.1f", next),
            color
        );
    }

    private double estimateRawClashPower(DomainMasteryProperties changedProp, int deltaLevel) {
        int masteryLevel = this.getDomainMasteryLevel();
        int barrierPower = adjustedEffectiveLevel(DomainMasteryProperties.BARRIER_POWER, changedProp, deltaLevel);
        int barrierRef = adjustedEffectiveLevel(DomainMasteryProperties.BARRIER_REFINEMENT, changedProp, deltaLevel);
        int radiusLevel = adjustedEffectiveLevel(DomainMasteryProperties.RADIUS_BOOST, changedProp, deltaLevel);
        double radius = 22.0 * Math.max(0.25, 1.0 + radiusLevel * 0.12);
        double base = 10.0;
        double hp = 1.0;
        double duration = 1.0;
        double form = DomainForm.CLOSED.formFactor;
        double radiusFactor = PowerCalculator.radiusFactor(radius);
        double mastery = (1.0 + masteryLevel * 0.04) * (1.0 + barrierPower * 0.06);
        double flat = masteryLevel;
        double grade = this.estimateClientGradeMultiplier();
        double power = base * hp * duration * form * radiusFactor * mastery * grade + flat;
        double refinement = 1.0 + Math.max(0, barrierRef) * 0.04;
        return Math.max(0.0, power * refinement);
    }

    private int adjustedEffectiveLevel(DomainMasteryProperties prop, DomainMasteryProperties changedProp, int deltaLevel) {
        int level = this.getEffectivePropertyLevel(prop);
        if (prop == changedProp) {
            level += deltaLevel;
        }
        return Math.max(-5, Math.min(prop.getMaxLevel(), level));
    }

    private double estimateClientGradeMultiplier() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1.0;
        }
        double level = mc.player.experienceLevel;
        if (level >= 20.0) return 1.45;
        if (level >= 13.0) return 1.32;
        if (level >= 11.0) return 1.24;
        if (level >= 9.0) return 1.16;
        if (level >= 7.0) return 1.10;
        if (level >= 4.0) return 1.05;
        if (level >= 2.0) return 1.02;
        return 1.0;
    }

    private static final class ClashPowerPreview {
        final String prefix;
        final String delta;
        final String suffix;
        final int deltaColor;

        ClashPowerPreview(String prefix, String delta, String suffix, int deltaColor) {
            this.prefix = prefix;
            this.delta = delta;
            this.suffix = suffix;
            this.deltaColor = deltaColor;
        }
    }

    // ===== FOOTER ACTIONS =====
    private void drawFooter(GuiGraphics g) {
        Font font = this.font;
        this.drawActionButton(g, this.getResetBtnBounds(), "RESET ALL", this.hoveredReset, 1013358, 1357990, true, !this.masteryMutationLocked);
        this.drawActionButton(g, this.getCloseBtnBounds(), "\u2718  CLOSE", this.hoveredClose, 1976635, 3359061, false, true);
    }

    /**
     * Draws action button as part of the addon presentation layer.
     * @param g render context used to draw the current frame.
     * @param bounds bounds used by this method.
     * @param text text used by this method.
     * @param hovered hovered used by this method.
     * @param bgCol bg col used by this method.
     * @param hoverCol hover col used by this method.
     * @param isReset is reset used by this method.
     * @param enabled enabled used by this method.
     */
    private void drawActionButton(GuiGraphics g, int[] bounds, String text, boolean hovered, int bgCol, int hoverCol, boolean isReset, boolean enabled) {
        int textCol;
        int borderCol;
        int fillCol;
        Font font = this.font;
        int x = bounds[0];
        int y = bounds[1];
        int w = bounds[2];
        int h = bounds[3];
        int a = (int)(this.openAnim * 255.0f);
        if (!enabled) {
            fillCol = (int)((float)a * 0.22f) << 24 | 0x333333;
            borderCol = (int)((float)a * 0.3f) << 24 | 0x555555;
            textCol = (int)((float)a * 0.42f) << 24 | 0x9CA3AF;
        } else if (hovered) {
            fillCol = a << 24 | hoverCol & 0xFFFFFF;
            borderCol = a << 24 | 0xFFFFFF;
            textCol = -1;
        } else {
            fillCol = a << 24 | bgCol & 0xFFFFFF;
            borderCol = (int)((float)a * 0.63f) << 24 | bgCol & 0xFFFFFF;
            textCol = (int)((float)a * 0.8f) << 24 | 0xE2E8F0;
        }
        g.fill(x, y, x + w, y + h, fillCol);
        if (hovered && enabled) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, (int)((float)a * 0.19f) << 24 | 0xFFFFFF);
        }
        g.fill(x, y, x + w, y + 1, borderCol);
        g.fill(x, y + h - 1, x + w, y + h, borderCol);
        g.fill(x, y, x + 1, y + h, borderCol);
        g.fill(x + w - 1, y, x + w, y + h, borderCol);
        if (isReset) {
            int stripeCol = (int)((float)a * 0.38f) << 24 | 0xFFFFFF;
            g.fill(x + 2, y + 2, x + 4, y + h - 2, stripeCol);
        }
        float textScale = this.resolveFittedTextScale(font, text, w - Math.max(8, (int)(this.fontScale * 10.0f)), 1.0f, 0.72f);
        int textX = x + (w - this.getScaledTextWidth(font, text, textScale)) / 2;
        int textY = y + (h - this.getScaledTextHeight(font, textScale)) / 2;
        this.drawScaledForegroundText(g, font, text, textX, textY, textCol, textScale);
    }

    // ===== LAYOUT HELPERS =====
    private int[] getFormBtnBounds(int i) {
        int btnW = Math.max(50, (int)(this.fontScale * 130.0f));
        int btnH = Math.max(10, (int)(this.fontScale * 28.0f));
        int gap = Math.max(2, (int)(this.fontScale * 10.0f));
        int totalW = 3 * btnW + 2 * gap;
        int startX = this.panelX + (this.drawW - totalW) / 2;
        int btnY = this.panelY + Math.max(20, (int)(this.fontScale * 82.0f));
        return new int[]{startX + i * (btnW + gap), btnY, btnW, btnH};
    }

    /**
     * Returns plus btn bounds for the current addon state.
     * @param i i used by this method.
     * @return the resolved plus btn bounds.
     */
    private int[] getPlusBtnBounds(int i) {
        int propStartX = this.getPropertyStartX();
        int propStartY = this.getPropertyStartY();
        int row = i / 2;
        int col = i % 2;
        int px = propStartX + col * (this.drawPropColW + this.drawPropGapX);
        int py = propStartY + row * (this.drawPropRow + this.drawPropGapY);
        int btnW = Math.max(12, (int)(this.fontScale * 18.0f));
        int btnH = Math.max(11, (int)(this.fontScale * 16.0f));
        int btnX = px + this.drawPropColW - btnW - Math.max(3, (int)(this.fontScale * 5.0f));
        int btnY = py + this.drawPropRow - btnH - Math.max(3, (int)(this.fontScale * 5.0f));
        return new int[]{btnX, btnY, btnW, btnH};
    }

    /**
     * Returns minus btn bounds for the current addon state.
     * @param i i used by this method.
     * @return the resolved minus btn bounds.
     */
    private int[] getMinusBtnBounds(int i) {
        int propStartX = this.getPropertyStartX();
        int propStartY = this.getPropertyStartY();
        int row = i / 2;
        int col = i % 2;
        int px = propStartX + col * (this.drawPropColW + this.drawPropGapX);
        int py = propStartY + row * (this.drawPropRow + this.drawPropGapY);
        int btnW = Math.max(12, (int)(this.fontScale * 18.0f));
        int btnH = Math.max(11, (int)(this.fontScale * 16.0f));
        int gap = Math.max(4, (int)(this.fontScale * 5.0f));
        int plusBtnX = px + this.drawPropColW - btnW - Math.max(3, (int)(this.fontScale * 5.0f));
        int btnX = plusBtnX - btnW - gap;
        int btnY = py + this.drawPropRow - btnH - Math.max(3, (int)(this.fontScale * 5.0f));
        return new int[]{btnX, btnY, btnW, btnH};
    }

    /**
     * Returns reset btn bounds for the current addon state.
     * @return the resolved reset btn bounds.
     */
    private int[] getResetBtnBounds() {
        return new int[]{this.panelX + Math.max(4, (int)(this.fontScale * 12.0f)), this.panelY + this.drawH - this.drawFooterH + Math.max(2, (int)(this.fontScale * 8.0f)), Math.max(40, (int)(this.fontScale * 110.0f)), Math.max(10, (int)(this.fontScale * 26.0f))};
    }

    /**
     * Returns close btn bounds for the current addon state.
     * @return the resolved close btn bounds.
     */
    private int[] getCloseBtnBounds() {
        return new int[]{this.panelX + this.drawW - Math.max(30, (int)(this.fontScale * 82.0f)), this.panelY + this.drawH - this.drawFooterH + Math.max(2, (int)(this.fontScale * 8.0f)), Math.max(30, (int)(this.fontScale * 70.0f)), Math.max(10, (int)(this.fontScale * 26.0f))};
    }

    /**
     * Checks whether is in rect is true for the current addon state.
     * @param px px used by this method.
     * @param py py used by this method.
     * @param r r used by this method.
     * @return true when is in rect succeeds; otherwise false.
     */
    private boolean isInRect(float px, float py, int[] r) {
        return px >= (float)r[0] && px <= (float)(r[0] + r[2]) && py >= (float)r[1] && py <= (float)(r[1] + r[3]);
    }

    /**
     * Returns property color for the current addon state.
     * @param idx identifier used to resolve the requested entry or state.
     * @return the resolved property color.
     */
    private int getPropertyColor(int idx) {
        return switch (idx) {
            case 0 -> 3718648;
            case 1 -> 16096779;
            case 2 -> 2278750;
            case 3 -> 16007006;
            case 4 -> 6333946;
            case 5 -> 16486972;
            case 6 -> 1357990;
            case 7 -> 16478597;
            case 8 -> 5614335;
            default -> 3718648;
        };
    }

    // ===== TEXT AND ITEM HELPERS =====
    private float resolveFittedTextScale(Font font, String text, int maxWidth, float maxScale, float minScale) {
        if (text == null || text.isEmpty()) {
            return maxScale;
        }
        if (maxWidth <= 0) {
            return minScale;
        }
        int rawWidth = font.width(text);
        if (rawWidth <= 0 || rawWidth <= maxWidth) {
            return maxScale;
        }
        float fittedScale = (float)maxWidth / (float)rawWidth;
        return Math.max(minScale, Math.min(maxScale, fittedScale));
    }

    /**
     * Returns scaled text width for the current addon state.
     * @param font font used by this method.
     * @param text text used by this method.
     * @param scale scale used by this method.
     * @return the resolved scaled text width.
     */
    private int getScaledTextWidth(Font font, String text, float scale) {
        return Math.round((float)font.width(text) * scale);
    }

    /**
     * Returns scaled text height for the current addon state.
     * @param font font used by this method.
     * @param scale scale used by this method.
     * @return the resolved scaled text height.
     */
    private int getScaledTextHeight(Font font, float scale) {
        Objects.requireNonNull(font);
        return Math.max(1, Math.round(9.0f * scale));
    }

    /**
     * Draws scaled foreground text as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param font font used by this method.
     * @param text text used by this method.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param color color used by this method.
     * @param scale scale used by this method.
     */
    private void drawScaledForegroundText(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 200.0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, Math.round((float)x / scale), Math.round((float)y / scale), color, false);
        graphics.pose().popPose();
    }

    /**
     * Draws fitted foreground text as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param font font used by this method.
     * @param text text used by this method.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param color color used by this method.
     * @param maxWidth max width used by this method.
     * @param maxScale max scale used by this method.
     * @param minScale min scale used by this method.
     */
    private void drawFittedForegroundText(GuiGraphics graphics, Font font, String text, int x, int y, int color, int maxWidth, float maxScale, float minScale) {
        float scale = this.resolveFittedTextScale(font, text, maxWidth, maxScale, minScale);
        this.drawScaledForegroundText(graphics, font, text, x, y, color, scale);
    }

    /**
     * Draws right aligned fitted foreground text as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param font font used by this method.
     * @param text text used by this method.
     * @param rightX right x used by this method.
     * @param y screen or world coordinate used by this calculation.
     * @param color color used by this method.
     * @param maxWidth max width used by this method.
     * @param maxScale max scale used by this method.
     * @param minScale min scale used by this method.
     */
    private void drawRightAlignedFittedForegroundText(GuiGraphics graphics, Font font, String text, int rightX, int y, int color, int maxWidth, float maxScale, float minScale) {
        float scale = this.resolveFittedTextScale(font, text, maxWidth, maxScale, minScale);
        int drawX = rightX - this.getScaledTextWidth(font, text, scale);
        this.drawScaledForegroundText(graphics, font, text, drawX, y, color, scale);
    }

    /**
     * Draws foreground text as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param font font used by this method.
     * @param text text used by this method.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param color color used by this method.
     */
    private void drawForegroundText(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        this.drawScaledForegroundText(graphics, font, text, x, y, color, 1.0f);
    }

    /**
     * Draws layered item as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param item item used by this method.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param z screen or world coordinate used by this calculation.
     */
    private void drawLayeredItem(GuiGraphics graphics, ItemStack item, int x, int y, double z) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, z);
        graphics.renderItem(item, x, y);
        graphics.pose().popPose();
    }

    /**
     * Draws layered item scaled as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param item item used by this method.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param size size used by this method.
     * @param z screen or world coordinate used by this calculation.
     */
    private void drawLayeredItemScaled(GuiGraphics graphics, ItemStack item, int x, int y, int size, double z) {
        float scale = Math.max(0.1f, (float)size / 16.0f);
        graphics.pose().pushPose();
        graphics.pose().translate((double)x, (double)y, z);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(item, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * Draws property control button as part of the addon presentation layer.
     * @param graphics render context used to draw the current frame.
     * @param x screen or world coordinate used by this calculation.
     * @param y screen or world coordinate used by this calculation.
     * @param w screen or world coordinate used by this calculation.
     * @param h screen or world coordinate used by this calculation.
     * @param fillColor fill color used by this method.
     * @param symbolColor symbol color used by this method.
     * @param plus plus used by this method.
     */
    private void drawPropertyControlButton(GuiGraphics graphics, int x, int y, int w, int h, int fillColor, int symbolColor, boolean plus) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 150.0);
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.fill(x, y, x + w, y + 1, -1606138812);
        graphics.fill(x, y + h - 1, x + w, y + h, -1609494255);
        graphics.fill(x, y, x + 1, y + h, -1606138812);
        graphics.fill(x + w - 1, y, x + w, y + h, -1609494255);
        int centerX = x + w / 2;
        int centerY = y + h / 2;
        int arm = Math.max(2, Math.min(w, h) / 3);
        int thickness = Math.max(1, Math.min(2, Math.round(this.fontScale * 1.5f)));
        graphics.fill(centerX - arm, centerY - thickness, centerX + arm + 1, centerY + thickness + 1, symbolColor);
        if (plus) {
            graphics.fill(centerX - thickness, centerY - arm, centerX + thickness + 1, centerY + arm + 1, symbolColor);
        }
        graphics.pose().popPose();
    }

    // ===== STATIC LABEL HELPERS =====
    private String getPropertyIcon(int idx) {
        return switch (idx) {
            case 0 -> "CE";
            case 1 -> "BF";
            case 2 -> "RCT";
            case 3 -> "BL";
            case 4 -> "SL";
            case 5 -> "DU";
            case 6 -> "RA";
            case 7 -> "CL";
            default -> "??";
        };
    }

    /**
     * Returns short name for the current addon state.
     * @param idx identifier used to resolve the requested entry or state.
     * @return the resolved short name.
     */
    private String getShortName(int idx) {
        return switch (idx) {
            case 0 -> "CE Drain";
            case 1 -> "BF Chance";
            case 2 -> "RCT Heal";
            case 3 -> "Blind";
            case 4 -> "Slow";
            case 5 -> "Duration";
            case 6 -> "Radius";
            case 7 -> "Barrier Power";
            case 8 -> "Barrier Ref";
            default -> "???";
        };
    }

    /**
     * Returns property tagline for the current addon state.
     * @param idx identifier used to resolve the requested entry or state.
     * @return the resolved property tagline.
     */
    private String getPropertyTagline(int idx) {
        return switch (idx) {
            case 0 -> "Drain victim CE";
            case 1 -> "Boost BF odds";
            case 2 -> "Amplify RCT";
            case 3 -> "Blind targets";
            case 4 -> "Slow targets";
            case 5 -> "Longer domain";
            case 6 -> "Wider domain";
            case 7 -> "Reinforce barrier";
            case 8 -> "Barrier resist";
            default -> "";
        };
    }

    /**
     * Returns property item for the current addon state.
     * @param idx identifier used to resolve the requested entry or state.
     * @return the resolved property item.
     */
    private ItemStack getPropertyItem(int idx) {
        return switch (idx) {
            case 0 -> new ItemStack((ItemLike)Items.LIGHTNING_ROD);
            case 1 -> new ItemStack((ItemLike)Items.GOLD_NUGGET);
            case 2 -> new ItemStack((ItemLike)Items.GLISTERING_MELON_SLICE);
            case 3 -> new ItemStack((ItemLike)Items.ENDER_EYE);
            case 4 -> new ItemStack((ItemLike)Items.COBWEB);
            case 5 -> new ItemStack((ItemLike)Items.CLOCK);
            case 6 -> new ItemStack((ItemLike)Items.COMPASS);
            case 7 -> new ItemStack((ItemLike)Items.NETHERITE_SWORD);
            case 8 -> new ItemStack((ItemLike)Items.SHIELD);
            default -> new ItemStack((ItemLike)Items.AMETHYST_SHARD);
        };
    }

    /**
     * Returns form item for the current addon state.
     * @param form form used by this method.
     * @return the resolved form item.
     */
    private ItemStack getFormItem(int form) {
        return switch (form) {
            case 0 -> new ItemStack((ItemLike)Items.GRAY_STAINED_GLASS_PANE);
            case 1 -> new ItemStack((ItemLike)Items.OBSIDIAN);
            case 2 -> new ItemStack((ItemLike)Items.ENDER_EYE);
            default -> new ItemStack((ItemLike)Items.BARRIER);
        };
    }

    // ===== CAPABILITY READ HELPERS =====
    private int getDomainMasteryLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getDomainMasteryLevel()).orElse(0);
        }
        catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns domain property points for the current addon state.
     * @return the resolved domain property points.
     */
    private int getDomainPropertyPoints() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getDomainPropertyPoints()).orElse(0);
        }
        catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns domain xp for the current addon state.
     * @return the resolved domain xp.
     */
    private double getDomainXP() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getDomainXP()).orElse(0.0);
        }
        catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Returns xp to next for the current addon state.
     * @return the resolved xp to next.
     */
    private int getXPToNext() {
        return switch (this.getDomainMasteryLevel()) {
            case 0 -> 300;
            case 1 -> 700;
            case 2 -> 1300;
            case 3 -> 2200;
            case 4 -> 3500;
            default -> 1;
        };
    }

    /**
     * Returns xp progress for the current addon state.
     * @return the resolved xp progress.
     */
    private double getXPProgress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getXPProgress()).orElse(0.0);
        }
        catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Returns property level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved property level.
     */
    private int getPropertyLevel(DomainMasteryProperties prop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getPropertyLevel(prop)).orElse(0);
        }
        catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns effective property level for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return the resolved effective property level.
     */
    private int getEffectivePropertyLevel(DomainMasteryProperties prop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.getEffectivePropertyLevel(prop)).orElse(0);
        }
        catch (Exception e) {
            return 0;
        }
    }

    /**
     * Checks whether is negative property is true for the current addon state.
     * @param propIdx identifier used to resolve the requested entry or state.
     * @return true when is negative property succeeds; otherwise false.
     */
    private boolean isNegativeProperty(int propIdx) {
        DomainMasteryProperties prop = DomainMasteryProperties.values()[propIdx];
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.isNegativeProperty(prop)).orElse(false);
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether can set negative property is true for the current addon state.
     * @param prop property identifier involved in this operation.
     * @return true when can set negative property succeeds; otherwise false.
     */
    private boolean canSetNegativeProperty(DomainMasteryProperties prop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        try {
            return mc.player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).resolve().map(data -> data.canSetNegative(prop)).orElse(false);
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether can upgrade property is true for the current addon state.
     * @param propIdx identifier used to resolve the requested entry or state.
     * @return true when can upgrade property succeeds; otherwise false.
     */
    private boolean canUpgradeProperty(int propIdx) {
        DomainMasteryProperties prop = DomainMasteryProperties.values()[propIdx];
        return !prop.isLocked(this.getDomainMasteryLevel()) && !this.isNegativeProperty(propIdx) && this.getPropertyLevel(prop) < prop.getMaxLevel() && this.getDomainPropertyPoints() >= prop.getPointCost();
    }

    /**
     * Checks whether can refund property is true for the current addon state.
     * @param propIdx identifier used to resolve the requested entry or state.
     * @return true when can refund property succeeds; otherwise false.
     */
    private boolean canRefundProperty(int propIdx) {
        DomainMasteryProperties prop = DomainMasteryProperties.values()[propIdx];
        return !prop.isLocked(this.getDomainMasteryLevel()) && !this.isNegativeProperty(propIdx) && this.getPropertyLevel(prop) > 0;
    }

    /**
     * Checks whether can negative decrease property is true for the current addon state.
     * @param propIdx identifier used to resolve the requested entry or state.
     * @return true when can negative decrease property succeeds; otherwise false.
     */
    private boolean canNegativeDecreaseProperty(int propIdx) {
        DomainMasteryProperties prop = DomainMasteryProperties.values()[propIdx];
        int effectiveLevel = this.getEffectivePropertyLevel(prop);
        return prop.supportsNegativeModify() && this.getDomainMasteryLevel() >= 5 && this.getPropertyLevel(prop) <= 0 && this.canSetNegativeProperty(prop) && (!this.isNegativeProperty(propIdx) || effectiveLevel > -5);
    }

    /**
     * Checks whether can negative increase property is true for the current addon state.
     * @param propIdx identifier used to resolve the requested entry or state.
     * @return true when can negative increase property succeeds; otherwise false.
     */
    private boolean canNegativeIncreaseProperty(int propIdx) {
        DomainMasteryProperties prop = DomainMasteryProperties.values()[propIdx];
        return prop.supportsNegativeModify() && this.isNegativeProperty(propIdx) && this.getDomainPropertyPoints() >= prop.getPointCost();
    }

    // ===== NETWORK ACTIONS =====
    private void setForm(int form) {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(3, form));
    }

    /**
     * Performs upgrade property for this addon component.
     * @param propIdx identifier used to resolve the requested entry or state.
     */
    private void upgradeProperty(int propIdx) {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(0, propIdx));
    }

    /**
     * Performs refund property for this addon component.
     * @param propIdx identifier used to resolve the requested entry or state.
     */
    private void refundProperty(int propIdx) {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(1, propIdx));
    }

    /**
     * Performs negative decrease for this addon component.
     * @param propIdx identifier used to resolve the requested entry or state.
     */
    private void negativeDecrease(int propIdx) {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(5, propIdx));
    }

    /**
     * Performs negative increase for this addon component.
     * @param propIdx identifier used to resolve the requested entry or state.
     */
    private void negativeIncrease(int propIdx) {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(6, propIdx));
    }

    /**
     * Performs reset all for this addon component.
     */
    private void resetAll() {
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainPropertyPacket(2, -1));
    }

    // ===== LOCKED-STATE FEEDBACK =====
    private void drawMutationLockTooltip(GuiGraphics g, int mx, int my) {
        Font font = this.font;
        String name = "Domain Mastery Locked";
        int maxDescWidth = Math.max(140, Math.min(260, this.width / 3));
        List<FormattedCharSequence> descLines = font.split((FormattedText)Component.literal((String)this.masteryMutationLockReason), maxDescWidth);
        int maxW = font.width(name);
        for (FormattedCharSequence line : descLines) {
            maxW = Math.max(maxW, font.width(line));
        }
        int tw = maxW + 20;
        Objects.requireNonNull(font);
        int n = descLines.size();
        Objects.requireNonNull(font);
        int th = 9 + n * 9 + 16;
        int tx = mx + 16;
        int ty = my - th - 4;
        if (tx + tw > this.width) {
            tx = mx - tw - 4;
        }
        if (ty < 0) {
            ty = my + 16;
        }
        g.pose().pushPose();
        g.pose().translate(0.0, 0.0, 420.0);
        g.fill(tx, ty, tx + tw, ty + th, -268106220);
        g.fill(tx, ty, tx + tw, ty + 1, -680437);
        g.fill(tx, ty + th - 1, tx + tw, ty + th, -680437);
        g.fill(tx, ty, tx + 1, ty + th, -680437);
        g.fill(tx + tw - 1, ty, tx + tw, ty + th, -680437);
        g.drawString(font, name, tx + 10, ty + 7, -680437, false);
        for (int i = 0; i < descLines.size(); ++i) {
            FormattedCharSequence formattedCharSequence = (FormattedCharSequence)descLines.get(i);
            Objects.requireNonNull(font);
            Objects.requireNonNull(font);
            g.drawString(font, formattedCharSequence, tx + 10, ty + 7 + 9 + i * 9, -1906448, false);
        }
        g.pose().popPose();
    }

    /**
     * Performs send form lock message for this addon component.
     * @param form form used by this method.
     */
    private void sendFormLockMessage(int form) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (form == 0) {
            return;
        }
        String message = form == 1 ? "Closed Domain requires Domain Mastery Lv.1" : (this.getDomainMasteryLevel() < 5 ? "Open Domain requires Domain Mastery Lv.5" : "Open Domain also requires the Open Domain achievement");
        mc.player.displayClientMessage((Component)Component.literal((String)"\u26a0 ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Mastery] ").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)message).withStyle(ChatFormatting.RED)), false);
    }

    /**
     * Performs send mutation lock message for this addon component.
     */
    private void sendMutationLockMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || this.masteryMutationLockReason.isEmpty()) {
            return;
        }
        mc.player.displayClientMessage((Component)Component.literal((String)"\u26a0 ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Mastery] ").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)this.masteryMutationLockReason).withStyle(ChatFormatting.RED)), false);
    }

    // ===== SOUND HELPERS =====
    private void playUiHoverSound(int hoveredControl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        float pitch = 0.92f + (float)(hoveredControl % 7) * 0.03f;
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.BOOK_PAGE_TURN, (float)0.08f, (float)pitch));
    }

    /**
     * Performs play ui open sound for this addon component.
     */
    private void playUiOpenSound() {
        Minecraft mc = Minecraft.getInstance();
        if (this.playedOpenSound || mc.getSoundManager() == null) {
            return;
        }
        this.playedOpenSound = true;
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.ENCHANTMENT_TABLE_USE, (float)0.11f, (float)0.92f));
    }

    /**
     * Performs play form select sound for this addon component.
     * @param form form used by this method.
     */
    private void playFormSelectSound(int form) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        float pitch = switch (form) {
            case 0 -> 0.82f;
            case 1 -> 0.98f;
            case 2 -> 1.14f;
            default -> 1.0f;
        };
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.AMETHYST_BLOCK_CHIME, (float)0.18f, (float)pitch));
    }

    /**
     * Performs play upgrade sound for this addon component.
     */
    private void playUpgradeSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.EXPERIENCE_ORB_PICKUP, (float)0.16f, (float)0.92f));
    }

    /**
     * Performs play refund sound for this addon component.
     */
    private void playRefundSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.ITEM_FRAME_REMOVE_ITEM, (float)0.15f, (float)0.86f));
    }

    /**
     * Performs play reset sound for this addon component.
     */
    private void playResetSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.ENCHANTMENT_TABLE_USE, (float)0.14f, (float)0.78f));
    }

    /**
     * Performs play close sound for this addon component.
     */
    private void playCloseSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.CHEST_CLOSE, (float)0.14f, (float)0.96f));
    }

    /**
     * Performs play ui lock sound for this addon component.
     */
    private void playUiLockSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)SoundEvents.DISPENSER_FAIL, (float)0.12f, (float)0.82f));
    }

    // ===== MISCELLANEOUS HELPERS =====
    private int getHoveredControlId() {
        if (this.hoveredFormIdx >= 0) {
            return 100 + this.hoveredFormIdx;
        }
        for (int i = 0; i < this.hoveredPlus.length; ++i) {
            if (this.hoveredPlus[i]) {
                return 200 + i;
            }
            if (!this.hoveredMinus[i]) continue;
            return 300 + i;
        }
        if (this.hoveredReset) {
            return 400;
        }
        if (this.hoveredClose) {
            return 401;
        }
        return -1;
    }

    /**
     * Returns property area width for the current addon state.
     * @return the resolved property area width.
     */
    private int getPropertyAreaWidth() {
        return this.drawW - Math.max(16, (int)(this.fontScale * 36.0f));
    }

    /**
     * Returns property start x for the current addon state.
     * @return the resolved property start x.
     */
    private int getPropertyStartX() {
        return this.panelX + Math.max(8, (int)(this.fontScale * 18.0f));
    }

    /**
     * Returns property start y for the current addon state.
     * @return the resolved property start y.
     */
    private int getPropertyStartY() {
        return this.panelY + this.drawHeaderH + Math.max(4, (int)(this.fontScale * 10.0f));
    }
}

