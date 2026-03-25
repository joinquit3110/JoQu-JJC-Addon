package net.mcreator.jujutsucraft.addon.limb;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
 * Forge Capability provider that attaches {@link LimbData} to every {@link LivingEntity}.
 *
 * <h2>Capability layout</h2>
 * One {@link LimbData} instance per entity, stored in the capability system. The data
 * survives player death via soft-clone ({@link #onPlayerClone}) but is reset on
 * hard death (respawning without keeping inventory).
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link RegisterCapabilitiesEvent} — registers the capability type.</li>
 *   <li>{@link AttachCapabilitiesEvent} — attaches the provider to every living entity.</li>
 *   <li>{@link PlayerEvent.Clone} — copies data on death (soft) or resets (hard).</li>
 *   <li>{@link PlayerEvent.PlayerRespawnEvent} — re-syncs data to the respawned player.</li>
 * </ul>
 *
 * @see LimbData
 * @see LimbSyncPacket
 */

@Mod.EventBusSubscriber(modid = "jjkblueredpurple")
public class LimbCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    public static final ResourceLocation ID = new ResourceLocation("jjkblueredpurple:limb_data");
    public static final Capability<LimbData> LIMB_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private final LimbData data = new LimbData();
    private final LazyOptional<LimbData> optional = LazyOptional.of(() -> this.data);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == LIMB_DATA) {
            return this.optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return this.data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.data.deserializeNBT(nbt);
    }

    public void invalidate() {
        this.optional.invalidate();
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(LimbData.class);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            event.addCapability(ID, new LimbCapabilityProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player player = event.getEntity();
            if (player instanceof ServerPlayer) {
                ServerPlayer newPlayer = (ServerPlayer) player;
                newPlayer.getCapability(LIMB_DATA).ifPresent(data -> {
                    for (LimbType t : LimbType.values()) {
                        data.setState(t, LimbState.INTACT);
                        data.setRegenProgress(t, 0.0f);
                    }
                    data.setSeverCooldownTicks(0);
                    data.setBloodDripTicks(0);
                    LimbGameplayHandler.removeAllModifiers(newPlayer);
                });
            }
            return;
        }
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(LIMB_DATA).ifPresent(oldData ->
            event.getEntity().getCapability(LIMB_DATA).ifPresent(newData -> newData.copyFrom(oldData)));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerRespawnServer(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        sp.getCapability(LIMB_DATA).ifPresent(data -> LimbSyncPacket.sendToPlayer(sp, sp, data));
    }

    public static LazyOptional<LimbData> get(LivingEntity entity) {
        return entity.getCapability(LIMB_DATA);
    }
}
