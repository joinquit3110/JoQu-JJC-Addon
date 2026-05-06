package net.mcreator.jujutsucraft.addon.limb;

import net.mcreator.jujutsucraft.addon.limb.ClientLimbCache;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
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
 * Client-side render hook that hides severed or still-regenerating model parts.
 *
 * <p>The handler reads cached limb snapshots sent from the server and updates player/humanoid model
 * visibility just before rendering, allowing the limb system to affect first- and third-person visuals.</p>
 */
@Mod.EventBusSubscriber(modid="jjkblueredpurple", value={Dist.CLIENT})
public class LimbRenderHandler {
    /**
     * At 75% reverse progress the full limb mesh becomes visible again instead of using the regrowth layer.
     */
    private static final float PHASE_FLESH = 0.75f;

    /**
     * Clears cached limb snapshots when the local player respawns.
     *
     * @param event Forge respawn event
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ClientLimbCache.clear();
    }

    /**
     * Applies limb visibility changes before a living entity is rendered.
     *
     * @param event pre-render hook for living entities
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        ClientLimbCache.EntityLimbSnapshot snapshot = ClientLimbCache.get(entity.getId());
        if (snapshot == null) {
            return;
        }
        EntityModel model = event.getRenderer().getModel();
        if (model instanceof PlayerModel) {
            PlayerModel pm = (PlayerModel)model;
            LimbRenderHandler.applyPreVisibility(pm, snapshot);
        } else if (model instanceof HumanoidModel) {
            HumanoidModel hm = (HumanoidModel)model;
            LimbRenderHandler.applyHumanoidModelVisibility(hm, snapshot);
        }
    }

    /**
     * Applies visibility and scale changes to player models, including overlay layers.
     *
     * @param model player model about to render
     * @param snapshot cached limb state for the rendered entity
     */
    private static void applyPreVisibility(PlayerModel<?> model, ClientLimbCache.EntityLimbSnapshot snapshot) {
        for (LimbType type : LimbType.values()) {
            ModelPart part = LimbRenderHandler.getModelPart(model, type);
            ModelPart overlay = LimbRenderHandler.getOverlayPart(model, type);
            LimbState state = snapshot.getState(type);
            float progress = snapshot.getRegenProgress(type);
            if (state == LimbState.SEVERED) {
                // Fully severed limbs and their cosmetic overlays are hidden completely.
                part.visible = false;
                if (overlay == null) continue;
                overlay.visible = false;
                continue;
            }
            if (state == LimbState.REVERSING) {
                if (progress < 0.75f) {
                    // Early reversing is drawn by the dedicated regrowth layer, not the base player skin.
                    part.visible = false;
                    if (overlay == null) continue;
                    overlay.visible = false;
                    continue;
                }
                // Once flesh coverage reaches the final phase, restore the normal model part.
                part.visible = true;
                part.xScale = 1.0f;
                part.yScale = 1.0f;
                part.zScale = 1.0f;
                if (overlay == null) continue;
                overlay.visible = true;
                overlay.xScale = 1.0f;
                overlay.yScale = 1.0f;
                overlay.zScale = 1.0f;
                continue;
            }
            // Intact limbs always render at normal visibility and scale.
            part.visible = true;
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;
            if (overlay == null) continue;
            overlay.visible = true;
            overlay.xScale = 1.0f;
            overlay.yScale = 1.0f;
            overlay.zScale = 1.0f;
        }
    }

    /**
     * Applies simple limb visibility to generic humanoid models that do not expose overlay parts.
     *
     * @param model humanoid model about to render
     * @param snapshot cached limb state for the rendered entity
     */
    private static void applyHumanoidModelVisibility(HumanoidModel<?> model, ClientLimbCache.EntityLimbSnapshot snapshot) {
        if (snapshot.isLimbMissing(LimbType.LEFT_ARM)) {
            model.leftArm.visible = false;
        }
        if (snapshot.isLimbMissing(LimbType.RIGHT_ARM)) {
            model.rightArm.visible = false;
        }
        if (snapshot.isLimbMissing(LimbType.LEFT_LEG)) {
            model.leftLeg.visible = false;
        }
        if (snapshot.isLimbMissing(LimbType.RIGHT_LEG)) {
            model.rightLeg.visible = false;
        }
        if (snapshot.isLimbMissing(LimbType.HEAD)) {
            model.head.visible = false;
        }
    }

    /**
     * Maps a tracked limb type to its matching player model part.
     *
     * @param model player model being queried
     * @param type limb type to resolve
     * @return matching base model part
     */
    private static ModelPart getModelPart(PlayerModel<?> model, LimbType type) {
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
     * Maps a tracked limb type to its matching outerwear overlay part.
     *
     * @param model player model being queried
     * @param type limb type to resolve
     * @return matching overlay part, or the head hat layer for the head
     */
    private static ModelPart getOverlayPart(PlayerModel<?> model, LimbType type) {
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
            case HEAD -> model.hat;
        };
    }
}
