package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.logic.FugaDustLogic;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.OCoolTimeProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side display fix for the base-mod skill HUD Fuga cooldown readout.
 *
 * <p>After the Incomplete Domain Shrine ends, the addon resets and exempts Fuga from its
 * cooldown so Fuga is genuinely usable (the addon skill wheel and the cast path both treat
 * it as ready). However the base-mod skill HUD's generic magic-cooldown number is produced
 * by {@code OCoolTimeProcedure.execute}, which derives the displayed seconds from the
 * {@code UNSTABLE} effect duration first (and {@code COOLDOWN_TIME} otherwise). Because the
 * base mod applies {@code UNSTABLE} as a post-domain debuff, the OG HUD keeps counting down
 * a Fuga cooldown even though Fuga is usable.</p>
 *
 * <p>This mixin suppresses ONLY that displayed number, and only for the exact case where the
 * addon has made Fuga usable: the player is Sukuna ({@code charId == 1}) with Fuga selected
 * ({@code Math.round(selectId) == 7}) and the addon Fuga reward/exemption is active (the
 * {@code jjkbrp_sukuna_fuga_dust_locked_full} reward flag, or the per-cast no-cooldown
 * marker the use-path sets). It does not remove the {@code UNSTABLE} effect (that is a real
 * debuff that must persist), and it does not touch the readout for any other technique or
 * character — when the gate does not hold, the original procedure runs unchanged.</p>
 *
 * <p>This is a pure client-side display override: it changes only the string the HUD draws,
 * never any server state, NBT, or effect.</p>
 */
@Mixin(value = {OCoolTimeProcedure.class}, remap = false)
public class OCoolTimeFugaUsableMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$hideFugaCooldownWhenUsable(Entity entity, CallbackInfoReturnable<String> cir) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int) Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        // Only the Sukuna + Fuga identity is affected; every other technique/character keeps
        // the unmodified base-mod cooldown readout.
        if (!FugaDustLogic.isFugaIdentity(charId, vars.PlayerSelectCurseTechnique)) {
            return;
        }
        // Suppress the displayed number only while the addon has genuinely made Fuga usable:
        // a confirmed persisted dust reward, or the per-cast no-cooldown markers set by the
        // use path. Outside these cases (e.g. an ordinary technique cooldown) the original
        // number is shown.
        boolean fugaUsable = player.getPersistentData().getBoolean("jjkbrp_sukuna_fuga_dust_locked_full")
                || player.getPersistentData().getBoolean("jjkbrp_sukuna_fuga_no_cooldown_casting")
                || player.getPersistentData().getBoolean("jjkbrp_sukuna_fuga_reward_casting");
        if (fugaUsable) {
            // Empty string = no cooldown number drawn for Fuga.
            cir.setReturnValue("");
        }
    }
}
