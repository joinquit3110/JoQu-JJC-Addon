package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.entity.EntityItemEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModItems;
import net.mcreator.jujutsucraft.procedures.EntityItemRightClickedOnEntityProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives active player Yuta a placeholder Authentic Mutual Love sword from domain decorations without using vanilla copy randomization.
 */
@Mixin(value = {EntityItemRightClickedOnEntityProcedure.class}, remap = false)
public class EntityItemRightClickedOnEntityProcedureMixin {
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$disableVanillaDomainDecorationCopy(LevelAccessor world, Entity entity, Entity sourceentity, CallbackInfo ci) {
        if (!(sourceentity instanceof ServerPlayer player) || !YutaCopyStore.isActiveYuta(player)) {
            return;
        }
        if (isDomainSwordDecoration(entity)) {
            YutaCopyStore.cleanupVanillaPlayerCopy(player);
            ItemStack sword = new ItemStack(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get());
            YutaCopyStore.markDomainSword(sword, player);
            if (player.getMainHandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, sword);
                player.getInventory().setChanged();
            } else {
                ItemHandlerHelper.giveItemToPlayer(player, sword);
            }
            if (!entity.level().isClientSide) {
                entity.discard();
            }
            ci.cancel();
        }
    }

    private static boolean isDomainSwordDecoration(Entity entity) {
        if (!(entity instanceof EntityItemEntity itemEntity)) {
            return false;
        }
        if (!itemEntity.getEntityData().get(EntityItemEntity.DATA_domain_decoration)) {
            return false;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return living.getItemBySlot(EquipmentSlot.HEAD).is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get());
    }
}
