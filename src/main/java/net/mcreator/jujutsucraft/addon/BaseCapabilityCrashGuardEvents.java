package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", bus=Mod.EventBusSubscriber.Bus.FORGE)
/**
 * Safety event subscriber that blocks server-side heal processing when the base player capability is missing, preventing capability-related crashes in addon-enhanced combat flows.
 */
public class BaseCapabilityCrashGuardEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    /**
     * Handles the living heal callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player player = (Player)livingEntity;
        if (player.level().isClientSide()) {
            return;
        }
        if (!player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).isPresent()) {
            event.setCanceled(true);
        }
    }
}

