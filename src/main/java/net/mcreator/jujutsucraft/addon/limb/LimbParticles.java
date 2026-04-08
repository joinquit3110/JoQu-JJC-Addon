package net.mcreator.jujutsucraft.addon.limb;

import java.util.Random;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Factory-style helper for all limb-system particles.
 *
 * <p>This class centralizes visual effects for sever bursts, directional blood spray, periodic drips,
 * reverse cursed technique regrowth, and geometric helpers for locating each limb in world space.</p>
 */
public final class LimbParticles {
    /** Shared random source used for all particle variation. */
    private static final Random RNG = new Random();
    /** Dark red dust used for heavy blood splashes and pools. */
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.5f, 0.01f, 0.01f), 1.4f);
    /** Brighter blood dust used to highlight the center of violent sever bursts. */
    private static final DustParticleOptions BLOOD_BRIGHT = new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.03f), 1.0f);
    /** Fine red mist used to soften the edges of a sever burst. */
    private static final DustParticleOptions BLOOD_MIST = new DustParticleOptions(new Vector3f(0.4f, 0.02f, 0.02f), 0.6f);
    /** Green restorative dust used along a regrowing limb path. */
    private static final DustParticleOptions REGEN_GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 0.8f);
    /** Cyan restorative dust paired with the green spiral during regrowth. */
    private static final DustParticleOptions REGEN_CYAN = new DustParticleOptions(new Vector3f(0.1f, 0.8f, 0.7f), 0.7f);
    /** White highlight dust used as a magical accent during restoration. */
    private static final DustParticleOptions REGEN_WHITE = new DustParticleOptions(new Vector3f(0.95f, 0.98f, 0.95f), 0.5f);
    /** Pale dust used during the bone phase of regrowth. */
    private static final DustParticleOptions BONE_DUST = new DustParticleOptions(new Vector3f(0.92f, 0.88f, 0.78f), 0.6f);
    /** Red dust used during the muscle phase of regrowth. */
    private static final DustParticleOptions MUSCLE_RED = new DustParticleOptions(new Vector3f(0.7f, 0.15f, 0.1f), 0.7f);
    /** Pink dust used during the flesh phase of regrowth. */
    private static final DustParticleOptions FLESH_PINK = new DustParticleOptions(new Vector3f(0.85f, 0.5f, 0.45f), 0.6f);

    /**
     * Utility class; not meant to be instantiated.
     */
    private LimbParticles() {
    }

    // ===== SEVER EFFECTS =====

    /**
     * Spawns the main blood burst when a limb is severed.
     *
     * @param entity entity that lost the limb
     * @param type limb whose sever point should be used
     */
    public static void spawnSeverBloodBurst(LivingEntity entity, LimbType type) {
        int i;
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 offset = LimbParticles.getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;
        for (i = 0; i < 30; ++i) {
            double vx = RNG.nextGaussian() * 0.12;
            double vy = RNG.nextGaussian() * 0.08 + 0.08;
            double vz = RNG.nextGaussian() * 0.12;
            // Dense dark dust forms the core burst right at the sever point.
            serverLevel.sendParticles((ParticleOptions)BLOOD_DARK, px, py, pz, 1, vx, vy, vz, 0.35);
        }
        for (i = 0; i < 20; ++i) {
            // Spread particles in a radial ring to widen the visible sever explosion.
            double angle = Math.toRadians(18.0 * (double)i);
            double speed = 0.15 + RNG.nextDouble() * 0.2;
            double vx = Math.cos(angle) * speed + RNG.nextGaussian() * 0.04;
            double vy = 0.1 + RNG.nextDouble() * 0.15;
            double vz = Math.sin(angle) * speed + RNG.nextGaussian() * 0.04;
            serverLevel.sendParticles((ParticleOptions)BLOOD_BRIGHT, px, py, pz, 1, vx, vy, vz, 0.4);
        }
        for (i = 0; i < 15; ++i) {
            // Fine mist softens the effect and helps the burst feel volumetric.
            serverLevel.sendParticles((ParticleOptions)BLOOD_MIST, px + RNG.nextGaussian() * 0.2, py + RNG.nextGaussian() * 0.15, pz + RNG.nextGaussian() * 0.2, 1, RNG.nextGaussian() * 0.02, 0.01, RNG.nextGaussian() * 0.02, 0.05);
        }
        for (i = 0; i < 50; ++i) {
            // Crimson spores provide a chaotic fleshy spray layer over the dust particles.
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.CRIMSON_SPORE, px + RNG.nextGaussian() * 0.15, py + RNG.nextGaussian() * 0.1, pz + RNG.nextGaussian() * 0.15, 1, RNG.nextGaussian() * 0.15, RNG.nextGaussian() * 0.1 + 0.08, RNG.nextGaussian() * 0.15, 0.25);
        }
        for (i = 0; i < 8; ++i) {
            // Dripping lava doubles as a thick-droplet blood analogue here.
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.DRIPPING_LAVA, px + RNG.nextGaussian() * 0.3, py + 0.1 + RNG.nextDouble() * 0.2, pz + RNG.nextGaussian() * 0.3, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (i = 0; i < 5; ++i) {
            // Light smoke adds a brief shock-wave haze around the burst.
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.SMOKE, px + RNG.nextGaussian() * 0.1, py + RNG.nextDouble() * 0.15, pz + RNG.nextGaussian() * 0.1, 1, 0.0, 0.03, 0.0, 0.02);
        }
        double groundY = entity.getY() + 0.05;
        for (int i2 = 0; i2 < 12; ++i2) {
            // A low ring of stationary dust suggests a quick blood splash on the ground.
            double a = Math.toRadians(30.0 * (double)i2 + RNG.nextDouble() * 15.0);
            double r = 0.3 + RNG.nextDouble() * 0.4;
            serverLevel.sendParticles((ParticleOptions)BLOOD_DARK, px + Math.cos(a) * r, groundY, pz + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Spawns a directional blood spray away from the attacking source.
     *
     * @param entity entity that lost the limb
     * @param type limb whose sever point should be used
     * @param direction normalized or unnormalized spray direction
     */
    public static void spawnBloodSpray(LivingEntity entity, LimbType type, Vec3 direction) {
        double speed;
        int i;
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 offset = LimbParticles.getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;
        Vec3 dir = direction.normalize();
        for (i = 0; i < 25; ++i) {
            speed = 0.15 + RNG.nextDouble() * 0.35;
            double spread = 0.08;
            double vx = dir.x * speed + RNG.nextGaussian() * spread;
            double vy = dir.y * speed + RNG.nextGaussian() * spread + 0.03;
            double vz = dir.z * speed + RNG.nextGaussian() * spread;
            DustParticleOptions dust = RNG.nextBoolean() ? BLOOD_DARK : BLOOD_BRIGHT;
            serverLevel.sendParticles((ParticleOptions)dust, px, py, pz, 1, vx, vy, vz, 0.5);
        }
        for (i = 0; i < 10; ++i) {
            speed = 0.05 + RNG.nextDouble() * 0.1;
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.CRIMSON_SPORE, px, py, pz, 1, dir.x * speed + RNG.nextGaussian() * 0.1, 0.05 + RNG.nextDouble() * 0.05, dir.z * speed + RNG.nextGaussian() * 0.1, 0.2);
        }
    }

    /**
     * Spawns periodic blood drips from a severed or reversing limb.
     *
     * @param entity entity dripping blood
     * @param type limb whose sever point should be used
     */
    public static void spawnBloodDrip(LivingEntity entity, LimbType type) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 offset = LimbParticles.getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;
        int count = 3 + RNG.nextInt(3);
        for (int i = 0; i < count; ++i) {
            DustParticleOptions dust = RNG.nextInt(3) == 0 ? BLOOD_BRIGHT : BLOOD_DARK;
            serverLevel.sendParticles((ParticleOptions)dust, px + RNG.nextGaussian() * 0.06, py - 0.05 - RNG.nextDouble() * 0.1, pz + RNG.nextGaussian() * 0.06, 1, 0.0, -0.06, 0.0, 0.08);
        }
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            // Moving entities occasionally leave small ground droplets behind them.
            serverLevel.sendParticles((ParticleOptions)BLOOD_DARK, entity.getX() + RNG.nextGaussian() * 0.15, entity.getY() + 0.02, entity.getZ() + RNG.nextGaussian() * 0.15, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ===== REGENERATION EFFECTS =====

    /**
     * Spawns progressive regeneration particles from stump to limb tip.
     *
     * @param entity entity regrowing the limb
     * @param type limb being restored
     * @param progress regeneration progress in the range {@code 0.0f-1.0f}
     */
    public static void spawnRegenParticles(LivingEntity entity, LimbType type, float progress) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 stump = LimbParticles.getStumpPos(entity, type);
        Vec3 fullTip = LimbParticles.getLimbOffset(entity, type);
        float growFrac = Math.min(progress, 1.0f);
        double sx = entity.getX() + stump.x;
        double sy = entity.getY() + stump.y;
        double sz = entity.getZ() + stump.z;
        double tx = entity.getX() + fullTip.x;
        double ty = entity.getY() + fullTip.y;
        double tz = entity.getZ() + fullTip.z;
        int count = 3 + (int)(progress * 8.0f);
        double time = (double)entity.tickCount * 0.12;
        for (int i = 0; i < count; ++i) {
            // Sample positions from the stump outward to the current growth frontier.
            float t = (float)i / (float)count * growFrac;
            double px = sx + (tx - sx) * (double)t;
            double py = sy + (ty - sy) * (double)t;
            double pz = sz + (tz - sz) * (double)t;
            double spiralR = 0.06 + (double)progress * 0.05;
            double angleA = time * 5.0 + (double)t * 14.0;
            serverLevel.sendParticles((ParticleOptions)REGEN_GREEN, px + Math.cos(angleA) * spiralR, py, pz + Math.sin(angleA) * spiralR, 1, 0.0, 0.005, 0.0, 0.015);
            double angleB = angleA + Math.PI;
            serverLevel.sendParticles((ParticleOptions)REGEN_CYAN, px + Math.cos(angleB) * spiralR, py, pz + Math.sin(angleB) * spiralR, 1, 0.0, 0.005, 0.0, 0.015);
        }
        double tipX = sx + (tx - sx) * (double)growFrac;
        double tipY = sy + (ty - sy) * (double)growFrac;
        double tipZ = sz + (tz - sz) * (double)growFrac;
        if (progress < 0.25f) {
            // Bone phase: pale dust and enchant particles emphasize skeletal reconstruction.
            if (RNG.nextInt(2) == 0) {
                serverLevel.sendParticles((ParticleOptions)BONE_DUST, tipX + RNG.nextGaussian() * 0.08, tipY + RNG.nextGaussian() * 0.06, tipZ + RNG.nextGaussian() * 0.08, 1, 0.0, 0.02, 0.0, 0.02);
            }
            if (RNG.nextInt(4) == 0) {
                serverLevel.sendParticles((ParticleOptions)ParticleTypes.ENCHANT, tipX + RNG.nextGaussian() * 0.12, tipY + 0.1, tipZ + RNG.nextGaussian() * 0.12, 1, 0.0, -0.1, 0.0, 0.1);
            }
        } else if (progress < 0.5f) {
            // Muscle phase: denser red particles and heavier droplets.
            if (RNG.nextInt(2) == 0) {
                serverLevel.sendParticles((ParticleOptions)MUSCLE_RED, tipX + RNG.nextGaussian() * 0.08, tipY + RNG.nextGaussian() * 0.06, tipZ + RNG.nextGaussian() * 0.08, 1, 0.0, 0.01, 0.0, 0.02);
            }
            if (RNG.nextInt(5) == 0) {
                serverLevel.sendParticles((ParticleOptions)ParticleTypes.DRIPPING_OBSIDIAN_TEAR, tipX + RNG.nextGaussian() * 0.06, tipY, tipZ + RNG.nextGaussian() * 0.06, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else if (RNG.nextInt(2) == 0) {
            // Flesh phase: pink tissue particles fill out the nearly finished limb.
            serverLevel.sendParticles((ParticleOptions)FLESH_PINK, tipX + RNG.nextGaussian() * 0.08, tipY + RNG.nextGaussian() * 0.06, tipZ + RNG.nextGaussian() * 0.08, 1, 0.0, 0.015, 0.0, 0.02);
        }
        if (RNG.nextInt(3) == 0) {
            serverLevel.sendParticles((ParticleOptions)REGEN_WHITE, tipX + RNG.nextGaussian() * 0.05, tipY + RNG.nextGaussian() * 0.04, tipZ + RNG.nextGaussian() * 0.05, 1, 0.0, 0.015, 0.0, 0.025);
        }
        if (progress > 0.3f && RNG.nextInt(4) == 0) {
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, tipX + RNG.nextGaussian() * 0.04, tipY, tipZ + RNG.nextGaussian() * 0.04, 1, 0.0, 0.012, 0.0, 0.006);
        }
    }

    /**
     * Spawns the finishing particle burst when a limb reaches full restoration.
     *
     * @param entity entity whose limb was restored
     * @param type limb that finished restoring
     */
    public static void spawnRegenCompleteBurst(LivingEntity entity, LimbType type) {
        double r;
        double a;
        int i;
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 offset = LimbParticles.getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;
        for (i = 0; i < 24; ++i) {
            // Outer green ring explodes away from the restored limb tip.
            a = Math.toRadians(15.0 * (double)i);
            r = 0.5;
            serverLevel.sendParticles((ParticleOptions)REGEN_GREEN, px + Math.cos(a) * r, py, pz + Math.sin(a) * r, 1, Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12, 0.1);
        }
        for (i = 0; i < 16; ++i) {
            // Offset inner cyan ring adds layered depth to the completion burst.
            a = Math.toRadians(22.5 * (double)i + 11.25);
            r = 0.3;
            serverLevel.sendParticles((ParticleOptions)REGEN_CYAN, px + Math.cos(a) * r, py + 0.05, pz + Math.sin(a) * r, 1, Math.cos(a) * 0.06, 0.04, Math.sin(a) * 0.06, 0.08);
        }
        for (i = 0; i < 8; ++i) {
            // Soul fire flame pillars emphasize the supernatural finish of RCT regrowth.
            a = Math.toRadians(45.0 * (double)i);
            r = 0.35;
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, px + Math.cos(a) * r, py - 0.2 + RNG.nextDouble() * 0.1, pz + Math.sin(a) * r, 1, 0.0, 0.15 + RNG.nextDouble() * 0.08, 0.0, 0.05);
        }
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLASH, px, py, pz, 1, 0.2, 0.2, 0.2, 0.03);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, px, py, pz, 12, 0.3, 0.3, 0.3, 0.06);
        for (i = 0; i < 6; ++i) {
            serverLevel.sendParticles((ParticleOptions)REGEN_WHITE, px + RNG.nextGaussian() * 0.25, py + RNG.nextDouble() * 0.5, pz + RNG.nextGaussian() * 0.25, 1, RNG.nextGaussian() * 0.05, 0.08, RNG.nextGaussian() * 0.05, 0.06);
        }
        for (i = 0; i < 6; ++i) {
            a = Math.toRadians(60.0 * (double)i);
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.SOUL, px + Math.cos(a) * 0.4, py + 0.2 + RNG.nextDouble() * 0.3, pz + Math.sin(a) * 0.4, 1, Math.cos(a) * 0.06, 0.1, Math.sin(a) * 0.06, 0.05);
        }
    }

    // ===== LIMB POSITION HELPERS =====

    /**
     * Approximates the world-space offset of the end point of a limb on the entity model.
     *
     * @param entity entity being sampled
     * @param type limb type to locate
     * @return local offset from the entity origin to that limb's main sever point
     */
    public static Vec3 getLimbOffset(LivingEntity entity, LimbType type) {
        float yRot = (float)Math.toRadians(entity.getYRot());
        double sinY = Math.sin(yRot);
        double cosY = Math.cos(yRot);
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_ARM -> new Vec3(cosY * 0.35, (double)entity.getEyeHeight() * 0.7, sinY * 0.35);
            case RIGHT_ARM -> new Vec3(-cosY * 0.35, (double)entity.getEyeHeight() * 0.7, -sinY * 0.35);
            case LEFT_LEG -> new Vec3(cosY * 0.12, (double)entity.getEyeHeight() * 0.3, sinY * 0.12);
            case RIGHT_LEG -> new Vec3(-cosY * 0.12, (double)entity.getEyeHeight() * 0.3, -sinY * 0.12);
            case HEAD -> new Vec3(0.0, (double)entity.getEyeHeight() * 0.95, 0.0);
        };
    }

    /**
     * Approximates the stump position used as the start of the regrowth effect.
     *
     * @param entity entity being sampled
     * @param type limb type to locate
     * @return local offset from the entity origin to the regrowth stump origin
     */
    private static Vec3 getStumpPos(LivingEntity entity, LimbType type) {
        float yRot = (float)Math.toRadians(entity.getYRot());
        double sinY = Math.sin(yRot);
        double cosY = Math.cos(yRot);
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_ARM -> new Vec3(cosY * 0.35, (double)entity.getEyeHeight() * 0.85, sinY * 0.35);
            case RIGHT_ARM -> new Vec3(-cosY * 0.35, (double)entity.getEyeHeight() * 0.85, -sinY * 0.35);
            case LEFT_LEG -> new Vec3(cosY * 0.12, (double)entity.getEyeHeight() * 0.45, sinY * 0.12);
            case RIGHT_LEG -> new Vec3(-cosY * 0.12, (double)entity.getEyeHeight() * 0.45, -sinY * 0.12);
            case HEAD -> new Vec3(0.0, (double)entity.getEyeHeight() * 0.85, 0.0);
        };
    }
}
