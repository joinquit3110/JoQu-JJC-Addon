package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainRadiusUtils;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={DomainExpansionBattleProcedure.class}, remap=false)
public abstract class DomainBattleRadiusMixin {
    @Redirect(method={"execute"}, at=@At(value="FIELD", target="Lnet/mcreator/jujutsucraft/network/JujutsucraftModVariables$MapVariables;DomainExpansionRadius:D", opcode=180), remap=false)
    private static double jjkbrp$readEffectiveBattleRadius(JujutsucraftModVariables.MapVariables mapVariables, LevelAccessor world, double x, double y, double z, Entity entity) {
        // Build must remain the OG pipeline, but the radius input itself is the per-cast
        // Domain Mastery snapshot. This preserves OG center, block/color mapping and marker
        // placement while letting positive/negative radius modify scale the physical shell/floor.
        if (world == null || world.isClientSide() || entity == null) {
            return Math.max(1.0, mapVariables.DomainExpansionRadius);
        }
        return DomainRadiusUtils.resolveActualRadius(world, entity.getPersistentData());
    }

    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/EntityType;m_262496_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;"), remap=false)
    private static Entity jjkbrp$stampRadiusOnDomainMarkerSpawn(EntityType<?> entityType, ServerLevel serverLevel, BlockPos pos, MobSpawnType spawnType, LevelAccessor world, double x, double y, double z, Entity caster) {
        Entity spawned = entityType.spawn(serverLevel, pos, spawnType);
        if (spawned != null && caster != null && world != null && !world.isClientSide()) {
            CompoundTag casterNbt = caster.getPersistentData();
            CompoundTag markerNbt = spawned.getPersistentData();
            if (casterNbt.contains("jjkbrp_base_domain_radius")) {
                markerNbt.putDouble("jjkbrp_base_domain_radius", casterNbt.getDouble("jjkbrp_base_domain_radius"));
            }
            if (casterNbt.contains("jjkbrp_radius_multiplier")) {
                markerNbt.putDouble("jjkbrp_radius_multiplier", casterNbt.getDouble("jjkbrp_radius_multiplier"));
            }
            markerNbt.putDouble("jjkbrp_actual_domain_radius", DomainRadiusUtils.resolveActualRadius(world, casterNbt));
        }
        return spawned;
    }

    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure;placeBlockSafe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Ljava/lang/String;)V"), remap=false)
    private static void jjkbrp$placeDomainBlockWithFloorSnapshot(LevelAccessor targetWorld, BlockPos pos, String blockName, LevelAccessor world, double x, double y, double z, Entity caster) {
        if (targetWorld == null || pos == null) {
            return;
        }
        String oldBlock = DomainBattleRadiusMixin.jjkbrp$resolveOldBlock(targetWorld, pos);
        DomainBattleRadiusMixin.jjkbrp$placeBlockSafe(targetWorld, pos, blockName);
        if (DomainBattleRadiusMixin.jjkbrp$isDomainCleanupBlock(targetWorld.getBlockState(pos), blockName)) {
            BlockEntity newBlockEntity = targetWorld.getBlockEntity(pos);
            if (newBlockEntity != null && !oldBlock.isBlank()) {
                newBlockEntity.getPersistentData().putString("old_block", oldBlock);
                newBlockEntity.setChanged();
            }
        }
    }

    private static String jjkbrp$resolveOldBlock(LevelAccessor world, BlockPos pos) {
        BlockState currentState = world.getBlockState(pos);
        if (DomainBattleRadiusMixin.jjkbrp$isDomainCleanupBlock(currentState, null)) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                String nestedOldBlock = blockEntity.getPersistentData().getString("old_block");
                if (!nestedOldBlock.isBlank()) {
                    return nestedOldBlock;
                }
            }
        }
        return DomainBattleRadiusMixin.jjkbrp$blockStateName(currentState);
    }

    private static String jjkbrp$blockStateName(BlockState state) {
        if (state == null) {
            return "minecraft:air";
        }
        return String.valueOf(state).replace("}", "").replace("Block{", "");
    }

    private static boolean jjkbrp$isDomainCleanupBlock(BlockState state, String requestedBlockName) {
        if (requestedBlockName != null && DomainBattleRadiusMixin.jjkbrp$isDomainCleanupBlockName(requestedBlockName)) {
            return true;
        }
        if (state == null) {
            return false;
        }
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return blockId != null && DomainBattleRadiusMixin.jjkbrp$isDomainCleanupBlockName(blockId.toString());
    }

    private static boolean jjkbrp$isDomainCleanupBlockName(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return false;
        }
        String cleanName = blockName.contains("[") ? blockName.substring(0, blockName.indexOf("[")) : blockName;
        if (!cleanName.contains(":")) {
            cleanName = "minecraft:" + cleanName;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(cleanName);
        } catch (Exception ignored) {
            return false;
        }
        if (!"jujutsucraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("domain_")
                || path.equals("block_red")
                || path.equals("block_universe")
                || path.equals("block_gravel")
                || path.equals("coffinofthe_ironmountain_1")
                || path.equals("coffinofthe_ironmountain_2")
                || path.equals("in_barrier")
                || path.equals("jujutsu_barrier");
    }

    private static void jjkbrp$placeBlockSafe(LevelAccessor world, BlockPos pos, String blockName) {
        if (world.isClientSide() || blockName == null || blockName.isBlank()) {
            return;
        }
        if (blockName.contains("[") && world instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(pos.getX(), pos.getY(), pos.getZ()), Vec2.ZERO, serverLevel, 4, "", Component.literal(""), serverLevel.getServer(), null).withSuppressedOutput(), "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + blockName + " replace");
            return;
        }
        String cleanName = blockName.contains("[") ? blockName.substring(0, blockName.indexOf("[")) : blockName;
        ResourceLocation id;
        try {
            id = new ResourceLocation(cleanName.contains(":") ? cleanName : "minecraft:" + cleanName);
        } catch (Exception ignored) {
            return;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block != null) {
            world.setBlock(pos, block.defaultBlockState(), 3);
        } else if (world instanceof Level level) {
            level.removeBlock(pos, false);
        }
    }
}
