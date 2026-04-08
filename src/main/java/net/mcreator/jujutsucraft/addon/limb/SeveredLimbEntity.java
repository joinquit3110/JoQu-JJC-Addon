package net.mcreator.jujutsucraft.addon.limb;

import java.util.Optional;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
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

/**
 * Physical detached limb entity spawned when a limb is severed.
 *
 * <p>The entity stores which limb it represents, who it came from, a small amount of cosmetic state,
 * and simple falling/bouncing physics before despawning after a short lifetime.</p>
 */
public class SeveredLimbEntity
extends Entity {
    /** Synced integer id identifying which limb this entity represents. */
    private static final EntityDataAccessor<Integer> LIMB_TYPE_ID = SynchedEntityData.defineId(SeveredLimbEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    /** Synced owner UUID used for skin selection and source tracking. */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(SeveredLimbEntity.class, (EntityDataSerializer)EntityDataSerializers.OPTIONAL_UUID);
    /** Synced owner name retained for display/debug information. */
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(SeveredLimbEntity.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    /** Synced flag indicating whether the originating player used the slim model variant. */
    private static final EntityDataAccessor<Boolean> SLIM_MODEL = SynchedEntityData.defineId(SeveredLimbEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    /** Client-side blood drip particle used while the detached limb is still fresh. */
    private static final DustParticleOptions BLOOD_DRIP = new DustParticleOptions(new Vector3f(0.5f, 0.02f, 0.02f), 0.6f);
    /** Maximum lifetime before the detached limb despawns, measured in ticks. */
    private static final int MAX_LIFETIME = 200;
    /** Number of ticks this detached limb has existed. */
    private int lifetime = 0;
    /** Cosmetic X-axis rotation used by the renderer. */
    private float rotX;
    /** Cosmetic Z-axis rotation used by the renderer. */
    private float rotZ;

    /**
     * Base constructor used by the entity type registration and spawning system.
     *
     * @param type registered entity type
     * @param level level the entity exists in
     */
    public SeveredLimbEntity(EntityType<?> type, Level level) {
        super(type, level);
        // Start with random tumble angles so detached limbs do not all look identical on spawn.
        this.rotX = level.getRandom().nextFloat() * 360.0f;
        this.rotZ = level.getRandom().nextFloat() * 360.0f;
    }

    /**
     * Convenience constructor used when spawning a detached limb from an owner.
     *
     * @param level level the entity exists in
     * @param owner entity that lost the limb
     * @param limbType limb represented by this detached entity
     */
    public SeveredLimbEntity(Level level, LivingEntity owner, LimbType limbType) {
        this((EntityType)LimbEntityRegistry.SEVERED_LIMB.get(), level);
        this.entityData.set(LIMB_TYPE_ID, limbType.getIndex());
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.entityData.set(OWNER_NAME, owner.getName().getString());
        this.entityData.set(SLIM_MODEL, false);
    }

    /**
     * Defines all synced data parameters required on both client and server.
     */
    protected void defineSynchedData() {
        this.entityData.define(LIMB_TYPE_ID, 0);
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(OWNER_NAME, "");
        this.entityData.define(SLIM_MODEL, false);
    }

    /**
     * Returns the represented limb type.
     *
     * @return resolved limb type, or {@code null} if the synced id is invalid
     */
    public LimbType getLimbType() {
        return LimbType.fromIndex((Integer)this.entityData.get(LIMB_TYPE_ID));
    }

    /**
     * Returns the UUID of the original owner when known.
     *
     * @return optional owner UUID
     */
    public Optional<UUID> getOwnerUUID() {
        return (Optional)this.entityData.get(OWNER_UUID);
    }

    /**
     * Returns the stored owner name.
     *
     * @return owner display name snapshot
     */
    public String getOwnerName() {
        return (String)this.entityData.get(OWNER_NAME);
    }

    /**
     * Returns whether the renderer should use the slim player model.
     *
     * @return {@code true} for the slim model variant
     */
    public boolean isSlimModel() {
        return (Boolean)this.entityData.get(SLIM_MODEL);
    }

    /**
     * Returns the cosmetic X-axis tumble rotation.
     *
     * @return X rotation in degrees
     */
    public float getLimbRotX() {
        return this.rotX;
    }

    /**
     * Returns the cosmetic Z-axis tumble rotation.
     *
     * @return Z rotation in degrees
     */
    public float getLimbRotZ() {
        return this.rotZ;
    }

    /**
     * Updates lifetime, simple physics, and client blood drip particles.
     */
    public void tick() {
        super.tick();
        ++this.lifetime;
        if (this.lifetime > 200 && !this.level().isClientSide) {
            this.discard();
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (!this.onGround()) {
            // Airborne limbs keep tumbling until they settle.
            this.rotX += 5.0f;
            this.rotZ += 3.0f;
        } else {
            // Ground friction rapidly damps the detached limb after landing.
            motion = new Vec3(motion.x * 0.92, 0.0, motion.z * 0.92);
            if (Math.abs(motion.y) < 0.01) {
                motion = new Vec3(motion.x, 0.0, motion.z);
            }
        }
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        if (this.onGround() && motion.y < -0.01) {
            // Small bounce when the limb first hits the ground.
            this.setDeltaMovement(new Vec3(motion.x * 0.8, -motion.y * 0.3, motion.z * 0.8));
        }
        if (!this.onGround()) {
            // Apply simple gravity while the limb is airborne.
            this.setDeltaMovement(this.getDeltaMovement().subtract(0.0, 0.04, 0.0));
        }
        if (this.level().isClientSide && this.lifetime < 100 && this.level().getRandom().nextInt(3) == 0) {
            // Early in its lifetime the limb occasionally emits a blood drip particle for extra feedback.
            this.level().addParticle((ParticleOptions)BLOOD_DRIP, this.getX() + this.level().getRandom().nextGaussian() * 0.05, this.getY() + 0.1, this.getZ() + this.level().getRandom().nextGaussian() * 0.05, 0.0, -0.03, 0.0);
        }
    }

    /**
     * Restores detached limb state from saved entity data.
     *
     * @param tag saved entity NBT
     */
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

    /**
     * Saves detached limb state into entity NBT.
     *
     * @param tag target entity NBT
     */
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LimbType", ((Integer)this.entityData.get(LIMB_TYPE_ID)).intValue());
        this.getOwnerUUID().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
        tag.putString("OwnerName", this.getOwnerName());
        tag.putBoolean("SlimModel", this.isSlimModel());
        tag.putInt("Lifetime", this.lifetime);
        tag.putFloat("RotX", this.rotX);
        tag.putFloat("RotZ", this.rotZ);
    }

    /**
     * Creates the vanilla/Forge spawn packet for this custom entity.
     *
     * @return network packet used to spawn the entity on clients
     */
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }

    /**
     * Prevents the detached limb from being targeted as a pickable hitbox.
     *
     * @return always {@code false}
     */
    public boolean isPickable() {
        return false;
    }
}
