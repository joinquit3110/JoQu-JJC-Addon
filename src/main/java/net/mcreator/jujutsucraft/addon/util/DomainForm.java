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
 *
 * <p><b>Clash-system extension:</b> in addition to the NBT-encoded {@link #getId() id}
 * (0 / 1 / 2), each constant also exposes its corresponding base-mod
 * {@code DOMAIN_EXPANSION} mob-effect {@link #amplifierValue amplifier value}
 * (-1 / 0 / 1) and the {@code Form_Factor} used by the Domain Clash
 * {@code Clash_Power} formula. See the {@code domain-clash-system} design document.</p>
 */
public enum DomainForm {

    /** An incomplete domain that has no full barrier shell. */
    INCOMPLETE(0, "Incomplete", -1, 0.95D),

    /** A standard closed barrier domain. */
    CLOSED(1, "Closed", 0, 1.00D),

    /** An open (barrier-less, sure-hit) domain. */
    OPEN(2, "Open", 1, 1.15D);

    private final int id;
    private final String displayName;

    /**
     * The base-mod {@code DOMAIN_EXPANSION} mob-effect amplifier value that
     * corresponds to this form ({@code -1} for INCOMPLETE, {@code 0} for CLOSED,
     * {@code 1} for OPEN). This is distinct from {@link #id}, which is the
     * NBT encoding stored by the addon.
     */
    public final int amplifierValue;

    /**
     * The {@code Form_Factor} used by the Domain Clash {@code Clash_Power}
     * formula: {@code 0.95} for INCOMPLETE, {@code 1.00} for CLOSED,
     * {@code 1.15} for OPEN.
     */
    public final double formFactor;

    DomainForm(int id, String displayName, int amplifierValue, double formFactor) {
        this.id = id;
        this.displayName = displayName;
        this.amplifierValue = amplifierValue;
        this.formFactor = formFactor;
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
     * Converts a base-mod {@code DOMAIN_EXPANSION} mob-effect amplifier value to the
     * corresponding {@link DomainForm}.
     *
     * <p>Mapping:
     * <ul>
     *   <li>{@code -1} &rarr; {@link #INCOMPLETE}</li>
     *   <li>{@code  0} &rarr; {@link #CLOSED}</li>
     *   <li>{@code  1} &rarr; {@link #OPEN}</li>
     * </ul>
     * Any other amplifier value falls back to {@link #CLOSED}.</p>
     *
     * @param amplifier the {@code DOMAIN_EXPANSION} mob-effect amplifier
     * @return the matching {@link DomainForm}, defaulting to {@link #CLOSED}
     */
    public static DomainForm fromAmplifier(int amplifier) {
        return switch (amplifier) {
            case -1 -> INCOMPLETE;
            case 1 -> OPEN;
            case 0 -> CLOSED;
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
