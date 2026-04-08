package net.mcreator.jujutsucraft.addon;

import java.util.HashMap;
import java.util.Map;

/**
 * Static policy table describing how each domain id should behave across incomplete, closed, and open forms. The addon uses these policies to apply archetypes, penalties, and scaling multipliers consistently.
 */
public final class DomainFormPolicy {
    private static final Map<Integer, Policy> POLICIES = new HashMap<Integer, Policy>();

    /**
     * Performs put for this addon component.
     * @param domainId identifier used to resolve the requested entry or state.
     * @param type type used by this method.
     * @param openAllowed open allowed used by this method.
     * @param incompletePenaltyPerTick tick-based timing value used by this operation.
     * @param openRangeMultiplier open range multiplier used by this method.
     * @param openSureHitMultiplier open sure hit multiplier used by this method.
     * @param openCeDrainMultiplier open ce drain multiplier used by this method.
     * @param openDurationMultiplier open duration multiplier used by this method.
     * @param barrierRefinement barrier refinement used by this method.
     */
    private static void put(int domainId, Archetype type, boolean openAllowed, double incompletePenaltyPerTick, double openRangeMultiplier, double openSureHitMultiplier, double openCeDrainMultiplier, double openDurationMultiplier, double barrierRefinement) {
        POLICIES.put(domainId, new Policy(type, openAllowed, incompletePenaltyPerTick, openRangeMultiplier, openSureHitMultiplier, openCeDrainMultiplier, openDurationMultiplier, barrierRefinement));
    }

    /**
     * Performs policy of for this addon component.
     * @param rawDomainId identifier used to resolve the requested entry or state.
     * @return the resulting policy of value.
     */
    public static Policy policyOf(double rawDomainId) {
        int domainId = (int)Math.round(rawDomainId);
        Policy p = POLICIES.get(domainId);
        if (p != null) {
            return p;
        }
        return new Policy(Archetype.SPECIAL, false, 0.01, 12.0, 0.9, 1.1, 1.0, 0.5);
    }

    /**
     * Creates a new domain form policy instance and initializes its addon state.
     */
    private DomainFormPolicy() {
    }

    static {
        DomainFormPolicy.put(1, Archetype.REFINED, true, 0.005, 18.0, 1.0, 1.25, 0.95, 0.95);
        DomainFormPolicy.put(2, Archetype.REFINED, true, 0.005, 16.0, 0.95, 1.3, 0.9, 0.95);
        DomainFormPolicy.put(18, Archetype.REFINED, true, 0.005, 17.0, 1.0, 1.2, 0.95, 0.9);
        DomainFormPolicy.put(29, Archetype.REFINED, true, 0.005, 16.0, 0.95, 1.35, 0.9, 0.7);
        DomainFormPolicy.put(15, Archetype.CONTROL, true, 0.015, 14.0, 0.9, 1.15, 1.0, 0.55);
        DomainFormPolicy.put(27, Archetype.CONTROL, false, 0.01, 12.0, 0.85, 1.1, 1.0, 0.65);
        DomainFormPolicy.put(24, Archetype.CONTROL, false, 0.01, 12.5, 0.9, 1.1, 1.0, 0.6);
        DomainFormPolicy.put(36, Archetype.CONTROL, false, 0.012, 12.5, 0.9, 1.1, 1.0, 0.6);
        DomainFormPolicy.put(6, Archetype.SUMMON, false, 0.02, 13.0, 0.85, 1.05, 1.05, 0.3);
        DomainFormPolicy.put(21, Archetype.SUMMON, false, 0.02, 13.0, 0.85, 1.05, 1.05, 0.3);
        DomainFormPolicy.put(23, Archetype.SUMMON, false, 0.018, 13.5, 0.88, 1.08, 1.05, 0.35);
        DomainFormPolicy.put(35, Archetype.SUMMON, false, 0.016, 13.0, 0.88, 1.08, 1.05, 0.35);
        DomainFormPolicy.put(4, Archetype.AOE, true, 0.008, 15.0, 1.05, 1.3, 0.9, 0.65);
        DomainFormPolicy.put(5, Archetype.AOE, true, 0.008, 14.5, 1.05, 1.3, 0.9, 0.65);
        DomainFormPolicy.put(8, Archetype.AOE, true, 0.009, 14.5, 1.0, 1.25, 0.92, 0.6);
        DomainFormPolicy.put(10, Archetype.AOE, true, 0.009, 14.0, 1.0, 1.2, 0.95, 0.6);
        DomainFormPolicy.put(40, Archetype.AOE, false, 0.012, 13.5, 0.92, 1.15, 0.98, 0.55);
        DomainFormPolicy.put(7, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(9, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(11, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(13, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(14, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(19, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(20, Archetype.UTILITY, false, 0.02, 13.0, 0.9, 1.08, 1.05, 0.4);
        DomainFormPolicy.put(25, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(26, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        DomainFormPolicy.put(43, Archetype.UTILITY, false, 0.011, 13.0, 0.95, 1.1, 1.0, 0.5);
        for (int id = 1; id <= 50; ++id) {
            POLICIES.putIfAbsent(id, new Policy(Archetype.SPECIAL, false, 0.01, 12.0, 0.9, 1.1, 1.0, 0.5));
        }
    }

    /**
     * Immutable per-domain policy record containing archetype, open-form allowance, and all form scaling multipliers.
     */
    public record Policy(Archetype archetype, boolean openAllowed, double incompletePenaltyPerTick, double openRangeMultiplier, double openSureHitMultiplier, double openCeDrainMultiplier, double openDurationMultiplier, double barrierRefinement) {
    }

    /**
     * High-level category used to group domains with similar tuning and balance expectations.
     */
    public static enum Archetype {
        /** High-refinement domains that support strong barrier control and usually better open-form performance. */
        REFINED,
        /** Domains focused on positioning, area control, and utility pressure. */
        CONTROL,
        /** Domains built around summoned allies or shikigami support. */
        SUMMON,
        /** Domains that emphasize broad area damage and overwhelming field pressure. */
        AOE,
        /** Domains with support-heavy or specialized effects rather than raw refinement. */
        UTILITY,
        /** Fallback archetype used when a domain does not fit one of the specialized categories. */
        SPECIAL;

    }
}

