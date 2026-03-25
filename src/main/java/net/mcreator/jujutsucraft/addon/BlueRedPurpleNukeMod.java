package net.mcreator.jujutsucraft.addon;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent;
import org.joml.Vector3f;

import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.entity.PurpleEntity;
import net.mcreator.jujutsucraft.procedures.SetRangedAmmoProcedure;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.mcreator.jujutsucraft.procedures.KnockbackProcedure;
import net.mcreator.jujutsucraft.procedures.BlockDestroyAllDirectionProcedure;
import net.mcreator.jujutsucraft.init.JujutsucraftModAttributes;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.init.JujutsucraftModParticleTypes;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Main addon mod class for Jujutsu Craft - Blue/Red/Purple Nuke System.
 * Handles Gojo-inspired techniques: Blue orbs, Red orbs, Purple Nuke, Infinity Crusher,
 * Black Flash charge tracking, and advanced entity behaviors.
 */
@Mod("jjkblueredpurple")
public class BlueRedPurpleNukeMod {

    // ═══════════════════════════════════════════════════════════════════
    //  CONFIGURATION CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Ticks a Blue orb lingers waiting for a Red (10 seconds). */
    private static final int LINGER_DURATION = 200;

    /** Radius for Red-Blue collision detection to trigger Purple Nuke. */
    private static final double NUKE_COLLISION_RADIUS = 4.0;

    /** Max range for teleport-behind target detection. */
    private static final double TELEPORT_RANGE = 32.0;

    /** Cone angle for finding teleport targets (degrees). */
    private static final double AIM_CONE_ANGLE = 30.0;

    /** Distance behind target where player teleports. */
    private static final double BEHIND_DISTANCE = 2.5;

    /** Minimum CE required to activate Purple Nuke. */
    private static final double NUKE_CE_THRESHOLD = 2000.0;

    /** CE cost deducted when Purple Nuke activates. */
    private static final double NUKE_CE_COST = 2000.0;

    /** Player HP fraction threshold for Purple Nuke (must be below 30% HP). */
    private static final double NUKE_HP_THRESHOLD = 0.3;

    /** Radius within which Red captures mobs. */
    private static final double RED_CAPTURE_RADIUS = 5.0;

    /** Max range a fully-charged normal Red orb travels. */
    private static final double RED_MAX_RANGE_FULL = 128.0;

    /** Ticks Red waits in pre-init before launching. */
    private static final int RED_PREINIT_TICKS = 5;

    /** Particle color for Red trail effects (RGB). */
    private static final Vector3f RED_TRAIL_COLOR = new Vector3f(0.95f, 0.15f, 0.1f);

    /** Particle color for Infinity Crusher aura (RGB). */
    private static final Vector3f BLUE_AURA_COLOR = new Vector3f(0.1f, 0.3f, 0.95f);

    /** How far from player's eye the Red charge anchor sits. */
    private static final double RED_CHARGE_ANCHOR_DISTANCE = 2.2;

    /** Y offset for Red charge anchor from eye level. */
    private static final double RED_CHARGE_ANCHOR_Y_OFFSET = -0.1;

    /** Min distance Blue aim orb stays from player. */
    private static final double BLUE_CROUCH_MIN_DISTANCE = 10.0;

    /** Max distance Blue aim orb stays from player. */
    private static final double BLUE_CROUCH_MAX_DISTANCE = 20.0;

    /** Max blocks per tick the Blue aim orb moves toward target. */
    private static final double BLUE_AIM_ORB_SPEED = 1.5;

    /** Total ticks (4.5s) of crouch-aim control before linger. */
    private static final int BLUE_AIM_DURATION = 90;

    // ─── Infinity Crusher ───────────────────────────────────────────
    private static final double CRUSHER_MIN_RADIUS = 1.0;
    private static final double CRUSHER_MAX_RADIUS = 3.0;
    private static final float  CRUSHER_BASE_WALL_DAMAGE = 3.0f;
    private static final double CRUSHER_CE_DAMAGE_SCALE = 0.012;
    private static final double CRUSHER_PUSH_STRENGTH = 0.5;
    private static final double CRUSHER_CONE_COS = 0.5; // ~60° cone
    private static final double CRUSHER_BASE_CE_DRAIN = 0.5;
    private static final double CRUSHER_CE_DRAIN_GROWTH = 0.02;
    private static final int    CRUSHER_HARDLOCK_THRESHOLD = 15;

    // ═══════════════════════════════════════════════════════════════════
    //  MOD LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    public BlueRedPurpleNukeMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry.ENTITIES.register(modBus);

        MinecraftForge.EVENT_BUS.register(this);
        ModNetworking.register();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EVENT ENTRY POINTS
    // ═══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof BlueEntity) {
            handleBlueTick(entity);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        handleInfinityCrusher(player);
        handleBlackFlashCharge(player);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BLACK FLASH CHARGE TRACKING
    // ═══════════════════════════════════════════════════════════════════

    private static void handleBlackFlashCharge(ServerPlayer player) {
        var data = player.getPersistentData();
        boolean hasMastery = data.getBoolean("addon_bf_mastery");
        if (!hasMastery) {
            if (CooldownTrackerEvents.hasAdvancement(player, "jjkblueredpurple:black_flash_mastery")) {
                data.putBoolean("addon_bf_mastery", true);
                hasMastery = true;
            }
        }
        if (!hasMastery) return;

        JujutsucraftModVariables.PlayerVariables vars = player
            .getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
            .orElse(new JujutsucraftModVariables.PlayerVariables());
        int selectId = (int) Math.round(vars.PlayerSelectCurseTechnique);

        if (selectId > 2) {
            if (data.getBoolean("addon_bf_charging")) {
                data.putBoolean("addon_bf_charging", false);
                data.putBoolean("addon_bf_guaranteed", false);
            }
            return;
        }

        double cnt6 = data.getDouble("cnt6");
        double skillTag = data.getDouble("skill");

        if (cnt6 >= 4.5 && skillTag != 0) {
            if (!data.getBoolean("addon_bf_guaranteed")) {
                data.putBoolean("addon_bf_guaranteed", true);
                data.putBoolean("addon_bf_charging", true);
            }
        } else if (cnt6 > 0 && skillTag != 0) {
            data.putBoolean("addon_bf_charging", true);
            data.putBoolean("addon_bf_charge_announced", false);
        } else if (cnt6 <= 0 && skillTag == 0) {
            data.putBoolean("addon_bf_charging", false);
            data.putBoolean("addon_bf_charge_announced", false);
        }

        if (cnt6 >= 5.0 && skillTag != 0 && !data.getBoolean("addon_bf_charge_announced")) {
            data.putBoolean("addon_bf_charge_announced", true);
            player.displayClientMessage(
                Component.literal("\u00a7l\"Black Flash\""), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED — Mixin entry points
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called from RedEntityMixin to determine whether this addon should
     * completely override the base Red behavior.
     */
    public static boolean shouldOverrideBaseRed(LivingEntity redEntity) {
        if (!(redEntity instanceof RedEntity re)) return false;
        if (redEntity.level().isClientSide()) return false;
        try {
            if (re.getEntityData().get(RedEntity.DATA_flag_purple)) return false;
        } catch (Exception ignored) {}
        if (redEntity.getPersistentData().getBoolean("flag_purple")) return false;
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) return false;
        if (!(redEntity.level() instanceof ServerLevel serverLevel)) return false;
        LivingEntity owner = resolveOwner(serverLevel, ownerUUID);
        return owner instanceof Player;
    }

    /**
     * Called from Mixin instead of AIRedProcedure.execute() when override is active.
     */
    public static void handleRedFromMixin(LivingEntity redEntity) {
        handleRedTick(redEntity);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED — Main tick dispatcher
    // ═══════════════════════════════════════════════════════════════════

    private static void handleRedTick(LivingEntity redEntity) {
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) return;

        Level world = redEntity.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        LivingEntity owner = resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player)) return;

        // Track the best available charge value
        double liveCnt6 = redEntity.getPersistentData().getDouble("cnt6");
        double ownerCnt6 = owner.getPersistentData().getDouble("cnt6");
        double cachedCnt6 = Math.max(Math.max(liveCnt6, ownerCnt6),
            redEntity.getPersistentData().getDouble("addon_red_charge_cached"));
        redEntity.getPersistentData().putDouble("addon_red_charge_cached", cachedCnt6);

        boolean addonInitDone = redEntity.getPersistentData().getBoolean("addon_red_init_done");

        // ═══ PRE-INIT: Keep orb visible at anchor, wait for charge to finish ═══
        if (!addonInitDone) {
            redEntity.getPersistentData().putDouble("cnt1", 0.0);
            redEntity.setHealth(redEntity.getMaxHealth());

            int preInitTicks = redEntity.getPersistentData().getInt("addon_red_preinit_ticks") + 1;
            redEntity.getPersistentData().putInt("addon_red_preinit_ticks", preInitTicks);

            boolean flagStart = redEntity.getPersistentData().getBoolean("flag_start");
            if (!flagStart) {
                double ownerSkill = owner.getPersistentData().getDouble("skill");
                if (ownerSkill == 0.0) {
                    flagStart = true;
                    redEntity.getPersistentData().putBoolean("flag_start", true);
                }
            }

            updateChargingRedOrbVisual(redEntity, owner, cachedCnt6);

            boolean shouldLaunch = (flagStart && preInitTicks >= RED_PREINIT_TICKS) || preInitTicks >= 200;
            if (!shouldLaunch) return;

            redEntity.getPersistentData().putBoolean("addon_red_init_done", true);
            double effectiveCnt6 = Math.max(cachedCnt6, ownerCnt6);
            redEntity.getPersistentData().putDouble("addon_red_charge_used", effectiveCnt6);

            boolean isCrouching = owner.isCrouching();

            if (isCrouching && effectiveCnt6 < 5.0) {
                // Crouch + not full charge → use original base mod Red behavior
                redEntity.getPersistentData().putBoolean("addon_red_use_og", true);
            } else if (isCrouching && effectiveCnt6 >= 5.0) {
                // Crouch + full charge → teleport behind target then explode
                redEntity.getPersistentData().putBoolean("addon_red_shift_cast", true);
                handleRedTeleportBehind(redEntity);
            } else {
                // Normal launch → enhanced flying orb
                initializeNormalRedOrb(redEntity, owner, effectiveCnt6);
            }
        }

        boolean useOg = redEntity.getPersistentData().getBoolean("addon_red_use_og");
        boolean shiftCast = redEntity.getPersistentData().getBoolean("addon_red_shift_cast");
        boolean normalActive = redEntity.getPersistentData().getBoolean("addon_red_normal_active");

        if (useOg) {
            net.mcreator.jujutsucraft.procedures.AIRedProcedure.execute(serverLevel, redEntity);
            if (getEffectiveRedCnt6(redEntity) >= 5.0) {
                checkAndActivatePurpleNuke(redEntity, owner, serverLevel);
            }
            return;
        }

        if (shiftCast) {
            if (getEffectiveRedCnt6(redEntity) >= 5.0) {
                if (checkAndActivatePurpleNuke(redEntity, owner, serverLevel)) return;
            }
            if (!redEntity.getPersistentData().getBoolean("addon_red_shift_exploded")) {
                redEntity.getPersistentData().putBoolean("addon_red_shift_exploded", true);
                handleShiftCastExplosion(redEntity, owner, serverLevel);
            }
            return;
        }

        if (normalActive) {
            if (getEffectiveRedCnt6(redEntity) >= 5.0) {
                if (checkAndActivatePurpleNuke(redEntity, owner, serverLevel)) return;
            }
            tickNormalRedOrb(redEntity, owner);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED NORMAL — Initialize flying orb
    // ═══════════════════════════════════════════════════════════════════

    private static void initializeNormalRedOrb(LivingEntity redEntity, LivingEntity owner, double effectiveCnt6) {
        Vec3 direction = owner.getLookAngle().normalize();
        if (direction.y < -0.12) {
            direction = new Vec3(direction.x, -0.12, direction.z).normalize();
        }

        Vec3 startPos;
        if (redEntity.getPersistentData().getBoolean("addon_red_charge_anchor_valid")) {
            startPos = new Vec3(
                redEntity.getPersistentData().getDouble("addon_red_charge_anchor_x"),
                redEntity.getPersistentData().getDouble("addon_red_charge_anchor_y"),
                redEntity.getPersistentData().getDouble("addon_red_charge_anchor_z"));
        } else {
            startPos = owner.getEyePosition(1.0f)
                .add(direction.scale(RED_CHARGE_ANCHOR_DISTANCE))
                .add(0.0, RED_CHARGE_ANCHOR_Y_OFFSET, 0.0);
        }

        double cnt6 = Math.max(effectiveCnt6, 0.0);
        double chargeRatio = Math.max(0.0, Math.min(cnt6, 5.0) / 5.0);
        double maxRange = RED_MAX_RANGE_FULL * (0.22 + 0.78 * chargeRatio);
        int chargeTier = getRedChargeTier(cnt6);
        double speed = getNormalRedSpeed(cnt6);

        redEntity.getPersistentData().putBoolean("addon_red_normal_active", true);
        redEntity.getPersistentData().putBoolean("flag_start", true);
        redEntity.getPersistentData().putDouble("cnt6", cnt6);
        redEntity.getPersistentData().putDouble("addon_red_dir_x", direction.x);
        redEntity.getPersistentData().putDouble("addon_red_dir_y", direction.y);
        redEntity.getPersistentData().putDouble("addon_red_dir_z", direction.z);
        redEntity.getPersistentData().putDouble("addon_red_travel", 0.0);
        redEntity.getPersistentData().putDouble("addon_red_ticks", 0.0);
        redEntity.getPersistentData().putDouble("addon_red_max_range", maxRange);
        redEntity.getPersistentData().putDouble("addon_red_speed", speed);
        redEntity.getPersistentData().putDouble("addon_red_charge_tier", chargeTier);
        redEntity.getPersistentData().putDouble("x_power", direction.x * speed);
        redEntity.getPersistentData().putDouble("y_power", direction.y * speed);
        redEntity.getPersistentData().putDouble("z_power", direction.z * speed);
        redEntity.getPersistentData().putDouble("x_pos", startPos.x);
        redEntity.getPersistentData().putDouble("y_pos", startPos.y);
        redEntity.getPersistentData().putDouble("z_pos", startPos.z);

        redEntity.teleportTo(startPos.x, startPos.y, startPos.z);
        redEntity.setDeltaMovement(Vec3.ZERO);
        redEntity.setNoGravity(true);
        redEntity.setInvisible(false);
        redEntity.getPersistentData().putBoolean("addon_red_charge_anchor_valid", false);

        if (redEntity.getAttributes().hasAttribute(JujutsucraftModAttributes.SIZE.get())) {
            redEntity.getAttribute(JujutsucraftModAttributes.SIZE.get())
                .setBaseValue(2.0 + chargeTier * 1.25);
        }

        if (redEntity.level() instanceof ServerLevel sl) {
            sl.playSound(null, BlockPos.containing(startPos),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 2.0f, 0.5f);
            SoundEvent electric = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("jujutsucraft:electric_shock"));
            if (electric != null) {
                sl.playSound(null, BlockPos.containing(startPos), electric,
                    SoundSource.NEUTRAL, 1.5f, 1.0f);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED NORMAL — Flying orb tick
    // ═══════════════════════════════════════════════════════════════════

    private static boolean tickNormalRedOrb(LivingEntity redEntity, LivingEntity owner) {
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel serverLevel)) return false;

        Vec3 current = getTrackedRedPosition(redEntity);
        Vec3 direction = new Vec3(
            redEntity.getPersistentData().getDouble("addon_red_dir_x"),
            redEntity.getPersistentData().getDouble("addon_red_dir_y"),
            redEntity.getPersistentData().getDouble("addon_red_dir_z")).normalize();
        if (direction.lengthSqr() < 1.0E-6) direction = redEntity.getLookAngle().normalize();

        double tickCount = redEntity.getPersistentData().getDouble("addon_red_ticks") + 1.0;
        redEntity.getPersistentData().putDouble("addon_red_ticks", tickCount);

        redEntity.getPersistentData().putDouble("cnt1", 0.0);
        redEntity.setHealth(redEntity.getMaxHealth());

        double speed = redEntity.getPersistentData().getDouble("addon_red_speed");
        if (speed <= 0.0) speed = getNormalRedSpeed(getEffectiveRedCnt6(redEntity));

        Vec3 next = current.add(direction.scale(speed));

        // Block collision check after first 2 ticks
        if (tickCount > 2.0) {
            HitResult blockHit = world.clip(new ClipContext(
                current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, redEntity));
            if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
                Vec3 hitPos = blockHit.getLocation();
                updateOrbPosition(redEntity, hitPos);
                explodeNormalRedOrb(redEntity, owner);
                return true;
            }
        }

        updateOrbPosition(redEntity, next);
        redEntity.setDeltaMovement(direction.scale(Math.max(0.1, speed * 0.12)));
        redEntity.setNoGravity(true);
        redEntity.setInvisible(false);

        pullMobsWithRedOrb(serverLevel, redEntity, owner, next, direction);
        spawnBlueLikeOrbVisuals(serverLevel, redEntity, next, (int) tickCount);

        int chargeTier = (int) redEntity.getPersistentData().getDouble("addon_red_charge_tier");
        double traveled = redEntity.getPersistentData().getDouble("addon_red_travel") + speed;
        redEntity.getPersistentData().putDouble("addon_red_travel", traveled);
        double maxRange = redEntity.getPersistentData().getDouble("addon_red_max_range");

        // Dynamically scale orb size based on charge and distance traveled
        if (redEntity.getAttributes().hasAttribute(JujutsucraftModAttributes.SIZE.get())) {
            double progress = maxRange > 0.0 ? Math.min(1.0, traveled / maxRange) : 0.0;
            double chargeRatio = Math.max(0.2, Math.min(1.0, getEffectiveRedCnt6(redEntity) / 5.0));
            double baseScale = 2.6 + chargeTier * 1.2;
            double growth = progress * (2.0 + chargeTier * 1.5) * chargeRatio;
            double pulse = Math.sin((serverLevel.getGameTime() + tickCount) * 0.6) * (0.18 + chargeTier * 0.08);
            double targetScale = Math.max(2.0, baseScale + growth + pulse);
            redEntity.getAttribute(JujutsucraftModAttributes.SIZE.get()).setBaseValue(targetScale);
        }

        if (traveled >= maxRange) {
            explodeNormalRedOrb(redEntity, owner);
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED NORMAL — Blue-like visual effects during flight
    // ═══════════════════════════════════════════════════════════════════

    private static void spawnBlueLikeOrbVisuals(ServerLevel serverLevel, LivingEntity redEntity, Vec3 orbPos, int tickCount) {
        int chargeTier = (int) redEntity.getPersistentData().getDouble("addon_red_charge_tier");
        double range = 1.0 + chargeTier * 0.5;

        if (tickCount % 2 == 0) {
            for (int i = 0; i < 4 + chargeTier * 2; i++) {
                double ox = (Math.random() - 0.5) * range * 10.0;
                double oy = (Math.random() - 0.5) * range * 10.0;
                double oz = (Math.random() - 0.5) * range * 10.0;
                double sx = orbPos.x + ox;
                double sy = orbPos.y + oy;
                double sz = orbPos.z + oz;
                double vx = orbPos.x - sx;
                double vy = orbPos.y - sy;
                double vz = orbPos.z - sz;
                double dis = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (dis < 0.001) continue;
                vx /= dis; vy /= dis; vz /= dis;
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    new CommandSourceStack(CommandSource.NULL, new Vec3(sx, sy, sz), Vec2.ZERO,
                        serverLevel, 4, "", Component.literal(""), serverLevel.getServer(), null
                    ).withSuppressedOutput(),
                    String.format(Locale.ROOT,
                        "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.0025 0 force",
                        vx * 10000.0, vy * 10000.0, vz * 10000.0));
            }
        }

        double orbRadius = 0.4 + chargeTier * 0.25;
        serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 0.6f + chargeTier * 0.2f),
            orbPos.x, orbPos.y, orbPos.z, 4 + chargeTier * 2, orbRadius, orbRadius, orbRadius, 0.0);
        serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_RED.get(),
            orbPos.x, orbPos.y, orbPos.z, 3 + chargeTier,
            orbRadius * 0.6, orbRadius * 0.6, orbRadius * 0.6, 0.01);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
            orbPos.x, orbPos.y, orbPos.z, 1 + chargeTier, 0.15, 0.15, 0.15, 0.02);
        if (tickCount % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                orbPos.x, orbPos.y, orbPos.z, 2, orbRadius * 0.4, orbRadius * 0.4, orbRadius * 0.4, 0.01);
        }
        if (chargeTier >= 2) {
            serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_CURSE_POWER_RED.get(),
                orbPos.x, orbPos.y, orbPos.z, 1 + chargeTier, 0.2, 0.2, 0.2, 0.01);
        }
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            orbPos.x, orbPos.y, orbPos.z, 2 + chargeTier,
            orbRadius * 0.7, orbRadius * 0.7, orbRadius * 0.7, 0.02);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED NORMAL — Blue-like mob pull
    // ═══════════════════════════════════════════════════════════════════

    private static void pullMobsWithRedOrb(ServerLevel serverLevel, LivingEntity redEntity,
                                            LivingEntity owner, Vec3 orbPos, Vec3 direction) {
        double cnt6 = getEffectiveRedCnt6(redEntity);
        int chargeTier = getRedChargeTier(cnt6);
        Vec3 normDir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : Vec3.ZERO;
        String orbId = redEntity.getUUID().toString();

        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(orbPos, orbPos).inflate(RED_CAPTURE_RADIUS + 15.0),
            e -> e.isAlive() && e != redEntity && e != owner
                && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));

        int capturedIdx = 0;
        for (LivingEntity entity : nearby) {
            boolean alreadyCaptured = orbId.equals(entity.getPersistentData().getString("addon_red_attached_orb"));
            double dist = entity.position().distanceTo(orbPos);

            if (alreadyCaptured) {
                Vec3 trailPos = orbPos.subtract(normDir.scale(1.2 + capturedIdx * 0.7));
                entity.teleportTo(trailPos.x, trailPos.y, trailPos.z);
                entity.setDeltaMovement(normDir.scale(0.3));
                entity.setNoGravity(true);
                entity.hurtMarked = true;
                capturedIdx++;

                if (capturedIdx % 3 == 0) {
                    serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 0.5f),
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        2, 0.15, 0.15, 0.15, 0.0);
                }
                continue;
            }

            if (dist > RED_CAPTURE_RADIUS) continue;

            Vec3 toOrb = orbPos.subtract(entity.position());
            double pullStrength = 2.5 + chargeTier * 0.8;
            double closeFactor = 1.0 + (1.0 - Math.min(1.0, dist / RED_CAPTURE_RADIUS)) * 2.0;
            Vec3 pull = toOrb.normalize().scale(pullStrength * closeFactor);
            Vec3 forward = normDir.scale(1.2 + chargeTier * 0.4);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.15).add(pull).add(forward));
            entity.hurtMarked = true;

            if (dist < 3.5) {
                entity.getPersistentData().putString("addon_red_attached_orb", orbId);
                entity.setNoGravity(true);
                Vec3 snapPos = orbPos.subtract(normDir.scale(1.2));
                entity.teleportTo(snapPos.x, snapPos.y, snapPos.z);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED NORMAL — Spectacular explosion
    // ═══════════════════════════════════════════════════════════════════

    private static void explodeNormalRedOrb(LivingEntity redEntity, LivingEntity owner) {
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel serverLevel)) { redEntity.discard(); return; }

        Vec3 pos = getTrackedRedPosition(redEntity);
        double cnt6 = getEffectiveRedCnt6(redEntity);
        int chargeTier = getRedChargeTier(cnt6);

        // Mass particle explosion
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z,
            14 + chargeTier * 8, 2.6 + chargeTier * 1.2, 2.6 + chargeTier * 1.2, 2.6 + chargeTier * 1.2, 0.0);
        serverLevel.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 3, 0.12, 0.12, 0.12, 0.0);
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 2, 0.2, 0.15, 0.2, 0.0);
        serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
            190 + chargeTier * 110, 3.4 + chargeTier * 1.5, 3.4 + chargeTier * 1.5, 3.4 + chargeTier * 1.5, 0.14);
        serverLevel.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z,
            80 + chargeTier * 36, 2.8 + chargeTier, 2.8 + chargeTier, 2.8 + chargeTier, 0.0);
        serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 1.1f + chargeTier * 0.45f),
            pos.x, pos.y, pos.z, 260 + chargeTier * 130,
            4.2 + chargeTier * 1.6, 2.8 + chargeTier * 1.1, 4.2 + chargeTier * 1.6, 0.0);
        serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_RED.get(), pos.x, pos.y, pos.z,
            320 + chargeTier * 160, 4.5 + chargeTier * 1.2, 3.0 + chargeTier, 4.5 + chargeTier * 1.2, 0.04);
        serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_CURSE_POWER_RED.get(), pos.x, pos.y, pos.z,
            230 + chargeTier * 120, 3.8 + chargeTier * 1.1, 2.6 + chargeTier * 0.8, 3.8 + chargeTier * 1.1, 0.03);
        serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_BLOOD_RED.get(), pos.x, pos.y, pos.z,
            145 + chargeTier * 70, 3.0 + chargeTier, 2.2 + chargeTier * 0.8, 3.0 + chargeTier, 0.02);
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.x, pos.y, pos.z,
            95 + chargeTier * 50, 3.2 + chargeTier * 1.2, 2.2 + chargeTier * 0.8, 3.2 + chargeTier * 1.2, 0.06);
        serverLevel.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
            86 + chargeTier * 40, 3.9 + chargeTier * 1.2, 2.6 + chargeTier * 0.9, 3.9 + chargeTier * 1.2, 0.24);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z,
            40 + chargeTier * 18, 2.4 + chargeTier * 0.8, 1.4 + chargeTier * 0.5, 2.4 + chargeTier * 0.8, 0.12);

        for (int i = 0; i < 10 + chargeTier * 5; i++) {
            double ox = (Math.random() - 0.5) * 16.0;
            double oy = (Math.random() - 0.5) * 16.0;
            double oz = (Math.random() - 0.5) * 16.0;
            double vx = -ox, vy = -oy, vz = -oz;
            double dis = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (dis > 0.001) {
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    new CommandSourceStack(CommandSource.NULL,
                        new Vec3(pos.x + ox, pos.y + oy, pos.z + oz), Vec2.ZERO,
                        serverLevel, 4, "", Component.literal(""), serverLevel.getServer(), null
                    ).withSuppressedOutput(),
                    String.format(Locale.ROOT,
                        "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.0025 0 force",
                        vx / dis * 10000, vy / dis * 10000, vz / dis * 10000));
            }
        }

        world.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 3.2f, 0.85f);
        world.playSound(null, BlockPos.containing(pos), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.NEUTRAL, 2.6f, 0.68f);
        world.playSound(null, BlockPos.containing(pos), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 2.0f, 0.62f);
        world.playSound(null, BlockPos.containing(pos), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2.2f, 1.2f);

        // Damage and launch captured mobs
        String orbId = redEntity.getUUID().toString();
        List<LivingEntity> attachedMobs = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(pos, pos).inflate(30.0),
            e -> e.isAlive() && orbId.equals(e.getPersistentData().getString("addon_red_attached_orb")));
        for (LivingEntity mob : attachedMobs) {
            mob.getPersistentData().remove("addon_red_attached_orb");
            mob.setNoGravity(false);
            float damage = switch (chargeTier) { case 1 -> 18.0f; case 2 -> 34.0f; default -> 56.0f; };
            mob.hurt(serverLevel.damageSources().explosion(redEntity, owner), damage);
            mob.setDeltaMovement(mob.position().subtract(pos).normalize().scale(1.2 + chargeTier * 0.7));
        }

        applyRedExplosionShockwave(serverLevel, redEntity, owner, pos, chargeTier);
        float blockPower = switch (chargeTier) { case 1 -> 4.0f; case 2 -> 5.6f; default -> 7.2f; };
        world.explode(owner, pos.x, pos.y, pos.z, blockPower, Level.ExplosionInteraction.MOB);

        redEntity.getPersistentData().putBoolean("addon_red_normal_active", false);
        redEntity.discard();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED CROUCH FULL — Shift-cast: teleport + OG-style explosion
    // ═══════════════════════════════════════════════════════════════════

    private static void handleShiftCastExplosion(LivingEntity redEntity, LivingEntity owner, ServerLevel serverLevel) {
        Vec3 pos = getTrackedRedPosition(redEntity);
        double cnt6 = getEffectiveRedCnt6(redEntity);
        double CNT6 = 1.0 + cnt6 * 0.1;
        int chargeTier = getRedChargeTier(cnt6);

        redEntity.getPersistentData().putDouble("cnt6", cnt6);
        redEntity.getPersistentData().putDouble("x_pos", pos.x);
        redEntity.getPersistentData().putDouble("y_pos", pos.y);
        redEntity.getPersistentData().putDouble("z_pos", pos.z);

        for (int step = 0; step < 2; step++) {
            redEntity.getPersistentData().putDouble("Damage", 34.0 * CNT6);
            redEntity.getPersistentData().putDouble("Range", 10.0 * CNT6);
            redEntity.getPersistentData().putDouble("knockback", 2.0 * CNT6);
            redEntity.getPersistentData().putDouble("effect", 0.0);
            RangeAttackProcedure.execute(serverLevel, pos.x, pos.y, pos.z, redEntity);

            redEntity.getPersistentData().putDouble("BlockRange", 4.0 * CNT6);
            redEntity.getPersistentData().putDouble("BlockDamage", 4.0 * CNT6);
            redEntity.getPersistentData().putBoolean("noParticle", true);
            BlockDestroyAllDirectionProcedure.execute(serverLevel, pos.x, pos.y, pos.z, redEntity);

            redEntity.getPersistentData().putDouble("Range", 10.0 * CNT6);
            redEntity.getPersistentData().putDouble("knockback", 2.0 * CNT6);
            KnockbackProcedure.execute(serverLevel, pos.x, pos.y, pos.z, redEntity);
        }

        redEntity.getPersistentData().putDouble("BlockRange", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("BlockDamage", 0.33);
        redEntity.getPersistentData().putBoolean("noParticle", true);
        BlockDestroyAllDirectionProcedure.execute(serverLevel, pos.x, pos.y, pos.z, redEntity);

        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            pos.x, pos.y, pos.z, (int)(30.0 * CNT6), 4.0, 4.0, 4.0, 1.0);

        redEntity.getPersistentData().putDouble("Damage", 34.0 * CNT6);
        redEntity.getPersistentData().putDouble("Range", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("knockback", 4.0 * CNT6);
        redEntity.getPersistentData().putDouble("effect", 0.0);
        redEntity.getPersistentData().putDouble("y_knockback", 0.65);
        RangeAttackProcedure.execute(serverLevel, pos.x, pos.y - 1.5, pos.z, redEntity);

        redEntity.getPersistentData().putDouble("Range", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("knockback", 4.0 * CNT6);
        redEntity.getPersistentData().putDouble("effect", 1.0);
        KnockbackProcedure.execute(serverLevel, pos.x, pos.y - 1.5, pos.z, redEntity);

        serverLevel.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.0);
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 1.2f), pos.x, pos.y, pos.z,
            100 + chargeTier * 50, 3.0 + chargeTier, 2.0 + chargeTier * 0.6, 3.0 + chargeTier, 0.0);
        serverLevel.sendParticles(JujutsucraftModParticleTypes.PARTICLE_RED.get(), pos.x, pos.y, pos.z,
            80 + chargeTier * 40, 2.5 + chargeTier, 1.5 + chargeTier * 0.5, 2.5 + chargeTier, 0.02);
        serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
            60 + chargeTier * 30, 2.0 + chargeTier * 0.8, 1.5 + chargeTier * 0.5, 2.0 + chargeTier * 0.8, 0.08);

        serverLevel.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.5f, 0.8f);
        SoundEvent electric = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:electric_shock"));
        if (electric != null) {
            serverLevel.playSound(null, BlockPos.containing(pos), electric, SoundSource.NEUTRAL, 2.0f, 0.8f);
        }

        float blockPower = (float)(2.0 + CNT6 * 2.0);
        serverLevel.explode(owner, pos.x, pos.y, pos.z, blockPower, Level.ExplosionInteraction.MOB);

        redEntity.discard();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED CROUCH FULL — Teleport owner behind nearest target
    // ═══════════════════════════════════════════════════════════════════

    private static void handleRedTeleportBehind(LivingEntity redEntity) {
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) return;
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        Entity ownerEntity;
        try { ownerEntity = serverLevel.getEntity(UUID.fromString(ownerUUID)); }
        catch (Exception e) { return; }
        if (!(ownerEntity instanceof LivingEntity owner)) return;

        Vec3 eyePos = owner.getEyePosition(1.0f);
        Vec3 lookVec = owner.getLookAngle().normalize();
        double cosThreshold = Math.cos(Math.toRadians(AIM_CONE_ANGLE));

        List<Entity> candidates = world.getEntitiesOfClass(Entity.class,
            new AABB(owner.position(), owner.position()).inflate(TELEPORT_RANGE),
            e -> e instanceof LivingEntity && e != owner && e.isAlive()
                && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));

        LivingEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            Vec3 toEntity = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eyePos);
            double dist = toEntity.length();
            if (dist < 1.0 || dist > TELEPORT_RANGE) continue;
            double dot = lookVec.dot(toEntity.normalize());
            if (dot < cosThreshold) continue;
            double score = dist + (1.0 - dot) * 10.0;
            if (score < bestScore) { bestScore = score; target = (LivingEntity) candidate; }
        }
        if (target == null) return;

        Vec3 targetPos = target.position();
        Vec3 dirToOwner = owner.position().subtract(targetPos).normalize();
        Vec3 behindPos = targetPos.subtract(dirToOwner.scale(BEHIND_DISTANCE));
        behindPos = new Vec3(behindPos.x, targetPos.y, behindPos.z);

        double dx = targetPos.x - behindPos.x;
        double dz = targetPos.z - behindPos.z;
        float newYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        double dy = (targetPos.y + target.getBbHeight() * 0.5) - (behindPos.y + owner.getEyeHeight());
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float newPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizDist)));

        if (owner instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(serverLevel, behindPos.x, behindPos.y, behindPos.z, newYaw, newPitch);
        } else {
            owner.teleportTo(behindPos.x, behindPos.y, behindPos.z);
            owner.setYRot(newYaw); owner.setXRot(newPitch);
        }

        Vec3 shootDir = targetPos.add(0, target.getBbHeight() * 0.5, 0)
            .subtract(behindPos.add(0, owner.getEyeHeight(), 0)).normalize();
        redEntity.getPersistentData().putDouble("x_power", shootDir.x * 3.0);
        redEntity.getPersistentData().putDouble("y_power", shootDir.y * 3.0);
        redEntity.getPersistentData().putDouble("z_power", shootDir.z * 3.0);

        double redY = behindPos.y + owner.getEyeHeight() * 0.75;
        redEntity.teleportTo(behindPos.x, redY, behindPos.z);
        redEntity.getPersistentData().putDouble("x_pos", behindPos.x);
        redEntity.getPersistentData().putDouble("y_pos", redY);
        redEntity.getPersistentData().putDouble("z_pos", behindPos.z);

        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
            behindPos.x, behindPos.y + 1.0, behindPos.z, 30, 0.5, 1.0, 0.5, 0.1);
        world.playSound(null, BlockPos.containing(behindPos.x, behindPos.y, behindPos.z),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED — Charging orb visual (pre-init)
    // ═══════════════════════════════════════════════════════════════════

    private static void updateChargingRedOrbVisual(LivingEntity redEntity, LivingEntity owner, double cachedCnt6) {
        if (!(redEntity.level() instanceof ServerLevel serverLevel)) return;

        Vec3 look = owner.getLookAngle().normalize();
        if (look.lengthSqr() < 1.0E-6) look = new Vec3(0, 0, 1);

        Vec3 chargePos = owner.getEyePosition(1.0f)
            .add(look.scale(RED_CHARGE_ANCHOR_DISTANCE))
            .add(0.0, RED_CHARGE_ANCHOR_Y_OFFSET, 0.0);

        redEntity.teleportTo(chargePos.x, chargePos.y, chargePos.z);
        redEntity.setDeltaMovement(Vec3.ZERO);
        redEntity.setNoGravity(true);
        redEntity.setInvisible(false);

        redEntity.getPersistentData().putDouble("x_pos", chargePos.x);
        redEntity.getPersistentData().putDouble("y_pos", chargePos.y);
        redEntity.getPersistentData().putDouble("z_pos", chargePos.z);
        redEntity.getPersistentData().putDouble("addon_red_charge_anchor_x", chargePos.x);
        redEntity.getPersistentData().putDouble("addon_red_charge_anchor_y", chargePos.y);
        redEntity.getPersistentData().putDouble("addon_red_charge_anchor_z", chargePos.z);
        redEntity.getPersistentData().putBoolean("addon_red_charge_anchor_valid", true);

        double cnt6 = Math.max(cachedCnt6, redEntity.getPersistentData().getDouble("cnt6"));
        int chargeTier = getRedChargeTier(cnt6);
        double ratio = Math.max(0.2, Math.min(cnt6, 5.0) / 5.0);
        if (redEntity.getAttributes().hasAttribute(JujutsucraftModAttributes.SIZE.get())) {
            redEntity.getAttribute(JujutsucraftModAttributes.SIZE.get()).setBaseValue(1.6 + ratio * 2.6);
        }

        redEntity.getPersistentData().putDouble("x_power", look.x * 0.25);
        redEntity.getPersistentData().putDouble("y_power", look.y * 0.25);
        redEntity.getPersistentData().putDouble("z_power", look.z * 0.25);

        serverLevel.sendParticles(ParticleTypes.END_ROD, chargePos.x, chargePos.y, chargePos.z,
            1 + chargeTier, 0.08 + ratio * 0.2, 0.08 + ratio * 0.2, 0.08 + ratio * 0.2, 0.02);
        serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 0.5f + (float)ratio * 0.8f),
            chargePos.x, chargePos.y, chargePos.z, 3 + chargeTier,
            0.12 + ratio * 0.18, 0.12 + ratio * 0.18, 0.12 + ratio * 0.18, 0.0);
        serverLevel.sendParticles(ParticleTypes.FLAME, chargePos.x, chargePos.y, chargePos.z,
            2 + chargeTier, 0.1 + ratio * 0.14, 0.1 + ratio * 0.14, 0.1 + ratio * 0.14, 0.0);

        Vec3 from = owner.getEyePosition(1.0f);
        for (int i = 0; i <= 5; i++) {
            Vec3 p = from.lerp(chargePos, i / 5.0);
            serverLevel.sendParticles(new DustParticleOptions(RED_TRAIL_COLOR, 0.35f + chargeTier * 0.1f),
                p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RED — Explosion shockwave (AoE damage/knockback)
    // ═══════════════════════════════════════════════════════════════════

    private static void applyRedExplosionShockwave(ServerLevel serverLevel, LivingEntity redEntity,
                                                    LivingEntity owner, Vec3 pos, int chargeTier) {
        double radius = 4.0 + chargeTier * 2.4;
        float baseDamage = switch (chargeTier) { case 1 -> 16.0f; case 2 -> 28.0f; default -> 42.0f; };

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(pos, pos).inflate(radius),
            e -> e.isAlive() && e != redEntity && e != owner
                && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));

        for (LivingEntity target : targets) {
            Vec3 delta = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(pos);
            double distance = Math.max(0.001, delta.length());
            double falloff = 1.0 - Math.min(1.0, distance / radius);
            if (falloff <= 0.0) continue;

            target.hurt(serverLevel.damageSources().explosion(redEntity, owner),
                (float)(baseDamage * (0.55 + 0.45 * falloff)));
            Vec3 push = delta.normalize().scale((0.7 + chargeTier * 0.3) * (0.35 + falloff));
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.18 + falloff * 0.2, push.z));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PURPLE NUKE — Activation check
    // ═══════════════════════════════════════════════════════════════════

    private static boolean checkAndActivatePurpleNuke(LivingEntity redEntity, LivingEntity owner,
                                                       ServerLevel serverLevel) {
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        Level world = redEntity.level();

        Vec3 projectilePos = getTrackedRedPosition(redEntity);
        List<Entity> nearbyBlues = world.getEntitiesOfClass(Entity.class,
            new AABB(projectilePos, projectilePos).inflate(NUKE_COLLISION_RADIUS),
            e -> e instanceof BlueEntity);

        for (Entity nearby : nearbyBlues) {
            if (!(nearby instanceof BlueEntity blueEntity)) continue;
            if (!blueEntity.getPersistentData().getBoolean("linger_active")) continue;

            String blueOwner = blueEntity.getPersistentData().getString("OWNER_UUID");
            if (!ownerUUID.equals(blueOwner)) continue;

            double blueCnt6 = blueEntity.getPersistentData().getDouble("linger_cnt6");
            if (blueCnt6 <= 0.0) blueCnt6 = blueEntity.getPersistentData().getDouble("cnt6");
            if (blueCnt6 < 5.0) continue;

            if (!(owner instanceof Player ownerPlayer)) continue;

            double currentCE = ((JujutsucraftModVariables.PlayerVariables) ownerPlayer
                .getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(new JujutsucraftModVariables.PlayerVariables())).PlayerCursePower;
            if (currentCE < NUKE_CE_THRESHOLD) continue;
            if (ownerPlayer.getHealth() / ownerPlayer.getMaxHealth() > NUKE_HP_THRESHOLD) continue;

            double cx = blueEntity.getX(), cy = blueEntity.getY(), cz = blueEntity.getZ();

            double HP = 800;
            if (ownerPlayer.hasEffect(MobEffects.DAMAGE_BOOST)) {
                HP = 800 + ownerPlayer.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() * 80;
            }

            double xPower = redEntity.getPersistentData().getDouble("x_power");
            double yPower = redEntity.getPersistentData().getDouble("y_power");
            double zPower = redEntity.getPersistentData().getDouble("z_power");

            serverLevel.getServer().getCommands().performPrefixedCommand(
                new CommandSourceStack(CommandSource.NULL, new Vec3(cx, cy, cz), Vec2.ZERO,
                    serverLevel, 4, "", Component.literal(""), serverLevel.getServer(), null
                ).withSuppressedOutput(),
                "summon jujutsucraft:purple ~ ~ ~ {Health:" + Math.round(HP)
                    + "f,Attributes:[{Name:generic.max_health,Base:" + Math.round(HP)
                    + "}],Rotation:[" + ownerPlayer.getYRot() + "F," + ownerPlayer.getXRot() + "F]}");

            Vec3 center = new Vec3(cx, cy, cz);
            double redCnt6 = getEffectiveRedCnt6(redEntity);
            boolean purpleSpawned = false;

            List<Entity> purples = world.getEntitiesOfClass(Entity.class,
                new AABB(center, center).inflate(2.0), e -> true)
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(center))).toList();

            for (Entity purpleE : purples) {
                if (!(purpleE instanceof PurpleEntity)) continue;
                if (purpleE.getPersistentData().getDouble("NameRanged_ranged") != 0.0) continue;

                SetRangedAmmoProcedure.execute(ownerPlayer, purpleE);
                purpleE.getPersistentData().putBoolean("explode", true);

                double maxCnt6 = Math.max(redCnt6, blueCnt6) * 2.0;
                purpleE.getPersistentData().putDouble("cnt6", maxCnt6);

                if (purpleE instanceof LivingEntity livingPurple) {
                    try {
                        if (livingPurple.getAttributes().hasAttribute(JujutsucraftModAttributes.SIZE.get())) {
                            livingPurple.getAttribute(JujutsucraftModAttributes.SIZE.get())
                                .setBaseValue(24.0 * (0.5 + maxCnt6 * 0.2));
                        }
                    } catch (Exception ignored) {}
                }

                purpleE.getPersistentData().putDouble("x_power", xPower);
                purpleE.getPersistentData().putDouble("y_power", yPower);
                purpleE.getPersistentData().putDouble("z_power", zPower);
                purpleE.getPersistentData().putDouble("cnt3", 1.0);
                purpleSpawned = true;
                break;
            }

            if (purpleSpawned) {
                final double finalCE = currentCE;
                ownerPlayer.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                    .ifPresent(cap -> {
                        cap.PlayerCursePower = finalCE - NUKE_CE_COST;
                        cap.syncPlayerVariables(ownerPlayer);
                    });
            }

            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, 2.0, 2.0, 2.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 50, 3.0, 3.0, 3.0, 0.5);

            SoundEvent electric = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("jujutsucraft:electric_shock"));
            if (electric != null) {
                world.playSound(null, BlockPos.containing(cx, cy, cz), electric, SoundSource.NEUTRAL, 4.0f, 0.5f);
            }
            world.playSound(null, BlockPos.containing(cx, cy, cz),
                SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4.0f, 0.5f);

            redEntity.discard();
            blueEntity.discard();
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INFINITY CRUSHER — Directional crush with scaling
    // ═══════════════════════════════════════════════════════════════════

    private static void handleInfinityCrusher(ServerPlayer player) {
        Level world = player.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        JujutsucraftModVariables.PlayerVariables vars = player
            .getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null)
            .orElse(new JujutsucraftModVariables.PlayerVariables());

        double ct = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        if ((int) Math.round(ct) != 2) return;
        if (!player.getPersistentData().getBoolean("infinity")) return;
        if ((int) Math.round(vars.PlayerSelectCurseTechnique) != 5) return;
        if (!player.isCrouching()) {
            resetInfinityCrusher(player);
            return;
        }

        var data = player.getPersistentData();
        int activeTicks = data.getInt("addon_infinity_crusher_ticks") + 1;
        data.putInt("addon_infinity_crusher_ticks", activeTicks);

        double ceDrain = CRUSHER_BASE_CE_DRAIN + activeTicks * CRUSHER_CE_DRAIN_GROWTH;
        double currentCE = vars.PlayerCursePower;
        if (currentCE < ceDrain) {
            resetInfinityCrusher(player);
            return;
        }
        double finalCE = currentCE - ceDrain;
        double totalCEDrained = data.getDouble("addon_infinity_crusher_total_ce") + ceDrain;
        data.putDouble("addon_infinity_crusher_total_ce", totalCEDrained);
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            cap.PlayerCursePower = finalCE;
            cap.syncPlayerVariables(player);
        });

        double growthFactor = Math.min(1.0, activeTicks / 200.0);
        double currentRadius = CRUSHER_MIN_RADIUS + (CRUSHER_MAX_RADIUS - CRUSHER_MIN_RADIUS) * growthFactor;
        float wallDamage = CRUSHER_BASE_WALL_DAMAGE + (float)(growthFactor * 8.0)
            + (float)(totalCEDrained * CRUSHER_CE_DAMAGE_SCALE);

        Vec3 center = new Vec3(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 auraCenter = center.add(lookDir.scale(currentRadius * 0.5));

        float dustSize = (float)(0.8 + growthFactor * 0.8);
        int particleCount = (int)(8 + growthFactor * 20);
        Vector3f auraColor = growthFactor < 0.5
            ? BLUE_AURA_COLOR
            : new Vector3f(
                (float)(0.1 + growthFactor * 0.6),
                (float)(0.3 - growthFactor * 0.2),
                (float)(0.95 - growthFactor * 0.3));

        serverLevel.sendParticles(new DustParticleOptions(auraColor, dustSize),
            auraCenter.x, auraCenter.y, auraCenter.z, particleCount,
            currentRadius * 0.6, currentRadius * 0.4, currentRadius * 0.6, 0.0);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            auraCenter.x, auraCenter.y, auraCenter.z, (int)(4 + growthFactor * 10),
            currentRadius * 0.4, currentRadius * 0.3, currentRadius * 0.4, 0.01);

        if (growthFactor > 0.5) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                auraCenter.x, auraCenter.y, auraCenter.z, (int)(growthFactor * 6),
                currentRadius * 0.5, currentRadius * 0.4, currentRadius * 0.5, 0.05);
        }

        if (serverLevel.getGameTime() % 3 == 0) {
            int convergeCount = (int)(3 + growthFactor * 5);
            for (int i = 0; i < convergeCount; i++) {
                double spread = currentRadius * 2.0;
                Vec3 particleStart = auraCenter.add(
                    (Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread);
                Vec3 vel = auraCenter.subtract(particleStart);
                double dis = vel.length();
                if (dis < 0.01) continue;
                vel = vel.scale(1.0 / dis);
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    new CommandSourceStack(CommandSource.NULL, particleStart, Vec2.ZERO,
                        serverLevel, 4, "", Component.literal(""), serverLevel.getServer(), null
                    ).withSuppressedOutput(),
                    String.format(Locale.ROOT,
                        "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.003 0 force",
                        vel.x * 10000, vel.y * 10000, vel.z * 10000));
            }
        }

        // ═══ Escalating Infinity Crusher sound effects ═══
        playInfinityCrusherSounds(serverLevel, player, auraCenter, activeTicks, growthFactor);

        // ═══ Reflect projectiles ═══
        double reflectRadius = Math.max(currentRadius + 1.0, (4.0 + player.getBbWidth()) / 2.0);
        double myNameRanged = data.getDouble("NameRanged");
        double myNameRangedR = data.getDouble("NameRanged_ranged");

        List<Entity> nearbyEntities = world.getEntitiesOfClass(Entity.class,
            new AABB(center, center).inflate(reflectRadius), e -> e != player);

        for (Entity entity : nearbyEntities) {
            boolean isRangedAmmo = entity.getType().is(
                TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge:ranged_ammo")))
                || entity.getPersistentData().getDouble("NameRanged_ranged") != 0.0;
            boolean isProjectile = entity instanceof Projectile;
            if (!isRangedAmmo && !isProjectile) continue;

            double entNR = entity.getPersistentData().getDouble("NameRanged");
            double entNRR = entity.getPersistentData().getDouble("NameRanged_ranged");
            boolean isOwn = false;
            if (entNRR != 0.0 && (entNRR == myNameRanged || entNRR == myNameRangedR)) isOwn = true;
            if (entNR != 0.0 && (entNR == myNameRangedR || entNR == myNameRanged)) isOwn = true;
            if (isOwn) continue;

            Vec3 vel = entity.getDeltaMovement();
            if (vel.length() > 0.01) {
                entity.setDeltaMovement(vel.scale(-1.5));
                entity.hurtMarked = true;
                if (entity instanceof Projectile proj) proj.setOwner(player);
                if (entity instanceof LivingEntity le) {
                    SetRangedAmmoProcedure.execute(player, entity);
                }
            } else if (isRangedAmmo) {
                Vec3 push = entity.position().subtract(center).normalize().scale(0.5);
                entity.setDeltaMovement(push);
                entity.hurtMarked = true;
            }
        }

        // ═══ Directional mob crush with hard-lock ═══
        Vec3 playerVel = player.getDeltaMovement();
        double forwardVel = playerVel.x * lookDir.x + playerVel.y * lookDir.y + playerVel.z * lookDir.z;
        boolean isMovingForward = forwardVel > 0.01;

        List<LivingEntity> nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(center, center).inflate(currentRadius + 3.0),
            e -> e.isAlive() && e != player
                && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));

        String playerUUID = player.getStringUUID();

        for (LivingEntity mob : nearbyMobs) {
            Vec3 toMob = mob.position().add(0, mob.getBbHeight() * 0.5, 0).subtract(center);
            double dist = toMob.length();

            boolean isHardLocked = playerUUID.equals(mob.getPersistentData().getString("addon_crusher_lock_owner"));
            int contactTicks = mob.getPersistentData().getInt("addon_crusher_contact_ticks");

            if (isHardLocked) {
                double lockX = mob.getPersistentData().getDouble("addon_crusher_lock_x");
                double lockY = mob.getPersistentData().getDouble("addon_crusher_lock_y");
                double lockZ = mob.getPersistentData().getDouble("addon_crusher_lock_z");

                double lockDistSq = player.distanceToSqr(lockX, lockY, lockZ);
                double releaseRange = currentRadius + 5.0;
                if (lockDistSq > releaseRange * releaseRange) {
                    mob.setNoGravity(false);
                    mob.getPersistentData().remove("addon_crusher_lock_owner");
                    mob.getPersistentData().putInt("addon_crusher_contact_ticks", 0);
                    continue;
                }

                mob.teleportTo(lockX, lockY, lockZ);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.setNoGravity(true);
                mob.hurtMarked = true;

                mob.hurt(serverLevel.damageSources().generic(), wallDamage);
                spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
                if (activeTicks % 8 == 0) {
                    world.playSound(null, BlockPos.containing(lockX, lockY, lockZ),
                        SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f + (float)growthFactor * 0.3f, 1.4f);
                }
                continue;
            }

            if (dist > currentRadius + 2.0) {
                if (contactTicks > 0) {
                    mob.getPersistentData().putInt("addon_crusher_contact_ticks", 0);
                    mob.getPersistentData().remove("addon_crusher_lock_owner");
                }
                continue;
            }

            Vec3 toMobNorm = toMob.normalize();
            double dot = lookDir.x * toMobNorm.x + lookDir.y * toMobNorm.y + lookDir.z * toMobNorm.z;
            if (dot < CRUSHER_CONE_COS) continue;
            if (!isMovingForward) continue;

            contactTicks++;
            mob.getPersistentData().putInt("addon_crusher_contact_ticks", contactTicks);

            if (contactTicks >= CRUSHER_HARDLOCK_THRESHOLD) {
                mob.getPersistentData().putString("addon_crusher_lock_owner", playerUUID);
                mob.getPersistentData().putDouble("addon_crusher_lock_x", mob.getX());
                mob.getPersistentData().putDouble("addon_crusher_lock_y", mob.getY());
                mob.getPersistentData().putDouble("addon_crusher_lock_z", mob.getZ());
            }

            double pushStrength = CRUSHER_PUSH_STRENGTH * (0.6 + 0.4 * dot) * (1.0 + growthFactor * 0.5);
            Vec3 pushDir = lookDir.scale(pushStrength).add(toMobNorm.scale(-0.05));
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.2).add(pushDir));
            mob.hurtMarked = true;

            if ((mob.horizontalCollision || mob.verticalCollision) && dist < currentRadius + 1.5) {
                mob.hurt(serverLevel.damageSources().generic(), wallDamage * 0.5f);
                spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
                if (activeTicks % 12 == 0) {
                    world.playSound(null, BlockPos.containing(mob.getX(), mob.getY(), mob.getZ()),
                        SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.35f, 1.6f);
                }
            }
        }
    }

    private static void resetInfinityCrusher(ServerPlayer player) {
        var data = player.getPersistentData();
        data.putInt("addon_infinity_crusher_ticks", 0);
        data.putDouble("addon_infinity_crusher_total_ce", 0);
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        String playerUUID = player.getStringUUID();
        List<LivingEntity> locked = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(player.position(), player.position()).inflate(10.0),
            e -> playerUUID.equals(e.getPersistentData().getString("addon_crusher_lock_owner")));
        for (LivingEntity mob : locked) {
            mob.setNoGravity(false);
            mob.getPersistentData().remove("addon_crusher_lock_owner");
            mob.getPersistentData().remove("addon_crusher_lock_x");
            mob.getPersistentData().remove("addon_crusher_lock_y");
            mob.getPersistentData().remove("addon_crusher_lock_z");
            mob.getPersistentData().putInt("addon_crusher_contact_ticks", 0);
        }
    }

    private static void spawnCrushVFX(ServerLevel serverLevel, LivingEntity mob, Vector3f color, double intensity) {
        double mx = mob.getX(), my = mob.getY() + mob.getBbHeight() * 0.5, mz = mob.getZ();
        int basePart = (int)(6 + intensity * 10);
        serverLevel.sendParticles(ParticleTypes.CRIT, mx, my, mz, basePart, 0.3, 0.4, 0.3, 0.15 + intensity * 0.1);
        serverLevel.sendParticles(new DustParticleOptions(color, (float)(0.8 + intensity * 0.5)), mx, my, mz, (int)(4 + intensity * 6), 0.2, 0.3, 0.2, 0.0);
        serverLevel.sendParticles(ParticleTypes.CLOUD, mx, my, mz, (int)(2 + intensity * 4), 0.2, 0.2, 0.2, 0.04);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, mx, my, mz, (int)(3 + intensity * 5), 0.2, 0.2, 0.2, 0.1);
        if (intensity > 0.6) {
            serverLevel.sendParticles(ParticleTypes.FLASH, mx, my, mz, 1, 0, 0, 0, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INFINITY CRUSHER — Escalating sound effects
    //  Three layers:
    //    • Ambient hum  — soft continuous buzz, pitch rises with power (every 5 ticks)
    //    • Pulse rumble — periodic thump that speeds up (every 40→15 ticks)
    //    • Impact crush — anvil clang when a locked mob takes damage
    // ═══════════════════════════════════════════════════════════════════

    private static void playInfinityCrusherSounds(ServerLevel serverLevel, ServerPlayer player,
                                                 Vec3 auraCenter, int activeTicks, double growthFactor) {
        // ── Layer 1: Ambient hum (every 5 ticks) ──────────────────────
        if (activeTicks % 5 == 0) {
            float basePitch = 0.8f + (float)growthFactor * 0.6f;   // 0.80 → 1.40
            float volume   = 0.05f + (float)growthFactor * 0.08f;  // 0.05 → 0.13
            serverLevel.playSound(null, BlockPos.containing(auraCenter),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                volume, basePitch);
        }

        // ── Layer 2: Pulse rumble (every 40→15 ticks) ──────────────────
        // Early phase: slow rumble. Late phase: rapid heartbeat-like thump.
        int rumbleInterval = Math.max(15, 40 - (int)(growthFactor * 25));
        if (activeTicks % rumbleInterval == 0) {
            float pitch = 0.5f + (float)growthFactor * 0.5f;  // 0.50 → 1.00
            float volume = 0.3f + (float)growthFactor * 0.25f; // 0.30 → 0.55
            serverLevel.playSound(null, BlockPos.containing(auraCenter),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                volume, pitch);
        }

        // ── Layer 3: Crush impact clang (reuses existing anvil trigger, already present) ──
        // Already handled by the existing `activeTicks % 8` anvil sound in the mob damage loop.
        // The guard above fires the same sound, so we only add a second layer here
        // (high-intensity electric crackle) when the crusher is very powerful.
        if (growthFactor > 0.7f && activeTicks % 10 == 0) {
            serverLevel.playSound(null, BlockPos.containing(auraCenter),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                0.25f + (float)(growthFactor - 0.7f) * 0.6f,
                1.2f + (float)growthFactor * 0.4f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BLUE — Linger & crouch aim
    // ═══════════════════════════════════════════════════════════════════

    private void handleBlueTick(LivingEntity blueEntity) {
        // Do not interfere with Blue orbs spawned by Hollow Purple / Purple Nuke
        if (blueEntity instanceof net.mcreator.jujutsucraft.entity.BlueEntity be) {
            try {
                if (be.getEntityData().get(net.mcreator.jujutsucraft.entity.BlueEntity.DATA_flag_purple)) return;
            } catch (Exception ignored) {}
        }
        if (blueEntity.getPersistentData().getBoolean("flag_purple")) return;

        // ══ LINGER PHASE: orb frozen in place, NO movement/aim, waiting for RED ══
        if (blueEntity.getPersistentData().getBoolean("linger_active")) {
            int timer = blueEntity.getPersistentData().getInt("linger_timer") + 1;
            blueEntity.getPersistentData().putInt("linger_timer", timer);
            blueEntity.getPersistentData().putDouble("cnt1", 0.0);

            double lx = blueEntity.getPersistentData().getDouble("linger_x");
            double ly = blueEntity.getPersistentData().getDouble("linger_y");
            double lz = blueEntity.getPersistentData().getDouble("linger_z");
            blueEntity.teleportTo(lx, ly, lz);
            blueEntity.setDeltaMovement(Vec3.ZERO);
            blueEntity.setHealth(blueEntity.getMaxHealth());

            if (blueEntity.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, lx, ly, lz, 3, 0.5, 0.5, 0.5, 0.02);
                if (timer % 20 == 0) {
                    sl.sendParticles(ParticleTypes.END_ROD, lx, ly, lz, 10, 1.0, 1.0, 1.0, 0.1);
                }
            }

            if (timer >= LINGER_DURATION) blueEntity.discard();
            return;
        }

        // ══ ORBIT PHASE ══
        boolean flagStart = getBlueEntityFlagStart(blueEntity);
        if (!flagStart) return;

        boolean circle = blueEntity.getPersistentData().getBoolean("circle");
        boolean aimEnded = blueEntity.getPersistentData().getBoolean("aim_ended");
        double cnt6 = blueEntity.getPersistentData().getDouble("cnt6");

        // Aim control: ONLY if Blue was started with crouch (circle=true),
        // full charged (cnt6 >= 5), player has NEVER released crouch since spawn,
        // and aim time hasn't expired.
        boolean aiming = false;
        if (circle && cnt6 >= 5.0 && !aimEnded) {
            int aimTicks = blueEntity.getPersistentData().getInt("addon_aim_ticks");
            if (!isOwnerCrouching(blueEntity) || aimTicks >= BLUE_AIM_DURATION) {
                blueEntity.getPersistentData().putBoolean("aim_ended", true);
                blueEntity.getPersistentData().putBoolean("addon_aim_active", false);
            } else {
                aiming = true;
                blueEntity.getPersistentData().putInt("addon_aim_ticks", aimTicks + 1);
                blueEntity.getPersistentData().putBoolean("addon_aim_active", true);
                if (cnt6 >= 5.0) {
                    blueEntity.addEffect(new MobEffectInstance(MobEffects.LUCK, 5, 15, false, false));
                }
                applyBlueAim(blueEntity);
            }
        }

        // Determine whether to transition to linger
        if (cnt6 < 5.0) return;

        boolean shouldLinger;
        if (circle && !aiming && aimEnded) {
            shouldLinger = true;
        } else if (circle && aiming) {
            shouldLinger = false;
        } else {
            double cnt1 = blueEntity.getPersistentData().getDouble("cnt1");
            double threshold = 30.0 * (1.0 + cnt6 * 0.1);
            shouldLinger = cnt1 >= threshold - 10;
        }

        if (!shouldLinger) return;

        blueEntity.getPersistentData().putBoolean("circle", false);
        blueEntity.getPersistentData().putBoolean("linger_active", true);
        blueEntity.getPersistentData().putBoolean("addon_aim_active", false);
        blueEntity.getPersistentData().putInt("linger_timer", 0);
        blueEntity.getPersistentData().putDouble("linger_x", blueEntity.getX());
        blueEntity.getPersistentData().putDouble("linger_y", blueEntity.getY());
        blueEntity.getPersistentData().putDouble("linger_z", blueEntity.getZ());
        blueEntity.getPersistentData().putDouble("linger_cnt6", cnt6);
        blueEntity.getPersistentData().putDouble("cnt1", 0.0);

        // Block AIBlueRedProcedure from detecting this Blue (prevents false Hollow Purple triggers)
        blueEntity.getPersistentData().putDouble("NameRanged_ranged", 0.0);
    }

    /**
     * Orbit phase aim: smooth speed-limited movement toward the player's look direction.
     */
    private void applyBlueAim(LivingEntity blueEntity) {
        if (!(blueEntity.level() instanceof ServerLevel serverLevel)) return;

        String ownerUUID = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) return;
        LivingEntity owner = resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player playerOwner)) return;

        Vec3 eye = playerOwner.getEyePosition(1.0f);
        Vec3 look = playerOwner.getLookAngle().normalize();
        if (look.lengthSqr() < 1.0E-6) return;

        Vec3 rayEnd = eye.add(look.scale(BLUE_CROUCH_MAX_DISTANCE));
        HitResult lookHit = serverLevel.clip(new ClipContext(
            eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, playerOwner));
        double targetDist = BLUE_CROUCH_MAX_DISTANCE;
        if (lookHit != null && lookHit.getType() == HitResult.Type.BLOCK) {
            targetDist = Math.min(targetDist, eye.distanceTo(lookHit.getLocation()));
        }
        targetDist = Math.max(BLUE_CROUCH_MIN_DISTANCE, Math.min(BLUE_CROUCH_MAX_DISTANCE, targetDist));
        Vec3 target = eye.add(look.scale(targetDist));

        Vec3 current = blueEntity.position();
        Vec3 delta = target.subtract(current);
        double dist = delta.length();
        Vec3 newPos;
        if (dist > BLUE_AIM_ORB_SPEED) {
            newPos = current.add(delta.normalize().scale(BLUE_AIM_ORB_SPEED));
        } else {
            newPos = target;
        }

        Vec3 fromPlayer = newPos.subtract(eye);
        double distFromPlayer = fromPlayer.length();
        if (distFromPlayer < BLUE_CROUCH_MIN_DISTANCE) {
            newPos = eye.add(fromPlayer.normalize().scale(BLUE_CROUCH_MIN_DISTANCE));
        } else if (distFromPlayer > BLUE_CROUCH_MAX_DISTANCE) {
            newPos = eye.add(fromPlayer.normalize().scale(BLUE_CROUCH_MAX_DISTANCE));
        }

        blueEntity.teleportTo(newPos.x, newPos.y, newPos.z);
        blueEntity.setDeltaMovement(Vec3.ZERO);
        blueEntity.getPersistentData().putDouble("x_pos", newPos.x);
        blueEntity.getPersistentData().putDouble("y_pos", newPos.y);
        blueEntity.getPersistentData().putDouble("z_pos", newPos.z);
        blueEntity.getPersistentData().putDouble("x_power", look.x * 0.2);
        blueEntity.getPersistentData().putDouble("y_power", look.y * 0.2);
        blueEntity.getPersistentData().putDouble("z_power", look.z * 0.2);
        blueEntity.setYRot(playerOwner.getYRot());
        blueEntity.setXRot(playerOwner.getXRot());
    }

    private boolean isOwnerCrouching(LivingEntity blueEntity) {
        if (!(blueEntity.level() instanceof ServerLevel serverLevel)) return false;
        String uuid = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (uuid.isEmpty()) return false;
        LivingEntity owner = resolveOwner(serverLevel, uuid);
        return owner instanceof Player p && p.isCrouching();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static boolean getBlueEntityFlagStart(LivingEntity entity) {
        if (!(entity instanceof BlueEntity be)) return false;
        try {
            return be.getEntityData().get(BlueEntity.DATA_flag_start);
        } catch (Exception e) {
            return false;
        }
    }

    private static void updateOrbPosition(LivingEntity redEntity, Vec3 pos) {
        redEntity.getPersistentData().putDouble("x_pos", pos.x);
        redEntity.getPersistentData().putDouble("y_pos", pos.y);
        redEntity.getPersistentData().putDouble("z_pos", pos.z);
        redEntity.teleportTo(pos.x, pos.y, pos.z);
    }

    private static Vec3 getTrackedRedPosition(LivingEntity redEntity) {
        double x = redEntity.getPersistentData().getDouble("x_pos");
        double y = redEntity.getPersistentData().getDouble("y_pos");
        double z = redEntity.getPersistentData().getDouble("z_pos");
        if (x == 0.0 && y == 0.0 && z == 0.0) return redEntity.position();
        return new Vec3(x, y, z);
    }

    private static double getEffectiveRedCnt6(LivingEntity redEntity) {
        return Math.max(redEntity.getPersistentData().getDouble("cnt6"),
            Math.max(redEntity.getPersistentData().getDouble("addon_red_charge_cached"),
                redEntity.getPersistentData().getDouble("addon_red_charge_used")));
    }

    private static LivingEntity resolveOwner(ServerLevel serverLevel, String ownerUUID) {
        try {
            UUID uuid = UUID.fromString(ownerUUID);
            Entity ownerEntity = serverLevel.getEntity(uuid);
            if (ownerEntity instanceof LivingEntity living) return living;
            ServerPlayer sp = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (sp != null) return sp;
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BLUE CROUCH FULL CHARGE — Enhanced aim with block destruction & pull
    // ═══════════════════════════════════════════════════════════════════

    private static final double BLUE_FULL_PULL_RADIUS = 6.0;
    private static final double BLUE_FULL_PULL_STRENGTH = 1.2;
    private static final int    BLUE_FULL_BLOCK_RANGE = 4;
    private static final float  BLUE_FULL_BLOCK_POWER = 4.0f;

    public static void handleCrouchFullChargeBlueAim(LivingEntity blueEntity) {
        if (!(blueEntity.level() instanceof ServerLevel serverLevel)) return;

        String ownerUUID = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) return;
        LivingEntity owner = resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player playerOwner)) return;

        Vec3 eye = playerOwner.getEyePosition(1.0f);
        Vec3 look = playerOwner.getLookAngle().normalize();
        if (look.lengthSqr() < 1.0E-6) look = new Vec3(0, 0, 1);

        Vec3 rayEnd = eye.add(look.scale(BLUE_CROUCH_MAX_DISTANCE));
        HitResult lookHit = serverLevel.clip(new ClipContext(
            eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, playerOwner));
        double targetDist = BLUE_CROUCH_MAX_DISTANCE;
        if (lookHit != null && lookHit.getType() == HitResult.Type.BLOCK) {
            targetDist = Math.min(targetDist, eye.distanceTo(lookHit.getLocation()));
        }
        targetDist = Math.max(BLUE_CROUCH_MIN_DISTANCE, Math.min(BLUE_CROUCH_MAX_DISTANCE, targetDist));
        Vec3 target = eye.add(look.scale(targetDist));

        Vec3 current = blueEntity.position();
        Vec3 delta = target.subtract(current);
        double dist = delta.length();
        Vec3 newPos;
        if (dist > BLUE_AIM_ORB_SPEED) {
            newPos = current.add(delta.normalize().scale(BLUE_AIM_ORB_SPEED));
        } else {
            newPos = target;
        }

        Vec3 fromPlayer = newPos.subtract(eye);
        double distFromPlayer = fromPlayer.length();
        if (distFromPlayer < BLUE_CROUCH_MIN_DISTANCE) {
            newPos = eye.add(fromPlayer.normalize().scale(BLUE_CROUCH_MIN_DISTANCE));
        } else if (distFromPlayer > BLUE_CROUCH_MAX_DISTANCE) {
            newPos = eye.add(fromPlayer.normalize().scale(BLUE_CROUCH_MAX_DISTANCE));
        }

        blueEntity.teleportTo(newPos.x, newPos.y, newPos.z);
        blueEntity.setDeltaMovement(Vec3.ZERO);
        blueEntity.getPersistentData().putDouble("x_pos", newPos.x);
        blueEntity.getPersistentData().putDouble("y_pos", newPos.y);
        blueEntity.getPersistentData().putDouble("z_pos", newPos.z);
        blueEntity.getPersistentData().putDouble("x_power", look.x * 0.2);
        blueEntity.getPersistentData().putDouble("y_power", look.y * 0.2);
        blueEntity.getPersistentData().putDouble("z_power", look.z * 0.2);
        blueEntity.setYRot(playerOwner.getYRot());
        blueEntity.setXRot(playerOwner.getXRot());

        blueEntity.addEffect(new MobEffectInstance(MobEffects.LUCK, 5, 15, false, false));

        pullMobsWithCrouchFullChargeBlue(serverLevel, blueEntity, owner, newPos, look);
        destroyBlocksNearCrouchBlue(serverLevel, blueEntity, owner, newPos, look);
    }

    private static void pullMobsWithCrouchFullChargeBlue(ServerLevel serverLevel, LivingEntity blueEntity,
                                                         LivingEntity owner, Vec3 orbPos, Vec3 lookDir) {
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new AABB(orbPos, orbPos).inflate(BLUE_FULL_PULL_RADIUS),
            e -> e.isAlive() && e != blueEntity && e != owner
                && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));

        for (LivingEntity entity : nearby) {
            Vec3 toOrb = orbPos.subtract(entity.position());
            double d = toOrb.length();
            if (d > BLUE_FULL_PULL_RADIUS || d < 0.001) continue;

            double strength = BLUE_FULL_PULL_STRENGTH * (1.0 + (1.0 - d / BLUE_FULL_PULL_RADIUS) * 2.0);
            Vec3 pull = toOrb.normalize().scale(strength);
            Vec3 awayFromLook = lookDir.scale(0.6);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2).add(pull).add(awayFromLook));
            entity.hurtMarked = true;

            if (d < 2.0) {
                entity.setNoGravity(true);
                entity.teleportTo(orbPos.x - lookDir.x * 0.8, orbPos.y, orbPos.z - lookDir.z * 0.8);
            }
        }
    }

    private static void destroyBlocksNearCrouchBlue(ServerLevel serverLevel, LivingEntity blueEntity,
                                                   LivingEntity owner, Vec3 orbPos, Vec3 lookDir) {
        int bx = Mth.floor(orbPos.x);
        int by = Mth.floor(orbPos.y);
        int bz = Mth.floor(orbPos.z);

        for (int dx = -BLUE_FULL_BLOCK_RANGE; dx <= BLUE_FULL_BLOCK_RANGE; dx++) {
            for (int dy = -BLUE_FULL_BLOCK_RANGE; dy <= BLUE_FULL_BLOCK_RANGE; dy++) {
                for (int dz = -BLUE_FULL_BLOCK_RANGE; dz <= BLUE_FULL_BLOCK_RANGE; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > BLUE_FULL_BLOCK_RANGE) continue;

                    BlockPos bpos = new BlockPos(bx + dx, by + dy, bz + dz);
                    net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(bpos);
                    if (state.isAir() || state.isSolid()) continue;

                    if (isEssentialBlock(serverLevel, bpos)) continue;

                    float hardness = state.getDestroySpeed(serverLevel, bpos);
                    if (hardness < 0 || hardness > 10.0f) continue;

                    serverLevel.destroyBlock(bpos, false, blueEntity);

                    serverLevel.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        bpos.getX() + 0.5, bpos.getY() + 0.5, bpos.getZ() + 0.5,
                        4, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    private static boolean isEssentialBlock(ServerLevel serverLevel, BlockPos bpos) {
        net.minecraft.world.level.block.Block block = serverLevel.getBlockState(bpos).getBlock();
        if (block == net.minecraft.world.level.block.Blocks.BEDROCK) return true;
        if (block == net.minecraft.world.level.block.Blocks.BARRIER) return true;
        if (block == net.minecraft.world.level.block.Blocks.SPAWNER) return true;
        if (block == net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME) return true;
        return false;
    }

    private static int getRedChargeTier(double cnt6) {
        if (cnt6 >= 5.0) return 3;
        if (cnt6 >= 3.0) return 2;
        return 1;
    }

    private static double getNormalRedSpeed(double cnt6) {
        return switch (getRedChargeTier(cnt6)) { case 1 -> 2.3; case 2 -> 3.2; default -> 4.3; };
    }
}
