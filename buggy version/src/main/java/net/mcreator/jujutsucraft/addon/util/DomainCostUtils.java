package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.init.JujutsucraftModItems;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Utility helpers for calculating the cursed energy cost of casting a domain.
 *
 * <p>This class centralizes the addon-side rules that decide whether the domain technique is the
 * currently selected skill, which domain form should be treated as active, and how various status
 * effects or held items modify the final cast cost.</p>
 */
public final class DomainCostUtils {
    /**
     * Private constructor for a pure utility class.
     *
     * <p>This prevents accidental instantiation because all behavior is exposed through static
     * helper methods.</p>
     */
    private DomainCostUtils() {
    }

    /**
     * Checks whether the player's currently selected curse technique is the domain slot.
     *
     * <p>Slot {@code 20} is treated as the domain technique selection in this addon.</p>
     *
     * @param vars the synced player variables from the original mod
     * @return {@code true} if the selected technique slot is the domain slot
     */
    public static boolean isDomainTechniqueSelected(JujutsucraftModVariables.PlayerVariables vars) {
        if (vars == null) {
            return false;
        }

        // The original technique selector is stored as a floating-point value, so round it first.
        return Math.round(vars.PlayerSelectCurseTechnique) == 20L;
    }

    /**
     * Resolves the effective domain form currently assigned to the player.
     *
     * <p>The addon first prefers the runtime-effective form stored in persistent data, then falls
     * back to the domain mastery capability selection if the runtime value has not been written yet.</p>
     *
     * @param player the player whose effective form should be resolved
     * @return the resolved form id, where incomplete is {@code 0}, closed is {@code 1}, and open is {@code 2}
     */
    public static int resolveEffectiveForm(Player player) {
        CompoundTag nbt = player.getPersistentData();
        if (nbt.contains("jjkbrp_domain_form_effective")) {
            return nbt.getInt("jjkbrp_domain_form_effective");
        }

        // Capability data is the fallback source when the runtime-effective value is absent.
        return player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).map(data -> data.getDomainTypeSelected()).orElse(0);
    }

    /**
     * Returns the cursed energy multiplier associated with a domain form.
     *
     * <p>Incomplete domains are intentionally cheaper, standard closed domains use the baseline
     * multiplier, and open domains are the most expensive form.</p>
     *
     * @param form the resolved domain form id
     * @return the cost multiplier for that form
     */
    public static double formMultiplier(int form) {
        return switch (form) {
            case 2 -> 1.6;
            case 1 -> 1.0;
            default -> 0.55;
        };
    }

    /**
     * Resolves the base technique cost before the domain form multiplier is applied.
     *
     * <p>This method incorporates gameplay modifiers from status effects and special held items.
     * It mirrors the original mod's cost conventions while layering addon-aware domain behavior on
     * top of them.</p>
     *
     * @param player the player attempting to cast the technique
     * @param vars the synced player variables containing the original technique cost
     * @return the effective base cost, clamped to zero or higher
     */
    public static double resolveTechniqueBaseCost(Player player, JujutsucraftModVariables.PlayerVariables vars) {
        ItemStack hand;
        int amp;
        if (player == null || vars == null) {
            return 0.0;
        }
        double cost = vars.PlayerSelectCurseTechniqueCostOrgin;

        // STAR_RAGE increases the cost of physical attacks outside a successful active domain state.
        if (player.hasEffect((MobEffect)JujutsucraftModMobEffects.STAR_RAGE.get()) && vars.PhysicalAttack && (!player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) || player.getPersistentData().getBoolean("Failed"))) {
            amp = player.getEffect((MobEffect)JujutsucraftModMobEffects.STAR_RAGE.get()).getAmplifier();
            cost = Math.round(cost + 10.0 + 9.0 * (double)(amp + 1));
        }

        // SUKUNA_EFFECT halves the final base cost before form scaling is applied.
        if (player.hasEffect((MobEffect)JujutsucraftModMobEffects.SUKUNA_EFFECT.get())) {
            cost = Math.round(cost * 0.5);
        }

        // SIX_EYES dramatically reduces cost exponentially based on amplifier level.
        if (player.hasEffect((MobEffect)JujutsucraftModMobEffects.SIX_EYES.get())) {
            amp = player.getEffect((MobEffect)JujutsucraftModMobEffects.SIX_EYES.get()).getAmplifier();
            cost = Math.round(cost * Math.pow(0.1, amp + 1));
        }

        // An unused loudspeaker forces the technique cost to zero for this cast.
        if ((hand = player.getMainHandItem()).getItem() == JujutsucraftModItems.LOUDSPEAKER.get() && !hand.getOrCreateTag().getBoolean("Used")) {
            cost = 0.0;
        }
        return Math.max(0.0, cost);
    }

    /**
     * Resolves the full expected cursed energy cost to cast the domain.
     *
     * <p>The final value is the base technique cost multiplied by the effective domain form's cost
     * factor.</p>
     *
     * @param player the player attempting to cast the domain
     * @param vars the synced player variables containing the original technique cost
     * @return the final expected cast cost, clamped to zero or higher
     */
    public static double resolveExpectedDomainCastCost(Player player, JujutsucraftModVariables.PlayerVariables vars) {
        double baseCost = DomainCostUtils.resolveTechniqueBaseCost(player, vars);
        return Math.max(0.0, baseCost * DomainCostUtils.formMultiplier(DomainCostUtils.resolveEffectiveForm(player)));
    }
}
