package net.mcreator.jujutsucraft.addon.logic;

/**
 * Immutable axis-aligned rectangle used as the pure geometry primitive for the
 * Domain Mastery header layout. Coordinates are integer pixels with {@code (x, y)}
 * as the top-left corner, {@code w} the width, and {@code h} the height, matching
 * the screen-space convention used by the addon's client rendering.
 *
 * <p>This type is intentionally free of any Minecraft imports so the header layout
 * geometry can be unit- and property-tested without a running client.</p>
 *
 * @param x left edge (pixels)
 * @param y top edge (pixels)
 * @param w width (pixels)
 * @param h height (pixels)
 */
public record Rect(int x, int y, int w, int h) {

    /**
     * Tests whether this rectangle overlaps another rectangle by a positive area.
     * Rectangles that merely touch along an edge or corner (zero overlap area) are
     * not considered intersecting, so adjacent header elements may share a boundary
     * without overlapping.
     *
     * @param other the rectangle to test against.
     * @return {@code true} if the two rectangles share interior area, {@code false} otherwise.
     */
    public boolean intersects(Rect other) {
        return this.x < other.x + other.w
                && this.x + this.w > other.x
                && this.y < other.y + other.h
                && this.y + this.h > other.y;
    }

    /**
     * Tests whether this rectangle is fully contained within the given bounds.
     * Containment is inclusive of the bounds' edges, so a rectangle flush against a
     * boundary still counts as within it.
     *
     * @param bounds the containing rectangle.
     * @return {@code true} if every point of this rectangle lies inside {@code bounds}, {@code false} otherwise.
     */
    public boolean within(Rect bounds) {
        return this.x >= bounds.x
                && this.y >= bounds.y
                && this.x + this.w <= bounds.x + bounds.w
                && this.y + this.h <= bounds.y + bounds.h;
    }
}
