package net.mcreator.jujutsucraft.addon;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.yuta.YutaFakePlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YutaClientEntityRenderers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private YutaClientEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)LimbEntityRegistry.YUTA_FAKE_PLAYER.get(), YutaFakePlayerRenderer::new);
        LOGGER.info("[EntityRendererDiag] Registered jjkblueredpurple:yuta_fake_player renderer from dedicated Yuta client registrar");
    }
}
