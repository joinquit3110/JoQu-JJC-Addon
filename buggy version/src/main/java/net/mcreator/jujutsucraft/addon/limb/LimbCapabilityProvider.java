package net.mcreator.jujutsucraft.addon.limb;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.limb.LimbData;
import net.mcreator.jujutsucraft.addon.limb.LimbGameplayHandler;
import net.mcreator.jujutsucraft.addon.limb.LimbState;
import net.mcreator.jujutsucraft.addon.limb.LimbSyncPacket;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge capability provider for {@link net.mcreator.jujutsucraft.addon.limb.LimbData}.
 *
 * <p>This class owns the per-entity limb capability instance, attaches it to every living entity,
 * serializes it, and handles lifecycle edge cases such as player cloning and respawn sync.</p>
 */
@Mod.EventBusSubscriber(modid="jjkblueredpurple")
public class LimbCapabilityProvider
implements ICapabilitySerializable<CompoundTag> {
    /** Unique capability attachment id stored on living entities. */
    public static final ResourceLocation ID = new ResourceLocation("jjkblueredpurple:limb_data");
    /** Shared Forge capability handle used to request limb data from entities. */
    public static final Capability<LimbData> LIMB_DATA = CapabilityManager.get((CapabilityToken)new CapabilityToken<LimbData>(){});
    /** Backing data object that stores all limb state for one entity. */
    private final LimbData data = new LimbData();
    /** Lazy wrapper exposed through the Forge capability API. */
    private final LazyOptional<LimbData> optional = LazyOptional.of(() -> this.data);

    /**
     * Exposes the limb capability when requested.
     *
     * @param cap requested capability type
     * @param side logical side query, unused for this capability
     * @param <T> capability payload type
     * @return the limb data optional when requested, otherwise an empty optional
     */
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == LIMB_DATA) {
            return this.optional.cast();
        }
        return LazyOptional.empty();
    }

    /**
     * Serializes the capability payload into NBT.
     *
     * @return serialized limb data
     */
    public CompoundTag serializeNBT() {
        return this.data.serializeNBT();
    }

    /**
     * Restores the capability payload from NBT.
     *
     * @param nbt serialized limb data
     */
    public void deserializeNBT(CompoundTag nbt) {
        this.data.deserializeNBT(nbt);
    }

    /**
     * Invalidates the lazy optional when the provider is being torn down.
     */
    public void invalidate() {
        this.optional.invalidate();
    }

    // ===== CAPABILITY REGISTRATION AND ATTACHMENT =====

    /**
     * Registers the limb capability with Forge.
     *
     * @param event Forge capability registration event
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(LimbData.class);
    }

    /**
     * Attaches the limb capability to every living entity.
     *
     * @param event capability attachment event for an entity
     */
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            event.addCapability(ID, (ICapabilityProvider)new LimbCapabilityProvider());
        }
    }

    // ===== PLAYER LIFECYCLE =====

    /**
     * Handles player cloning after dimension changes and death.
     *
     * <p>On death, the replacement player is intentionally reset to intact limbs. On non-death clones,
     * the old capability data is copied forward.</p>
     *
     * @param event Forge clone event
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player player = event.getEntity();
            if (player instanceof ServerPlayer) {
                ServerPlayer newPlayer = (ServerPlayer)player;
                newPlayer.getCapability(LIMB_DATA).ifPresent(data -> {
                    for (LimbType t : LimbType.values()) {
                        data.setState(t, LimbState.INTACT);
                        data.setRegenProgress(t, 0.0f);
                    }
                    // Death fully clears temporary limb timers and gameplay penalties.
                    data.setSeverCooldownTicks(0);
                    data.setBloodDripTicks(0);
                    LimbGameplayHandler.removeAllModifiers((LivingEntity)newPlayer);
                });
            }
            return;
        }
        // Non-death clones keep their previous limb state.
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(LIMB_DATA).ifPresent(oldData -> event.getEntity().getCapability(LIMB_DATA).ifPresent(newData -> newData.copyFrom((LimbData)oldData)));
        event.getOriginal().invalidateCaps();
    }

    /**
     * Resends limb state to a player after respawn so the client cache is correct.
     *
     * @param event Forge player respawn event
     */
    @SubscribeEvent
    public static void onPlayerRespawnServer(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer sp = (ServerPlayer)player;
        sp.getCapability(LIMB_DATA).ifPresent(data -> {
            LimbSyncPacket.sendToPlayer(sp, (LivingEntity)sp, data);
            LimbSyncPacket.sendToTrackingPlayers((LivingEntity)sp, data);
        });
    }

    /**
     * Convenience getter for retrieving limb data from a living entity.
     *
     * @param entity entity that may own limb data
     * @return lazy optional wrapping the capability payload
     */
    public static LazyOptional<LimbData> get(LivingEntity entity) {
        return entity.getCapability(LIMB_DATA);
    }
}
