package net.mcreator.jujutsucraft.addon.clash.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An unordered pair of participant UUIDs used as the key for a {@code ClashSession} in the
 * {@code ClashRegistry}.
 *
 * <p>The canonical constructor normalizes the two UUIDs so that {@code a} is always the "smaller"
 * of the two by {@link UUID#compareTo}. Because the record's auto-generated {@link #equals(Object)}
 * and {@link #hashCode()} work off the normalized components, two pairs constructed with the same
 * set of UUIDs in any order compare equal and hash the same, whether they were created via
 * {@link #of(UUID, UUID)} or via direct {@code new ParticipantPair(x, y)}.
 *
 * <p>Both components must be non-null; constructing a pair with a {@code null} UUID throws
 * {@link NullPointerException}.
 *
 * <p>Requirements: 10.1, 14.1.
 */
public record ParticipantPair(UUID a, UUID b) {

    public ParticipantPair {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.compareTo(b) > 0) {
            UUID tmp = a;
            a = b;
            b = tmp;
        }
    }

    /**
     * Returns a {@code ParticipantPair} whose components are ordered by {@link UUID#compareTo}, so
     * that {@code of(x, y).equals(of(y, x))} for any two UUIDs {@code x} and {@code y}.
     *
     * @param one first participant UUID, must be non-null
     * @param two second participant UUID, must be non-null
     * @return an unordered pair wrapping the two UUIDs
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ParticipantPair of(UUID one, UUID two) {
        return new ParticipantPair(one, two);
    }
}
