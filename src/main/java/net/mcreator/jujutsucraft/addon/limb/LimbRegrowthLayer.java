package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom RenderLayer that renders bone/muscle/flesh solid-color overlays
 * during limb regeneration (REVERSING state).
 *
 * Using a RenderLayer instead of RenderLivingEvent.Post ensures the
 * PoseStack ALREADY has all entity transforms (yaw, flip, crouch,
 * swimming, elytra, etc.) and ModelParts have animated rotations
 * from setupAnim(). No manual transform replication needed.
 */
public class LimbRegrowthLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    // Phase thresholds — match LimbRenderHandler
    private static final float PHASE_BONE = 0.25f;
    private static final float PHASE_MUSCLE = 0.5f;
    private static final float PHASE_FLESH = 0.75f;

    // Procedural textures
    private static ResourceLocation boneTex;
    private static ResourceLocation muscleTex;
    private static ResourceLocation fleshTex;
    private static final int TEX_SIZE = 16;

    public LimbRegrowthLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light,
                       AbstractClientPlayer entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float headYaw, float headPitch) {

        ClientLimbCache.EntityLimbSnapshot snapshot = ClientLimbCache.get(entity.getId());
        if (snapshot == null) return;

        // Check if any limb needs solid-color phase rendering
        boolean anyEarlyPhase = false;
        for (LimbType type : LimbType.values()) {
            if (snapshot.getState(type) == LimbState.REVERSING
                    && snapshot.getRegenProgress(type) < PHASE_FLESH) {
                anyEarlyPhase = true;
                break;
            }
        }
        if (!anyEarlyPhase) return;

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();

        for (LimbType type : LimbType.values()) {
            LimbState state = snapshot.getState(type);
            float progress = snapshot.getRegenProgress(type);

            if (state != LimbState.REVERSING || progress >= PHASE_FLESH)
                continue;

            ModelPart part = getModelPart(model, type);

            // Normalize progress within solid-color range (0 → PHASE_FLESH)
            // so scale goes 0→1 across bone/muscle/flesh phases.
            float normalizedProgress = Math.min(1.0f, progress / PHASE_FLESH);
            float growthScale = Math.max(0.05f, easeOutCubic(normalizedProgress));

            // Save original state
            float origXScale = part.xScale;
            float origYScale = part.yScale;
            float origZScale = part.zScale;
            boolean origVisible = part.visible;

            // Force visible — Pre handler hid it for the skin texture pass.
            // ModelPart.render() returns early if visible==false.
            part.visible = true;

            // Growth scale via ModelPart → scales around pivot point
            part.xScale = origXScale * growthScale;
            part.yScale = origYScale * growthScale;
            part.zScale = origZScale * growthScale;

            // Select phase texture
            ResourceLocation phaseTex = progress < PHASE_BONE ? getOrCreateBoneTexture()
                    : (progress < PHASE_MUSCLE ? getOrCreateMuscleTexture()
                            : getOrCreateFleshTexture());

            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(phaseTex));

            // part.render() uses its own animated xRot/yRot/zRot from setupAnim —
            // correct position for ALL animations (walking, running, swimming, etc.)
            part.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

            // Restore
            part.visible = origVisible;
            part.xScale = origXScale;
            part.yScale = origYScale;
            part.zScale = origZScale;
        }
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

    // ══════════════════════════════════════════════════════════════
    // Easing — smooth deceleration, no overshoot
    // ══════════════════════════════════════════════════════════════

    private static float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    // ══════════════════════════════════════════════════════════════
    // Dynamic procedural textures (16×16 RGBA)
    // ══════════════════════════════════════════════════════════════

    private static ResourceLocation getOrCreateBoneTexture() {
        if (boneTex == null) {
            boneTex = new ResourceLocation("jjkblueredpurple", "dynamic/bone");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            Random rng = new Random(45214L);
            for (int y = 0; y < TEX_SIZE; ++y) {
                for (int x = 0; x < TEX_SIZE; ++x) {
                    int base = 210 + rng.nextInt(30);
                    int g = base - 5 - rng.nextInt(10);
                    int b = base - 20 - rng.nextInt(15);
                    if (rng.nextInt(8) == 0 || (y % 5 == 0 && rng.nextInt(3) == 0)) {
                        base -= 50 + rng.nextInt(30);
                        g -= 40;
                        b -= 30;
                    }
                    if (x % 4 == 0 && rng.nextInt(4) == 0) {
                        base -= 25;
                        g -= 20;
                    }
                    img.setPixelRGBA(x, y, packABGR(255, clamp(base), clamp(g), clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(boneTex, new DynamicTexture(img));
        }
        return boneTex;
    }

    private static ResourceLocation getOrCreateMuscleTexture() {
        if (muscleTex == null) {
            muscleTex = new ResourceLocation("jjkblueredpurple", "dynamic/muscle");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            Random rng = new Random(41052L);
            for (int y = 0; y < TEX_SIZE; ++y) {
                for (int x = 0; x < TEX_SIZE; ++x) {
                    int r = 140 + rng.nextInt(30);
                    int g = 45 + rng.nextInt(25);
                    int b = 40 + rng.nextInt(20);
                    if (x % 3 == 0) {
                        r += 20 + rng.nextInt(15);
                        g += 10;
                    }
                    if (y % 6 < 1) {
                        r += 15;
                        g += 15;
                        b += 10;
                    }
                    if (x % 3 == 1 && rng.nextInt(3) == 0) {
                        r -= 30;
                        g -= 15;
                    }
                    img.setPixelRGBA(x, y, packABGR(255, clamp(r), clamp(g), clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(muscleTex, new DynamicTexture(img));
        }
        return muscleTex;
    }

    private static ResourceLocation getOrCreateFleshTexture() {
        if (fleshTex == null) {
            fleshTex = new ResourceLocation("jjkblueredpurple", "dynamic/flesh");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            Random rng = new Random(61925L);
            for (int y = 0; y < TEX_SIZE; ++y) {
                for (int x = 0; x < TEX_SIZE; ++x) {
                    int r = 185 + rng.nextInt(25);
                    int g = 80 + rng.nextInt(30);
                    int b = 75 + rng.nextInt(25);
                    double dist = Math.sqrt(Math.pow((double) (x - 8) + rng.nextGaussian() * 2.0, 2.0)
                            + Math.pow((double) (y - 8) + rng.nextGaussian() * 2.0, 2.0));
                    if (dist < 3.0 + rng.nextDouble() * 2.0) {
                        r -= 25;
                        g -= 10;
                    }
                    if (rng.nextInt(12) == 0) {
                        r = 160 + rng.nextInt(20);
                        g = 20 + rng.nextInt(15);
                        b = 20 + rng.nextInt(15);
                    }
                    if ((x + y) % 5 == 0 && rng.nextInt(2) == 0) {
                        r -= 10;
                        g += 5;
                        b += 5;
                    }
                    img.setPixelRGBA(x, y, packABGR(255, clamp(r), clamp(g), clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(fleshTex, new DynamicTexture(img));
        }
        return fleshTex;
    }

    private static int packABGR(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
