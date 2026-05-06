package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.mcreator.jujutsucraft.addon.limb.ClientLimbCache;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Client render layer for the visual regrowth effect while a limb is reversing.
 *
 * <p>The effect is divided into three visual phases across the first 75% of progress:
 * bone ({@code < 0.25f}), muscle ({@code < 0.5f}), and flesh ({@code < 0.75f}). Once the final
 * phase threshold is reached, the normal player model becomes visible again.</p>
 */
public class LimbRegrowthLayer
extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Progress threshold where bone visuals end. */
    private static final float PHASE_BONE = 0.25f;
    /** Progress threshold where muscle visuals end. */
    private static final float PHASE_MUSCLE = 0.5f;
    /** Progress threshold where flesh visuals end and normal rendering resumes. */
    private static final float PHASE_FLESH = 0.75f;
    /** Lazily created procedural texture for the bone phase. */
    private static ResourceLocation boneTex;
    /** Lazily created procedural texture for the muscle phase. */
    private static ResourceLocation muscleTex;
    /** Lazily created procedural texture for the flesh phase. */
    private static ResourceLocation fleshTex;
    /** Width and height of the generated procedural textures in pixels. */
    private static final int TEX_SIZE = 16;

    /**
     * Creates the regrowth layer for a player renderer.
     *
     * @param parent parent renderer/model provider
     */
    public LimbRegrowthLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /**
     * Renders the temporary regrowth mesh for any limb still in the early reversing phases.
     *
     * @param poseStack active pose stack
     * @param buffer buffer source used for drawing
     * @param light packed light value
     * @param entity rendered player
     * @param limbSwing vanilla animation input
     * @param limbSwingAmount vanilla animation input
     * @param partialTick partial tick interpolation value
     * @param ageInTicks vanilla animation input
     * @param headYaw head yaw value
     * @param headPitch head pitch value
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
        ClientLimbCache.EntityLimbSnapshot snapshot = ClientLimbCache.get(entity.getId());
        if (snapshot == null) {
            return;
        }
        boolean anyEarlyPhase = false;
        for (LimbType type : LimbType.values()) {
            if (snapshot.getState(type) != LimbState.REVERSING || !(snapshot.getRegenProgress(type) < 0.75f)) continue;
            anyEarlyPhase = true;
            break;
        }
        if (!anyEarlyPhase) {
            return;
        }
        PlayerModel model = (PlayerModel)this.getParentModel();
        for (LimbType type : LimbType.values()) {
            LimbState state = snapshot.getState(type);
            float progress = snapshot.getRegenProgress(type);
            if (state != LimbState.REVERSING || progress >= 0.75f) continue;
            ModelPart part = LimbRegrowthLayer.getModelPart(model, type);
            // Normalize the first 75% of regen into a 0-1 scale for procedural growth visuals.
            float normalizedProgress = Math.min(1.0f, progress / 0.75f);
            float growthScale = Math.max(0.05f, LimbRegrowthLayer.easeOutCubic(normalizedProgress));
            float origXScale = part.xScale;
            float origYScale = part.yScale;
            float origZScale = part.zScale;
            boolean origVisible = part.visible;
            part.visible = true;
            // Scale the part from a tiny stump toward full size using a smooth ease-out curve.
            part.xScale = origXScale * growthScale;
            part.yScale = origYScale * growthScale;
            part.zScale = origZScale * growthScale;
            ResourceLocation phaseTex = progress < 0.25f ? LimbRegrowthLayer.getOrCreateBoneTexture() : (progress < 0.5f ? LimbRegrowthLayer.getOrCreateMuscleTexture() : LimbRegrowthLayer.getOrCreateFleshTexture());
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull((ResourceLocation)phaseTex));
            part.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
            // Restore the original model state so later render passes are unaffected.
            part.visible = origVisible;
            part.xScale = origXScale;
            part.yScale = origYScale;
            part.zScale = origZScale;
        }
    }

    /**
     * Maps a tracked limb type to its player model part.
     *
     * @param model player model being queried
     * @param type limb type to resolve
     * @return matching player model part
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
     * Smooths the growth animation so it expands quickly at first and settles naturally near the end.
     *
     * @param t normalized progress from {@code 0.0f} to {@code 1.0f}
     * @return eased scale factor
     */
    private static float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    // ===== PROCEDURAL PHASE TEXTURES =====

    /**
     * Returns the cached procedural bone texture, creating it on first use.
     *
     * @return resource location for the bone-phase texture
     */
    private static ResourceLocation getOrCreateBoneTexture() {
        if (boneTex == null) {
            boneTex = new ResourceLocation("jjkblueredpurple", "dynamic/bone");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            // Fixed seeds keep the generated texture stable across runs and clients.
            Random rng = new Random(45214L);
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    int base = 210 + rng.nextInt(30);
                    int g = base - 5 - rng.nextInt(10);
                    int b = base - 20 - rng.nextInt(15);
                    // Random darker streaks and occasional striations add bone-like texture variation.
                    if (rng.nextInt(8) == 0 || y % 5 == 0 && rng.nextInt(3) == 0) {
                        base -= 50 + rng.nextInt(30);
                        g -= 40;
                        b -= 30;
                    }
                    if (x % 4 == 0 && rng.nextInt(4) == 0) {
                        base -= 25;
                        g -= 20;
                    }
                    img.setPixelRGBA(x, y, LimbRegrowthLayer.packABGR(255, LimbRegrowthLayer.clamp(base), LimbRegrowthLayer.clamp(g), LimbRegrowthLayer.clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(boneTex, (AbstractTexture)new DynamicTexture(img));
        }
        return boneTex;
    }

    /**
     * Returns the cached procedural muscle texture, creating it on first use.
     *
     * @return resource location for the muscle-phase texture
     */
    private static ResourceLocation getOrCreateMuscleTexture() {
        if (muscleTex == null) {
            muscleTex = new ResourceLocation("jjkblueredpurple", "dynamic/muscle");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            Random rng = new Random(41052L);
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    int r = 140 + rng.nextInt(30);
                    int g = 45 + rng.nextInt(25);
                    int b = 40 + rng.nextInt(20);
                    // Vertical streaking and banding create a fibrous muscle appearance.
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
                    img.setPixelRGBA(x, y, LimbRegrowthLayer.packABGR(255, LimbRegrowthLayer.clamp(r), LimbRegrowthLayer.clamp(g), LimbRegrowthLayer.clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(muscleTex, (AbstractTexture)new DynamicTexture(img));
        }
        return muscleTex;
    }

    /**
     * Returns the cached procedural flesh texture, creating it on first use.
     *
     * @return resource location for the flesh-phase texture
     */
    private static ResourceLocation getOrCreateFleshTexture() {
        if (fleshTex == null) {
            fleshTex = new ResourceLocation("jjkblueredpurple", "dynamic/flesh");
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            Random rng = new Random(61925L);
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    int r = 185 + rng.nextInt(25);
                    int g = 80 + rng.nextInt(30);
                    int b = 75 + rng.nextInt(25);
                    // Distance-based mottling and occasional darker spots keep the texture organic.
                    double dist = Math.sqrt(Math.pow((double)(x - 8) + rng.nextGaussian() * 2.0, 2.0) + Math.pow((double)(y - 8) + rng.nextGaussian() * 2.0, 2.0));
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
                    img.setPixelRGBA(x, y, LimbRegrowthLayer.packABGR(255, LimbRegrowthLayer.clamp(r), LimbRegrowthLayer.clamp(g), LimbRegrowthLayer.clamp(b)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(fleshTex, (AbstractTexture)new DynamicTexture(img));
        }
        return fleshTex;
    }

    /**
     * Packs color channels into the ABGR format expected by {@link com.mojang.blaze3d.platform.NativeImage}.
     *
     * @param a alpha channel in the range {@code 0-255}
     * @param r red channel in the range {@code 0-255}
     * @param g green channel in the range {@code 0-255}
     * @param b blue channel in the range {@code 0-255}
     * @return packed ABGR integer color
     */
    private static int packABGR(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | r & 0xFF;
    }

    /**
     * Clamps a channel value to the valid 8-bit color range.
     *
     * @param v raw color channel value
     * @return clamped value between {@code 0} and {@code 255}
     */
    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
