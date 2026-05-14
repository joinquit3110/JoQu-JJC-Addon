package net.mcreator.jujutsucraft.addon;

import com.mojang.blaze3d.platform.InputConstants;
import net.mcreator.jujutsucraft.addon.clash.client.ClashHudOverlay;
import net.mcreator.jujutsucraft.addon.clash.client.ClientClashCache;
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
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {
    private static KeyMapping SKILL_WHEEL_KEY;
    private static KeyMapping DOMAIN_MASTERY_KEY;
    private static boolean wasKeyDown;
    private static boolean wasDMKeyDown;
    private static boolean wasSneakDown;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            if (mc.level == null) {
                ClientClashCache.INSTANCE.clear();
            }
            return;
        }
        ClientPacketHandler.markClientTick(mc.level != null ? mc.level.getGameTime() : 0L);
        NearDeathClientState.clientTick();
        BlackFlashHudOverlay.clientTick();
        if (mc.level != null) {
            ClientClashCache.INSTANCE.pruneExpired(mc.level.getGameTime());
        }
        ClientPacketHandler.ClientCooldownCache.tickDecay();
        ClientEvents.tickGojoSneakTap(mc);
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

    private static void tickGojoSneakTap(Minecraft mc) {
        if (mc.screen != null || mc.options == null || mc.options.keyShift == null) {
            wasSneakDown = false;
            return;
        }
        boolean down = mc.options.keyShift.isDown();
        if (down && !wasSneakDown) {
            ModNetworking.sendGojoShiftTap();
        }
        wasSneakDown = down;
    }

    private static void tickMovementKey(KeyMapping key, long window) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        BlackFlashHudOverlay.renderWorldBillboard(event);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            if (mc.level == null) {
                ClientClashCache.INSTANCE.clear();
            }
            return;
        }
        BlackFlashHudOverlay.onRawKeyInput(event.getKey(), event.getAction());
        if (mc.screen != null) {
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

    private static void openWheel(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        if (mc.screen instanceof SkillWheelScreen || mc.screen instanceof DomainMasteryScreen) {
            return;
        }
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.RequestWheelPacket());
    }

    private static void openDomainMastery(Minecraft mc) {
        if (mc.screen != null) {
            return;
        }
        if (mc.screen instanceof DomainMasteryScreen || mc.screen instanceof SkillWheelScreen) {
            return;
        }
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.DomainMasteryOpenPacket());
    }

    private static boolean isWheelKeyDown() {
        if (SKILL_WHEEL_KEY == null || !SKILL_WHEEL_KEY.getKeyConflictContext().isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = SKILL_WHEEL_KEY.getKey();
        return InputConstants.isKeyDown(window, key.getValue());
    }

    public static boolean isWheelKey(int keyCode, int scanCode) {
        if (SKILL_WHEEL_KEY == null) {
            return false;
        }
        return SKILL_WHEEL_KEY.getKey().getValue() == keyCode;
    }

    private static boolean isDomainMasteryKeyDown() {
        if (DOMAIN_MASTERY_KEY == null || !DOMAIN_MASTERY_KEY.getKeyConflictContext().isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = DOMAIN_MASTERY_KEY.getKey();
        return InputConstants.isKeyDown(window, key.getValue());
    }

    @Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            ClientClashCache.registerClientHandler();
            SKILL_WHEEL_KEY = new KeyMapping("key.jjkblueredpurple.skill_wheel", (IKeyConflictContext) KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 258, "key.categories.jjkblueredpurple");
            event.register(SKILL_WHEEL_KEY);
            DOMAIN_MASTERY_KEY = new KeyMapping("key.jjkblueredpurple.domain_mastery", (IKeyConflictContext) KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 44, "key.categories.jjkblueredpurple");
            event.register(DOMAIN_MASTERY_KEY);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll(ClashHudOverlay.OVERLAY_ID, ClashHudOverlay.INSTANCE);
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer((EntityType) LimbEntityRegistry.SEVERED_LIMB.get(), SeveredLimbRenderer::new);
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skinName : event.getSkins()) {
                LivingEntityRenderer renderer = event.getSkin(skinName);
                if (renderer == null) {
                    continue;
                }
                LivingEntityRenderer livingRenderer = renderer;
                livingRenderer.addLayer((RenderLayer) new LimbRegrowthLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) livingRenderer));
            }
        }
    }
}
