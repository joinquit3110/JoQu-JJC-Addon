package net.mcreator.jujutsucraft.addon.mixin;

import java.util.Objects;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Barrier placement mixin for `DomainExpansionBattleProcedure.execute()`.
 *
 * <p>Incomplete domains now follow the base mod's Megumi-style path directly:
 * startup marks the cast as incomplete, `GetDomainBlockProcedure` leaves the
 * shell palette empty, and the shared spherical builder performs all growth.
 * This mixin only tracks ownership / restore metadata for placed barrier blocks
 * and preserves the closed-domain floor correction logic.</p>
 */
@Mixin(value={DomainExpansionBattleProcedure.class}, remap=false)
public abstract class DomainBarrierMixin {
    @Unique
    private static final ThreadLocal<Entity> JJKBRP$currentCaster = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<BlockPos> JJKBRP$expectedFloorPos = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<String> JJKBRP$expectedFloorBlock = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> JJKBRP$expectedFloorPlaced = new ThreadLocal<>();
    @Unique
    private static final ResourceLocation JJKBRP$BARRIER_TAG_ID = Objects.requireNonNull(ResourceLocation.tryParse("jujutsucraft:barrier"));

    @Shadow(remap=false)
    private static void placeBlockSafe(LevelAccessor world, BlockPos pos, String blockName) {
    }

    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$captureCaster(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Entity stale = JJKBRP$currentCaster.get();
        if (stale != null) {
            JJKBRP$currentCaster.remove();
        }
        JJKBRP$currentCaster.set(entity);
        JJKBRP$expectedFloorPos.remove();
        JJKBRP$expectedFloorBlock.remove();
        JJKBRP$expectedFloorPlaced.remove();
        if (entity instanceof LivingEntity) {
            LivingEntity caster = (LivingEntity)entity;
            CompoundTag nbt = caster.getPersistentData();
            String floorBlock = nbt.getString("domain_floor");
            if (!world.isClientSide() && !DomainBarrierMixin.jjkbrp$isOpenDomainState(caster, nbt) && !DomainBarrierMixin.jjkbrp$isIncompleteDomainState(caster) && nbt.contains("jjkbrp_caster_x_at_cast") && nbt.contains("jjkbrp_caster_y_at_cast") && nbt.contains("jjkbrp_caster_z_at_cast") && !floorBlock.isEmpty() && !"minecraft:air".equals(floorBlock)) {
                int floorY = (int)Math.floor(nbt.getDouble("jjkbrp_caster_y_at_cast")) - 1;
                BlockPos expectedFloor = BlockPos.containing(nbt.getDouble("jjkbrp_caster_x_at_cast"), floorY, nbt.getDouble("jjkbrp_caster_z_at_cast"));
                JJKBRP$expectedFloorPos.set(expectedFloor);
                JJKBRP$expectedFloorBlock.set(floorBlock);
                JJKBRP$expectedFloorPlaced.set(Boolean.FALSE);
            }
        }
    }

    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$clearCaster(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        BlockPos expectedFloor = JJKBRP$expectedFloorPos.get();
        String floorBlock = JJKBRP$expectedFloorBlock.get();
        Boolean floorPlaced = JJKBRP$expectedFloorPlaced.get();
        if (expectedFloor != null && Boolean.FALSE.equals(floorPlaced) && floorBlock != null && !floorBlock.isEmpty()) {
            DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, expectedFloor, floorBlock);
        }
        JJKBRP$currentCaster.remove();
        JJKBRP$expectedFloorPos.remove();
        JJKBRP$expectedFloorBlock.remove();
        JJKBRP$expectedFloorPlaced.remove();
    }

    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure;placeBlockSafe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Ljava/lang/String;)V", remap=false), remap=false)
    private static void jjkbrp$redirectPlaceBlock(LevelAccessor world, BlockPos pos, String blockName) {
        Entity caster = JJKBRP$currentCaster.get();
        if (!(caster instanceof LivingEntity)) {
            DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
            return;
        }
        LivingEntity livingCaster = (LivingEntity)caster;
        CompoundTag nbt = livingCaster.getPersistentData();
        if (DomainBarrierMixin.jjkbrp$isOpenDomainState(livingCaster, nbt)) {
            return;
        }
        if (DomainBarrierMixin.jjkbrp$isIncompleteDomainState(livingCaster)) {
            if (blockName != null && !blockName.isEmpty()) {
                nbt.putBoolean("jjkbrp_barrier_blocks_placed", true);
                DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, pos, blockName);
            }
            return;
        }
        String outside = nbt.getString("domain_outside");
        boolean isOutsideWall = !outside.isEmpty() && blockName != null && blockName.equals(outside);
        String floor = nbt.getString("domain_floor");
        String inside = nbt.getString("domain_inside");
        boolean floorMatchesInside = !floor.isEmpty() && floor.equals(inside);
        boolean isFloorBlock = !floor.isEmpty() && blockName != null && blockName.equals(floor);
        BlockPos expectedFloorPos = JJKBRP$expectedFloorPos.get();
        if (isFloorBlock && expectedFloorPos != null && expectedFloorPos.equals(pos)) {
            JJKBRP$expectedFloorPlaced.set(Boolean.TRUE);
        }
        if (isFloorBlock && !floorMatchesInside && nbt.contains("jjkbrp_caster_y_at_cast")) {
            int correctFloorY = (int)Math.floor(nbt.getDouble("jjkbrp_caster_y_at_cast")) - 1;
            if (pos.getY() > correctFloorY) {
                return;
            }
        }
        nbt.putBoolean("jjkbrp_barrier_blocks_placed", true);
        DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
        if (isOutsideWall || DomainBarrierMixin.jjkbrp$shouldCleanEdgeShellPlacement(pos, blockName, livingCaster)) {
            DomainBarrierMixin.jjkbrp$cleanAdjacentWallBlocks(world, pos, livingCaster);
        }
    }

    @Unique
    private static boolean jjkbrp$isBarrierBlock(BlockState state) {
        return state.is(BlockTags.create(JJKBRP$BARRIER_TAG_ID));
    }

    @Unique
    private static boolean jjkbrp$isIncompleteDomainState(LivingEntity entity) {
        return DomainAddonUtils.isIncompleteDomainState(entity);
    }

    @Unique
    private static void jjkbrp$placeTrackedBarrierBlock(LevelAccessor world, BlockPos pos, String blockName) {
        if (world.isClientSide() || blockName == null || blockName.isEmpty()) {
            return;
        }
        BlockState currentState = world.getBlockState(pos);
        BlockEntity existingBe = world.getBlockEntity(pos);
        String oldBlock = DomainBarrierMixin.jjkbrp$isBarrierBlock(currentState) ? (existingBe != null ? existingBe.getPersistentData().getString("old_block") : "") : String.valueOf(currentState).replace("}", "").replace("Block{", "");
        DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
        BlockState placedState = world.getBlockState(pos);
        BlockEntity placedBe = world.getBlockEntity(pos);
        if (DomainBarrierMixin.jjkbrp$isBarrierBlock(placedState) && placedBe != null) {
            placedBe.getPersistentData().putString("old_block", oldBlock);
            Entity caster = JJKBRP$currentCaster.get();
            if (caster != null) {
                placedBe.getPersistentData().putString("OWNER_UUID", caster.getUUID().toString());
            }
        }
        Entity caster = JJKBRP$currentCaster.get();
        if (caster instanceof LivingEntity) {
            ((LivingEntity)caster).getPersistentData().putBoolean("jjkbrp_barrier_blocks_placed", true);
        }
        BlockPos expectedFloorPos = JJKBRP$expectedFloorPos.get();
        if (expectedFloorPos != null && expectedFloorPos.equals(pos)) {
            JJKBRP$expectedFloorPlaced.set(Boolean.TRUE);
        }
    }

    @Unique
    private static void jjkbrp$cleanAdjacentWallBlocks(LevelAccessor world, BlockPos wallPos, LivingEntity caster) {
        if (world.isClientSide() || caster == null) {
            return;
        }
        CompoundTag nbt = caster.getPersistentData();
        double domainId = nbt.getDouble("select") > 0.0 ? nbt.getDouble("select") : nbt.getDouble("skill_domain");
        if (Math.round(domainId) == 29L) {
            return;
        }
        if (caster.hasEffect(JujutsucraftModMobEffects.ZONE.get())) {
            return;
        }
        String floorBlock = nbt.getString("domain_floor");
        double actualRadius = DomainAddonUtils.getActualDomainRadius(world, nbt);
        if (actualRadius <= 0.0) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(caster);
        double floorY = nbt.contains("x_pos_doma") ? nbt.getDouble("y_pos_doma") - 1.0 : Math.floor(caster.getY()) - 1.0;
        double maxEdgeDistanceSq = (actualRadius + 1.25) * (actualRadius + 1.25);
        double innerDistanceSq = actualRadius * actualRadius;
        double seamBandMinSq = Math.max(0.0, (actualRadius - 3.0) * (actualRadius - 3.0));
        for (int dxOffset = -1; dxOffset <= 1; ++dxOffset) {
            for (int dyOffset = -1; dyOffset <= 1; ++dyOffset) {
                for (int dzOffset = -1; dzOffset <= 1; ++dzOffset) {
                    if (dxOffset == 0 && dyOffset == 0 && dzOffset == 0) {
                        continue;
                    }
                    BlockPos targetPos = wallPos.offset(dxOffset, dyOffset, dzOffset);
                    BlockState targetState = world.getBlockState(targetPos);
                    if (targetState.isAir() || DomainBarrierMixin.jjkbrp$isBarrierBlock(targetState)) {
                        continue;
                    }
                    double dx = targetPos.getX() - center.x;
                    double dy = targetPos.getY() - center.y;
                    double dz = targetPos.getZ() - center.z;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > maxEdgeDistanceSq) {
                        continue;
                    }
                    if (targetPos.getY() > floorY && distanceSq < seamBandMinSq) {
                        continue;
                    }
                    String replacement = "jujutsucraft:in_barrier";
                    if (targetPos.getY() <= floorY && targetPos.getY() >= floorY - 4.0 && distanceSq < innerDistanceSq && !floorBlock.isEmpty() && !"minecraft:air".equals(floorBlock)) {
                        replacement = floorBlock;
                    }
                    DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, targetPos, replacement);
                }
            }
        }
    }

    @Unique
    private static boolean jjkbrp$shouldCleanEdgeShellPlacement(BlockPos pos, String blockName, LivingEntity caster) {
        if (caster == null || blockName == null || blockName.isEmpty()) {
            return false;
        }
        CompoundTag nbt = caster.getPersistentData();
        String outside = nbt.getString("domain_outside");
        String inside = nbt.getString("domain_inside");
        String floor = nbt.getString("domain_floor");
        boolean shellBlock = blockName.equals(outside) || blockName.equals(inside) || "jujutsucraft:in_barrier".equals(blockName) || blockName.equals(floor);
        if (!shellBlock) {
            return false;
        }
        double actualRadius = DomainAddonUtils.getActualDomainRadius(caster.level(), nbt);
        if (actualRadius <= 0.0) {
            return false;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(caster);
        double dx = pos.getX() - center.x;
        double dy = pos.getY() - center.y;
        double dz = pos.getZ() - center.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double shellBandMinSq = Math.max(0.0, (actualRadius - 3.0) * (actualRadius - 3.0));
        double shellBandMaxSq = (actualRadius + 1.25) * (actualRadius + 1.25);
        return distanceSq >= shellBandMinSq && distanceSq <= shellBandMaxSq;
    }

    @Unique
    private static boolean jjkbrp$isOpenDomainState(LivingEntity caster, CompoundTag nbt) {
        return DomainAddonUtils.isOpenDomainState(caster);
    }
}
