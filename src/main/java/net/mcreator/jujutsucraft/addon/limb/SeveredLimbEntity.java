package net.mcreator.jujutsucraft.addon.limb;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

public class SeveredLimbEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIMB_TYPE_ID = SynchedEntityData.defineId(SeveredLimbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(SeveredLimbEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(SeveredLimbEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM_MODEL = SynchedEntityData.defineId(SeveredLimbEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions BLOOD_DRIP = new DustParticleOptions(new Vector3f(0.5f, 0.02f, 0.02f), 0.6f);
    private static final int MAX_LIFETIME = 200;
    private int lifetime = 0;
    private float rotX;
    private float rotZ;

    public SeveredLimbEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.rotX = level.getRandom().nextFloat() * 360.0f;
        this.rotZ = level.getRandom().nextFloat() * 360.0f;
    }

    public SeveredLimbEntity(Level level, LivingEntity owner, LimbType limbType) {
        this((EntityType)LimbEntityRegistry.SEVERED_LIMB.get(), level);
        this.entityData.set(LIMB_TYPE_ID, limbType.getIndex());
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.entityData.set(OWNER_NAME, owner.getName().getString());
        this.entityData.set(SLIM_MODEL, false);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIMB_TYPE_ID, 0);
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(OWNER_NAME, "");
        this.entityData.define(SLIM_MODEL, false);
    }

    public LimbType getLimbType() {
        return LimbType.fromIndex(this.entityData.get(LIMB_TYPE_ID));
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(OWNER_UUID);
    }

    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    public boolean isSlimModel() {
        return this.entityData.get(SLIM_MODEL);
    }

    public float getLimbRotX() {
        return this.rotX;
    }

    public float getLimbRotZ() {
        return this.rotZ;
    }

    @Override
    public void tick() {
        super.tick();
        ++this.lifetime;
        if (this.lifetime > 200 && !this.level().isClientSide) {
            this.discard();
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (!this.onGround()) {
            this.rotX += 5.0f;
            this.rotZ += 3.0f;
        } else {
            motion = new Vec3(motion.x * 0.92, 0.0, motion.z * 0.92);
            if (Math.abs(motion.y) < 0.01) {
                motion = new Vec3(motion.x, 0.0, motion.z);
            }
        }
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        if (this.onGround() && motion.y < -0.01) {
            this.setDeltaMovement(new Vec3(motion.x * 0.8, -motion.y * 0.3, motion.z * 0.8));
        }
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().subtract(0.0, 0.04, 0.0));
        }
        if (this.level().isClientSide && this.lifetime < 100 && this.level().getRandom().nextInt(3) == 0) {
            this.level().addParticle(BLOOD_DRIP,
                this.getX() + this.level().getRandom().nextGaussian() * 0.05,
                this.getY() + 0.1,
                this.getZ() + this.level().getRandom().nextGaussian() * 0.05,
                0.0, -0.03, 0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(LIMB_TYPE_ID, tag.getInt("LimbType"));
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        this.entityData.set(SLIM_MODEL, tag.getBoolean("SlimModel"));
        this.lifetime = tag.getInt("Lifetime");
        this.rotX = tag.getFloat("RotX");
        this.rotZ = tag.getFloat("RotZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LimbType", this.entityData.get(LIMB_TYPE_ID));
        this.getOwnerUUID().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
        tag.putString("OwnerName", this.getOwnerName());
        tag.putBoolean("SlimModel", this.isSlimModel());
        tag.putInt("Lifetime", this.lifetime);
        tag.putFloat("RotX", this.rotX);
        tag.putFloat("RotZ", this.rotZ);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
