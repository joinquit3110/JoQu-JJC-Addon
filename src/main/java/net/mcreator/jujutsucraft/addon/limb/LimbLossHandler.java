package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.LimbCapabilityProvider;
import net.mcreator.jujutsucraft.addon.limb.LimbData;
import net.mcreator.jujutsucraft.addon.limb.LimbGameplayHandler;
import net.mcreator.jujutsucraft.addon.limb.LimbParticles;
import net.mcreator.jujutsucraft.addon.limb.LimbSounds;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbSyncPacket;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.mcreator.jujutsucraft.addon.limb.SeveredLimbEntity;
import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.addon.yuta.YutaFakePlayerEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Central server-side handler for limb severing, regeneration, and sync events.
 *
 * <p>This class decides when incoming damage is severe enough to attempt a sever, chooses which limb
 * to remove, applies the resulting gameplay effects, drives reverse cursed technique regeneration,
 * and ensures clients tracking the entity receive updated limb state.</p>
 */
@Mod.EventBusSubscriber(modid="jjkblueredpurple")
public class LimbLossHandler {
    /** Logger used for debug and info traces around limb events. */
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Shared random source for sever rolls, limb selection, and throw variance. */
    private static final Random RNG = new Random();

    // ===== DAMAGE-TRIGGERED LIMB LOSS =====

    /**
     * Evaluates player damage events to decide whether a limb sever should occur.
     *
     * @param event incoming damage event after armor and mitigation have been applied
     */
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity instanceof YutaFakePlayerEntity fake) {
            event.setAmount(Math.min(event.getAmount(), 40.0f));
            if (fake.getHealth() - event.getAmount() < 40.0f) {
                fake.setHealth(Math.min(fake.getMaxHealth(), 200.0f));
            }
            LimbLossHandler.handleFakePlayerHit(fake, event.getSource());
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        float actualDamage = event.getAmount();
        float currentHp = entity.getHealth();
        float maxHp = entity.getMaxHealth();
        if (maxHp <= 0.0f) {
            return;
        }
        LimbCapabilityProvider.get(entity).ifPresent(data -> {
            boolean lethalHit;
            if (data.getSeverCooldownTicks() > 0) {
                // A short cooldown prevents one burst of damage from severing multiple limbs at once.
                return;
            }
            float hpAfter = currentHp - actualDamage;
            // The hit must be meaningfully large relative to the victim, but never lower than 4 damage.
            float scaledMinDmg = Math.max(4.0f, maxHp * 0.05f);
            boolean bigHit = actualDamage >= currentHp * 0.3f && actualDamage >= scaledMinDmg;
            boolean wasLowHp = currentHp / maxHp < 0.3f;
            boolean dropsToLowHp = hpAfter > 0.0f && hpAfter / maxHp < 0.3f;
            boolean bl = lethalHit = hpAfter <= 0.0f;
            // Only critical or near-fatal combat moments can trigger a sever roll.
            if (!(bigHit || wasLowHp || dropsToLowHp || lethalHit)) {
                return;
            }
            float chance = 0.0f;
            if (bigHit) {
                chance += 0.25f + actualDamage / maxHp * 0.3f;
            }
            if (dropsToLowHp) {
                chance += 0.2f + (1.0f - hpAfter / maxHp) * 0.25f;
            }
            if (wasLowHp) {
                chance += 0.15f;
            }
            if (lethalHit) {
                chance += 0.15f;
            }
            // The sever chance is hard-capped at 85% even for extreme hits.
            chance = Math.min(chance, 0.85f);
            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)entity;
                boolean hasRCTSkill = LimbLossHandler.hasRCTAdvancement(sp);
                if (!hasRCTSkill) {
                    // Players without the RCT advancement only use 20% of the calculated chance.
                    chance *= 0.2f;
                }
                LOGGER.debug("[Limb] Player {} actual damage {}/{} HP (post-armor), hpAfter={}, bigHit={}, lowHp={}, dropsLow={}, lethal={}, chance={}, hasRCT={}", new Object[]{sp.getName().getString(), Float.valueOf(actualDamage), Float.valueOf(maxHp), Float.valueOf(hpAfter), bigHit, wasLowHp, dropsToLowHp, lethalHit, Float.valueOf(chance), hasRCTSkill});
            }
            if (RNG.nextFloat() > chance) {
                return;
            }
            LimbType toSever = LimbLossHandler.pickLimbToSever(data, entity);
            if (toSever == null) {
                return;
            }
            LOGGER.info("[Limb] Severing {} from {}", (Object)toSever, (Object)entity.getName().getString());
            LimbLossHandler.severLimb(entity, data, toSever, event.getSource());
        });
    }

    private static void handleFakePlayerHit(YutaFakePlayerEntity fake, DamageSource source) {
        double technique = fake.getPersistentData().getDouble("PlayerCurseTechnique");
        boolean rctMode = fake.getPersistentData().getBoolean(YutaFakePlayerEntity.KEY_RCT);
        int nextHit = fake.getPersistentData().getInt(YutaFakePlayerEntity.KEY_HIT_COUNT) + 1;
        fake.getPersistentData().putInt(YutaFakePlayerEntity.KEY_HIT_COUNT, nextHit);
        if (rctMode && nextHit % 2 == 0) {
            YutaCopyStore.onLimbRegrown(fake, LimbType.RIGHT_ARM.getSerializedName());
            LOGGER.info("[Yuta Fake] RCT regrew test limb for {}", (Object)fake.getName().getString());
            return;
        }
        YutaCopyStore.spawnLimbCopyItem(fake, LimbType.RIGHT_ARM.getSerializedName(), technique, 0.0D, true, false);
        fake.getPersistentData().putBoolean("jjkaddon_fake_source", true);
        LOGGER.info("[Yuta Fake] Dropped test limb CT {} for {}", (Object)((int)Math.round(technique)), (Object)fake.getName().getString());
    }

    // ===== LIMB SELECTION =====

    /**
     * Chooses which limb should be severed next.
     *
     * <p>The selection prioritizes non-head limbs first, favoring arms over legs. The head is only
     * considered as a last resort once every other limb is already unavailable.</p>
     *
     * @param data current limb capability data
     * @param entity victim being evaluated
     * @return selected limb to sever, or {@code null} when nothing valid remains
     */
    private static LimbType pickLimbToSever(LimbData data, LivingEntity entity) {
        ArrayList<LimbType> candidates = new ArrayList<LimbType>();
        for (LimbType type : LimbType.values()) {
            // Head severing is intentionally delayed until no intact arms or legs remain.
            if (type == LimbType.HEAD || data.getState(type) != LimbState.INTACT) continue;
            candidates.add(type);
        }
        if (candidates.isEmpty()) {
            if (entity instanceof Player && data.getState(LimbType.HEAD) == LimbState.INTACT) {
                return LimbType.HEAD;
            }
            return null;
        }
        List<LimbType> arms = candidates.stream().filter(LimbType::isArm).toList();
        List<LimbType> legs = candidates.stream().filter(LimbType::isLeg).toList();
        float roll = RNG.nextFloat();
        if (!arms.isEmpty() && roll < 0.5f) {
            return arms.get(RNG.nextInt(arms.size()));
        }
        if (!legs.isEmpty() && roll < 0.85f) {
            return legs.get(RNG.nextInt(legs.size()));
        }
        // Remaining cases fall back to a shuffled intact-limb list for variety.
        Collections.shuffle(candidates, RNG);
        return (LimbType)((Object)candidates.get(0));
    }

    // ===== SEVER EXECUTION =====

    /**
     * Applies the effects of severing one limb.
     *
     * @param entity entity losing the limb
     * @param data mutable limb capability data for that entity
     * @param type limb being severed
     * @param source damage source that caused the sever, used for directional blood spray when available
     */
    public static void severLimb(LivingEntity entity, LimbData data, LimbType type, DamageSource source) {
        ServerPlayer sp;
        boolean hasRCT = entity.hasEffect((MobEffect)JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());
        if (entity instanceof ServerPlayer) {
            sp = (ServerPlayer)entity;
            String[] words = type.getSerializedName().split("_");
            StringBuilder name = new StringBuilder();
            for (String w : words) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            // Display a compact on-screen warning naming the lost limb.
            sp.displayClientMessage((Component)Component.literal((String)("\u00a7c\u2620 " + String.valueOf(name) + "!")), true);
        }
        if (type == LimbType.HEAD) {
            if (!hasRCT) {
                // Head sever without active RCT is immediately fatal.
                data.setState(type, LimbState.SEVERED);
                LimbSounds.playHeadSeverSound(entity);
                LimbParticles.spawnSeverBloodBurst(entity, type);
                LimbLossHandler.spawnSeveredLimbEntity(entity, type);
                entity.setHealth(0.0f);
                data.setSeverCooldownTicks(40);
                data.setBloodDripTicks(200);
                LimbSyncPacket.sendToTrackingPlayers(entity, data);
                return;
            }
            // With RCT active, the head enters the reversing phase and starts partway restored.
            data.setState(type, LimbState.REVERSING);
            data.setRegenProgress(type, 0.3f);
            LimbSounds.playHeadSeverSound(entity);
        } else {
            data.setState(type, LimbState.SEVERED);
        }
        // Standard sever aftermath uses a 40-tick sever cooldown and 200 ticks of blood dripping.
        data.setSeverCooldownTicks(40);
        data.setBloodDripTicks(200);
        LimbSounds.playSeverSound(entity);
        LimbParticles.spawnSeverBloodBurst(entity, type);
        if (source != null && source.getDirectEntity() != null) {
            // Spray blood away from the attacker or projectile impact point for directional feedback.
            Vec3 dir = entity.position().subtract(source.getDirectEntity().position()).normalize();
            LimbParticles.spawnBloodSpray(entity, type, dir);
        }
        SeveredLimbEntity detached = LimbLossHandler.spawnSeveredLimbEntity(entity, type);
        if (entity instanceof ServerPlayer) {
            YutaCopyStore.attachPlayerLimbCopyData(entity, type.getSerializedName(), detached);
        } else {
            YutaCopyStore.spawnLimbCopyItem(entity, type.getSerializedName());
        }
        LimbGameplayHandler.applyLimbDebuffs(entity, data);
        LimbSyncPacket.sendToTrackingPlayers(entity, data);
        if (entity instanceof ServerPlayer) {
            sp = (ServerPlayer)entity;
            // Also send to the victim directly so self-view render state updates immediately.
            LimbSyncPacket.sendToPlayer(sp, entity, data);
        }
    }

    /**
     * Spawns the detached severed limb entity with a small outward toss.
     *
     * @param owner entity that lost the limb
     * @param type limb that should be represented by the detached entity
     */
    private static SeveredLimbEntity spawnSeveredLimbEntity(LivingEntity owner, LimbType type) {
        Level level = owner.level();
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 offset = LimbParticles.getLimbOffset(owner, type);
        SeveredLimbEntity limbEntity = new SeveredLimbEntity((Level)serverLevel, owner, type);
        limbEntity.moveTo(owner.getX() + offset.x, owner.getY() + offset.y, owner.getZ() + offset.z);
        // Randomized throw values make detached limbs feel like physical debris rather than static drops.
        double throwX = owner.level().getRandom().nextGaussian() * 0.15;
        double throwY = 0.15 + owner.level().getRandom().nextDouble() * 0.1;
        double throwZ = owner.level().getRandom().nextGaussian() * 0.15;
        limbEntity.setDeltaMovement(throwX, throwY, throwZ);
        serverLevel.addFreshEntity((Entity)limbEntity);
        return limbEntity;
    }

    // ===== TICK PROCESSING =====

    /**
     * Ticks cooldowns, blood drips, and regeneration for living entities with limb data.
     *
     * @param event living tick event
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        LimbCapabilityProvider.get(entity).ifPresent(data -> {
            if (!data.hasSeveredLimbs() && data.getBloodDripTicks() <= 0) {
                return;
            }
            data.tickCooldown();
            if (entity.tickCount % 4 == 0) {
                for (LimbType type : LimbType.values()) {
                    LimbState state = data.getState(type);
                    if (state == LimbState.SEVERED) {
                        LimbParticles.spawnBloodDrip(entity, type);
                        continue;
                    }
                    // Reversing limbs drip less often than fully severed limbs.
                    if (state != LimbState.REVERSING || entity.tickCount % 8 != 0) continue;
                    LimbParticles.spawnBloodDrip(entity, type);
                }
            }
            LimbLossHandler.tickRegeneration(entity, data);
        });
    }

    /**
     * Advances reverse cursed technique limb restoration.
     *
     * <p>Normal regeneration progresses by {@code 0.02f} per tick. While the entity is in the Zone,
     * the rate doubles to {@code 0.04f} and an additional point of health is restored every 10 ticks.</p>
     *
     * @param entity entity being regenerated
     * @param data mutable limb capability data
     */
    private static void tickRegeneration(LivingEntity entity, LimbData data) {
        float maxHp;
        float regenRate;
        if (entity == null) {
            return;
        }
        if (!entity.isAlive() || entity.isDeadOrDying() || entity.isRemoved() || entity.getHealth() <= 0.0f) {
            return;
        }
        boolean hasRCT = entity.hasEffect((MobEffect)JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());
        if (!hasRCT) {
            return;
        }
        boolean inZone = entity.hasEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
        float f = regenRate = inZone ? 0.04f : 0.02f;
        if (inZone && entity.tickCount % 10 == 0 && (maxHp = entity.getMaxHealth()) > 0.0f) {
            // Zone grants both faster limb restoration and a small periodic health refill.
            entity.setHealth(Math.min(maxHp, entity.getHealth() + 1.0f));
        }
        boolean changed = false;
        for (LimbType type : LimbType.values()) {
            LimbState state = data.getState(type);
            if (state == LimbState.SEVERED) {
                // Active RCT automatically starts the reversing phase for newly severed limbs.
                data.setState(type, LimbState.REVERSING);
                data.setRegenProgress(type, 0.0f);
                LimbSounds.playRegenStartSound(entity);
                changed = true;
                continue;
            }
            if (state != LimbState.REVERSING) continue;
            float progress = data.getRegenProgress(type) + regenRate;
            data.setRegenProgress(type, progress);
            LimbParticles.spawnRegenParticles(entity, type, progress);
            if (entity.tickCount % 10 == 0) {
                LimbSounds.playRegenPulseSound(entity);
            }
            if (progress >= 1.0f) {
                data.setState(type, LimbState.INTACT);
                data.setRegenProgress(type, 0.0f);
                YutaCopyStore.onLimbRegrown(entity, type.getSerializedName());
                LimbSounds.playRegenCompleteSound(entity);
                LimbParticles.spawnRegenCompleteBurst(entity, type);
                changed = true;
                continue;
            }
            changed = true;
        }
        if (changed) {
            LimbGameplayHandler.refreshDebuffs(entity, data);
            LimbSyncPacket.sendToTrackingPlayers(entity, data);
            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)entity;
                LimbSyncPacket.sendToPlayer(sp, entity, data);
            }
        }
    }

    // ===== TRACKING AND LOGIN SYNC =====

    /**
     * Sends limb data to a server player when they begin tracking a target.
     *
     * @param event Forge tracking event
     */
    @SubscribeEvent
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        Entity entity = event.getTarget();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity)entity;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer sp = (ServerPlayer)player;
        LimbCapabilityProvider.get(target).ifPresent(data -> {
            LimbSyncPacket.sendToPlayer(sp, target, data);
        });
    }

    /**
     * Resends the player's limb state and near-death cooldown data on login.
     *
     * @param event Forge login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer sp = (ServerPlayer)player;
        LimbCapabilityProvider.get((LivingEntity)sp).ifPresent(data -> {
            LimbSyncPacket.sendToPlayer(sp, (LivingEntity)sp, data);
            if (data.hasSeveredLimbs()) {
                LimbSyncPacket.sendToTrackingPlayers((LivingEntity)sp, data);
            }
        });
        ModNetworking.sendNearDeathCdSync(sp);
    }

    /**
     * Resends the player's limb state after changing dimension so the client cache stays current.
     *
     * @param event Forge dimension change event
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer sp = (ServerPlayer)player;
        LimbCapabilityProvider.get((LivingEntity)sp).ifPresent(data -> {
            LimbSyncPacket.sendToPlayer(sp, (LivingEntity)sp, data);
            if (data.hasSeveredLimbs()) {
                LimbSyncPacket.sendToTrackingPlayers((LivingEntity)sp, data);
            }
        });
    }

    /**
     * Checks whether the player has unlocked the base RCT advancement used by sever chance scaling.
     *
     * @param player player being checked
     * @return {@code true} when the advancement is complete
     */
    private static boolean hasRCTAdvancement(ServerPlayer player) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation("jujutsucraft:reverse_cursed_technique_1"));
            return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
        }
        catch (Exception e) {
            return false;
        }
    }
}
