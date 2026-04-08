package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.mcreator.jujutsucraft.addon.limb.SeveredLimbEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Client renderer for {@link net.mcreator.jujutsucraft.addon.limb.SeveredLimbEntity}.
 *
 * <p>The renderer reuses vanilla player model parts, hides everything except the requested limb,
 * applies tumble rotation from the entity, and draws the detached limb using the owner's default skin.</p>
 */
public class SeveredLimbRenderer
extends EntityRenderer<SeveredLimbEntity> {
    /** Standard-width player model used for most detached limbs. */
    private final PlayerModel<Player> wideModel;
    /** Slim player model used when the source player uses the slim arm layout. */
    private final PlayerModel<Player> slimModel;

    /**
     * Builds the renderer and bakes both player model variants.
     *
     * @param ctx renderer construction context
     */
    public SeveredLimbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wideModel = new PlayerModel(ctx.getModelSet().bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(ctx.getModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    /**
     * Renders a detached limb using the correct player model part and skin.
     *
     * @param entity detached limb entity being rendered
     * @param entityYaw interpolated entity yaw
     * @param partialTick partial tick interpolation value
     * @param poseStack active pose stack
     * @param bufferSource render buffer source
     * @param packedLight packed light value
     */
    public void render(SeveredLimbEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        LimbType limbType = entity.getLimbType();
        if (limbType == null) {
            return;
        }
        PlayerModel<Player> model = entity.isSlimModel() ? this.slimModel : this.wideModel;
        this.hideAllParts(model);
        ModelPart mainPart = this.getPartForType(model, limbType);
        ModelPart overlayPart = this.getOverlayForType(model, limbType);
        if (mainPart == null) {
            return;
        }
        mainPart.visible = true;
        if (overlayPart != null) {
            overlayPart.visible = true;
        }
        ResourceLocation skin = this.getSkinTexture(entity);
        poseStack.pushPose();
        // Flip and scale the model into item-like detached-limb space.
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        float mX = mainPart.x;
        float mY = mainPart.y;
        float mZ = mainPart.z;
        float mXR = mainPart.xRot;
        float mYR = mainPart.yRot;
        float mZR = mainPart.zRot;
        // Reset the part origin so only the detached limb is rendered around the entity center.
        mainPart.x = 0.0f;
        mainPart.y = 0.0f;
        mainPart.z = 0.0f;
        mainPart.xRot = (float)Math.toRadians(entity.getLimbRotX());
        mainPart.yRot = 0.0f;
        mainPart.zRot = (float)Math.toRadians(entity.getLimbRotZ());
        float oX = 0.0f;
        float oY = 0.0f;
        float oZ = 0.0f;
        float oXR = 0.0f;
        float oYR = 0.0f;
        float oZR = 0.0f;
        if (overlayPart != null) {
            oX = overlayPart.x;
            oY = overlayPart.y;
            oZ = overlayPart.z;
            oXR = overlayPart.xRot;
            oYR = overlayPart.yRot;
            oZR = overlayPart.zRot;
            // Keep the overlay aligned exactly with the exposed limb part.
            overlayPart.x = 0.0f;
            overlayPart.y = 0.0f;
            overlayPart.z = 0.0f;
            overlayPart.xRot = mainPart.xRot;
            overlayPart.yRot = 0.0f;
            overlayPart.zRot = mainPart.zRot;
        }
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent((ResourceLocation)skin));
        mainPart.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (overlayPart != null) {
            overlayPart.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // Restore all mutated part transforms so the baked model can be reused safely.
        mainPart.x = mX;
        mainPart.y = mY;
        mainPart.z = mZ;
        mainPart.xRot = mXR;
        mainPart.yRot = mYR;
        mainPart.zRot = mZR;
        if (overlayPart != null) {
            overlayPart.x = oX;
            overlayPart.y = oY;
            overlayPart.z = oZ;
            overlayPart.xRot = oXR;
            overlayPart.yRot = oYR;
            overlayPart.zRot = oZR;
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * Hides every player model part so only the selected limb needs to be re-enabled.
     *
     * @param model model to reset
     */
    private void hideAllParts(PlayerModel<Player> model) {
        model.head.visible = false;
        model.hat.visible = false;
        model.body.visible = false;
        model.leftPants.visible = false;
        model.leftArm.visible = false;
        model.leftSleeve.visible = false;
        model.rightArm.visible = false;
        model.rightSleeve.visible = false;
        model.leftLeg.visible = false;
        model.rightLeg.visible = false;
        model.rightPants.visible = false;
        model.jacket.visible = false;
    }

    /**
     * Returns the base player model part for a detached limb type.
     *
     * @param model player model to query
     * @param type limb being rendered
     * @return matching base model part
     */
    private ModelPart getPartForType(PlayerModel<Player> model, LimbType type) {
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
            case HEAD -> model.head;
        };
    }

    /**
     * Returns the matching overlay layer for a detached limb type.
     *
     * @param model player model to query
     * @param type limb being rendered
     * @return matching overlay model part
     */
    private ModelPart getOverlayForType(PlayerModel<Player> model, LimbType type) {
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
            case HEAD -> model.hat;
        };
    }

    /**
     * Chooses a default player skin based on the owner UUID when available.
     *
     * @param entity detached limb entity being rendered
     * @return resource location for the skin texture
     */
    private ResourceLocation getSkinTexture(SeveredLimbEntity entity) {
        Optional<UUID> ownerUUID = entity.getOwnerUUID();
        if (ownerUUID.isPresent()) {
            return DefaultPlayerSkin.getDefaultSkin((UUID)ownerUUID.get());
        }
        return DefaultPlayerSkin.getDefaultSkin((UUID)UUID.nameUUIDFromBytes(new byte[0]));
    }

    /**
     * Returns the texture location requested by the renderer API.
     *
     * @param entity detached limb entity
     * @return skin texture used for rendering
     */
    public ResourceLocation getTextureLocation(SeveredLimbEntity entity) {
        return this.getSkinTexture(entity);
    }
}
