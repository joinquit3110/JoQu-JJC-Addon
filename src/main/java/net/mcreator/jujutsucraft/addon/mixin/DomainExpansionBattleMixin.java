package net.mcreator.jujutsucraft.addon.mixin;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Battle-phase normalization mixin for `DomainExpansionBattleProcedure.execute()` that keeps the cleanup entity aligned to the current domain center and range both before and after the battle tick.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionBattleProcedure.class}, remap=false)
public abstract class DomainExpansionBattleMixin {
    /**
     * Injects at battle start to normalize cleanup entities and snapshot the current runtime form.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$markDomainBattleStart(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (player.level().isClientSide()) {
            return;
        }
        // Normalize cleanup entities on both entry and exit so battle ticks never leave behind duplicate or stale cleanup markers.
        DomainExpansionBattleMixin.jjkbrp$normalizeCleanupEntities(world, player);
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY).ifPresent(data -> player.getPersistentData().putInt("jjkbrp_domain_form_runtime", data.getDomainTypeSelected()));
    }

    /**
     * Injects after the battle tick so cleanup entities remain centered and only one authoritative instance survives.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$normalizeCleanupEntitiesAfterBattle(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (player.level().isClientSide()) {
            return;
        }
        // Normalize cleanup entities on both entry and exit so battle ticks never leave behind duplicate or stale cleanup markers.
        DomainExpansionBattleMixin.jjkbrp$normalizeCleanupEntities(world, player);
    }

    /**
     * Keeps the nearest cleanup entity synchronized to the actual domain center and radius while discarding duplicates.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */
    private static void jjkbrp$normalizeCleanupEntities(LevelAccessor world, Player player) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        if (player.getPersistentData().getBoolean("jjkbrp_open_form_active")) {
            return;
        }
        if (!DomainAddonUtils.isDomainBuildOrActive(serverLevel, player)) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)player);
        double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)serverLevel, player.getPersistentData());
        double searchRadius = Math.max(3.0, actualRadius + 2.0);
        List<DomainExpansionEntityEntity> entities = serverLevel.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(center.x - searchRadius, center.y - searchRadius, center.z - searchRadius, center.x + searchRadius, center.y + searchRadius, center.z + searchRadius), e -> true);
        if (entities.isEmpty()) {
            return;
        }
        String playerUuid = player.getUUID().toString();
        Comparator<DomainExpansionEntityEntity> distanceComparator = Comparator.comparingDouble(cleanup -> cleanup.distanceToSqr(center.x, center.y, center.z));
        List<DomainExpansionEntityEntity> ownedOrClaimable = entities.stream()
                .filter(cleanup -> DomainExpansionBattleMixin.jjkbrp$isOwnedByOrClaimableFor(cleanup, playerUuid, center))
                .toList();
        if (ownedOrClaimable.isEmpty()) {
            return;
        }
        // Keep the player's own non-breaking cleanup entity when possible. Never discard cleanup anchors owned by other live casters.
        DomainExpansionEntityEntity keeper = ownedOrClaimable.stream().filter(cleanup -> !cleanup.getPersistentData().getBoolean("Break")).min(distanceComparator).orElseGet(() -> ownedOrClaimable.stream().min(distanceComparator).orElse(null));
        if (keeper == null) {
            return;
        }
        for (DomainExpansionEntityEntity cleanup2 : ownedOrClaimable) {
            if (cleanup2 == keeper) continue;
            cleanup2.discard();
        }
        CompoundTag entityNbt = keeper.getPersistentData();
        entityNbt.putString("jjkbrp_owner_uuid", playerUuid);        entityNbt.putDouble("x_pos", center.x);
        entityNbt.putDouble("y_pos", center.y);
        entityNbt.putDouble("z_pos", center.z);
        entityNbt.putDouble("range", actualRadius);
        entityNbt.putBoolean("Break", false);
        entityNbt.putDouble("cnt_life2", 0.0);
        entityNbt.putDouble("cnt_break", 0.0);
        keeper.setDeltaMovement(Vec3.ZERO);
        keeper.setPos(center.x, center.y, center.z);
    }
    @Unique
    private static boolean jjkbrp$isOwnedByOrClaimableFor(DomainExpansionEntityEntity cleanup, String ownerUuid, Vec3 center) {
        if (cleanup == null || ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        CompoundTag nbt = cleanup.getPersistentData();
        String existingOwner = nbt.getString("jjkbrp_owner_uuid");
        if (existingOwner == null || existingOwner.isEmpty()) {
            existingOwner = nbt.getString("OWNER_UUID");
        }
        if (ownerUuid.equals(existingOwner)) {
            return true;
        }
        if (existingOwner != null && !existingOwner.isEmpty() && DomainExpansionBattleMixin.jjkbrp$isValidUuid(existingOwner)) {
            return false;
        }
        Vec3 cleanupCenter = nbt.contains("x_pos") ? new Vec3(nbt.getDouble("x_pos"), nbt.getDouble("y_pos"), nbt.getDouble("z_pos")) : cleanup.position();
        return cleanupCenter.distanceToSqr(center) <= 9.0 && cleanup.distanceToSqr(center.x, center.y, center.z) <= 16.0;
    }

    @Unique
    private static boolean jjkbrp$isValidUuid(String raw) {
        try {
            UUID.fromString(raw);
            return true;
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
