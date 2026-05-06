package net.mcreator.jujutsucraft.addon.util;

/**
 * Enumerates the six possible clash-type pairings between two domain forms.
 *
 * <p>Each value encodes the ordered form pair so that pressure mechanics can
 * dispatch on type without inspecting raw form integers.  The canonical
 * ordering places the "stronger" or "attacking" form first where asymmetry
 * exists (e.g. open attacks closed in {@link #OPEN_VS_CLOSED}).</p>
 *
 * <p><b>Phase 1 note:</b> this enum is additive and is not yet referenced
 * by production mixin logic.  It will be consumed by {@code ClashSession}
 * once the registry is wired in Phase 2.</p>
 */
public enum ClashType {

    /** Open domain clashes with a closed domain. */
    OPEN_VS_CLOSED("Open vs Closed"),

    /** Two open domains engage in mutual sure-hit pressure. */
    OPEN_VS_OPEN("Open vs Open"),

    /** Open domain clashes with an incomplete domain. */
    OPEN_VS_INCOMPLETE("Open vs Incomplete"),

    /** Two closed domains apply mutual barrier pressure. */
    CLOSED_VS_CLOSED("Closed vs Closed"),

    /** Closed domain clashes with an incomplete domain. */
    CLOSED_VS_INCOMPLETE("Closed vs Incomplete"),

    /** Two incomplete domains clash. */
    INCOMPLETE_VS_INCOMPLETE("Incomplete vs Incomplete");

    private final String displayName;

    ClashType(String displayName) {
        this.displayName = displayName;
    }

    /** Returns a human-readable label for logging and HUD use. */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Derives the clash type from the two participant forms.
     *
     * <p>Order is normalized so that e.g. {@code (CLOSED, OPEN)} produces
     * {@link #OPEN_VS_CLOSED} rather than requiring the caller to know the
     * canonical ordering.</p>
     *
     * @param formA the domain form of participant A
     * @param formB the domain form of participant B
     * @return the derived {@link ClashType}
     */
    public static ClashType derive(DomainForm formA, DomainForm formB) {
        // Normalize so the higher-ordinal form comes first for consistent matching.
        DomainForm hi = formA.getId() >= formB.getId() ? formA : formB;
        DomainForm lo = formA.getId() >= formB.getId() ? formB : formA;

        if (hi == DomainForm.OPEN && lo == DomainForm.CLOSED) {
            return OPEN_VS_CLOSED;
        }
        if (hi == DomainForm.OPEN && lo == DomainForm.OPEN) {
            return OPEN_VS_OPEN;
        }
        if (hi == DomainForm.OPEN && lo == DomainForm.INCOMPLETE) {
            return OPEN_VS_INCOMPLETE;
        }
        if (hi == DomainForm.CLOSED && lo == DomainForm.CLOSED) {
            return CLOSED_VS_CLOSED;
        }
        if (hi == DomainForm.CLOSED && lo == DomainForm.INCOMPLETE) {
            return CLOSED_VS_INCOMPLETE;
        }
        // INCOMPLETE vs INCOMPLETE
        return INCOMPLETE_VS_INCOMPLETE;
    }
}
