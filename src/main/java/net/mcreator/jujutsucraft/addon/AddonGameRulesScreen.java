package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class AddonGameRulesScreen extends Screen {
    private static final int PANEL_MAX_W = 880;
    private static final int PANEL_MAX_H = 560;
    private static final int COLOR_PANEL = 0x050711;
    private static final int COLOR_PANEL_SOFT = 0x0A1020;
    private static final int COLOR_HEADER = 0x0D1225;
    private static final int COLOR_SIDEBAR = 0x080C18;
    private static final int COLOR_ROW = 0x101827;
    private static final int COLOR_ROW_HOVER = 0x172238;
    private static final int COLOR_TEXT = 0xEAF2FF;
    private static final int COLOR_TEXT_MID = 0x9CAFC6;
    private static final int COLOR_TEXT_DIM = 0x5F7088;
    private static final int COLOR_RED = 0xEF4444;
    private static final int COLOR_BLUE = 0x38BDF8;
    private static final int COLOR_GREEN = 0x22C55E;
    private static final int COLOR_GOLD = 0xFBBF24;

    private List<AddonGameRules.RuleSnapshot> allRules = List.of();
    private final Map<String, EditBox> integerEditors = new HashMap<>();
    private EditBox searchBox;
    private String selectedTabId = "";
    private String searchText = "";
    private int scrollOffset = 0;
    private boolean needsRebuild = false;
    private boolean playedOpenSound = false;
    private int lastHoveredControl = -1;
    private long openTimeMs = 0L;
    private float openAnim = 0.0f;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int headerH;
    private int sidebarW;
    private int rowH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int listTop;
    private int listBottom;
    private int lastLayoutScreenW = -1;
    private int lastLayoutScreenH = -1;

    public AddonGameRulesScreen(List<AddonGameRules.RuleSnapshot> rules) {
        super(Component.literal("JoQu's JJC Addon Gamerules"));
        this.assignRules(rules, false);
    }

    public void updateRules(List<AddonGameRules.RuleSnapshot> rules) {
        this.assignRules(rules, true);
        if (this.minecraft != null && this.width > 0 && this.height > 0) {
            this.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        if (this.openTimeMs == 0L) {
            this.openTimeMs = System.currentTimeMillis();
        }
        this.computeLayout();
        this.rebuildWidgets();
        this.playOpenSound();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        long age = System.currentTimeMillis() - this.openTimeMs;
        this.openAnim = Math.min(1.0f, (float)age / 180.0f);
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        for (EditBox editor : this.integerEditors.values()) {
            editor.tick();
        }
        if (this.needsRebuild) {
            this.needsRebuild = false;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.ensureWidgetsForCurrentSize();
        this.drawBackdrop(graphics);
        this.drawPanel(graphics);
        this.drawHeader(graphics, mouseX, mouseY);
        this.drawSidebar(graphics, mouseX, mouseY);
        this.drawRuleRows(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.drawScrollBar(graphics);
        this.updateHoverSound(mouseX, mouseY);
        AddonGameRules.RuleSnapshot hovered = this.hoveredVisibleRule(mouseX, mouseY);
        if (hovered != null) {
            this.drawRuleTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.ensureWidgetsForCurrentSize();
        if (button == 0) {
            if (this.closeBounds().contains(mouseX, mouseY)) {
                this.playUiSound(SoundEvents.CHEST_CLOSE, 0.14f, 0.96f);
                this.onClose();
                return true;
            }
            if (this.refreshBounds().contains(mouseX, mouseY)) {
                this.playUiSound(SoundEvents.BOOK_PAGE_TURN, 0.13f, 1.18f);
                this.requestSnapshot();
                return true;
            }
            List<TabSummary> tabs = this.tabs();
            for (int i = 0; i < tabs.size(); i++) {
                UiRect bounds = this.tabBounds(i);
                if (bounds.contains(mouseX, mouseY)) {
                    TabSummary tab = tabs.get(i);
                    if (!tab.id.equals(this.selectedTabId)) {
                        this.selectedTabId = tab.id;
                        this.scrollOffset = 0;
                        this.playUiSound(SoundEvents.BOOK_PAGE_TURN, 0.10f, 0.92f + i * 0.03f);
                        this.rebuildWidgets();
                    }
                    return true;
                }
            }
            List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
            int visibleRows = this.visibleRowCount();
            int start = Math.min(this.scrollOffset, Math.max(0, visible.size() - visibleRows));
            int end = Math.min(visible.size(), start + visibleRows);
            for (int i = start; i < end; i++) {
                AddonGameRules.RuleSnapshot rule = visible.get(i);
                UiRect row = this.rowBounds(i - start);
                if (!row.contains(mouseX, mouseY)) continue;
                if (rule.kind() == AddonGameRules.RuleKind.BOOLEAN) {
                    UiRect toggle = this.booleanToggleBounds(row);
                    if (toggle.contains(mouseX, mouseY)) {
                        this.sendRuleValue(rule.id(), Boolean.toString(!Boolean.parseBoolean(rule.value())));
                        this.playUiSound(SoundEvents.BEACON_POWER_SELECT, 0.16f, Boolean.parseBoolean(rule.value()) ? 0.82f : 1.24f);
                        return true;
                    }
                } else {
                    IntControls controls = this.integerControls(row);
                    if (controls.minus().contains(mouseX, mouseY)) {
                        this.sendRuleValue(rule.id(), Integer.toString(this.currentEditorInt(rule) - this.stepFor(rule)));
                        this.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.13f, 0.82f);
                        return true;
                    }
                    if (controls.plus().contains(mouseX, mouseY)) {
                        this.sendRuleValue(rule.id(), Integer.toString(this.currentEditorInt(rule) + this.stepFor(rule)));
                        this.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.13f, 1.32f);
                        return true;
                    }
                    if (controls.apply() != null && controls.apply().contains(mouseX, mouseY)) {
                        this.sendRuleValue(rule.id(), this.currentEditorText(rule));
                        this.playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.16f, 1.04f);
                        return true;
                    }
                    if (controls.reset() != null && controls.reset().contains(mouseX, mouseY)) {
                        this.sendRuleValue(rule.id(), rule.defaultValue());
                        this.playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.12f, 0.72f);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!this.listBounds().contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int maxScroll = Math.max(0, visible.size() - this.visibleRowCount());
        int old = this.scrollOffset;
        if (amount > 0.0) this.scrollOffset = Math.max(0, this.scrollOffset - 1);
        else if (amount < 0.0) this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 1);
        if (old != this.scrollOffset) {
            this.rebuildWidgets();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            for (Map.Entry<String, EditBox> entry : this.integerEditors.entrySet()) {
                EditBox editor = entry.getValue();
                if (editor != null && editor.isFocused()) {
                    this.sendRuleValue(entry.getKey(), editor.getValue());
                    editor.setFocused(false);
                    this.playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.16f, 1.04f);
                    return true;
                }
            }
        }
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void assignRules(List<AddonGameRules.RuleSnapshot> rules, boolean keepSelection) {
        String oldTab = keepSelection ? this.selectedTabId : "";
        ArrayList<AddonGameRules.RuleSnapshot> copy = new ArrayList<>(rules == null ? List.of() : rules);
        copy.sort(Comparator.comparingInt(AddonGameRules.RuleSnapshot::tabOrder).thenComparing(AddonGameRules.RuleSnapshot::label, String.CASE_INSENSITIVE_ORDER));
        this.allRules = List.copyOf(copy);
        this.selectedTabId = !oldTab.isEmpty() && this.hasTab(oldTab) ? oldTab : this.firstTabId();
        this.clampScroll();
    }

    protected void rebuildWidgets() {
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        String focusedEditorId = this.focusedEditorId();
        this.clearWidgets();
        this.integerEditors.clear();
        this.computeLayout();
        this.lastLayoutScreenW = this.width;
        this.lastLayoutScreenH = this.height;
        this.clampScroll();
        UiRect search = this.searchBounds();
        UiRect searchText = this.searchTextBounds(search);
        this.searchBox = new EditBox(this.font, searchText.x(), searchText.y(), searchText.w(), searchText.h(), Component.literal("Search"));
        this.searchBox.setValue(this.searchText);
        this.searchBox.setMaxLength(96);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFEAF2FF);
        this.searchBox.setSuggestion(this.searchText.isEmpty() ? "Search all sections" : "");
        this.searchBox.setResponder(value -> {
            if (!value.equals(this.searchText)) {
                this.searchText = value;
                this.scrollOffset = 0;
                this.needsRebuild = true;
            }
        });
        this.searchBox.setFocused(searchFocused);
        this.addRenderableWidget(this.searchBox);

        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int visibleRows = this.visibleRowCount();
        int start = Math.min(this.scrollOffset, Math.max(0, visible.size() - visibleRows));
        int end = Math.min(visible.size(), start + visibleRows);
        for (int i = start; i < end; i++) {
            AddonGameRules.RuleSnapshot rule = visible.get(i);
            if (rule.kind() != AddonGameRules.RuleKind.INTEGER) continue;
            UiRect row = this.rowBounds(i - start);
            IntControls controls = this.integerControls(row);
            UiRect textBounds = this.integerTextBounds(controls.edit());
            EditBox editor = new EditBox(this.font, textBounds.x(), textBounds.y(), textBounds.w(), textBounds.h(), Component.literal(rule.label()));
            editor.setValue(rule.value());
            editor.setMaxLength(12);
            editor.setBordered(false);
            editor.setTextColor(0xFFEAF2FF);
            editor.setFocused(rule.id().equals(focusedEditorId));
            this.integerEditors.put(rule.id(), editor);
            this.addRenderableWidget(editor);
        }
    }

    private void ensureWidgetsForCurrentSize() {
        if (this.width != this.lastLayoutScreenW || this.height != this.lastLayoutScreenH) {
            this.rebuildWidgets();
        } else {
            this.computeLayout();
        }
    }

    private void computeLayout() {
        int maxW = Math.max(228, this.width - 12);
        int maxH = Math.max(190, this.height - 20);
        this.panelW = Math.min(PANEL_MAX_W, maxW);
        this.panelH = Math.min(PANEL_MAX_H, maxH);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.headerH = this.panelH < 330 ? 46 : 54;
        this.sidebarW = this.panelW < 520 ? 112 : 158;
        this.rowH = this.panelH < 360 ? 38 : 44;
        this.contentX = this.panelX + this.sidebarW + 12;
        this.contentY = this.panelY + this.headerH + 12;
        this.contentW = Math.max(84, this.panelX + this.panelW - this.contentX - 14);
        this.listTop = this.contentY;
        this.listBottom = this.panelY + this.panelH - 16;
    }

    private void drawBackdrop(GuiGraphics graphics) {
        int alpha = (int)(170.0f * Math.max(this.openAnim, 0.35f));
        this.fillVerticalGradient(graphics, 0, 0, this.width, this.height, argb(alpha, 0x02040A), argb(Math.min(210, alpha + 20), 0x120711));
    }

    private void drawPanel(GuiGraphics g) {
        int a = (int)(255.0f * Math.max(this.openAnim, 0.65f));
        g.fill(this.panelX - 2, this.panelY - 2, this.panelX + this.panelW + 2, this.panelY - 1, argb(74, COLOR_BLUE));
        g.fill(this.panelX - 2, this.panelY + this.panelH + 1, this.panelX + this.panelW + 2, this.panelY + this.panelH + 2, argb(66, COLOR_RED));
        g.fill(this.panelX - 2, this.panelY - 1, this.panelX - 1, this.panelY + this.panelH + 1, argb(58, COLOR_BLUE));
        g.fill(this.panelX + this.panelW + 1, this.panelY - 1, this.panelX + this.panelW + 2, this.panelY + this.panelH + 1, argb(58, COLOR_RED));
        this.fillVerticalGradient(g, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, argb(a, 0x09111F), argb(a, 0x03050D));
        this.fillVerticalGradient(g, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.headerH, argb(248, 0x141D34), argb(238, 0x07101F));
        this.fillVerticalGradient(g, this.panelX, this.panelY + this.headerH, this.panelX + this.sidebarW, this.panelY + this.panelH, argb(235, 0x0A1222), argb(238, 0x050813));
        this.fillVerticalGradient(g, this.panelX + this.sidebarW, this.panelY + this.headerH, this.panelX + this.panelW, this.panelY + this.panelH, argb(230, 0x0C1424), argb(224, 0x060A14));
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 1, argb(156, COLOR_BLUE));
        g.fill(this.panelX, this.panelY + this.panelH - 1, this.panelX + this.panelW, this.panelY + this.panelH, argb(132, COLOR_RED));
        g.fill(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelH, argb(128, COLOR_BLUE));
        g.fill(this.panelX + this.panelW - 1, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, argb(128, COLOR_RED));
        g.fill(this.panelX + this.sidebarW, this.panelY + this.headerH, this.panelX + this.sidebarW + 1, this.panelY + this.panelH, argb(92, COLOR_BLUE));
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        Font font = this.font;
        int titleX = this.panelX + 16;
        int titleY = this.panelY + (this.headerH < 50 ? 9 : 11);
        String brand = this.trimToWidth("JoQu's JJC Addon", Math.max(58, this.sidebarW - 20));
        g.drawString(font, brand, titleX + 1, titleY + 1, argb(150, 0x000000), false);
        g.drawString(font, brand, titleX, titleY, argb(255, COLOR_TEXT), false);
        g.drawString(font, "GAMERULE CONTROL", titleX, titleY + 12, argb(230, COLOR_BLUE), false);
        UiRect search = this.searchBounds();
        g.fill(search.x() - 2, search.y() - 2, search.right() + 2, search.bottom() + 2, argb(130, COLOR_BLUE));
        this.fillVerticalGradient(g, search.x(), search.y(), search.right(), search.bottom(), argb(245, 0x0B1427), argb(238, 0x050812));
        if (this.isSearching()) {
            String searchCount = this.filteredRules().size() + " global matches";
            int sx = search.x();
            int sy = search.bottom() + 5;
            g.drawString(font, this.trimToWidth(searchCount, Math.max(40, search.w())), sx, sy, argb(200, COLOR_GOLD), false);
        }
        this.drawFlatButton(g, this.refreshBounds(), this.refreshBounds().w() < 46 ? "REF" : "RELOAD", COLOR_BLUE, this.refreshBounds().contains(mouseX, mouseY), true);
        this.drawFlatButton(g, this.closeBounds(), "X", COLOR_RED, this.closeBounds().contains(mouseX, mouseY), true);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        List<TabSummary> tabs = this.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            TabSummary tab = tabs.get(i);
            UiRect bounds = this.tabBounds(i);
            boolean active = tab.id.equals(this.selectedTabId);
            boolean hover = bounds.contains(mouseX, mouseY);
            int accent = this.accentForTab(tab.id);
            int bg = active ? blend(accent, 0x050711, 0.28f) : (hover ? COLOR_ROW_HOVER : 0x0B1020);
            this.fillVerticalGradient(g, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb(active ? 245 : 190, blend(accent, bg, active ? 0.16f : 0.08f)), argb(active ? 220 : 165, bg));
            g.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), argb(active ? 255 : 110, accent));
            if (active) g.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), argb(180, accent));
            this.drawLayeredItem(g, this.itemForTab(tab.id), bounds.x() + 5, bounds.y() + (bounds.h() - 16) / 2, 350.0D);
            String label = this.trimToWidth(this.compactTabName(tab.name), bounds.w() - 50);
            g.drawString(this.font, label, bounds.x() + 27, bounds.y() + (bounds.h() - 8) / 2, argb(255, active ? COLOR_TEXT : COLOR_TEXT_MID), false);
            String count = Integer.toString(tab.count);
            int countW = this.font.width(count) + 8;
            UiRect badge = new UiRect(bounds.right() - countW - 4, bounds.y() + (bounds.h() - 14) / 2, countW, 14);
            g.fill(badge.x(), badge.y(), badge.right(), badge.bottom(), argb(active ? 210 : 120, blend(accent, 0x050711, 0.32f)));
            g.drawString(this.font, count, badge.x() + (badge.w() - this.font.width(count)) / 2, badge.y() + 3, argb(active ? 255 : 190, active ? accent : COLOR_TEXT_DIM), false);
        }
    }

    private void drawRuleRows(GuiGraphics g, int mouseX, int mouseY) {
        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int visibleRows = this.visibleRowCount();
        int start = Math.min(this.scrollOffset, Math.max(0, visible.size() - visibleRows));
        int end = Math.min(visible.size(), start + visibleRows);
        if (visible.isEmpty()) {
            String empty = this.isSearching() ? "No global gamerule matches" : "No rules in this tab";
            g.drawString(this.font, empty, this.contentX + 10, this.listTop + 16, argb(210, COLOR_TEXT_MID), false);
            return;
        }
        for (int i = start; i < end; i++) {
            AddonGameRules.RuleSnapshot rule = visible.get(i);
            UiRect row = this.rowBounds(i - start);
            boolean hover = row.contains(mouseX, mouseY);
            boolean changed = !rule.value().equals(rule.defaultValue());
            int accent = this.accentForTab(rule.tabId());
            int rowBottom = row.bottom() - 5;
            this.fillVerticalGradient(g, row.x(), row.y(), row.right(), rowBottom, argb(hover ? 248 : 222, hover ? blend(accent, COLOR_ROW_HOVER, 0.16f) : COLOR_ROW_HOVER), argb(hover ? 226 : 198, COLOR_ROW));
            if (hover) {
                g.fill(row.x(), row.y(), row.right(), row.y() + 1, argb(130, accent));
                g.fill(row.x(), rowBottom - 1, row.right(), rowBottom, argb(70, accent));
            }
            g.fill(row.x(), row.y(), row.x() + 3, row.bottom() - 5, argb(changed ? 255 : 170, changed ? COLOR_GOLD : accent));
            int labelEnd = rule.kind() == AddonGameRules.RuleKind.BOOLEAN ? this.booleanToggleBounds(row).x() - 8 : this.integerControls(row).minus().x() - 8;
            this.drawLayeredItem(g, this.itemForRule(rule), row.x() + 9, row.y() + 5, 340.0D);
            int textX = row.x() + 31;
            if (this.isSearching()) {
                UiRect badge = this.drawSectionBadge(g, rule, textX, row.y() + 6, labelEnd - textX);
                textX = badge.right() + 6;
            }
            int labelMax = Math.max(0, labelEnd - textX);
            if (labelMax > 24) g.drawString(this.font, this.trimToWidth(rule.label(), labelMax), textX, row.y() + 7, argb(255, COLOR_TEXT), false);
            int detailMax = Math.max(0, labelEnd - row.x() - 12);
            if (detailMax > 58 && rowBottom - row.y() >= 30) {
                String detail = this.isSearching() ? "Section: " + rule.tabName() + " | " + rule.id() : rule.id();
                if (changed) {
                    detail = detail + "  default " + rule.defaultValue();
                }
                g.drawString(this.font, this.trimToWidth(detail, detailMax), row.x() + 10, row.y() + 21, argb(190, COLOR_TEXT_DIM), false);
            }
            if (rule.kind() == AddonGameRules.RuleKind.BOOLEAN) this.drawBooleanToggle(g, rule, this.booleanToggleBounds(row), mouseX, mouseY);
            else this.drawIntegerControls(g, rule, row, mouseX, mouseY);
        }
    }

    private List<AddonGameRules.RuleSnapshot> filteredRules() {
        String needle = this.searchText == null ? "" : this.searchText.trim().toLowerCase(Locale.ROOT);
        ArrayList<AddonGameRules.RuleSnapshot> out = new ArrayList<>();
        for (AddonGameRules.RuleSnapshot rule : this.allRules) {
            if (needle.isEmpty()) {
                if (!this.selectedTabId.isEmpty() && !rule.tabId().equals(this.selectedTabId)) continue;
            } else {
                String haystack = (rule.label() + " " + rule.id() + " " + rule.fieldName() + " " + rule.description()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            out.add(rule);
        }
        return out;
    }

    private boolean isSearching() {
        return this.searchText != null && !this.searchText.trim().isEmpty();
    }

    private List<TabSummary> tabs() {
        LinkedHashMap<String, TabSummary> tabs = new LinkedHashMap<>();
        for (AddonGameRules.RuleSnapshot rule : this.allRules) {
            TabSummary tab = tabs.get(rule.tabId());
            if (tab == null) {
                tab = new TabSummary(rule.tabId(), rule.tabName(), rule.tabOrder());
                tabs.put(rule.tabId(), tab);
            }
            tab.count++;
        }
        ArrayList<TabSummary> out = new ArrayList<>(tabs.values());
        out.sort(Comparator.comparingInt(tab -> tab.order));
        return out;
    }

    private AddonGameRules.RuleSnapshot hoveredVisibleRule(int mouseX, int mouseY) {
        if (!this.listBounds().contains(mouseX, mouseY)) return null;
        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int visibleRows = this.visibleRowCount();
        int start = Math.min(this.scrollOffset, Math.max(0, visible.size() - visibleRows));
        int index = (mouseY - this.listTop) / this.rowH;
        int actual = start + index;
        if (index < 0 || index >= visibleRows || actual < 0 || actual >= visible.size()) return null;
        UiRect row = this.rowBounds(index);
        return row.contains(mouseX, mouseY) ? visible.get(actual) : null;
    }

    private void requestSnapshot() {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.RequestAddonGameRulesPacket());
    }

    private void sendRuleValue(String id, String value) {
        if (id == null || id.isBlank() || value == null || value.isBlank()) return;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.SetAddonGameRulePacket(id, value.trim()));
    }

    private int currentEditorInt(AddonGameRules.RuleSnapshot rule) {
        try {
            return Integer.parseInt(this.currentEditorText(rule).trim());
        } catch (NumberFormatException ignored) {
            try {
                return Integer.parseInt(rule.value().trim());
            } catch (NumberFormatException ignoredAgain) {
                return 0;
            }
        }
    }

    private String currentEditorText(AddonGameRules.RuleSnapshot rule) {
        EditBox editor = this.integerEditors.get(rule.id());
        return editor == null ? rule.value() : editor.getValue();
    }

    private int stepFor(AddonGameRules.RuleSnapshot rule) {
        String id = rule.id().toLowerCase(Locale.ROOT);
        if (id.endsWith("percent")) return 5;
        if (id.endsWith("ticks")) return 20;
        if (id.endsWith("basis")) return 25;
        return 1;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, this.filteredRules().size() - this.visibleRowCount());
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
    }

    private int visibleRowCount() {
        return Math.max(1, (this.listBottom - this.listTop) / Math.max(1, this.rowH));
    }

    private String focusedEditorId() {
        for (Map.Entry<String, EditBox> entry : this.integerEditors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isFocused()) return entry.getKey();
        }
        return "";
    }

    private String firstTabId() {
        return this.allRules.isEmpty() ? "" : this.allRules.get(0).tabId();
    }

    private boolean hasTab(String id) {
        for (AddonGameRules.RuleSnapshot rule : this.allRules) {
            if (rule.tabId().equals(id)) return true;
        }
        return false;
    }

    private UiRect searchBounds() {
        int closeW = 22;
        int refreshW = this.panelW < 520 ? 34 : 62;
        int right = this.panelX + this.panelW - 14 - closeW - 6 - refreshW - 8;
        int x = Math.min(this.contentX, right - 42);
        int w = Math.max(42, right - x);
        return new UiRect(x, this.panelY + (this.headerH - 20) / 2, w, 20);
    }

    private UiRect searchTextBounds(UiRect search) {
        return new UiRect(search.x() + 7, search.y() + 6, Math.max(20, search.w() - 14), 10);
    }

    private UiRect integerTextBounds(UiRect edit) {
        return new UiRect(edit.x() + 5, edit.y() + 6, Math.max(16, edit.w() - 10), 9);
    }

    private UiRect refreshBounds() {
        int closeW = 22;
        int refreshW = this.panelW < 520 ? 34 : 62;
        return new UiRect(this.panelX + this.panelW - 14 - closeW - 6 - refreshW, this.panelY + (this.headerH - 20) / 2, refreshW, 20);
    }

    private UiRect closeBounds() {
        return new UiRect(this.panelX + this.panelW - 14 - 22, this.panelY + (this.headerH - 20) / 2, 22, 20);
    }

    private UiRect tabBounds(int index) {
        int tabH = this.panelH < 380 ? 18 : 22;
        int gap = this.panelH < 380 ? 3 : 4;
        return new UiRect(this.panelX + 10, this.panelY + this.headerH + 12 + index * (tabH + gap), this.sidebarW - 20, tabH);
    }

    private UiRect listBounds() {
        return new UiRect(this.contentX, this.listTop, this.contentW, Math.max(1, this.listBottom - this.listTop));
    }

    private UiRect rowBounds(int visibleIndex) {
        return new UiRect(this.contentX, this.listTop + visibleIndex * this.rowH, this.contentW, this.rowH);
    }

    private UiRect booleanToggleBounds(UiRect row) {
        int w = Math.min(66, Math.max(46, row.w() - 16));
        return new UiRect(row.right() - w - 8, row.y() + Math.max(4, (this.rowH - 20) / 2), w, 20);
    }

    private IntControls integerControls(UiRect row) {
        int button = 20;
        int editW = this.panelW < 560 ? 44 : 58;
        int applyW = this.panelW < 500 ? 0 : 30;
        int resetW = this.panelW < 610 ? 0 : 34;
        int total = button + 4 + editW + 4 + button + (applyW > 0 ? 4 + applyW : 0) + (resetW > 0 ? 4 + resetW : 0);
        int maxTotal = Math.max(72, row.w() - 16);
        if (total > maxTotal && resetW > 0) {
            resetW = 0;
            total = button + 4 + editW + 4 + button + (applyW > 0 ? 4 + applyW : 0);
        }
        if (total > maxTotal && applyW > 0) {
            applyW = 0;
            total = button + 4 + editW + 4 + button;
        }
        if (total > maxTotal) {
            editW = Math.max(30, editW - (total - maxTotal));
            total = button + 4 + editW + 4 + button;
        }
        int x = row.right() - total - 8;
        int y = row.y() + Math.max(5, (this.rowH - 20) / 2);
        UiRect minus = new UiRect(x, y, button, 20);
        UiRect edit = new UiRect(minus.right() + 4, y, editW, 20);
        UiRect plus = new UiRect(edit.right() + 4, y, button, 20);
        UiRect apply = null;
        UiRect reset = null;
        int cursor = plus.right();
        if (applyW > 0) {
            apply = new UiRect(cursor + 4, y, applyW, 20);
            cursor = apply.right();
        }
        if (resetW > 0) reset = new UiRect(cursor + 4, y, resetW, 20);
        return new IntControls(minus, edit, plus, apply, reset);
    }

    private void drawBooleanToggle(GuiGraphics g, AddonGameRules.RuleSnapshot rule, UiRect bounds, int mouseX, int mouseY) {
        boolean enabled = Boolean.parseBoolean(rule.value());
        int accent = enabled ? COLOR_GREEN : COLOR_RED;
        boolean hover = bounds.contains(mouseX, mouseY);
        this.fillVerticalGradient(g, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb(hover ? 250 : 224, blend(accent, 0x101827, enabled ? 0.52f : 0.42f)), argb(hover ? 224 : 198, blend(accent, 0x050812, enabled ? 0.34f : 0.26f)));
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, argb(210, accent));
        g.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), argb(120, accent));
        this.drawCentered(g, enabled ? "ON" : "OFF", bounds, enabled ? COLOR_TEXT : 0xFFD5D5);
        int knobW = 8;
        int knobX = enabled ? bounds.right() - knobW - 4 : bounds.x() + 4;
        g.fill(knobX, bounds.y() + 5, knobX + knobW, bounds.bottom() - 5, argb(230, enabled ? COLOR_GREEN : COLOR_RED));
    }

    private void drawIntegerControls(GuiGraphics g, AddonGameRules.RuleSnapshot rule, UiRect row, int mouseX, int mouseY) {
        IntControls controls = this.integerControls(row);
        this.drawFlatButton(g, controls.minus(), "-", COLOR_RED, controls.minus().contains(mouseX, mouseY), true);
        g.fill(controls.edit().x() - 1, controls.edit().y() - 1, controls.edit().right() + 1, controls.edit().bottom() + 1, argb(110, this.accentForTab(rule.tabId())));
        this.fillVerticalGradient(g, controls.edit().x(), controls.edit().y(), controls.edit().right(), controls.edit().bottom(), argb(248, 0x111A2D), argb(238, 0x050812));
        this.drawFlatButton(g, controls.plus(), "+", COLOR_GREEN, controls.plus().contains(mouseX, mouseY), true);
        if (controls.apply() != null) this.drawFlatButton(g, controls.apply(), "OK", COLOR_BLUE, controls.apply().contains(mouseX, mouseY), true);
        if (controls.reset() != null) this.drawFlatButton(g, controls.reset(), "RST", COLOR_GOLD, controls.reset().contains(mouseX, mouseY), true);
    }

    private void drawScrollBar(GuiGraphics g) {
        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int visibleRows = this.visibleRowCount();
        if (visible.size() <= visibleRows) return;
        int trackX = this.panelX + this.panelW - 8;
        int trackY = this.listTop;
        int trackH = Math.max(24, this.listBottom - this.listTop - 5);
        int thumbH = Math.max(18, (int)((float)visibleRows / (float)visible.size() * (float)trackH));
        int maxScroll = Math.max(1, visible.size() - visibleRows);
        int thumbY = trackY + (int)((float)(trackH - thumbH) * ((float)this.scrollOffset / (float)maxScroll));
        g.fill(trackX, trackY, trackX + 3, trackY + trackH, argb(70, COLOR_TEXT_DIM));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, argb(210, COLOR_BLUE));
    }

    private void drawRuleTooltip(GuiGraphics g, AddonGameRules.RuleSnapshot rule, int mouseX, int mouseY) {
        int maxWidth = Math.max(170, Math.min(360, this.width - 28));
        ArrayList<String> lines = new ArrayList<>();
        lines.add(rule.label());
        lines.addAll(this.wrapText(rule.description(), maxWidth - 20));
        lines.add("Current: " + rule.value() + "   Default: " + rule.defaultValue());
        lines.add(rule.id());
        int tw = 0;
        for (String line : lines) tw = Math.max(tw, this.font.width(line));
        tw = Math.min(maxWidth, tw + 20);
        int th = 12 + lines.size() * 10;
        int tx = mouseX + 14;
        int ty = mouseY + 12;
        if (tx + tw > this.width) tx = mouseX - tw - 10;
        if (ty + th > this.height) ty = mouseY - th - 10;
        tx = Math.max(4, Math.min(tx, Math.max(4, this.width - tw - 4)));
        ty = Math.max(4, Math.min(ty, Math.max(4, this.height - th - 4)));
        int accent = this.accentForTab(rule.tabId());
        g.pose().pushPose();
        g.pose().translate(0.0D, 0.0D, 420.0D);
        this.fillVerticalGradient(g, tx, ty, tx + tw, ty + th, argb(248, 0x0F1728), argb(244, 0x040711));
        g.fill(tx, ty, tx + tw, ty + 1, argb(230, accent));
        g.fill(tx, ty + th - 1, tx + tw, ty + th, argb(160, accent));
        g.fill(tx, ty, tx + 1, ty + th, argb(160, accent));
        g.fill(tx + tw - 1, ty, tx + tw, ty + th, argb(160, accent));
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? COLOR_TEXT : (i == lines.size() - 1 ? COLOR_TEXT_DIM : COLOR_TEXT_MID);
            g.drawString(this.font, this.trimToWidth(lines.get(i), tw - 18), tx + 9, ty + 7 + i * 10, argb(255, color), false);
        }
        g.pose().popPose();
    }

    private UiRect drawSectionBadge(GuiGraphics g, AddonGameRules.RuleSnapshot rule, int x, int y, int maxWidth) {
        int accent = this.accentForTab(rule.tabId());
        String label = this.trimToWidth(rule.tabName(), Math.max(20, maxWidth - 8));
        int w = Math.max(24, Math.min(maxWidth, this.font.width(label) + 10));
        UiRect bounds = new UiRect(x, y, w, 14);
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb(210, blend(accent, 0x050711, 0.25f)));
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, argb(210, accent));
        g.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), argb(95, accent));
        g.drawString(this.font, this.trimToWidth(label, bounds.w() - 6), bounds.x() + 5, bounds.y() + 3, argb(245, COLOR_TEXT), false);
        return bounds;
    }

    private void drawLayeredItem(GuiGraphics g, ItemStack stack, int x, int y, double z) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0.0D, 0.0D, z);
        g.renderItem(stack, x, y);
        g.pose().popPose();
    }

    private ItemStack itemForRule(AddonGameRules.RuleSnapshot rule) {
        String id = rule.id().toLowerCase(Locale.ROOT);
        if (id.contains("sound")) return new ItemStack((ItemLike)Items.NOTE_BLOCK);
        if (id.contains("effect") || id.contains("vfx") || id.contains("particle")) return new ItemStack((ItemLike)Items.AMETHYST_SHARD);
        if (id.contains("cooldown") || id.contains("ticks") || id.contains("duration")) return new ItemStack((ItemLike)Items.CLOCK);
        if (id.contains("cost") || id.contains("ce")) return new ItemStack((ItemLike)Items.LAPIS_LAZULI);
        if (id.contains("damage")) return new ItemStack((ItemLike)Items.NETHERITE_SWORD);
        if (id.contains("radius") || id.contains("range")) return new ItemStack((ItemLike)Items.COMPASS);
        if (id.contains("chance") || id.contains("percent") || id.contains("basis")) return new ItemStack((ItemLike)Items.REDSTONE);
        if (rule.kind() == AddonGameRules.RuleKind.BOOLEAN) return new ItemStack((ItemLike)Items.LEVER);
        return this.itemForTab(rule.tabId());
    }

    private ItemStack itemForTab(String tabId) {
        return switch (tabId) {
            case "core" -> new ItemStack((ItemLike)Items.NETHER_STAR);
            case "interface" -> new ItemStack((ItemLike)Items.COMPASS);
            case "gojo" -> new ItemStack((ItemLike)Items.ENDER_PEARL);
            case "black_flash" -> new ItemStack((ItemLike)Items.LIGHTNING_ROD);
            case "domain_clash" -> new ItemStack((ItemLike)Items.AMETHYST_SHARD);
            case "domain" -> new ItemStack((ItemLike)Items.ENDER_EYE);
            case "sukuna" -> new ItemStack((ItemLike)Items.BLAZE_POWDER);
            case "limb" -> new ItemStack((ItemLike)Items.BONE);
            case "rct" -> new ItemStack((ItemLike)Items.GLISTERING_MELON_SLICE);
            case "yuta" -> new ItemStack((ItemLike)Items.DIAMOND_SWORD);
            default -> new ItemStack((ItemLike)Items.BOOK);
        };
    }

    private void fillVerticalGradient(GuiGraphics g, int x1, int y1, int x2, int y2, int topColor, int bottomColor) {
        int h = y2 - y1;
        if (h <= 0 || x2 <= x1) {
            return;
        }
        int steps = Math.min(28, Math.max(1, h));
        for (int i = 0; i < steps; i++) {
            float t = steps <= 1 ? 0.0f : (float)i / (float)(steps - 1);
            int yStart = y1 + i * h / steps;
            int yEnd = y1 + (i + 1) * h / steps;
            g.fill(x1, yStart, x2, Math.max(yStart + 1, yEnd), lerpArgb(topColor, bottomColor, t));
        }
    }

    private static int lerpArgb(int a, int b, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int aa = a >>> 24;
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int oa = (int)(aa + (ba - aa) * clamped);
        int or = (int)(ar + (br - ar) * clamped);
        int og = (int)(ag + (bg - ag) * clamped);
        int ob = (int)(ab + (bb - ab) * clamped);
        return oa << 24 | or << 16 | og << 8 | ob;
    }

    private void drawFlatButton(GuiGraphics g, UiRect bounds, String label, int accent, boolean hover, boolean enabled) {
        int bg = enabled ? (hover ? blend(accent, 0x070B16, 0.42f) : blend(accent, 0x070B16, 0.22f)) : 0x171717;
        this.fillVerticalGradient(g, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb(enabled ? 238 : 120, hover ? blend(accent, bg, 0.24f) : bg), argb(enabled ? 202 : 104, blend(bg, 0x02040A, 0.35f)));
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, argb(enabled ? 205 : 80, accent));
        g.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), argb(enabled ? 95 : 45, accent));
        this.drawCentered(g, label, bounds, enabled ? COLOR_TEXT : COLOR_TEXT_DIM);
    }

    private void drawPill(GuiGraphics g, UiRect bounds, String label, int accent, int mouseX, int mouseY) {
        boolean hover = bounds.contains(mouseX, mouseY);
        this.fillVerticalGradient(g, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb(hover ? 224 : 182, blend(accent, 0x111827, 0.34f)), argb(hover ? 188 : 150, blend(accent, 0x050711, 0.22f)));
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, argb(190, accent));
        this.drawCentered(g, label, bounds, COLOR_TEXT);
    }

    private void drawCentered(GuiGraphics g, String text, UiRect bounds, int color) {
        String fitted = this.trimToWidth(text, bounds.w() - 4);
        int x = bounds.x() + (bounds.w() - this.font.width(fitted)) / 2;
        int y = bounds.y() + (bounds.h() - 8) / 2;
        g.drawString(this.font, fitted, x, y, argb(255, color), false);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) return maxWidth <= 0 ? "" : text;
        String suffix = "...";
        int suffixW = this.font.width(suffix);
        String out = text;
        while (!out.isEmpty() && this.font.width(out) + suffixW > maxWidth) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? "" : out + suffix;
    }

    private List<String> wrapText(String text, int maxWidth) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(next) <= maxWidth) {
                line.setLength(0);
                line.append(next);
            } else {
                if (line.length() > 0) lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private String compactTabName(String name) {
        if (this.sidebarW >= 150) return name;
        return switch (name) {
            case "Black Flash" -> "BlackFlash";
            case "Domain Clash" -> "Clash";
            case "Sukuna / Fuga" -> "Sukuna";
            case "Near Death / RCT" -> "RCT";
            case "Yuta / Rika" -> "Yuta";
            default -> name;
        };
    }

    private int accentForTab(String tabId) {
        return switch (tabId) {
            case "core" -> 0xF43F5E;
            case "interface" -> 0x22D3EE;
            case "gojo" -> 0x38BDF8;
            case "black_flash" -> 0xFBBF24;
            case "domain_clash" -> 0xA78BFA;
            case "domain" -> 0x8B5CF6;
            case "sukuna" -> 0xEF4444;
            case "limb" -> 0x22C55E;
            case "rct" -> 0x34D399;
            case "yuta" -> 0xEC4899;
            default -> 0x94A3B8;
        };
    }

    private void updateHoverSound(int mouseX, int mouseY) {
        int hovered = this.hoveredControlId(mouseX, mouseY);
        if (hovered != this.lastHoveredControl) {
            if (hovered >= 0) {
                this.playButtonSound(1.55f);
            }
            this.lastHoveredControl = hovered;
        }
    }

    private int hoveredControlId(int mouseX, int mouseY) {
        if (this.closeBounds().contains(mouseX, mouseY)) return 1;
        if (this.refreshBounds().contains(mouseX, mouseY)) return 2;
        List<TabSummary> tabs = this.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (this.tabBounds(i).contains(mouseX, mouseY)) return 100 + i;
        }
        List<AddonGameRules.RuleSnapshot> visible = this.filteredRules();
        int visibleRows = this.visibleRowCount();
        int start = Math.min(this.scrollOffset, Math.max(0, visible.size() - visibleRows));
        int end = Math.min(visible.size(), start + visibleRows);
        for (int i = start; i < end; i++) {
            AddonGameRules.RuleSnapshot rule = visible.get(i);
            UiRect row = this.rowBounds(i - start);
            if (!row.contains(mouseX, mouseY)) continue;
            if (rule.kind() == AddonGameRules.RuleKind.BOOLEAN) {
                return this.booleanToggleBounds(row).contains(mouseX, mouseY) ? 1000 + i : -1;
            }
            IntControls controls = this.integerControls(row);
            if (controls.minus().contains(mouseX, mouseY)) return 2000 + i;
            if (controls.plus().contains(mouseX, mouseY)) return 3000 + i;
            if (controls.apply() != null && controls.apply().contains(mouseX, mouseY)) return 4000 + i;
            if (controls.reset() != null && controls.reset().contains(mouseX, mouseY)) return 5000 + i;
        }
        return -1;
    }

    private void playOpenSound() {
        if (this.playedOpenSound) {
            return;
        }
        this.playedOpenSound = true;
        this.playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.12f, 0.92f);
        this.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.10f, 1.45f);
    }

    private void playUiSound(SoundEvent event, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, volume, pitch));
    }

    private void playButtonSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void playClick(float pitch) {
        this.playButtonSound(pitch);
    }

    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int blend(int foreground, int background, float amount) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int fr = foreground >> 16 & 0xFF;
        int fg = foreground >> 8 & 0xFF;
        int fb = foreground & 0xFF;
        int br = background >> 16 & 0xFF;
        int bg = background >> 8 & 0xFF;
        int bb = background & 0xFF;
        int r = (int)(br + (fr - br) * clamped);
        int g = (int)(bg + (fg - bg) * clamped);
        int b = (int)(bb + (fb - bb) * clamped);
        return r << 16 | g << 8 | b;
    }

    private record UiRect(int x, int y, int w, int h) {
        int right() { return this.x + this.w; }
        int bottom() { return this.y + this.h; }
        boolean contains(double px, double py) { return px >= this.x && px < this.right() && py >= this.y && py < this.bottom(); }
    }

    private record IntControls(UiRect minus, UiRect edit, UiRect plus, UiRect apply, UiRect reset) {
    }

    private static final class TabSummary {
        private final String id;
        private final String name;
        private final int order;
        private int count;

        private TabSummary(String id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }

    // END_HELPERS
}
