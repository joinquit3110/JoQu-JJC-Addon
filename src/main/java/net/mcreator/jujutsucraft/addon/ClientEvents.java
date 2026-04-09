package net.mcreator.jujutsucraft.addon;

import com.mojang.blaze3d.platform.InputConstants;
import net.mcreator.jujutsucraft.addon.DomainMasteryScreen;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.NearDeathClientState;
import net.mcreator.jujutsucraft.addon.SkillWheelScreen;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.limb.LimbRegrowthLayer;
import net.mcreator.jujutsucraft.addon.limb.SeveredLimbRenderer;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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

@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.FORGE)
/**
 * Client-only Forge event handler that keeps addon client state updated, opens custom screens from key bindings, and registers custom entity render layers.
 */
public class ClientEvents {
    // Key mapping used to request and open the radial technique wheel.
    private static KeyMapping SKILL_WHEEL_KEY;
    // Key mapping used to open the addon domain mastery screen.
    private static KeyMapping DOMAIN_MASTERY_KEY;
    // Previous frame state for the wheel key so the screen only opens on a fresh press.
    private static boolean wasKeyDown;
    // Previous frame state for the domain mastery key so the screen only opens on a fresh press.
    private static boolean wasDMKeyDown;

    @SubscribeEvent
    /**
     * Handles the client tick callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        NearDeathClientState.clientTick();
        if (mc.screen instanceof SkillWheelScreen) {
            long window = mc.getWindow().getWindow();
            ClientEvents.tickMovementKey(mc.options.keyUp, window);
            ClientEvents.tickMovementKey(mc.options.keyDown, window);
            ClientEvents.tickMovementKey(mc.options.keyLeft, window);
            ClientEvents.tickMovementKey(mc.options.keyRight, window);
            ClientEvents.tickMovementKey(mc.options.keyShift, window);
            ClientEvents.tickMovementKey(mc.options.keySprint, window);
        }
    }

    /**
     * Advances movement key by one tick.
     * @param key key used by this method.
     * @param window window used by this method.
     */
    private static void tickMovementKey(KeyMapping key, long window) {
    }

    @SubscribeEvent
    /**
     * Handles the key input callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (mc.screen != null) {
            // While a GUI is open (notably chat), consume key state only for edge tracking and never open gameplay menus.
            wasKeyDown = ClientEvents.isWheelKeyDown();
            wasDMKeyDown = ClientEvents.isDomainMasteryKeyDown();
            return;
        }
        boolean isDown = ClientEvents.isWheelKeyDown();
        if (isDown && !wasKeyDown) {
            ClientEvents.openWheel(mc);
        }
        wasKeyDown = isDown;
        boolean isDMDown = ClientEvents.isDomainMasteryKeyDown();
        if (isDMDown && !wasDMKeyDown) {
            ClientEvents.openDomainMastery(mc);
        }
        wasDMKeyDown = isDMDown;
    }

    /**
     * Opens wheel on the appropriate client screen or workflow.
     * @param mc mc used by this method.
     */
    private static void openWheel(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (mc.screen instanceof SkillWheelScreen) {
            return;
        }
        if (mc.screen instanceof DomainMasteryScreen) {
            return;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (vars.noChangeTechnique) {
            return;
        }
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.RequestWheelPacket());
    }

    /**
     * Opens domain mastery on the appropriate client screen or workflow.
     * @param mc mc used by this method.
     */
    private static void openDomainMastery(Minecraft mc) {
        if (mc.screen != null) {
            return;
        }
        if (mc.screen instanceof DomainMasteryScreen) {
            return;
        }
        if (mc.screen instanceof SkillWheelScreen) {
            return;
        }
        ModNetworking.CHANNEL.sendToServer((Object)new ModNetworking.DomainMasteryOpenPacket());
    }

    /**
     * Checks whether is wheel key down is true for the current addon state.
     * @return true when is wheel key down succeeds; otherwise false.
     */
    private static boolean isWheelKeyDown() {
        if (SKILL_WHEEL_KEY == null) {
            return false;
        }
        if (!SKILL_WHEEL_KEY.getKeyConflictContext().isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = SKILL_WHEEL_KEY.getKey();
        return InputConstants.isKeyDown((long)window, (int)key.getValue());
    }

    /**
     * Checks whether is wheel key is true for the current addon state.
     * @param keyCode key code used by this method.
     * @param scanCode scan code used by this method.
     * @return true when is wheel key succeeds; otherwise false.
     */
    public static boolean isWheelKey(int keyCode, int scanCode) {
        if (SKILL_WHEEL_KEY == null) {
            return false;
        }
        return SKILL_WHEEL_KEY.getKey().getValue() == keyCode;
    }

    /**
     * Checks whether is domain mastery key down is true for the current addon state.
     * @return true when is domain mastery key down succeeds; otherwise false.
     */
    private static boolean isDomainMasteryKeyDown() {
        if (DOMAIN_MASTERY_KEY == null) {
            return false;
        }
        if (!DOMAIN_MASTERY_KEY.getKeyConflictContext().isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = DOMAIN_MASTERY_KEY.getKey();
        return InputConstants.isKeyDown((long)window, (int)key.getValue());
    }

    @Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.MOD)
    /**
     * Client mod-bus registration helper that owns key binding setup and renderer registration for addon-only client content.
     */
    public static class ModBusEvents {
        @SubscribeEvent
        /**
         * Registers keys with the appropriate Forge or client system.
         * @param event context data supplied by the current callback or network pipeline.
         */
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            SKILL_WHEEL_KEY = new KeyMapping("key.jjkblueredpurple.skill_wheel", (IKeyConflictContext)KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 258, "key.categories.jjkblueredpurple");
            event.register(SKILL_WHEEL_KEY);
            DOMAIN_MASTERY_KEY = new KeyMapping("key.jjkblueredpurple.domain_mastery", (IKeyConflictContext)KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 44, "key.categories.jjkblueredpurple");
            event.register(DOMAIN_MASTERY_KEY);
        }

        @SubscribeEvent
        /**
         * Registers entity renderers with the appropriate Forge or client system.
         * @param event context data supplied by the current callback or network pipeline.
         */
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer((EntityType)LimbEntityRegistry.SEVERED_LIMB.get(), SeveredLimbRenderer::new);
        }

        @SubscribeEvent
        /**
         * Handles the add layers callback for this addon component.
         * @param event context data supplied by the current callback or network pipeline.
         */
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skinName : event.getSkins()) {
                LivingEntityRenderer renderer = event.getSkin(skinName);
                if (renderer == null) continue;
                LivingEntityRenderer livingRenderer = renderer;
                livingRenderer.addLayer((RenderLayer)new LimbRegrowthLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>)livingRenderer));
            }
        }
    }
}

