package net.mcreator.jujutsucraft.addon.util;

import net.minecraft.world.entity.LivingEntity;

/**
 * Type-safe representation of domain expansion forms.
 *
 * <p>Replaces the scattered {@code int thisForm} / {@code int candidateForm} values
 * used across domain mixins with a proper enum.  The ordinal values are chosen to
 * match the integer encoding already stored in
 * {@code jjkbrp_domain_form_cast_locked} and {@code jjkbrp_domain_form_effective}.</p>
 *
 * <p><b>Phase 1 note:</b> this enum is additive.  Existing mixin code continues
 * to use raw ints internally; this enum provides a canonical mapping for the
 * registry and helper layers being built.</p>
 */
public enum DomainForm {

    /** An incomplete domain that has no full barrier shell. */
    INCOMPLETE(0, "Incomplete"),

    /** A standard closed barrier domain. */
    CLOSED(1, "Closed"),

    /** An open (barrier-less, sure-hit) domain. */
    OPEN(2, "Open");

    private final int id;
    private final String displayName;

    DomainForm(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Returns the integer ID stored in NBT ({@code 0}, {@code 1}, or {@code 2}). */
    public int getId() {
        return this.id;
    }

    /** Returns a human-readable label for HUD / log use. */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Converts an integer form value to the corresponding enum constant.
     *
     * @param id the integer domain form value (0 = incomplete, 1 = closed, 2 = open)
     * @return the matching {@link DomainForm}, defaulting to {@link #CLOSED} for unknown values
     */
    public static DomainForm fromId(int id) {
        return switch (id) {
            case 0 -> INCOMPLETE;
            case 2 -> OPEN;
            default -> CLOSED;
        };
    }

    /**
     * Resolves the current domain form of a living entity by inspecting its
     * persistent data and runtime state, using the same heuristics as the
     * existing {@code DomainAddonUtils} form-detection helpers.
     *
     * @param entity the entity to inspect; may be {@code null}
     * @return the detected {@link DomainForm}, defaulting to {@link #CLOSED}
     */
    public static DomainForm resolve(LivingEntity entity) {
        if (entity == null) {
            return CLOSED;
        }
        if (DomainAddonUtils.isIncompleteDomainState(entity)) {
            return INCOMPLETE;
        }
        if (DomainAddonUtils.isOpenDomainState(entity)) {
            return OPEN;
        }
        return CLOSED;
    }
}
