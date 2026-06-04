package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure formatting of the Sukuna Incomplete Domain Shrine dust overlay bar.
 *
 * <p>This class is intentionally Minecraft-free so the overlay bar computation can be
 * unit- and property-tested without a running client or server. It mirrors the base-mod
 * bar style: a bold-red ({@code §l§4}) run of filled {@code |} glyphs proportional to
 * {@code dust_amount / 200}, with the remaining segments rendered dim ({@code §r§7}).
 *
 * <p>Invariants (see design Property 1):
 * <ul>
 *   <li>{@link #filledSegments(double, int)} is in {@code [0, totalSegments]}.</li>
 *   <li>{@code filledSegments(dust, 10)} is monotonic non-decreasing in {@code dust}.</li>
 *   <li>{@code filledSegments(0, 10) == 0} and {@code filledSegments(200, 10) == 10}.</li>
 *   <li>The bar is proportional to dust, never a fixed/spoofed value.</li>
 * </ul>
 */
public final class DustOverlayFormat {

    /** Maximum dust value; the base-mod {@code dust_amount} ranges over {@code [0, 200]}. */
    public static final double DUST_MAX = 200.0;

    /** Number of segments in the rendered overlay bar. */
    public static final int BAR_SEGMENTS = 10;

    /** Label shown in the {@code OVERLAY1} capability field. */
    public static final String OVERLAY1_LABEL = "DUST";

    /** Color prefix for filled (bold red) bar segments. */
    private static final String FILLED_PREFIX = "\u00a7l\u00a74";

    /** Color prefix for empty (reset + dim gray) bar segments. */
    private static final String EMPTY_PREFIX = "\u00a7r\u00a77";

    /** A single bar glyph. */
    private static final String SEGMENT = "|";

    private DustOverlayFormat() {
        // Pure utility class; no instances.
    }

    /**
     * Clamp a raw dust value into the valid {@code [0, 200]} range.
     *
     * <p>{@code NaN} maps to {@code 0}. Values below {@code 0} (including negative infinity)
     * clamp to {@code 0}; values above {@link #DUST_MAX} (including positive infinity) clamp
     * to {@link #DUST_MAX}.
     *
     * @param dust raw dust value (possibly corrupted)
     * @return a finite value in {@code [0, 200]}
     */
    public static double clampDust(double dust) {
        if (Double.isNaN(dust)) {
            return 0.0;
        }
        if (dust < 0.0) {
            return 0.0;
        }
        if (dust > DUST_MAX) {
            return DUST_MAX;
        }
        return dust;
    }

    /**
     * Number of filled bar segments for a dust value, in {@code [0, totalSegments]}.
     *
     * <p>Equals {@code round(clampDust(dust) / 200 * totalSegments)}, clamped into
     * {@code [0, totalSegments]}.
     *
     * @param dust          raw dust value (clamped internally)
     * @param totalSegments total number of segments in the bar
     * @return the number of filled segments, in {@code [0, totalSegments]}
     */
    public static int filledSegments(double dust, int totalSegments) {
        if (totalSegments <= 0) {
            return 0;
        }
        double clamped = clampDust(dust);
        long segments = Math.round(clamped / DUST_MAX * totalSegments);
        if (segments < 0L) {
            return 0;
        }
        if (segments > totalSegments) {
            return totalSegments;
        }
        return (int) segments;
    }

    /**
     * Build the {@code OVERLAY2} string proportional to {@code dust / 200}.
     *
     * <p>Produces a bold-red ({@code §l§4}) run of filled {@code |} glyphs equal to
     * {@code filledSegments(dust, 10)}, followed by the remaining segments rendered dim
     * ({@code §r§7}). An empty bar (all dim) is produced at {@code dust == 0}; a full
     * 10-segment red bar ({@code §l§4||||||||||}) is produced at {@code dust == 200},
     * matching the base-mod style.
     *
     * @param dust raw dust value (clamped internally)
     * @return the formatted overlay bar string
     */
    public static String buildBar(double dust) {
        int filled = filledSegments(dust, BAR_SEGMENTS);
        StringBuilder bar = new StringBuilder(FILLED_PREFIX);
        for (int index = 0; index < BAR_SEGMENTS; index++) {
            if (index == filled) {
                bar.append(EMPTY_PREFIX);
            }
            bar.append(SEGMENT);
        }
        return bar.toString();
    }
}
