package net.mcreator.jujutsucraft.addon.limb;

import java.util.Random;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class LimbParticles {
    private static final Random RNG = new Random();

    // Blood colors — varying shades for realism
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.5f, 0.01f, 0.01f), 1.4f);
    private static final DustParticleOptions BLOOD_BRIGHT = new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.03f), 1.0f);
    private static final DustParticleOptions BLOOD_MIST = new DustParticleOptions(new Vector3f(0.4f, 0.02f, 0.02f), 0.6f);

    // Regen colors
    private static final DustParticleOptions REGEN_GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 0.8f);
    private static final DustParticleOptions REGEN_CYAN = new DustParticleOptions(new Vector3f(0.1f, 0.8f, 0.7f), 0.7f);
    private static final DustParticleOptions REGEN_WHITE = new DustParticleOptions(new Vector3f(0.95f, 0.98f, 0.95f), 0.5f);

    // Phase-specific colors
    private static final DustParticleOptions BONE_DUST = new DustParticleOptions(new Vector3f(0.92f, 0.88f, 0.78f), 0.6f);
    private static final DustParticleOptions MUSCLE_RED = new DustParticleOptions(new Vector3f(0.7f, 0.15f, 0.1f), 0.7f);
    private static final DustParticleOptions FLESH_PINK = new DustParticleOptions(new Vector3f(0.85f, 0.5f, 0.45f), 0.6f);

    private LimbParticles() {}

    // ══════════════════════════════════════════════════════════════
    // BLOOD — Limb Severance
    // ══════════════════════════════════════════════════════════════

    /**
     * Massive blood burst when a limb is severed.
     * Multi-layered: dark splatter core + bright spray + mist cloud + drips.
     */
    public static void spawnSeverBloodBurst(LivingEntity entity, LimbType type) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 offset = getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;

        // Layer 1: Dark blood core burst — dense, close
        for (int i = 0; i < 30; ++i) {
            double vx = RNG.nextGaussian() * 0.12;
            double vy = RNG.nextGaussian() * 0.08 + 0.08;
            double vz = RNG.nextGaussian() * 0.12;
            serverLevel.sendParticles(BLOOD_DARK, px, py, pz, 1, vx, vy, vz, 0.35);
        }

        // Layer 2: Bright arterial spray — larger, faster
        for (int i = 0; i < 20; ++i) {
            double angle = Math.toRadians(18.0 * i);
            double speed = 0.15 + RNG.nextDouble() * 0.2;
            double vx = Math.cos(angle) * speed + RNG.nextGaussian() * 0.04;
            double vy = 0.1 + RNG.nextDouble() * 0.15;
            double vz = Math.sin(angle) * speed + RNG.nextGaussian() * 0.04;
            serverLevel.sendParticles(BLOOD_BRIGHT, px, py, pz, 1, vx, vy, vz, 0.4);
        }

        // Layer 3: Blood mist cloud — fine, lingering
        for (int i = 0; i < 15; ++i) {
            serverLevel.sendParticles(BLOOD_MIST,
                    px + RNG.nextGaussian() * 0.2,
                    py + RNG.nextGaussian() * 0.15,
                    pz + RNG.nextGaussian() * 0.2,
                    1, RNG.nextGaussian() * 0.02, 0.01, RNG.nextGaussian() * 0.02, 0.05);
        }

        // Layer 4: Crimson spore cloud (vanilla particle — nice organic feel)
        for (int i = 0; i < 50; ++i) {
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    px + RNG.nextGaussian() * 0.15, py + RNG.nextGaussian() * 0.1,
                    pz + RNG.nextGaussian() * 0.15,
                    1, RNG.nextGaussian() * 0.15, RNG.nextGaussian() * 0.1 + 0.08,
                    RNG.nextGaussian() * 0.15, 0.25);
        }

        // Layer 5: Gravity-affected blood arcs (lava drip = drops down)
        for (int i = 0; i < 8; ++i) {
            serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                    px + RNG.nextGaussian() * 0.3,
                    py + 0.1 + RNG.nextDouble() * 0.2,
                    pz + RNG.nextGaussian() * 0.3,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Layer 6: Wound smoke
        for (int i = 0; i < 5; ++i) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    px + RNG.nextGaussian() * 0.1,
                    py + RNG.nextDouble() * 0.15,
                    pz + RNG.nextGaussian() * 0.1,
                    1, 0.0, 0.03, 0.0, 0.02);
        }

        // Ground blood pool — flat ring of particles at feet
        double groundY = entity.getY() + 0.05;
        for (int i = 0; i < 12; ++i) {
            double a = Math.toRadians(30.0 * i + RNG.nextDouble() * 15.0);
            double r = 0.3 + RNG.nextDouble() * 0.4;
            serverLevel.sendParticles(BLOOD_DARK,
                    px + Math.cos(a) * r, groundY, pz + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Directional blood spray when hit from a specific direction.
     */
    public static void spawnBloodSpray(LivingEntity entity, LimbType type, Vec3 direction) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 offset = getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;
        Vec3 dir = direction.normalize();

        // Main spray in hit direction
        for (int i = 0; i < 25; ++i) {
            double speed = 0.15 + RNG.nextDouble() * 0.35;
            double spread = 0.08;
            double vx = dir.x * speed + RNG.nextGaussian() * spread;
            double vy = dir.y * speed + RNG.nextGaussian() * spread + 0.03;
            double vz = dir.z * speed + RNG.nextGaussian() * spread;
            DustParticleOptions dust = RNG.nextBoolean() ? BLOOD_DARK : BLOOD_BRIGHT;
            serverLevel.sendParticles(dust, px, py, pz, 1, vx, vy, vz, 0.5);
        }

        // Secondary splatter — wider, slower
        for (int i = 0; i < 10; ++i) {
            double speed = 0.05 + RNG.nextDouble() * 0.1;
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    px, py, pz, 1,
                    dir.x * speed + RNG.nextGaussian() * 0.1,
                    0.05 + RNG.nextDouble() * 0.05,
                    dir.z * speed + RNG.nextGaussian() * 0.1, 0.2);
        }
    }

    /**
     * Ongoing blood drip from severed stump.
     */
    public static void spawnBloodDrip(LivingEntity entity, LimbType type) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 offset = getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;

        // Main drip stream
        int count = 3 + RNG.nextInt(3);
        for (int i = 0; i < count; ++i) {
            DustParticleOptions dust = RNG.nextInt(3) == 0 ? BLOOD_BRIGHT : BLOOD_DARK;
            serverLevel.sendParticles(dust,
                    px + RNG.nextGaussian() * 0.06,
                    py - 0.05 - RNG.nextDouble() * 0.1,
                    pz + RNG.nextGaussian() * 0.06,
                    1, 0.0, -0.06, 0.0, 0.08);
        }



        // Ground blood trail when moving
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            serverLevel.sendParticles(BLOOD_DARK,
                    entity.getX() + RNG.nextGaussian() * 0.15,
                    entity.getY() + 0.02,
                    entity.getZ() + RNG.nextGaussian() * 0.15,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REGEN — Limb Regeneration
    // ══════════════════════════════════════════════════════════════

    /**
     * Regeneration particles — double helix spiral with phase-specific effects.
     */
    public static void spawnRegenParticles(LivingEntity entity, LimbType type, float progress) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 stump = getStumpPos(entity, type);
        Vec3 fullTip = getLimbOffset(entity, type);
        float growFrac = Math.min(progress, 1.0f);

        double sx = entity.getX() + stump.x;
        double sy = entity.getY() + stump.y;
        double sz = entity.getZ() + stump.z;
        double tx = entity.getX() + fullTip.x;
        double ty = entity.getY() + fullTip.y;
        double tz = entity.getZ() + fullTip.z;

        int count = 3 + (int)(progress * 8.0f);
        double time = entity.tickCount * 0.12;

        // ── Double helix spiral ──
        for (int i = 0; i < count; ++i) {
            float t = (float) i / (float) count * growFrac;
            double px = sx + (tx - sx) * t;
            double py = sy + (ty - sy) * t;
            double pz = sz + (tz - sz) * t;

            double spiralR = 0.06 + progress * 0.05;

            // Helix A (green)
            double angleA = time * 5.0 + t * 14.0;
            serverLevel.sendParticles(REGEN_GREEN,
                    px + Math.cos(angleA) * spiralR, py,
                    pz + Math.sin(angleA) * spiralR,
                    1, 0.0, 0.005, 0.0, 0.015);

            // Helix B (cyan, 180° offset)
            double angleB = angleA + Math.PI;
            serverLevel.sendParticles(REGEN_CYAN,
                    px + Math.cos(angleB) * spiralR, py,
                    pz + Math.sin(angleB) * spiralR,
                    1, 0.0, 0.005, 0.0, 0.015);
        }

        // ── Phase-specific particles at growth tip ──
        double tipX = sx + (tx - sx) * growFrac;
        double tipY = sy + (ty - sy) * growFrac;
        double tipZ = sz + (tz - sz) * growFrac;

        if (progress < 0.25f) {
            // BONE phase: white ash + enchant shimmer
            if (RNG.nextInt(2) == 0) {
                serverLevel.sendParticles(BONE_DUST,
                        tipX + RNG.nextGaussian() * 0.08,
                        tipY + RNG.nextGaussian() * 0.06,
                        tipZ + RNG.nextGaussian() * 0.08,
                        1, 0.0, 0.02, 0.0, 0.02);
            }
            if (RNG.nextInt(4) == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        tipX + RNG.nextGaussian() * 0.12, tipY + 0.1,
                        tipZ + RNG.nextGaussian() * 0.12,
                        1, 0.0, -0.1, 0.0, 0.1);
            }
        } else if (progress < 0.5f) {
            // MUSCLE phase: red particles + wet drips
            if (RNG.nextInt(2) == 0) {
                serverLevel.sendParticles(MUSCLE_RED,
                        tipX + RNG.nextGaussian() * 0.08,
                        tipY + RNG.nextGaussian() * 0.06,
                        tipZ + RNG.nextGaussian() * 0.08,
                        1, 0.0, 0.01, 0.0, 0.02);
            }
            if (RNG.nextInt(5) == 0) {
                serverLevel.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                        tipX + RNG.nextGaussian() * 0.06, tipY,
                        tipZ + RNG.nextGaussian() * 0.06,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        } else {
            // FLESH phase: pink particles
            if (RNG.nextInt(2) == 0) {
                serverLevel.sendParticles(FLESH_PINK,
                        tipX + RNG.nextGaussian() * 0.08,
                        tipY + RNG.nextGaussian() * 0.06,
                        tipZ + RNG.nextGaussian() * 0.08,
                        1, 0.0, 0.015, 0.0, 0.02);
            }
        }

        // ── Pulsing glow at growth tip ──
        if (RNG.nextInt(3) == 0) {
            serverLevel.sendParticles(REGEN_WHITE,
                    tipX + RNG.nextGaussian() * 0.05,
                    tipY + RNG.nextGaussian() * 0.04,
                    tipZ + RNG.nextGaussian() * 0.05,
                    1, 0.0, 0.015, 0.0, 0.025);
        }
        if (progress > 0.3f && RNG.nextInt(4) == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    tipX + RNG.nextGaussian() * 0.04, tipY,
                    tipZ + RNG.nextGaussian() * 0.04,
                    1, 0.0, 0.012, 0.0, 0.006);
        }
    }

    /**
     * Completion burst — shockwave ring + energy pillars + flash.
     */
    public static void spawnRegenCompleteBurst(LivingEntity entity, LimbType type) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 offset = getLimbOffset(entity, type);
        double px = entity.getX() + offset.x;
        double py = entity.getY() + offset.y;
        double pz = entity.getZ() + offset.z;

        // Expanding shockwave ring
        for (int i = 0; i < 24; ++i) {
            double a = Math.toRadians(15.0 * i);
            double r = 0.5;
            serverLevel.sendParticles(REGEN_GREEN,
                    px + Math.cos(a) * r, py, pz + Math.sin(a) * r,
                    1, Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12, 0.1);
        }

        // Inner ring (cyan)
        for (int i = 0; i < 16; ++i) {
            double a = Math.toRadians(22.5 * i + 11.25);
            double r = 0.3;
            serverLevel.sendParticles(REGEN_CYAN,
                    px + Math.cos(a) * r, py + 0.05, pz + Math.sin(a) * r,
                    1, Math.cos(a) * 0.06, 0.04, Math.sin(a) * 0.06, 0.08);
        }

        // Rising energy pillars (soul fire flame)
        for (int i = 0; i < 8; ++i) {
            double a = Math.toRadians(45.0 * i);
            double r = 0.35;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    px + Math.cos(a) * r,
                    py - 0.2 + RNG.nextDouble() * 0.1,
                    pz + Math.sin(a) * r,
                    1, 0.0, 0.15 + RNG.nextDouble() * 0.08, 0.0, 0.05);
        }

        // Central flash
        serverLevel.sendParticles(ParticleTypes.FLASH, px, py, pz, 1, 0.2, 0.2, 0.2, 0.03);

        // End rod sparkles
        serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 12, 0.3, 0.3, 0.3, 0.06);

        // Totem-like sparkle
        for (int i = 0; i < 6; ++i) {
            serverLevel.sendParticles(REGEN_WHITE,
                    px + RNG.nextGaussian() * 0.25,
                    py + RNG.nextDouble() * 0.5,
                    pz + RNG.nextGaussian() * 0.25,
                    1, RNG.nextGaussian() * 0.05, 0.08, RNG.nextGaussian() * 0.05, 0.06);
        }

        // Soul particles — ethereal completion feel
        for (int i = 0; i < 6; ++i) {
            double a = Math.toRadians(60.0 * i);
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    px + Math.cos(a) * 0.4,
                    py + 0.2 + RNG.nextDouble() * 0.3,
                    pz + Math.sin(a) * 0.4,
                    1, Math.cos(a) * 0.06, 0.1, Math.sin(a) * 0.06, 0.05);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Position helpers
    // ══════════════════════════════════════════════════════════════

    public static Vec3 getLimbOffset(LivingEntity entity, LimbType type) {
        float yRot = (float) Math.toRadians(entity.getYRot());
        double sinY = Math.sin(yRot);
        double cosY = Math.cos(yRot);
        return switch (type) {
            case LEFT_ARM -> new Vec3(cosY * 0.35, entity.getEyeHeight() * 0.7, sinY * 0.35);
            case RIGHT_ARM -> new Vec3(-cosY * 0.35, entity.getEyeHeight() * 0.7, -sinY * 0.35);
            case LEFT_LEG -> new Vec3(cosY * 0.12, entity.getEyeHeight() * 0.3, sinY * 0.12);
            case RIGHT_LEG -> new Vec3(-cosY * 0.12, entity.getEyeHeight() * 0.3, -sinY * 0.12);
            case HEAD -> new Vec3(0.0, entity.getEyeHeight() * 0.95, 0.0);
        };
    }

    private static Vec3 getStumpPos(LivingEntity entity, LimbType type) {
        float yRot = (float) Math.toRadians(entity.getYRot());
        double sinY = Math.sin(yRot);
        double cosY = Math.cos(yRot);
        return switch (type) {
            case LEFT_ARM -> new Vec3(cosY * 0.35, entity.getEyeHeight() * 0.85, sinY * 0.35);
            case RIGHT_ARM -> new Vec3(-cosY * 0.35, entity.getEyeHeight() * 0.85, -sinY * 0.35);
            case LEFT_LEG -> new Vec3(cosY * 0.12, entity.getEyeHeight() * 0.45, sinY * 0.12);
            case RIGHT_LEG -> new Vec3(-cosY * 0.12, entity.getEyeHeight() * 0.45, -sinY * 0.12);
            case HEAD -> new Vec3(0.0, entity.getEyeHeight() * 0.85, 0.0);
        };
    }
}
