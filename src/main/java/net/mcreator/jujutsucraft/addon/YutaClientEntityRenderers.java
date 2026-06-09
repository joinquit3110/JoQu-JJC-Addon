package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.yuta.YutaFakePlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YutaClientEntityRenderers {
    private YutaClientEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)LimbEntityRegistry.YUTA_FAKE_PLAYER.get(), YutaFakePlayerRenderer::new);
    }
}
