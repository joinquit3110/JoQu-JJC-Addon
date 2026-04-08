package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.world.level.LevelAccessor;

/**
 * Utility helpers for temporarily overriding the shared domain radius during barrier placement.
 *
 * <p>This class uses a {@link ThreadLocal} to remember the original scaled radius while barrier
 * mixins and related placement code suppress the global map radius. That prevents sphere-building
 * logic from reading the wrong radius value during intermediate block placement steps.</p>
 */
public final class DomainRadiusUtils {
    /**
     * Per-thread storage for the original radius captured before temporary suppression is applied.
     *
     * <p>Thread-local state is used so nested or parallel world logic does not leak radius values
     * across unrelated execution contexts.</p>
     */
    private static final ThreadLocal<Double> SCALED_CTX_ORIGINAL = new ThreadLocal();

    /**
     * Private constructor for a pure utility class.
     *
     * <p>This prevents accidental instantiation because all behavior is exposed through static
     * helper methods.</p>
     */
    private DomainRadiusUtils() {
    }

    /**
     * Records the original radius after scaling has been applied.
     *
     * <p>Later suppression calls can use this saved value to temporarily restore a smaller block
     * placement radius before the scaled radius is restored again.</p>
     *
     * @param world the world context associated with the scaling event
     * @param originalRadius the unscaled or pre-suppression radius to preserve for later use
     */
    public static void onScalingApplied(LevelAccessor world, double originalRadius) {
        // Store the pre-suppression radius for the current execution thread only.
        SCALED_CTX_ORIGINAL.set(originalRadius);
    }

    /**
     * Returns the original radius saved for the current scaling context.
     *
     * @return the saved original radius for this thread, or {@code null} if none is active
     */
    public static Double getOriginalRadiusIfScaling() {
        return SCALED_CTX_ORIGINAL.get();
    }

    /**
     * Clears the current thread's temporary scaling context.
     *
     * <p>This should be called when the placement flow is fully complete so stale radius state does
     * not affect later operations on the same thread.</p>
     */
    public static void clearScalingContext() {
        SCALED_CTX_ORIGINAL.remove();
    }

    /**
     * Temporarily suppresses the global block-placement radius for the current world operation.
     *
     * <p>If a saved original radius exists, the shared map variable is set back to that original
     * value so block placement logic does not use the already-scaled visual/gameplay radius.</p>
     *
     * @param world the world whose shared domain radius should be temporarily reduced
     * @return the original saved radius, or {@code null} if no scaling context is active
     */
    public static Double suppressForBlock(LevelAccessor world) {
        Double original = DomainRadiusUtils.getOriginalRadiusIfScaling();
        if (original != null) {
            try {
                // Restore the original radius while individual barrier blocks are being placed.
                JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius = original;
            }
            catch (Exception exception) {
                // Ignore map-variable access failures because suppression is only a best-effort guard.
            }
        }
        return original;
    }

    /**
     * Restores the shared domain radius after temporary suppression completes.
     *
     * <p>The original radius is re-scaled by the provided multiplier so the world returns to the
     * expected scaled radius once block placement is finished.</p>
     *
     * @param world the world whose shared domain radius should be restored
     * @param original the original saved radius returned by {@link #suppressForBlock(LevelAccessor)}
     * @param radiusMultiplier the multiplier used to rebuild the final active radius
     */
    public static void restoreAfterSuppressed(LevelAccessor world, Double original, double radiusMultiplier) {
        if (original != null) {
            try {
                // Clamp the multiplier to at least 1.0 so restoration never shrinks below the original.
                JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius = original * Math.max(1.0, radiusMultiplier);
            }
            catch (Exception exception) {
                // Ignore map-variable access failures because the caller cannot recover here anyway.
            }
        }
    }

    /**
     * Legacy overload that restores the radius without applying any extra multiplier.
     *
     * @param world the world whose shared domain radius should be restored
     * @param original the original saved radius returned by {@link #suppressForBlock(LevelAccessor)}
     */
    @Deprecated
    public static void restoreAfterSuppressed(LevelAccessor world, Double original) {
        DomainRadiusUtils.restoreAfterSuppressed(world, original, 1.0);
    }
}
