package net.mcreator.jujutsucraft.addon;

/**
 * Client-side event hub for the JJK Blue/Red/Purple addon.
 *
 * <h2>Key bindings</h2>
 * Registers the TAB key as the skill wheel toggle and handles both press
 * (open) and release (close + confirm) events.
 *
 * <h2>Screen-level movement blocking</h2>
 * When the skill wheel is open, forwards key events are re-routed through
 * {@link SkillWheelScreen} via the client tick so that movement keys do not
 * affect the player while selecting a technique.
 *
 * <h2>ModBus layer</h2>
 * {@link ModBusEvents} (separate registration via {@code @Mod.EventBusSubscriber Bus.MOD})
 * registers the key mapping and all entity renderers/layers:
 * <ul>
 *   <li>{@link LimbRegrowthLayer} — bone/muscle/flesh regrowth overlay on players</li>
 *   <li>{@link SeveredLimbRenderer} — severed limb dropped entities</li>
 * </ul>
 */

import com.mojang.blaze3d.platform.InputConstants;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.limb.LimbRegrowthLayer;
import net.mcreator.jujutsucraft.addon.limb.SeveredLimbRenderer;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {
    private static KeyMapping SKILL_WHEEL_KEY;
    private static boolean wasKeyDown;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        NearDeathClientState.clientTick();

        if (mc.screen instanceof SkillWheelScreen) {
            long window = mc.getWindow().getWindow();
            tickMovementKey(mc.options.keyUp, window);
            tickMovementKey(mc.options.keyDown, window);
            tickMovementKey(mc.options.keyLeft, window);
            tickMovementKey(mc.options.keyRight, window);
            tickMovementKey(mc.options.keyShift, window);
            tickMovementKey(mc.options.keySprint, window);
        }
    }

    private static void tickMovementKey(KeyMapping key, long window) {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        boolean isDown = isWheelKeyDown();
        if (isDown && !wasKeyDown) {
            openWheel(mc);
        }
        wasKeyDown = isDown;
    }

    private static void openWheel(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.screen instanceof SkillWheelScreen) return;

        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
            .orElse(new JujutsucraftModVariables.PlayerVariables());
        if (vars.noChangeTechnique) return;

        ModNetworking.CHANNEL.sendToServer(new ModNetworking.RequestWheelPacket());
    }

    private static boolean isWheelKeyDown() {
        if (SKILL_WHEEL_KEY == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = SKILL_WHEEL_KEY.getKey();
        return InputConstants.isKeyDown(window, key.getValue());
    }

    public static boolean isWheelKey(int keyCode, int scanCode) {
        if (SKILL_WHEEL_KEY == null) return false;
        return SKILL_WHEEL_KEY.getKey().getValue() == keyCode;
    }

    @Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            SKILL_WHEEL_KEY = new KeyMapping("key.jjkblueredpurple.skill_wheel",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, 258, "key.categories.jjkblueredpurple");
            event.register(SKILL_WHEEL_KEY);
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(LimbEntityRegistry.SEVERED_LIMB.get(), SeveredLimbRenderer::new);
        }

        @SuppressWarnings("unchecked")
        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skinName : event.getSkins()) {
                var renderer = event.getSkin(skinName);
                if (renderer == null) continue;
                var livingRenderer = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer;
                livingRenderer.addLayer(new LimbRegrowthLayer(livingRenderer));
            }
        }
    }
}
