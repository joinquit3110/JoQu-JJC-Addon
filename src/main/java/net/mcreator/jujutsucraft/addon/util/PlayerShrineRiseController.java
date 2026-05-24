package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.entity.EntityMalevolentShrine2Entity;
import net.mcreator.jujutsucraft.entity.EntityMalevolentShrineEntity;
import net.mcreator.jujutsucraft.entity.EntitySkullEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PlayerShrineRiseController {
    public static final String KEY_ENABLED = "jjkbrp_player_shrine_rise";

    private static final String KEY_FORM = "jjkbrp_shrine_rise_form";
    private static final String KEY_FINAL_X = "jjkbrp_shrine_rise_final_x";
    private static final String KEY_FINAL_Y = "jjkbrp_shrine_rise_final_y";
    private static final String KEY_FINAL_Z = "jjkbrp_shrine_rise_final_z";
    private static final String KEY_START_Y = "jjkbrp_shrine_rise_start_y";
    private static final String KEY_PLATFORM_OFFSET = "jjkbrp_shrine_rise_platform_offset";
    private static final String KEY_HAND_FORWARD_OFFSET = "jjkbrp_shrine_rise_hand_forward_offset";
    private static final String KEY_RISE_TICK = "jjkbrp_shrine_rise_tick";
    private static final String KEY_RING_INDEX = "jjkbrp_shrine_ring_index";

    private static final int FORM_INCOMPLETE = 0;
    private static final int FORM_COMPLETE = 1;
    private static final int COMPLETE_RING_SEGMENTS = 18;
    private static final int COMPLETE_RISE_TICKS = 42;
    private static final int INCOMPLETE_RISE_TICKS = 38;
    private static final double COMPLETE_PLATFORM_OFFSET = 6.0D;
    private static final double INCOMPLETE_PLATFORM_OFFSET = 10.0D;
    private static final double INCOMPLETE_HAND_FORWARD_OFFSET = 3.55D;

    private PlayerShrineRiseController() {
    }

    public static void prepareSpawn(ServerLevel level, Entity shrine, Player caster, int selectedForm) {
        if (level == null || shrine == null || caster == null || !isShrineVisualEntity(shrine)) {
            return;
        }

        boolean incomplete = selectedForm == DomainMasteryData.FORM_INCOMPLETE || shrine instanceof EntityMalevolentShrine2Entity;
        double platformOffset = incomplete ? INCOMPLETE_PLATFORM_OFFSET : COMPLETE_PLATFORM_OFFSET;
        double handForwardOffset = incomplete ? INCOMPLETE_HAND_FORWARD_OFFSET : 0.0D;
        double platformX = caster.getX();
        double platformZ = caster.getZ();
        double finalY = findGroundY(level, platformX, caster.getY(), platformZ);
        double finalX = platformX;
        double finalZ = platformZ;

        if (incomplete) {
            Vec3 front = frontVector(shrine.getYRot());
            finalX -= front.x * handForwardOffset;
            finalZ -= front.z * handForwardOffset;
        }

        double startY = finalY - platformOffset;
        CompoundTag data = shrine.getPersistentData();
        data.putBoolean(KEY_ENABLED, true);
        data.putInt(KEY_FORM, incomplete ? FORM_INCOMPLETE : FORM_COMPLETE);
        data.putDouble(KEY_FINAL_X, finalX);
        data.putDouble(KEY_FINAL_Y, finalY);
        data.putDouble(KEY_FINAL_Z, finalZ);
        data.putDouble(KEY_START_Y, startY);
        data.putDouble(KEY_PLATFORM_OFFSET, platformOffset);
        data.putDouble(KEY_HAND_FORWARD_OFFSET, handForwardOffset);
        data.putInt(KEY_RISE_TICK, 0);
        data.putInt(KEY_RING_INDEX, 0);
        data.putString("OWNER_UUID", caster.getStringUUID());
        data.putDouble("jjkbrp_radius_multiplier", caster.getPersistentData().getDouble("jjkbrp_radius_multiplier"));
        float decorationScale = PehkuiDomainScaleUtil.resolveDecorationScale(caster.getPersistentData());
        data.putDouble("jjkbrp_decoration_scale", decorationScale);

        shrine.setNoGravity(true);
        shrine.setInvulnerable(true);
        shrine.moveTo(finalX, startY, finalZ, shrine.getYRot(), shrine.getXRot());
        shrine.setDeltaMovement(Vec3.ZERO);
        PehkuiDomainScaleUtil.applyDecorationScale(shrine, caster.getPersistentData());
        playRiseStartEffects(level, finalX, finalY, finalZ, decorationScale);

        if (!incomplete) {
            data.putBoolean("flag_start", true);
            shrine.setInvisible(true);
        }
    }

    public static void tick(Entity shrine) {
        if (shrine == null || shrine.level().isClientSide() || !isPlayerShrineRise(shrine)) {
            return;
        }
        if (!(shrine.level() instanceof ServerLevel level)) {
            return;
        }

        CompoundTag data = shrine.getPersistentData();
        boolean incomplete = data.getInt(KEY_FORM) == FORM_INCOMPLETE;
        double finalX = data.getDouble(KEY_FINAL_X);
        double finalY = data.getDouble(KEY_FINAL_Y);
        double finalZ = data.getDouble(KEY_FINAL_Z);
        double startY = data.getDouble(KEY_START_Y);

        shrine.setNoGravity(true);
        shrine.setDeltaMovement(Vec3.ZERO);

        if (!incomplete && data.getInt(KEY_RING_INDEX) < COMPLETE_RING_SEGMENTS) {
            shrine.setInvisible(true);
            moveShrine(shrine, finalX, startY, finalZ);
            spawnNextCompleteRingSkull(level, shrine, data);
            return;
        }

        shrine.setInvisible(false);
        int riseTicks = incomplete ? INCOMPLETE_RISE_TICKS : COMPLETE_RISE_TICKS;
        int currentTick = data.getInt(KEY_RISE_TICK);
        if (currentTick >= riseTicks) {
            moveShrine(shrine, finalX, finalY, finalZ);
            return;
        }
        int nextTick = currentTick + 1;
        data.putInt(KEY_RISE_TICK, nextTick);

        double progress = Math.min(1.0D, (double)Math.min(nextTick, riseTicks) / (double)riseTicks);
        double eased = smoothStep(progress);
        double currentY = startY + (finalY - startY) * eased;
        Vec3 shake = riseShake(nextTick, progress);
        moveShrine(shrine, finalX + shake.x, currentY, finalZ + shake.z);
        playRiseTickEffects(level, finalX, currentY, finalZ, nextTick, progress, decorationScale(data));
    }

    public static boolean isPlayerShrineRise(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(KEY_ENABLED);
    }

    private static boolean isShrineVisualEntity(Entity entity) {
        return entity instanceof EntityMalevolentShrineEntity || entity instanceof EntityMalevolentShrine2Entity;
    }

    private static void moveShrine(Entity shrine, double x, double y, double z) {
        shrine.moveTo(x, y, z, shrine.getYRot(), shrine.getXRot());
        shrine.setDeltaMovement(Vec3.ZERO);
        shrine.fallDistance = 0.0F;
    }

    private static void spawnNextCompleteRingSkull(ServerLevel level, Entity shrine, CompoundTag data) {
        int index = data.getInt(KEY_RING_INDEX);
        if (index >= COMPLETE_RING_SEGMENTS) {
            return;
        }

        float orbitAngle = shrine.getYRot() + index * 20.0F;
        float skullYaw = orbitAngle;
        double radius = Math.max(1.0D, shrine.getBbWidth() - 2.5D);
        double x = data.getDouble(KEY_FINAL_X) + Math.cos(Math.toRadians(orbitAngle + 90.0F)) * radius;
        double z = data.getDouble(KEY_FINAL_Z) + Math.sin(Math.toRadians(orbitAngle + 90.0F)) * radius;
        double y = findGroundY(level, x, data.getDouble(KEY_FINAL_Y), z);

        @SuppressWarnings({"rawtypes", "unchecked"})
        EntitySkullEntity skull = new EntitySkullEntity((EntityType)JujutsucraftModEntities.ENTITY_SKULL.get(), (Level)level);
        skull.moveTo(x, y, z, skullYaw, 0.0F);
        skull.setYRot(skullYaw);
        skull.setYBodyRot(skullYaw);
        skull.setYHeadRot(skullYaw);
        skull.yRotO = skullYaw;
        skull.yBodyRotO = skullYaw;
        skull.yHeadRotO = skullYaw;
        skull.setInvulnerable(true);
        skull.setNoGravity(true);
        PehkuiDomainScaleUtil.applyScale(skull, PehkuiDomainScaleUtil.resolveDecorationScale(data));
        playSkullSpawnEffects(level, x, y, z, index, decorationScale(data));
        skull.getPersistentData().putDouble("NameRanged_ranged", data.getDouble("NameRanged_ranged"));
        skull.getPersistentData().putString("OWNER_UUID", data.getString("OWNER_UUID"));
        level.addFreshEntity(skull);

        data.putInt(KEY_RING_INDEX, index + 1);
    }

    private static void playRiseStartEffects(ServerLevel level, double x, double y, double z, double scale) {
        BlockPos pos = BlockPos.containing(x, y, z);
        double s = Math.max(0.5D, scale);
        level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2.4F, 0.45F);
        level.playSound(null, pos, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.8F, 0.62F);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.55F, 0.45F);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.4F, 0.55F);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.2D * s, z, 2, 1.2D * s, 0.15D * s, 1.2D * s, 0.02D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.2D * s, z, (int)Math.round(90 * s), 3.8D * s, 0.35D * s, 3.8D * s, 0.025D);
        level.sendParticles(ParticleTypes.SOUL, x, y + 0.4D * s, z, (int)Math.round(70 * s), 3.1D * s, 0.45D * s, 3.1D * s, 0.055D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.65D * s, z, (int)Math.round(36 * s), 2.4D * s, 0.5D * s, 2.4D * s, 0.035D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.35D * s, z, (int)Math.round(44 * s), 2.6D * s, 0.3D * s, 2.6D * s, 0.025D);
    }

    private static void playSkullSpawnEffects(ServerLevel level, double x, double y, double z, int index, double scale) {
        double s = Math.max(0.5D, scale);
        float pitch = 0.62F + (index % 6) * 0.035F;
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.BONE_BLOCK_PLACE, SoundSource.HOSTILE, 1.15F, pitch);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.SCULK_BLOCK_HIT, SoundSource.HOSTILE, 0.8F, 0.42F + (index % 3) * 0.05F);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.6F, 0.7F);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.45D * s, z, (int)Math.round(22 * s), 0.65D * s, 0.35D * s, 0.65D * s, 0.025D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.35D * s, z, (int)Math.round(16 * s), 0.45D * s, 0.25D * s, 0.45D * s, 0.018D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.65D * s, z, (int)Math.round(6 * s), 0.25D * s, 0.18D * s, 0.25D * s, 0.012D);
    }

    private static Vec3 riseShake(int tick, double progress) {
        double strength = Math.sin(progress * Math.PI) * 0.09D;
        if (strength <= 1.0E-4D) {
            return Vec3.ZERO;
        }
        double x = Math.sin(tick * 1.73D) * strength;
        double z = Math.cos(tick * 1.31D) * strength;
        return new Vec3(x, 0.0D, z);
    }

    private static void playRiseTickEffects(ServerLevel level, double x, double y, double z, int tick, double progress, double scale) {
        double s = Math.max(0.5D, scale);
        if (tick % 3 == 0) {
            double spread = (2.2D + progress * 2.2D) * s;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.25D * s, z, (int)Math.round(22 * s), spread, 0.2D * s, spread, 0.02D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.55D * s, z, (int)Math.round(10 * s), spread * 0.55D, 0.2D * s, spread * 0.55D, 0.015D);
        }
        if (tick % 6 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.0D * s, z, (int)Math.round(8 * s), 1.4D * s, 0.55D * s, 1.4D * s, 0.025D);
        }
        if (tick % 7 == 0) {
            level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 0.9F, 0.48F + (float)(progress * 0.2D));
        }
        if (tick % 13 == 0) {
            level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.SCULK_SHRIEKER_STEP, SoundSource.HOSTILE, 0.8F, 0.55F);
        }
    }

    private static double decorationScale(CompoundTag data) {
        if (data == null) {
            return 1.0D;
        }
        if (data.contains("jjkbrp_decoration_scale")) {
            return Math.max(0.5D, Math.min(3.0D, data.getDouble("jjkbrp_decoration_scale")));
        }
        return PehkuiDomainScaleUtil.resolveDecorationScale(data);
    }

    private static double findGroundY(ServerLevel level, double x, double y, double z) {
        double result = y;
        for (int i = 0; i < 100 && result > level.getMinBuildHeight(); i++) {
            BlockPos below = BlockPos.containing(x, result - 1.0D, z);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                break;
            }
            result -= 1.0D;
        }
        return result;
    }

    private static Vec3 frontVector(float yaw) {
        double radians = Math.toRadians(yaw + 90.0F);
        return new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
    }

    private static Vec3 rightVector(float yaw) {
        Vec3 front = frontVector(yaw);
        return new Vec3(-front.z, 0.0D, front.x);
    }

    private static double smoothStep(double progress) {
        return progress * progress * (3.0D - 2.0D * progress);
    }
}
