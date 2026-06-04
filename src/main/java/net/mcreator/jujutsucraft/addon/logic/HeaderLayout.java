package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free geometry for the Domain Mastery screen header region.
 *
 * <p>This record centralizes the {@code Math.max(floor, (int)(coeff * fontScale))}
 * sizing math that {@code DomainMasteryScreen} previously computed inline across
 * {@code drawHeader}, {@code getFormBtnBounds}, and {@code drawFormHint}, and returns
 * the rectangles the screen draws: the header bounds, the "DOMAIN MASTERY" title, the
 * centered underline, the MASTERY and POINTS stat pills, the XP bar, the three
 * domain-form selector buttons (INCOMPLETE / CLOSED / OPEN), and the
 * "All Domain forms are unlocked" status text. Because it carries no Minecraft
 * imports, the geometry can be unit- and property-tested without a running client.</p>
 *
 * <h2>Restrained styling (Requirement 1.1)</h2>
 * The previous chromatic {@code accentLeft} / {@code accentRight} accent blocks — which
 * backed the multi-color decorative bars — are <b>removed</b>. The restrained style
 * keeps only the {@code title}, a {@code headerBounds} backdrop, and a single
 * {@code underline}.
 *
 * <h2>Form selectors and status (Requirement 1.8)</h2>
 * The three form-selector button rects ({@code incompleteBtn}, {@code closedBtn},
 * {@code openBtn}) and the {@code unlockedStatus} text rect are <b>added</b> alongside
 * the {@code xpBar}, so the layout can guarantee the XP bar clears them. The selectors
 * are laid out as a centered row of three at {@code btnY = panelY + max(20, 82 * fontScale)},
 * each {@code max(50, 130 * fontScale)} wide and {@code max(10, 28 * fontScale)} tall,
 * separated by {@code max(2, 10 * fontScale)}, mirroring the screen's
 * {@code getFormBtnBounds}. The status text sits below them at
 * {@code panelY + max(20, 122 * fontScale)}, mirroring {@code drawFormHint}.
 *
 * <h2>Layout</h2>
 * The MASTERY pill is pinned to the header's top-left corner and the POINTS pill to
 * the top-right corner, exactly as the screen draws them. The title and underline are
 * laid out inside the <em>inter-pill column</em> — the horizontal band strictly between
 * the two pills (each pill edge separated by a positive gap). The title is centered in
 * that column and the underline centered below it. Stacking the title region inside the
 * inter-pill column is what makes the title/pill non-overlap invariant hold <em>by
 * construction</em> rather than by coincidence.
 *
 * <h2>Dimension math (Requirement 1.4 / Property 21)</h2>
 * Every element <em>dimension</em> (each width and height) is
 * {@code Math.max(floor, (int)(coeff * fontScale))} with a positive coefficient and a
 * constant floor (see {@link #dim(int, float, float)}), so it is a non-decreasing
 * function of {@code fontScale} and increasing {@code fontScale} never shrinks an
 * element. The title and underline widths are additionally capped to the inter-pill
 * column width as a safety net so they can never reach the pills; for a non-degenerate
 * header (below) this cap never binds, leaving the dimensions equal to their pure
 * {@code max(floor, coeff * fontScale)} values. Only <em>positions</em> are clamped to
 * keep rects inside the header and to pin the XP bar above the selector row, so
 * clamping never affects any dimension's monotonicity.
 *
 * <h2>XP bar / form-selector non-overlap (Requirement 1.8 / Property 20)</h2>
 * The XP bar top is pinned so that
 * {@code xpBar.y + xpBar.h + GAP <= incompleteBtn.y} with {@code GAP == }
 * {@link #XP_FORM_GAP} {@code >= 1} pixel maintained across the whole {@code fontScale}
 * range {@code (0, 1]}: the bar is placed at its natural band
 * ({@code panelY + max(50, 58 * fontScale)}) but pulled up to
 * {@code incompleteBtn.y - barH - GAP} whenever the natural band would crowd the
 * selector row. Because the status text row sits at or below the selector row
 * ({@code 122 * fontScale >= 82 * fontScale} and both share the {@code 20px} floor), the
 * XP bar — which ends at least {@code GAP} pixels above the selector row — is therefore
 * also strictly above the status text, so it intersects neither the selectors nor the
 * status by construction.
 *
 * <h2>Fit, bounds, and stat non-overlap (Requirements 1.3, 1.7 / Property 19)</h2>
 * <ul>
 *   <li><b>Fit:</b> the title width is capped to the inter-pill column width, which is
 *       strictly less than the header inner width ({@code drawW - 2 * padX}); the full
 *       "DOMAIN MASTERY" string therefore always fits within the inner width with no
 *       truncation, and the title rect stays inside {@link #headerBounds}.</li>
 *   <li><b>Bounds:</b> {@code title} and {@code underline} are positioned inside the
 *       column and above the XP-bar band, so they stay within {@link #headerBounds}
 *       (whose bottom is the header separator at {@code y == panelY + drawHeaderH}).</li>
 *   <li><b>Non-overlap:</b> the title region lives inside the inter-pill column, so it
 *       is x-separated from both pills by a positive gap; and the XP bar sits in a band
 *       below the title stack and above the selector row, so the title region does not
 *       intersect the {@code masteryPill}, {@code pointsPill}, or {@code xpBar}.</li>
 * </ul>
 *
 * <h2>Coefficients and floors</h2>
 * The pill size ({@code 104 x 31}, floors {@code 78 x 24}), XP-bar height ({@code 9},
 * floor {@code 3}), paddings ({@code 14} / {@code 12}), gaps ({@code 10} / {@code 5}),
 * underline height ({@code 2}), the XP-bar natural vertical offset ({@code 58}, floor
 * {@code 50}), the form-selector size ({@code 130 x 28}, floors {@code 50 x 10}), the
 * selector gap ({@code 10}, floor {@code 2}), the selector-row offset ({@code 82}, floor
 * {@code 20}), and the status-row offset ({@code 122}, floor {@code 20}) are taken
 * directly from the existing {@code DomainMasteryScreen} inline math. The title width
 * coefficient ({@code 96}, floor {@code 60}) is derived from the rendered
 * "DOMAIN MASTERY" string width (~{@code 80px} at the base font) times the {@code 1.18}
 * title scale the screen uses; the title height ({@code 11}, floor {@code 8}) mirrors
 * {@code 9 * titleScale}. The underline width ({@code 120}, floor {@code 70})
 * approximates the screen's underline span. The XP-bar width uses a pure scaled
 * dimension ({@code 560}, floor {@code 80}); this is a deliberate, documented deviation
 * from the screen's {@code drawW - 2 * padX} bar width, because a subtraction-based
 * width would shrink as {@code fontScale} grows (the paddings grow) and so would violate
 * the monotonic-scaling guarantee. At {@code fontScale == 1} the {@code 560}px bar still
 * sits comfortably inside the {@code ~612}px inner width.
 *
 * <h2>Non-degenerate header precondition</h2>
 * The fit, bounds, and title/stat non-overlap guarantees assume a non-degenerate
 * header: one wide enough for the two corner pills to leave a positive inter-pill
 * column, and tall enough to contain the title stack above the XP-bar band. The
 * screen's header ({@code 640 x 154} at full scale) satisfies this with wide margins
 * across its usable scale range. For a header so small that the pills would overlap (no
 * positive column), the column-capped title region collapses to zero-width rects and
 * the layout degrades gracefully rather than throwing. The XP-bar / selector / status
 * non-overlap (Requirement 1.8) holds unconditionally across the whole {@code fontScale}
 * range because it is pinned by construction and is independent of the header width.
 *
 * @param headerBounds   the header area above the separator (panel header rectangle)
 * @param title          the "DOMAIN MASTERY" text region, centered in the inter-pill column
 * @param underline      the centered single-color underline block, below the title
 * @param masteryPill    the MASTERY stat pill, pinned to the header's top-left
 * @param pointsPill     the POINTS stat pill, pinned to the header's top-right
 * @param xpBar          the XP progress bar, pinned above the form-selector row
 * @param incompleteBtn  the INCOMPLETE form-selector button (leftmost of the row)
 * @param closedBtn      the CLOSED form-selector button (center of the row)
 * @param openBtn        the OPEN form-selector button (rightmost of the row)
 * @param unlockedStatus the "All Domain forms are unlocked" status text region
 */
public record HeaderLayout(
        Rect headerBounds,
        Rect title,
        Rect underline,
        Rect masteryPill,
        Rect pointsPill,
        Rect xpBar,
        Rect incompleteBtn,
        Rect closedBtn,
        Rect openBtn,
        Rect unlockedStatus) {

    /** The exact title string rendered in the header (14 characters, including the space). */
    public static final String TITLE_TEXT = "DOMAIN MASTERY";

    /**
     * Minimum vertical gap (pixels) the XP bar maintains above the form-selector row,
     * i.e. {@code xpBar.y + xpBar.h + XP_FORM_GAP <= incompleteBtn.y}. Required to be
     * {@code >= 1} by Requirement 1.8; a value of {@code 2} keeps a comfortable margin.
     */
    public static final int XP_FORM_GAP = 2;

    // ---- Stat pill (from DomainMasteryScreen: statW/statH) ----
    private static final int PILL_W_FLOOR = 78;
    private static final float PILL_W_COEFF = 104.0f;
    private static final int PILL_H_FLOOR = 24;
    private static final float PILL_H_COEFF = 31.0f;

    // ---- XP bar (height from DomainMasteryScreen: barS; width matches the form-button row) ----
    private static final int BAR_H_FLOOR = 3;
    private static final float BAR_H_COEFF = 9.0f;
    // Reserved vertical band ABOVE the XP bar for its "x / y XP" label, which the screen
    // draws at xpBar.y - max(10, 12 * fontScale). The band equals that exact offset so the
    // label sits flush below the pill row (it is drawn at the top of the reserved band) and
    // never collides upward into the stat pills at small fontScale.
    private static final int XP_LABEL_FLOOR = 10;
    private static final float XP_LABEL_COEFF = 12.0f;
    // Tight vertical gap between the stacked lower-header bands. Kept small (floor 2) so the
    // whole stack (pills -> XP label -> XP bar -> button row -> status) fits inside the
    // proportionally-shrinking header (154 * fontScale) even at small fontScale (~0.5),
    // where the element floors otherwise dominate.
    private static final int STACK_GAP_FLOOR = 2;
    private static final float STACK_GAP_COEFF = 4.0f;

    // ---- Title text region ----
    // titleH mirrors the screen's getScaledTextHeight(titleScale) = ~9 * titleScale,
    // where titleScale = max(0.92, 1.18 * fontScale).
    private static final int TITLE_H_FLOOR = 8;
    private static final float TITLE_H_COEFF = 11.0f;
    // titleW mirrors font.width("DOMAIN MASTERY") (~80px) * titleScale (~1.18 at full).
    private static final int TITLE_W_FLOOR = 60;
    private static final float TITLE_W_COEFF = 96.0f;

    // ---- Underline block ----
    private static final int UNDERLINE_W_FLOOR = 70;
    private static final float UNDERLINE_W_COEFF = 120.0f;
    private static final int UNDERLINE_H_FLOOR = 1;
    private static final float UNDERLINE_H_COEFF = 2.0f;

    // ---- Form-selector buttons (from DomainMasteryScreen: getFormBtnBounds) ----
    private static final int BTN_W_FLOOR = 50;
    private static final float BTN_W_COEFF = 130.0f;
    private static final int BTN_H_FLOOR = 10;
    private static final float BTN_H_COEFF = 28.0f;
    private static final int BTN_GAP_FLOOR = 2;
    private static final float BTN_GAP_COEFF = 10.0f;

    // ---- "All Domain forms are unlocked" status text (from DomainMasteryScreen: drawFormHint) ----
    private static final int STATUS_W_FLOOR = 90;
    private static final float STATUS_W_COEFF = 180.0f;
    private static final int STATUS_H_FLOOR = 7;
    private static final float STATUS_H_COEFF = 10.0f;

    // ---- Positioning insets (from DomainMasteryScreen: padS, statY, gaps) ----
    private static final int PAD_X_FLOOR = 2;
    private static final float PAD_X_COEFF = 14.0f;
    private static final int PAD_TOP_FLOOR = 9;
    private static final float PAD_TOP_COEFF = 12.0f;
    private static final int GAP_FLOOR = 6;
    private static final float GAP_COEFF = 10.0f;
    private static final int SMALL_GAP_FLOOR = 3;
    private static final float SMALL_GAP_COEFF = 5.0f;

    /**
     * A single scaled dimension: {@code Math.max(floor, (int)(coeff * fontScale))}.
     *
     * <p>With a positive {@code coeff} and a constant {@code floor}, the result is a
     * non-decreasing function of {@code fontScale}, which is the basis for the header's
     * monotonic-scaling guarantee (Property 21).</p>
     *
     * @param floor     the constant minimum pixel floor (must be {@code >= 0})
     * @param coeff     the positive scaling coefficient
     * @param fontScale the current font scale (expected in {@code (0, 1]})
     * @return the scaled dimension, never below {@code floor}
     */
    private static int dim(int floor, float coeff, float fontScale) {
        return Math.max(floor, (int) (coeff * fontScale));
    }

    /**
     * Computes the full header layout for the given font scale and panel rectangle.
     *
     * <p>The {@code (panelX, panelY, drawW, drawHeaderH)} arguments describe the panel
     * header rectangle exactly as {@code DomainMasteryScreen} derives it from the window
     * size; {@code fontScale} drives the size of every element. For a non-degenerate
     * header (see the type's precondition) the returned rectangles satisfy the fit,
     * bounds, title/stat non-overlap, and monotonicity guarantees documented on this
     * type; the XP-bar / form-selector / status non-overlap holds unconditionally.</p>
     *
     * @param fontScale   the UI font scale, expected in {@code (0, 1]}
     * @param panelX      the panel's left edge in screen pixels
     * @param panelY      the panel's top edge in screen pixels
     * @param drawW       the scaled panel width in pixels
     * @param drawHeaderH the scaled header height in pixels (separator at {@code panelY + drawHeaderH})
     * @return the computed {@link HeaderLayout}
     */
    public static HeaderLayout compute(float fontScale, int panelX, int panelY,
                                       int drawW, int drawHeaderH) {
        int bx = panelX;
        int by = panelY;
        int bw = Math.max(0, drawW);
        int bh = Math.max(0, drawHeaderH);
        Rect headerBounds = new Rect(bx, by, bw, bh);

        // Positioning insets and separators (pure scaled dimensions used only for layout).
        int padX = dim(PAD_X_FLOOR, PAD_X_COEFF, fontScale);
        int padTop = dim(PAD_TOP_FLOOR, PAD_TOP_COEFF, fontScale);
        int gap = dim(GAP_FLOOR, GAP_COEFF, fontScale);
        int smallGap = dim(SMALL_GAP_FLOOR, SMALL_GAP_COEFF, fontScale);

        // ---- Stat pills (pure monotonic dimensions; pinned to the header corners) ----
        int statW = dim(PILL_W_FLOOR, PILL_W_COEFF, fontScale);
        int statH = dim(PILL_H_FLOOR, PILL_H_COEFF, fontScale);
        Rect masteryPill = new Rect(bx + padX, by + padTop, statW, statH);
        Rect pointsPill = new Rect(bx + bw - padX - statW, by + padTop, statW, statH);

        // ---- Inter-pill column: the horizontal band the title stack lives in ----
        // Separated from each pill by a positive gap, so anything inside this column
        // cannot intersect either pill.
        int columnLeft = masteryPill.x() + masteryPill.w() + gap;
        int columnRight = pointsPill.x() - gap;
        int columnW = Math.max(0, columnRight - columnLeft);

        // ---- Title text region: vertically centered against the pill band ----
        // Give the title rect the full pill height so the screen vertically-centers the
        // "DOMAIN MASTERY" text against the MASTERY / POINTS pills, keeping the top row
        // visually balanced instead of the title floating above the tall pills.
        int titleH = dim(TITLE_H_FLOOR, TITLE_H_COEFF, fontScale);
        int titleW = Math.min(dim(TITLE_W_FLOOR, TITLE_W_COEFF, fontScale), columnW);
        int titleX = columnLeft + (columnW - titleW) / 2;
        int titleY = by + padTop;
        int titleBandH = statH;
        Rect title = new Rect(titleX, titleY, titleW, titleBandH);

        // ---- Underline: centered in the column, just under the (vertically-centered) title text ----
        int underlineH = dim(UNDERLINE_H_FLOOR, UNDERLINE_H_COEFF, fontScale);
        int underlineW = Math.min(dim(UNDERLINE_W_FLOOR, UNDERLINE_W_COEFF, fontScale), columnW);
        int underlineX = columnLeft + (columnW - underlineW) / 2;
        // The screen draws the title centered in the band; place the underline just below
        // the title text's lower edge (band center + half the text height).
        int titleTextBottom = titleY + titleBandH / 2 + titleH / 2;
        int underlineY = titleTextBottom + smallGap;
        Rect underline = new Rect(underlineX, underlineY, underlineW, underlineH);

        // ---- Form-selector buttons: centered row of three (computed first so the XP bar
        // can be width-matched and aligned to this row for a balanced layout) ----
        int btnW = dim(BTN_W_FLOOR, BTN_W_COEFF, fontScale);
        int btnH = dim(BTN_H_FLOOR, BTN_H_COEFF, fontScale);
        int btnGap = dim(BTN_GAP_FLOOR, BTN_GAP_COEFF, fontScale);
        int totalW = 3 * btnW + 2 * btnGap;
        int startX = bx + (bw - totalW) / 2;

        // ---- Sequential lower-header stack (Req 1.7, 1.8) ----
        // Anchor the stack below the lower of the pill row and the underline, with a tight
        // scaled gap so the whole stack (XP label -> XP bar -> button row -> status) fits
        // inside the proportionally-shrinking header (154 * fontScale) at any scale.
        int stackGap = dim(STACK_GAP_FLOOR, STACK_GAP_COEFF, fontScale);
        int pillsBottom = masteryPill.y() + masteryPill.h();
        int underlineBottom = underline.y() + underline.h();
        int stackTop = Math.max(pillsBottom, underlineBottom) + stackGap;

        // ---- XP bar: centered and width-matched to the button row so it lines up with the
        // buttons below it (the previous edge-to-edge hairline looked unbalanced). The
        // "x / y XP" label is drawn centered over this bar by the screen. ----
        int xpLabelBand = dim(XP_LABEL_FLOOR, XP_LABEL_COEFF, fontScale);
        int barH = dim(BAR_H_FLOOR, BAR_H_COEFF, fontScale);
        int barW = totalW;
        int barX = startX;
        int barTop = stackTop + xpLabelBand;
        Rect xpBar = new Rect(barX, barTop, barW, barH);

        // ---- Form-selector buttons, at least XP_FORM_GAP below the XP bar ----
        int btnY = barTop + barH + Math.max(XP_FORM_GAP, stackGap);
        Rect incompleteBtn = new Rect(startX, btnY, btnW, btnH);
        Rect closedBtn = new Rect(startX + (btnW + btnGap), btnY, btnW, btnH);
        Rect openBtn = new Rect(startX + 2 * (btnW + btnGap), btnY, btnW, btnH);

        // ---- "All Domain forms are unlocked" status text, one gap below the buttons ----
        int statusW = dim(STATUS_W_FLOOR, STATUS_W_COEFF, fontScale);
        int statusH = dim(STATUS_H_FLOOR, STATUS_H_COEFF, fontScale);
        int statusX = bx + (bw - statusW) / 2;
        int statusY = btnY + btnH + stackGap;
        Rect unlockedStatus = new Rect(statusX, statusY, statusW, statusH);

        return new HeaderLayout(
                headerBounds,
                title,
                underline,
                masteryPill,
                pointsPill,
                xpBar,
                incompleteBtn,
                closedBtn,
                openBtn,
                unlockedStatus);
    }
}
