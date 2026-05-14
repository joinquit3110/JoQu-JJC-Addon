package net.mcreator.jujutsucraft.addon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.CooldownTrackerEvents;
import net.mcreator.jujutsucraft.addon.DomainMasteryCommands;
import net.mcreator.jujutsucraft.addon.ModItems;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.clash.ClashSubsystem;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.limb.LimbGameplayHandler;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.yuta.YutaFakePlayerCommands;
import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.entity.PurpleEntity;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModAttributes;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.init.JujutsucraftModParticleTypes;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.mcreator.jujutsucraft.procedures.BlockDestroyAllDirectionProcedure;
import net.mcreator.jujutsucraft.procedures.KnockbackProcedure;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.mcreator.jujutsucraft.procedures.SetRangedAmmoProcedure;
import net.mcreator.jujutsucraft.procedures.AIRedProcedure;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
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
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import org.slf4j.Logger;

@Mod(value="jjkblueredpurple")
/**
 * Primary addon mod entry point that wires Forge events, networking, and the custom Gojo combat extensions. This class owns the Red orb rewrite, Blue aim and linger behavior, Purple fusion checks, Infinity Crusher control, and Black Flash charge helpers.
 */
// ===== MOD BOOTSTRAP =====
public class BlueRedPurpleNukeMod {
    private static final Logger LOGGER = LogUtils.getLogger();
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
    // Normalized size of the client-visible Black Flash timing target arc for normal characters.
    private static final float BF_TIMING_RED_SIZE = 0.045f;
    // Yuji's Black Flash identity keeps a modestly easier target arc without making timed releases too forgiving.
    private static final float BF_TIMING_RED_SIZE_YUJI = 0.075f;
    // Yuji's timing needle completes a lap faster; 13 ticks is about 28% faster than the normal 18-tick lap.
    private static final float BF_TIMING_PERIOD_TICKS_YUJI = 13.0f;
    // Wider normalized edge tolerance for visual/input-boundary error; client decides timing against the visible red arc.
    public static final float BF_TIMING_EDGE_TOLERANCE = 0.012f;
    // Maximum normalized client/server needle drift accepted for normal diagnostics; visual-in-red client releases are not rejected unless drift is extreme.
    private static final float BF_TIMING_CLIENT_DRIFT_TOLERANCE = 0.34f;
    // Very large drift is still suspicious, but only rejects packets that are not visually inside the synced red arc.
    private static final float BF_TIMING_EXTREME_DRIFT_TOLERANCE = 0.48f;
    // Packet needle and packet elapsed must agree; this prevents stale render samples from passing as fresh input.
    private static final float BF_TIMING_PACKET_NEEDLE_TOLERANCE = 0.045f;
    // Extra age slack for packet travel and jitter when checking whether a release is stale.
    private static final float BF_TIMING_LATENCY_JITTER_TICKS = 3.0f;
    // Cap half-ping compensation so very high or unstable pings cannot stretch a timing session indefinitely.
    private static final float BF_TIMING_MAX_LATENCY_COMP_TICKS = 6.0f;
    // Real server damage hits this many ticks before a valid release can still consume the timed Black Flash.
    private static final int BF_RECENT_HIT_GRACE_TICKS = 10;
    // Server grace window after charge release so a same-frame client release packet can arrive before cleanup.
    private static final int BF_TIMING_RELEASE_PACKET_GRACE_TICKS = 6;
    // Ignore sub-frame/client-race releases before the HUD/server session has had enough ticks to stabilize.
    public static final int BF_TIMING_MIN_RELEASE_AGE_TICKS = 3;
    // Keep the first visible target arc away from the t=0 needle so fresh rings do not bait instant releases.
    private static final float BF_TIMING_INITIAL_NEEDLE_MIN_SEPARATION = 0.26f;
    // Short grace only for diagnostics/legacy state; resolved casts are primarily gated by an explicit release/reset latch.
    private static final int BF_RESOLVED_CAST_DEBOUNCE_TICKS = 4;
    // cnt6 must leave the completed-cast charge state before another timing ring may start.
    // This is intentionally a hard reset threshold: key-up/idle flags can become true while the base cast still holds cnt6 high.
    private static final double BF_CHARGE_RETRIGGER_RESET_CNT6 = 1.0;
    private static final String BF_STATE_IDLE = "IDLE";
    private static final String BF_STATE_RING_ACTIVE = "RING_ACTIVE";
    private static final String BF_STATE_WAITING_HIT = "WAITING_HIT";
    private static final String BF_STATE_LOCKED_UNTIL_RESET = "LOCKED_UNTIL_RESET";
    // Server ticks after a timed release where the next valid hit may consume the guaranteed Black Flash proc.
    public static final int BF_GUARANTEE_TIMEOUT_TICKS = 60;
    public static final int BF_FLOW_MAX = 8;
    public static final int BF_FLOW_COOLDOWN_TICKS = 1200;
    // Tunable one-shot celebration when the player completes all eight Black Flash timing flow hits.
    private static final int BF_SPARKS_BLESSING_BUFF_TICKS = 160;
    private static final int BF_SPARKS_SMOKE_RING_POINTS = 48;
    private static final double BF_SPARKS_SMOKE_RING_RADIUS = 5.8;
    private static final double BF_SPARKS_SHOCKWAVE_RADIUS = 4.6;
    private static final boolean BF_BLESSING_BREAK_BLOCKS = true;
    private static final int[] BF_BLESSING_BLOCK_BREAK_WAVE_RADII = new int[]{7, 11, 15, 20};
    private static final int[] BF_BLESSING_BLOCK_BREAK_WAVE_CAPS = new int[]{80, 110, 140, 170};
    private static final int[] BF_BLESSING_BLOCK_BREAK_WAVE_DELAYS = new int[]{0, 10, 20, 32};
    private static final int BF_BLESSING_MAX_BLOCK_BREAKS = 500;
    private static final float BF_BLESSING_MAX_BLOCK_HARDNESS = 2.4f;
    private static final double BF_BLESSING_ENTITY_RADIUS = 7.0;
    private static final DustParticleOptions BF_SPARKS_RED_DUST = new DustParticleOptions(new Vector3f(0.95f, 0.02f, 0.02f), 1.9f);
    private static final DustParticleOptions BF_SPARKS_BLACK_DUST = new DustParticleOptions(new Vector3f(0.02f, 0.0f, 0.0f), 2.15f);
    // Hard lifetime for an unconsumed timed success. Expiry sends explicit fail feedback instead of leaving TIMED pending.
    private static final int BF_PENDING_SUCCESS_EXPIRE_TICKS = BF_GUARANTEE_TIMEOUT_TICKS;
    // One full lap of the Black Flash timing needle for normal characters, in ticks.
    public static final float BF_TIMING_PERIOD_TICKS = 18.0f;
    // Base cnt6 charge required before the Black Flash timing session becomes available.
    private static final double BF_CHARGE_REQUIRED_BASE = 5.0;
    // Flow now makes the next timing session arrive sooner; the hard no-retrigger lock below still prevents same-cast phantom rings.
    private static final double BF_CHARGE_REQUIRED_REDUCTION_PER_FLOW = 0.12;
    private static final double BF_CHARGE_REQUIRED_MIN = 3.8;
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
    // Hard-locked targets only take Crusher damage on this interval so sustained control does not apply full damage every tick.
    private static final int CRUSHER_LOCK_DAMAGE_INTERVAL = 5;
    // Pre-lock wall scrapes use a slightly slower interval because they are the lighter sustained damage state.
    private static final int CRUSHER_COLLISION_DAMAGE_INTERVAL = 6;
    // Total cursed-energy contribution soft-caps here before overflow is heavily damped.
    private static final double CRUSHER_CE_DAMAGE_SOFT_CAP = 40.0;
    // Overflow contribution retained after the Crusher CE soft cap is reached.
    private static final double CRUSHER_CE_DAMAGE_OVERFLOW_WEIGHT = 0.2;
    // Bonus-rank share retained by Blue's multi-hit damage scaling.
    private static final double BLUE_MULTI_HIT_RANK_BONUS_WEIGHT = 0.16;
    // Hard ceiling for Blue's per-hit Infinity damage scaling.
    private static final double BLUE_MULTI_HIT_MAX_SCALE = 1.6;
    // Bonus-rank share retained by Red's burst damage scaling.
    private static final double RED_BURST_RANK_BONUS_WEIGHT = 0.28;
    // Hard ceiling for Red's burst damage scaling before the normal-flight advantage is applied.
    private static final double RED_BURST_MAX_SCALE = 1.85;
    // Crouch full-charge Red finisher keeps only a very narrow slice of owner-rank bonus so it stays slightly above crouch low-charge without spiking far past it.
    private static final double RED_SHIFT_FINISHER_RANK_BONUS_WEIGHT = 0.06;
    // Hard ceiling for the crouch full-charge Red finisher rank scaling.
    private static final double RED_SHIFT_FINISHER_MAX_SCALE = 1.18;
    // Bonus-rank share retained by Infinity Crusher damage scaling.
    private static final double CRUSHER_RANK_BONUS_WEIGHT = 0.22;
    // Hard ceiling for Infinity Crusher damage scaling.
    private static final double CRUSHER_MAX_SCALE = 1.7;
    // Bonus-rank share retained by Purple fusion damage scaling before repeated-pass budgeting is applied.
    private static final double PURPLE_FUSION_RANK_BONUS_WEIGHT = 0.24;
    // Hard ceiling for Purple fusion damage scaling before repeated-pass budgeting is applied.
    private static final double PURPLE_FUSION_MAX_SCALE = 1.75;
    // Repeated Blue passes keep a small floor of the caster's bonus while the rest decays per execute call.
    private static final double BLUE_PASS_MIN_RETENTION = 0.18;
    // Repeated Blue passes lose rank bonus at this rate per execute call.
    private static final double BLUE_PASS_FALLOFF = 0.45;
    // Repeated Red passes lose rank bonus faster because several base and addon variants front-load their damage.
    private static final double RED_PASS_MIN_RETENTION = 0.16;
    // Repeated Red passes lose rank bonus at this rate per execute call.
    private static final double RED_PASS_FALLOFF = 0.75;
    // Purple fusion keeps only a narrow slice of repeated rank bonus because the base beam already ticks very hard on its own.
    private static final double PURPLE_PASS_MIN_RETENTION = 0.12;
    // Repeated Purple fusion passes lose rank bonus at this rate per execute call.
    private static final double PURPLE_PASS_FALLOFF = 1.0;
    // Repeated Infinity Crusher hits keep only a narrow slice of repeated rank bonus because the move is sustained control, not repeated burst casting.
    private static final double CRUSHER_PASS_MIN_RETENTION = 0.15;
    // Repeated Infinity Crusher hits lose rank bonus at this rate per damage event.
    private static final double CRUSHER_PASS_FALLOFF = 0.85;
    // Hollow Purple fusion still gains a fusion bump, but no longer doubles full charge into an extreme cnt6 value that then scales every repeated base tick.
    private static final double PURPLE_FUSION_CNT6_CAP = 6.0;
    // Pull radius used by the crouch full-charge Blue variant.
    private static final double BLUE_FULL_PULL_RADIUS = 6.0;
    // Pull strength used by the crouch full-charge Blue variant.
    private static final double BLUE_FULL_PULL_STRENGTH = 1.2;
    // Direct Blue AI damage is throttled to this tick interval so repeated base executes cannot apply every tick.
    public static final int BLUE_DAMAGE_COOLDOWN_TICKS = 5;
    // Direct Red AI damage is throttled to this tick interval so any remaining base execute paths cannot stack repeated hits on the same victim.
    public static final int RED_DAMAGE_COOLDOWN_TICKS = 5;
    // Crouch low-charge Red keeps addon-controlled orb movement, but caps travel so the safer single-hit path stays a close-to-mid-range burst.
    private static final double CROUCH_RED_LOW_CHARGE_MAX_RANGE = 28.0;
    // Crouch low-charge Red flies slightly slower than the standing version so its safer route still feels deliberate rather than like a long-range launcher.
    private static final double CROUCH_RED_LOW_CHARGE_SPEED_SCALE = 0.9;
    // Block destruction radius, in blocks, for the crouch full-charge Blue variant.
    private static final int BLUE_FULL_BLOCK_RANGE = 4;
    // Full-charge Blue only attempts terrain destruction on this interval.
    private static final int BLUE_BLOCK_BREAK_INTERVAL = 8;
    // Hard cap for how many blocks a single full-charge Blue break pulse may destroy.
    private static final int BLUE_MAX_BLOCKS_PER_BREAK = 24;
    // OG Blue VFX stream is restored on a light interval so addon aim visuals stay close to vanilla behavior without flooding packets.
    private static final int BLUE_OG_EFFECT_PARTICLE_INTERVAL = 2;
    // Environmental destruction power marker passed into the block-breaking helper.
    private static final float BLUE_FULL_BLOCK_POWER = 4.0f;
    // NBT key storing the Blue full-charge orb UUID currently locking a target in the addon hold state.
    private static final String BLUE_FULL_LOCK_UUID = "addon_blue_lock_uuid";
    // NBT flag marking whether the target is currently inside Blue's locked hold phase.
    private static final String BLUE_FULL_LOCKED = "addon_blue_locked";
    // NBT flag marking that this addon explicitly enabled NoGravity for a Blue full-charge lock and must therefore restore gravity during cleanup.
    private static final String BLUE_FULL_LOCK_GRAVITY_MANAGED = "addon_blue_lock_gravity_managed";
    // NBT key storing the last server tick where the Blue full-charge lock was actively refreshed.
    private static final String BLUE_FULL_LOCK_LAST_SEEN_TICK = "addon_blue_lock_last_seen_tick";
    // Distance where a target first enters Blue's full-charge lock state.
    private static final double BLUE_FULL_LOCK_ENTER_RADIUS = 1.6;
    // Distance where a locked target is considered to have truly escaped and should be released without threshold jitter.
    private static final double BLUE_FULL_LOCK_RELEASE_RADIUS = 2.6;
    // Maximum speed applied while Blue steers a locked target toward the hold anchor.
    private static final double BLUE_FULL_LOCK_MAX_SPEED = 0.32;
    // Maximum velocity magnitude applied during Blue's non-locked pull phase.
    private static final double BLUE_FULL_PULL_MAX_SPEED = 0.9;
    // Minimum horizontal spacing preserved between the owner and a Blue-held target.
    private static final double BLUE_FULL_OWNER_CLEARANCE = 2.2;
    // Failsafe timeout before an unrefreshed Blue lock is treated as orphaned and forcibly cleaned up.
    private static final int BLUE_FULL_LOCK_ORPHAN_TIMEOUT_TICKS = 8;
    // Light repeated-pass damping retained for direct owner-rank scaling on sustained Infinity-technique hits.
    private static final double DIRECT_RANK_PASS_FALLOFF = 0.25;
    // Minimum repeated-pass bonus share retained so follow-up hits keep most of the owner's real rank scaling.
    private static final double DIRECT_RANK_PASS_MIN_RETENTION = 0.8;
    // Slight edge so addon normal Red stays a bit stronger than crouch Red at the same rank.
    private static final double NORMAL_RED_DAMAGE_ADVANTAGE = 1.08;
    // Flight distance is normalized against this cap before Red's normal-mode bonus is fully applied.
    private static final double NORMAL_RED_DISTANCE_BONUS_MAX_DISTANCE = 60.0;
    // Maximum extra damage multiplier granted to normal Red after flying the full bonus distance.
    private static final double NORMAL_RED_DISTANCE_DAMAGE_BONUS = 1.5;
    // Maximum bonus added to normal Red explosion radii from long flight.
    private static final double NORMAL_RED_DISTANCE_RADIUS_BONUS = 0.3;
    // Maximum bonus added to normal Red explosion knockback from long flight.
    private static final double NORMAL_RED_DISTANCE_KNOCKBACK_BONUS = 0.4;
    // Gojo double-shift teleport: cursed energy cost paid up front when the blink is armed.
    private static final double GOJO_TELEPORT_CE_COST = 60.0;
    // Gojo double-shift teleport cooldown after the delayed blink resolves.
    private static final int GOJO_TELEPORT_COOLDOWN_TICKS = 60;
    // Maximum ticks between two crouch rising edges for the double-shift trigger.
    private static final int GOJO_TELEPORT_DOUBLE_SHIFT_WINDOW = 12;
    // Startup delay before the server moves the player to the calculated destination.
    private static final int GOJO_TELEPORT_DELAY_TICKS = 5;
    // Maximum server-side raycast distance in the player's look direction.
    private static final double GOJO_TELEPORT_RANGE = 18.0;
    // Short lockout so the first crouch ticks used to arm teleport do not immediately over-feed Infinity Crusher.
    private static final int GOJO_TELEPORT_CRUSHER_SUPPRESS_TICKS = 4;
    private static final String GOJO_TP_CD = "addon_gojo_tp_cd";
    private static final String GOJO_TP_LAST_SHIFT_TICK = "addon_gojo_tp_last_shift_tick";
    private static final String GOJO_TP_PENDING = "addon_gojo_tp_pending";
    private static final String GOJO_TP_EXECUTE_TICK = "addon_gojo_tp_execute_tick";
    private static final String GOJO_TP_TARGET_X = "addon_gojo_tp_target_x";
    private static final String GOJO_TP_TARGET_Y = "addon_gojo_tp_target_y";
    private static final String GOJO_TP_TARGET_Z = "addon_gojo_tp_target_z";
    private static final String GOJO_TP_YAW = "addon_gojo_tp_yaw";
    private static final String GOJO_TP_PITCH = "addon_gojo_tp_pitch";
    private static final String GOJO_TP_CRUSHER_SUPPRESS_UNTIL = "addon_gojo_tp_crusher_suppress_until";
    private static final List<BlackFlashBlessingWaveSession> BF_BLESSING_WAVE_SESSIONS = new ArrayList<>();

    /**
     * Initializes the addon mod, registers custom entities, subscribes Forge event listeners, and prepares the networking channel.
     */
    public BlueRedPurpleNukeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LimbEntityRegistry.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(BlueRedPurpleNukeMod::registerEntityAttributes);
        MinecraftForge.EVENT_BUS.register((Object)this);
        MinecraftForge.EVENT_BUS.register(ClashSubsystem.getInstance().tickHandler());
        ModNetworking.register();
    }

    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(LimbEntityRegistry.YUTA_FAKE_PLAYER.get(), Villager.createAttributes().build());
    }

    @SubscribeEvent
    // ===== FORGE EVENT REGISTRATION =====
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = (CommandDispatcher<CommandSourceStack>)event.getDispatcher();
        DomainMasteryCommands.register(dispatcher);
        YutaFakePlayerCommands.register(dispatcher);
        dispatcher.register(Commands.literal("jjkbrp_bf_reset_timing_cd").requires(src -> src.hasPermission(0)).executes(ctx -> BlueRedPurpleNukeMod.resetBlackFlashTimingCooldown(ctx.getSource())));
        dispatcher.register(Commands.literal("jjkbrp_bf_test_effects").requires(src -> src.hasPermission(0))
                .then(Commands.literal("completion").executes(ctx -> BlueRedPurpleNukeMod.testBlackFlashCompletionEffects(ctx.getSource())))
                .then(Commands.literal("blackflash").executes(ctx -> BlueRedPurpleNukeMod.testBlackFlashHudFeedback(ctx.getSource(), true, true)))
                .then(Commands.literal("released").executes(ctx -> BlueRedPurpleNukeMod.testBlackFlashHudFeedback(ctx.getSource(), true, false)))
                .then(Commands.literal("failed").executes(ctx -> BlueRedPurpleNukeMod.testBlackFlashHudFeedback(ctx.getSource(), false, false)))
                .then(Commands.literal("ring").executes(ctx -> BlueRedPurpleNukeMod.testBlackFlashTimingRing(ctx.getSource()))));
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ClashSubsystem.getInstance().onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClashSubsystem.getInstance().onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() != null) {
            ClashSubsystem.getInstance().onLivingDeath(event.getEntity());
        }
    }
    private static int resetBlackFlashTimingCooldown(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CompoundTag data = player.getPersistentData();
            data.putInt("addon_bf_flow_cd", 0);
            data.putInt("addon_bf_flow", 0);
            data.putBoolean("addon_bf_guaranteed", false);
            data.putBoolean("addon_bf_waiting_hit", false);
            data.putBoolean("addon_bf_released", false);
            data.putBoolean("addon_bf_timing_resolved", false);
            data.putBoolean("addon_bf_resolved_cooldown", false);
            data.putBoolean("addon_bf_no_retrigger_until_cnt6_drop", false);
            data.remove("addon_bf_no_retrigger_tick");
            BlueRedPurpleNukeMod.clearBlackFlashRuntimeState(player);
            ModNetworking.sendBlackFlashSync(player);
            source.sendSuccess(() -> Component.literal("Reset Black Flash timing cooldown and flow."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int testBlackFlashCompletionEffects(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CompoundTag data = player.getPersistentData();
            data.putInt("addon_bf_flow", BF_FLOW_MAX);
            data.putInt("addon_bf_flow_cd", 0);
            ModNetworking.sendBlackFlashSync(player);
            BlueRedPurpleNukeMod.playBlackFlashFlowBlessingEffects(player);
            source.sendSuccess(() -> Component.literal("Played Black Flash 8/8 completion effect."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int testBlackFlashHudFeedback(CommandSourceStack source, boolean success, boolean confirmedHit) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CompoundTag data = player.getPersistentData();
            data.putInt("addon_bf_flow", confirmedHit ? BF_FLOW_MAX : Math.max(1, data.getInt("addon_bf_flow")));
            data.putInt("addon_bf_flow_cd", 0);
            ModNetworking.sendBlackFlashSync(player);
            ModNetworking.sendBlackFlashFeedback(player, success, confirmedHit);
            source.sendSuccess(() -> Component.literal("Played Black Flash HUD feedback: success=" + success + ", confirmedHit=" + confirmedHit + "."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int testBlackFlashTimingRing(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CompoundTag data = player.getPersistentData();
            long nowTick = player.serverLevel().getGameTime();
            data.putInt("addon_bf_flow", Math.max(1, data.getInt("addon_bf_flow")));
            data.putInt("addon_bf_flow_cd", 0);
            data.putBoolean("addon_bf_charging", true);
            BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_RING_ACTIVE);
            data.putLong("addon_bf_timing_start_tick", nowTick);
            data.putFloat("addon_bf_timing_period_ticks", BF_TIMING_PERIOD_TICKS);
            data.putFloat("addon_bf_timing_red_start", 0.72f);
            data.putFloat("addon_bf_timing_red_size", BF_TIMING_RED_SIZE);
            data.putLong("addon_bf_timing_nonce", nowTick == 0L ? 1L : nowTick);
            ModNetworking.sendBlackFlashSync(player);
            source.sendSuccess(() -> Component.literal("Started Black Flash timing ring test."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
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
            UUID playerId = player2.getUUID();
            BlueRedPurpleNukeMod.resetInfinityCrusher(player2);
            BlueRedPurpleNukeMod.clearGojoTeleportRuntimeState(player2);
            BlueRedPurpleNukeMod.clearBlackFlashRuntimeState(player2);
            BlueRedPurpleNukeMod.removeBlackFlashBlessingWaveSessions(playerId);
            LimbGameplayHandler.forgetPlayer(playerId);
        }
    }

    // ===== SERVER TICK HELPERS =====

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        BlueRedPurpleNukeMod.tickBlackFlashBlessingWaveSessions();
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
        BlueRedPurpleNukeMod.handleBlueFullChargeLockTick(entity);
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
    public void onLivingAttackForBlackFlashConfirm(LivingAttackEvent event) {
        if (event == null || event.getEntity() == null || event.getEntity().level().isClientSide() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        BlueRedPurpleNukeMod.tryConfirmBlackFlashDamageHit(target, attacker, direct, "living_attack");
    }

    @SubscribeEvent
    public void onLivingHurtForBlackFlashConfirm(LivingHurtEvent event) {
        if (event == null || event.getEntity() == null || event.getEntity().level().isClientSide() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        BlueRedPurpleNukeMod.tryConfirmBlackFlashDamageHit(target, attacker, direct, "living_hurt");
    }

    @SubscribeEvent
    public void onLivingDamageForBlackFlashConfirm(LivingDamageEvent event) {
        if (event == null || event.getEntity() == null || event.getEntity().level().isClientSide() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        BlueRedPurpleNukeMod.tryConfirmBlackFlashDamageHit(target, attacker, direct, "living_damage");
    }

    @SubscribeEvent
    public void onAttackEntityForBlackFlashConfirm(AttackEntityEvent event) {
        if (event == null || event.getEntity() == null || event.getTarget() == null || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer owner) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        BlueRedPurpleNukeMod.tryConfirmBlackFlashCombatHit(owner, target, "attack_entity");
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
        boolean teleportConsumedOrSuppressesCrusher = BlueRedPurpleNukeMod.handleGojoDoubleShiftTeleport(player2);
        if (!teleportConsumedOrSuppressesCrusher) {
            BlueRedPurpleNukeMod.handleInfinityCrusher(player2);
        }
        BlueRedPurpleNukeMod.tickBlackFlashFlowCooldown(player2);
        BlueRedPurpleNukeMod.handleBlackFlashCharge(player2);
        BlueRedPurpleNukeMod.expireBlackFlashGuaranteeIfNeeded(player2, player2.getPersistentData());
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
        double cnt6 = data.getDouble("cnt6");
        double skillTag = data.getDouble("skill");
        boolean resetReached = BlueRedPurpleNukeMod.shouldClearBlackFlashNoRetriggerLock(data, cnt6, skillTag);
        String state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        if (resetReached && BF_STATE_WAITING_HIT.equals(state)) {
            BlueRedPurpleNukeMod.expireBlackFlashGuaranteeIfNeeded(player, data);
            return;
        }
        if (resetReached && !BF_STATE_IDLE.equals(state)) {
            LOGGER.warn("[BlackFlashState] transition player={} {}->IDLE reason=charge_reset cnt6={} skill={}", new Object[]{player.getName().getString(), state, cnt6, skillTag});
            BlueRedPurpleNukeMod.closeBlackFlashPendingWithFailure(player, data, "charge_reset");
            BlueRedPurpleNukeMod.clearBlackFlashCastGates(data);
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            ModNetworking.sendBlackFlashSync(player);
            state = BF_STATE_IDLE;
        }
        if (BlueRedPurpleNukeMod.isBlackFlashFlowCooldownActive(player)) {
            if (!BF_STATE_LOCKED_UNTIL_RESET.equals(state)) {
                BlueRedPurpleNukeMod.closeBlackFlashPendingWithFailure(player, data, "flow_cooldown_active");
                BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
                BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
                BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
                ModNetworking.sendBlackFlashSync(player);
            }
            return;
        }
        BlueRedPurpleNukeMod.expireBlackFlashGuaranteeIfNeeded(player, data);
        state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        boolean pressZ = data.getBoolean("PRESS_Z");
        boolean lastPressZ = data.contains("addon_bf_last_press_z") ? data.getBoolean("addon_bf_last_press_z") : pressZ;
        data.putBoolean("addon_bf_last_press_z", pressZ);
        boolean ringActive = BF_STATE_RING_ACTIVE.equals(state) && data.getBoolean("addon_bf_charging");
        if (selectId > 2) {
            if (ringActive) {
                BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, false);
            }
            return;
        }
        if (ringActive && lastPressZ && !pressZ && data.getLong("addon_bf_timing_nonce") != 0L) {
            long elapsed = player.serverLevel().getGameTime() - data.getLong("addon_bf_timing_start_tick");
            float serverNeedle = BlueRedPurpleNukeMod.computeBlackFlashNeedle(elapsed, 0.0f, BlueRedPurpleNukeMod.getBlackFlashTimingPeriodTicks(data));
            boolean serverTimingSuccess = BlueRedPurpleNukeMod.isBlackFlashNeedleInRedArc(serverNeedle, BlueRedPurpleNukeMod.frac(data.getFloat("addon_bf_timing_red_start")), Math.max(0.01f, data.getFloat("addon_bf_timing_red_size")), BF_TIMING_EDGE_TOLERANCE);
            LOGGER.warn("[BlackFlashReleaseDiag] server detected OG PRESS_Z release edge player={} state={} nonce={} needle={} elapsed={} success={}", new Object[]{player.getName().getString(), state, data.getLong("addon_bf_timing_nonce"), serverNeedle, elapsed, serverTimingSuccess});
            BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, true, serverNeedle, (float)elapsed, data.getLong("addon_bf_timing_nonce"), serverTimingSuccess);
            return;
        }
        if (!BF_STATE_IDLE.equals(state)) {
            return;
        }
        double requiredCharge = BlueRedPurpleNukeMod.getBlackFlashRequiredCharge(data);
        if (cnt6 >= requiredCharge && skillTag != 0.0 && data.getInt("addon_bf_flow_cd") <= 0) {
            BlueRedPurpleNukeMod.startBlackFlashTiming(player, data);
        }
    }

    private static boolean shouldClearBlackFlashNoRetriggerLock(CompoundTag data, double cnt6, double skillTag) {
        return skillTag == 0.0 || cnt6 <= BF_CHARGE_RETRIGGER_RESET_CNT6;
    }

    private static String getBlackFlashState(CompoundTag data) {
        return data == null ? BF_STATE_IDLE : BlueRedPurpleNukeMod.normalizeBlackFlashState(data.getString("addon_bf_state"));
    }

    private static String normalizeBlackFlashState(String state) {
        if (BF_STATE_RING_ACTIVE.equals(state) || BF_STATE_WAITING_HIT.equals(state) || BF_STATE_LOCKED_UNTIL_RESET.equals(state)) {
            return state;
        }
        if ("RESOLVED_LOCK".equals(state)) {
            return BF_STATE_LOCKED_UNTIL_RESET;
        }
        return BF_STATE_IDLE;
    }

    private static void setBlackFlashState(CompoundTag data, String state) {
        data.putString("addon_bf_state", BlueRedPurpleNukeMod.normalizeBlackFlashState(state));
    }

    private static void clearBlackFlashCastGates(CompoundTag data) {
        data.putBoolean("addon_bf_resolved_cooldown", false);
        data.putBoolean("addon_bf_block_until_charge_released", false);
        data.putBoolean("addon_bf_no_retrigger_until_cnt6_drop", false);
        data.putBoolean("addon_bf_released", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        data.putBoolean("addon_bf_timing_resolved", false);
        data.putBoolean("addon_bf_guaranteed", false);
        data.putBoolean("addon_bf_timed_feedback_sent", false);
        data.putBoolean("addon_bf_expire_feedback_sent", false);
        data.remove("addon_bf_release_tick");
        data.remove("addon_bf_guarantee_nonce");
        data.remove("addon_bf_recent_hit_tick");
        data.remove("addon_bf_recent_hit_entity_id");
        data.remove("addon_bf_recent_hit_source");
        data.remove("addon_bf_resolved_cooldown_tick");
        data.remove("addon_bf_resolved_cnt6");
        data.remove("addon_bf_no_retrigger_tick");
        data.putBoolean("addon_bf_seen_charge_reset", false);
        BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_IDLE);
    }

    private static void armBlackFlashResolvedCastGate(CompoundTag data, long nowTick) {
        data.putBoolean("addon_bf_resolved_cooldown", true);
        data.putBoolean("addon_bf_block_until_charge_released", true);
        data.putBoolean("addon_bf_no_retrigger_until_cnt6_drop", true);
        data.putLong("addon_bf_resolved_cooldown_tick", nowTick);
        data.putLong("addon_bf_no_retrigger_tick", nowTick);
        data.putDouble("addon_bf_resolved_cnt6", data.getDouble("cnt6"));
        data.putBoolean("addon_bf_seen_charge_reset", false);
        BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_LOCKED_UNTIL_RESET);
    }

    private static double getBlackFlashRequiredCharge(CompoundTag data) {
        int flow = data == null ? 0 : Math.max(0, Math.min(BF_FLOW_MAX, data.getInt("addon_bf_flow")));
        // Red/Blue charge state in the base mod caps around cnt6=5.0. Keep ring start under that cap,
        // and make higher flow feel faster by shaving a small amount per confirmed Black Flash.
        return Math.max(BF_CHARGE_REQUIRED_MIN, BF_CHARGE_REQUIRED_BASE - (double)flow * BF_CHARGE_REQUIRED_REDUCTION_PER_FLOW);
    }

    private static void startBlackFlashTiming(ServerPlayer player, CompoundTag data) {
        String state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        if (!BF_STATE_IDLE.equals(state) || data.getBoolean("addon_bf_no_retrigger_until_cnt6_drop")) {
            LOGGER.warn("[BlackFlashReleaseDiag] blocked timing start player={} state={} cnt6={} skill={} noRetrigger={}", new Object[]{player.getName().getString(), state, data.getDouble("cnt6"), data.getDouble("skill"), data.getBoolean("addon_bf_no_retrigger_until_cnt6_drop")});
            return;
        }
        BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_RING_ACTIVE);
        data.putBoolean("addon_bf_seen_charge_reset", false);
        data.putBoolean("addon_bf_charging", true);
        data.putBoolean("addon_bf_released", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        data.putBoolean("addon_bf_guaranteed", false);
        data.putBoolean("addon_bf_timing_resolved", false);
        data.putBoolean("addon_bf_timed_feedback_sent", false);
        data.putBoolean("addon_bf_expire_feedback_sent", false);
        boolean yuji = BlueRedPurpleNukeMod.isYuji(player);
        data.putLong("addon_bf_timing_nonce", data.getLong("addon_bf_timing_nonce") + 1L);
        data.putLong("addon_bf_timing_start_tick", player.serverLevel().getGameTime());
        float basePeriod = yuji ? BF_TIMING_PERIOD_TICKS_YUJI : BF_TIMING_PERIOD_TICKS;
        int flow = Math.max(0, Math.min(BF_FLOW_MAX, data.getInt("addon_bf_flow")));
        data.putFloat("addon_bf_timing_period_ticks", Math.max(6.0f, basePeriod - (float)flow * (yuji ? 0.65f : 0.85f)));
        float redSize = yuji ? BF_TIMING_RED_SIZE_YUJI : BF_TIMING_RED_SIZE;
        data.putFloat("addon_bf_timing_red_start", BlueRedPurpleNukeMod.chooseBlackFlashRedStartAwayFromInitialNeedle(player, redSize));
        data.putFloat("addon_bf_timing_red_size", redSize);
        if (!data.getBoolean("addon_bf_charge_announced")) {
            data.putBoolean("addon_bf_charge_announced", true);
        }
        LOGGER.warn("[BlackFlashTimingDiag] START nonce={} state={} flow={} redStart={} redSize={} period={} startTick={}", new Object[]{data.getLong("addon_bf_timing_nonce"), BlueRedPurpleNukeMod.getBlackFlashState(data), flow, data.getFloat("addon_bf_timing_red_start"), data.getFloat("addon_bf_timing_red_size"), data.getFloat("addon_bf_timing_period_ticks"), data.getLong("addon_bf_timing_start_tick")});
        ModNetworking.sendBlackFlashSync(player);
    }

    public static void resolveBlackFlashTimingRelease(ServerPlayer player, boolean releasedFromCharge) {
        BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, releasedFromCharge, Float.NaN, 0L, false);
    }

    public static void resolveBlackFlashTimingRelease(ServerPlayer player, boolean releasedFromCharge, float clientNeedle) {
        BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, releasedFromCharge, clientNeedle, 0L, false);
    }

    public static void resolveBlackFlashTimingRelease(ServerPlayer player, boolean releasedFromCharge, float clientNeedle, long clientNonce) {
        BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, releasedFromCharge, clientNeedle, clientNonce, false);
    }

    public static void resolveBlackFlashTimingRelease(ServerPlayer player, boolean releasedFromCharge, float clientNeedle, long clientNonce, boolean clientTimingSuccess) {
        BlueRedPurpleNukeMod.resolveBlackFlashTimingRelease(player, releasedFromCharge, clientNeedle, Float.NaN, clientNonce, clientTimingSuccess);
    }

    public static void resolveBlackFlashTimingRelease(ServerPlayer player, boolean releasedFromCharge, float clientNeedle, float clientElapsedTicks, long clientNonce, boolean clientTimingSuccess) {
        CompoundTag data = player.getPersistentData();
        String state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        long serverNonce = data.getLong("addon_bf_timing_nonce");
        boolean charging = data.getBoolean("addon_bf_charging");
        boolean sameNonce = clientNonce != 0L && clientNonce == serverNonce;
        if (!releasedFromCharge || !BF_STATE_RING_ACTIVE.equals(state) || !charging) {
            String rejectReason = !releasedFromCharge ? "not_released_from_charge" : (!BF_STATE_RING_ACTIVE.equals(state) ? "state_" + state.toLowerCase(Locale.ROOT) : "not_charging");
            LOGGER.warn("[BlackFlashTimingDiag] RELEASE_REJECT reason={} nonce={} clientNeedle={} state={} age={}", new Object[]{rejectReason, clientNonce != 0L ? clientNonce : serverNonce, clientNeedle, state, BlueRedPurpleNukeMod.getBlackFlashTimingAge(player, data)});
            LOGGER.warn("[BlackFlashReleaseDiag] invalid/stale release rejected player={} state={} releasedFromCharge={} charging={} clientNonce={} serverNonce={} sameNonce={}", new Object[]{player.getName().getString(), state, releasedFromCharge, charging, clientNonce, serverNonce, sameNonce});
            if (BF_STATE_RING_ACTIVE.equals(state) && !releasedFromCharge) {
                BlueRedPurpleNukeMod.handleBlackFlashFlowFailure(player, data, "not_released_from_charge");
                BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, "not_released_from_charge");
                BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
            } else if (sameNonce) {
                BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, "release_rejected_state_" + state.toLowerCase(Locale.ROOT));
            }
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            ModNetworking.sendBlackFlashSync(player);
            return;
        }
        if (clientNonce != serverNonce || serverNonce == 0L) {
            LOGGER.warn("[BlackFlashTimingDiag] RELEASE_REJECT reason=nonce_mismatch nonce={} clientNeedle={} state={} age={}", new Object[]{clientNonce, clientNeedle, state, BlueRedPurpleNukeMod.getBlackFlashTimingAge(player, data)});
            LOGGER.warn("[BlackFlashReleaseDiag] stale release packet sync-clear player={} state={} clientNonce={} serverNonce={} charging={}", new Object[]{player.getName().getString(), state, clientNonce, serverNonce, charging});
            BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, "release_nonce_mismatch");
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            ModNetworking.sendBlackFlashSync(player);
            return;
        }
        float redSize = Math.max(0.01f, data.getFloat("addon_bf_timing_red_size"));
        float redStart = BlueRedPurpleNukeMod.frac(data.getFloat("addon_bf_timing_red_start"));
        long nowTick = player.serverLevel().getGameTime();
        long timingStartTick = data.getLong("addon_bf_timing_start_tick");
        long elapsed = nowTick - timingStartTick;
        boolean clientElapsedProvided = !Float.isNaN(clientElapsedTicks) && !Float.isInfinite(clientElapsedTicks) && clientElapsedTicks >= 0.0f;
        float observedReleaseElapsed = clientElapsedProvided ? clientElapsedTicks : (float)elapsed;
        if (observedReleaseElapsed >= 0.0f && observedReleaseElapsed < (float)BF_TIMING_MIN_RELEASE_AGE_TICKS) {
            LOGGER.warn("[BlackFlashTimingDiag] RELEASE_REJECT reason=too_early nonce={} clientNeedle={} clientElapsed={} state={} age={}", new Object[]{serverNonce, clientNeedle, clientElapsedTicks, state, elapsed});
            LOGGER.warn("[BlackFlashReleaseDiag] early release ignored player={} nonce={} age={} observedReleaseElapsed={} minAge={} redStart={} redSize={}", new Object[]{player.getName().getString(), serverNonce, elapsed, observedReleaseElapsed, BF_TIMING_MIN_RELEASE_AGE_TICKS, redStart, redSize});
            BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, "release_too_early");
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, nowTick);
            ModNetworking.sendBlackFlashSync(player);
            return;
        }
        float periodTicks = BlueRedPurpleNukeMod.getBlackFlashTimingPeriodTicks(data);
        float serverNeedle = BlueRedPurpleNukeMod.computeBlackFlashNeedle(elapsed, 0.0f, periodTicks);
        float normalizedClientNeedle = BlueRedPurpleNukeMod.frac(clientNeedle);
        boolean clientProvided = !Float.isNaN(clientNeedle) && !Float.isInfinite(clientNeedle);
        float oneWayLatencyTicks = BlueRedPurpleNukeMod.estimateBlackFlashOneWayLatencyTicks(player);
        float latencyWindow = Math.max(BF_TIMING_LATENCY_JITTER_TICKS, oneWayLatencyTicks + BF_TIMING_LATENCY_JITTER_TICKS);
        boolean staleByAge = observedReleaseElapsed < 0.0f || observedReleaseElapsed > 80.0f || elapsed < -1L || (float)elapsed > 80.0f + latencyWindow;
        float clientSelfCheckTolerance = Math.max(BF_TIMING_EDGE_TOLERANCE, 0.018f);
        boolean clientVisiblyInsideRed = clientProvided && BlueRedPurpleNukeMod.isBlackFlashNeedleInRedArc(normalizedClientNeedle, redStart, redSize, clientSelfCheckTolerance);
        boolean serverInsideRed = BlueRedPurpleNukeMod.isBlackFlashNeedleInRedArc(serverNeedle, redStart, redSize, BF_TIMING_EDGE_TOLERANCE);
        float clientElapsedNeedle = clientElapsedProvided ? BlueRedPurpleNukeMod.computeBlackFlashNeedle(clientElapsedTicks, periodTicks) : normalizedClientNeedle;
        boolean clientElapsedInsideRed = clientElapsedProvided && BlueRedPurpleNukeMod.isBlackFlashNeedleInRedArc(clientElapsedNeedle, redStart, redSize, clientSelfCheckTolerance);
        float drift = clientProvided ? BlueRedPurpleNukeMod.circularDistance(normalizedClientNeedle, serverNeedle) : 1.0f;
        float packetNeedleElapsedDrift = clientProvided && clientElapsedProvided ? BlueRedPurpleNukeMod.circularDistance(normalizedClientNeedle, clientElapsedNeedle) : 0.0f;
        boolean packetNeedleMatchesElapsed = !clientProvided || !clientElapsedProvided || packetNeedleElapsedDrift <= BF_TIMING_PACKET_NEEDLE_TOLERANCE;
        boolean elapsedPlausible = !clientElapsedProvided || clientElapsedTicks <= (float)elapsed + Math.max(2.0f, oneWayLatencyTicks + 2.0f);
        boolean clientSelfConsistent = clientTimingSuccess == clientVisiblyInsideRed;
        boolean driftExtreme = drift > BF_TIMING_EXTREME_DRIFT_TOLERANCE;
        boolean clientVisibleSuccess = clientProvided && clientTimingSuccess && clientVisiblyInsideRed && clientSelfConsistent;
        boolean serverTimingAccept = serverInsideRed && (!clientProvided || drift <= BF_TIMING_CLIENT_DRIFT_TOLERANCE || clientElapsedInsideRed);
        boolean success = !staleByAge && elapsedPlausible && (clientVisibleSuccess || clientElapsedInsideRed || serverTimingAccept);
        String reason = success ? (clientVisibleSuccess ? "client_visible_window" : (clientElapsedInsideRed ? "client_elapsed_window" : "server_window")) : (staleByAge ? "stale_age" : (!elapsedPlausible ? "client_elapsed_implausible" : (!clientProvided ? "no_client_needle" : (!clientTimingSuccess && !clientElapsedInsideRed && !serverInsideRed ? "outside_all_windows" : (!clientSelfConsistent ? "client_success_mismatch" : "window_rejected")))));
        LOGGER.warn("[BlackFlashTimingDiag] {} reason={} nonce={} clientNeedle={} clientElapsed={} state={} age={} latencyMs={}", new Object[]{success ? "RELEASE_ACCEPT" : "RELEASE_REJECT", reason, serverNonce, normalizedClientNeedle, clientElapsedTicks, state, elapsed, player.latency});
        LOGGER.warn("[BlackFlashState] release player={} nonce={} {}->{} success={} clientTimingSuccess={} clientNeedle={} clientElapsed={} clientElapsedNeedle={} clientVisiblyInsideRed={} clientElapsedInsideRed={} clientSelfConsistent={} packetNeedleMatchesElapsed={} elapsedPlausible={} serverNeedle={} serverInsideRed={} serverTimingAccept={} drift={} driftExtreme={} packetNeedleElapsedDrift={} latencyTicks={} redStart={} redSize={}", new Object[]{player.getName().getString(), serverNonce, state, success ? BF_STATE_WAITING_HIT : BF_STATE_LOCKED_UNTIL_RESET, success, clientTimingSuccess, normalizedClientNeedle, clientElapsedTicks, clientElapsedNeedle, clientVisiblyInsideRed, clientElapsedInsideRed, clientSelfConsistent, packetNeedleMatchesElapsed, elapsedPlausible, serverNeedle, serverInsideRed, serverTimingAccept, drift, driftExtreme, packetNeedleElapsedDrift, oneWayLatencyTicks, redStart, redSize});
        BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
        data.putBoolean("addon_bf_timing_resolved", true);
        data.putBoolean("addon_bf_released", true);
        data.putLong("addon_bf_last_resolved_nonce", serverNonce);
        if (success) {
            data.putBoolean("addon_bf_waiting_hit", true);
            data.putBoolean("addon_bf_guaranteed", true);
            data.putLong("addon_bf_release_tick", nowTick);
            data.putLong("addon_bf_guarantee_nonce", serverNonce);
            data.putBoolean("addon_bf_timed_feedback_sent", true);
            BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_WAITING_HIT);
            if (BlueRedPurpleNukeMod.tryConfirmRecentBlackFlashHit(player, data, timingStartTick, nowTick, serverNonce, "release_recent_server_hit")) {
                return;
            }
            ModNetworking.sendBlackFlashFeedback(player, true, false);
        } else {
            data.putBoolean("addon_bf_waiting_hit", false);
            data.putBoolean("addon_bf_guaranteed", false);
            data.remove("addon_bf_release_tick");
            data.remove("addon_bf_guarantee_nonce");
            BlueRedPurpleNukeMod.handleBlackFlashFlowFailure(player, data, "release_missed_window");
            BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, "release_missed_window");
            BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, nowTick);
        }
        ModNetworking.sendBlackFlashSync(player);
    }

    private static float computeBlackFlashNeedle(long elapsedTicks, float partialTick, float periodTicks) {
        return BlueRedPurpleNukeMod.frac(((float)Math.max(0L, elapsedTicks) + partialTick) / Math.max(1.0f, periodTicks));
    }

    private static float computeBlackFlashNeedle(float elapsedTicks, float periodTicks) {
        return BlueRedPurpleNukeMod.frac(Math.max(0.0f, elapsedTicks) / Math.max(1.0f, periodTicks));
    }

    private static float getBlackFlashTimingPeriodTicks(CompoundTag data) {
        return data != null && data.contains("addon_bf_timing_period_ticks") ? Math.max(1.0f, data.getFloat("addon_bf_timing_period_ticks")) : BF_TIMING_PERIOD_TICKS;
    }

    private static float estimateBlackFlashOneWayLatencyTicks(ServerPlayer player) {
        int latencyMs = player == null ? 0 : Math.max(0, player.latency);
        return Math.min(BF_TIMING_MAX_LATENCY_COMP_TICKS, (float)latencyMs / 100.0f);
    }

    private static float chooseBlackFlashRedStartAwayFromInitialNeedle(ServerPlayer player, float redSize) {
        float randomStart = player != null ? player.getRandom().nextFloat() : 0.5f;
        float safeSize = Math.max(0.01f, Math.min(0.95f, redSize));
        float minSep = Math.max(BF_TIMING_INITIAL_NEEDLE_MIN_SEPARATION, safeSize + 0.06f);
        float distFromNeedleZero = Math.min(BlueRedPurpleNukeMod.frac(randomStart), 1.0f - BlueRedPurpleNukeMod.frac(randomStart + safeSize));
        if (distFromNeedleZero >= minSep) {
            return BlueRedPurpleNukeMod.frac(randomStart);
        }
        float safeSpan = Math.max(0.02f, 1.0f - safeSize - minSep * 2.0f);
        return BlueRedPurpleNukeMod.frac(minSep + (player != null ? player.getRandom().nextFloat() : 0.5f) * safeSpan);
    }

    private static boolean isYuji(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double activeTech = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        return (int)Math.round(activeTech) == 21;
    }

    public static boolean isBlackFlashNeedleInRedArc(float needle, float redStart, float redSize, float tolerance) {
        float clampedSize = Math.max(0.01f, Math.min(1.0f, redSize));
        float edgeTolerance = Math.max(0.0f, tolerance);
        float delta = BlueRedPurpleNukeMod.frac(needle - redStart);
        return delta <= clampedSize + edgeTolerance || delta >= 1.0f - edgeTolerance;
    }

    private static float circularDistance(float a, float b) {
        float delta = Math.abs(BlueRedPurpleNukeMod.frac(a) - BlueRedPurpleNukeMod.frac(b));
        return Math.min(delta, 1.0f - delta);
    }

    private static float frac(float value) {
        return value - (float)Math.floor(value);
    }

    private static long getBlackFlashTimingAge(ServerPlayer player, CompoundTag data) {
        if (player == null || data == null || !data.contains("addon_bf_timing_start_tick")) {
            return -1L;
        }
        return player.serverLevel().getGameTime() - data.getLong("addon_bf_timing_start_tick");
    }

    private static void clearBlackFlashTiming(CompoundTag data) {
        data.putBoolean("addon_bf_charging", false);
        data.putBoolean("addon_bf_charge_announced", false);
        data.putBoolean("addon_bf_release_pending", false);
        data.remove("addon_bf_release_pending_tick");
        data.remove("addon_bf_timing_start_tick");
        data.remove("addon_bf_timing_period_ticks");
        data.remove("addon_bf_timing_red_start");
        data.remove("addon_bf_timing_red_size");
    }

    public static void clearBlackFlashRuntimeState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putInt("addon_bf_flow_cd", 0);
        data.putInt("addon_bf_flow", 0);
        data.putBoolean("addon_bf_guaranteed", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        data.putBoolean("addon_bf_released", false);
        data.putBoolean("addon_bf_timing_resolved", false);
        data.putBoolean("addon_bf_resolved_cooldown", false);
        data.putBoolean("addon_bf_no_retrigger_until_cnt6_drop", false);
        data.putBoolean("addon_bf_block_until_charge_released", false);
        data.putBoolean("addon_bf_timed_feedback_sent", false);
        data.putBoolean("addon_bf_expire_feedback_sent", false);
        data.putBoolean("addon_bf_seen_charge_reset", false);
        data.remove("addon_bf_release_tick");
        data.remove("addon_bf_guarantee_nonce");
        data.remove("addon_bf_recent_hit_tick");
        data.remove("addon_bf_recent_hit_entity_id");
        data.remove("addon_bf_recent_hit_source");
        data.remove("addon_bf_resolved_cooldown_tick");
        data.remove("addon_bf_resolved_cnt6");
        data.remove("addon_bf_no_retrigger_tick");
        BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
        BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
        BlueRedPurpleNukeMod.setBlackFlashState(data, BF_STATE_IDLE);
        ModNetworking.sendBlackFlashSync(player);
    }

    public static boolean isBlackFlashGuaranteeActive(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (BlueRedPurpleNukeMod.isBlackFlashFlowCooldownActive(player)) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        BlueRedPurpleNukeMod.expireBlackFlashGuaranteeIfNeeded(player, data);
        return BF_STATE_WAITING_HIT.equals(BlueRedPurpleNukeMod.getBlackFlashState(data)) && data.getBoolean("addon_bf_guaranteed") && data.getBoolean("addon_bf_waiting_hit") && data.contains("addon_bf_release_tick") && player.serverLevel().getGameTime() - data.getLong("addon_bf_release_tick") <= (long)BF_GUARANTEE_TIMEOUT_TICKS;
    }

    public static boolean consumeBlackFlashGuarantee(ServerPlayer player) {
        if (!BlueRedPurpleNukeMod.isBlackFlashGuaranteeActive(player)) {
            return false;
        }
        BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
        return true;
    }

    public static void clearBlackFlashGuarantee(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putBoolean("addon_bf_guaranteed", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        data.putBoolean("addon_bf_expire_feedback_sent", false);
        data.remove("addon_bf_release_tick");
        data.remove("addon_bf_guarantee_nonce");
    }

    public static void tryConfirmBlackFlashGuaranteeHit(ServerPlayer player, String source) {
        if (player == null || !BlueRedPurpleNukeMod.isBlackFlashGuaranteeActive(player)) {
            return;
        }
        LOGGER.warn("[BlackFlashReleaseDiag] direct guarantee confirm source={} player={}", new Object[]{source, player.getName().getString()});
        BlueRedPurpleNukeMod.confirmBlackFlashGuaranteeHit(player, source);
    }

    public static void tryConfirmBlackFlashCombatHit(ServerPlayer player, LivingEntity target, String source) {
        if (player == null || target == null || target == player || target.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if ("living_hurt".equals(source) || "living_damage".equals(source)) {
            BlueRedPurpleNukeMod.recordRecentBlackFlashServerHit(player, target, source);
        }
        if (!data.getBoolean("addon_bf_guaranteed") || !data.getBoolean("addon_bf_waiting_hit") || !BlueRedPurpleNukeMod.isBlackFlashGuaranteeActive(player)) {
            return;
        }
        if (!target.isAlive() && target.getHealth() <= 0.0f) {
            return;
        }
        long age = player.serverLevel().getGameTime() - data.getLong("addon_bf_release_tick");
        if (age < 0L || age > (long)BF_GUARANTEE_TIMEOUT_TICKS) {
            return;
        }
        LOGGER.warn("[BlackFlashReleaseDiag] combat guarantee confirm source={} player={} target={} age={} nonce={}", new Object[]{source, player.getName().getString(), target.getType().toString(), age, data.getLong("addon_bf_guarantee_nonce")});
        BlueRedPurpleNukeMod.confirmBlackFlashGuaranteeHit(player, source);
    }

    private static void recordRecentBlackFlashServerHit(ServerPlayer player, LivingEntity target, String source) {
        if (player == null || target == null || target == player || target.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        String state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        if (!BF_STATE_RING_ACTIVE.equals(state) && !BF_STATE_WAITING_HIT.equals(state) && !data.getBoolean("addon_bf_charging") && !data.getBoolean("addon_bf_guaranteed")) {
            return;
        }
        long nowTick = player.serverLevel().getGameTime();
        data.putLong("addon_bf_recent_hit_tick", nowTick);
        data.putInt("addon_bf_recent_hit_entity_id", target.getId());
        data.putString("addon_bf_recent_hit_source", source == null ? "" : source);
        LOGGER.warn("[BlackFlashReleaseDiag] recent server hit recorded player={} target={} tick={} state={} source={}", new Object[]{player.getName().getString(), target.getType().toString(), nowTick, state, source});
    }

    private static boolean tryConfirmRecentBlackFlashHit(ServerPlayer player, CompoundTag data, long timingStartTick, long nowTick, long serverNonce, String source) {
        if (player == null || data == null || serverNonce == 0L) {
            return false;
        }
        long hitTick = data.getLong("addon_bf_recent_hit_tick");
        if (hitTick <= 0L || hitTick < timingStartTick - 1L || nowTick - hitTick > (long)BF_RECENT_HIT_GRACE_TICKS) {
            return false;
        }
        if (!BF_STATE_WAITING_HIT.equals(BlueRedPurpleNukeMod.getBlackFlashState(data)) || !data.getBoolean("addon_bf_guaranteed") || !data.getBoolean("addon_bf_waiting_hit") || data.getLong("addon_bf_guarantee_nonce") != serverNonce) {
            return false;
        }
        LOGGER.warn("[BlackFlashReleaseDiag] confirm recent server hit player={} nonce={} hitAge={} hitSource={} hitEntityId={}", new Object[]{player.getName().getString(), serverNonce, nowTick - hitTick, data.getString("addon_bf_recent_hit_source"), data.getInt("addon_bf_recent_hit_entity_id")});
        return BlueRedPurpleNukeMod.confirmBlackFlashGuaranteeHit(player, source);
    }

    private static void tryConfirmBlackFlashDamageHit(LivingEntity target, Entity attacker, Entity direct, String source) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        ServerPlayer owner = BlueRedPurpleNukeMod.resolveBlackFlashDamageOwner(target.level(), attacker, direct, target);
        if (owner != null) {
            BlueRedPurpleNukeMod.tryConfirmBlackFlashCombatHit(owner, target, source);
        }
    }

    private static ServerPlayer resolveBlackFlashDamageOwner(Level level, Entity attacker, Entity direct, LivingEntity target) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        ServerPlayer owner = BlueRedPurpleNukeMod.resolveBlackFlashDamageOwnerFromEntity(serverLevel, attacker);
        if (owner != null) {
            return owner;
        }
        owner = BlueRedPurpleNukeMod.resolveBlackFlashDamageOwnerFromEntity(serverLevel, direct);
        if (owner != null) {
            return owner;
        }
        return BlueRedPurpleNukeMod.resolveBlackFlashWaitingOwnerNearTarget(serverLevel, target);
    }

    private static ServerPlayer resolveBlackFlashDamageOwnerFromEntity(ServerLevel serverLevel, Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        String ownerUUID = entity.getPersistentData().getString("OWNER_UUID");
        if (ownerUUID == null || ownerUUID.isEmpty()) {
            ownerUUID = entity.getPersistentData().getString("OwnerUUID");
        }
        if (ownerUUID == null || ownerUUID.isEmpty()) {
            ownerUUID = entity.getPersistentData().getString("owner_uuid");
        }
        if (ownerUUID == null || ownerUUID.isEmpty()) {
            Entity ownerEntity = entity instanceof Projectile projectile ? projectile.getOwner() : null;
            return ownerEntity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        }
        try {
            return serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(ownerUUID));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ServerPlayer resolveBlackFlashWaitingOwnerNearTarget(ServerLevel serverLevel, LivingEntity target) {
        if (serverLevel == null || target == null) {
            return null;
        }
        ServerPlayer best = null;
        double bestDist = 100.0;
        for (ServerPlayer candidate : serverLevel.players()) {
            CompoundTag data = candidate.getPersistentData();
            if (!data.getBoolean("addon_bf_guaranteed") || !data.getBoolean("addon_bf_waiting_hit") || !data.contains("addon_bf_release_tick")) {
                continue;
            }
            long age = candidate.serverLevel().getGameTime() - data.getLong("addon_bf_release_tick");
            if (age < 0L || age > (long)BF_GUARANTEE_TIMEOUT_TICKS) {
                continue;
            }
            double dist = candidate.distanceToSqr(target);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        if (best != null) {
            LOGGER.warn("[BlackFlashReleaseDiag] fallback_owner_near_target player={} target={} distSq={}", new Object[]{best.getName().getString(), target.getType().toString(), bestDist});
        }
        return best;
    }

    public static boolean confirmBlackFlashGuaranteeHit(ServerPlayer player) {
        return BlueRedPurpleNukeMod.confirmBlackFlashGuaranteeHit(player, "direct");
    }

    public static boolean confirmBlackFlashGuaranteeHit(ServerPlayer player, String source) {
        if (player == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        long confirmedNonce = data.getLong("addon_bf_guarantee_nonce");
        long confirmedAge = data.contains("addon_bf_release_tick") ? player.serverLevel().getGameTime() - data.getLong("addon_bf_release_tick") : -1L;
        long lastConfirmedNonce = data.getLong("addon_bf_last_confirmed_nonce");
        boolean stateActive = BF_STATE_WAITING_HIT.equals(BlueRedPurpleNukeMod.getBlackFlashState(data)) && data.getBoolean("addon_bf_guaranteed") && data.getBoolean("addon_bf_waiting_hit") && data.contains("addon_bf_release_tick") && confirmedNonce != 0L;
        boolean ageValid = confirmedAge >= 0L && confirmedAge <= (long)BF_GUARANTEE_TIMEOUT_TICKS;
        if (!stateActive || !ageValid || lastConfirmedNonce == confirmedNonce) {
            LOGGER.warn("[BlackFlashReleaseDiag] suppress duplicate/stale guarantee confirm source={} player={} nonce={} lastConfirmed={} age={} state={} stateActive={} ageValid={} guaranteed={} waitingHit={} flow={}", new Object[]{source, player.getName().getString(), confirmedNonce, lastConfirmedNonce, confirmedAge, BlueRedPurpleNukeMod.getBlackFlashState(data), stateActive, ageValid, data.getBoolean("addon_bf_guaranteed"), data.getBoolean("addon_bf_waiting_hit"), data.getInt("addon_bf_flow")});
            return false;
        }
        data.putLong("addon_bf_last_confirmed_nonce", confirmedNonce);
        BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
        data.putBoolean("addon_bf_timed_feedback_sent", true);
        data.putBoolean("addon_bf_expire_feedback_sent", true);
        LOGGER.warn("[BlackFlashTimingDiag] HIT_CONFIRM nonce={} source={} event={} state={} age={}", new Object[]{confirmedNonce, source, source, BlueRedPurpleNukeMod.getBlackFlashState(data), confirmedAge});
        LOGGER.warn("[BlackFlashReleaseDiag] confirmed guarantee hit source={} player={} nonce={} age={} flowBefore={}", new Object[]{source, player.getName().getString(), confirmedNonce, confirmedAge, data.getInt("addon_bf_flow")});
        BlueRedPurpleNukeMod.advanceBlackFlashFlow(player, data);
        data.putBoolean("addon_bf_released", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        data.putBoolean("addon_bf_timing_resolved", true);
        data.putBoolean("addon_bf_guaranteed", false);
        data.putBoolean("addon_bf_charging", false);
        BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
        BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
        ModNetworking.sendBlackFlashFeedback(player, true, true);
        ModNetworking.sendBlackFlashSync(player);
        return true;
    }

    private static void expireBlackFlashGuaranteeIfNeeded(ServerPlayer player, CompoundTag data) {
        if (!data.getBoolean("addon_bf_guaranteed") || !data.getBoolean("addon_bf_waiting_hit") || !data.contains("addon_bf_release_tick")) {
            return;
        }
        long releaseTick = data.getLong("addon_bf_release_tick");
        long guaranteeNonce = data.getLong("addon_bf_guarantee_nonce");
        if (guaranteeNonce != 0L && data.getLong("addon_bf_last_confirmed_nonce") == guaranteeNonce) {
            BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
            data.putBoolean("addon_bf_released", false);
            data.putBoolean("addon_bf_waiting_hit", false);
            BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
            ModNetworking.sendBlackFlashSync(player);
            return;
        }
        long age = player.serverLevel().getGameTime() - releaseTick;
        if (age <= (long)BF_PENDING_SUCCESS_EXPIRE_TICKS) {
            return;
        }
        if (!data.getBoolean("addon_bf_guaranteed") || !data.getBoolean("addon_bf_waiting_hit") || data.getLong("addon_bf_release_tick") != releaseTick || data.getLong("addon_bf_guarantee_nonce") != guaranteeNonce) {
            LOGGER.warn("[BlackFlashReleaseDiag] skip stale guarantee expiry player={} age={} nonce={} waitingHit={} guaranteed={}", new Object[]{player.getName().getString(), age, guaranteeNonce, data.getBoolean("addon_bf_waiting_hit"), data.getBoolean("addon_bf_guaranteed")});
            return;
        }
        LOGGER.warn("[BlackFlashTimingDiag] TIMEOUT_FAIL nonce={} age={} state={}", new Object[]{guaranteeNonce, age, BlueRedPurpleNukeMod.getBlackFlashState(data)});
        LOGGER.warn("[BlackFlashReleaseDiag] fail_reason=timeout_no_hit_confirm player={} age={} nonce={} waitingHit={} timedFeedbackSent={}", new Object[]{player.getName().getString(), age, guaranteeNonce, data.getBoolean("addon_bf_waiting_hit"), data.getBoolean("addon_bf_timed_feedback_sent")});
        if (!data.getBoolean("addon_bf_expire_feedback_sent")) {
            data.putBoolean("addon_bf_expire_feedback_sent", true);
            ModNetworking.sendBlackFlashFeedback(player, false, false);
        }
        BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
        BlueRedPurpleNukeMod.handleBlackFlashFlowFailure(player, data, "guarantee_expired");
        data.putBoolean("addon_bf_released", false);
        data.putBoolean("addon_bf_waiting_hit", false);
        BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
        ModNetworking.sendBlackFlashSync(player);
    }


    public static boolean isBlackFlashFlowCooldownActive(ServerPlayer player) {
        return player != null && player.getPersistentData().getInt("addon_bf_flow_cd") > 0;
    }

    private static void tickBlackFlashFlowCooldown(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int cd = data.getInt("addon_bf_flow_cd");
        if (cd > 0) {
            data.putInt("addon_bf_flow_cd", cd - 1);
            if (cd - 1 <= 0) {
                data.putInt("addon_bf_flow", 0);
                BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
                BlueRedPurpleNukeMod.clearBlackFlashGuarantee(player);
                BlueRedPurpleNukeMod.clearBlackFlashCastGates(data);
                ModNetworking.sendBlackFlashSync(player);
            }
        }
    }

    private static void advanceBlackFlashFlow(ServerPlayer player, CompoundTag data) {
        int next = Math.min(BF_FLOW_MAX, Math.max(0, data.getInt("addon_bf_flow")) + 1);
        data.putInt("addon_bf_flow", next);
        if (next >= BF_FLOW_MAX) {
            data.putBoolean("addon_bf_sparks_blessed", true);
            data.putInt("addon_bf_flow_cd", BF_FLOW_COOLDOWN_TICKS);
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            BlueRedPurpleNukeMod.grantBlackFlashFlowAdvancement(player);
            BlueRedPurpleNukeMod.playBlackFlashFlowBlessingEffects(player);
        }
    }

    private static void handleBlackFlashFlowFailure(ServerPlayer player, CompoundTag data, String reason) {
        if (data.getInt("addon_bf_flow") > 0 || data.getBoolean("addon_bf_charging") || data.getBoolean("addon_bf_guaranteed")) {
            LOGGER.warn("[BlackFlashFlow] reset player={} reason={} flow={}", new Object[]{player.getName().getString(), reason, data.getInt("addon_bf_flow")});
            data.putInt("addon_bf_flow", 0);
            data.putInt("addon_bf_flow_cd", BF_FLOW_COOLDOWN_TICKS);
            data.putBoolean("addon_bf_guaranteed", false);
            data.putBoolean("addon_bf_waiting_hit", false);
            data.putBoolean("addon_bf_charging", false);
            BlueRedPurpleNukeMod.clearBlackFlashTiming(data);
            BlueRedPurpleNukeMod.armBlackFlashResolvedCastGate(data, player.serverLevel().getGameTime());
            ModNetworking.sendBlackFlashSync(player);
        }
    }

    private static boolean grantBlackFlashFlowAdvancement(ServerPlayer player) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation("jjkblueredpurple:blessed_by_the_sparks_of_black"));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (!progress.isDone()) {
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(adv, criterion);
                }
                player.displayClientMessage(Component.literal("\u00a70\u26a1 \u00a74Blessed by the Black Sparks"), false);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void playBlackFlashFlowBlessingEffects(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 0.15;
        double z = player.getZ();
        level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.75f, 0.42f);
        level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.25f, 0.46f);
        level.sendParticles(ParticleTypes.FLASH, x, y + 1.05, z, 2, 0.05, 0.05, 0.05, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.55, z, 1, 0.45, 0.2, 0.45, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.05, z, 60, 0.9, 1.35, 0.9, 0.12);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.15, z, 48, 1.25, 0.9, 1.25, 0.26);
        level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), x, y + 1.0, z, 12, 0.75, 1.15, 0.75, 0.0);
        level.sendParticles(BF_SPARKS_BLACK_DUST, x, y + 0.95, z, 70, 1.2, 0.9, 1.2, 0.045);
        level.sendParticles(BF_SPARKS_RED_DUST, x, y + 1.05, z, 76, 1.3, 1.0, 1.3, 0.055);
        for (int pillar = 0; pillar < 7; ++pillar) {
            double py = y + 0.2 + (double)pillar * 0.36;
            double spread = 0.25 + (double)pillar * 0.08;
            level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_CURSE_POWER_RED.get()), x, py, z, 18, spread, 0.08, spread, 0.018 + (double)pillar * 0.004);
            level.sendParticles(pillar % 2 == 0 ? BF_SPARKS_RED_DUST : BF_SPARKS_BLACK_DUST, x, py, z, 14, spread * 0.82, 0.05, spread * 0.82, 0.025);
        }
        for (int wave = 0; wave < 8; ++wave) {
            double smokeRadius = 1.8 + (double)wave * 1.45;
            double sparkRadius = 2.35 + (double)wave * 1.62;
            int samples = 28 + wave * 6;
            for (int i = 0; i < samples; ++i) {
                double angle = (Math.PI * 2.0 * (double)i) / (double)samples + (double)wave * 0.33;
                double ringX = Math.cos(angle);
                double ringZ = Math.sin(angle);
                double ripple = Math.sin((double)i * 0.75 + (double)wave) * 0.12;
                if (i % 2 == 0) {
                    level.sendParticles(wave >= 5 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, x + ringX * smokeRadius, y + 0.08 + ripple, z + ringZ * smokeRadius, 1, ringX * (0.16 + (double)wave * 0.05), 0.025, ringZ * (0.16 + (double)wave * 0.05), 0.012);
                }
                level.sendParticles(i % 3 == 0 ? BF_SPARKS_BLACK_DUST : BF_SPARKS_RED_DUST, x + ringX * sparkRadius, y + 0.7 + (double)wave * 0.08 + ripple, z + ringZ * sparkRadius, 1, ringX * 0.05, 0.08, ringZ * 0.05, 0.018);
                if (i % 9 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x + ringX * (sparkRadius + 0.55), y + 0.95 + (double)wave * 0.1, z + ringZ * (sparkRadius + 0.55), 1, ringX * 0.12, 0.06, ringZ * 0.12, 0.16);
                }
                if (i % 14 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, x + ringX * sparkRadius, y + 1.2 + (double)wave * 0.08, z + ringZ * sparkRadius, 1, ringX * 0.04, 0.02, ringZ * 0.04, 0.09);
                }
            }
        }
        for (int slash = 0; slash < 14; ++slash) {
            double angle = (Math.PI * 2.0 * (double)slash) / 14.0 + 0.18;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            double start = 1.25 + (double)(slash % 3) * 0.55;
            for (int step = 0; step < 9; ++step) {
                double dist = start + (double)step * 0.72;
                double side = ((double)step - 4.0) * 0.045;
                level.sendParticles(slash % 2 == 0 ? BF_SPARKS_RED_DUST : BF_SPARKS_BLACK_DUST, x + dirX * dist - dirZ * side, y + 1.0 + (double)step * 0.035, z + dirZ * dist + dirX * side, 1, 0.025, 0.025, 0.025, 0.015);
            }
        }
        level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_RED.get()), x, y + 1.05, z, 60, 2.2, 0.95, 2.2, 0.026);
        BlueRedPurpleNukeMod.applyBlackFlashFlowBlessingImpact(level, player, new Vec3(x, y, z));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BF_SPARKS_BLESSING_BUFF_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BF_SPARKS_BLESSING_BUFF_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BF_SPARKS_BLESSING_BUFF_TICKS, 0, false, true, true));
    }

    private static void applyBlackFlashFlowBlessingImpact(ServerLevel level, ServerPlayer player, Vec3 center) {
        if (!BF_BLESSING_BREAK_BLOCKS || level == null || player == null || center == null) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        BlockPos protectedSupport = BlueRedPurpleNukeMod.findBlackFlashBlessingOriginUnderFeet(level, player);
        BlockPos origin = BlueRedPurpleNukeMod.findBlackFlashBlessingOriginBehindPlayer(level, player);
        Vec3 groundCenter = new Vec3((double)origin.getX() + 0.5, (double)origin.getY() + 1.0, (double)origin.getZ() + 0.5);
        BlueRedPurpleNukeMod.scheduleBlackFlashBlessingBlockWaves(level, player, origin, protectedSupport, groundCenter);
        center = groundCenter;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(BF_BLESSING_ENTITY_RADIUS), e -> e.isAlive() && e != player && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        for (LivingEntity target : targets) {
            Vec3 delta = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0).subtract(center);
            double distance = Math.max(0.001, delta.length());
            double falloff = 1.0 - Math.min(1.0, distance / BF_BLESSING_ENTITY_RADIUS);
            Vec3 push = delta.normalize().scale(0.55 + falloff * 1.25);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.22 + falloff * 0.45, push.z));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 1, false, true, true));
        }
    }

    private static BlockPos findBlackFlashBlessingOriginUnderFeet(ServerLevel level, ServerPlayer player) {
        BlockPos feetBelow = player.blockPosition().below();
        for (int yOff = 0; yOff >= -2; --yOff) {
            BlockPos pos = feetBelow.offset(0, yOff, 0);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }
        for (int yOff = 1; yOff >= -3; --yOff) {
            BlockPos pos = feetBelow.offset(0, yOff, 0);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return feetBelow;
    }

    private static Vec3 getBlackFlashBlessingBackwardDirection(ServerPlayer player) {
        if (player == null) {
            return new Vec3(0.0, 0.0, -1.0);
        }
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6) {
            float yawRad = player.getYRot() * ((float)Math.PI / 180.0f);
            horizontalLook = new Vec3((double)(-Mth.sin(yawRad)), 0.0, (double)Mth.cos(yawRad));
        }
        return horizontalLook.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : horizontalLook.normalize().scale(-1.0);
    }

    private static BlockPos findBlackFlashBlessingOriginBehindPlayer(ServerLevel level, ServerPlayer player) {
        Vec3 backward = BlueRedPurpleNukeMod.getBlackFlashBlessingBackwardDirection(player);
        Vec3 behind = backward.scale(4.0);
        BlockPos behindFeetBelow = BlockPos.containing(player.getX() + behind.x, player.getY() - 1.0, player.getZ() + behind.z);
        for (int yOff = 2; yOff >= -4; --yOff) {
            BlockPos pos = behindFeetBelow.offset(0, yOff, 0);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }
        for (int yOff = 2; yOff >= -4; --yOff) {
            BlockPos pos = behindFeetBelow.offset(0, yOff, 0);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return behindFeetBelow;
    }

    private static void scheduleBlackFlashBlessingBlockWaves(ServerLevel level, ServerPlayer player, BlockPos origin, BlockPos protectedSupportBlock, Vec3 center) {
        if (level == null || player == null || origin == null || center == null) {
            return;
        }
        BlockPos protectedPos = protectedSupportBlock != null ? protectedSupportBlock.immutable() : origin.immutable();
        Vec3 backward = BlueRedPurpleNukeMod.getBlackFlashBlessingBackwardDirection(player);
        Vec3 side = new Vec3(-backward.z, 0.0, backward.x).normalize();
        LOGGER.warn("[BlackFlashBlessingDir] player={} yaw={} backward=({}, {}) side=({}, {}) origin={}", new Object[]{player.getName().getString(), player.getYRot(), backward.x, backward.z, side.x, side.z, origin});
        BlackFlashBlessingWaveSession session = new BlackFlashBlessingWaveSession(level.dimension(), player.getUUID(), origin.immutable(), protectedPos, center, backward, side, level.getGameTime());
        synchronized (BF_BLESSING_WAVE_SESSIONS) {
            BF_BLESSING_WAVE_SESSIONS.add(session);
        }
    }

    private static void tickBlackFlashBlessingWaveSessions() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        synchronized (BF_BLESSING_WAVE_SESSIONS) {
            Iterator<BlackFlashBlessingWaveSession> iterator = BF_BLESSING_WAVE_SESSIONS.iterator();
            while (iterator.hasNext()) {
                BlackFlashBlessingWaveSession session = iterator.next();
                ServerLevel level = server.getLevel(session.levelKey);
                if (level == null) {
                    iterator.remove();
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(session.playerUuid);
                if (player == null || !player.isAlive()) {
                    iterator.remove();
                    continue;
                }
                long age = level.getGameTime() - session.startTick;
                while (session.nextWave < BF_BLESSING_BLOCK_BREAK_WAVE_RADII.length && age >= (long)BF_BLESSING_BLOCK_BREAK_WAVE_DELAYS[session.nextWave] && session.broken < BF_BLESSING_MAX_BLOCK_BREAKS) {
                    int wave = session.nextWave++;
                    int radius = BF_BLESSING_BLOCK_BREAK_WAVE_RADII[wave];
                    int waveCap = Math.min(BF_BLESSING_BLOCK_BREAK_WAVE_CAPS[wave], BF_BLESSING_MAX_BLOCK_BREAKS - session.broken);
                    int waveBroken = BlueRedPurpleNukeMod.breakBlackFlashBlessingWave(level, player, session.origin, session.center, session.backward, session.side, wave, radius, waveCap, session.visited, session.protectedSupportBlock);
                    session.broken += waveBroken;
                    BlueRedPurpleNukeMod.spawnBlackFlashBlessingWaveEffects(level, session.center, session.origin, wave, radius, waveBroken);
                }
                if (session.nextWave >= BF_BLESSING_BLOCK_BREAK_WAVE_RADII.length || session.broken >= BF_BLESSING_MAX_BLOCK_BREAKS) {
                    iterator.remove();
                }
            }
        }
    }

    private static int breakBlackFlashBlessingWave(ServerLevel level, ServerPlayer player, BlockPos origin, Vec3 center, Vec3 backward, Vec3 side, int wave, int radius, int maxBreaks, Set<BlockPos> visited, BlockPos protectedSupportBlock) {
        int broken = 0;
        int innerRadius = wave <= 0 ? 0 : BF_BLESSING_BLOCK_BREAK_WAVE_RADII[wave - 1] + 1;
        int innerRadiusSq = innerRadius * innerRadius;
        int outerRadiusSq = radius * radius;
        List<BlockPos> candidates = new ArrayList<>();
        Set<BlockPos> waveSeen = new HashSet<>();
        for (int depth = 0; depth <= radius; ++depth) {
            for (int width = -radius; width <= radius; ++width) {
                int distSq = depth * depth + width * width;
                if (distSq > outerRadiusSq || distSq < innerRadiusSq) {
                    continue;
                }
                double columnX = (double)origin.getX() + 0.5 + backward.x * (double)depth + side.x * (double)width;
                double columnZ = (double)origin.getZ() + 0.5 + backward.z * (double)depth + side.z * (double)width;
                BlockPos column = BlockPos.containing(columnX, origin.getY(), columnZ);
                BlockPos pos = BlueRedPurpleNukeMod.findBlackFlashBlessingGroundBreakPos(level, column);
                if (pos == null || pos.equals(protectedSupportBlock) || visited.contains(pos) || !waveSeen.add(pos)) {
                    continue;
                }
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator
                .comparingDouble((BlockPos pos) -> {
                    double dx = (double)pos.getX() + 0.5 - center.x;
                    double dz = (double)pos.getZ() + 0.5 - center.z;
                    return Math.atan2(dz, dx);
                })
                .thenComparingDouble(pos -> {
                    double dx = (double)pos.getX() + 0.5 - center.x;
                    double dz = (double)pos.getZ() + 0.5 - center.z;
                    return dx * dx + dz * dz;
                }));
        int candidateCount = candidates.size();
        int attempts = Math.min(candidateCount, Math.max(maxBreaks * 3, maxBreaks + 24));
        for (int i = 0; i < attempts && broken < maxBreaks; ++i) {
            int index = candidateCount <= attempts ? i : Mth.floor(((double)i + 0.5) * (double)candidateCount / (double)attempts);
            if (index < 0 || index >= candidateCount) {
                continue;
            }
            BlockPos pos = candidates.get(index);
            if (!visited.add(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!BlueRedPurpleNukeMod.canBlackFlashBlessingBreakBlock(level, pos, state)) {
                continue;
            }
            BlueRedPurpleNukeMod.spawnBlackFlashBlessingBlockBreakParticles(level, center, pos, state, wave);
            level.destroyBlock(pos, false, player);
            ++broken;
        }
        return broken;
    }

    private static void spawnBlackFlashBlessingBlockBreakParticles(ServerLevel level, Vec3 center, BlockPos pos, BlockState state, int wave) {
        double px = (double)pos.getX() + 0.5;
        double py = (double)pos.getY() + 0.75;
        double pz = (double)pos.getZ() + 0.5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), px, py, pz, 5 + wave * 3, 0.28 + wave * 0.08, 0.18 + wave * 0.05, 0.28 + wave * 0.08, 0.045 + wave * 0.012);
        if (wave == 0) {
            level.sendParticles(BF_SPARKS_BLACK_DUST, px, py + 0.18, pz, 2, 0.12, 0.08, 0.12, 0.018);
        } else if (wave == 1) {
            level.sendParticles(BF_SPARKS_RED_DUST, px, py + 0.24, pz, 3, 0.16, 0.1, 0.16, 0.028);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py + 0.35, pz, 1, 0.08, 0.04, 0.08, 0.12);
        } else {
            Vec3 out = new Vec3(px - center.x, 0.0, pz - center.z);
            if (out.lengthSqr() < 1.0E-6) out = new Vec3(0.0, 0.0, 1.0);
            out = out.normalize();
            level.sendParticles(BF_SPARKS_RED_DUST, px, py + 0.28, pz, 3, out.x * 0.08, 0.12, out.z * 0.08, 0.035);
            level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), px, py + 0.55, pz, 1, 0.08, 0.08, 0.08, 0.0);
        }
    }

    private static void spawnBlackFlashBlessingWaveEffects(ServerLevel level, Vec3 center, BlockPos origin, int wave, int radius, int broken) {
        BlueRedPurpleNukeMod.playBlackFlashBlessingWaveSound(level, origin, wave);
        BlueRedPurpleNukeMod.spawnBlackFlashLightningWave(level, center, wave, radius);
    }

    private static void playBlackFlashBlessingWaveSound(ServerLevel level, BlockPos origin, int wave) {
        float volume = 1.05f + (float)wave * 0.16f;
        float pitch = 0.92f + (float)(wave % 3) * 0.06f;
        SoundEvent stoneCrash = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:stone_crash"));
        SoundEvent glassCrash = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:glass_crash"));
        SoundEvent waterSplash = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("jujutsucraft:water_splash"));
        level.playSound(null, origin, stoneCrash != null ? stoneCrash : SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, volume, pitch);
        if (wave >= 1) {
            level.playSound(null, origin, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, volume * 0.82f, pitch * 0.94f);
        }
        if (wave >= 2) {
            level.playSound(null, origin, glassCrash != null ? glassCrash : SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, volume * 0.72f, pitch * 1.08f);
        }
        if (wave >= 3) {
            level.playSound(null, origin, waterSplash != null ? waterSplash : SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, volume * 0.58f, pitch * 0.86f);
        }
    }

    private static void spawnBlackFlashLightningWave(ServerLevel level, Vec3 center, int wave, int radius) {
        double y = center.y + 0.35 + (double)wave * 0.18;
        int samples = Math.max(28, radius * (6 + wave));
        double centerSpread = 0.7 + (double)wave * 0.22;
        level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), center.x, y + 0.95, center.z, 10 + wave * 5, centerSpread, 1.15, centerSpread, 0.0);
        level.sendParticles(ParticleTypes.FLASH, center.x, y + 0.9, center.z, 2, 0.06, 0.06, 0.06, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, y + 0.85, center.z, 18 + wave * 8, centerSpread, 0.55, centerSpread, 0.24);
        for (int i = 0; i < samples; ++i) {
            double angle = (Math.PI * 2.0 * (double)i) / (double)samples + (double)wave * 0.37;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double branch = radius * (0.78 + 0.22 * Math.sin((double)i * 1.71 + (double)wave));
            double px = center.x + dx * branch;
            double pz = center.z + dz * branch;
            level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), px, y + 0.55, pz, 2, 0.1, 0.22, 0.1, 0.0);
            if (i % 2 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, y + 0.65, pz, 2, dx * 0.1, 0.07, dz * 0.1, 0.18);
            if (i % 3 == 0) level.sendParticles(BF_SPARKS_RED_DUST, px, y + 0.42, pz, 1, dx * 0.055, 0.08, dz * 0.055, 0.035);
            if (i % 4 == 0) level.sendParticles(BF_SPARKS_BLACK_DUST, px, y + 0.28, pz, 1, dx * 0.055, 0.05, dz * 0.055, 0.024);
            if (i % 9 == 0) {
                double fork = Math.max(2.0, radius * (0.16 + (double)wave * 0.025));
                level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), px + dz * fork, y + 0.85, pz - dx * fork, 1, 0.08, 0.18, 0.08, 0.0);
                level.sendParticles((ParticleOptions)((SimpleParticleType)JujutsucraftModParticleTypes.PARTICLE_BLACK_FLASH_1.get()), px - dz * fork, y + 0.85, pz + dx * fork, 1, 0.08, 0.18, 0.08, 0.0);
            }
        }
    }

    private static final class BlackFlashBlessingWaveSession {
        private final ResourceKey<Level> levelKey;
        private final UUID playerUuid;
        private final BlockPos origin;
        private final BlockPos protectedSupportBlock;
        private final Vec3 center;
        private final Vec3 backward;
        private final Vec3 side;
        private final long startTick;
        private final Set<BlockPos> visited = new HashSet<>();
        private int nextWave;
        private int broken;

        private BlackFlashBlessingWaveSession(ResourceKey<Level> levelKey, UUID playerUuid, BlockPos origin, BlockPos protectedSupportBlock, Vec3 center, Vec3 backward, Vec3 side, long startTick) {
            this.levelKey = levelKey;
            this.playerUuid = playerUuid;
            this.origin = origin;
            this.protectedSupportBlock = protectedSupportBlock;
            this.center = center;
            this.backward = backward;
            this.side = side;
            this.startTick = startTick;
        }
    }

    private static BlockPos findBlackFlashBlessingGroundBreakPos(ServerLevel level, BlockPos column) {
        for (int yOff = 1; yOff >= -2; --yOff) {
            BlockPos pos = column.offset(0, yOff, 0);
            BlockState state = level.getBlockState(pos);
            if (BlueRedPurpleNukeMod.canBlackFlashBlessingBreakBlock(level, pos, state) && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos;
            }
        }
        for (int yOff = 2; yOff >= -1; --yOff) {
            BlockPos pos = column.offset(0, yOff, 0);
            BlockState state = level.getBlockState(pos);
            if (BlueRedPurpleNukeMod.canBlackFlashBlessingBreakBlock(level, pos, state)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean canBlackFlashBlessingBreakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        Block block = state.getBlock();
        if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.STRUCTURE_BLOCK || block == Blocks.JIGSAW || block == Blocks.SPAWNER || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0f && hardness <= BF_BLESSING_MAX_BLOCK_HARDNESS;
    }

    private static void closeBlackFlashPendingWithFailure(ServerPlayer player, CompoundTag data, String reason) {
        if (player == null || data == null) {
            return;
        }
        String state = BlueRedPurpleNukeMod.getBlackFlashState(data);
        if (BF_STATE_RING_ACTIVE.equals(state) && data.getBoolean("addon_bf_charging")) {
            LOGGER.warn("[BlackFlashTimingDiag] RELEASE_REJECT reason={} nonce={} clientNeedle={} state={} age={}", new Object[]{reason, data.getLong("addon_bf_timing_nonce"), Float.NaN, state, BlueRedPurpleNukeMod.getBlackFlashTimingAge(player, data)});
            BlueRedPurpleNukeMod.sendBlackFlashFailureOnce(player, data, reason);
        } else if (BF_STATE_WAITING_HIT.equals(state) && data.getBoolean("addon_bf_waiting_hit") && data.getBoolean("addon_bf_guaranteed")) {
            long releaseTick = data.contains("addon_bf_release_tick") ? data.getLong("addon_bf_release_tick") : player.serverLevel().getGameTime();
            LOGGER.warn("[BlackFlashTimingDiag] HOLD_WAITING_HIT reason={} nonce={} age={} state={}", new Object[]{reason, data.getLong("addon_bf_guarantee_nonce"), player.serverLevel().getGameTime() - releaseTick, state});
        }
    }

    private static void sendBlackFlashFailureOnce(ServerPlayer player, CompoundTag data, String reason) {
        LOGGER.warn("[BlackFlashReleaseDiag] fail_reason={} player={} nonce={} state={} charging={} waitingHit={} guaranteed={}", new Object[]{reason, player.getName().getString(), data.getLong("addon_bf_timing_nonce"), BlueRedPurpleNukeMod.getBlackFlashState(data), data.getBoolean("addon_bf_charging"), data.getBoolean("addon_bf_waiting_hit"), data.getBoolean("addon_bf_guaranteed")});
        long nonce = data.getLong("addon_bf_timing_nonce");
        if (nonce != 0L && data.getLong("addon_bf_last_confirmed_nonce") == nonce) {
            LOGGER.warn("[BlackFlashReleaseDiag] suppress failure after confirmed black flash player={} nonce={} reason={}", new Object[]{player.getName().getString(), nonce, reason});
            return;
        }
        long lastFailNonce = data.getLong("addon_bf_last_fail_feedback_nonce");
        if (nonce != 0L && lastFailNonce == nonce) {
            LOGGER.warn("[BlackFlashReleaseDiag] suppress duplicate failure feedback player={} nonce={} reason={}", new Object[]{player.getName().getString(), nonce, reason});
            return;
        }
        data.putLong("addon_bf_last_fail_feedback_nonce", nonce);
        ModNetworking.sendBlackFlashFeedback(player, false, false);
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
        if (!(owner instanceof Player player)) {
            return false;
        }
        boolean useOgCrouchLowCharge = BlueRedPurpleNukeMod.shouldUseOgCrouchLowChargeRoute(redEntity, player);
        return !useOgCrouchLowCharge;
    }

    private static double getRedRouteCnt6(LivingEntity redEntity, LivingEntity owner) {
        CompoundTag data = redEntity.getPersistentData();
        double ownerCnt6 = owner != null ? owner.getPersistentData().getDouble("cnt6") : 0.0;
        double routedCnt6 = Math.max(Math.max(data.getDouble("cnt6"), ownerCnt6), Math.max(data.getDouble("addon_red_charge_cached"), data.getDouble("addon_red_charge_used")));
        boolean launchStarted = BlueRedPurpleNukeMod.hasRedLaunchStarted(redEntity);
        if (launchStarted && !data.getBoolean("addon_red_route_charge_locked")) {
            data.putDouble("addon_red_route_charge", routedCnt6);
            data.putBoolean("addon_red_route_charge_locked", true);
        }
        if (data.getBoolean("addon_red_route_charge_locked")) {
            routedCnt6 = Math.max(0.0, data.getDouble("addon_red_route_charge"));
        }
        return routedCnt6;
    }

    private static boolean hasRedLaunchStarted(LivingEntity redEntity) {
        boolean launchStarted = redEntity.getPersistentData().getBoolean("flag_start");
        if (redEntity instanceof RedEntity re) {
            try {
                launchStarted = launchStarted || ((Boolean)re.getEntityData().get(RedEntity.DATA_flag_start)).booleanValue();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return launchStarted;
    }

    private static boolean shouldUseOgCrouchLowChargeRoute(LivingEntity redEntity, Player owner) {
        CompoundTag data = redEntity.getPersistentData();
        if (data.getBoolean("addon_red_route_mode_locked")) {
            boolean useOg = data.getBoolean("addon_red_use_og");
            if (useOg) {
                BlueRedPurpleNukeMod.clearAddonRedControllerState(redEntity);
            }
            return useOg;
        }
        double routedCnt6 = BlueRedPurpleNukeMod.getRedRouteCnt6(redEntity, owner);
        boolean launchStarted = BlueRedPurpleNukeMod.hasRedLaunchStarted(redEntity);
        if (owner.isCrouching() && routedCnt6 < 5.0) {
            data.putBoolean("addon_red_crouch_cast_seen", true);
        }
        boolean liveDecision = (owner.isCrouching() || data.getBoolean("addon_red_crouch_cast_seen")) && routedCnt6 < 5.0;
        data.putBoolean("addon_red_use_og", liveDecision);
        if (launchStarted) {
            data.putBoolean("addon_red_route_mode_locked", true);
            if (liveDecision) {
                BlueRedPurpleNukeMod.clearAddonRedControllerState(redEntity);
            }
        }
        return data.getBoolean("addon_red_use_og");
    }

    private static void clearAddonRedControllerState(LivingEntity redEntity) {
        CompoundTag data = redEntity.getPersistentData();
        data.putBoolean("addon_red_init_done", false);
        data.putBoolean("addon_red_normal_active", false);
        data.putBoolean("addon_red_shift_cast", false);
        data.putBoolean("addon_red_shift_exploded", false);
        data.putBoolean("addon_red_crouch_low_charge", false);
        data.putBoolean("addon_red_charge_anchor_valid", false);
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
        Player ownerPlayer = (Player)owner;
        if (BlueRedPurpleNukeMod.shouldUseOgCrouchLowChargeRoute(redEntity, ownerPlayer)) {
            AIRedProcedure.execute((LevelAccessor)serverLevel, (Entity)redEntity);
            return;
        }
        double liveCnt6 = redEntity.getPersistentData().getDouble("cnt6");
        double ownerCnt6 = owner.getPersistentData().getDouble("cnt6");
        double cachedCnt6 = Math.max(Math.max(liveCnt6, ownerCnt6), redEntity.getPersistentData().getDouble("addon_red_charge_cached"));
        double routedCnt6 = BlueRedPurpleNukeMod.getRedRouteCnt6(redEntity, owner);
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
            double effectiveCnt6 = Math.max(Math.max(cachedCnt6, ownerCnt6), routedCnt6);
            boolean isCrouching = ownerPlayer.isCrouching();
            // Route crouch low-charge Red back into the original AIRed path using the cast-locked route decision, so releasing crouch after launch cannot bounce the orb back into addon control.
            if (BlueRedPurpleNukeMod.shouldUseOgCrouchLowChargeRoute(redEntity, ownerPlayer)) {
                AIRedProcedure.execute((LevelAccessor)serverLevel, (Entity)redEntity);
                return;
            }
            redEntity.getPersistentData().putBoolean("addon_red_init_done", true);
            redEntity.getPersistentData().putDouble("addon_red_charge_used", effectiveCnt6);
            if (isCrouching && effectiveCnt6 >= 5.0) {
                redEntity.getPersistentData().putBoolean("addon_red_shift_cast", true);
                BlueRedPurpleNukeMod.handleRedTeleportBehind(redEntity);
            } else {
                BlueRedPurpleNukeMod.initializeNormalRedOrb(redEntity, owner, effectiveCnt6);
            }
        }
        boolean shiftCast = redEntity.getPersistentData().getBoolean("addon_red_shift_cast");
        boolean normalActive = redEntity.getPersistentData().getBoolean("addon_red_normal_active");
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
        redEntity.getPersistentData().putDouble("addon_cnt6", cnt6);
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
        redEntity.getPersistentData().putDouble("addon_red_spawn_x", startPos.x);
        redEntity.getPersistentData().putDouble("addon_red_spawn_y", startPos.y);
        redEntity.getPersistentData().putDouble("addon_red_spawn_z", startPos.z);
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
     * Initializes the crouch low-charge Red path on top of the addon normal-orb controller so the cast keeps movement and visuals without falling back to the vanilla multi-hit AI loop.
     * @param redEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param effectiveCnt6 effective cnt 6 used by this method.
     */
    private static void initializeCrouchLowChargeRedOrb(LivingEntity redEntity, LivingEntity owner, double effectiveCnt6) {
        BlueRedPurpleNukeMod.initializeNormalRedOrb(redEntity, owner, effectiveCnt6);
        CompoundTag data = redEntity.getPersistentData();
        double chargeRatio = Math.max(0.0, Math.min(Math.max(effectiveCnt6, 0.0), 5.0) / 5.0);
        double crouchMaxRange = Math.min(data.getDouble("addon_red_max_range"), CROUCH_RED_LOW_CHARGE_MAX_RANGE * (0.75 + chargeRatio * 0.25));
        double crouchSpeed = Math.max(0.45, data.getDouble("addon_red_speed") * CROUCH_RED_LOW_CHARGE_SPEED_SCALE);
        data.putBoolean("addon_red_crouch_low_charge", true);
        data.putDouble("addon_red_max_range", crouchMaxRange);
        data.putDouble("addon_red_speed", crouchSpeed);
        data.putDouble("x_power", data.getDouble("addon_red_dir_x") * crouchSpeed);
        data.putDouble("y_power", data.getDouble("addon_red_dir_y") * crouchSpeed);
        data.putDouble("z_power", data.getDouble("addon_red_dir_z") * crouchSpeed);
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
        double distanceTraveled = BlueRedPurpleNukeMod.getNormalRedDistanceTraveled(redEntity, pos);
        double distanceFactor = BlueRedPurpleNukeMod.getNormalRedDistanceFactor(distanceTraveled);
        double distanceMultiplier = BlueRedPurpleNukeMod.getNormalRedDistanceMultiplier(distanceFactor);
        double radiusMultiplier = BlueRedPurpleNukeMod.getNormalRedRadiusMultiplier(distanceFactor);
        double knockbackMultiplier = BlueRedPurpleNukeMod.getNormalRedKnockbackMultiplier(distanceFactor);
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
        String orbId = redEntity.getUUID().toString();
        List<LivingEntity> attachedMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(30.0), e -> e.isAlive() && orbId.equals(e.getPersistentData().getString("addon_red_attached_orb")));
        for (LivingEntity mob : attachedMobs) {
            mob.getPersistentData().remove("addon_red_attached_orb");
            mob.setNoGravity(false);
            Vec3 releaseDir = mob.position().subtract(pos);
            if (releaseDir.lengthSqr() < 1.0E-6) {
                releaseDir = new Vec3(0.0, 0.2, 0.0);
            }
            mob.setDeltaMovement(releaseDir.normalize().scale((1.2 + (double)chargeTier * 0.7) * knockbackMultiplier));
            mob.hurtMarked = true;
        }
        double cnt6Val = Math.max(redEntity.getPersistentData().getDouble("addon_cnt6"), cnt6);
        double CNT6 = 1.0 + cnt6Val * 0.1;
        double cntLife = Math.max(redEntity.getPersistentData().getDouble("cnt_life"), redEntity.getPersistentData().getDouble("addon_red_ticks"));
        double ogDamage = Math.max(40.0 * Math.pow(0.99, cntLife), 28.0) * CNT6 * distanceMultiplier;
        BlueRedPurpleNukeMod.applyOriginalRedRangeAttack(serverLevel, redEntity, pos, ogDamage, 10.0 * CNT6 * radiusMultiplier, 2.0 * CNT6 * knockbackMultiplier, 0.0);
        BlueRedPurpleNukeMod.applyRedExplosionTerrainBurst(serverLevel, redEntity, pos, chargeTier);
        redEntity.getPersistentData().putBoolean("addon_red_normal_active", false);
        redEntity.getPersistentData().putBoolean("addon_red_crouch_low_charge", false);
        redEntity.discard();
    }

    // ===== SHIFT-CAST RED VARIANT =====
    private static void handleShiftCastExplosion(LivingEntity redEntity, LivingEntity owner, ServerLevel serverLevel) {
        Vec3 pos = BlueRedPurpleNukeMod.getTrackedRedPosition(redEntity);
        double cnt6 = Math.max(5.0, BlueRedPurpleNukeMod.getEffectiveRedCnt6(redEntity));
        double CNT6 = 1.0 + cnt6 * 0.1;
        int chargeTier = BlueRedPurpleNukeMod.getRedChargeTier(cnt6);
        redEntity.getPersistentData().putDouble("cnt6", cnt6);
        redEntity.getPersistentData().putDouble("x_pos", pos.x);
        redEntity.getPersistentData().putDouble("y_pos", pos.y);
        redEntity.getPersistentData().putDouble("z_pos", pos.z);
        // Shift Red keeps one setup pulse for crowd control, but removes the repeated full-damage calls that stacked multiple independent RangeAttack passes in a single cast.
        redEntity.getPersistentData().putDouble("BlockRange", 6.0 * CNT6);
        redEntity.getPersistentData().putDouble("BlockDamage", 4.0 * CNT6);
        redEntity.getPersistentData().putBoolean("noParticle", true);
        BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
        redEntity.getPersistentData().putDouble("Range", 10.0 * CNT6);
        redEntity.getPersistentData().putDouble("knockback", 2.0 * CNT6);
        redEntity.getPersistentData().putDouble("effect", 0.0);
        KnockbackProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
        redEntity.getPersistentData().putDouble("BlockRange", 16.0 * CNT6);
        redEntity.getPersistentData().putDouble("BlockDamage", 0.33);
        redEntity.getPersistentData().putBoolean("noParticle", true);
        BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, (double)pos.x, (double)pos.y, (double)pos.z, (Entity)redEntity);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, (int)(30.0 * CNT6), 4.0, 4.0, 4.0, 1.0);
        BlueRedPurpleNukeMod.applyOriginalMaxChargeRedTeleportDamage(serverLevel, redEntity, pos, cnt6);
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
        BlueRedPurpleNukeMod.applyRedExplosionTerrainBurst(serverLevel, redEntity, pos, chargeTier);
        redEntity.discard();
    }

    private static void applyOriginalMaxChargeRedTeleportDamage(ServerLevel serverLevel, LivingEntity redEntity, Vec3 pos, double cnt6) {
        CompoundTag data = redEntity.getPersistentData();
        double CNT6 = 1.0 + cnt6 * 0.1;
        double xPos = pos.x;
        double yPos = pos.y;
        double zPos = pos.z;
        double xPower = data.getDouble("x_power");
        double yPower = data.getDouble("y_power");
        double zPower = data.getDouble("z_power");
        for (int i = 0; i < 8; ++i) {
            xPos += xPower * 0.25;
            yPos += yPower * 0.25;
            zPos += zPower * 0.25;
            data.putDouble("x_pos", xPos);
            data.putDouble("y_pos", yPos);
            data.putDouble("z_pos", zPos);
            double cntLife = data.getDouble("cnt_life");
            double damage = Math.max(40.0 * Math.pow(0.99, cntLife), 28.0) * CNT6;
            BlueRedPurpleNukeMod.applyOriginalRedRangeAttack(serverLevel, redEntity, new Vec3(xPos, yPos, zPos), damage, 10.0 * CNT6, 2.0 * CNT6, 0.0);
            data.putDouble("BlockRange", 6.0 * CNT6);
            data.putDouble("BlockDamage", 10.0 * CNT6);
            data.putBoolean("noParticle", true);
            BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, xPos, yPos, zPos, (Entity)redEntity);
            data.putBoolean("noEffect", true);
        }
        data.putBoolean("noEffect", false);
        data.putDouble("cnt_life", data.getDouble("cnt_life") + 1.0);
        data.putBoolean("addon_red_shift_finisher_damage_active", true);
        try {
            BlueRedPurpleNukeMod.applyOriginalRedRangeAttack(serverLevel, redEntity, new Vec3(pos.x, pos.y - 1.5, pos.z), 40.0 * CNT6, 16.0 * CNT6, 4.0 * CNT6, 0.65);
        }
        finally {
            data.remove("addon_red_shift_finisher_damage_active");
        }
    }

    private static void applyOriginalRedRangeAttack(ServerLevel serverLevel, LivingEntity redEntity, Vec3 pos, double damage, double range, double knockback, double yKnockback) {
        CompoundTag data = redEntity.getPersistentData();
        data.putDouble("Damage", damage);
        data.putDouble("Range", range);
        data.putDouble("knockback", knockback);
        data.putDouble("effect", 0.0);
        data.putDouble("y_knockback", yKnockback);
        RangeAttackProcedure.execute((LevelAccessor)serverLevel, pos.x, pos.y, pos.z, (Entity)redEntity);
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
    private static void applyRedExplosionShockwave(ServerLevel serverLevel, LivingEntity redEntity, LivingEntity owner, Vec3 pos, int chargeTier, Set<UUID> excludedTargets, double distanceMultiplier, double radiusMultiplier, double knockbackMultiplier, double minimumDamage) {
        // Higher Red charge tiers widen the shockwave radius so fully committed casts punish larger groups.
        double radius = (6.0 + (double)chargeTier * 4.0) * radiusMultiplier;
        float baseDamage = switch (chargeTier) {
            case 1 -> 28.0f;
            case 2 -> 40.0f;
            default -> 54.0f;
        };
        double normalDamageScale = BlueRedPurpleNukeMod.getNormalRedDamageScale(owner);
        double scaledBaseDamage = Math.max((double)baseDamage * normalDamageScale * distanceMultiplier, minimumDamage);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius), e -> e.isAlive() && e != redEntity && e != owner && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity) && (excludedTargets == null || !excludedTargets.contains(e.getUUID())));
        for (LivingEntity target : targets) {
            Vec3 delta = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0).subtract(pos);
            double distance = Math.max(0.001, delta.length());
            double falloff = 1.0 - Math.min(1.0, distance / radius);
            if (falloff <= 0.0) continue;
            boolean hurt = target.hurt(serverLevel.damageSources().explosion((Entity)redEntity, (Entity)owner), (float)(scaledBaseDamage * (0.75 + 0.25 * falloff)));
            if (hurt && owner instanceof ServerPlayer serverOwner) {
                BlueRedPurpleNukeMod.tryConfirmBlackFlashGuaranteeHit(serverOwner, "red_shockwave_manual_explosion");
            }
            Vec3 push = delta.normalize().scale((1.5 + (double)chargeTier * 0.5) * (0.35 + falloff) * knockbackMultiplier);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.35 + falloff * 0.25 * knockbackMultiplier, push.z));
        }
    }

    /**
     * Replaces the vanilla Red explosion entity-damage lane with a terrain-only burst so direct hits and the manual shockwave stay distinct instead of triple-dipping the same target.
     * @param serverLevel level value used by this operation.
     * @param redEntity entity instance being processed by this helper.
     * @param pos position used by this helper.
     * @param chargeTier resolved Red charge tier.
     */
    private static void applyRedExplosionTerrainBurst(ServerLevel serverLevel, LivingEntity redEntity, Vec3 pos, int chargeTier) {
        redEntity.getPersistentData().putDouble("BlockRange", switch (chargeTier) {
            case 1 -> 8.0;
            case 2 -> 10.0;
            default -> 12.0;
        });
        redEntity.getPersistentData().putDouble("BlockDamage", switch (chargeTier) {
            case 1 -> 5.0;
            case 2 -> 6.5;
            default -> 8.0;
        });
        redEntity.getPersistentData().putBoolean("noParticle", true);
        BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, pos.x, pos.y, pos.z, (Entity)redEntity);
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
                double maxCnt6 = Math.min(PURPLE_FUSION_CNT6_CAP, Math.max(redCnt6, blueCnt6) + 1.0);
                purpleE.getPersistentData().putDouble("cnt6", maxCnt6);
                purpleE.getPersistentData().putInt("jjkbrp_inf_rank_pass_count", 0);
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

    // ===== GOJO DOUBLE-SHIFT TELEPORT =====
    private static boolean handleGojoDoubleShiftTeleport(ServerPlayer player) {
        Level world = player.level();
        if (!(world instanceof ServerLevel)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        CompoundTag data = player.getPersistentData();
        long now = serverLevel.getGameTime();
        int cd = data.getInt(GOJO_TP_CD);
        if (cd > 0) {
            data.putInt(GOJO_TP_CD, cd - 1);
        }
        if (data.getBoolean(GOJO_TP_PENDING)) {
            data.putLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL, Math.max(data.getLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL), now + 1L));
            if (now >= data.getLong(GOJO_TP_EXECUTE_TICK)) {
                Vec3 source = player.position();
                Vec3 target = new Vec3(data.getDouble(GOJO_TP_TARGET_X), data.getDouble(GOJO_TP_TARGET_Y), data.getDouble(GOJO_TP_TARGET_Z));
                float yaw = data.getFloat(GOJO_TP_YAW);
                float pitch = data.getFloat(GOJO_TP_PITCH);
                data.putBoolean(GOJO_TP_PENDING, false);
                BlueRedPurpleNukeMod.spawnGojoTeleportBurst(serverLevel, source.add(0.0, player.getBbHeight() * 0.5, 0.0), false);
                player.teleportTo(serverLevel, target.x, target.y, target.z, yaw, pitch);
                player.resetFallDistance();
                BlueRedPurpleNukeMod.spawnGojoTeleportBurst(serverLevel, target.add(0.0, player.getBbHeight() * 0.5, 0.0), true);
                serverLevel.playSound(null, BlockPos.containing((Position)target), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.25f, 1.35f);
                data.putInt(GOJO_TP_CD, GOJO_TELEPORT_COOLDOWN_TICKS);
            }
            return true;
        }
        if (data.getLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL) >= now) {
            return true;
        }
        return false;
    }

    public static void handleGojoShiftTapPacket(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Level world = player.level();
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        if (!BlueRedPurpleNukeMod.isGojoTeleportTapEligible(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = serverLevel.getGameTime();
        data.putLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL, Math.max(data.getLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL), now + 1L));
        long lastShift = data.getLong(GOJO_TP_LAST_SHIFT_TICK);
        data.putLong(GOJO_TP_LAST_SHIFT_TICK, now);
        if (lastShift <= 0L || now - lastShift > (long)GOJO_TELEPORT_DOUBLE_SHIFT_WINDOW) {
            return;
        }
        data.putLong(GOJO_TP_LAST_SHIFT_TICK, 0L);
        BlueRedPurpleNukeMod.tryArmGojoTeleport(player, serverLevel, data, now);
    }

    private static void clearGojoTeleportRuntimeState(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(GOJO_TP_PENDING, false);
        data.remove(GOJO_TP_LAST_SHIFT_TICK);
        data.remove(GOJO_TP_EXECUTE_TICK);
        data.remove(GOJO_TP_TARGET_X);
        data.remove(GOJO_TP_TARGET_Y);
        data.remove(GOJO_TP_TARGET_Z);
        data.remove(GOJO_TP_YAW);
        data.remove(GOJO_TP_PITCH);
        data.remove(GOJO_TP_CRUSHER_SUPPRESS_UNTIL);
    }

    private static boolean isGojoTeleportTapEligible(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double activeTechnique = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        return (int)Math.round(activeTechnique) == 2
            && (int)Math.round(vars.PlayerSelectCurseTechnique) == 5
            && player.getPersistentData().getBoolean("infinity")
            && player.hasEffect((MobEffect)JujutsucraftModMobEffects.SIX_EYES.get());
    }

    private static boolean tryArmGojoTeleport(ServerPlayer player, ServerLevel serverLevel, CompoundTag data, long now) {
        if (data.getBoolean(GOJO_TP_PENDING)) {
            data.putLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL, now + (long)GOJO_TELEPORT_CRUSHER_SUPPRESS_TICKS);
            return true;
        }
        if (data.getInt(GOJO_TP_CD) > 0) {
            return true;
        }
        if (!BlueRedPurpleNukeMod.isGojoTeleportTapEligible(player)) {
            return false;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (vars.PlayerCursePower < GOJO_TELEPORT_CE_COST) {
            return true;
        }
        Vec3 target = BlueRedPurpleNukeMod.findGojoTeleportTarget(serverLevel, player);
        if (target == null) {
            return true;
        }
        double finalCE = vars.PlayerCursePower - GOJO_TELEPORT_CE_COST;
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            cap.PlayerCursePower = finalCE;
            cap.syncPlayerVariables((Entity)player);
        });
        data.putBoolean(GOJO_TP_PENDING, true);
        data.putLong(GOJO_TP_EXECUTE_TICK, now + (long)GOJO_TELEPORT_DELAY_TICKS);
        data.putDouble(GOJO_TP_TARGET_X, target.x);
        data.putDouble(GOJO_TP_TARGET_Y, target.y);
        data.putDouble(GOJO_TP_TARGET_Z, target.z);
        data.putFloat(GOJO_TP_YAW, player.getYRot());
        data.putFloat(GOJO_TP_PITCH, player.getXRot());
        data.putLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL, now + (long)GOJO_TELEPORT_DELAY_TICKS + (long)GOJO_TELEPORT_CRUSHER_SUPPRESS_TICKS);
        BlueRedPurpleNukeMod.resetInfinityCrusher(player);
        BlueRedPurpleNukeMod.spawnGojoTeleportFrame(serverLevel, player, target, player.getYRot(), player.getXRot());
        BlueRedPurpleNukeMod.spawnGojoTeleportCharge(serverLevel, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), target.add(0.0, player.getBbHeight() * 0.5, 0.0));
        return true;
    }

    private static Vec3 findGojoTeleportTarget(ServerLevel serverLevel, ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0E-6) {
            return null;
        }
        look = look.normalize();
        Vec3 rayEnd = eye.add(look.scale(GOJO_TELEPORT_RANGE));
        BlockHitResult hit = serverLevel.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)player));
        Vec3 desired = rayEnd;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            desired = hit.getLocation().subtract(look.scale(0.65));
        }
        BlockPos base = BlockPos.containing(desired.x, desired.y, desired.z);
        for (int yOff = 1; yOff >= -3; --yOff) {
            for (int radius = 0; radius <= 2; ++radius) {
                for (int xOff = -radius; xOff <= radius; ++xOff) {
                    for (int zOff = -radius; zOff <= radius; ++zOff) {
                        if (Math.max(Math.abs(xOff), Math.abs(zOff)) != radius) {
                            continue;
                        }
                        BlockPos feet = base.offset(xOff, yOff, zOff);
                        if (BlueRedPurpleNukeMod.isSafeGojoTeleportStandPos(serverLevel, player, feet)) {
                            return new Vec3((double)feet.getX() + 0.5, feet.getY(), (double)feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafeGojoTeleportStandPos(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos below = feet.below();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            return false;
        }
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getFluidState(below).isEmpty()) {
            return false;
        }
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox((double)feet.getX() + 0.5, feet.getY(), (double)feet.getZ() + 0.5);
        return level.noCollision(player, box);
    }

    private static void spawnGojoTeleportCharge(ServerLevel level, Vec3 source, Vec3 destination) {
        level.playSound(null, BlockPos.containing((Position)source), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.75f, 1.7f);
        level.sendParticles((ParticleOptions)new DustParticleOptions(BLUE_AURA_COLOR, 1.25f), source.x, source.y, source.z, 32, 0.45, 0.7, 0.45, 0.0);
        level.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, source.x, source.y, source.z, 28, 0.55, 0.75, 0.55, 0.12);
        level.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, destination.x, destination.y, destination.z, 34, 0.45, 0.75, 0.45, 0.08);
        level.sendParticles((ParticleOptions)ParticleTypes.END_ROD, destination.x, destination.y, destination.z, 18, 0.35, 0.65, 0.35, 0.08);
    }

    private static void spawnGojoTeleportBurst(ServerLevel level, Vec3 pos, boolean arrival) {
        level.sendParticles((ParticleOptions)new DustParticleOptions(BLUE_AURA_COLOR, arrival ? 1.6f : 1.1f), pos.x, pos.y, pos.z, arrival ? 70 : 45, 0.7, 0.9, 0.7, 0.0);
        level.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, arrival ? 55 : 34, 0.65, 0.85, 0.65, 0.18);
        level.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, arrival ? 64 : 40, 0.75, 0.95, 0.75, 0.12);
        level.sendParticles((ParticleOptions)ParticleTypes.END_ROD, pos.x, pos.y, pos.z, arrival ? 26 : 14, 0.55, 0.75, 0.55, 0.2);
        if (arrival) {
            level.sendParticles((ParticleOptions)ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnGojoTeleportFrame(ServerLevel level, ServerPlayer owner, Vec3 target, float yaw, float pitch) {
        ResourceLocation projectionId = new ResourceLocation("jujutsucraft", "entity_projection_sorcery");
        try {
            EntityType<?> projectionType = ForgeRegistries.ENTITY_TYPES.getValue(projectionId);
            if (projectionType == null) {
                return;
            }
            Entity projection = projectionType.create(level);
            if (projection == null) {
                return;
            }
            projection.moveTo(target.x, target.y, target.z, yaw, pitch);
            projection.setYRot(yaw);
            projection.setXRot(pitch);
            projection.setYHeadRot(yaw);
            projection.setYBodyRot(yaw);
            projection.setInvulnerable(true);
            projection.setNoGravity(true);
            projection.setGlowingTag(true);
            projection.setCustomName(Component.literal("FRAME1"));
            projection.setCustomNameVisible(false);
            if (projection instanceof Mob mob) {
                mob.setNoAi(true);
            }
            CompoundTag ownerData = owner.getPersistentData();
            double nameRanged = ownerData.getDouble("NameRanged");
            if (nameRanged == 0.0) {
                nameRanged = 1.0 + owner.getRandom().nextDouble() * 1000000.0;
                ownerData.putDouble("NameRanged", nameRanged);
            }
            CompoundTag frameData = projection.getPersistentData();
            frameData.putDouble("NameRanged_ranged", nameRanged);
            frameData.putDouble("cnt5", 1.0);
            frameData.putDouble("x_pos", target.x);
            frameData.putDouble("y_pos", target.y);
            frameData.putDouble("z_pos", target.z);
            frameData.putString("owner_name", owner.getScoreboardName());
            frameData.putString("OWNER_UUID", owner.getUUID().toString());
            frameData.putString("OwnerUUID", owner.getUUID().toString());
            frameData.putString("owner_uuid", owner.getUUID().toString());
            BlueRedPurpleNukeMod.configureProjectionSorceryVisual(projection);
            level.addFreshEntity(projection);
        }
        catch (Exception ignored) {
        }
    }

    private static void configureProjectionSorceryVisual(Entity projection) {
        BlueRedPurpleNukeMod.invokeProjectionMethod(projection, "setTexture", new Class<?>[]{String.class}, new Object[]{"entity_projection_sorcery"});
        if (!BlueRedPurpleNukeMod.invokeProjectionMethod(projection, "setDATA_cnt_skin", new Class<?>[]{double.class}, new Object[]{3.0})) {
            BlueRedPurpleNukeMod.invokeProjectionMethod(projection, "setDATA_cnt_skin", new Class<?>[]{Double.TYPE}, new Object[]{3.0});
        }
    }

    private static boolean invokeProjectionMethod(Entity projection, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = projection.getClass().getMethod(methodName, parameterTypes);
            method.invoke(projection, args);
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    // ===== INFINITY CRUSHER =====
    private static void handleInfinityCrusher(ServerPlayer player) {
        double ct;
        if (player.getPersistentData().getLong(GOJO_TP_CRUSHER_SUPPRESS_UNTIL) >= player.serverLevel().getGameTime()) {
            return;
        }
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
        double ceDrain = CRUSHER_BASE_CE_DRAIN + (double)activeTicks * CRUSHER_CE_DRAIN_GROWTH;
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
        double currentRadius = CRUSHER_MIN_RADIUS + (CRUSHER_MAX_RADIUS - CRUSHER_MIN_RADIUS) * growthFactor;
        double effectiveCEDrained = BlueRedPurpleNukeMod.getSoftCappedValue(totalCEDrained, CRUSHER_CE_DAMAGE_SOFT_CAP, CRUSHER_CE_DAMAGE_OVERFLOW_WEIGHT);
        float wallDamage = (float)((double)CRUSHER_BASE_WALL_DAMAGE + growthFactor * 2.5 + effectiveCEDrained * CRUSHER_CE_DAMAGE_SCALE);
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
                if (activeTicks % CRUSHER_LOCK_DAMAGE_INTERVAL != 0) continue;
                int hitIndex = BlueRedPurpleNukeMod.nextInfinityCrusherDamageHit(player);
                float scaledDamage = (float)((double)wallDamage * BlueRedPurpleNukeMod.getInfinityCrusherDamageScaleForHit((LivingEntity)player, hitIndex));
                mob.hurt(serverLevel.damageSources().generic(), scaledDamage);
                BlueRedPurpleNukeMod.spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
                if (activeTicks % (CRUSHER_LOCK_DAMAGE_INTERVAL * 2) != 0) continue;
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
            if (dot < CRUSHER_CONE_COS || !isMovingForward) continue;
            mob.getPersistentData().putInt("addon_crusher_contact_ticks", ++contactTicks);
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
            if (!mob.horizontalCollision && !mob.verticalCollision || !(dist < currentRadius + 1.5)) continue;
            if (activeTicks % CRUSHER_COLLISION_DAMAGE_INTERVAL != 0) continue;
            int hitIndex = BlueRedPurpleNukeMod.nextInfinityCrusherDamageHit(player);
            float scaledDamage = (float)((double)(wallDamage * 0.5f) * BlueRedPurpleNukeMod.getInfinityCrusherDamageScaleForHit((LivingEntity)player, hitIndex));
            mob.hurt(serverLevel.damageSources().generic(), scaledDamage);
            BlueRedPurpleNukeMod.spawnCrushVFX(serverLevel, mob, auraColor, growthFactor);
            if (activeTicks % (CRUSHER_COLLISION_DAMAGE_INTERVAL * 2) != 0) continue;
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
        data.putInt("addon_infinity_crusher_damage_hits", 0);
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
                BlueRedPurpleNukeMod.applyBlueLingerControl(sl, blueEntity, timer, new Vec3(lx, ly, lz));
            }
            if (timer >= 200) {
                if (level instanceof ServerLevel) {
                    BlueRedPurpleNukeMod.releaseBlueFullChargeTargets((ServerLevel)level, blueEntity);
                }
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
                BlueRedPurpleNukeMod.handleCrouchFullChargeBlueAim(blueEntity);
            }
        }
        if (cnt6 < 5.0) {
            return;
        }
        if (circle && !aiming && aimEnded) {
            if (blueEntity.level() instanceof ServerLevel) {
                BlueRedPurpleNukeMod.releaseBlueFullChargeTargets((ServerLevel)blueEntity.level(), blueEntity);
            }
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
        blueEntity.getPersistentData().putInt("linger_damage_ticks", BlueRedPurpleNukeMod.getOriginalBlueLingerDamageTicks(circle, aimEnded, cnt6, blueEntity.getPersistentData().getDouble("cnt1")));
        blueEntity.getPersistentData().putDouble("cnt1", 0.0);
        blueEntity.getPersistentData().putDouble("NameRanged_ranged", 0.0);
    }

    private static int getOriginalBlueLingerDamageTicks(boolean circle, boolean aimEnded, double cnt6, double currentCnt1) {
        if (circle && aimEnded) {
            return 45;
        }
        double CNT6 = 1.0 + Math.max(cnt6, 0.0) * 0.1;
        double originalLimit = 30.0 * CNT6;
        return Math.max(0, Math.min(45, (int)Math.ceil(originalLimit - currentCnt1 + 1.0)));
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
     * Keeps addon-controlled Blue linger as the authoritative pull-and-DPS state after the base Blue AI is suppressed.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param timer current linger lifetime in ticks.
     * @param orbPos authoritative linger position.
     */
    private static void applyBlueLingerControl(ServerLevel serverLevel, LivingEntity blueEntity, int timer, Vec3 orbPos) {
        String ownerUUID = blueEntity.getPersistentData().getString("OWNER_UUID");
        LivingEntity owner = ownerUUID.isEmpty() ? null : BlueRedPurpleNukeMod.resolveOwner(serverLevel, ownerUUID);
        double cnt6 = Math.max(blueEntity.getPersistentData().getDouble("linger_cnt6"), blueEntity.getPersistentData().getDouble("cnt6"));
        double cntFactor = 1.0 + Math.max(cnt6, 0.0) * 0.1;
        double pullRadius = Math.max(3.5, 2.8 + cnt6 * 0.6);
        if (timer <= blueEntity.getPersistentData().getInt("linger_damage_ticks")) {
            BlueRedPurpleNukeMod.applyOriginalBlueDamageTick(serverLevel, blueEntity, cnt6, orbPos);
        }
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(orbPos, orbPos).inflate(pullRadius), e -> e.isAlive() && e != blueEntity && !BlueRedPurpleNukeMod.isOwnerOrOwnedRika(e, owner) && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        for (LivingEntity target : nearby) {
            Vec3 delta = orbPos.subtract(target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0));
            double dist = delta.length();
            if (dist < 0.001 || dist > pullRadius) {
                continue;
            }
            double falloff = 1.0 - Math.min(1.0, dist / pullRadius);
            Vec3 pull = delta.normalize().scale((0.18 + falloff * 0.32) * cntFactor);
            target.setDeltaMovement(target.getDeltaMovement().scale(0.35).add(pull));
            target.hurtMarked = true;
            if (dist < 1.4) {
                Vec3 snap = orbPos.add(delta.normalize().scale(-0.6));
                target.teleportTo(snap.x, snap.y - (double)target.getBbHeight() * 0.25, snap.z);
            }
        }
    }

    private static void removeBlackFlashBlessingWaveSessions(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (BF_BLESSING_WAVE_SESSIONS) {
            BF_BLESSING_WAVE_SESSIONS.removeIf(session -> playerId.equals(session.playerUuid));
        }
    }

    private static void applyOriginalBlueDamageTick(ServerLevel serverLevel, LivingEntity blueEntity, double cnt6, Vec3 orbPos) {
        double CNT6 = 1.0 + Math.max(cnt6, 0.0) * 0.1;
        CompoundTag data = blueEntity.getPersistentData();
        data.putDouble("Damage", 13.0 * CNT6);
        data.putDouble("Range", 4.0 * CNT6);
        data.putDouble("knockback", 0.0);
        RangeAttackProcedure.execute((LevelAccessor)serverLevel, orbPos.x, orbPos.y, orbPos.z, (Entity)blueEntity);
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

    private static boolean isOwnerOrOwnedRika(Entity candidate, LivingEntity owner) {
        if (candidate == null || owner == null) {
            return false;
        }
        if (candidate == owner || candidate.getUUID().equals(owner.getUUID())) {
            return true;
        }
        CompoundTag candidateData = candidate.getPersistentData();
        CompoundTag ownerData = owner.getPersistentData();
        double ownerFriend = ownerData.getDouble("friend_num");
        double candidateFriend = candidateData.getDouble("friend_num");
        if (ownerFriend != 0.0 && Math.abs(ownerFriend - candidateFriend) < 0.001) {
            return true;
        }
        if (!YutaCopyStore.isRikaEntity(candidate)) {
            return false;
        }
        String ownerUuid = owner.getStringUUID();
        String storedOwnerUuid = candidateData.getString("OWNER_UUID");
        if (!storedOwnerUuid.isBlank() && ownerUuid.equals(storedOwnerUuid)) {
            return true;
        }
        String storedRikaUuid = ownerData.getString("RIKA_UUID");
        if (!storedRikaUuid.isBlank() && storedRikaUuid.equals(candidate.getStringUUID())) {
            return true;
        }
        return false;
    }

    // ===== FULL-CHARGE BLUE VARIANT =====
    public static void handleCrouchFullChargeBlueAim(LivingEntity blueEntity) {
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
        double ownerScale = BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner);
        double rankBonus = Math.max(0.0, ownerScale - 1.0);
        double pullRadius = BLUE_FULL_PULL_RADIUS * (1.0 + rankBonus * 0.35);
        double pullStrength = BLUE_FULL_PULL_STRENGTH * (1.0 + rankBonus * 0.45);
        Player playerOwner = (Player)owner;
        Vec3 eye = playerOwner.getEyePosition(1.0f);
        Vec3 rawLook = playerOwner.getLookAngle();
        Vec3 look = rawLook.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : rawLook.normalize();
        Vec3 currentPos = blueEntity.position();
        Vec3 desiredPos = BlueRedPurpleNukeMod.getBlueFullChargeAimTargetPosition(serverLevel, playerOwner, blueEntity, eye, look);
        Vec3 orbTravel = desiredPos.subtract(currentPos);
        Vec3 newPos = desiredPos;
        if (orbTravel.length() > 1.1) {
            newPos = currentPos.add(orbTravel.normalize().scale(1.1));
            orbTravel = desiredPos.subtract(newPos);
        }
        blueEntity.teleportTo(newPos.x, newPos.y, newPos.z);
        blueEntity.setDeltaMovement(BlueRedPurpleNukeMod.limitVectorLength(newPos.subtract(currentPos), 0.35));
        blueEntity.getPersistentData().putDouble("x_pos", newPos.x);
        blueEntity.getPersistentData().putDouble("y_pos", newPos.y);
        blueEntity.getPersistentData().putDouble("z_pos", newPos.z);
        blueEntity.getPersistentData().putDouble("x_power", look.x * 0.2);
        blueEntity.getPersistentData().putDouble("y_power", look.y * 0.2);
        blueEntity.getPersistentData().putDouble("z_power", look.z * 0.2);
        blueEntity.setYRot(playerOwner.getYRot());
        blueEntity.setXRot(playerOwner.getXRot());
        blueEntity.addEffect(new MobEffectInstance(MobEffects.LUCK, 5, 15, false, false));
        int aimTicks = blueEntity.getPersistentData().getInt("addon_aim_ticks");
        BlueRedPurpleNukeMod.emitCrouchFullChargeBlueOgEffects(serverLevel, blueEntity, newPos, aimTicks);
        BlueRedPurpleNukeMod.pullMobsWithCrouchFullChargeBlue(serverLevel, blueEntity, owner, newPos, look, pullRadius, pullStrength);
        if (aimTicks > 0 && aimTicks % BLUE_BLOCK_BREAK_INTERVAL == 0) {
            BlueRedPurpleNukeMod.applyCrouchFullChargeBlueOgBlockBreak(serverLevel, blueEntity, newPos, aimTicks);
        }
    }

    /**
     * Restores the narrow OG Blue ambient effect slice that the aim mixin suppresses, without re-entering the original AI gameplay path.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param orbPos authoritative aimed orb position.
     * @param aimTicks current addon aim tick counter.
     */
    private static void emitCrouchFullChargeBlueOgEffects(ServerLevel serverLevel, LivingEntity blueEntity, Vec3 orbPos, int aimTicks) {
        double cnt6 = Math.max(5.0, blueEntity.getPersistentData().getDouble("cnt6"));
        double size = 0.0;
        if (blueEntity.getAttributes().hasAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()) && blueEntity.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()) != null) {
            size = blueEntity.getAttribute((Attribute)JujutsucraftModAttributes.SIZE.get()).getValue();
        }
        double dis = Math.max(8.0, Math.max(size * 10.0, cnt6 * 2.0));
        BlueRedPurpleNukeMod.emitCrouchFullChargeBlueOgSuctionParticles(serverLevel, orbPos, dis);
        if (aimTicks != 1) {
            return;
        }
        SoundEvent gatewaySpawn = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.end_gateway.spawn"));
        if (gatewaySpawn == null) {
            return;
        }
        BlockPos soundPos = BlockPos.containing((Position)orbPos);
        float volume = (float)(1.5 + (1.0 + cnt6 * 0.1));
        serverLevel.playSound(null, soundPos, gatewaySpawn, SoundSource.NEUTRAL, volume, 1.0f);
        serverLevel.playSound(null, soundPos, gatewaySpawn, SoundSource.NEUTRAL, volume, 0.5f);
    }

    /**
     * Recreates the OG full-charge Blue `minecraft:enchanted_hit` suction streaks on the addon-only crouch path.
     * @param serverLevel level value used by this operation.
     * @param orbPos authoritative aimed orb position.
     * @param dis resolved OG-like visual radius scalar.
     */
    private static void emitCrouchFullChargeBlueOgSuctionParticles(ServerLevel serverLevel, Vec3 orbPos, double dis) {
        int burstCount = 8;
        double radius = Math.max(8.0, Math.min(48.0, dis * 2.0));
        for (int i = 0; i < burstCount; ++i) {
            double startX = orbPos.x + (Math.random() - 0.5) * radius;
            double startY = orbPos.y + (Math.random() - 0.5) * radius;
            double startZ = orbPos.z + (Math.random() - 0.5) * radius;
            Vec3 pull = orbPos.subtract(startX, startY, startZ);
            double length = pull.length();
            if (length < 0.001) {
                continue;
            }
            Vec3 velocity = pull.scale(1.0 / length);
            serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(startX, startY, startZ), Vec2.ZERO, serverLevel, 4, "", (Component)Component.literal((String)""), serverLevel.getServer(), null).withSuppressedOutput(), String.format(Locale.ROOT, "particle minecraft:enchanted_hit ~ ~ ~ %.4f %.4f %.4f 0.0025 0 force", velocity.x * 10000.0, velocity.y * 10000.0, velocity.z * 10000.0));
        }
    }

    /**
     * Performs pull mobs with crouch full charge blue for this addon component.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param owner entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param lookDir look dir used by this method.
     */
    private static void pullMobsWithCrouchFullChargeBlue(ServerLevel serverLevel, LivingEntity blueEntity, LivingEntity owner, Vec3 orbPos, Vec3 lookDir, double pullRadius, double pullStrength) {
        double effectivePullRadius = Math.max(0.1, pullRadius);
        double effectivePullStrength = Math.max(0.0, pullStrength);
        Vec3 normalizedLook = lookDir.lengthSqr() > 1.0E-6 ? lookDir.normalize() : new Vec3(0.0, 0.0, 1.0);
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(orbPos, orbPos).inflate(effectivePullRadius), e -> e.isAlive() && e != blueEntity && !BlueRedPurpleNukeMod.isOwnerOrOwnedRika(e, owner) && !(e instanceof BlueEntity) && !(e instanceof RedEntity) && !(e instanceof PurpleEntity));
        String blueLockUuid = blueEntity.getUUID().toString();
        long gameTime = serverLevel.getGameTime();
        for (LivingEntity entity : nearby) {
            boolean lockedByThisBlue = BlueRedPurpleNukeMod.isBlueFullChargeLockedBy(entity, blueLockUuid);
            Vec3 entityCenter = entity.position().add(0.0, (double)entity.getBbHeight() * 0.5, 0.0);
            Vec3 toOrb = orbPos.subtract(entityCenter);
            double d = toOrb.length();
            if (d > effectivePullRadius || d < 0.001) {
                if (lockedByThisBlue) {
                    BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
                }
                continue;
            }
            Vec3 lockPos = BlueRedPurpleNukeMod.getBlueFullChargeLockAnchor(orbPos, normalizedLook, owner, entity);
            if (!lockedByThisBlue && d <= BLUE_FULL_LOCK_ENTER_RADIUS) {
                BlueRedPurpleNukeMod.beginBlueFullChargeTargetLock(serverLevel, entity, blueLockUuid);
                lockedByThisBlue = true;
                LOGGER.debug("[BlueFullChargeDiag] Locking target={} blue={} distToOrb={} distToLock={} motionBefore={}", new Object[]{entity.getName().getString(), blueLockUuid, d, lockPos.distanceTo(entityCenter), entity.getDeltaMovement()});
            }
            if (lockedByThisBlue) {
                BlueRedPurpleNukeMod.refreshBlueFullChargeTargetLock(entity.getPersistentData(), gameTime);
                if (d > Math.max(BLUE_FULL_LOCK_RELEASE_RADIUS, BLUE_FULL_LOCK_ENTER_RADIUS + 0.4)) {
                    LOGGER.debug("[BlueFullChargeDiag] Releasing target={} blue={} distToOrb={} motion={}", new Object[]{entity.getName().getString(), blueLockUuid, d, entity.getDeltaMovement()});
                    BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
                } else {
                    BlueRedPurpleNukeMod.applyBlueFullChargeLockMotion(entity, lockPos, owner);
                }
                continue;
            }
            double falloff = 1.0 - Math.min(1.0, d / effectivePullRadius);
            Vec3 pullTarget = d < BLUE_FULL_LOCK_RELEASE_RADIUS ? lockPos : orbPos.add(normalizedLook.scale(0.35));
            Vec3 pullDelta = pullTarget.subtract(entityCenter);
            if (pullDelta.lengthSqr() < 1.0E-6) {
                pullDelta = toOrb;
            }
            double desiredSpeed = Math.min(BLUE_FULL_PULL_MAX_SPEED, 0.14 + effectivePullStrength * (0.08 + falloff * 0.16));
            Vec3 desiredMotion = pullDelta.normalize().scale(desiredSpeed);
            Vec3 blendedMotion = entity.getDeltaMovement().scale(0.72).add(desiredMotion.scale(0.28));
            blendedMotion = BlueRedPurpleNukeMod.applyBlueFullChargeOwnerAvoidance(owner, entity, pullTarget, blendedMotion);
            entity.setDeltaMovement(BlueRedPurpleNukeMod.limitVectorLength(blendedMotion, BLUE_FULL_PULL_MAX_SPEED));
            entity.hurtMarked = true;
        }
    }

    /**
     * Resolves the intended aim position for full-charge crouch Blue while keeping the orb within the supported front cone distance band.
     * @param serverLevel level value used by this operation.
     * @param playerOwner player instance involved in this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param eye eye position used by this helper.
     * @param look normalized owner look vector.
     * @return clamped target position for the current full-charge Blue aim tick.
     */
    private static Vec3 getBlueFullChargeAimTargetPosition(ServerLevel serverLevel, Player playerOwner, LivingEntity blueEntity, Vec3 eye, Vec3 look) {
        Vec3 rayEnd = eye.add(look.scale(BLUE_CROUCH_MAX_DISTANCE));
        BlockHitResult lookHit = serverLevel.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)playerOwner));
        double targetDist = BLUE_CROUCH_MAX_DISTANCE;
        if (lookHit != null && lookHit.getType() == HitResult.Type.BLOCK) {
            targetDist = Math.min(targetDist, eye.distanceTo(lookHit.getLocation()));
        }
        targetDist = Math.max(BLUE_CROUCH_MIN_DISTANCE, Math.min(BLUE_CROUCH_MAX_DISTANCE, targetDist));
        Vec3 current = blueEntity.position();
        Vec3 target = eye.add(look.scale(targetDist));
        Vec3 delta = target.subtract(current);
        Vec3 newPos = delta.length() > 1.1 ? current.add(delta.normalize().scale(1.1)) : target;
        Vec3 fromPlayer = newPos.subtract(eye);
        double distFromPlayer = fromPlayer.length();
        if (distFromPlayer < BLUE_CROUCH_MIN_DISTANCE) {
            Vec3 safeDir = fromPlayer.lengthSqr() > 1.0E-6 ? fromPlayer.normalize() : look;
            newPos = eye.add(safeDir.scale(BLUE_CROUCH_MIN_DISTANCE));
        } else if (distFromPlayer > BLUE_CROUCH_MAX_DISTANCE) {
            newPos = eye.add(fromPlayer.normalize().scale(BLUE_CROUCH_MAX_DISTANCE));
        }
        return newPos;
    }

    /**
     * Checks whether the specified target is currently locked by this Blue full-charge orb.
     * @param target entity instance being processed by this helper.
     * @param blueLockUuid identifier used to resolve runtime state for this operation.
     * @return true when the target is locked by the provided Blue orb UUID.
     */
    private static boolean isBlueFullChargeLockedBy(LivingEntity target, String blueLockUuid) {
        CompoundTag data = target.getPersistentData();
        return data.getBoolean(BLUE_FULL_LOCKED) && blueLockUuid.equals(data.getString(BLUE_FULL_LOCK_UUID));
    }

    /**
     * Enters the managed Blue full-charge lock state and enables NoGravity only once per target lifecycle.
     * @param serverLevel level value used by this operation.
     * @param target entity instance being processed by this helper.
     * @param blueLockUuid identifier used to resolve runtime state for this operation.
     */
    private static void beginBlueFullChargeTargetLock(ServerLevel serverLevel, LivingEntity target, String blueLockUuid) {
        CompoundTag data = target.getPersistentData();
        data.putString(BLUE_FULL_LOCK_UUID, blueLockUuid);
        data.putBoolean(BLUE_FULL_LOCKED, true);
        data.putLong(BLUE_FULL_LOCK_LAST_SEEN_TICK, serverLevel.getGameTime());
        if (!data.getBoolean(BLUE_FULL_LOCK_GRAVITY_MANAGED)) {
            target.setNoGravity(true);
            data.putBoolean(BLUE_FULL_LOCK_GRAVITY_MANAGED, true);
        }
    }

    /**
     * Refreshes the active Blue full-charge lock so orphaned targets can be detected by the validator.
     * @param data persistent data container used by this helper.
     * @param gameTime current server tick time.
     */
    private static void refreshBlueFullChargeTargetLock(CompoundTag data, long gameTime) {
        data.putBoolean(BLUE_FULL_LOCKED, true);
        data.putLong(BLUE_FULL_LOCK_LAST_SEEN_TICK, gameTime);
    }

    /**
     * Computes a stable hold anchor behind the orb while preserving a safe horizontal gap from the owner.
     * @param orbPos orb pos used by this method.
     * @param normalizedLook normalized owner look vector.
     * @param owner entity instance being processed by this helper.
     * @param target entity instance being processed by this helper.
     * @return safe hold anchor used for Blue's locked pull state.
     */
    private static Vec3 getBlueFullChargeLockAnchor(Vec3 orbPos, Vec3 normalizedLook, LivingEntity owner, LivingEntity target) {
        Vec3 safeLook = normalizedLook.lengthSqr() > 1.0E-6 ? normalizedLook.normalize() : new Vec3(0.0, 0.0, 1.0);
        Vec3 ownerCenter = owner.position().add(0.0, (double)owner.getBbHeight() * 0.5, 0.0);
        double minOwnerClearance = Math.max(BLUE_FULL_OWNER_CLEARANCE, (double)(owner.getBbWidth() + target.getBbWidth()) * 0.5 + 0.75);
        Vec3 anchor = orbPos.subtract(safeLook.scale(1.35));
        anchor = new Vec3(anchor.x, orbPos.y - (double)target.getBbHeight() * 0.3, anchor.z);
        Vec3 fromOwner = anchor.subtract(ownerCenter);
        double horizontalDistanceSq = fromOwner.x * fromOwner.x + fromOwner.z * fromOwner.z;
        if (horizontalDistanceSq < minOwnerClearance * minOwnerClearance) {
            Vec3 lateral = new Vec3(-safeLook.z, 0.0, safeLook.x);
            if (lateral.lengthSqr() < 1.0E-6) {
                lateral = new Vec3(1.0, 0.0, 0.0);
            }
            double side = Math.signum(target.getX() - owner.getX());
            if (side == 0.0) {
                side = 1.0;
            }
            Vec3 forwardAnchor = ownerCenter.add(safeLook.scale(minOwnerClearance + 0.5));
            anchor = forwardAnchor.add(lateral.normalize().scale(0.45 * side));
            anchor = new Vec3(anchor.x, orbPos.y - (double)target.getBbHeight() * 0.3, anchor.z);
        }
        return anchor;
    }

    /**
     * Applies a soft velocity-based hold motion for a locked Blue target instead of a hard snap or teleport.
     * @param target entity instance being processed by this helper.
     * @param lockPos lock anchor used by this helper.
     * @param owner entity instance being processed by this helper.
     */
    private static void applyBlueFullChargeLockMotion(LivingEntity target, Vec3 lockPos, LivingEntity owner) {
        Vec3 targetCenter = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0);
        Vec3 toLock = lockPos.subtract(targetCenter);
        double lockDistance = toLock.length();
        Vec3 desiredMotion;
        if (lockDistance > 0.03) {
            double holdSpeed = Mth.clamp(lockDistance * 0.2, 0.05, BLUE_FULL_LOCK_MAX_SPEED);
            desiredMotion = toLock.normalize().scale(holdSpeed);
        } else {
            desiredMotion = target.getDeltaMovement().scale(0.45);
        }
        Vec3 blendedMotion = target.getDeltaMovement().scale(0.55).add(desiredMotion.scale(0.45));
        blendedMotion = BlueRedPurpleNukeMod.applyBlueFullChargeOwnerAvoidance(owner, target, lockPos, blendedMotion);
        target.setDeltaMovement(BlueRedPurpleNukeMod.limitVectorLength(blendedMotion, BLUE_FULL_LOCK_MAX_SPEED));
        target.hurtMarked = true;
        if (target.tickCount % 20 == 0 && lockDistance > 0.35) {
            LOGGER.debug("[BlueFullChargeDiag] Holding target={} distToLock={} motion={}", new Object[]{target.getName().getString(), lockDistance, target.getDeltaMovement()});
        }
    }

    /**
     * Pushes Blue-held targets away from the owner if the proposed movement would collapse them into the caster's collision space.
     * @param owner entity instance being processed by this helper.
     * @param target entity instance being processed by this helper.
     * @param pullTarget pull target used by this helper.
     * @param proposedMotion proposed motion used by this helper.
     * @return owner-safe target motion.
     */
    private static Vec3 applyBlueFullChargeOwnerAvoidance(LivingEntity owner, LivingEntity target, Vec3 pullTarget, Vec3 proposedMotion) {
        Vec3 ownerCenter = owner.position().add(0.0, (double)owner.getBbHeight() * 0.5, 0.0);
        Vec3 targetCenter = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0);
        Vec3 nextCenter = targetCenter.add(proposedMotion);
        double minOwnerClearance = Math.max(BLUE_FULL_OWNER_CLEARANCE, (double)(owner.getBbWidth() + target.getBbWidth()) * 0.5 + 0.35);
        Vec3 fromOwner = nextCenter.subtract(ownerCenter);
        double horizontalDistance = Math.sqrt(fromOwner.x * fromOwner.x + fromOwner.z * fromOwner.z);
        if (horizontalDistance >= minOwnerClearance) {
            return proposedMotion;
        }
        Vec3 away = new Vec3(fromOwner.x, 0.0, fromOwner.z);
        if (away.lengthSqr() < 1.0E-6) {
            away = new Vec3(targetCenter.x - ownerCenter.x, 0.0, targetCenter.z - ownerCenter.z);
        }
        if (away.lengthSqr() < 1.0E-6) {
            away = new Vec3(-(pullTarget.z - ownerCenter.z), 0.0, pullTarget.x - ownerCenter.x);
        }
        if (away.lengthSqr() < 1.0E-6) {
            away = new Vec3(1.0, 0.0, 0.0);
        }
        double correction = minOwnerClearance - horizontalDistance + 0.05;
        return proposedMotion.add(away.normalize().scale(correction));
    }

    /**
     * Caps vector length without changing direction so Blue pull and hold movement stay smooth instead of snapping to large velocities.
     * @param motion motion vector used by this helper.
     * @param maxLength maximum allowed vector length.
     * @return clamped motion vector.
     */
    private static Vec3 limitVectorLength(Vec3 motion, double maxLength) {
        double safeMax = Math.max(0.0, maxLength);
        if (motion.lengthSqr() < 1.0E-6 || safeMax <= 0.0) {
            return Vec3.ZERO;
        }
        double length = motion.length();
        if (length <= safeMax) {
            return motion;
        }
        return motion.scale(safeMax / length);
    }

    /**
     * Replays the OG Blue two-pass terrain break on the addon-only crouch max-charge route so smoke, debris, and material sounds come from the original destroy procedure.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param aimTicks current addon aim tick counter.
     */
    private static void applyCrouchFullChargeBlueOgBlockBreak(ServerLevel serverLevel, LivingEntity blueEntity, Vec3 orbPos, int aimTicks) {
        double cnt6 = Math.max(5.0, blueEntity.getPersistentData().getDouble("cnt6"));
        double cntFactor = 1.0 + cnt6 * 0.1;
        double effectScale = 0.01;
        MobEffectInstance luck = blueEntity.getEffect(MobEffects.LUCK);
        if (luck != null) {
            effectScale += Math.min(luck.getAmplifier(), 30) * 0.0333;
        }
        BlueRedPurpleNukeMod.executeCrouchFullChargeBlueOgBlockBreakPass(serverLevel, blueEntity, orbPos, Math.min(7.0 * cntFactor, (double)aimTicks * 0.5), 5.0 * effectScale * cntFactor);
        BlueRedPurpleNukeMod.executeCrouchFullChargeBlueOgBlockBreakPass(serverLevel, blueEntity, orbPos, Math.min(9.0 * cntFactor, (double)aimTicks), 2.5 * effectScale * cntFactor);
    }

    /**
     * Invokes the original `BlockDestroyAllDirectionProcedure.execute(...)` with a temporary OG-like Blue payload, then restores any prior orb state so other addon paths remain untouched.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     * @param orbPos orb pos used by this method.
     * @param blockRange block range used by this method.
     * @param blockDamage block damage used by this method.
     */
    private static void executeCrouchFullChargeBlueOgBlockBreakPass(ServerLevel serverLevel, LivingEntity blueEntity, Vec3 orbPos, double blockRange, double blockDamage) {
        if (blockRange <= 0.5 || blockDamage <= 0.0) {
            return;
        }
        CompoundTag data = blueEntity.getPersistentData();
        boolean hadKnockback = data.contains("knockback");
        double oldKnockback = data.getDouble("knockback");
        boolean hadBlockRange = data.contains("BlockRange");
        double oldBlockRange = data.getDouble("BlockRange");
        boolean hadBlockDamage = data.contains("BlockDamage");
        double oldBlockDamage = data.getDouble("BlockDamage");
        boolean hadNoParticle = data.contains("noParticle");
        boolean oldNoParticle = data.getBoolean("noParticle");
        boolean hadNoEffect = data.contains("noEffect");
        boolean oldNoEffect = data.getBoolean("noEffect");
        data.putDouble("knockback", -1.0);
        data.putDouble("BlockRange", blockRange);
        data.putDouble("BlockDamage", blockDamage);
        data.putBoolean("noParticle", false);
        data.remove("noEffect");
        try {
            BlockDestroyAllDirectionProcedure.execute((LevelAccessor)serverLevel, orbPos.x, orbPos.y, orbPos.z, (Entity)blueEntity);
        }
        finally {
            if (hadKnockback) {
                data.putDouble("knockback", oldKnockback);
            } else {
                data.remove("knockback");
            }
            if (hadBlockRange) {
                data.putDouble("BlockRange", oldBlockRange);
            } else {
                data.remove("BlockRange");
            }
            if (hadBlockDamage) {
                data.putDouble("BlockDamage", oldBlockDamage);
            } else {
                data.remove("BlockDamage");
            }
            if (hadNoParticle) {
                data.putBoolean("noParticle", oldNoParticle);
            } else {
                data.remove("noParticle");
            }
            if (hadNoEffect) {
                data.putBoolean("noEffect", oldNoEffect);
            } else {
                data.remove("noEffect");
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
        return BlueRedPurpleNukeMod.getDamageFixEquivalentScale(player);
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
     * Converts a raw owner-rank multiplier into an Infinity-technique-specific damage scale with damped bonus retention and a hard cap.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param bonusWeight fraction of the rank bonus retained for this technique profile.
     * @param maxScale hard upper bound applied after damping.
     * @return balanced Infinity-technique damage scale.
     */
    public static double getInfinityTechniqueDamageScaleFromRank(double ownerRankScale, double bonusWeight, double maxScale) {
        if (ownerRankScale <= 1.0) {
            return 1.0;
        }
        double adjustedScale = 1.0 + (ownerRankScale - 1.0) * bonusWeight;
        return Math.max(1.0, Math.min(adjustedScale, maxScale));
    }

    /**
     * Returns direct owner-rank scaling with only a light repeated-pass reduction so sustained Infinity techniques still respect the user's real rank progression.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param passIndex one-based execute or hit count for the current cast.
     * @return lightly reduced direct owner-rank scale.
     */
    public static double getDirectOwnerRankScaleForPass(double ownerRankScale, int passIndex) {
        return BlueRedPurpleNukeMod.getInfinityTechniquePassScale(ownerRankScale, passIndex, DIRECT_RANK_PASS_FALLOFF, DIRECT_RANK_PASS_MIN_RETENTION);
    }

    /**
     * Returns the damped rank scale used by Blue multi-hit damage.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @return balanced Blue per-hit damage scale.
     */
    public static double getBlueMultiHitDamageScaleFromRank(double ownerRankScale) {
        return BlueRedPurpleNukeMod.getInfinityTechniqueDamageScaleFromRank(ownerRankScale, BLUE_MULTI_HIT_RANK_BONUS_WEIGHT, BLUE_MULTI_HIT_MAX_SCALE);
    }

    /**
     * Returns the damped rank scale used by Red burst damage.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @return balanced Red burst damage scale.
     */
    public static double getRedBurstDamageScaleFromRank(double ownerRankScale) {
        return BlueRedPurpleNukeMod.getInfinityTechniqueDamageScaleFromRank(ownerRankScale, RED_BURST_RANK_BONUS_WEIGHT, RED_BURST_MAX_SCALE);
    }

    /**
     * Returns the narrower rank scale used only by Red's crouch full-charge teleport finisher.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @return balanced Red shift-finisher damage scale.
     */
    public static double getRedShiftFinisherDamageScaleFromRank(double ownerRankScale) {
        return BlueRedPurpleNukeMod.getInfinityTechniqueDamageScaleFromRank(ownerRankScale, RED_SHIFT_FINISHER_RANK_BONUS_WEIGHT, RED_SHIFT_FINISHER_MAX_SCALE);
    }

    /**
     * Returns the damped rank scale used by Infinity Crusher damage.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @return balanced Infinity Crusher damage scale.
     */
    public static double getInfinityCrusherDamageScaleFromRank(double ownerRankScale) {
        return BlueRedPurpleNukeMod.getInfinityTechniqueDamageScaleFromRank(ownerRankScale, CRUSHER_RANK_BONUS_WEIGHT, CRUSHER_MAX_SCALE);
    }

    /**
     * Returns the damped rank scale used by Purple fusion damage before repeated-pass budgeting is applied.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @return balanced Purple fusion damage scale.
     */
    public static double getPurpleFusionDamageScaleFromRank(double ownerRankScale) {
        return BlueRedPurpleNukeMod.getInfinityTechniqueDamageScaleFromRank(ownerRankScale, PURPLE_FUSION_RANK_BONUS_WEIGHT, PURPLE_FUSION_MAX_SCALE);
    }

    /**
     * Applies a per-pass budget to the retained rank bonus so repeated execute calls from a single Infinity cast do not reuse the full progression multiplier every time.
     * @param dampedScale technique-specific rank scale after the first damping step.
     * @param passIndex one-based count of `RangeAttackProcedure.execute()` calls or sustained hit events for the current cast.
     * @param falloff additional bonus-loss factor per pass.
     * @param minRetention minimum retained share of the damped rank bonus.
     * @return pass-adjusted rank scale.
     */
    public static double getInfinityTechniquePassScale(double dampedScale, int passIndex, double falloff, double minRetention) {
        if (dampedScale <= 1.0) {
            return 1.0;
        }
        int clampedPassIndex = Math.max(1, passIndex);
        double retainedBonus = 1.0 / (1.0 + (double)(clampedPassIndex - 1) * Math.max(falloff, 0.0));
        retainedBonus = Math.max(Math.min(retainedBonus, 1.0), minRetention);
        return 1.0 + (dampedScale - 1.0) * retainedBonus;
    }

    /**
     * Returns Blue's per-pass rank scale so sustained pull ticks keep progression without reusing the full bonus on every execute call.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param passIndex one-based execute count for the current Blue cast.
     * @return pass-adjusted Blue rank scale.
     */
    public static double getBlueRankScaleForPass(double ownerRankScale, int passIndex) {
        return BlueRedPurpleNukeMod.getInfinityTechniquePassScale(BlueRedPurpleNukeMod.getBlueMultiHitDamageScaleFromRank(ownerRankScale), passIndex, BLUE_PASS_FALLOFF, BLUE_PASS_MIN_RETENTION);
    }

    /**
     * Returns Red's per-pass rank scale so multi-pass base Red variants keep progression without reusing the full bonus on every execute call.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param passIndex one-based execute count for the current Red cast.
     * @return pass-adjusted Red rank scale.
     */
    public static double getRedRankScaleForPass(double ownerRankScale, int passIndex) {
        return BlueRedPurpleNukeMod.getInfinityTechniquePassScale(BlueRedPurpleNukeMod.getRedBurstDamageScaleFromRank(ownerRankScale), passIndex, RED_PASS_FALLOFF, RED_PASS_MIN_RETENTION);
    }

    /**
     * Returns Red shift-finisher's per-pass rank scale so the teleport finisher keeps some owner progression but no longer inherits the full direct-owner multiplier.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param passIndex one-based execute count for the current Red shift-finisher cast.
     * @return pass-adjusted Red shift-finisher rank scale.
     */
    public static double getRedShiftFinisherRankScaleForPass(double ownerRankScale, int passIndex) {
        return BlueRedPurpleNukeMod.getInfinityTechniquePassScale(BlueRedPurpleNukeMod.getRedShiftFinisherDamageScaleFromRank(ownerRankScale), passIndex, RED_PASS_FALLOFF, RED_PASS_MIN_RETENTION);
    }

    /**
     * Returns Purple fusion's per-pass rank scale so the fusion keeps its finisher fantasy without multiplying full owner rank on every repeated base beam tick.
     * @param ownerRankScale raw owner rank multiplier resolved for the current attack.
     * @param passIndex one-based execute count for the current Purple fusion cast.
     * @return pass-adjusted Purple fusion rank scale.
     */
    public static double getPurpleFusionRankScaleForPass(double ownerRankScale, int passIndex) {
        return BlueRedPurpleNukeMod.getInfinityTechniquePassScale(BlueRedPurpleNukeMod.getPurpleFusionDamageScaleFromRank(ownerRankScale), passIndex, PURPLE_PASS_FALLOFF, PURPLE_PASS_MIN_RETENTION);
    }

    /**
     * Returns Infinity Crusher scaling for a specific sustained damage hit so repeated wall scrapes and locks do not reuse the full rank bonus every time.
     * @param owner entity instance being processed by this helper.
     * @param hitIndex one-based sustained hit count for the current Crusher use.
     * @return pass-adjusted Infinity Crusher rank scale.
     */
    public static double getInfinityCrusherDamageScaleForHit(LivingEntity owner, int hitIndex) {
        return BlueRedPurpleNukeMod.getDirectOwnerRankScaleForPass(BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner), hitIndex);
    }

    /**
     * Advances the shared Crusher hit counter used for sustained rank-budget damping.
     * @param player player instance involved in this operation.
     * @return one-based damage hit index for the current Crusher use.
     */
    private static int nextInfinityCrusherDamageHit(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int hitIndex = data.getInt("addon_infinity_crusher_damage_hits") + 1;
        data.putInt("addon_infinity_crusher_damage_hits", hitIndex);
        return hitIndex;
    }

    /**
     * Soft-caps an increasing value so overflow contributes only partially after the specified threshold.
     * @param value raw value being balanced.
     * @param softCap threshold where overflow damping begins.
     * @param overflowWeight retained fraction of overflow after the soft cap.
     * @return soft-capped value.
     */
    private static double getSoftCappedValue(double value, double softCap, double overflowWeight) {
        double clamped = Math.max(0.0, value);
        if (clamped <= softCap) {
            return clamped;
        }
        return softCap + (clamped - softCap) * overflowWeight;
    }

    /**
     * Returns Red burst scaling for the current owner using the shared Infinity-technique balance helper.
     * @param owner entity instance being processed by this helper.
     * @return balanced Red burst damage scale.
     */
    private static double getRedBurstDamageScale(LivingEntity owner) {
        return BlueRedPurpleNukeMod.getRedBurstDamageScaleFromRank(BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner));
    }

    /**
     * Returns Infinity Crusher scaling for the current owner using the shared Infinity-technique balance helper.
     * @param owner entity instance being processed by this helper.
     * @return balanced Infinity Crusher damage scale.
     */
    private static double getInfinityCrusherDamageScale(LivingEntity owner) {
        return BlueRedPurpleNukeMod.getInfinityCrusherDamageScaleFromRank(BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner));
    }

    /**
     * Returns normal red damage scale for the current addon state.
     * @param owner entity instance being processed by this helper.
     * @return the resolved normal red damage scale.
     */
    private static double getNormalRedDamageScale(LivingEntity owner) {
        return BlueRedPurpleNukeMod.getOwnerRankDamageScale(owner) * NORMAL_RED_DAMAGE_ADVANTAGE;
    }

    /**
     * Returns crouch Red's finisher damage so normal-flight Red can never drop below the shift-cast baseline.
     * @param cnt6 charge value used by the current Red cast.
     * @return shift-cast Red finisher baseline damage.
     */
    private static double getShiftRedExplosionBaseDamage(double cnt6) {
        double clampedCnt6 = Math.max(cnt6, 0.0);
        return 28.0 * (1.0 + clampedCnt6 * 0.1);
    }

    /**
     * Resolves how far the addon normal Red orb actually flew from its initial spawn position before detonation.
     * @param redEntity entity instance being processed by this helper.
     * @param currentPos tracked explosion position for the orb.
     * @return real travel distance, or tracked fallback travel when spawn data is unavailable.
     */
    private static double getNormalRedDistanceTraveled(LivingEntity redEntity, Vec3 currentPos) {
        CompoundTag data = redEntity.getPersistentData();
        if (data.contains("addon_red_spawn_x") && data.contains("addon_red_spawn_y") && data.contains("addon_red_spawn_z")) {
            double spawnX = data.getDouble("addon_red_spawn_x");
            double spawnY = data.getDouble("addon_red_spawn_y");
            double spawnZ = data.getDouble("addon_red_spawn_z");
            double dx = currentPos.x - spawnX;
            double dy = currentPos.y - spawnY;
            double dz = currentPos.z - spawnZ;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return Math.max(0.0, data.getDouble("addon_red_travel"));
    }

    /**
     * Normalizes Red's flight distance into a capped 0..1 factor used for distance-based bonuses.
     * @param distanceTraveled measured orb flight distance.
     * @return capped distance factor.
     */
    private static double getNormalRedDistanceFactor(double distanceTraveled) {
        return Math.min(Math.max(distanceTraveled, 0.0) / NORMAL_RED_DISTANCE_BONUS_MAX_DISTANCE, 1.0);
    }

    /**
     * Returns the damage multiplier granted by Red's travelled distance.
     * @param distanceFactor normalized flight factor.
     * @return distance-scaled damage multiplier.
     */
    private static double getNormalRedDistanceMultiplier(double distanceFactor) {
        return 1.0 + Math.max(0.0, distanceFactor) * NORMAL_RED_DISTANCE_DAMAGE_BONUS;
    }

    /**
     * Returns the radius multiplier granted by Red's travelled distance.
     * @param distanceFactor normalized flight factor.
     * @return distance-scaled radius multiplier.
     */
    private static double getNormalRedRadiusMultiplier(double distanceFactor) {
        return 1.0 + Math.max(0.0, distanceFactor) * NORMAL_RED_DISTANCE_RADIUS_BONUS;
    }

    /**
     * Returns the knockback multiplier granted by Red's travelled distance.
     * @param distanceFactor normalized flight factor.
     * @return distance-scaled knockback multiplier.
     */
    private static double getNormalRedKnockbackMultiplier(double distanceFactor) {
        return 1.0 + Math.max(0.0, distanceFactor) * NORMAL_RED_DISTANCE_KNOCKBACK_BONUS;
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

    /**
     * Releases an invalid or expired full-charge Blue gravity lock during server living ticks.
     * @param entity entity instance being processed by this helper.
     */
    private static void handleBlueFullChargeLockTick(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        boolean hasManagedLockState = data.contains(BLUE_FULL_LOCK_UUID) || data.getBoolean(BLUE_FULL_LOCKED) || data.getBoolean(BLUE_FULL_LOCK_GRAVITY_MANAGED);
        if (!hasManagedLockState) {
            return;
        }
        Level level = entity.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        if (data.getBoolean(BLUE_FULL_LOCK_GRAVITY_MANAGED) && !data.contains(BLUE_FULL_LOCK_UUID)) {
            BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
            return;
        }
        String blueUuid = data.getString(BLUE_FULL_LOCK_UUID);
        if (blueUuid.isEmpty()) {
            BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
            return;
        }
        long currentGameTime = serverLevel.getGameTime();
        long lastSeenTick = data.contains(BLUE_FULL_LOCK_LAST_SEEN_TICK) ? data.getLong(BLUE_FULL_LOCK_LAST_SEEN_TICK) : currentGameTime;
        if (currentGameTime - lastSeenTick > (long)BLUE_FULL_LOCK_ORPHAN_TIMEOUT_TICKS) {
            BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
            return;
        }
        try {
            Entity blueEntity = serverLevel.getEntity(UUID.fromString(blueUuid));
            if (!(blueEntity instanceof BlueEntity) || !blueEntity.isAlive() || blueEntity.isRemoved()) {
                BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
                return;
            }
            CompoundTag blueData = blueEntity.getPersistentData();
            if (!blueData.getBoolean("addon_aim_active") || blueData.getBoolean("aim_ended") || !blueData.getBoolean("circle")) {
                BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
            }
        }
        catch (Exception exception) {
            BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(entity);
        }
    }

    /**
     * Releases every target currently locked by the specified full-charge Blue orb.
     * @param serverLevel level value used by this operation.
     * @param blueEntity entity instance being processed by this helper.
     */
    private static void releaseBlueFullChargeTargets(ServerLevel serverLevel, LivingEntity blueEntity) {
        String blueUuid = blueEntity.getUUID().toString();
        List<LivingEntity> lockedTargets = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(blueEntity.position(), blueEntity.position()).inflate(128.0), e -> blueUuid.equals(e.getPersistentData().getString(BLUE_FULL_LOCK_UUID)));
        for (LivingEntity target : lockedTargets) {
            BlueRedPurpleNukeMod.releaseBlueFullChargeTarget(target);
        }
    }

    /**
     * Restores a target previously pinned by full-charge Blue and removes the persistent lock markers.
     * @param target entity instance being processed by this helper.
     */
    private static void releaseBlueFullChargeTarget(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        boolean managedGravity = data.getBoolean(BLUE_FULL_LOCK_GRAVITY_MANAGED);
        boolean hadManagedLock = data.contains(BLUE_FULL_LOCK_UUID) || data.getBoolean(BLUE_FULL_LOCKED) || managedGravity;
        if (managedGravity) {
            target.setNoGravity(false);
        }
        data.remove(BLUE_FULL_LOCK_UUID);
        data.remove(BLUE_FULL_LOCKED);
        data.remove(BLUE_FULL_LOCK_GRAVITY_MANAGED);
        data.remove(BLUE_FULL_LOCK_LAST_SEEN_TICK);
        if (hadManagedLock) {
            Vec3 releaseMotion = target.getDeltaMovement();
            if (releaseMotion.lengthSqr() > 1.0E-6) {
                releaseMotion = new Vec3(releaseMotion.x * 0.65, Math.min(releaseMotion.y, 0.18), releaseMotion.z * 0.65);
            }
            target.setDeltaMovement(releaseMotion);
            target.hurtMarked = true;
        }
    }
}




