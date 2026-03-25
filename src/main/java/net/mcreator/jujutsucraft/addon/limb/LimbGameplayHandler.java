package net.mcreator.jujutsucraft.addon.limb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.mcreator.jujutsucraft.addon.limb.LimbCapabilityProvider;
import net.mcreator.jujutsucraft.addon.limb.LimbData;
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
 * Applies gameplay debuffs to entities that have lost limbs and prevents
 * them from performing limb-dependent actions (jumping, sprinting, holding items).
 *
 * <h2>Movement penalties</h2>
 * <ul>
 *   <li>1 leg lost: reduced jump height (multiplier 0.4)</li>
 *   <li>2 legs lost: near-total movement lock (multiplier 0.05, sprint disabled)</li>
 * </ul>
 *
 * <h2>Item-drop mechanic</h2>
 * While any arm (left, right, or both) is missing or regenerating,
 * the player cannot hold items. The held item is dropped immediately
 * and every second the player continues holding something.
 */
@Mod.EventBusSubscriber(modid = "jjkblueredpurple")
public class LimbGameplayHandler {

    private static final UUID LEFT_ARM_MINING_UUID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000001");
    private static final UUID RIGHT_ARM_ATTACK_UUID = UUID.fromString("a1b2c3d4-2222-4000-8000-000000000002");
    private static final UUID LEG_SPEED_UUID = UUID.fromString("a1b2c3d4-3333-4000-8000-000000000003");

    // Tracks the previous tick's Y velocity for each player — used to detect jump starts
    // without relying on a ground-state method (not available in this Forge version).
    private static final Map<UUID, Double> PREV_DELTA_Y = new ConcurrentHashMap<>();

    // EquipmentSlot entries for each hand
    private static final EquipmentSlot[] HAND_SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /**
     * Applies movement speed penalty based on how many legs are missing.
     * 1 leg: moderate slow, 2 legs: near-total immobilisation.
     */
    public static void applyLimbDebuffs(LivingEntity entity, LimbData data) {
        removeAllModifiers(entity);

        int missingArms = data.countSeveredArms();
        int missingLegs = data.countSeveredLegs();

        // ── Arm: attack damage penalty ──────────────────────────────────
        if (missingArms > 0) {
            float damagePenalty = missingArms >= 2 ? 0.5f : 0.25f;
            AttributeInstance atkDmg = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atkDmg != null) {
                atkDmg.removeModifier(RIGHT_ARM_ATTACK_UUID);
                atkDmg.addTransientModifier(new AttributeModifier(
                    RIGHT_ARM_ATTACK_UUID, "jjkbrp_limb_arm_strike_damage",
                    (double)(-damagePenalty), AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (entity instanceof ServerPlayer sp) {
                sp.getPersistentData().putFloat("jjkbrp_strike_damage_penalty", damagePenalty);
            }
        }

        // ── Leg: movement speed penalty ─────────────────────────────────
        if (missingLegs > 0) {
            double speedPenalty = missingLegs >= 2 ? -0.95 : -0.6;
            AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(LEG_SPEED_UUID);
                attr.addTransientModifier(new AttributeModifier(
                    LEG_SPEED_UUID, "jjkbrp_limb_leg_slow",
                    speedPenalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    public static void removeAllModifiers(LivingEntity entity) {
        AttributeInstance moveSpd = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance atkDmg = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkDmg != null) atkDmg.removeModifier(RIGHT_ARM_ATTACK_UUID);
        if (moveSpd != null) moveSpd.removeModifier(LEG_SPEED_UUID);
    }

    public static void refreshDebuffs(LivingEntity entity, LimbData data) {
        if (data.hasSeveredLimbs()) {
            applyLimbDebuffs(entity, data);
        } else {
            removeAllModifiers(entity);
        }
    }

    /**
     * Dampens or cancels the player's jump based on how many legs are missing.
     *
     * <ul>
     *   <li>2 legs lost: jump is fully negated (Y velocity zeroed).</li>
     *   <li>1 leg lost: jump height reduced to 40%.</li>
     * </ul>
     *
     * Since {@link LivingJumpEvent} is not available in this Forge version,
     * we detect jumps by watching for the transition from grounded to airborne
     * with positive Y velocity on the server side.
     */
    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide) return;

        LimbCapabilityProvider.get(player).ifPresent(data -> {
            int missingArms = data.countSeveredArms();
            int missingLegs = data.countSeveredLegs();

            if (missingLegs >= 2) {
                player.setSprinting(false);
            }

            if (missingArms <= 0) return;

            // ── Drop items currently held in either hand ──────────────
            for (EquipmentSlot slot : HAND_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                    player.drop(stack, false);
                }
            }

            // ── Safety net: clear any item the player somehow still ────
            //    holds in the active slot on the hotbar ──────────────
            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = player.getInventory().getItem(i);
                if (!hotbarItem.isEmpty()) {
                    // Only drop the selected hotbar slot
                    if (i == player.getInventory().selected) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                        player.drop(hotbarItem, false);
                    }
                }
            }
        });

        // ── Jump dampening: detect jump starts by velocity change ───────────
        LimbCapabilityProvider.get(player).ifPresent(data -> {
            int missingLegs = data.countSeveredLegs();
            if (missingLegs == 0) return;

            UUID uuid = player.getUUID();
            double prevY = PREV_DELTA_Y.getOrDefault(uuid, 0.0);
            double currY = player.getDeltaMovement().y;
            // A jump starts when Y velocity transitions from ≤ 0 to > 0
            if (prevY <= 0 && currY > 0) {
                if (missingLegs >= 2) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                } else if (missingLegs == 1) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.4, 1.0));
                }
            }
            PREV_DELTA_Y.put(uuid, currY);
        });
    }
}
