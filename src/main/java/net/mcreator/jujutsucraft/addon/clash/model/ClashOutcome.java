package net.mcreator.jujutsucraft.addon.clash.model;

import javax.annotation.Nullable;

/**
 * Terminal state of a {@code ClashSession}.
 *
 * <p>The four real values describe how a session ended:
 * <ul>
 *   <li>{@link #WINNER_A} — {@code casterA} won the clash.</li>
 *   <li>{@link #WINNER_B} — {@code casterB} won the clash.</li>
 *   <li>{@link #TIE} — the power difference fell below the tie threshold and both casters are
 *       treated as losers.</li>
 *   <li>{@link #CANCELLED} — the session was cancelled (overlap lost, dimension change,
 *       {@code DOMAIN_EXPANSION} removed, invalid reference, stale, or server shutdown) and no
 *       loser effects were applied.</li>
 * </ul>
 *
 * <p>The {@link DomainClashHudSnapshotPacket} additionally needs to carry the "in-progress" state
 * (no outcome yet). That is represented by a Java {@code null} rather than a fifth enum value so
 * that the packet field can stay a plain {@code @Nullable ClashOutcome}. The packet encoding uses
 * a single unsigned byte with the mapping:
 *
 * <pre>
 *   null      -&gt; 0
 *   WINNER_A  -&gt; 1
 *   WINNER_B  -&gt; 2
 *   TIE       -&gt; 3
 *   CANCELLED -&gt; 4
 * </pre>
 *
 * <p>Requirements: 5.2, 5.3, 11.4, 14.2.
 */
public enum ClashOutcome {
    WINNER_A,
    WINNER_B,
    TIE,
    CANCELLED;

    /**
     * Returns the packet-encoding byte for this outcome. The result is always in the range
     * {@code 1..4}; the {@code 0} value is reserved for the {@code null} "in-progress" state and
     * is produced only by {@link #toByte(ClashOutcome)} when passed {@code null}.
     *
     * @return {@code 1} for {@link #WINNER_A}, {@code 2} for {@link #WINNER_B}, {@code 3} for
     *     {@link #TIE}, or {@code 4} for {@link #CANCELLED}
     */
    public byte toByte() {
        return switch (this) {
            case WINNER_A -> (byte) 1;
            case WINNER_B -> (byte) 2;
            case TIE -> (byte) 3;
            case CANCELLED -> (byte) 4;
        };
    }

    /**
     * Null-safe packet-encoding helper. Returns {@code 0} when {@code outcome} is {@code null} and
     * otherwise delegates to {@link #toByte()}.
     *
     * @param outcome the outcome to encode, possibly {@code null} to signal an in-progress session
     * @return {@code 0} for {@code null}, {@code 1..4} for the four real outcome values
     */
    public static byte toByte(@Nullable ClashOutcome outcome) {
        return outcome == null ? (byte) 0 : outcome.toByte();
    }

    /**
     * Decodes the packet-encoding byte back to an outcome. {@code 0} decodes to {@code null}
     * (in-progress session); any other byte outside the range {@code 0..4} throws
     * {@link IllegalArgumentException} so malformed packets fail loudly on the receiving side.
     *
     * @param b the encoded byte
     * @return the corresponding outcome, or {@code null} when {@code b == 0}
     * @throws IllegalArgumentException if {@code b} is not in the range {@code 0..4}
     */
    @Nullable
    public static ClashOutcome fromByte(byte b) {
        return switch (b) {
            case 0 -> null;
            case 1 -> WINNER_A;
            case 2 -> WINNER_B;
            case 3 -> TIE;
            case 4 -> CANCELLED;
            default -> throw new IllegalArgumentException("Unknown ClashOutcome byte: " + b);
        };
    }
}
