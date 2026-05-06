package net.mcreator.jujutsucraft.addon;

import javax.annotation.Nullable;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", bus=Mod.EventBusSubscriber.Bus.FORGE)
/**
 * Forge capability bootstrap for domain mastery data. It attaches the capability to real players, copies data across death clones, serializes NBT, and keeps the client synchronized after lifecycle events.
 */
public class DomainMasteryCapabilityProvider {
    // Capability identifier used when attaching domain mastery data to player entities.
    public static final ResourceLocation IDENTIFIER = new ResourceLocation("jjkblueredpurple", "domain_mastery");
    // Forge capability handle for addon domain mastery data.
    public static Capability<DomainMasteryData> DOMAIN_MASTERY_CAPABILITY = CapabilityManager.get((CapabilityToken)new CapabilityToken<DomainMasteryData>(){});

    @SubscribeEvent
    /**
     * Handles the attach capabilities callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !(event.getObject() instanceof FakePlayer)) {
            event.addCapability(IDENTIFIER, (ICapabilityProvider)new DomainMasteryCapabilitySerializable());
        }
    }

    @SubscribeEvent
    /**
     * Handles the player login callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            sp.getCapability(DOMAIN_MASTERY_CAPABILITY).ifPresent(data -> data.syncToClient(sp));
        }
    }

    @SubscribeEvent
    /**
     * Handles the player respawn callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            BlueRedPurpleNukeMod.clearBlackFlashRuntimeState(sp);
            sp.getCapability(DOMAIN_MASTERY_CAPABILITY).ifPresent(data -> data.syncToClient(sp));
        }
    }

    @SubscribeEvent
    /**
     * Handles the player change dimension callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            sp.getCapability(DOMAIN_MASTERY_CAPABILITY).ifPresent(data -> data.syncToClient(sp));
        }
    }

    @SubscribeEvent
    /**
     * Handles the player clone callback for this addon component.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getOriginal().reviveCaps();
            event.getOriginal().getCapability(DOMAIN_MASTERY_CAPABILITY).ifPresent(oldData -> event.getEntity().getCapability(DOMAIN_MASTERY_CAPABILITY).ifPresent(newData -> {
                CompoundTag saved = oldData.writeNBT();
                newData.readNBT(saved);
            }));
            DomainMasteryCapabilityProvider.copyBlackFlashMasteryPersistentData(event.getOriginal(), event.getEntity());
            event.getOriginal().invalidateCaps();
        }
    }

    /**
     * Copies persistent Black Flash unlock-only state across the death clone while intentionally resetting runtime flow/cooldown/timing state.
     * @param original original player entity involved in this operation.
     * @param cloned replacement player entity involved in this operation.
     */
    private static void copyBlackFlashMasteryPersistentData(Player original, Player cloned) {
        CompoundTag oldData = original.getPersistentData();
        CompoundTag newData = cloned.getPersistentData();
        if (oldData.contains("addon_bf_mastery")) {
            newData.putBoolean("addon_bf_mastery", oldData.getBoolean("addon_bf_mastery"));
        }
        if (oldData.contains("addon_bf_total_hits")) {
            newData.putInt("addon_bf_total_hits", oldData.getInt("addon_bf_total_hits"));
        }
        if (oldData.contains("addon_bf_sparks_blessed")) {
            newData.putBoolean("addon_bf_sparks_blessed", oldData.getBoolean("addon_bf_sparks_blessed"));
        }
        newData.putInt("addon_bf_flow", 0);
        newData.putInt("addon_bf_flow_cd", 0);
        newData.putBoolean("addon_bf_guaranteed", false);
        newData.putBoolean("addon_bf_waiting_hit", false);
        newData.putBoolean("addon_bf_charging", false);
        newData.putBoolean("addon_bf_released", false);
        newData.putBoolean("addon_bf_timing_resolved", false);
        newData.putBoolean("addon_bf_resolved_cooldown", false);
        newData.putBoolean("addon_bf_no_retrigger_until_cnt6_drop", false);
        newData.putBoolean("addon_bf_block_until_charge_released", false);
        newData.putString("addon_bf_state", "IDLE");
        newData.remove("addon_bf_release_tick");
        newData.remove("addon_bf_guarantee_nonce");
        newData.remove("addon_bf_timing_start_tick");
        newData.remove("addon_bf_timing_period_ticks");
        newData.remove("addon_bf_timing_red_start");
        newData.remove("addon_bf_timing_red_size");
        newData.remove("addon_bf_resolved_cooldown_tick");
        newData.remove("addon_bf_resolved_cnt6");
        newData.remove("addon_bf_no_retrigger_tick");
    }

    /**
     * Serializable provider that exposes the domain mastery data instance as a Forge capability and persists it through NBT.
     */
    private static class DomainMasteryCapabilitySerializable
    implements ICapabilitySerializable<Tag> {
        // Backing data instance exposed by this serializable capability provider.
        private final DomainMasteryData data = new DomainMasteryData();
        // Lazy capability handle returned to Forge capability consumers.
        private final LazyOptional<DomainMasteryData> optional = LazyOptional.of(() -> this.data);

        /**
         * Creates a new domain mastery capability serializable instance and initializes its addon state.
         */
        private DomainMasteryCapabilitySerializable() {
        }

        /**
         * Returns capability for the current addon state.
         * @param cap cap used by this method.
         * @param side side used by this method.
         * @return the resolved capability.
         */
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == DOMAIN_MASTERY_CAPABILITY ? this.optional.cast() : LazyOptional.empty();
        }

        /**
         * Performs serialize nbt for this addon component.
         * @return the resulting serialize nbt value.
         */
        public Tag serializeNBT() {
            return this.data.writeNBT();
        }

        /**
         * Performs deserialize nbt for this addon component.
         * @param nbt serialized data container used by this operation.
         */
        public void deserializeNBT(Tag nbt) {
            if (nbt instanceof CompoundTag) {
                CompoundTag compound = (CompoundTag)nbt;
                this.data.readNBT(compound);
            }
        }
    }

    @Mod.EventBusSubscriber(modid="jjkblueredpurple", bus=Mod.EventBusSubscriber.Bus.MOD)
    /**
     * Mod-bus subscriber that registers the domain mastery capability type during addon startup.
     */
    public static class ModBusRegistration {
        @SubscribeEvent
        /**
         * Registers addon content with the appropriate Forge or client system.
         * @param event context data supplied by the current callback or network pipeline.
         */
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(DomainMasteryData.class);
        }
    }
}

