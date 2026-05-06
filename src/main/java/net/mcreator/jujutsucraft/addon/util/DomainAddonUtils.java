package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Central utility class for domain-related runtime state in the addon.
 *
 * <p>This class acts as the source of truth for domain calculations and state checks used by
 * multiple systems, including radius resolution, center lookup, owner resolution for spawned
 * projectiles or orb entities, domain activity checks, combat lock rules, effect duration hacks,
 * and long-distance particle dispatch.</p>
 */
public class DomainAddonUtils {
    /** Base fallback radius used when no runtime or persisted domain radius can be resolved. */
    private static final double DEFAULT_DOMAIN_RADIUS = 16.0;

    /** Grace window, in ticks, for considering a player combat-tagged after the last combat event. */
    private static final int DOMAIN_MASTERY_COMBAT_TAG_GRACE_TICKS = 100;

    /** Persistent data key storing a generic domain expansion state value. */
    private static final String TAG_DOMAIN_EXPANSION = "DomainExpansion";

    /** Persistent data key storing the last recorded combat tick for addon combat-tag tracking. */
    private static final String TAG_LAST_COMBAT_TICK = "addon_bf_last_combat_tick";

    /** Persistent data key used to mark the temporary BF bonus granted during a domain flow. */
    private static final String TAG_DOMAIN_BF_BONUS = "jjkbrp_domain_bf_bonus";

    /** Persistent data key storing a projectile or helper entity owner's UUID. */
    private static final String TAG_OWNER_UUID = "OWNER_UUID";

    /** Persistent data key storing a numeric owner marker used by the original mod. */
    private static final String TAG_NAME_RANGED = "NameRanged";

    /** Persistent data key storing the ranged-name marker copied onto spawned entities. */
    private static final String TAG_NAME_RANGED_RANGED = "NameRanged_ranged";

    /** Search radius used when resolving an owner from the NameRanged marker instead of UUID. */
    private static final double OWNER_NAME_RANGED_SEARCH_RADIUS = 256.0;

    /**
     * Cached reflective handle for the long-distance particle overload on {@link ServerLevel}.
     *
     * <p>This method is not always available in every mapping/runtime combination, so it is
     * resolved once and reused.</p>
     */
    private static final Method LONG_DISTANCE_PARTICLE_METHOD = DomainAddonUtils.resolveLongDistanceParticleMethod();

    /** Shared logger used for domain diagnostics, especially owner-resolution tracing. */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Removes temporary BF boost bookkeeping created during domain logic.
     *
     * <p>This cleans both the addon marker and the mirrored {@code cnt6} value adjustment so the
     * caster's persistent state returns to its pre-domain baseline.</p>
     *
     * @param caster the entity whose temporary BF boost should be removed
     */
    public static void cleanupDomainRuntimeState(LivingEntity caster) { cleanupBFBoost(caster); if (caster != null) clearIncompleteDomainData(caster.getPersistentData()); }

    public static void cleanupBFBoost(LivingEntity caster) {
        if (caster == null) {
            return;
        }
        CompoundTag nbt = caster.getPersistentData();

        // Remove the high-level marker first so other systems stop treating the bonus as active.
        nbt.remove(TAG_DOMAIN_BF_BONUS);
        if (nbt.contains("jjkbrp_bf_cnt6_boost")) {
            double boost = nbt.getDouble("jjkbrp_bf_cnt6_boost");
            if (boost > 0.0) {
                // Clamp to zero to avoid underflow if the stored boost exceeds the current cnt6 value.
                nbt.putDouble("cnt6", Math.max(0.0, nbt.getDouble("cnt6") - boost));
            }
            // Remove the temporary boost snapshot after applying the rollback.
            nbt.remove("jjkbrp_bf_cnt6_boost");
        }
    }

    /**
     * Removes all addon incomplete-domain transient data, including both the
     * current thin-form markers and legacy BFS/runtime keys from older builds.
     *
     * @param nbt the persistent tag to clean
     */
    public static void clearIncompleteDomainData(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        nbt.remove("jjkbrp_incomplete_penalty_per_tick");
        nbt.remove("jjkbrp_incomplete_surface_multiplier");
        nbt.remove("jjkbrp_incomplete_form_active");
        nbt.remove("jjkbrp_incomplete_session_active");
        nbt.remove("jjkbrp_incomplete_cancelled");
        nbt.remove("jjkbrp_incomplete_runtime_id");
        nbt.remove("jjkbrp_incomplete_runtime_version");
        nbt.remove("jjkbrp_incomplete_runtime_center_x");
        nbt.remove("jjkbrp_incomplete_runtime_center_y");
        nbt.remove("jjkbrp_incomplete_runtime_center_z");
        nbt.remove("jjkbrp_incomplete_runtime_radius");
        nbt.remove("jjkbrp_incomplete_runtime_frontier");
        nbt.remove("jjkbrp_incomplete_runtime_next_frontier");
        nbt.remove("jjkbrp_incomplete_runtime_visited");
        nbt.remove("jjkbrp_incomplete_runtime_perimeter_index");
        nbt.remove("jjkbrp_incomplete_runtime_perimeter_passes");
        nbt.remove("jjkbrp_incomplete_runtime_next_floor_advance_tick");
        nbt.remove("jjkbrp_incomplete_runtime_floor_plan");
        nbt.remove("jjkbrp_incomplete_runtime_floor_plan_index");
        nbt.remove("jjkbrp_incomplete_runtime_active_shell");
        nbt.remove("jjkbrp_incomplete_runtime_next_shell_advance_tick");
        nbt.remove("jjkbrp_incomplete_runtime_last_tick");
        nbt.remove("jjkbrp_incomplete_runtime_blocked_edges");
        nbt.remove("jjkbrp_incomplete_runtime_revisits");
        nbt.remove("jjkbrp_incomplete_runtime_walls");
        nbt.remove("jjkbrp_incomplete_runtime_complete");
        nbt.remove("jjkbrp_ig2_version");
        nbt.remove("jjkbrp_ig2_runtime_id");
        nbt.remove("jjkbrp_ig2_center_x");
        nbt.remove("jjkbrp_ig2_center_y");
        nbt.remove("jjkbrp_ig2_center_z");
        nbt.remove("jjkbrp_ig2_seed_floor_y");
        nbt.remove("jjkbrp_ig2_radius");
        nbt.remove("jjkbrp_ig2_stage");
        nbt.remove("jjkbrp_ig2_last_tick");
        nbt.remove("jjkbrp_ig2_frontier");
        nbt.remove("jjkbrp_ig2_visited");
        nbt.remove("jjkbrp_ig2_blocked_edges");
        nbt.remove("jjkbrp_ig2_walls");
        nbt.remove("jjkbrp_ig2_dome_state");
        nbt.remove("jjkbrp_ig2_dome_cursor");
        nbt.remove("jjkbrp_ig2_roof_frontier");
        nbt.remove("jjkbrp_ig2_roof_visited");
        nbt.remove("jjkbrp_ig2_roof_seeded");
    }

    /**
     * Resolves the actual domain radius used by gameplay systems.
     *
     * <p>The method first prefers persisted per-entity runtime values, then falls back to the map
     * variable radius maintained by the original mod, and finally to the addon default radius if
     * all other lookups fail.</p>
     *
     * @param world the world context used for map-variable fallback lookup
     * @param nbt the entity persistent data containing possible radius overrides
     * @return the resolved radius, always clamped to at least {@code 1.0}
     */
    public static double getActualDomainRadius(LevelAccessor world, CompoundTag nbt) {
        if (nbt != null && nbt.contains("jjkbrp_base_domain_radius")) {
            double baseRadius = nbt.getDouble("jjkbrp_base_domain_radius");
            double radiusMultiplier = nbt.getDouble("jjkbrp_radius_multiplier");

            // Treat near-zero multipliers as invalid data and fall back to the neutral multiplier.
            if (Math.abs(radiusMultiplier) < 1.0E-4) {
                radiusMultiplier = 1.0;
            }

            // Prevent extremely small or negative persisted multipliers from collapsing the domain.
            radiusMultiplier = Math.max(0.5, radiusMultiplier);
            return Math.max(1.0, baseRadius * radiusMultiplier);
        }
        try {
            // Fall back to the original global map variable when no entity-specific value exists.
            return Math.max(1.0, JujutsucraftModVariables.MapVariables.get((LevelAccessor)world).DomainExpansionRadius);
        }
        catch (Exception ignored) {
            // Final safety fallback for missing map data or unexpected runtime failures.
            return 16.0;
        }
    }

    /**
     * Resolves the center point for a domain tied to the given entity.
     *
     * <p>Closed domains use the original {@code x_pos_doma/y_pos_doma/z_pos_doma} keys. Open
     * domains can also store a separate center, which is checked as a secondary fallback. If no
     * saved center exists, the entity's current position is returned.</p>
     *
     * @param entity the entity whose domain center should be resolved
     * @return the saved domain center, {@link Vec3#ZERO} for null input, or the entity position
     */
    public static Vec3 getDomainCenter(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.contains("x_pos_doma")) {
            return new Vec3(nbt.getDouble("x_pos_doma"), nbt.getDouble("y_pos_doma"), nbt.getDouble("z_pos_doma"));
        }
        if (nbt.contains("jjkbrp_open_domain_cx")) {
            return new Vec3(nbt.getDouble("jjkbrp_open_domain_cx"), nbt.getDouble("jjkbrp_open_domain_cy"), nbt.getDouble("jjkbrp_open_domain_cz"));
        }
        return entity.position();
    }

    /**
     * Resolves the center point specifically for open-domain state.
     *
     * <p>If no open-domain center exists yet, this falls back to the generic domain center helper so
     * callers still get a meaningful position.</p>
     *
     * @param entity the entity whose open-domain center should be resolved
     * @return the open-domain center, generic domain center fallback, or {@link Vec3#ZERO}
     */
    public static Vec3 getOpenDomainCenter(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.contains("jjkbrp_open_domain_cx")) {
            return new Vec3(nbt.getDouble("jjkbrp_open_domain_cx"), nbt.getDouble("jjkbrp_open_domain_cy"), nbt.getDouble("jjkbrp_open_domain_cz"));
        }
        return DomainAddonUtils.getDomainCenter(entity);
    }

    /**
     * Determines whether an entity is currently in an open-domain state.
     *
     * <p>The addon checks several persisted markers because different parts of the original mod and
     * addon pipeline can expose the same state in different formats. As a final fallback, a domain
     * expansion effect amplifier above zero is treated as the open form.</p>
     *
     * @param entity the entity to inspect
     * @return {@code true} if the entity is treated as using an open domain form
     */
    public static boolean isOpenDomainState(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean("jjkbrp_open_form_active")) {
            return true;
        }
        if (data.contains("jjkbrp_domain_form_cast_locked") && data.getInt("jjkbrp_domain_form_cast_locked") == 2) {
            return true;
        }
        if (data.contains("jjkbrp_domain_form_effective") && data.getInt("jjkbrp_domain_form_effective") == 2) {
            return true;
        }
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        MobEffectInstance effect = entity.getEffect(domainEffect);

        // In this project, non-zero amplifier values are used to distinguish the open form runtime.
        return effect != null && effect.getAmplifier() > 0;
    }

    /**
     * Determines whether an entity is currently in an incomplete-domain state.
     *
     * @param entity the entity to inspect
     * @return {@code true} if the entity is treated as using the incomplete domain form
     */
    public static boolean isIncompleteDomainState(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.contains("jjkbrp_domain_form_cast_locked")
                && data.getInt("jjkbrp_domain_form_cast_locked") == 0) {
            return true;
        }
        if (data.getBoolean("jjkbrp_incomplete_form_active")) {
            return true;
        }
        return data.contains("jjkbrp_domain_form_effective")
                && data.getInt("jjkbrp_domain_form_effective") == 0;
    }

    /**
     * Determines whether the entity has a standard closed domain active.
     *
     * <p>A closed domain is defined here as having the domain expansion effect while not being
     * classified as open or incomplete by the other specialized helpers.</p>
     *
     * @param entity the entity to inspect
     * @return {@code true} if the entity is in the closed domain state
     */
    public static boolean isClosedDomainActive(LivingEntity entity) {
        return entity != null && entity.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) && !DomainAddonUtils.isOpenDomainState(entity) && !DomainAddonUtils.isIncompleteDomainState(entity);
    }

    /**
     * Resolves the owner of a projectile or helper entity using its current level.
     *
     * @param projectileOrOrb the entity carrying owner-identification data
     * @return the resolved living owner, or {@code null} if no owner can be found
     */
    public static LivingEntity resolveOwnerEntity(Entity projectileOrOrb) {
        if (projectileOrOrb == null) {
            return null;
        }
        return DomainAddonUtils.resolveOwnerEntity((LevelAccessor)projectileOrOrb.level(), projectileOrOrb);
    }

    /**
     * Resolves the owner of a projectile or helper entity from persisted metadata.
     *
     * <p>The method first tries the authoritative UUID path, then falls back to the less reliable
     * {@code NameRanged_ranged} marker used by some original mod projectiles. Diagnostic logging is
     * rate-limited by {@link #shouldLogOwnerResolution(Entity)}.</p>
     *
     * @param world the world that should contain the owner
     * @param projectileOrOrb the entity carrying owner-identification data
     * @return the resolved living owner, or {@code null} if no owner can be found
     */
    public static LivingEntity resolveOwnerEntity(LevelAccessor world, Entity projectileOrOrb) {
        LivingEntity owner;
        LivingEntity owner2;
        if (projectileOrOrb == null || !(world instanceof ServerLevel)) {
            return null;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        CompoundTag data = projectileOrOrb.getPersistentData();
        String ownerUUID = data.getString(TAG_OWNER_UUID);
        if (!ownerUUID.isBlank() && (owner2 = DomainAddonUtils.resolveOwnerByUuid(serverLevel, ownerUUID)) != null) {
            if (DomainAddonUtils.shouldLogOwnerResolution(projectileOrOrb)) {
                LOGGER.debug("[GojoDomainDiag] Resolved owner via OWNER_UUID entity={} owner={} ownerUuid={}", new Object[]{projectileOrOrb.getClass().getSimpleName(), owner2.getName().getString(), ownerUUID});
            }
            return owner2;
        }
        double ownerNameRanged = data.getDouble(TAG_NAME_RANGED_RANGED);
        if (ownerNameRanged != 0.0 && (owner = DomainAddonUtils.resolveOwnerByNameRanged(serverLevel, projectileOrOrb, ownerNameRanged)) != null) {
            if (DomainAddonUtils.shouldLogOwnerResolution(projectileOrOrb)) {
                LOGGER.debug("[GojoDomainDiag] Resolved owner via NameRanged_ranged entity={} owner={} nameRanged_ranged={}", new Object[]{projectileOrOrb.getClass().getSimpleName(), owner.getName().getString(), ownerNameRanged});
            }
            return owner;
        }
        if (DomainAddonUtils.shouldLogOwnerResolution(projectileOrOrb) && (!ownerUUID.isBlank() || ownerNameRanged != 0.0)) {
            LOGGER.debug("[GojoDomainDiag] Failed to resolve owner entity={} ownerUuidPresent={} nameRanged_ranged={}", new Object[]{projectileOrOrb.getClass().getSimpleName(), !ownerUUID.isBlank(), ownerNameRanged});
        }
        return null;
    }

    /**
     * Checks whether the resolved owner of a projectile or orb currently has any domain state active.
     *
     * @param projectileOrOrb the spawned helper entity to inspect
     * @return {@code true} if the resolved owner is in open, incomplete, or closed domain state
     */
    public static boolean isOwnerInDomain(Entity projectileOrOrb) {
        boolean ownerDomainActive;
        if (projectileOrOrb == null) {
            return false;
        }
        LivingEntity owner = DomainAddonUtils.resolveOwnerEntity(projectileOrOrb);
        if (owner == null) {
            return false;
        }
        boolean ownerOpen = DomainAddonUtils.isOpenDomainState(owner);
        boolean ownerIncomplete = DomainAddonUtils.isIncompleteDomainState(owner);
        boolean ownerClosed = DomainAddonUtils.isClosedDomainActive(owner);

        // Any recognized domain form counts as active owner-domain participation.
        boolean bl = ownerDomainActive = ownerOpen || ownerIncomplete || ownerClosed;
        if (DomainAddonUtils.shouldLogOwnerResolution(projectileOrOrb)) {
            LOGGER.debug("[GojoDomainDiag] Owner domain state entity={} owner={} open={} incomplete={} closed={} active={}", new Object[]{projectileOrOrb.getClass().getSimpleName(), owner.getName().getString(), ownerOpen, ownerIncomplete, ownerClosed, ownerDomainActive});
        }
        return ownerDomainActive;
    }

    /**
     * Resolves a living owner directly from a UUID string.
     *
     * <p>This supports both generic entities and online players, because some owner lookups may be
     * backed by the entity registry while others are most reliably retrieved from the player list.</p>
     *
     * @param serverLevel the server level used for lookup
     * @param ownerUUID the UUID string stored on the spawned entity
     * @return the resolved living owner, or {@code null} if parsing or lookup fails
     */
    private static LivingEntity resolveOwnerByUuid(ServerLevel serverLevel, String ownerUUID) {
        try {
            UUID uuid = UUID.fromString(ownerUUID);
            Entity ownerEntity = serverLevel.getEntity(uuid);
            if (ownerEntity instanceof LivingEntity) {
                LivingEntity livingOwner = (LivingEntity)ownerEntity;
                return livingOwner;
            }
            return serverLevel.getServer().getPlayerList().getPlayer(uuid);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves a living owner from the legacy {@code NameRanged} numeric marker.
     *
     * <p>Players are checked first because they are the most common source. If no player matches,
     * nearby living entities are scanned within a large search area around the projectile.</p>
     *
     * @param serverLevel the server level used for lookup
     * @param projectileOrOrb the entity carrying the marker value
     * @param ownerNameRanged the numeric owner marker to match
     * @return the resolved living owner, or {@code null} if no candidate matches
     */
    private static LivingEntity resolveOwnerByNameRanged(ServerLevel serverLevel, Entity projectileOrOrb, double ownerNameRanged) {
        for (ServerPlayer player : serverLevel.players()) {
            if (Double.compare(player.getPersistentData().getDouble(TAG_NAME_RANGED), ownerNameRanged) != 0) continue;
            return player;
        }
        AABB searchBox = projectileOrOrb.getBoundingBox().inflate(256.0);
        for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> entity.isAlive() && entity != projectileOrOrb)) {
            if (Double.compare(candidate.getPersistentData().getDouble(TAG_NAME_RANGED), ownerNameRanged) != 0) continue;
            return candidate;
        }
        return null;
    }

    /**
     * Determines whether diagnostic owner-resolution logging should run this tick.
     *
     * @param projectileOrOrb the entity being inspected
     * @return {@code true} every 20 ticks for non-null entities, otherwise {@code false}
     */
    private static boolean shouldLogOwnerResolution(Entity projectileOrOrb) {
        // Log once per second to avoid spamming server output while still preserving diagnostics.
        return projectileOrOrb != null && projectileOrOrb.tickCount % 20 == 0;
    }

    /**
     * Checks whether an entity currently has an active domain expansion state.
     *
     * <p>This method accepts both the formal mob effect and the persistent-data based runtime flags
     * used during domain build-up or transitional states.</p>
     *
     * @param entity the entity to inspect
     * @return {@code true} if any supported domain expansion marker is active
     */
    public static boolean hasActiveDomainExpansion(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (entity.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        if (data.getDouble(TAG_DOMAIN_EXPANSION) > 0.0) {
            return true;
        }
        return DomainAddonUtils.isDomainBuildOrActive(entity);
    }

    /**
     * Checks whether an entity is combat-tagged and therefore should be prevented from some actions.
     *
     * <p>The check supports both the original combat cooldown effect and the addon-managed persistent
     * timestamp fallback.</p>
     *
     * @param entity the entity to inspect
     * @return {@code true} if the entity is still considered in combat
     */
    public static boolean isCombatTagged(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.hasEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get())) {
            return true;
        }
        long lastCombatTick = entity.getPersistentData().getLong(TAG_LAST_COMBAT_TICK);
        return lastCombatTick > 0L && (long)entity.tickCount - lastCombatTick < 100L;
    }

    /**
     * Checks whether domain mastery mutation should currently be blocked for an entity.
     *
     * @param entity the entity to inspect
     * @return {@code true} when active domain or combat state locks mastery changes
     */
    public static boolean isDomainMasteryMutationLocked(LivingEntity entity) {
        return DomainAddonUtils.hasActiveDomainExpansion(entity) || DomainAddonUtils.isCombatTagged(entity);
    }

    /**
     * Produces a user-facing explanation for why domain mastery mutation is blocked.
     *
     * @param entity the entity to inspect
     * @return the most specific lock reason currently applicable to the entity
     */
    public static String getDomainMasteryMutationLockReason(LivingEntity entity) {
        if (DomainAddonUtils.hasActiveDomainExpansion(entity)) {
            return "Cannot change Domain Mastery while Domain Expansion is active";
        }
        if (DomainAddonUtils.isCombatTagged(entity)) {
            return "Cannot change Domain Mastery while in combat";
        }
        return "Domain Mastery changes are currently locked";
    }

    /**
     * Checks whether a player's domain is currently building or already active.
     *
     * <p>This version matches the original player-centric data flow and requires a stored domain
     * center plus startup-state counters to be present.</p>
     *
     * @param player the player to inspect
     * @return {@code true} if the domain build sequence or active state is recognized
     */
    public static boolean isDomainBuildOrActive(Player player) {
        boolean hasStartupState;
        if (player == null) {
            return false;
        }
        CompoundTag nbt = player.getPersistentData();
        if (nbt.getBoolean("jjkbrp_force_closed_cleanup")) {
            return false;
        }
        if (!nbt.contains("x_pos_doma")) {
            return false;
        }
        if (player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        if (nbt.getBoolean("Failed")) {
            return false;
        }

        // Startup markers indicate that the player has progressed into the domain construction flow.
        boolean bl = hasStartupState = nbt.getDouble("select") != 0.0 || nbt.getDouble("skill") != 0.0 || nbt.getDouble("skill_domain") != 0.0;
        if (!hasStartupState) {
            return false;
        }

        // cnt3/cnt1 are original mod timing counters used to confirm that building is underway.
        return nbt.getDouble("cnt3") >= 20.0 && nbt.getDouble("cnt1") > 0.0;
    }

    /**
     * Checks whether a living entity's domain is currently building or already active.
     *
     * <p>This overload is slightly broader than the player version because non-player casters can use
     * the addon runtime domain id marker during the startup process.</p>
     *
     * @param caster the living caster to inspect
     * @return {@code true} if the domain build sequence or active state is recognized
     */
    public static boolean isDomainBuildOrActive(LivingEntity caster) {
        boolean hasStartupState;
        if (caster == null) {
            return false;
        }
        CompoundTag nbt = caster.getPersistentData();
        if (nbt.getBoolean("jjkbrp_force_closed_cleanup")) {
            return false;
        }
        if (!nbt.contains("x_pos_doma")) {
            return false;
        }
        if (caster.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        if (nbt.getBoolean("Failed") || nbt.getBoolean("DomainDefeated")) {
            return false;
        }

        // Runtime domain id support lets addon-driven casters participate in the same detection flow.
        boolean bl = hasStartupState = nbt.getDouble("select") != 0.0 || nbt.getDouble("skill") != 0.0 || nbt.getDouble("skill_domain") != 0.0 || nbt.getDouble("jjkbrp_domain_id_runtime") != 0.0;
        if (!hasStartupState) {
            return false;
        }
        return nbt.getDouble("cnt3") >= 20.0 && nbt.getDouble("cnt1") > 0.0;
    }

    /**
     * Checks whether a player's domain is building or active, excluding cleanup-break states nearby.
     *
     * @param world the world used to scan for cleanup entities
     * @param player the player whose domain state should be checked
     * @return {@code true} if the domain is active/building and not currently being broken nearby
     */
    public static boolean isDomainBuildOrActive(ServerLevel world, Player player) {
        if (!DomainAddonUtils.isDomainBuildOrActive(player)) {
            return false;
        }
        if (world == null) {
            return true;
        }

        // A nearby breaking cleanup entity means the domain is in teardown rather than valid activity.
        return !DomainAddonUtils.hasBreakingCleanupEntity(world, DomainAddonUtils.getDomainCenter((Entity)player), 4.0);
    }

    /**
     * Checks whether a living caster's domain is building or active, excluding cleanup-break states.
     *
     * @param world the world used to scan for cleanup entities
     * @param caster the caster whose domain state should be checked
     * @return {@code true} if the domain is active/building and not currently being broken nearby
     */
    public static boolean isDomainBuildOrActive(ServerLevel world, LivingEntity caster) {
        if (!DomainAddonUtils.isDomainBuildOrActive(caster)) {
            return false;
        }
        if (world == null) {
            return true;
        }
        return !DomainAddonUtils.hasBreakingCleanupEntity(world, DomainAddonUtils.getDomainCenter((Entity)caster), 4.0);
    }

    /**
     * Detects whether a breaking cleanup entity exists near a domain center.
     *
     * <p>Cleanup entities are spawned during domain teardown, so this helper prevents systems from
     * incorrectly treating a dismantling domain as still valid and active.</p>
     *
     * @param world the world to search
     * @param center the domain center to compare against
     * @param maxDistanceSqr the maximum allowed squared distance from the center
     * @return {@code true} if a matching breaking cleanup entity is found
     */
    public static boolean hasBreakingCleanupEntity(ServerLevel world, Vec3 center, double maxDistanceSqr) {
        if (world == null || center == null) {
            return false;
        }

        // Expand slightly beyond the exact radius so nearby cleanup helpers are not missed.
        double searchRadius = Math.max(4.0, Math.sqrt(Math.max(0.0, maxDistanceSqr)) + 2.0);
        for (DomainExpansionEntityEntity cleanup : world.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(center.x - searchRadius, center.y - searchRadius, center.z - searchRadius, center.x + searchRadius, center.y + searchRadius, center.z + searchRadius), entity -> entity.getPersistentData().getBoolean("Break"))) {
            CompoundTag cleanupNbt = cleanup.getPersistentData();

            // Prefer explicit stored cleanup coordinates, then fall back to the entity position itself.
            Vec3 vec3 = cleanupNbt.contains("x_pos") ? new Vec3(cleanupNbt.getDouble("x_pos"), cleanupNbt.getDouble("y_pos"), cleanupNbt.getDouble("z_pos")) : cleanup.position();
            Vec3 cleanupCenter = vec3;
            if (!(cleanupCenter.distanceToSqr(center) <= maxDistanceSqr)) continue;
            return true;
        }
        return false;
    }

    /**
     * Finds the closest player whose domain center matches a given location and is still active.
     *
     * @param world the world to search
     * @param center the target center to compare against
     * @param maxDistanceSqr the maximum allowed squared distance from the target center
     * @return the closest matching player, or {@code null} if no live match is found
     */
    public static Player findMatchingLiveDomainPlayer(ServerLevel world, Vec3 center, double maxDistanceSqr) {
        if (world == null || center == null) {
            return null;
        }
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player player : world.players()) {
            double distance;
            if (!DomainAddonUtils.isDomainBuildOrActive(player) || (distance = DomainAddonUtils.getDomainCenter((Entity)player).distanceToSqr(center)) > maxDistanceSqr || distance >= closestDistance) continue;
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    /**
     * Finds the closest living caster whose domain center matches a given location and is still active.
     *
     * @param world the world to search
     * @param center the target center to compare against
     * @param maxDistanceSqr the maximum allowed squared distance from the target center
     * @return the closest matching living caster, or {@code null} if no live match is found
     */
    public static LivingEntity findMatchingLiveDomainCaster(ServerLevel world, Vec3 center, double maxDistanceSqr) {
        if (world == null || center == null) {
            return null;
        }
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        // Always scan players globally because scaled closed domains can let the caster roam far from center.
        for (Player player : world.players()) {
            double distance;
            if (!DomainAddonUtils.isDomainBuildOrActive(player) || (distance = DomainAddonUtils.getDomainCenter((Entity)player).distanceToSqr(center)) > maxDistanceSqr || distance >= closestDistance) continue;
            closest = player;
            closestDistance = distance;
        }

        double searchRadius = Math.max(96.0, Math.sqrt(Math.max(0.0, maxDistanceSqr)) + 128.0);
        for (LivingEntity caster : world.getEntitiesOfClass(LivingEntity.class, new AABB(center.x - searchRadius, center.y - searchRadius, center.z - searchRadius, center.x + searchRadius, center.y + searchRadius, center.z + searchRadius), e -> !(e instanceof Player))) {
            double distance;
            if (!DomainAddonUtils.isDomainBuildOrActive(caster) || (distance = DomainAddonUtils.getDomainCenter((Entity)caster).distanceToSqr(center)) > maxDistanceSqr || distance >= closestDistance) continue;
            closest = caster;
            closestDistance = distance;
        }
        return closest;
    }

    /**
     * Resolves the gameplay range used by open domains.
     *
     * <p>Open domains intentionally extend beyond the shell radius, so this helper applies the saved
     * range multiplier with a minimum value to keep behavior stable.</p>
     *
     * @param world the world context used for radius lookup
     * @param entity the entity whose open-domain range should be resolved
     * @return the effective open-domain gameplay range
     */
    public static double getOpenDomainRange(LevelAccessor world, Entity entity) {
        if (entity == null) {
            return 40.0;
        }
        CompoundTag nbt = entity.getPersistentData();
        double baseRadius = DomainAddonUtils.getActualDomainRadius(world, nbt);

        // Open-domain gameplay range is much larger than the shell itself by design.
        double multiplier = Math.max(2.5, nbt.getDouble("jjkbrp_open_range_multiplier"));
        return baseRadius * multiplier;
    }

    /**
     * Resolves the visual effect range used by open-domain particles and other VFX.
     *
     * @param world the world context used for radius lookup
     * @param entity the entity whose open-domain visual range should be resolved
     * @return the effective visual range for open-domain rendering helpers
     */
    public static double getOpenDomainVisualRange(LevelAccessor world, Entity entity) {
        if (entity == null) {
            return 48.0;
        }
        CompoundTag nbt = entity.getPersistentData();
        double baseRadius = DomainAddonUtils.getActualDomainRadius(world, nbt);
        double multiplier = Math.max(2.5, nbt.getDouble("jjkbrp_open_range_multiplier"));

        // Visual range is intentionally compressed relative to the full gameplay range for VFX balance.
        double visualMultiplier = Math.max(3.0, Math.min(4.5, multiplier * 0.25));
        return baseRadius * visualMultiplier;
    }

    /**
     * Resolves the shell radius used for open-domain sphere visuals.
     *
     * @param world the world context used for radius lookup
     * @param entity the entity whose open-domain shell radius should be resolved
     * @return the shell radius, clamped to a minimum of {@code 8.0}
     */
    public static double getOpenDomainShellRadius(LevelAccessor world, Entity entity) {
        if (entity == null) {
            return 16.0;
        }
        double baseRadius = DomainAddonUtils.getActualDomainRadius(world, entity.getPersistentData());
        return Math.max(8.0, baseRadius);
    }

    /**
     * Uses reflection to forcibly update a mob effect instance duration.
     *
     * <p>This exists because mapped names can differ between environments. The method tries several
     * field names and finally scans integer fields heuristically to find the duration slot without
     * touching likely amplifier fields.</p>
     *
     * @param instance the effect instance to mutate
     * @param newDuration the new duration in ticks
     * @return {@code true} if a suitable field was found and updated
     */
    public static boolean setEffectDuration(MobEffectInstance instance, int newDuration) {
        if (instance == null || newDuration <= 0) {
            return false;
        }
        try {
            // Some environments expose an obfuscated or remapped field under an unexpected name.
            Field durationField = MobEffectInstance.class.getDeclaredField("effect");
            durationField.setAccessible(true);
            durationField.setInt(instance, newDuration);
            return true;
        }
        catch (NoSuchFieldException e1) {
            try {
                // Preferred direct field name in more standard mappings.
                Field durationField = MobEffectInstance.class.getDeclaredField("duration");
                durationField.setAccessible(true);
                durationField.setInt(instance, newDuration);
                return true;
            }
            catch (Exception e2) {
                for (Field field : MobEffectInstance.class.getDeclaredFields()) {
                    if (field.getType() != Integer.TYPE) continue;
                    try {
                        int value;
                        field.setAccessible(true);
                        String name = field.getName().toLowerCase();

                        // Skip obvious amplifier fields and suspiciously tiny values that are unlikely
                        // to represent the main effect duration.
                        if (name.contains("amplifier") || name.contains("amp") || name.equals("duration") || (value = field.getInt(instance)) < 20) continue;
                        field.setInt(instance, newDuration);
                        return true;
                    }
                    catch (Exception exception) {
                        // Intentionally ignored because this is a best-effort reflective fallback.
                    }
                }
            }
        }
        catch (Exception exception) {
            // Intentionally ignored because unsupported mappings should simply report failure.
        }
        return false;
    }

    /**
     * Sends particles to players beyond the standard short-range packet behavior when possible.
     *
     * <p>If the reflective long-distance overload exists, particles are sent per viewer with the
     * force flag enabled. Otherwise, the method falls back to the normal world broadcast.</p>
     *
     * @param world the server world sending the particles
     * @param particle the particle type to send
     * @param x the x-coordinate of the particle origin
     * @param y the y-coordinate of the particle origin
     * @param z the z-coordinate of the particle origin
     * @param count the number of particles to spawn
     * @param offsetX the x spread offset
     * @param offsetY the y spread offset
     * @param offsetZ the z spread offset
     * @param speed the particle speed parameter
     */
    public static void sendLongDistanceParticles(ServerLevel world, ParticleOptions particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (count <= 0) {
            return;
        }
        if (LONG_DISTANCE_PARTICLE_METHOD != null) {
            boolean sent = true;
            for (ServerPlayer viewer : world.players()) {
                try {
                    // Send directly to each viewer with the long-distance flag enabled.
                    LONG_DISTANCE_PARTICLE_METHOD.invoke((Object)world, viewer, particle, true, x, y, z, count, offsetX, offsetY, offsetZ, speed);
                }
                catch (Exception ignored) {
                    // Any reflective failure drops back to the vanilla sendParticles behavior.
                    sent = false;
                    break;
                }
            }
            if (sent) {
                return;
            }
        }
        world.sendParticles(particle, x, y, z, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Resolves the reflective long-distance particle overload on {@link ServerLevel}.
     *
     * @return the method handle if available, otherwise {@code null}
     */
    private static Method resolveLongDistanceParticleMethod() {
        try {
            return ServerLevel.class.getMethod("sendParticles", ServerPlayer.class, ParticleOptions.class, Boolean.TYPE, Double.TYPE, Double.TYPE, Double.TYPE, Integer.TYPE, Double.TYPE, Double.TYPE, Double.TYPE, Double.TYPE);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    // ==================== Phase 1 Foundation Helpers ====================

    /**
     * Resolves the domain form of a living entity as a type-safe {@link DomainForm} enum.
     *
     * <p>This is the canonical centralized implementation that replaces the
     * scattered {@code jjkbrp$resolveDomainForm()} copies in domain mixins.
     * It inspects persistent data, capability state, and runtime flags in the
     * same priority order as the original mixin implementations.</p>
     *
     * @param entity the entity to inspect; may be {@code null}
     * @return the resolved {@link DomainForm}, defaulting to {@link DomainForm#CLOSED}
     */
    public static DomainForm resolveDomainForm(LivingEntity entity) {
        return DomainForm.resolve(entity);
    }

    /**
     * Resolves the domain form as a raw integer, matching the encoding stored
     * in {@code jjkbrp_domain_form_cast_locked} and {@code jjkbrp_domain_form_effective}.
     *
     * @param entity the entity to inspect; may be {@code null}
     * @return {@code 0} (incomplete), {@code 1} (closed), or {@code 2} (open)
     */
    public static int resolveDomainFormInt(LivingEntity entity) {
        return resolveDomainForm(entity).getId();
    }

    public static String resolveDomainName(int domainId) {
        return switch (domainId) {
            case 1 -> "Malevolent Shrine";
            case 2 -> "Unlimited Void";
            case 4 -> "Coffin of the Iron Mountain";
            case 5 -> "Authentic Mutual Love";
            case 6 -> "Chimera Shadow Garden";
            case 7 -> "Kashimo Domain";
            case 8 -> "Horizon of the Captivating Skandha";
            case 9 -> "Tsukumo Domain";
            case 10 -> "Choso Domain";
            case 11 -> "Mei Mei Domain";
            case 13 -> "Nanami Domain";
            case 14 -> "Ceremonial Sea of Light";
            case 15 -> "Self-Embodiment of Perfection";
            case 18 -> "Womb Profusion";
            case 19 -> "Time Cell Moon Palace";
            case 21 -> "Itadori Domain";
            case 23 -> "Kurourushi Domain";
            case 24 -> "Uraume Domain";
            case 25 -> "Graveyard Domain";
            case 26 -> "Ogi Domain";
            case 27 -> "Deadly Sentencing";
            case 29 -> "Idle Death Gamble";
            case 35 -> "Junpei Domain";
            case 36 -> "Nishimiya Domain";
            case 40 -> "Takuma Ino Domain";
            default -> "";
        };
    }
}
