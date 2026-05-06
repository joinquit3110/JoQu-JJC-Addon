package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.AIDomainExpansionEntityProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={AIDomainExpansionEntityProcedure.class}, remap=false)
public abstract class DomainCleanupRadiusMixin {
    @Redirect(method={"execute"}, at=@At(value="FIELD", target="Lnet/mcreator/jujutsucraft/network/JujutsucraftModVariables$MapVariables;DomainExpansionRadius:D", opcode=180), remap=false)
    private static double jjkbrp$readEffectiveCleanupRadius(JujutsucraftModVariables.MapVariables mapVariables, LevelAccessor world, double x, double y, double z, Entity entity) {
        // Cleanup must mirror the same per-cast footprint used by OG barrier construction.
        // BreakDomain copies the caster snapshot onto this cleanup marker before runtime keys are
        // cleared, so cancel/expiration scans the full modified shell/floor instead of the OG size.
        if (world == null || world.isClientSide() || entity == null) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        return DomainRadiusUtils.resolveActualRadius(world, entity.getPersistentData());
    }
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/block/state/BlockState;m_204336_(Lnet/minecraft/tags/TagKey;)Z"), remap=false)
    private static boolean jjkbrp$treatStoredDomainFloorAsCleanupTarget(BlockState state, TagKey tag, LevelAccessor world, double x, double y, double z, Entity entity) {
        if (state.is(tag)) {
            return true;
        }
        if (world == null || world.isClientSide() || tag == null || tag.location() == null || !"jujutsucraft".equals(tag.location().getNamespace()) || !"barrier".equals(tag.location().getPath())) {
            return false;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && !blockEntity.getPersistentData().getString("old_block").isBlank();
    }
}
