package net.mcreator.jujutsucraft.addon.yuta;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;

/**
 * Client renderer for the addon Yuta fake-player helper entity.
 *
 * <p>The entity extends vanilla {@code Villager}, so registering a real {@link VillagerRenderer} subclass is
 * the Forge 1.20.1-compatible way to render the body model. The previous minimal renderer intentionally drew
 * no model, which made the entity appear invisible except for its nameplate.</p>
 */
public class YutaFakePlayerRenderer extends VillagerRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

    public YutaFakePlayerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
        LOGGER.info("[EntityRendererDiag] Registered villager model renderer for jjkblueredpurple:yuta_fake_player");
    }

    @Override
    public ResourceLocation getTextureLocation(Villager entity) {
        return VILLAGER_TEXTURE;
    }
}
