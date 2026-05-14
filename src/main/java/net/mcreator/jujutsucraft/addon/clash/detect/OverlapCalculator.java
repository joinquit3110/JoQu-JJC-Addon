package net.mcreator.jujutsucraft.addon.clash.detect;

import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry helpers for the Domain Clash subsystem.
 *
 * <p>{@link #distance(Vec3, Vec3)} delegates to {@link Vec3#distanceTo(Vec3)}, and
 * {@link #overlaps(Vec3, double, Vec3, double)} reports whether two spheres, given by
 * their centers and radii, touch or intersect. The boundary is inclusive: two spheres
 * whose centers are exactly {@code radiusA + radiusB} apart are considered overlapping,
 * matching the semantics required by the clash-detector spec (Requirements 1.2, 1.3, 1.6).
 */
public final class OverlapCalculator {

    private OverlapCalculator() {
        // utility class — not instantiable
    }

    /**
     * Euclidean distance between {@code a} and {@code b}.
     */
    public static double distance(Vec3 a, Vec3 b) {
        return a.distanceTo(b);
    }

    /**
     * Returns {@code true} iff the spheres centered at {@code centerA}/{@code centerB}
     * with radii {@code radiusA}/{@code radiusB} touch or intersect, i.e.
     * {@code distance(centerA, centerB) <= radiusA + radiusB}.
     */
    public static boolean overlaps(Vec3 centerA, double radiusA, Vec3 centerB, double radiusB) {
        return distance(centerA, centerB) <= radiusA + radiusB;
    }
}
