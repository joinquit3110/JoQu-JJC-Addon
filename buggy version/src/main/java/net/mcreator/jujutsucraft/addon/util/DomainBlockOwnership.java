package net.mcreator.jujutsucraft.addon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative block ownership/allowlist checks for domain cleanup. */
public final class DomainBlockOwnership {
    private DomainBlockOwnership() {}
    public static boolean isBarrier(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.getBlockState(pos).is(BlockTags.create(new net.minecraft.resources.ResourceLocation("jujutsucraft:barrier")));
    }
    public static boolean hasOldBlockSnapshot(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getPersistentData().contains("old_block") && !be.getPersistentData().getString("old_block").isBlank();
    }
    public static boolean isKnownDomainBlock(ServerLevel level, BlockPos pos) {
        return isBarrier(level, pos) || hasOldBlockSnapshot(level, pos) || isActualDomainPaletteBlock(level, pos);
    }

    public static boolean isActualDomainPaletteBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state == null || state.isAir()) return false;
        String blockName = String.valueOf(state).replace("}", "").replace("Block{", "");
        if (blockName.startsWith("jujutsucraft:")) {
            String path = blockName.substring("jujutsucraft:".length());
            return path.contains("barrier")
                    || path.contains("domain")
                    || path.contains("floor")
                    || path.equals("in_barrier")
                    || path.equals("inside_barrier")
                    || path.equals("outside_barrier");
        }
        return false;
    }
}
