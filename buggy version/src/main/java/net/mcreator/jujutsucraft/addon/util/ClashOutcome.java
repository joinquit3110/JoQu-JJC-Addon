package net.mcreator.jujutsucraft.addon.util;

/**
 * Possible outcomes for a single pairwise {@code ClashSession}.
 *
 * <p>During Phase 1 this enum is defined but not yet consumed by production
 * code.  Phase 2+ will use it inside the registry's session-resolution logic.</p>
 */
public enum ClashOutcome {

    /** The clash is still in progress — no outcome determined yet. */
    PENDING,

    /** Participant A won the clash. */
    A_WINS,

    /** Participant B won the clash. */
    B_WINS,

    /** Both participants collapsed within the tie window. */
    TIE,

    /** The clash expired without a decisive result (e.g. one domain despawned). */
    EXPIRED
}
