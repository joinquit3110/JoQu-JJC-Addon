package net.mcreator.jujutsucraft.addon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Invisible, server-authoritative solid platform that gives the Malevolent Shrine a real walkable
 * collision surface.
 *
 * <p>The shrine visual entities ({@code EntityMalevolentShrineEntity} /
 * {@code EntityMalevolentShrine2Entity}) have a tiny registered hitbox (4x10 / 8x6) relative to
 * their enormous, heavily-rotated models (~40-62 blocks wide, tens of blocks tall). A single entity
 * AABB therefore cannot match the model, so making the shrine itself collidable only produces an
 * invisible "pole" near its feet that shoves the player sideways instead of letting them stand on
 * the structure.</p>
 *
 * <p>This entity sidesteps that limitation: it is a thin, wide solid slab the player stands on TOP
 * of. {@link PlayerShrineRiseController} spawns one per shrine, sizes it to the shrine footprint
 * (scaled by the domain decoration scale), and moves it every tick so its top surface tracks the
 * rising/settled shrine. Because it carries a real collision AABB on both server and client, the
 * local player's own movement prediction can stand on it and it lifts the player as it rises.</p>
 *
 * <p>It renders with a {@code NoopRenderer} (invisible), takes no damage, never moves on its own,
 * and self-discards shortly after the controller stops refreshing it (i.e. when the shrine is
 * removed and the domain ends).</p>
 */
public class ShrinePlatformEntity extends Entity {
    /** Synced full width (X/Z footprint) of the collision slab, in blocks. */
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(ShrinePlatformEntity.class, EntityDataSerializers.FLOAT);
    /** Synced height (thickness) of the collision slab, in blocks. */
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(ShrinePlatformEntity.class, EntityDataSerializers.FLOAT);

    /** Smallest allowed slab dimension so the AABB never collapses to zero. */
    private static final float MIN_DIM = 0.2F;
    /** Ticks without a controller keep-alive ping before the orphaned platform removes itself. */
    private static final long STALE_GRACE_TICKS = 30L;
    /** Hard lifetime cap (server ticks) as a final safety net against leaks. */
    private static final long MAX_LIFETIME_TICKS = 24000L;

    /** Last server gametime the owning controller refreshed this platform. */
    private long lastKeepAliveTick = Long.MIN_VALUE;
    /** Server gametime this platform was created, for the hard lifetime cap. */
    private long spawnTick = Long.MIN_VALUE;

    public ShrinePlatformEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_WIDTH, 1.0F);
        this.entityData.define(DATA_HEIGHT, 1.0F);
    }

    /**
     * Sets the collision slab footprint and thickness, then refreshes the bounding box.
     *
     * @param width  full X/Z footprint in blocks
     * @param height slab thickness in blocks
     */
    public void setPlatformSize(float width, float height) {
        float w = Math.max(MIN_DIM, width);
        float h = Math.max(MIN_DIM, height);
        if (this.entityData.get(DATA_WIDTH) != w) {
            this.entityData.set(DATA_WIDTH, w);
        }
        if (this.entityData.get(DATA_HEIGHT) != h) {
            this.entityData.set(DATA_HEIGHT, h);
        }
        this.refreshDimensions();
    }

    /** Records a controller keep-alive ping so the platform knows its shrine is still active. */
    public void keepAlive(long gameTime) {
        if (this.spawnTick == Long.MIN_VALUE) {
            this.spawnTick = gameTime;
        }
        this.lastKeepAliveTick = gameTime;
    }

    public EntityDimensions getDimensions(Pose pose) {
        float w = this.entityData != null ? this.entityData.get(DATA_WIDTH) : 1.0F;
        float h = this.entityData != null ? this.entityData.get(DATA_HEIGHT) : 1.0F;
        return EntityDimensions.fixed(Math.max(MIN_DIM, w), Math.max(MIN_DIM, h));
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_WIDTH.equals(accessor) || DATA_HEIGHT.equals(accessor)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    public void tick() {
        super.tick();
        // The platform never moves or falls on its own; the controller repositions it each shrine tick.
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (this.level().isClientSide()) {
            return;
        }
        long now = this.level().getGameTime();
        if (this.spawnTick == Long.MIN_VALUE) {
            this.spawnTick = now;
        }
        boolean stale = this.lastKeepAliveTick != Long.MIN_VALUE && now - this.lastKeepAliveTick > STALE_GRACE_TICKS;
        boolean expired = now - this.spawnTick > MAX_LIFETIME_TICKS;
        if (stale || expired) {
            this.discard();
        }
    }

    /** Makes the slab a hard, walkable obstacle (SRG m_5829_). */
    public boolean canBeCollidedWith() {
        return true;
    }

    /** Keeps the platform from being shoved by entities that walk into it. */
    public boolean isPushable() {
        return false;
    }

    /** Hidden from ray-trace / attacks so it never blocks interaction with the shrine or targets. */
    public boolean isPickable() {
        return false;
    }

    public boolean isNoGravity() {
        return true;
    }

    protected boolean canRide(Entity vehicle) {
        return false;
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("PlatformWidth")) {
            this.entityData.set(DATA_WIDTH, Math.max(MIN_DIM, tag.getFloat("PlatformWidth")));
        }
        if (tag.contains("PlatformHeight")) {
            this.entityData.set(DATA_HEIGHT, Math.max(MIN_DIM, tag.getFloat("PlatformHeight")));
        }
        this.refreshDimensions();
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("PlatformWidth", this.entityData.get(DATA_WIDTH));
        tag.putFloat("PlatformHeight", this.entityData.get(DATA_HEIGHT));
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
