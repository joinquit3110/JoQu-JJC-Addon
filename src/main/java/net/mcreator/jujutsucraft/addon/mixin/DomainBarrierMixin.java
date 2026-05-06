package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.block.Blocks;
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
import org.slf4j.Logger;

/**
 * Barrier placement mixin for `DomainExpansionBattleProcedure.execute()`.
 *
 * <p>Incomplete domains now follow the base mod's Megumi-style path directly:
 * startup marks the cast as incomplete, `GetDomainBlockProcedure` leaves the
 * shell palette empty, and the shared spherical builder performs all growth.
 * This mixin only tracks ownership / restore metadata for placed barrier blocks
 * while preserving the base mod's closed-domain floor placement behavior.</p>
 */
@Mixin(value={DomainExpansionBattleProcedure.class}, remap=false)
public abstract class DomainBarrierMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();
    @Unique
    private static final ThreadLocal<Entity> JJKBRP$currentCaster = new ThreadLocal<>();
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
    }

    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$clearCaster(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        JJKBRP$currentCaster.remove();
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
        blockName = DomainBarrierMixin.jjkbrp$resolveRadiusAwareSpecialBlock(world, pos, blockName, livingCaster);
        String outside = nbt.getString("domain_outside");
        boolean isOutsideWall = !outside.isEmpty() && blockName != null && blockName.equals(outside);
        nbt.putBoolean("jjkbrp_barrier_blocks_placed", true);
        DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, pos, blockName);
        if (isOutsideWall || DomainBarrierMixin.jjkbrp$shouldCleanEdgeShellPlacement(pos, blockName, livingCaster)) {
            DomainBarrierMixin.jjkbrp$cleanAdjacentWallBlocks(world, pos, livingCaster);
        }
    }

    @Unique
    private static String jjkbrp$resolveRadiusAwareSpecialBlock(LevelAccessor world, BlockPos pos, String originalBlock, LivingEntity caster) {
        if (world.isClientSide() || caster == null || originalBlock == null || originalBlock.isEmpty()) {
            return originalBlock;
        }
        CompoundTag nbt = caster.getPersistentData();
        if (DomainBarrierMixin.jjkbrp$isOpenDomainState(caster, nbt) || DomainBarrierMixin.jjkbrp$isIncompleteDomainState(caster)) {
            return originalBlock;
        }
        int domainId = DomainBarrierMixin.jjkbrp$resolveDomainId(nbt);
        if (!DomainBarrierMixin.jjkbrp$isSpecialRadiusAwareDomain(domainId)) {
            return originalBlock;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(caster);
        double actualRadius = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
        double baseRadius = Math.max(1.0, nbt.contains("jjkbrp_base_domain_radius") ? nbt.getDouble("jjkbrp_base_domain_radius") : 16.0);
        double scale = Math.max(0.25, actualRadius / baseRadius);
        int period = Math.max(1, (int)Math.round(5.0 * scale));
        int width = Math.max(1, (int)Math.round(scale));
        double floorY = nbt.contains("x_pos_doma") ? nbt.getDouble("y_pos_doma") - 1.0 : Math.floor(caster.getY()) - 1.0;
        double dx = pos.getX() - center.x;
        double dy = pos.getY() - center.y;
        double dz = pos.getZ() - center.z;
        double radial = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double normalized = actualRadius <= 0.0 ? 0.0 : radial / actualRadius;
        // Keep the original lower floor band authoritative.  Earlier radius-aware correction rewrote
        // the floor for special domains after the base builder had already selected its block, which
        // made scaled closed-domain floors diverge from OG and produced rough lower-shell patches.
        // Only non-floor shell/stripe accents are still adjusted here.
        boolean nearFloor = pos.getY() <= floorY && pos.getY() >= floorY - 4.0;
        boolean shellInteriorBand = radial >= Math.max(0.0, actualRadius - Math.max(3.0, 2.0 * scale)) && radial < actualRadius + 0.75;
        if (nearFloor) {
            return originalBlock;
        }
        return switch (domainId) {
            case 1 -> shellInteriorBand && DomainBarrierMixin.jjkbrp$isScaledStripe(pos.getX(), center.x, period, width) ? "jujutsucraft:domain_bone" : originalBlock;
            case 15 -> DomainBarrierMixin.jjkbrp$isScaledStripe(pos.getX(), center.x, period, width) || DomainBarrierMixin.jjkbrp$isScaledStripe(pos.getY(), center.y, period, width) || DomainBarrierMixin.jjkbrp$isScaledStripe(pos.getZ(), center.z, period, width) ? "jujutsucraft:domain_bone" : originalBlock;
            case 21 -> {
                double fenceLine = center.x - actualRadius * 0.5;
                if (Math.abs(pos.getX() - fenceLine) <= width) {
                    yield "jujutsucraft:domain_fence";
                }
                yield originalBlock;
            }
            case 29 -> shellInteriorBand ? "jujutsucraft:domain_white" : originalBlock;
            default -> originalBlock;
        };
    }

    @Unique
    private static int jjkbrp$resolveDomainId(CompoundTag nbt) {
        double runtime = nbt.getDouble("jjkbrp_domain_id_runtime");
        if (runtime > 0.0) {
            return (int)Math.round(runtime);
        }
        double select = nbt.getDouble("select");
        if (select > 0.0) {
            return (int)Math.round(select);
        }
        return (int)Math.round(nbt.getDouble("skill_domain"));
    }

    @Unique
    private static boolean jjkbrp$isSpecialRadiusAwareDomain(int domainId) {
        return domainId == 1 || domainId == 8 || domainId == 15 || domainId == 21 || domainId == 27 || domainId == 29;
    }

    @Unique
    private static boolean jjkbrp$isScaledStripe(double coord, double centerCoord, int period, int width) {
        int distance = (int)Math.round(Math.abs(coord - Math.round(centerCoord)));
        return distance % Math.max(1, period) < Math.max(1, width);
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
        if (oldBlock == null || oldBlock.isEmpty()) {
            oldBlock = Blocks.AIR.defaultBlockState().toString().replace("}", "").replace("Block{", "");
        }
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
                    if (targetPos.getY() <= floorY + 1.0 && targetPos.getY() >= floorY - 4.0) {
                        continue;
                    }
                    if (targetPos.getY() > floorY && distanceSq < seamBandMinSq) {
                        continue;
                    }
                    // Adjacent cleanup is only a seam/wall repair pass.  Do not synthesize floor-band blocks here:
                    // OG floor placement is handled by the main spherical lower-band logic, and neighborhood
                    // floor spreading creates irregular raised/lowered patches on scaled domains.
                    DomainBarrierMixin.jjkbrp$placeTrackedBarrierBlock(world, targetPos, "jujutsucraft:in_barrier");
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
        boolean shellBlock = blockName.equals(outside) || blockName.equals(inside) || "jujutsucraft:in_barrier".equals(blockName);
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
