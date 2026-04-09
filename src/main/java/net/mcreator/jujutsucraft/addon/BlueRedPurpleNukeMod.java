package net.mcreator.jujutsucraft.addon;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.CooldownTrackerEvents;
import net.mcreator.jujutsucraft.addon.DomainMasteryCommands;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.entity.PurpleEntity;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModAttributes;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.init.JujutsucraftModParticleTypes;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.AIRedProcedure;
import net.mcreator.jujutsucraft.procedures.BlockDestroyAllDirectionProcedure;
import net.mcreator.jujutsucraft.procedures.KnockbackProcedure;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.mcreator.jujutsucraft.procedures.SetRangedAmmoProcedure;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

@Mod(value="jjkblueredpurple")
/**
 * Primary addon mod entry point that wires Forge events, networking, and the custom Gojo combat extensions. This class owns the Red orb rewrite, Blue aim and linger behavior, Purple fusion checks, Infinity Crusher control, and Black Flash charge helpers.
 */
// ===== MOD BOOTSTRAP =====
public class BlueRedPurpleNukeMod {
    // Maximum Blue linger lifetime in ticks before the stationary orb expires naturally.
    private static final int LINGER_DURATION = 200;
    // Radius used to detect valid Red and Blue overlap for Hollow Purple fusion.
    private static final double NUKE_COLLISION_RADIUS = 4.0;
    // Maximum search range for the shift-cast Red teleport-behind target selection.
    private static final double TELEPORT_RANGE = 32.0;
    // Forward aim cone, in degrees, used when evaluating shift-cast teleport candidates.
    private static final double AIM_CONE_ANGLE = 30.0;
    // Distance placed behind a valid shift-cast target before triggering the Red explosion.
    private static final double BEHIND_DISTANCE = 2.5;
    // Minimum cursed energy required before the addon allows Purple fusion to activate.
    private static final double NUKE_CE_THRESHOLD = 2000.0;
    // Cursed energy consumed when Hollow Purple fusion succeeds.
    private static final double NUKE_CE_COST = 2000.0;
    // Maximum owner health ratio that still allows Purple fusion, keeping the move as a risky finisher.
    private static final double NUKE_HP_THRESHOLD = 0.3;
    // Baseline pull radius used when the custom Red orb captures and drags nearby targets.
    private static final double RED_CAPTURE_RADIUS = 5.0;
    // Maximum travel range of a fully charged custom Red orb.
    private static final double RED_MAX_RANGE_FULL = 128.0;
    // Minimum pre-launch setup ticks before Red chooses its final cast mode.
    private static final int RED_PREINIT_TICKS = 5;
    // Particle tint used for the energized Red orb trail visuals.
    private static final Vector3f RED_TRAIL_COLOR = new Vector3f(0.95f, 0.15f, 0.1f);
    // Particle tint used for Blue-style support visuals around addon orb effects.
    private static final Vector3f BLUE_AURA_COLOR = new Vector3f(0.1f, 0.3f, 0.95f);
    // Forward offset used to anchor the charging Red orb in front of its owner.
    private static final double RED_CHARGE_ANCHOR_DISTANCE = 2.2;
    // Vertical offset applied to the Red charge anchor so the orb sits slightly below eye level.
    private static final double RED_CHARGE_ANCHOR_Y_OFFSET = -0.1;
    // Minimum retarget distance for the crouch-based Blue aim mode.
    private static final double BLUE_CROUCH_MIN_DISTANCE = 10.0;
    // Maximum retarget distance for the crouch-based Blue aim mode.
    private static final double BLUE_CROUCH_MAX_DISTANCE = 20.0;
    // Movement speed applied while Blue smoothly glides toward its aimed position.
    private static final double BLUE_AIM_ORB_SPEED = 1.5;
    // Maximum number of ticks that Blue can remain in explicit aim mode.
    private static final int BLUE_AIM_DURATION = 90;
    // Starting radius of the Infinity Crusher control cone.
    private static final double CRUSHER_MIN_RADIUS = 1.0;
    // Maximum radius the Infinity Crusher area can grow to over time.
    private static final double CRUSHER_MAX_RADIUS = 3.0;
    // Base wall-impact damage dealt when Crusher forces a target into terrain.
    private static final float CRUSHER_BASE_WALL_DAMAGE = 3.0f;
    // Extra wall-impact damage added per point of cursed energy drained by Crusher.
    private static final double CRUSHER_CE_DAMAGE_SCALE = 0.012;
    // Baseline directional push strength used by Infinity Crusher.
    private static final double CRUSHER_PUSH_STRENGTH = 0.5;
    // Cosine threshold that defines how narrow the Crusher forward cone is.
    private static final double CRUSHER_CONE_COS = 0.5;
    // Initial cursed energy drained per tick while Infinity Crusher is sustained.
    private static final double CRUSHER_BASE_CE_DRAIN = 0.5;
    // Additional cursed energy drain added each active Crusher tick.
    private static final double CRUSHER_CE_DRAIN_GROWTH = 0.02;
    // Number of sustained ticks required before Crusher hard-locks a target.
    private static final int CRUSHER_HARDLOCK_THRESHOLD = 15;
    // Pull radius used by the crouch full-charge Blue variant.
    private static final double BLUE_FULL_PULL_RADIUS = 6.0;
    // Pull strength used by the crouch full-charge Blue variant.
    private static final double BLUE_FULL_PULL_STRENGTH = 1.2;
    // Block destruction radius, in blocks, for the crouch full-charge Blue variant.
    private static final int BLUE_FULL_BLOCK_RANGE = 4;
    // Environmental destruction power marker passed into the block-breaking helper.
    private static final float BLUE_FULL_BLOCK_POWER = 4.0f;
    // Slight edge so addon normal Red stays a bit stronger than crouch Red at the same rank.
    private static final double NORMAL_RED_DAMAGE_ADVANTAGE = 1.08;

    /**
     * Initializes the addon mod, registers custom entities, subscribes Forge event listeners, and prepares the networking channel.
     */
    public BlueRedPurpleNukeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LimbEntityRegistry.ENTITIES.register(modBus);
        MinecraftForge.EVENT_BUS.register((Object)this);
        ModNetworking.register();
    }

    @SubscribeEvent
    // ===== FORGE EVENT REGISTRATION =====
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DomainMasteryCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }

    @SubscribeEvent
    /**
     * Handles the player logout callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            BlueRedPurpleNukeMod.resetInfinityCrusher(player2);
        }
    }

    @SubscribeEvent
    // ===== ENTITY TICK HANDLERS =====
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        Level level;
        String orbId;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        // Blue entities are fully server-driven here so aim, linger, and fusion state stay authoritative.
        if (entity instanceof BlueEntity) {
            this.handleBlueTick(entity);
            return;
        }
        if (entity.tickCount % 40 == 0 && entity.getPersistentData().contains("addon_red_attached_orb") && !(orbId = entity.getPersistentData().getString("addon_red_attached_orb")).isEmpty() && (level = entity.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            try {
                Entity orb = sl.getEntity(UUID.fromString(orbId));
                if (orb == null || !orb.isAlive()) {
                    entity.getPersistentData().remove("addon_red_attached_orb");
                    entity.setNoGravity(false);
                }
            }
            catch (Exception e) {
                entity.getPersistentData().remove("addon_red_attached_orb");
                entity.setNoGravity(false);
            }
        }
    }

    @SubscribeEvent
    // ===== PLAYER TICK HANDLERS =====
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        BlueRedPurpleNukeMod.handleInfinityCrusher(player2);
        BlueRedPurpleNukeMod.handleBlackFlashCharge(player2);
        BlueRedPurpleNukeMod.handleDomainBFBoostCleanup(player2);
    }

    // ===== BLACK FLASH SUPPORT =====
    private static void handleDomainBFBoostCleanup(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        if (!nbt.contains("jjkbrp_bf_cnt6_boost") && !nbt.contains("jjkbrp_domain_bf_bonus")) {
            return;
        }
        // Domain-only Black Flash boosts are cleared as soon as the owner leaves Domain Expansion so the bonus cannot leak into normal combat.
        if (player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return;
        }
        DomainAddonUtils.cleanupBFBoost((LivingEntity)player);
    }

    /**
     * Maintains the player's Black Flash charge flags and announcement state while melee charge is being built.
     * @param player player instance involved in this operation.
     */
    private static void handleBlackFlashCharge(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        boolean hasMastery = data.getBoolean("addon_bf_mastery");
        if (!hasMastery && CooldownTrackerEvents.hasAdvancement(player, "jjkblueredpurple:black_flash_mastery")) {
            data.putBoolean("addon_bf_mastery", true);
            hasMastery = true;
        }
        if (!hasMastery) {
            return;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int selectId = (int)Math.round(vars.PlayerSelectCurseTechnique);
        if (selectId > 2) {
            if (data.getBoolean("addon_bf_charging")) {
                data.putBoolean("addon_bf_charging", false);
                data.putBoolean("addon_bf_guaranteed", false);
            }
            return;
        }
        double cnt6 = data.getDouble("cnt6");
        double skillTag = data.getDouble("skill");
        // The addon treats a nearly full melee charge as a Black Flash-ready window and upgrades it to guaranteed once the charge is high enough.
        if (cnt6 >= 4.5 && skillTag != 0.0) {
            if (!data.getBoolean("addon_bf_guaranteed")) {
                data.putBoolean("addon_bf_guaranteed", true);
                data.putBoolean("addon_bf_charging", true);
            }
        } else if (cnt6 > 0.0 && skillTag != 0.0) {
            data.putBoolean("addon_bf_charging", true);
            data.putBoolean("addon_bf_charge_announced", false);
        } else if (cnt6 <= 0.0 && skillTag == 0.0) {
            data.putBoolean("addon_bf_charging", false);
            data.putBoolean("addon_bf_charge_announced", false);
        }
        // The final announce gate only fires once per charge cycle to avoid flooding the action bar while the user holds the input.
        if (cnt6 >= 5.0 && skillTag != 0.0 && !data.getBoolean("addon_bf_charge_announced")) {
            data.putBoolean("addon_bf_charge_announced", true);
            player.displayClientMessage((Component)Component.literal((String)"\u00a7l\"Black Flash\""), false);
        }
    }

    // ===== RED ORB SYSTEM =====
    public static boolean shouldOverrideBaseRed(LivingEntity redEntity) {
        if (!(redEntity instanceof RedEntity)) {
            return false;
        }
        RedEntity re = (RedEntity)redEntity;
        if (redEntity.level().isClientSide()) {
            return false;
        }
        try {
            if (((Boolean)re.getEntityData().get(RedEntity.DATA_flag_purple)).booleanValue()) {
                return false;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (redEntity.getPersistentData().getBoolean("flag_purple")) {
            return false;
        }
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) {
            return false;
        }
        Level level = redEntity.level();
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        LivingEntity owner = BlueRedPurpleNukeMod.resolveOwner(serverLevel, ownerUUID);
        return owner instanceof Player;
    }

    /**
     * Handles red from mixin for the addon system.
     * @param redEntity entity instance being processed by this helper.
     */
    public static void handleRedFromMixin(LivingEntity redEntity) {
        BlueRedPurpleNukeMod.handleRedTick(redEntity);
    }

    /**
     * Runs the custom server-side Red orb state machine, including startup, cast mode selection, flight, and Purple fusion checks.
     * @param redEntity entity instance being processed by this helper.
     */
    private static void handleRedTick(LivingEntity redEntity) {
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) {
            return;
        }
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        LivingEntity owner = BlueRedPurpleNukeMod.resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player)) {
            return;
        }
        double liveCnt6 = redEntity.getPersistentData().getDouble("cnt6");
        double ownerCnt6 = owner.getPersistentData().getDouble("cnt6");
        double cachedCnt6 = Math.max(Math.max(liveCnt6, ownerCnt6), redEntity.getPersistentData().getDouble("addon_red_charge_cached"));
        redEntity.getPersistentData().putDouble("addon_red_charge_cached", cachedCnt6);
        boolean addonInitDone = redEntity.getPersistentData().getBoolean("addon_red_init_done");
        if (!addonInitDone) {
            boolean shouldLaunch;
            double ownerSkill;
            redEntity.getPersistentData().putDouble("cnt1", 0.0);
            redEntity.setHealth(redEntity.getMaxHealth());
            int preInitTicks = redEntity.getPersistentData().getInt("addon_red_preinit_ticks") + 1;
            redEntity.getPersistentData().putInt("addon_red_preinit_ticks", preInitTicks);
            boolean flagStart = redEntity.getPersistentData().getBoolean("flag_start");
            if (!flagStart && (ownerSkill = owner.getPersistentData().getDouble("skill")) == 0.0) {
                flagStart = true;
                redEntity.getPersistentData().putBoolean("flag_start", true);
            }
            BlueRedPurpleNukeMod.updateChargingRedOrbVisual(redEntity, owner, cachedCnt6);
            // Wait a short startup window for normal launches, but also force resolution after a long stall so the orb never hangs forever.
            boolean bl = shouldLaunch = flagStart && preInitTicks >= 5 || preInitTicks >= 200;
            if (!shouldLaunch) {
                return;
            }
            redEntity.getPersistentData().putBoolean("addon_red_init_done", true);
            double effectiveCnt6 = Math.max(cachedCnt6, ownerCnt6);
            redEntity.getPersistentData().putDouble("addon_red_charge_used", effectiveCnt6);
            boolean isCrouching = owner.isCrouching();
            // Crouching reroutes Red into the original low-charge behavior, but a full crouch charge upgrades into the teleport-behind explosion variant.
            if (isCrouching && effectiveCnt6 < 5.0) {
                redEntity.getPersistentData().putBoolean("addon_red_use_og", true);
            } else if (isCrouching && effectiveCnt6 >= 5.0) {
                redEntity.getPersistentData().putBoolean("addon_red_shift_cast", true);
                BlueRedPurpleNukeMod.handleRedTeleportBehind(redEntity);
            } else {
                BlueRedPurpleNukeMod.initializeNormalRedOrb(redEntity, owner, effectiveCnt6);
            }
        }
        boolean useOg = redEntity.getPersistentData().getBoolean("addon_red_use_og");
        boolean shiftCast = redEntity.getPersistentData().getBoolean("addon_red_shift_cast");
        boolean normalActive = redEntity.getPersistentData().getBoolean("addon_red_normal_active");
        if (useOg) {
            AIRedProcedure.execute((LevelAccessor)serverLevel, (Entity)redEntity);
            if (BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity) >= 5.0) {
                BlueRedPurpleNukeMod.checkAndActivatePurpleNuke(redEntity, owner, serverLevel);
            }
            return;
        }
        if (shiftCast) {
            if (BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity) >= 5.0 && BlueRedPurpleNukeMod.checkAndActivatePurpleNuke(redEntity, owner, serverLevel)) {
                return;
            }
            if (!redEntity.getPersistentData().getBoolean("addon_red_shift_exploded")) {
                redEntity.getPersistentData().putBoolean("addon_red_shift_exploded", true);
                BlueRedPurpleNukeMod.handleShiftCastExplosion(redEntity, owner, serverLevel);
            }
            return;
        }
        if (normalActive) {
            if (BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity) >= 5.0 && BlueRedPurpleNukeMod.checkAndActivatePurpleNuke(redEntity, owner, serverLevel)) {
                return;
            }
            BlueRedPurpleNukeMod.tickNormalRedOrb(redEntity, owner);
        }
    }

    /**
     * Configures the addon normal-flight Red orb using the resolved charge, direction, range, and movement data.
     * @param redEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param effectiveCnt6 effective cnt 6 used by this method.
     */
    private static void initializeNormalRedOrb(LivingEntity redEntity, LivingEntity owner, double effectiveCnt6) {
        Level level;
        Vec3 direction = owner.getLookAngle().normalize();
        if (direction.y < -0.12) {
            direction = new Vec3(direction.x, -0.12, direction.z).normalize();
        }
        Vec3 startPos = redEntity.getPersistentData().getBoolean("addon_red_charge_anchor_valid") ? new Vec3(redEntity.getPersistentData().getDouble("addon_red_charge_anchor_x"), redEntity.getPersistentData().getDouble("addon_red_charge_anchor_y"), redEntity.getPersistentData().getDouble("addon_red_charge_anchor_z")) : owner.getEyePosition(1.0f).add(direction.scale(2.2)).add(0.0, -0.1, 0.0);
        double cnt6 = Math.max(effectiveCnt6, 0.0);
        // Normalize Red charge into a 0..1 ratio so range and other scaling values can be derived consistently.
        double chargeRatio = Math.max(0.0, Math.min(cnt6, 5.0) / 5.0);
        // Even weak casts travel a meaningful distance, while full charge reaches the addon maximum range cap.
        double maxRange = 128.0 * (0.22 + 0.78 * chargeRatio);
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        double speed = BlueRedPurpleNukeMod.getNormalRedSpeed(cnt6);
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
        redEntity.getPersistentData().putDouble("addon_red_charge_tier", (double)chargeTier);
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
        if (redEntity.getAttributes().hasAttribute((Attribute)JujutsucraftModAttributes.SIZE.get())) {
            redEntity.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()).setBaseValue(2.0 + (double)chargeTier * 1.25);
        }
        if ((level = redEntity.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            sl.playSound(null, BlockPos.containing((Position)startPos), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 2.0f, 0.5f);
            SoundEvent electric = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:electric_shock"));
            if (electric != null) {
                sl.playSound(null, BlockPos.containing((Position)startPos), electric, SoundSource.NEUTRAL, 1.5f, 1.0f);
            }
        }
    }

    /**
     * Advances the addon Red orb one server tick, including movement, collision, capture, visuals, and detonation.
     * @param redEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @return true when tick normal red orb succeeds; otherwise false.
     */
    private static boolean tickNormalRedOrb(LivingEntity redEntity, LivingEntity owner) {
        BlockHitResult blockHit;
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        Vec3 current = BlueRedPurpleNukeMod.getTrackedRedPosition(redEntity);
        Vec3 rawDir = new Vec3(redEntity.getPersistentData().getDouble("addon_red_dir_x"), redEntity.getPersistentData().getDouble("addon_red_dir_y"), redEntity.getPersistentData().getDouble("addon_red_dir_z"));
        Vec3 direction = rawDir.lengthSqr() < 1.0E-6 ? redEntity.getLookAngle().normalize() : rawDir.normalize();
        double tickCount = redEntity.getPersistentData().getDouble("addon_red_ticks") + 1.0;
        redEntity.getPersistentData().putDouble("addon_red_ticks", tickCount);
        redEntity.getPersistentData().putDouble("cnt1", 0.0);
        redEntity.setHealth(redEntity.getMaxHealth());
        double speed = redEntity.getPersistentData().getDouble("addon_red_speed");
        if (speed <= 0.0) {
            speed = BlueRedPurpleNukeMod.getNormalRedSpeed(BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity));
        }
        Vec3 next = current.add(direction.scale(speed));
        if (tickCount > 2.0 && (blockHit = world.clip(new ClipContext(current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)redEntity))) != null && blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitPos = blockHit.getLocation();
            BlueRedPurpleNukeMod.updateOrbPosition(redEntity, hitPos);
            BlueRedPurpleNukeMod.explodeNormalRedOrb(redEntity, owner);
            return true;
        }
        BlueRedPurpleNukeMod.updateOrbPosition(redEntity, next);
        redEntity.setDeltaMovement(direction.scale(Math.max(0.1, speed * 0.12)));
        redEntity.setNoGravity(true);
        redEntity.setInvisible(false);
        BlueRedPurpleNukeMod.pullMobsWithRedOrb(serverLevel, redEntity, owner, next, direction);
        BlueRedPurpleNukeMod.spawnBlueLikeOrbVisuals(serverLevel, redEntity, next, (int)tickCount);
        int chargeTier = (int)redEntity.getPersistentData().getDouble("addon_red_charge_tier");
        double traveled = redEntity.getPersistentData().getDouble("addon_red_travel") + speed;
        redEntity.getPersistentData().putDouble("addon_red_travel", traveled);
        double maxRange = redEntity.getPersistentData().getDouble("addon_red_max_range");
        if (redEntity.getAttributes().hasAttribute((Attribute)JujutsucraftModAttributes.SIZE.get())) {
            double progress = maxRange > 0.0 ? Math.min(1.0, traveled / maxRange) : 0.0;
            double chargeRatio = Math.max(0.2, Math.min(1.0, BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity) / 5.0));
            double baseScale = 2.6 + (double)chargeTier * 1.2;
            double growth = progress * (2.0 + (double)chargeTier * 1.5) * chargeRatio;
            double pulse = Math.sin(((double)serverLevel.getGameTime() + tickCount) * 0.6) * (0.18 + (double)chargeTier * 0.08);
            double targetScale = Math.max(2.0, baseScale + growth + pulse);
            redEntity.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()).setBaseValue(targetScale);
        }
        if (traveled >= maxRange) {
            BlueRedPurpleNukeMod.explodeNormalRedOrb(redEntity, owner);
            return true;
        }
        return false;
    }

    // ===== RED ORB VISUALS AND CONTROL =====
    private static void spawnBlueLikeOrbVisuals(ServerLevel serverLevel, LivingEntity redEntity, Vec3 orbPos, int tickCount) {
        int chargeTier = (int)redEntity.getPersistentData().getDouble("addon_red_charge_tier");
        double range = 1.0 + (double)chargeTier * 0.5;
        if (tickCount % 2 == 0) {
            for (int i = 0; i < 4 + chargeTier * 2; ++i) {
                double oz;
                double sz;
                double vz;
                double oy;
                double sy;
                double vy;
                double ox = (Math.random() - 0.5) * range * 10.0;
                double sx = orbPos.x + ox;
                double vx = orbPos.x - sx;
                double dis = Math.sqrt(vx * vx + (vy = orbPos.y - (sy = orbPos.y + (oy = (Math.random() - 0.5) * range * 10.0))) * vy + (vz = orbPos.z - (sz = orbPos.z + (oz = (Math.random() - 0.5) * range * 10.0))) * vz);
                if (dis < 0.001) continue;
                serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(sx, sy, sz), Vec2.ZERO, serverLevel, 4, "", (Component)Component.literal((String)""), serverLevel.getServer(), null).withSuppressedOutput(), String.format(Locale.ROOT, "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.0025 0 force", (vx /= dis) * 10000.0, (vy /= dis) * 10000.0, (vz /= dis) * 10000.0));
            }
        }
        double orbRadius = 0.4 + (double)chargeTier * 0.25;
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 0.6f + (float)chargeTier * 0.2f), orbPos.x, orbPos.y, orbPos.z, 4 + chargeTier * 2, orbRadius, orbRadius, orbRadius, 0.0);
        serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_RED.get()), orbPos.x, orbPos.y, orbPos.z, 3 + chargeTier, orbRadius * 0.6, orbRadius * 0.6, orbRadius * 0.6, 0.01);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, orbPos.x, orbPos.y, orbPos.z, 1 + chargeTier, 0.15, 0.15, 0.15, 0.02);
        if (tickCount % 2 == 0) {
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.CLOUD, orbPos.x, orbPos.y, orbPos.z, 2, orbRadius * 0.4, orbRadius * 0.4, orbRadius * 0.4, 0.01);
        }
        if (chargeTier >= 2) {
            serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_CURSE_POWER_RED.get()), orbPos.x, orbPos.y, orbPos.z, 1 + chargeTier, 0.2, 0.2, 0.2, 0.01);
        }
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, orbPos.x, orbPos.y, orbPos.z, 2 + chargeTier, orbRadius * 0.7, orbRadius * 0.7, orbRadius * 0.7, 0.02);
    }

    /**
     * Performs pull mobs with red orb for this addon component.
     * @param serverLevel level value used by this operation.
     * @param redEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param direction direction used by this method.
     */
    private static void pullMobsWithRedOrb(ServerLevel serverLevel, LivingEntity redEntity, LivingEntity owner, Vec3 orbPos, Vec3 direction) {
        double cnt6 = BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity);
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        Vec3 normDir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : Vec3.ZERO;
        String orbId = redEntity.getUUID().toString();
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(orbPos, orbPos).inflate(20.0), e -> e.isAlive() && e != redEntity && e != owner && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        int capturedIdx = 0;
        for (LivingEntity entity : nearby) {
            boolean alreadyCaptured = orbId.equals(entity.getPersistentData().getString("addon_red_attached_orb"));
            double dist = entity.position().distanceTo(orbPos);
            if (alreadyCaptured) {
                Vec3 trailPos = orbPos.subtract(normDir.scale(1.2 + (double)capturedIdx * 0.7));
                entity.teleportTo(trailPos.x, trailPos.y, trailPos.z);
                entity.setDeltaMovement(normDir.scale(0.3));
                entity.setNoGravity(true);
                entity.hurtMarked = true;
                if (++capturedIdx % 3 != 0) continue;
                serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 0.5f), entity.getX(), entity.getY() + (double)entity.getBbHeight() * 0.5, entity.getZ(), 2, 0.15, 0.15, 0.15, 0.0);
                continue;
            }
            if (dist > 5.0) continue;
            Vec3 toOrb = orbPos.subtract(entity.position());
            double pullStrength = 2.5 + (double)chargeTier * 0.8;
            double closeFactor = 1.0 + (1.0 - Math.min(1.0, dist / 5.0)) * 2.0;
            Vec3 pull = toOrb.normalize().scale(pullStrength * closeFactor);
            Vec3 forward = normDir.scale(1.2 + (double)chargeTier * 0.4);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.15).add(pull).add(forward));
            entity.hurtMarked = true;
            if (!(dist < 3.5)) continue;
            entity.getPersistentData().putString("addon_red_attached_orb", orbId);
            entity.setNoGravity(true);
            Vec3 snapPos = orbPos.subtract(normDir.scale(1.2));
            entity.teleportTo(snapPos.x, snapPos.y, snapPos.z);
        }
    }

    // ===== RED ORB EXPLOSION LOGIC =====
    private static void explodeNormalRedOrb(LivingEntity redEntity, LivingEntity owner) {
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel)) {
            redEntity.discard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        Vec3 pos = BlueRedPurpleNukeMod.getTrackedRedPosition(redEntity);
        double cnt6 = BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity);
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 14 + chargeTier * 8, 2.6 + (double)chargeTier * 1.2, 2.6 + (double)chargeTier * 1.2, 2.6 + (double)chargeTier * 1.2, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLASH, pos.x, pos.y, pos.z, 3, 0.12, 0.12, 0.12, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 2, 0.2, 0.15, 0.2, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLAME, pos.x, pos.y, pos.z, 190 + chargeTier * 110, 3.4 + (double)chargeTier * 1.5, 3.4 + (double)chargeTier * 1.5, 3.4 + (double)chargeTier * 1.5, 0.14);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.LAVA, pos.x, pos.y, pos.z, 80 + chargeTier * 36, 2.8 + (double)chargeTier, 2.8 + (double)chargeTier, 2.8 + (double)chargeTier, 0.0);
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 1.1f + (float)chargeTier * 0.45f), pos.x, pos.y, pos.z, 260 + chargeTier * 130, 4.2 + (double)chargeTier * 1.6, 2.8 + (double)chargeTier * 1.1, 4.2 + (double)chargeTier * 1.6, 0.0);
        serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_RED.get()), pos.x, pos.y, pos.z, 320 + chargeTier * 160, 4.5 + (double)chargeTier * 1.2, 3.0 + (double)chargeTier, 4.5 + (double)chargeTier * 1.2, 0.04);
        serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_CURSE_POWER_RED.get()), pos.x, pos.y, pos.z, 230 + chargeTier * 120, 3.8 + (double)chargeTier * 1.1, 2.6 + (double)chargeTier * 0.8, 3.8 + (double)chargeTier * 1.1, 0.03);
        serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLOOD_RED.get()), pos.x, pos.y, pos.z, 145 + chargeTier * 70, 3.0 + (double)chargeTier, 2.2 + (double)chargeTier * 0.8, 3.0 + (double)chargeTier, 0.02);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.x, pos.y, pos.z, 95 + chargeTier * 50, 3.2 + (double)chargeTier * 1.2, 2.2 + (double)chargeTier * 0.8, 3.2 + (double)chargeTier * 1.2, 0.06);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 86 + chargeTier * 40, 3.9 + (double)chargeTier * 1.2, 2.6 + (double)chargeTier * 0.9, 3.9 + (double)chargeTier * 1.2, 0.24);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 40 + chargeTier * 18, 2.4 + (double)chargeTier * 0.8, 1.4 + (double)chargeTier * 0.5, 2.4 + (double)chargeTier * 0.8, 0.12);
        for (int i = 0; i < 10 + chargeTier * 5; ++i) {
            double oz;
            double vz;
            double oy;
            double vy;
            double ox = (Math.random() - 0.5) * 16.0;
            double vx = -ox;
            double dis = Math.sqrt(vx * vx + (vy = -(oy = (Math.random() - 0.5) * 16.0)) * vy + (vz = -(oz = (Math.random() - 0.5) * 16.0)) * vz);
            if (!(dis > 0.001)) continue;
            serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(pos.x + ox, pos.y + oy, pos.z + oz), Vec2.ZERO, serverLevel, 4, "", (Component)Component.literal((String)""), serverLevel.getServer(), null).withSuppressedOutput(), String.format(Locale.ROOT, "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.0025 0 force", vx / dis * 10000.0, vy / dis * 10000.0, vz / dis * 10000.0));
        }
        world.playSound(null, BlockPos.containing((Position)pos), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 3.2f, 0.85f);
        world.playSound(null, BlockPos.containing((Position)pos), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.NEUTRAL, 2.6f, 0.68f);
        world.playSound(null, BlockPos.containing((Position)pos), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 2.0f, 0.62f);
        world.playSound(null, BlockPos.containing((Position)pos), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2.2f, 1.2f);
        double normalDamageScale = BlueRedPurpleNukeMod.getNormalRedDamageScale(owner);
        String orbId = redEntity.getUUID().toString();
        List<LivingEntity> attachedMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(30.0), e -> e.isAlive() && orbId.equals(e.getPersistentData().getString("addon_red_attached_orb")));
        for (LivingEntity mob : attachedMobs) {
            mob.getPersistentData().remove("addon_red_attached_orb");
            mob.setNoGravity(false);
            float damage = switch (chargeTier) {
                case 1 -> 18.0f;
                case 2 -> 34.0f;
                default -> 56.0f;
            };
            damage = (float)((double)damage * normalDamageScale);
            mob.hurt(serverLevel.damageSources().explosion((Entity)redEntity, (Entity)owner), damage);
            mob.setDeltaMovement(mob.position().subtract(pos).normalize().scale(1.2 + (double)chargeTier * 0.7));
        }
        BlueRedPurpleNukeMod.applyRedExplosionShockwave(serverLevel, redEntity, owner, pos, chargeTier);
        float blockPower = switch (chargeTier) {
            case 1 -> 4.0f;
            case 2 -> 5.6f;
            default -> 7.2f;
        };
        boolean ownerHadInfinity = owner instanceof Player && owner.getPersistentData().getBoolean("infinity");
        boolean wasInvulnerable = owner.isInvulnerable();
        if (ownerHadInfinity && !wasInvulnerable) {
            owner.setInvulnerable(true);
        }
        world.explode((Entity)owner, pos.x, pos.y, pos.z, blockPower, Level.ExplosionInteraction.MOB);
        if (ownerHadInfinity && !wasInvulnerable) {
            owner.setInvulnerable(false);
        }
        redEntity.getPersistentData().putBoolean("addon_red_normal_active", false);
        redEntity.discard();
    }

    // ===== SHIFT-CAST RED VARIANT =====
    private static void handleShiftCastExplosion(LivingEntity redEntity, LivingEntity owner, ServerLevel serverLevel) {
        Vec3 pos = BlueRedPurpleNukeMod.getTrackedRedPosition(redEntity);
        double cnt6 = BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity);
        double CNT6 = 1.0 + cnt6 * 0.1;
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        redEntity.getPersistentData().putDouble("cnt6", cnt6);
        redEntity.getPersistentData().putDouble("x_pos", pos.x);
        redEntity.getPersistentData().putDouble("y_pos", pos.y);
        redEntity.getPersistentData().putDouble("z_pos", pos.z);
        for (int step = 0; step < 2; ++step) {
            redEntity.getPersistentData().putDouble("Damage", 34.0 * CNT6);
            redEntity.getPersistentData().putDouble("Range", 10.0 * CNT6);
            redEntity.getPersistentData().putDouble("knockback", 2.0 * CNT6);
            redEntity.getPersistentData().putDouble("effect", 0.0);
            RangeAttackProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
            redEntity.getPersistentData().putDouble("BlockRange", 4.0 * CNT6);
            redEntity.getPersistentData().putDouble("BlockDamage", 4.0 * CNT6);
            redEntity.getPersistentData().putBoolean("noParticle", true);
            BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
            redEntity.getPersistentData().putDouble("Range", 10.0 * CNT6);
            redEntity.getPersistentData().putDouble("knockback", 2.0 * CNT6);
            KnockbackProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
        }
        redEntity.getPersistentData().putDouble("BlockRange", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("BlockDamage", 0.33);
        redEntity.getPersistentData().putBoolean("noParticle", true);
        BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, (int)(30.0 * CNT6), 4.0, 4.0, 4.0, 1.0);
        redEntity.getPersistentData().putDouble("Damage", 34.0 * CNT6);
        redEntity.getPersistentData().putDouble("Range", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("knockback", 4.0 * CNT6);
        redEntity.getPersistentData().putDouble("effect", 0.0);
        redEntity.getPersistentData().putDouble("y_knockback", 0.65);
        RangeAttackProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)(pos.y - 1.5), (double)pos.z, (Entity)redEntity);
        redEntity.getPersistentData().putDouble("Range", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("knockback", 4.0 * CNT6);
        redEntity.getPersistentData().putDouble("effect", 1.0);
        KnockbackProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)(pos.y - 1.5), (double)pos.z, (Entity)redEntity);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 1.2f), pos.x, pos.y, pos.z, 100 + chargeTier * 50, 3.0 + (double)chargeTier, 2.0 + (double)chargeTier * 0.6, 3.0 + (double)chargeTier, 0.0);
        serverLevel.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_RED.get()), pos.x, pos.y, pos.z, 80 + chargeTier * 40, 2.5 + (double)chargeTier, 1.5 + (double)chargeTier * 0.5, 2.5 + (double)chargeTier, 0.02);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLAME, pos.x, pos.y, pos.z, 60 + chargeTier * 30, 2.0 + (double)chargeTier * 0.8, 1.5 + (double)chargeTier * 0.5, 2.0 + (double)chargeTier * 0.8, 0.08);
        serverLevel.playSound(null, BlockPos.containing((Position)pos), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.5f, 0.8f);
        SoundEvent electric = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:electric_shock"));
        if (electric != null) {
            serverLevel.playSound(null, BlockPos.containing((Position)pos), electric, SoundSource.NEUTRAL, 2.0f, 0.8f);
        }
        float blockPower = (float)(2.0 + CNT6 * 2.0);
        boolean ownerHadInfinity = owner instanceof Player && owner.getPersistentData().getBoolean("infinity");
        boolean wasInvulnerable = owner.isInvulnerable();
        if (ownerHadInfinity && !wasInvulnerable) {
            owner.setInvulnerable(true);
        }
        serverLevel.explode((Entity)owner, pos.x, pos.y, pos.z, blockPower, Level.ExplosionInteraction.MOB);
        if (ownerHadInfinity && !wasInvulnerable) {
            owner.setInvulnerable(false);
        }
        redEntity.discard();
    }

    /**
     * Handles red teleport behind for the addon system.
     * @param redEntity entity instance being processed by this helper.
     */
    private static void handleRedTeleportBehind(LivingEntity redEntity) {
        Entity ownerEntity;
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) {
            return;
        }
        Level world = redEntity.level();
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        try {
            ownerEntity = serverLevel.getEntity(UUID.fromString(ownerUUID));
        }
        catch (Exception e2) {
            return;
        }
        if (!(ownerEntity instanceof LivingEntity)) {
            return;
        }
        LivingEntity owner = (LivingEntity)ownerEntity;
        Vec3 eyePos = owner.getEyePosition(1.0f);
        Vec3 lookVec = owner.getLookAngle().normalize();
        double cosThreshold = Math.cos(Math.toRadians(30.0));
        List<Entity> candidates = world.getEntitiesOfClass(Entity.class, new AABB(owner.position(), owner.position()).inflate(32.0), e -> e instanceof LivingEntity && e != owner && e.isAlive() && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        LivingEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double score;
            double dot;
            Vec3 toEntity = candidate.position().add(0.0, (double)candidate.getBbHeight() * 0.5, 0.0).subtract(eyePos);
            double dist = toEntity.length();
            if (dist < 1.0 || dist > 32.0 || (dot = lookVec.dot(toEntity.normalize())) < cosThreshold || !((score = dist + (1.0 - dot) * 10.0) < bestScore)) continue;
            bestScore = score;
            target = (LivingEntity)candidate;
        }
        if (target == null) {
            return;
        }
        Vec3 targetPos = target.position();
        Vec3 dirToOwner = owner.position().subtract(targetPos).normalize();
        Vec3 behindPos = targetPos.subtract(dirToOwner.scale(2.5));
        behindPos = new Vec3(behindPos.x, targetPos.y, behindPos.z);
        double dx = targetPos.x - behindPos.x;
        double dz = targetPos.z - behindPos.z;
        float newYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        double dy = targetPos.y + (double)target.getBbHeight() * 0.5 - (behindPos.y + (double)owner.getEyeHeight());
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float newPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizDist)));
        if (owner instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)owner;
            serverPlayer.teleportTo(serverLevel, behindPos.x, behindPos.y, behindPos.z, newYaw, newPitch);
        } else {
            owner.teleportTo(behindPos.x, behindPos.y, behindPos.z);
            owner.setYRot(newYaw);
            owner.setXRot(newPitch);
        }
        Vec3 shootDir = targetPos.add(0.0, (double)target.getBbHeight() * 0.5, 0.0).subtract(behindPos.add(0.0, (double)owner.getEyeHeight(), 0.0)).normalize();
        redEntity.getPersistentData().putDouble("x_power", shootDir.x * 3.0);
        redEntity.getPersistentData().putDouble("y_power", shootDir.y * 3.0);
        redEntity.getPersistentData().putDouble("z_power", shootDir.z * 3.0);
        double redY = behindPos.y + (double)owner.getEyeHeight() * 0.75;
        redEntity.teleportTo(behindPos.x, redY, behindPos.z);
        redEntity.getPersistentData().putDouble("x_pos", behindPos.x);
        redEntity.getPersistentData().putDouble("y_pos", redY);
        redEntity.getPersistentData().putDouble("z_pos", behindPos.z);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, behindPos.x, behindPos.y + 1.0, behindPos.z, 30, 0.5, 1.0, 0.5, 0.1);
        world.playSound(null, BlockPos.containing((double)behindPos.x, (double)behindPos.y, (double)behindPos.z), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 1.2f);
    }

    // ===== RED CHARGE VISUALS =====
    private static void updateChargingRedOrbVisual(LivingEntity redEntity, LivingEntity owner, double cachedCnt6) {
        Level level = redEntity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        Vec3 rawLook = owner.getLookAngle();
        Vec3 look = rawLook.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : rawLook.normalize();
        Vec3 chargePos = owner.getEyePosition(1.0f).add(look.scale(2.2)).add(0.0, -0.1, 0.0);
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
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        double ratio = Math.max(0.2, Math.min(cnt6, 5.0) / 5.0);
        if (redEntity.getAttributes().hasAttribute((Attribute)JujutsucraftModAttributes.SIZE.get())) {
            redEntity.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()).setBaseValue(1.6 + ratio * 2.6);
        }
        redEntity.getPersistentData().putDouble("x_power", look.x * 0.25);
        redEntity.getPersistentData().putDouble("y_power", look.y * 0.25);
        redEntity.getPersistentData().putDouble("z_power", look.z * 0.25);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, chargePos.x, chargePos.y, chargePos.z, 1 + chargeTier, 0.08 + ratio * 0.2, 0.08 + ratio * 0.2, 0.08 + ratio * 0.2, 0.02);
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 0.5f + (float)ratio * 0.8f), chargePos.x, chargePos.y, chargePos.z, 3 + chargeTier, 0.12 + ratio * 0.18, 0.12 + ratio * 0.18, 0.12 + ratio * 0.18, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLAME, chargePos.x, chargePos.y, chargePos.z, 2 + chargeTier, 0.1 + ratio * 0.14, 0.1 + ratio * 0.14, 0.1 + ratio * 0.14, 0.0);
        Vec3 from = owner.getEyePosition(1.0f);
        for (int i = 0; i <= 5; ++i) {
            Vec3 p = from.lerp(chargePos, (double)i / 5.0);
            serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(RED_TRAIL_COLOR, 0.35f + (float)chargeTier * 0.1f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ===== RED SHOCKWAVE SUPPORT =====
    private static void applyRedExplosionShockwave(ServerLevel serverLevel, LivingEntity redEntity, LivingEntity owner, Vec3 pos, int chargeTier) {
        // Higher Red charge tiers widen the shockwave radius so fully committed casts punish larger groups.
        double radius = 4.0 + (double)chargeTier * 2.4;
        float baseDamage = switch (chargeTier) {
            case 1 -> 16.0f;
            case 2 -> 28.0f;
            default -> 42.0f;
        };
        double normalDamageScale = BlueRedPurpleNukeMod.getNormalRedDamageScale(owner);
        baseDamage = (float)((double)baseDamage * normalDamageScale);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius), e -> e.isAlive() && e != redEntity && e != owner && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        for (LivingEntity target : targets) {
            Vec3 delta = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0).subtract(pos);
            double distance = Math.max(0.001, delta.length());
            double falloff = 1.0 - Math.min(1.0, distance / radius);
            if (falloff <= 0.0) continue;
            target.hurt(serverLevel.damageSources().explosion((Entity)redEntity, (Entity)owner), (float)((double)baseDamage * (0.55 + 0.45 * falloff)));
            Vec3 push = delta.normalize().scale((0.7 + (double)chargeTier * 0.3) * (0.35 + falloff));
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.18 + falloff * 0.2, push.z));
        }
    }

    // ===== PURPLE FUSION SYSTEM =====
    private static boolean checkAndActivatePurpleNuke(LivingEntity redEntity, LivingEntity owner, ServerLevel serverLevel) {
        String ownerUUID = redEntity.getPersistentData().getString("OWNER_UUID");
        Level world = redEntity.level();
        Vec3 projectilePos = BlueRedPurpleNukeMod.getTrackedRedPosition(redEntity);
        List<Entity> nearbyBlues = world.getEntitiesOfClass(Entity.class, new AABB(projectilePos, projectilePos).inflate(4.0), e -> e instanceof BlueEntity);
        for (Entity nearby : nearbyBlues) {
            String blueOwner;
            BlueEntity blueEntity;
            if (!(nearby instanceof BlueEntity) || !(blueEntity = (BlueEntity)nearby).getPersistentData().getBoolean("linger_active") || !ownerUUID.equals(blueOwner = blueEntity.getPersistentData().getString("OWNER_UUID"))) continue;
            double blueCnt6 = blueEntity.getPersistentData().getDouble("linger_cnt6");
            if (blueCnt6 <= 0.0) {
                blueCnt6 = blueEntity.getPersistentData().getDouble("cnt6");
            }
            if (blueCnt6 < 5.0 || !(owner instanceof Player)) continue;
            Player ownerPlayer = (Player)owner;
            double currentCE = ((JujutsucraftModVariables.PlayerVariables)ownerPlayer.getCapability((Capability)JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse((Object)new JujutsucraftModVariables.PlayerVariables())).PlayerCursePower;
            if (currentCE < 2000.0 || (double)(ownerPlayer.getHealth() / ownerPlayer.getMaxHealth()) > 0.3) continue;
            double cx = blueEntity.getX();
            double cy = blueEntity.getY();
            double cz = blueEntity.getZ();
            double HP = 800.0;
            if (ownerPlayer.hasEffect(MobEffects.DAMAGE_BOOST)) {
                HP = 800 + ownerPlayer.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() * 80;
            }
            double xPower = redEntity.getPersistentData().getDouble("x_power");
            double yPower = redEntity.getPersistentData().getDouble("y_power");
            double zPower = redEntity.getPersistentData().getDouble("z_power");
            serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(cx, cy, cz), Vec2.ZERO, serverLevel, 4, "", (Component)Component.literal((String)""), serverLevel.getServer(), null).withSuppressedOutput(), "summon jujutsucraft:purple ~ ~ ~ {Health:" + Math.round(HP) + "f,Attributes:[{Name:generic.max_health,Base:" + Math.round(HP) + "}],Rotation:[" + ownerPlayer.getYRot() + "F," + ownerPlayer.getXRot() + "F]}");
            Vec3 center = new Vec3(cx, cy, cz);
            double redCnt6 = BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity);
            boolean purpleSpawned = false;
            List<Entity> purples = world.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(2.0), e -> true).stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(center))).toList();
            for (Entity purpleE : purples) {
                if (!(purpleE instanceof PurpleEntity) || purpleE.getPersistentData().getDouble("NameRanged_ranged") != 0.0) continue;
                SetRangedAmmoProcedure.execute((Entity)ownerPlayer, (Entity)purpleE);
                purpleE.getPersistentData().putBoolean("explode", true);
                purpleE.getPersistentData().putBoolean("addon_purple_fusion", true);
                purpleE.getPersistentData().putString("OWNER_UUID", ownerPlayer.getStringUUID());
                double maxCnt6 = Math.max(redCnt6, blueCnt6) * 2.0;
                purpleE.getPersistentData().putDouble("cnt6", maxCnt6);
                if (purpleE instanceof LivingEntity) {
                    LivingEntity livingPurple = (LivingEntity)purpleE;
                    try {
                        if (livingPurple.getAttributes().hasAttribute((Attribute)JujutsucraftModAttributes.SIZE.get())) {
                            livingPurple.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()).setBaseValue(24.0 * (0.5 + maxCnt6 * 0.2));
                        }
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
                purpleE.getPersistentData().putDouble("x_power", xPower);
                purpleE.getPersistentData().putDouble("y_power", yPower);
                purpleE.getPersistentData().putDouble("z_power", zPower);
                purpleE.getPersistentData().putDouble("cnt3", 1.0);
                purpleSpawned = true;
                break;
            }
            if (purpleSpawned) {
                double finalCE = currentCE;
                ownerPlayer.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
                    cap.PlayerCursePower = finalCE - 2000.0;
                    cap.syncPlayerVariables((Entity)ownerPlayer);
                });
            }
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, 2.0, 2.0, 2.0, 0.0);
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.END_ROD, cx, cy, cz, 50, 3.0, 3.0, 3.0, 0.5);
            SoundEvent electric = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:electric_shock"));
            if (electric != null) {
                world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), electric, SoundSource.NEUTRAL, 4.0f, 0.5f);
            }
            world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4.0f, 0.5f);
            redEntity.discard();
            blueEntity.discard();
            return true;
        }
        return false;
    }

    // ===== INFINITY CRUSHER =====
    private static void handleInfinityCrusher(ServerPlayer player) {
        double ct;
        Level world = player.level();
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double d = ct = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        if ((int)Math.round(ct) != 2) {
            return;
        }
        if (!player.getPersistentData().getBoolean("infinity")) {
            return;
        }
        if ((int)Math.round(vars.PlayerSelectCurseTechnique) != 5) {
            return;
        }
        if (!player.isCrouching()) {
            BlueRedPurpleNukeMod.resetInfinityCrusher(player);
            return;
        }
        CompoundTag data = player.getPersistentData();
        int activeTicks = data.getInt("addon_infinity_crusher_ticks") + 1;
        data.putInt("addon_infinity_crusher_ticks", activeTicks);
        double ceDrain = 0.5 + (double)activeTicks * 0.02;
        double currentCE = vars.PlayerCursePower;
        if (currentCE < ceDrain) {
            BlueRedPurpleNukeMod.resetInfinityCrusher(player);
            return;
        }
        double finalCE = currentCE - ceDrain;
        double totalCEDrained = data.getDouble("addon_infinity_crusher_total_ce") + ceDrain;
        data.putDouble("addon_infinity_crusher_total_ce", totalCEDrained);
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            cap.PlayerCursePower = finalCE;
            cap.syncPlayerVariables((Entity)player);
        });
        double growthFactor = Math.min(1.0, (double)activeTicks / 200.0);
        double currentRadius = 1.0 + 2.0 * growthFactor;
        float wallDamage = 3.0f + (float)(growthFactor * 8.0) + (float)(totalCEDrained * 0.012);
        Vec3 center = new Vec3(player.getX(), player.getY() + (double)player.getBbHeight() * 0.5, player.getZ());
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 auraCenter = center.add(lookDir.scale(currentRadius * 0.5));
        float dustSize = (float)(0.8 + growthFactor * 0.8);
        int particleCount = (int)(8.0 + growthFactor * 20.0);
        Vector3f auraColor = growthFactor < 0.5 ? BLUE_AURA_COLOR : new Vector3f((float)(0.1 + growthFactor * 0.6), (float)(0.3 - growthFactor * 0.2), (float)(0.95 - growthFactor * 0.3));
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(auraColor, dustSize), auraCenter.x, auraCenter.y, auraCenter.z, particleCount, currentRadius * 0.6, currentRadius * 0.4, currentRadius * 0.6, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, auraCenter.x, auraCenter.y, auraCenter.z, (int)(4.0 + growthFactor * 10.0), currentRadius * 0.4, currentRadius * 0.3, currentRadius * 0.4, 0.01);
        if (growthFactor > 0.5) {
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, auraCenter.x, auraCenter.y, auraCenter.z, (int)(growthFactor * 6.0), currentRadius * 0.5, currentRadius * 0.4, currentRadius * 0.5, 0.05);
        }
        if (serverLevel.getGameTime() % 3L == 0L) {
            int convergeCount = (int)(3.0 + growthFactor * 5.0);
            for (int i = 0; i < convergeCount; ++i) {
                double spread = currentRadius * 2.0;
                Vec3 particleStart = auraCenter.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread);
                Vec3 vel = auraCenter.subtract(particleStart);
                double dis = vel.length();
                if (dis < 0.01) continue;
                vel = vel.scale(1.0 / dis);
                serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, particleStart, Vec2.ZERO, serverLevel, 4, "", (Component)Component.literal((String)""), serverLevel.getServer(), null).withSuppressedOutput(), String.format(Locale.ROOT, "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.003 0 force", vel.x * 10000.0, vel.y * 10000.0, vel.z * 10000.0));
            }
        }
        BlueRedPurpleNukeMod.playInfinityCrusherSounds(serverLevel, player, auraCenter, activeTicks, growthFactor);
        double reflectRadius = Math.max(currentRadius + 1.0, (4.0 + (double)player.getBbWidth()) / 2.0);
        double myNameRanged = data.getDouble("NameRanged");
        double myNameRangedR = data.getDouble("NameRanged_ranged");
        List<Entity> nearbyEntities = world.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(reflectRadius), e -> e != player);
        for (Entity entity : nearbyEntities) {
            boolean isRangedAmmo = entity.getType().is(TagKey.create((ResourceKey)Registries.ENTITY_TYPE, (ResourceLocation)new ResourceLocation("forge:ranged_ammo"))) || entity.getPersistentData().getDouble("NameRanged_ranged") != 0.0;
            boolean isProjectile = entity instanceof Projectile;
            if (!isRangedAmmo && !isProjectile) continue;
            double entNR = entity.getPersistentData().getDouble("NameRanged");
            double entNRR = entity.getPersistentData().getDouble("NameRanged_ranged");
            boolean isOwn = false;
            if (entNRR != 0.0 && (entNRR == myNameRanged || entNRR == myNameRangedR)) {
                isOwn = true;
            }
            if (entNR != 0.0 && (entNR == myNameRangedR || entNR == myNameRanged)) {
                isOwn = true;
            }
            if (isOwn) continue;
            Vec3 vel = entity.getDeltaMovement();
            if (vel.length() > 0.01) {
                entity.setDeltaMovement(vel.scale(-1.5));
                entity.hurtMarked = true;
                if (entity instanceof Projectile) {
                    Projectile proj = (Projectile)entity;
                    proj.setOwner((Entity)player);
                }
                if (!(entity instanceof LivingEntity)) continue;
                LivingEntity le = (LivingEntity)entity;
                SetRangedAmmoProcedure.execute((Entity)player, (Entity)entity);
                continue;
            }
            if (!isRangedAmmo) continue;
            Vec3 push = entity.position().subtract(center).normalize().scale(0.5);
            entity.setDeltaMovement(push);
            entity.hurtMarked = true;
        }
        Vec3 playerVel = player.getDeltaMovement();
        double forwardVel = playerVel.x * lookDir.x + playerVel.y * lookDir.y + playerVel.z * lookDir.z;
        boolean isMovingForward = forwardVel > 0.01;
        List<LivingEntity> nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(currentRadius + 3.0), e -> e.isAlive() && e != player && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        String playerUUID = player.getStringUUID();
        for (LivingEntity mob : nearbyMobs) {
            Vec3 toMob = mob.position().add(0.0, (double)mob.getBbHeight() * 0.5, 0.0).subtract(center);
            double dist = toMob.length();
            boolean isHardLocked = playerUUID.equals(mob.getPersistentData().getString("addon_crusher_lock_owner"));
            int contactTicks = mob.getPersistentData().getInt("addon_crusher_contact_ticks");
            if (isHardLocked) {
                double releaseRange;
                double lockZ;
                double lockY;
                double lockX = mob.getPersistentData().getDouble("addon_crusher_lock_x");
                double lockDistSq = player.distanceToSqr(lockX, lockY = mob.getPersistentData().getDouble("addon_crusher_lock_y"), lockZ = mob.getPersistentData().getDouble("addon_crusher_lock_z"));
                if (lockDistSq > (releaseRange = currentRadius + 5.0) * releaseRange) {
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
                BlueRedPurpleNukeMod.spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
                if (activeTicks % 8 != 0) continue;
                world.playSound(null, BlockPos.containing((double)lockX, (double)lockY, (double)lockZ), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f + (float)growthFactor * 0.3f, 1.4f);
                continue;
            }
            if (dist > currentRadius + 2.0) {
                if (contactTicks <= 0) continue;
                mob.getPersistentData().putInt("addon_crusher_contact_ticks", 0);
                mob.getPersistentData().remove("addon_crusher_lock_owner");
                continue;
            }
            Vec3 toMobNorm = toMob.normalize();
            double dot = lookDir.x * toMobNorm.x + lookDir.y * toMobNorm.y + lookDir.z * toMobNorm.z;
            if (dot < 0.5 || !isMovingForward) continue;
            mob.getPersistentData().putInt("addon_crusher_contact_ticks", ++contactTicks);
            if (contactTicks >= 15) {
                mob.getPersistentData().putString("addon_crusher_lock_owner", playerUUID);
                mob.getPersistentData().putDouble("addon_crusher_lock_x", mob.getX());
                mob.getPersistentData().putDouble("addon_crusher_lock_y", mob.getY());
                mob.getPersistentData().putDouble("addon_crusher_lock_z", mob.getZ());
            }
            double pushStrength = 0.5 * (0.6 + 0.4 * dot) * (1.0 + growthFactor * 0.5);
            Vec3 pushDir = lookDir.scale(pushStrength).add(toMobNorm.scale(-0.05));
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.2).add(pushDir));
            mob.hurtMarked = true;
            if (!mob.horizontalCollision && !mob.verticalCollision || !(dist < currentRadius + 1.5)) continue;
            mob.hurt(serverLevel.damageSources().generic(), wallDamage * 0.5f);
            BlueRedPurpleNukeMod.spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
            if (activeTicks % 12 != 0) continue;
            world.playSound(null, BlockPos.containing((double)mob.getX(), (double)mob.getY(), (double)mob.getZ()), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.35f, 1.6f);
        }
    }

    /**
     * Performs reset infinity crusher for this addon component.
     * @param player player instance involved in this operation.
     */
    private static void resetInfinityCrusher(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putInt("addon_infinity_crusher_ticks", 0);
        data.putDouble("addon_infinity_crusher_total_ce", 0.0);
        Level level = player.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        String playerUUID = player.getStringUUID();
        List<LivingEntity> locked = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(player.position(), player.position()).inflate(128.0), e -> playerUUID.equals(e.getPersistentData().getString("addon_crusher_lock_owner")));
        for (LivingEntity mob : locked) {
            mob.setNoGravity(false);
            mob.getPersistentData().remove("addon_crusher_lock_owner");
            mob.getPersistentData().remove("addon_crusher_lock_x");
            mob.getPersistentData().remove("addon_crusher_lock_y");
            mob.getPersistentData().remove("addon_crusher_lock_z");
            mob.getPersistentData().putInt("addon_crusher_contact_ticks", 0);
        }
    }

    /**
     * Performs spawn crush vfx for this addon component.
     * @param serverLevel level value used by this operation.
     * @param mob entity instance being processed by this helper.
     * @param color color used by this method.
     * @param intensity intensity used by this method.
     */
    private static void spawnCrushVFX(ServerLevel serverLevel, LivingEntity mob, Vector3f color, double intensity) {
        double mx = mob.getX();
        double my = mob.getY() + (double)mob.getBbHeight() * 0.5;
        double mz = mob.getZ();
        int basePart = (int)(6.0 + intensity * 10.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.CRIT, mx, my, mz, basePart, 0.3, 0.4, 0.3, 0.15 + intensity * 0.1);
        serverLevel.sendParticles((ParticleOptions)new DustParticleOptions(color, (float)(0.8 + intensity * 0.5)), mx, my, mz, (int)(4.0 + intensity * 6.0), 0.2, 0.3, 0.2, 0.0);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.CLOUD, mx, my, mz, (int)(2.0 + intensity * 4.0), 0.2, 0.2, 0.2, 0.04);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, mx, my, mz, (int)(3.0 + intensity * 5.0), 0.2, 0.2, 0.2, 0.1);
        if (intensity > 0.6) {
            serverLevel.sendParticles((ParticleOptions)ParticleTypes.FLASH, mx, my, mz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Performs play infinity crusher sounds for this addon component.
     * @param serverLevel level value used by this operation.
     * @param player player instance involved in this operation.
     * @param auraCenter aura center used by this method.
     * @param activeTicks tick-based timing value used by this operation.
     * @param growthFactor growth factor used by this method.
     */
    private static void playInfinityCrusherSounds(ServerLevel serverLevel, ServerPlayer player, Vec3 auraCenter, int activeTicks, double growthFactor) {
        int rumbleInterval;
        if (activeTicks % 5 == 0) {
            float basePitch = 0.8f + (float)growthFactor * 0.6f;
            float volume = 0.05f + (float)growthFactor * 0.08f;
            serverLevel.playSound(null, BlockPos.containing((Position)auraCenter), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, volume, basePitch);
        }
        if (activeTicks % (rumbleInterval = Math.max(15, 40 - (int)(growthFactor * 25.0))) == 0) {
            float pitch = 0.5f + (float)growthFactor * 0.5f;
            float volume = 0.3f + (float)growthFactor * 0.25f;
            serverLevel.playSound(null, BlockPos.containing((Position)auraCenter), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, volume, pitch);
        }
        if (growthFactor > (double)0.7f && activeTicks % 10 == 0) {
            serverLevel.playSound(null, BlockPos.containing((Position)auraCenter), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25f + (float)(growthFactor - (double)0.7f) * 0.6f, 1.2f + (float)growthFactor * 0.4f);
        }
    }

    // ===== BLUE ORB SYSTEM =====
    private void handleBlueTick(LivingEntity blueEntity) {
        boolean shouldLinger;
        if (blueEntity instanceof BlueEntity) {
            BlueEntity be = (BlueEntity)blueEntity;
            try {
                if (((Boolean)be.getEntityData().get(BlueEntity.DATA_flag_purple)).booleanValue()) {
                    return;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (blueEntity.getPersistentData().getBoolean("flag_purple")) {
            return;
        }
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
            Level level = blueEntity.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel)level;
                sl.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, lx, ly, lz, 3, 0.5, 0.5, 0.5, 0.02);
                if (timer % 20 == 0) {
                    sl.sendParticles((ParticleOptions)ParticleTypes.END_ROD, lx, ly, lz, 10, 1.0, 1.0, 1.0, 0.1);
                }
            }
            if (timer >= 200) {
                blueEntity.discard();
            }
            return;
        }
        boolean flagStart = BlueRedPurpleNukeMod.getBlueEntityFlagStart(blueEntity);
        if (!flagStart) {
            return;
        }
        boolean circle = blueEntity.getPersistentData().getBoolean("circle");
        boolean aimEnded = blueEntity.getPersistentData().getBoolean("aim_ended");
        double cnt6 = blueEntity.getPersistentData().getDouble("cnt6");
        boolean aiming = false;
        if (circle && cnt6 >= 5.0 && !aimEnded) {
            int aimTicks = blueEntity.getPersistentData().getInt("addon_aim_ticks");
            if (!this.isOwnerCrouching(blueEntity) || aimTicks >= 90) {
                blueEntity.getPersistentData().putBoolean("aim_ended", true);
                blueEntity.getPersistentData().putBoolean("addon_aim_active", false);
            } else {
                aiming = true;
                blueEntity.getPersistentData().putInt("addon_aim_ticks", aimTicks + 1);
                blueEntity.getPersistentData().putBoolean("addon_aim_active", true);
                if (cnt6 >= 5.0) {
                    blueEntity.addEffect(new MobEffectInstance(MobEffects.LUCK, 5, 15, false, false));
                }
                this.applyBlueAim(blueEntity);
            }
        }
        if (cnt6 < 5.0) {
            return;
        }
        if (circle && !aiming && aimEnded) {
            shouldLinger = true;
        } else if (circle && aiming) {
            shouldLinger = false;
        } else {
            double threshold;
            double cnt1 = blueEntity.getPersistentData().getDouble("cnt1");
            boolean bl = shouldLinger = cnt1 >= (threshold = 30.0 * (1.0 + cnt6 * 0.1)) - 10.0;
        }
        if (!shouldLinger) {
            return;
        }
        blueEntity.getPersistentData().putBoolean("circle", false);
        blueEntity.getPersistentData().putBoolean("linger_active", true);
        blueEntity.getPersistentData().putBoolean("addon_aim_active", false);
        blueEntity.getPersistentData().putInt("linger_timer", 0);
        blueEntity.getPersistentData().putDouble("linger_x", blueEntity.getX());
        blueEntity.getPersistentData().putDouble("linger_y", blueEntity.getY());
        blueEntity.getPersistentData().putDouble("linger_z", blueEntity.getZ());
        blueEntity.getPersistentData().putDouble("linger_cnt6", cnt6);
        blueEntity.getPersistentData().putDouble("cnt1", 0.0);
        blueEntity.getPersistentData().putDouble("NameRanged_ranged", 0.0);
    }

    /**
     * Applies blue aim to the current addon state.
     * @param blueEntity entity instance being processed by this helper.
     */
    private void applyBlueAim(LivingEntity blueEntity) {
        Vec3 current;
        Vec3 target;
        Vec3 delta;
        double dist;
        Vec3 newPos;
        Vec3 fromPlayer;
        double distFromPlayer;
        Level level = blueEntity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        String ownerUUID = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) {
            return;
        }
        LivingEntity owner = BlueRedPurpleNukeMod.resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player)) {
            return;
        }
        Player playerOwner = (Player)owner;
        Vec3 eye = playerOwner.getEyePosition(1.0f);
        Vec3 rawLookAim = playerOwner.getLookAngle();
        if (rawLookAim.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 look = rawLookAim.normalize();
        Vec3 rayEnd = eye.add(look.scale(20.0));
        BlockHitResult lookHit = serverLevel.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)playerOwner));
        double targetDist = 20.0;
        if (lookHit != null && lookHit.getType() == HitResult.Type.BLOCK) {
            targetDist = Math.min(targetDist, eye.distanceTo(lookHit.getLocation()));
        }
        if ((distFromPlayer = (fromPlayer = (newPos = (dist = (delta = (target = eye.add(look.scale(targetDist = Math.max(10.0, Math.min(20.0, targetDist))))).subtract(current = blueEntity.position())).length()) > 1.5 ? current.add(delta.normalize().scale(1.5)) : target).subtract(eye)).length()) < 10.0) {
            newPos = eye.add(fromPlayer.normalize().scale(10.0));
        } else if (distFromPlayer > 20.0) {
            newPos = eye.add(fromPlayer.normalize().scale(20.0));
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

    /**
     * Checks whether is owner crouching is true for the current addon state.
     * @param blueEntity entity instance being processed by this helper.
     * @return true when is owner crouching succeeds; otherwise false.
     */
    private boolean isOwnerCrouching(LivingEntity blueEntity) {
        Player p;
        Level level = blueEntity.level();
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        String uuid = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (uuid.isEmpty()) {
            return false;
        }
        LivingEntity owner = BlueRedPurpleNukeMod.resolveOwner(serverLevel, uuid);
        return owner instanceof Player && (p = (Player)owner).isCrouching();
    }

    /**
     * Returns blue entity flag start for the current addon state.
     * @param entity entity instance being processed by this helper.
     * @return true when get blue entity flag start succeeds; otherwise false.
     */
    private static boolean getBlueEntityFlagStart(LivingEntity entity) {
        if (!(entity instanceof BlueEntity)) {
            return false;
        }
        BlueEntity be = (BlueEntity)entity;
        try {
            return (Boolean)be.getEntityData().get(BlueEntity.DATA_flag_start);
        }
        catch (Exception e) {
            return false;
        }
    }

    // ===== SHARED ORB HELPERS =====
    private static void updateOrbPosition(LivingEntity redEntity, Vec3 pos) {
        redEntity.getPersistentData().putDouble("x_pos", pos.x);
        redEntity.getPersistentData().putDouble("y_pos", pos.y);
        redEntity.getPersistentData().putDouble("z_pos", pos.z);
        redEntity.teleportTo(pos.x, pos.y, pos.z);
    }

    /**
     * Returns tracked red position for the current addon state.
     * @param redEntity entity instance being processed by this helper.
     * @return the resolved tracked red position.
     */
    private static Vec3 getTrackedRedPosition(LivingEntity redEntity) {
        double x = redEntity.getPersistentData().getDouble("x_pos");
        double y = redEntity.getPersistentData().getDouble("y_pos");
        double z = redEntity.getPersistentData().getDouble("z_pos");
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            return redEntity.position();
        }
        return new Vec3(x, y, z);
    }

    /**
     * Returns effective red cnt 6 for the current addon state.
     * @param redEntity entity instance being processed by this helper.
     * @return the resolved effective red cnt 6.
     */
    private static double getEffectiveRedCnt6(LivingEntity redEntity) {
        return Math.max(redEntity.getPersistentData().getDouble("cnt6"), Math.max(redEntity.getPersistentData().getDouble("addon_red_charge_cached"), redEntity.getPersistentData().getDouble("addon_red_charge_used")));
    }

    /**
     * Resolves owner from the available addon data.
     * @param serverLevel level value used by this operation.
     * @param ownerUUID identifier used to resolve the requested entry or state.
     * @return the resolved resolve owner.
     */
    private static LivingEntity resolveOwner(ServerLevel serverLevel, String ownerUUID) {
        try {
            UUID uuid = UUID.fromString(ownerUUID);
            Entity ownerEntity = serverLevel.getEntity(uuid);
            if (ownerEntity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity)ownerEntity;
                return living;
            }
            ServerPlayer sp = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (sp != null) {
                return sp;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    // ===== FULL-CHARGE BLUE VARIANT =====
    public static void handleCrouchFullChargeBlueAim(LivingEntity blueEntity) {
        Vec3 current;
        Vec3 target;
        Vec3 delta;
        double dist;
        Vec3 newPos;
        Vec3 fromPlayer;
        double distFromPlayer;
        Level level = blueEntity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        String ownerUUID = blueEntity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID.isEmpty()) {
            return;
        }
        LivingEntity owner = BlueRedPurpleNukeMod.resolveOwner(serverLevel, ownerUUID);
        if (!(owner instanceof Player)) {
            return;
        }
        Player playerOwner = (Player)owner;
        Vec3 eye = playerOwner.getEyePosition(1.0f);
        Vec3 rawLook2 = playerOwner.getLookAngle();
        Vec3 look = rawLook2.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : rawLook2.normalize();
        Vec3 rayEnd = eye.add(look.scale(20.0));
        BlockHitResult lookHit = serverLevel.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)playerOwner));
        double targetDist = 20.0;
        if (lookHit != null && lookHit.getType() == HitResult.Type.BLOCK) {
            targetDist = Math.min(targetDist, eye.distanceTo(lookHit.getLocation()));
        }
        if ((distFromPlayer = (fromPlayer = (newPos = (dist = (delta = (target = eye.add(look.scale(targetDist = Math.max(10.0, Math.min(20.0, targetDist))))).subtract(current = blueEntity.position())).length()) > 1.5 ? current.add(delta.normalize().scale(1.5)) : target).subtract(eye)).length()) < 10.0) {
            newPos = eye.add(fromPlayer.normalize().scale(10.0));
        } else if (distFromPlayer > 20.0) {
            newPos = eye.add(fromPlayer.normalize().scale(20.0));
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
        BlueRedPurpleNukeMod.pullMobsWithCrouchFullChargeBlue(serverLevel, blueEntity, owner, newPos, look);
        BlueRedPurpleNukeMod.destroyBlocksNearCrouchBlue(serverLevel, blueEntity, owner, newPos, look);
    }

    /**
     * Performs pull mobs with crouch full charge blue for this addon component.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param lookDir look dir used by this method.
     */
    private static void pullMobsWithCrouchFullChargeBlue(ServerLevel serverLevel, LivingEntity blueEntity, LivingEntity owner, Vec3 orbPos, Vec3 lookDir) {
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(orbPos, orbPos).inflate(6.0), e -> e.isAlive() && e != blueEntity && e != owner && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        for (LivingEntity entity : nearby) {
            Vec3 toOrb = orbPos.subtract(entity.position());
            double d = toOrb.length();
            if (d > 6.0 || d < 0.001) continue;
            double strength = 1.2 * (1.0 + (1.0 - d / 6.0) * 2.0);
            Vec3 pull = toOrb.normalize().scale(strength);
            Vec3 awayFromLook = lookDir.scale(0.6);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2).add(pull).add(awayFromLook));
            entity.hurtMarked = true;
            if (!(d < 2.0)) continue;
            entity.setNoGravity(true);
            entity.teleportTo(orbPos.x - lookDir.x * 0.8, orbPos.y, orbPos.z - lookDir.z * 0.8);
        }
    }

    /**
     * Performs destroy blocks near crouch blue for this addon component.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param lookDir look dir used by this method.
     */
    private static void destroyBlocksNearCrouchBlue(ServerLevel serverLevel, LivingEntity blueEntity, LivingEntity owner, Vec3 orbPos, Vec3 lookDir) {
        int bx = Mth.floor((double)orbPos.x);
        int by = Mth.floor((double)orbPos.y);
        int bz = Mth.floor((double)orbPos.z);
        for (int dx = -4; dx <= 4; ++dx) {
            for (int dy = -4; dy <= 4; ++dy) {
                for (int dz = -4; dz <= 4; ++dz) {
                    float hardness;
                    BlockPos bpos;
                    BlockState state;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > 4.0 || (state = serverLevel.getBlockState(bpos = new BlockPos(bx + dx, by + dy, bz + dz))).isAir() || state.isSolid() || BlueRedPurpleNukeMod.isEssentialBlock(serverLevel, bpos) || (hardness = state.getDestroySpeed((BlockGetter)serverLevel, bpos)) < 0.0f || hardness > 10.0f) continue;
                    serverLevel.destroyBlock(bpos, false, (Entity)blueEntity);
                    serverLevel.sendParticles((ParticleOptions)new BlockParticleOption(ParticleTypes.BLOCK, state), (double)bpos.getX() + 0.5, (double)bpos.getY() + 0.5, (double)bpos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    /**
     * Checks whether is essential block is true for the current addon state.
     * @param serverLevel level value used by this operation.
     * @param bpos bpos used by this method.
     * @return true when is essential block succeeds; otherwise false.
     */
    private static boolean isEssentialBlock(ServerLevel serverLevel, BlockPos bpos) {
        Block block = serverLevel.getBlockState(bpos).getBlock();
        if (block == Blocks.BEDROCK) {
            return true;
        }
        if (block == Blocks.BARRIER) {
            return true;
        }
        if (block == Blocks.SPAWNER) {
            return true;
        }
        return block == Blocks.END_PORTAL_FRAME;
    }

    /**
     * Returns owner rank damage scale for the current addon state.
     * @param owner entity instance being processed by this helper.
     * @return the resolved owner rank damage scale.
     */
    private static double getOwnerRankDamageScale(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer)) {
            return 1.0;
        }
        ServerPlayer player = (ServerPlayer)owner;
        JujutsucraftModVariables.PlayerVariables vars = (JujutsucraftModVariables.PlayerVariables)player.getCapability((Capability)JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse((Object)new JujutsucraftModVariables.PlayerVariables());
        double capabilityScale = BlueRedPurpleNukeMod.getScaleFromPlayerLevel(vars.PlayerLevel);
        double advancementScale = BlueRedPurpleNukeMod.getAdvancementRankDamageScale(player);
        double effectScale = BlueRedPurpleNukeMod.getDamageFixEquivalentScale(player);
        return Math.max(Math.max(capabilityScale, advancementScale), effectScale);
    }

    /**
     * Converts player level to the same baseline curve used by base Strength-driven damage.
     * @param playerLevel level value used by this operation.
     * @return converted baseline damage scale.
     */
    private static double getScaleFromPlayerLevel(double playerLevel) {
        if (playerLevel <= 0.0) {
            return 1.0;
        }
        double level = Math.max(playerLevel - 1.0, 0.0);
        double levelPower = Math.round(level);
        if (levelPower < 3.0) {
            levelPower = Math.min(levelPower, 1.0);
        }
        // Base formula shape: Damage *= 1 + ((1 + StrengthAmp) * 0.333).
        return Math.max(1.0, 1.0 + (1.0 + levelPower) * 0.333);
    }

    /**
     * Uses grade advancements as fallback rank scaling when capability data has not synced yet.
     * @param player entity instance being processed by this helper.
     * @return advancement-derived rank scale.
     */
    private static double getAdvancementRankDamageScale(ServerPlayer player) {
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_special")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(20.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_1")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(13.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_1_semi")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(11.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_2")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(9.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_2_semi")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(7.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_3")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(4.0);
        }
        if (CooldownTrackerEvents.hasAdvancement(player, "jujutsucraft:sorcerer_grade_4")) {
            return BlueRedPurpleNukeMod.getScaleFromPlayerLevel(2.0);
        }
        return 1.0;
    }

    /**
     * Recreates the base DamageFix contributions from Strength/Weakness/Zone and attack attribute.
     * @param player entity instance being processed by this helper.
     * @return live DamageFix-equivalent scale.
     */
    private static double getDamageFixEquivalentScale(ServerPlayer player) {
        double strengthLevel = 0.0;
        if (player.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            strengthLevel += player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.333;
        }
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (strength != null) {
            strengthLevel += 1.0 + strength.getAmplifier();
        }
        MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
        if (weakness != null) {
            strengthLevel -= 1.0 + weakness.getAmplifier();
        }
        double scale = 1.0 + strengthLevel * 0.333;
        MobEffectInstance zone = player.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
        if (zone != null) {
            scale *= 1.2 + 0.1 * zone.getAmplifier();
        }
        return Math.max(scale, 1.0);
    }

    /**
     * Returns normal red damage scale for the current addon state.
     * @param owner entity instance being processed by this helper.
     * @return the resolved normal red damage scale.
     */
    private static double getNormalRedDamageScale(LivingEntity owner) {
        return BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner) * NORMAL_RED_DAMAGE_ADVANTAGE;
    }

    // ===== NUMERIC HELPERS =====
    private static int getRedChargeTier(double cnt6) {
        if (cnt6 >= 5.0) {
            return 3;
        }
        if (cnt6 >= 3.0) {
            return 2;
        }
        return 1;
    }

    /**
     * Returns normal red speed for the current addon state.
     * @param cnt6 cnt 6 used by this method.
     * @return the resolved normal red speed.
     */
    private static double getNormalRedSpeed(double cnt6) {
        return switch (BlueRedPurpleNukeMod.getRedChargeTier(cnt6)) {
            case 1 -> 2.3;
            case 2 -> 3.2;
            default -> 4.3;
        };
    }
}

