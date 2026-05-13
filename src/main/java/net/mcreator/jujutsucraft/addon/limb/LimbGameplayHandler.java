package net.mcreator.jujutsucraft.addon.limb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.mcreator.jujutsucraft.addon.limb.LimbCapabilityProvider;
import net.mcreator.jujutsucraft.addon.limb.LimbData;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies gameplay penalties caused by missing limbs.
 *
 * <p>This handler owns persistent attribute modifiers, forced item drops, sprint suppression, and
 * reduced jump strength so that severed limbs have tangible combat and movement consequences.</p>
 */
@Mod.EventBusSubscriber(modid="jjkblueredpurple")
public class LimbGameplayHandler {
    /** UUID used for the left-arm-related mining/utility modifier slot. */
    private static final UUID LEFT_ARM_MINING_UUID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000001");
    /** UUID used for the right-arm attack damage penalty modifier. */
    private static final UUID RIGHT_ARM_ATTACK_UUID = UUID.fromString("a1b2c3d4-2222-4000-8000-000000000002");
    /** UUID used for the leg movement speed penalty modifier. */
    private static final UUID LEG_SPEED_UUID = UUID.fromString("a1b2c3d4-3333-4000-8000-000000000003");
    /**
     * Tracks the previous vertical motion of each player so jump starts can be detected reliably.
     */
    private static final Map<UUID, Double> PREV_DELTA_Y = new ConcurrentHashMap<UUID, Double>();

    public static void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            PREV_DELTA_Y.remove(playerId);
        }
    }

    // ===== ATTRIBUTE MODIFIERS =====

    /**
     * Applies all limb-related attribute penalties to an entity.
     *
     * @param entity entity receiving penalties
     * @param data current limb capability data
     */
    public static void applyLimbDebuffs(LivingEntity entity, LimbData data) {
        LimbGameplayHandler.removeAllModifiers(entity);
        int missingArms = data.countSeveredArms();
        int missingLegs = data.countSeveredLegs();
        if (missingArms > 0) {
            // One arm removes 25% strike damage; both arms remove 50%.
            float damagePenalty = missingArms >= 2 ? 0.5f : 0.25f;
            AttributeInstance atkDmg = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atkDmg != null) {
                atkDmg.removeModifier(RIGHT_ARM_ATTACK_UUID);
                atkDmg.addTransientModifier(new AttributeModifier(RIGHT_ARM_ATTACK_UUID, "jjkbrp_limb_arm_strike_damage", (double)(-damagePenalty), AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)entity;
                // This persistent key is used by other systems to read the current strike penalty.
                sp.getPersistentData().putFloat("jjkbrp_strike_damage_penalty", damagePenalty);
            }
        }
        if (missingLegs > 0) {
            // One missing leg heavily slows movement; two missing legs nearly immobilize the player.
            double speedPenalty = missingLegs >= 2 ? -0.95 : -0.6;
            AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(LEG_SPEED_UUID);
                attr.addTransientModifier(new AttributeModifier(LEG_SPEED_UUID, "jjkbrp_limb_leg_slow", speedPenalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    /**
     * Removes every limb-related attribute modifier from the entity.
     *
     * @param entity entity to clean up
     */
    public static void removeAllModifiers(LivingEntity entity) {
        AttributeInstance moveSpd = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance atkDmg = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkDmg != null) {
            atkDmg.removeModifier(RIGHT_ARM_ATTACK_UUID);
        }
        if (moveSpd != null) {
            moveSpd.removeModifier(LEG_SPEED_UUID);
        }
    }

    /**
     * Reapplies or removes penalties based on the entity's current limb state.
     *
     * @param entity entity whose penalties should be refreshed
     * @param data current limb capability data
     */
    public static void refreshDebuffs(LivingEntity entity, LimbData data) {
        if (data.hasSeveredLimbs()) {
            LimbGameplayHandler.applyLimbDebuffs(entity, data);
        } else {
            LimbGameplayHandler.removeAllModifiers(entity);
        }
    }

    // ===== PER-TICK GAMEPLAY ENFORCEMENT =====

    /**
     * Enforces held-item drops, sprint restrictions, and jump suppression for missing limbs.
     *
     * @param event living tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (player.level().isClientSide) {
            return;
        }
        LimbCapabilityProvider.get((LivingEntity)player).ifPresent(data -> {
            ItemStack mainhand;
            ItemStack offhand;
            int missingLegs = data.countSeveredLegs();
            if (missingLegs >= 2) {
                // Players with no legs cannot sustain sprinting.
                player.setSprinting(false);
            }
            if (data.isLimbMissing(LimbType.LEFT_ARM) && !(offhand = player.getItemBySlot(EquipmentSlot.OFFHAND)).isEmpty()) {
                // Losing the left arm immediately drops the offhand item.
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.drop(offhand, false);
            }
            if (data.isLimbMissing(LimbType.RIGHT_ARM) && !(mainhand = player.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
                // Losing the right arm immediately drops the main-hand item.
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                player.drop(mainhand, false);
            }
            if (data.isLimbMissing(LimbType.LEFT_ARM) && data.isLimbMissing(LimbType.RIGHT_ARM)) {
                // With both arms gone, even the selected hotbar slot cannot be retained.
                int selected = player.getInventory().selected;
                ItemStack hotbarItem = player.getInventory().getItem(selected);
                if (!hotbarItem.isEmpty()) {
                    player.getInventory().setItem(selected, ItemStack.EMPTY);
                    player.drop(hotbarItem, false);
                }
            }
        });
        LimbCapabilityProvider.get((LivingEntity)player).ifPresent(data -> {
            int missingLegs = data.countSeveredLegs();
            if (missingLegs == 0) {
                return;
            }
            UUID uuid = player.getUUID();
            double prevY = PREV_DELTA_Y.getOrDefault(uuid, 0.0);
            double currY = player.getDeltaMovement().y;
            // A transition from non-positive Y velocity to positive Y velocity marks the start of a jump.
            if (prevY <= 0.0 && currY > 0.0) {
                if (missingLegs >= 2) {
                    // No legs means jumps are fully suppressed.
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                } else if (missingLegs == 1) {
                    // One missing leg preserves only 40% of normal upward impulse.
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.4, 1.0));
                }
            }
            PREV_DELTA_Y.put(uuid, currY);
        });
    }
}
