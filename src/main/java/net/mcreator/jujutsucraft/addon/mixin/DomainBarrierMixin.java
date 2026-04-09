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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Barrier placement mixin for `DomainExpansionBattleProcedure.execute()` that redirects barrier block placement to support incomplete-domain custom shapes, floor correction, and edge-shell cleanup.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionBattleProcedure.class}, remap=false)
public abstract class DomainBarrierMixin {
    // Thread-local caster reference captured for the current barrier-placement pass so redirected placement logic can inspect the owner.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Entity> JJKBRP$currentCaster = new ThreadLocal();
    // Thread-local floor position backup used when incomplete barrier floor correction needs to validate the next placement.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<BlockPos> JJKBRP$expectedFloorPos = new ThreadLocal();
    // Thread-local block id backup used for incomplete floor replacement validation.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<String> JJKBRP$expectedFloorBlock = new ThreadLocal();
    // Thread-local flag tracking whether the expected floor block was successfully placed during wrapped-floor shaping.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ThreadLocal<Boolean> JJKBRP$expectedFloorPlaced = new ThreadLocal();
    // Barrier block tag id used when deciding whether nearby shell blocks should be cleaned up.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final ResourceLocation JJKBRP$BARRIER_TAG_ID = Objects.requireNonNull(ResourceLocation.tryParse((String)"jujutsucraft:barrier"));
    // Maximum vertical climb distance used while wrapping incomplete-domain wall placement upward.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final int JJKBRP$INCOMPLETE_CLIMB_MAX_HEIGHT = 6;

    /**
     * Performs place block safe for this mixin.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     */
    // References an existing target-class member so this mixin can call or mirror it without redefining the original implementation.
    @Shadow(remap=false)
    private static void placeBlockSafe(LevelAccessor world, BlockPos pos, String blockName) {
    }


    // ===== CASTER CONTEXT =====
    /**
     * Captures the current domain caster before barrier placement begins so redirected placement calls can inspect the active owner.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$captureCaster(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        Entity stale = JJKBRP$currentCaster.get();
        if (stale != null) {
            JJKBRP$currentCaster.remove();
        }
        // Capture the caster at method entry because the redirected block-placement helper does not receive the owner directly.
        JJKBRP$currentCaster.set(entity);
        JJKBRP$expectedFloorPos.remove();
        JJKBRP$expectedFloorBlock.remove();
        JJKBRP$expectedFloorPlaced.remove();
        if (entity instanceof LivingEntity) {
            String floorBlock;
            LivingEntity caster = (LivingEntity)entity;
            CompoundTag nbt = caster.getPersistentData();
            if (!world.isClientSide() && !DomainBarrierMixin.jjkbrp$isOpenDomainState(caster, nbt) && nbt.contains("jjkbrp_caster_x_at_cast") && nbt.contains("jjkbrp_caster_y_at_cast") && nbt.contains("jjkbrp_caster_z_at_cast") && !(floorBlock = nbt.getString("domain_floor")).isEmpty() && !"minecraft:air".equals(floorBlock)) {
                int floorY = (int)Math.floor(nbt.getDouble("jjkbrp_caster_y_at_cast")) - 1;
                BlockPos expectedFloor = BlockPos.containing((double)nbt.getDouble("jjkbrp_caster_x_at_cast"), (double)floorY, (double)nbt.getDouble("jjkbrp_caster_z_at_cast"));
                JJKBRP$expectedFloorPos.set(expectedFloor);
                JJKBRP$expectedFloorBlock.set(floorBlock);
                JJKBRP$expectedFloorPlaced.set(Boolean.FALSE);
            }
            if (!world.isClientSide() && DomainBarrierMixin.jjkbrp$isIncompleteDomainState(caster)) {
                DomainBarrierMixin.jjkbrp$seedIncompleteWrapShell(world, caster, nbt);
            }
        }
    }

    /**
     * Clears the thread-local barrier-placement context after the base battle procedure finishes.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
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


    // ===== INCOMPLETE BARRIER HELPERS =====
    /**
     * Performs is incomplete support block for this mixin.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @return whether is incomplete support block is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isIncompleteSupportBlock(LevelAccessor world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (DomainBarrierMixin.jjkbrp$isBarrierBlock(state)) {
            return false;
        }
        return state.canOcclude() || state.blocksMotion();
    }

    /**
     * Performs is barrier block for this mixin.
     * @param state state used by this method.
     * @return whether is barrier block is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isBarrierBlock(BlockState state) {
        return state.is(BlockTags.create((ResourceLocation)JJKBRP$BARRIER_TAG_ID));
    }

    @Unique
    private static boolean jjkbrp$hasAdjacentWallSupport(LevelAccessor world, BlockPos pos) {
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                BlockPos adjacent = pos.offset(dx, 0, dz);
                BlockState adjState = world.getBlockState(adjacent);
                if (!adjState.isAir() && !DomainBarrierMixin.jjkbrp$isBarrierBlock(adjState) && (adjState.canOcclude() || adjState.blocksMotion())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Performs is weak overwriteable floor block for this mixin.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param state state used by this method.
     * @return whether is weak overwriteable floor block is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isWeakOverwriteableFloorBlock(LevelAccessor world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (DomainBarrierMixin.jjkbrp$isBarrierBlock(state)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (!state.canOcclude() && !state.blocksMotion()) {
            return true;
        }
        if (state.getCollisionShape((BlockGetter)world, pos).isEmpty()) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.contains("torch") || path.contains("flower") || path.contains("grass") || path.contains("fern") || path.contains("sapling") || path.contains("mushroom") || path.contains("vine") || path.contains("bush") || path.contains("carpet") || path.contains("seagrass") || path.contains("lily_pad");
    }


    // ===== INCOMPLETE BARRIER SHAPING =====
    /**
     * Places incomplete-domain wall blocks using the custom wrapped shell logic instead of the default closed barrier pattern.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     * @param caster entity involved in the current mixin operation.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$placeIncompleteWrappedWall(LevelAccessor world, BlockPos pos, String blockName, LivingEntity caster) {
        if (blockName == null || blockName.isEmpty()) {
            return;
        }
        BlockState currentState = world.getBlockState(pos);
        boolean currentIsBarrier = DomainBarrierMixin.jjkbrp$isBarrierBlock(currentState);
        if (currentIsBarrier || currentState.isAir()) {
            DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
            return;
        }
        if (caster == null) {
            return;
        }
        CompoundTag casterNbt = caster.getPersistentData();
        int maxFloorY = DomainBarrierMixin.jjkbrp$getFloorY(casterNbt, caster);
        int maxClimbY = maxFloorY + JJKBRP$INCOMPLETE_CLIMB_MAX_HEIGHT;
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
        double actualRadius = DomainAddonUtils.getActualDomainRadius(world, casterNbt);
        if (actualRadius <= 0.0) {
            return;
        }
        double shellBandMinSq = Math.max(0.0, (actualRadius - 2.0) * (actualRadius - 2.0));
        double shellBandMaxSq = (actualRadius + 1.5) * (actualRadius + 1.5);
        for (int up = 1; up <= JJKBRP$INCOMPLETE_CLIMB_MAX_HEIGHT; ++up) {
            BlockPos climbPos = pos.above(up);
            if (climbPos.getY() > maxClimbY) break;
            BlockState climbState = world.getBlockState(climbPos);
            if (!climbState.isAir() && !DomainBarrierMixin.jjkbrp$isBarrierBlock(climbState)) {
                DomainBarrierMixin.jjkbrp$extendCeilingHorizontally(world, climbPos.below(), blockName, center, actualRadius);
                break;
            }
            BlockPos supportPos = climbPos.below();
            boolean hasVerticalSupport = DomainBarrierMixin.jjkbrp$isBarrierBlock(world.getBlockState(supportPos))
                    || DomainBarrierMixin.jjkbrp$isIncompleteSupportBlock(world, supportPos)
                    || DomainBarrierMixin.jjkbrp$hasAdjacentWallSupport(world, climbPos);
            if (!hasVerticalSupport) continue;
            double dx = (double)climbPos.getX() - center.x;
            double dz = (double)climbPos.getZ() - center.z;
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq < shellBandMinSq || horizontalDistSq > shellBandMaxSq) continue;
            DomainBarrierMixin.placeBlockSafe(world, climbPos, blockName);
        }
    }

    @Unique
    private static void jjkbrp$extendCeilingHorizontally(LevelAccessor world, BlockPos contactPos, String blockName,
                                                          Vec3 center, double radius) {
        double dirX = center.x - (double)contactPos.getX();
        double dirZ = center.z - (double)contactPos.getZ();
        double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (dirLen < 0.001) return;
        dirX /= dirLen;
        dirZ /= dirLen;
        int maxCeilingSpan = (int)(radius * 0.7);
        int ceilingY = contactPos.getY();
        for (int step = 1; step <= maxCeilingSpan; step++) {
            int nx = contactPos.getX() + (int)Math.round(dirX * step);
            int nz = contactPos.getZ() + (int)Math.round(dirZ * step);
            BlockPos ceilingAbove = new BlockPos(nx, ceilingY + 1, nz);
            BlockPos ceilingPlace = new BlockPos(nx, ceilingY, nz);
            BlockState aboveState = world.getBlockState(ceilingAbove);
            if (aboveState.isAir()) break;
            BlockState placeState = world.getBlockState(ceilingPlace);
            if (placeState.isAir() || DomainBarrierMixin.jjkbrp$isBarrierBlock(placeState)) {
                DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, ceilingPlace, blockName);
            }
            double dx = (double)ceilingPlace.getX() - center.x;
            double dz = (double)ceilingPlace.getZ() - center.z;
            if (dx * dx + dz * dz < radius * 0.3 * radius * 0.3) break;
        }
    }

    /**
     * Places the incomplete-domain floor while correcting weak or mismatched floor blocks.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$placeIncompleteWrappedFloor(LevelAccessor world, BlockPos pos, String blockName) {
        if (blockName == null || blockName.isEmpty()) {
            return;
        }
        Entity casterEntity = JJKBRP$currentCaster.get();
        if (casterEntity instanceof LivingEntity) {
            LivingEntity caster = (LivingEntity)casterEntity;
            CompoundTag nbt = caster.getPersistentData();
            int floorY = DomainBarrierMixin.jjkbrp$getFloorY(nbt, caster);
            Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
            double radius = Math.max(2.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
            String wallBlock = nbt.getString("domain_outside");
            if (wallBlock == null || wallBlock.isEmpty() || "minecraft:air".equals(wallBlock)) {
                wallBlock = blockName;
            }
            DomainBarrierMixin.jjkbrp$applyIncompleteCrawlerAt(world, pos, blockName, wallBlock, center, radius, floorY);
            return;
        }
        BlockState currentState = world.getBlockState(pos);
        if (DomainBarrierMixin.jjkbrp$canReplaceForIncompleteFloor(world, pos, currentState)) {
            DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, pos, blockName);
        }
    }

    @Unique
    private static boolean jjkbrp$canReplaceForIncompleteFloor(LevelAccessor world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (DomainBarrierMixin.jjkbrp$isBarrierBlock(state)) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return DomainBarrierMixin.jjkbrp$isWeakOverwriteableFloorBlock(world, pos, state);
    }

    @Unique
    private static void jjkbrp$seedIncompleteWrapShell(LevelAccessor world, LivingEntity caster, CompoundTag nbt) {
        String floorBlock = nbt.getString("domain_floor");
        if (floorBlock == null || floorBlock.isEmpty() || "minecraft:air".equals(floorBlock)) {
            return;
        }
        String wallBlock = nbt.getString("domain_outside");
        if (wallBlock == null || wallBlock.isEmpty() || "minecraft:air".equals(wallBlock)) {
            wallBlock = "jujutsucraft:in_barrier";
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
        double radius = Math.max(2.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
        int floorY = DomainBarrierMixin.jjkbrp$getFloorY(nbt, caster);
        int radialSteps = Math.max(2, (int)Math.ceil(radius));
        int angularSteps = Math.max(24, (int)Math.ceil(radius * 10.0));
        for (int rIndex = 0; rIndex <= radialSteps; ++rIndex) {
            double r = radius * (double)rIndex / (double)radialSteps;
            for (int angleIndex = 0; angleIndex < angularSteps; ++angleIndex) {
                double angle = (Math.PI * 2.0 * (double)angleIndex) / (double)angularSteps;
                int sx = (int)Math.round(center.x + Math.cos(angle) * r);
                int sz = (int)Math.round(center.z + Math.sin(angle) * r);
                BlockPos seedPos = new BlockPos(sx, floorY, sz);
                DomainBarrierMixin.jjkbrp$applyIncompleteCrawlerAt(world, seedPos, floorBlock, wallBlock, center, radius, floorY);
            }
        }
    }

    @Unique
    private static void jjkbrp$applyIncompleteCrawlerAt(LevelAccessor world, BlockPos floorPos, String floorBlock, String wallBlock,
                                                         Vec3 center, double radius, int floorY) {
        if (floorPos == null || floorBlock == null || floorBlock.isEmpty()) {
            return;
        }
        double dx = (double)floorPos.getX() - center.x;
        double dz = (double)floorPos.getZ() - center.z;
        double horizontalDistSq = dx * dx + dz * dz;
        double maxDistSq = (radius + 1.0) * (radius + 1.0);
        if (horizontalDistSq > maxDistSq) {
            return;
        }

        BlockState currentState = world.getBlockState(floorPos);
        if (DomainBarrierMixin.jjkbrp$canReplaceForIncompleteFloor(world, floorPos, currentState)) {
            DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, floorPos, floorBlock);
            return;
        }

        int maxClimbY = floorY + JJKBRP$INCOMPLETE_CLIMB_MAX_HEIGHT;
        for (int up = 1; up <= JJKBRP$INCOMPLETE_CLIMB_MAX_HEIGHT; ++up) {
            BlockPos climbPos = floorPos.above(up);
            if (climbPos.getY() > maxClimbY) {
                break;
            }
            BlockState climbState = world.getBlockState(climbPos);
            if (!DomainBarrierMixin.jjkbrp$canReplaceForIncompleteFloor(world, climbPos, climbState)) {
                continue;
            }
            BlockPos supportPos = climbPos.below();
            boolean hasSupport = DomainBarrierMixin.jjkbrp$isBarrierBlock(world.getBlockState(supportPos))
                    || DomainBarrierMixin.jjkbrp$isIncompleteSupportBlock(world, supportPos)
                    || DomainBarrierMixin.jjkbrp$hasAdjacentWallSupport(world, climbPos);
            if (!hasSupport) {
                continue;
            }
            DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, climbPos, wallBlock);
            if (!world.getBlockState(climbPos.above()).isAir()) {
                DomainBarrierMixin.jjkbrp$extendCeilingHorizontally(world, climbPos, wallBlock, center, radius);
            }
            return;
        }

        if (!world.getBlockState(floorPos.above()).isAir()) {
            DomainBarrierMixin.jjkbrp$extendCeilingHorizontally(world, floorPos, wallBlock, center, radius);
        }
    }

    @Unique
    private static int jjkbrp$getFloorY(CompoundTag nbt, LivingEntity caster) {
        if (nbt.contains("y_pos_doma")) {
            return (int) Math.floor(nbt.getDouble("y_pos_doma")) - 1;
        }
        if (nbt.contains("jjkbrp_caster_y_at_cast")) {
            return (int) Math.floor(nbt.getDouble("jjkbrp_caster_y_at_cast")) - 1;
        }
        return (int) Math.floor(caster.getY()) - 1;
    }

    /**
     * Performs is incomplete domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is incomplete domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isIncompleteDomainState(LivingEntity entity) {
        return DomainAddonUtils.isIncompleteDomainState(entity);
    }


    // ===== PLACEMENT REDIRECT =====
    /**
     * Redirects the original barrier block placement call so addon-specific incomplete placement and cleanup rules can run.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
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
        String outside = nbt.getString("domain_outside");
        boolean isOutsideWall = !outside.isEmpty() && blockName != null && blockName.equals(outside);
        String floor = nbt.getString("domain_floor");
        String inside = nbt.getString("domain_inside");
        boolean floorMatchesInside = !floor.isEmpty() && floor.equals(inside);
        boolean isFloorBlock = !floor.isEmpty() && blockName != null && blockName.equals(floor);
        boolean incompleteForm = DomainBarrierMixin.jjkbrp$isIncompleteDomainState(livingCaster);
        if (incompleteForm) {
            int maxFloorY = DomainBarrierMixin.jjkbrp$getFloorY(nbt, livingCaster);
            if (isOutsideWall) {
                if (pos.getY() > maxFloorY + 1) return;
                DomainBarrierMixin.jjkbrp$placeIncompleteWrappedWall(world, pos, blockName, livingCaster);
                return;
            }
            if (isFloorBlock) {
                if (pos.getY() > maxFloorY) return;
                BlockPos expectedFloorPos = JJKBRP$expectedFloorPos.get();
                if (expectedFloorPos != null && expectedFloorPos.equals((Object)pos)) {
                    JJKBRP$expectedFloorPlaced.set(Boolean.TRUE);
                }
                DomainBarrierMixin.jjkbrp$placeIncompleteWrappedFloor(world, pos, blockName);
                return;
            }
            return;
        }
        BlockPos expectedFloorPos = JJKBRP$expectedFloorPos.get();
        if (isFloorBlock && expectedFloorPos != null && expectedFloorPos.equals((Object)pos)) {
            JJKBRP$expectedFloorPlaced.set(Boolean.TRUE);
        }
        if (isFloorBlock && !floorMatchesInside && nbt.contains("jjkbrp_caster_y_at_cast")) {
            double casterY = nbt.getDouble("jjkbrp_caster_y_at_cast");
            int correctFloorY = (int)Math.floor(casterY) - 1;
            if (pos.getY() > correctFloorY) {
                return;
            }
        }
        nbt.putBoolean("jjkbrp_barrier_blocks_placed", true);
        if (!isOutsideWall) {
            DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
            if (DomainBarrierMixin.jjkbrp$shouldCleanEdgeShellPlacement(pos, blockName, livingCaster)) {
                DomainBarrierMixin.jjkbrp$cleanAdjacentWallBlocks(world, pos, livingCaster);
            }
            return;
        }
        DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
        DomainBarrierMixin.jjkbrp$cleanAdjacentWallBlocks(world, pos, livingCaster);
    }

    /**
     * Performs place tracked barrier block for this mixin.
     * @param world world access used by the current mixin callback.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$placeTrackedBarrierBlock(LevelAccessor world, BlockPos pos, String blockName) {
        BlockEntity placedBe;
        BlockEntity be;
        if (world.isClientSide() || blockName == null || blockName.isEmpty()) {
            return;
        }
        BlockState currentState = world.getBlockState(pos);
        String oldBlock = DomainBarrierMixin.jjkbrp$isBarrierBlock(currentState) ? ((be = world.getBlockEntity(pos)) != null ? be.getPersistentData().getString("old_block") : "") : String.valueOf(currentState).replace("}", "").replace("Block{", "");
        DomainBarrierMixin.placeBlockSafe(world, pos, blockName);
        BlockState placedState = world.getBlockState(pos);
        if (DomainBarrierMixin.jjkbrp$isBarrierBlock(placedState) && (placedBe = world.getBlockEntity(pos)) != null) {
            placedBe.getPersistentData().putString("old_block", oldBlock);
        }
    }


    // ===== EDGE SHELL CLEANUP =====
    /**
     * Cleans nearby shell blocks that would leave messy wall edges after incomplete barrier shaping.
     * @param world world access used by the current mixin callback.
     * @param wallPos wall pos used by this method.
     * @param caster entity involved in the current mixin operation.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$cleanAdjacentWallBlocks(LevelAccessor world, BlockPos wallPos, LivingEntity caster) {
        if (world.isClientSide() || caster == null) {
            return;
        }
        CompoundTag nbt = caster.getPersistentData();
        double domainId = nbt.getDouble("select") > 0 ? nbt.getDouble("select") : nbt.getDouble("skill_domain");
        if (Math.round(domainId) == 29) {
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
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
        double floorY = nbt.contains("x_pos_doma") ? nbt.getDouble("y_pos_doma") - 1.0 : Math.floor(caster.getY()) - 1.0;
        double maxEdgeDistanceSq = (actualRadius + 1.25) * (actualRadius + 1.25);
        double innerDistanceSq = actualRadius * actualRadius;
        double seamBandMinSq = Math.max(0.0, (actualRadius - 3.0) * (actualRadius - 3.0));
        for (int dxOffset = -1; dxOffset <= 1; ++dxOffset) {
            for (int dyOffset = -1; dyOffset <= 1; ++dyOffset) {
                for (int dzOffset = -1; dzOffset <= 1; ++dzOffset) {
                    double dz;
                    double dy;
                    double dx;
                    double distanceSq;
                    BlockPos targetPos;
                    BlockState targetState;
                    if (dxOffset == 0 && dyOffset == 0 && dzOffset == 0 || (targetState = world.getBlockState(targetPos = wallPos.offset(dxOffset, dyOffset, dzOffset))).isAir() || DomainBarrierMixin.jjkbrp$isBarrierBlock(targetState) || (distanceSq = (dx = (double)targetPos.getX() - center.x) * dx + (dy = (double)targetPos.getY() - center.y) * dy + (dz = (double)targetPos.getZ() - center.z) * dz) > maxEdgeDistanceSq || (double)targetPos.getY() > floorY && distanceSq < seamBandMinSq) continue;
                    String replacement = "jujutsucraft:in_barrier";
                    if ((double)targetPos.getY() <= floorY && (double)targetPos.getY() >= floorY - 4.0 && distanceSq < innerDistanceSq && floorBlock != null && !floorBlock.isEmpty() && !"minecraft:air".equals(floorBlock)) {
                        replacement = floorBlock;
                    }
                    DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, targetPos, replacement);
                }
            }
        }
    }

    /**
     * Performs should clean edge shell placement for this mixin.
     * @param pos pos used by this method.
     * @param blockName block name used by this method.
     * @param caster entity involved in the current mixin operation.
     * @return whether should clean edge shell placement is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$shouldCleanEdgeShellPlacement(BlockPos pos, String blockName, LivingEntity caster) {
        boolean shellBlock;
        if (caster == null || blockName == null || blockName.isEmpty()) {
            return false;
        }
        CompoundTag nbt = caster.getPersistentData();
        String outside = nbt.getString("domain_outside");
        String inside = nbt.getString("domain_inside");
        String floor = nbt.getString("domain_floor");
        boolean bl = shellBlock = blockName.equals(outside) || blockName.equals(inside) || "jujutsucraft:in_barrier".equals(blockName) || blockName.equals(floor);
        if (!shellBlock) {
            return false;
        }
        double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)caster.level(), nbt);
        if (actualRadius <= 0.0) {
            return false;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
        double dx = (double)pos.getX() - center.x;
        double dy = (double)pos.getY() - center.y;
        double dz = (double)pos.getZ() - center.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double shellBandMinSq = Math.max(0.0, (actualRadius - 3.0) * (actualRadius - 3.0));
        double shellBandMaxSq = (actualRadius + 1.25) * (actualRadius + 1.25);
        return distanceSq >= shellBandMinSq && distanceSq <= shellBandMaxSq;
    }

    /**
     * Performs is open domain state for this mixin.
     * @param caster entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return whether is open domain state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isOpenDomainState(LivingEntity caster, CompoundTag nbt) {
        return DomainAddonUtils.isOpenDomainState(caster);
    }

    /**
     * Performs is base startup open state for this mixin.
     * @param nbt persistent data container used by this helper.
     * @return whether is base startup open state is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isBaseStartupOpenState(CompoundTag nbt) {
        int resolvedId;
        if (nbt == null) {
            return false;
        }
        if (!nbt.contains("cnt2") || nbt.getDouble("cnt2") <= 0.0) {
            return false;
        }
        if (nbt.getDouble("cnt7") <= 0.0 && !nbt.contains("x_pos_doma")) {
            return false;
        }
        double domainId = nbt.getDouble("select");
        if (domainId == 0.0) {
            domainId = nbt.getDouble("skill_domain");
        }
        if (domainId == 0.0) {
            domainId = nbt.getDouble("jjkbrp_domain_id_runtime");
        }
        return (resolvedId = (int)Math.round(domainId)) == 1 || resolvedId == 18;
    }
}
