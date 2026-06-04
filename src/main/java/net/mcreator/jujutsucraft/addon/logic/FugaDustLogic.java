package net.mcreator.jujutsucraft.addon.logic;

/**
 * Pure, Minecraft-free logic for the Fuga (Divine Flame Arrow) dust reward.
 *
 * <p>This class holds the two decisions that carry correctness risk for the
 * Incomplete Domain Shrine dust reward:
 * <ul>
 *   <li>{@link #resolveTransferDust(double, double)} — the real (un-spoofed) dust
 *       value that is transferred into the flame-arrow projectile's {@code cnt6}
 *       boost field before the base mod zeroes the owner's {@code dust_amount}.</li>
 *   <li>{@link #isFugaIdentity(int, double)} — the Sukuna + Fuga identity gate that
 *       every reward/overlay/cooldown path is guarded on.</li>
 * </ul>
 *
 * <p>There are intentionally no Minecraft imports here so the logic can be
 * unit- and property-tested without a running client or server.
 */
public final class FugaDustLogic {

    /** Lower bound of the valid dust range. */
    public static final double DUST_MIN = 0.0;

    /** Upper bound of the valid dust range. */
    public static final double DUST_MAX = 200.0;

    private FugaDustLogic() {
        // utility class; no instances
    }

    /**
     * Resolves the real dust value to transfer into the projectile's {@code cnt6}.
     *
     * <p>The stored dust is first clamped into {@code [0, 200]} (with {@code NaN}
     * treated as {@code 0}, consistent with {@code DustOverlayFormat.clampDust}),
     * then combined with the projectile's existing {@code cnt6} via {@code max} so
     * the transfer never reduces an already-larger boost. There is deliberately no
     * {@code 200} floor: the boost reflects the real stored dust, not a spoofed
     * full value.
     *
     * @param storedDust  the owner's stored {@code dust_amount} (any raw value)
     * @param existingCnt6 the projectile's current {@code cnt6} value
     * @return {@code max(clamp(storedDust, 0, 200), existingCnt6)}; never below
     *         {@code clamp(storedDust, 0, 200)}
     */
    public static double resolveTransferDust(double storedDust, double existingCnt6) {
        return Math.max(clampDust(storedDust), existingCnt6);
    }

    /**
     * The Sukuna + Fuga identity gate shared by every reward path.
     *
     * @param charId   the active character id
     * @param selectId the selected technique id (rounded before comparison)
     * @return {@code true} iff {@code charId == 1} (Sukuna) and
     *         {@code Math.round(selectId) == 7} (Fuga / Divine Flame Arrow)
     */
    public static boolean isFugaIdentity(int charId, double selectId) {
        return charId == 1 && Math.round(selectId) == 7;
    }

    /**
     * Clamps a raw dust value into {@code [0, 200]}, mapping {@code NaN} to
     * {@code 0}. Inlined here (rather than depending on
     * {@code DustOverlayFormat.clampDust}) so this class is self-contained.
     */
    private static double clampDust(double dust) {
        if (Double.isNaN(dust)) {
            return DUST_MIN;
        }
        if (dust < DUST_MIN) {
            return DUST_MIN;
        }
        if (dust > DUST_MAX) {
            return DUST_MAX;
        }
        return dust;
    }
}
