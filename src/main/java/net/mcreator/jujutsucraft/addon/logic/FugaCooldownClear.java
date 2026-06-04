package net.mcreator.jujutsucraft.addon.logic;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Pure, Minecraft-free model of the Fuga (Divine Flame Arrow) cooldown clear.
 *
 * <p>When the Incomplete Domain Shrine ends with surehit and Fuga unused, the addon
 * must make Fuga read as usable in both the base-mod skill UI and the addon HUD. The
 * skill UIs derive the Fuga cooldown indicator from two kinds of state:
 * <ul>
 *   <li><b>Cooldown effect entries</b> that, while present, show Fuga on cooldown:
 *       {@link #EXTENSION_COOLDOWN_7}, {@link #FUGA_COOLDOWN}, and
 *       {@link #COOLDOWN_TIME}. These must be <em>removed</em> entirely.</li>
 *   <li><b>Cooldown-max values</b> that drive the cooldown ring fill amount:
 *       {@link #TECHNIQUE_CD_MAX}, {@link #COMBAT_CD_MAX}, and {@link #CD_MAX_1_7}.
 *       A stale non-zero value here renders a full-but-stuck ring even after the
 *       effects are gone, so these must be <em>zeroed</em> (when present).</li>
 * </ul>
 *
 * <p>This class isolates that decision as a pure function over a
 * {@code Map<String,Integer>} of technique cooldown effect durations and cd-max
 * values, so the high-risk "clear only Fuga, leave every other technique untouched,
 * and stay cleared under repeated (per-tick, Unstable-window) application" contract
 * can be unit- and property-tested without a running client or server (design
 * Property 10; Requirements 4.3, 4.4, 4.5).
 *
 * <p><b>Purity.</b> {@link #clear(Map)} never mutates its argument; it returns a new
 * {@code LinkedHashMap}. <b>Idempotence.</b> {@code clear(clear(m))} equals
 * {@code clear(m)}: removed keys stay removed and zeroed keys stay zero, so folding
 * the clear over any number of ticks keeps Fuga cleared for the full Unstable window.
 */
public final class FugaCooldownClear {

    /**
     * The {@code jujutsucraft_acerycd:extension_cooldown_7} per-skill Fuga cooldown
     * effect read by the addon wheel/HUD payload. Removed on clear.
     */
    public static final String EXTENSION_COOLDOWN_7 = "jujutsucraft_acerycd:extension_cooldown_7";

    /**
     * The {@code jujutsucraft_plus:fuga} per-skill Fuga cooldown effect read by the
     * addon wheel/HUD payload. Removed on clear.
     */
    public static final String FUGA_COOLDOWN = "jujutsucraft_plus:fuga";

    /**
     * The technique {@code COOLDOWN_TIME} effect read by the base-mod
     * {@code LogicCooldownMagicOnlyProcedure} / {@code OCoolTimeProcedure}. Removed on clear.
     */
    public static final String COOLDOWN_TIME = "COOLDOWN_TIME";

    /**
     * The {@code jjkbrp_technique_cd_max} peak technique-cooldown value the skill UI reads
     * to render the cooldown ring fill. Zeroed on clear (when present).
     */
    public static final String TECHNIQUE_CD_MAX = "jjkbrp_technique_cd_max";

    /**
     * The {@code jjkbrp_combat_cd_max} peak combat-cooldown value the skill UI reads for the
     * ring fill. Zeroed on clear (when present).
     */
    public static final String COMBAT_CD_MAX = "jjkbrp_combat_cd_max";

    /**
     * The {@code jjkbrp_cd_max_1_7} per-skill cooldown-max for Sukuna (charId 1) Fuga
     * (select 7) read by {@code buildWheelEntries}. Zeroed on clear (when present).
     */
    public static final String CD_MAX_1_7 = "jjkbrp_cd_max_1_7";

    /**
     * The three cooldown-effect keys that are removed entirely on clear. Unmodifiable,
     * iteration-order-stable.
     */
    public static final Set<String> REMOVED_EFFECT_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(EXTENSION_COOLDOWN_7, FUGA_COOLDOWN, COOLDOWN_TIME)));

    /**
     * The three cooldown-max keys that are zeroed (only when present) on clear.
     * Unmodifiable, iteration-order-stable.
     */
    public static final Set<String> ZEROED_CD_MAX_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(TECHNIQUE_CD_MAX, COMBAT_CD_MAX, CD_MAX_1_7)));

    private FugaCooldownClear() {
        // utility class; no instances
    }

    /**
     * Computes the Fuga cooldown clear over a snapshot of technique cooldown effect
     * durations and cd-max values.
     *
     * <p>The result is a new map where:
     * <ul>
     *   <li>each of {@link #REMOVED_EFFECT_KEYS} is removed entirely;</li>
     *   <li>each of {@link #ZEROED_CD_MAX_KEYS} that is <em>present</em> in the input is
     *       set to {@code 0} (absent cd-max keys are left absent — "zeroes only" the
     *       present ones);</li>
     *   <li>every other entry is copied through unchanged.</li>
     * </ul>
     *
     * <p>The input map is never mutated. A {@code null} input is treated as an empty map
     * and yields an empty result. The returned map preserves the input's iteration order
     * for the entries it keeps.
     *
     * <p>This operation is idempotent: applying it to its own output yields an equal map,
     * because the removed keys are already absent and the zeroed keys already hold
     * {@code 0}. Folding it over any number of ticks therefore keeps Fuga cleared for the
     * full Unstable window without affecting any other technique's entries.
     *
     * @param cooldowns a snapshot of technique cooldown effect durations and cd-max values
     *                  (may be {@code null})
     * @return a new map with the Fuga cooldown cleared per the rules above
     */
    public static Map<String, Integer> clear(Map<String, Integer> cooldowns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (cooldowns == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            String key = entry.getKey();
            if (REMOVED_EFFECT_KEYS.contains(key)) {
                // Drop the cooldown-effect entry entirely so the skill UI stops showing
                // Fuga on cooldown.
                continue;
            }
            if (ZEROED_CD_MAX_KEYS.contains(key)) {
                // Zero the cd-max so the cooldown ring renders empty (no stuck ring).
                result.put(key, 0);
                continue;
            }
            // Any other technique's effect or cd-max value passes through untouched.
            result.put(key, entry.getValue());
        }
        return result;
    }
}
