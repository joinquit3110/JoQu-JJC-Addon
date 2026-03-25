package net.mcreator.jujutsucraft.addon.limb;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class LimbSounds {
    private static final SoundEvent ELECTRIC_SHOCK = SoundEvent.createVariableRangeEvent(
        new ResourceLocation("jujutsucraft", "electric_shock"));

    private LimbSounds() {}

    public static void playSeverSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.8f, 0.5f);
    }

    public static void playRegenStartSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, ELECTRIC_SHOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    public static void playRegenPulseSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    public static void playRegenCompleteSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, ELECTRIC_SHOCK, SoundSource.PLAYERS, 1.0f, 1.5f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.8f);
    }

    public static void playHeadSeverSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.5f, 0.4f);
        level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 0.3f);
        level.playSound(null, x, y, z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 2.0f);
    }
}
