package net.mcreator.jujutsucraft.addon.limb;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
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

@Mod.EventBusSubscriber(modid = "jjkblueredpurple")
public class LimbLossHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RNG = new Random();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player)) return;

        float actualDamage = event.getAmount();
        float currentHp = entity.getHealth();
        float maxHp = entity.getMaxHealth();
        if (maxHp <= 0.0f) return;

        LimbCapabilityProvider.get(entity).ifPresent(data -> {
            if (data.getSeverCooldownTicks() > 0) return;

            float hpAfter = currentHp - actualDamage;
            float scaledMinDmg = Math.max(4.0f, maxHp * 0.05f);
            boolean bigHit = actualDamage >= currentHp * 0.3f && actualDamage >= scaledMinDmg;
            boolean wasLowHp = currentHp / maxHp < 0.3f;
            boolean dropsToLowHp = hpAfter > 0.0f && hpAfter / maxHp < 0.3f;
            boolean lethalHit = hpAfter <= 0.0f;

            if (!(bigHit || wasLowHp || dropsToLowHp || lethalHit)) return;

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
            chance = Math.min(chance, 0.85f);

            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) entity;
                boolean hasRCTSkill = hasRCTAdvancement(sp);
                if (!hasRCTSkill) {
                    chance *= 0.2f;
                }
                LOGGER.debug("[Limb] Player {} actual damage {}/{} HP (post-armor), hpAfter={}, bigHit={}, lowHp={}, dropsLow={}, lethal={}, chance={}, hasRCT={}",
                    sp.getName().getString(), actualDamage, maxHp, hpAfter, bigHit, wasLowHp, dropsToLowHp, lethalHit, chance, hasRCTSkill);
            }

            if (RNG.nextFloat() > chance) return;

            LimbType toSever = pickLimbToSever(data, entity);
            if (toSever == null) return;

            LOGGER.info("[Limb] Severing {} from {}", toSever, entity.getName().getString());
            severLimb(entity, data, toSever, event.getSource());
        });
    }

    private static LimbType pickLimbToSever(LimbData data, LivingEntity entity) {
        ArrayList<LimbType> candidates = new ArrayList<>();
        for (LimbType type : LimbType.values()) {
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
        Collections.shuffle(candidates, RNG);
        return candidates.get(0);
    }

    public static void severLimb(LivingEntity entity, LimbData data, LimbType type, DamageSource source) {
        boolean hasRCT = entity.hasEffect(JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());

        if (entity instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) entity;
            String[] words = type.getSerializedName().split("_");
            StringBuilder name = new StringBuilder();
            for (String w : words) {
                if (name.length() > 0) name.append(' ');
                name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            sp.displayClientMessage(Component.literal("\u00a7c\u2620 " + name + "!"), true);
        }

        if (type == LimbType.HEAD) {
            if (!hasRCT) {
                data.setState(type, LimbState.SEVERED);
                LimbSounds.playHeadSeverSound(entity);
                LimbParticles.spawnSeverBloodBurst(entity, type);
                spawnSeveredLimbEntity(entity, type);
                entity.setHealth(0.0f);
                data.setSeverCooldownTicks(40);
                data.setBloodDripTicks(200);
                LimbSyncPacket.sendToTrackingPlayers(entity, data);
                return;
            }
            data.setState(type, LimbState.REVERSING);
            data.setRegenProgress(type, 0.3f);
            LimbSounds.playHeadSeverSound(entity);
        } else {
            data.setState(type, LimbState.SEVERED);
        }

        data.setSeverCooldownTicks(40);
        data.setBloodDripTicks(200);
        LimbSounds.playSeverSound(entity);
        LimbParticles.spawnSeverBloodBurst(entity, type);

        if (source != null && source.getDirectEntity() != null) {
            Vec3 dir = entity.position().subtract(source.getDirectEntity().position()).normalize();
            LimbParticles.spawnBloodSpray(entity, type, dir);
        }

        spawnSeveredLimbEntity(entity, type);
        LimbGameplayHandler.applyLimbDebuffs(entity, data);
        LimbSyncPacket.sendToTrackingPlayers(entity, data);
        if (entity instanceof ServerPlayer sp) {
            LimbSyncPacket.sendToPlayer(sp, entity, data);
        }
    }

    private static void spawnSeveredLimbEntity(LivingEntity owner, LimbType type) {
        Level level = owner.level();
        if (!(level instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) level;

        Vec3 offset = LimbParticles.getLimbOffset(owner, type);
        SeveredLimbEntity limbEntity = new SeveredLimbEntity(serverLevel, owner, type);
        limbEntity.moveTo(owner.getX() + offset.x, owner.getY() + offset.y, owner.getZ() + offset.z);

        double throwX = owner.level().getRandom().nextGaussian() * 0.15;
        double throwY = 0.15 + owner.level().getRandom().nextDouble() * 0.1;
        double throwZ = owner.level().getRandom().nextGaussian() * 0.15;
        limbEntity.setDeltaMovement(throwX, throwY, throwZ);

        serverLevel.addFreshEntity(limbEntity);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        LimbCapabilityProvider.get(entity).ifPresent(data -> {
            if (!data.hasSeveredLimbs() && data.getBloodDripTicks() <= 0) return;

            data.tickCooldown();

            // Blood drip: continuous for SEVERED limbs, reduced for REVERSING
            if (entity.tickCount % 4 == 0) {
                for (LimbType type : LimbType.values()) {
                    LimbState state = data.getState(type);
                    if (state == LimbState.SEVERED) {
                        LimbParticles.spawnBloodDrip(entity, type);
                    } else if (state == LimbState.REVERSING && entity.tickCount % 8 == 0) {
                        // Reduced drip rate during regeneration
                        LimbParticles.spawnBloodDrip(entity, type);
                    }
                }
            }
            tickRegeneration(entity, data);
        });
    }

    private static void tickRegeneration(LivingEntity entity, LimbData data) {
        boolean hasRCT = entity.hasEffect(JujutsucraftModMobEffects.REVERSE_CURSED_TECHNIQUE.get());
        if (!hasRCT) return;

        boolean inZone = entity.hasEffect(JujutsucraftModMobEffects.ZONE.get());
        float regenRate = inZone ? 0.04f : 0.02f;
        if (inZone && entity.tickCount % 10 == 0) {
            entity.heal(1.0f);
        }

        boolean changed = false;
        for (LimbType type : LimbType.values()) {
            LimbState state = data.getState(type);
            // SEVERED -> immediately start reversing (RCT active on this tick)
            if (state == LimbState.SEVERED) {
                data.setState(type, LimbState.REVERSING);
                data.setRegenProgress(type, 0.0f);
                LimbSounds.playRegenStartSound(entity);
                changed = true;
                continue;
            }
            // REVERSING: progress 0->1 with bone/muscle/flesh phase
            if (state == LimbState.REVERSING) {
                float progress = data.getRegenProgress(type) + regenRate;
                data.setRegenProgress(type, progress);
                LimbParticles.spawnRegenParticles(entity, type, progress);
                if (entity.tickCount % 10 == 0) {
                    LimbSounds.playRegenPulseSound(entity);
                }
                if (progress >= 1.0f) {
                    data.setState(type, LimbState.INTACT);
                    data.setRegenProgress(type, 0.0f);
                    LimbSounds.playRegenCompleteSound(entity);
                    LimbParticles.spawnRegenCompleteBurst(entity, type);
                    changed = true;
                } else {
                    changed = true;
                }
            }
        }

        if (changed) {
            LimbGameplayHandler.refreshDebuffs(entity, data);
            LimbSyncPacket.sendToTrackingPlayers(entity, data);
            if (entity instanceof ServerPlayer sp) {
                LimbSyncPacket.sendToPlayer(sp, entity, data);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        Entity entity = event.getTarget();
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) entity;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer sp = (ServerPlayer) player;
        LimbCapabilityProvider.get(target).ifPresent(data -> {
            if (data.hasSeveredLimbs()) {
                LimbSyncPacket.sendToPlayer(sp, target, data);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer sp = (ServerPlayer) player;
        LimbCapabilityProvider.get(sp).ifPresent(data -> {
            if (data.hasSeveredLimbs()) {
                LimbSyncPacket.sendToTrackingPlayers(sp, data);
            }
        });
        ModNetworking.sendNearDeathCdSync(sp);
    }

    private static boolean hasRCTAdvancement(ServerPlayer player) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(
                new ResourceLocation("jujutsucraft:reverse_cursed_technique_1"));
            return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
        } catch (Exception e) {
            return false;
        }
    }
}
