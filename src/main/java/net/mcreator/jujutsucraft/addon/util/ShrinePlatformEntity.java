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

/** Invisible solid slab moved by {@link PlayerShrineRiseController} under the shrine deck. */
public class ShrinePlatformEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(ShrinePlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(ShrinePlatformEntity.class, EntityDataSerializers.FLOAT);

    private static final float MIN_DIM = 0.2F;
    private static final long STALE_GRACE_TICKS = 30L;
    private static final long MAX_LIFETIME_TICKS = 24000L;

    private long lastKeepAliveTick = Long.MIN_VALUE;
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

    public boolean canBeCollidedWith() {
        return true;
    }

    public boolean isPushable() {
        return false;
    }

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
