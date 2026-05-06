package net.mcreator.jujutsucraft.addon.limb;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Centralized helper for limb-related sound playback.
 *
 * <p>The limb system layers multiple vanilla and custom sounds together to sell violent sever events,
 * the start of reverse cursed technique, periodic restoration pulses, and successful regrowth.</p>
 */
public final class LimbSounds {
    /** Custom electric shock sound reused by reverse cursed technique regeneration effects. */
    private static final SoundEvent ELECTRIC_SHOCK = SoundEvent.createVariableRangeEvent((ResourceLocation)new ResourceLocation("jujutsucraft", "electric_shock"));

    /**
     * Utility class; not meant to be instantiated.
     */
    private LimbSounds() {
    }

    /**
     * Plays the standard non-head sever sound stack.
     *
     * @param entity entity at whose position the sounds should be played
     */
    public static void playSeverSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        // Blend hurt, bone crack, and wet impact layers into a more visceral sever sound.
        level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.8f, 0.5f);
    }

    /**
     * Plays the sound stack that marks the start of regeneration.
     *
     * @param entity entity beginning reverse healing
     */
    public static void playRegenStartSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, ELECTRIC_SHOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    /**
     * Plays the periodic pulse sound during active regeneration.
     *
     * @param entity entity currently reversing a limb
     */
    public static void playRegenPulseSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    /**
     * Plays the completion sound stack after a limb finishes regrowing.
     *
     * @param entity entity whose limb finished restoring
     */
    public static void playRegenCompleteSound(LivingEntity entity) {
        Level level = entity.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        level.playSound(null, x, y, z, ELECTRIC_SHOCK, SoundSource.PLAYERS, 1.0f, 1.5f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.8f);
    }

    /**
     * Plays the heavier, more dramatic sound stack reserved for head sever events.
     *
     * @param entity entity whose head was severed
     */
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
