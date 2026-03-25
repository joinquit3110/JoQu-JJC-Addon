package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
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
import net.minecraft.world.entity.player.Player;

public class SeveredLimbRenderer extends EntityRenderer<SeveredLimbEntity> {
    private final PlayerModel<Player> wideModel;
    private final PlayerModel<Player> slimModel;

    public SeveredLimbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wideModel = new PlayerModel<>(ctx.getModelSet().bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(ctx.getModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(SeveredLimbEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        LimbType limbType = entity.getLimbType();
        if (limbType == null) return;

        PlayerModel<Player> model = entity.isSlimModel() ? this.slimModel : this.wideModel;
        this.hideAllParts(model);

        ModelPart mainPart = this.getPartForType(model, limbType);
        ModelPart overlayPart = this.getOverlayForType(model, limbType);
        if (mainPart == null) return;

        mainPart.visible = true;
        if (overlayPart != null) overlayPart.visible = true;

        ResourceLocation skin = this.getSkinTexture(entity);

        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);

        float mX = mainPart.x;
        float mY = mainPart.y;
        float mZ = mainPart.z;
        float mXR = mainPart.xRot;
        float mYR = mainPart.yRot;
        float mZR = mainPart.zRot;
        mainPart.x = 0.0f;
        mainPart.y = 0.0f;
        mainPart.z = 0.0f;
        mainPart.xRot = (float) Math.toRadians(entity.getLimbRotX());
        mainPart.yRot = 0.0f;
        mainPart.zRot = (float) Math.toRadians(entity.getLimbRotZ());

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
            overlayPart.x = 0.0f;
            overlayPart.y = 0.0f;
            overlayPart.z = 0.0f;
            overlayPart.xRot = mainPart.xRot;
            overlayPart.yRot = 0.0f;
            overlayPart.zRot = mainPart.zRot;
        }

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(skin));
        mainPart.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (overlayPart != null) {
            overlayPart.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }

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

    private ModelPart getPartForType(PlayerModel<Player> model, LimbType type) {
        return switch (type) {
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
            case HEAD -> model.head;
        };
    }

    private ModelPart getOverlayForType(PlayerModel<Player> model, LimbType type) {
        return switch (type) {
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
            case HEAD -> model.hat;
        };
    }

    private ResourceLocation getSkinTexture(SeveredLimbEntity entity) {
        Optional<UUID> ownerUUID = entity.getOwnerUUID();
        if (ownerUUID.isPresent()) {
            return DefaultPlayerSkin.getDefaultSkin(ownerUUID.get());
        }
        return DefaultPlayerSkin.getDefaultSkin(UUID.nameUUIDFromBytes(new byte[0]));
    }

    @Override
    public ResourceLocation getTextureLocation(SeveredLimbEntity entity) {
        return this.getSkinTexture(entity);
    }
}
