package net.mcreator.jujutsucraft.addon.limb;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles limb visibility during the render pipeline.
 *
 * Pre handler: hides skin-textured ModelParts for severed/regenerating limbs.
 * The solid-color bone/muscle/flesh overlay is rendered by LimbRegrowthLayer
 * (a proper RenderLayer), which automatically inherits all entity transforms.
 */
@Mod.EventBusSubscriber(modid = "jjkblueredpurple", value = Dist.CLIENT)
public class LimbRenderHandler {

    private static final float PHASE_FLESH = 0.75f;

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ClientLimbCache.clear();
    }

    /**
     * RenderLivingEvent.Pre — hide skin-textured parts for missing/regenerating limbs.
     *
     * SEVERED:              hide completely
     * REVERSING < 75%:      hide (LimbRegrowthLayer renders bone/muscle/flesh instead)
     * REVERSING >= 75%:     show (skin texture fades back in)
     * INTACT:               show normally
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        ClientLimbCache.EntityLimbSnapshot snapshot = ClientLimbCache.get(entity.getId());
        if (snapshot == null) return;

        EntityModel model = event.getRenderer().getModel();
        if (model instanceof PlayerModel<?> pm) {
            applyPreVisibility(pm, snapshot);
        } else if (model instanceof HumanoidModel<?> hm) {
            applyHumanoidModelVisibility(hm, snapshot);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Pre-event: visibility / scale management
    // ══════════════════════════════════════════════════════════════

    private static void applyPreVisibility(PlayerModel<?> model, ClientLimbCache.EntityLimbSnapshot snapshot) {
        for (LimbType type : LimbType.values()) {
            ModelPart part = getModelPart(model, type);
            ModelPart overlay = getOverlayPart(model, type);
            LimbState state = snapshot.getState(type);
            float progress = snapshot.getRegenProgress(type);

            if (state == LimbState.SEVERED) {
                part.visible = false;
                if (overlay != null) overlay.visible = false;
                continue;
            }

            if (state == LimbState.REVERSING) {
                if (progress < PHASE_FLESH) {
                    // Bone/muscle/flesh phase — hide skin so LimbRegrowthLayer renders
                    part.visible = false;
                    if (overlay != null) overlay.visible = false;
                } else {
                    // ≥75%: skin texture fades back in
                    part.visible = true;
                    part.xScale = 1.0f;
                    part.yScale = 1.0f;
                    part.zScale = 1.0f;
                    if (overlay != null) {
                        overlay.visible = true;
                        overlay.xScale = 1.0f;
                        overlay.yScale = 1.0f;
                        overlay.zScale = 1.0f;
                    }
                }
                continue;
            }

            // INTACT — restore to normal
            part.visible = true;
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;
            if (overlay != null) {
                overlay.visible = true;
                overlay.xScale = 1.0f;
                overlay.yScale = 1.0f;
                overlay.zScale = 1.0f;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HumanoidModel (non-player entities) support
    // ══════════════════════════════════════════════════════════════

    private static void applyHumanoidModelVisibility(HumanoidModel<?> model,
            ClientLimbCache.EntityLimbSnapshot snapshot) {
        if (snapshot.isLimbMissing(LimbType.LEFT_ARM))  model.leftArm.visible = false;
        if (snapshot.isLimbMissing(LimbType.RIGHT_ARM)) model.rightArm.visible = false;
        if (snapshot.isLimbMissing(LimbType.LEFT_LEG))  model.leftLeg.visible = false;
        if (snapshot.isLimbMissing(LimbType.RIGHT_LEG)) model.rightLeg.visible = false;
        if (snapshot.isLimbMissing(LimbType.HEAD))      model.head.visible = false;
    }

    // ══════════════════════════════════════════════════════════════
    // ModelPart mapping
    // ══════════════════════════════════════════════════════════════

    private static ModelPart getModelPart(PlayerModel<?> model, LimbType type) {
        return switch (type) {
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
            case HEAD -> model.head;
        };
    }

    private static ModelPart getOverlayPart(PlayerModel<?> model, LimbType type) {
        return switch (type) {
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
            case HEAD -> model.hat;
        };
    }
}
