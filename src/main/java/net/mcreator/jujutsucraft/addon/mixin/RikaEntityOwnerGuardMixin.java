package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.entity.Rika2Entity;
import net.mcreator.jujutsucraft.entity.RikaEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps player-owned Rika friendly after world reload/rejoin.
 *
 * <p>The base Rika AI clears OWNER_UUID/friend_num when the owner cannot be resolved on a tick.
 * During reload/rejoin that can briefly happen before the player entity is available, which turns
 * Rika into an unowned monster and lets the normal player-target goal pick the owner. This guard
 * restores ownership from the player's persisted RIKA_UUID and removes owner/friend targets.</p>
 */
@Mixin(value = {RikaEntity.class, Rika2Entity.class}, remap = false)
public class RikaEntityOwnerGuardMixin {
    @Unique
    private static final Logger JJKBRP$LOGGER = LogUtils.getLogger();

    @Inject(method = {"m_6075_"}, at = {@At(value = "HEAD")}, remap = false)
    private void jjkbrp$restoreOwnerBeforeBaseRikaTick(CallbackInfo ci) {
        Entity rika = (Entity)(Object)this;
        RikaEntityOwnerGuardMixin.jjkbrp$restoreOwnerAndClearFriendlyTarget(rika, "HEAD");
    }

    @Inject(method = {"m_6075_"}, at = {@At(value = "TAIL")}, remap = false)
    private void jjkbrp$restoreOwnerAfterBaseRikaTick(CallbackInfo ci) {
        Entity rika = (Entity)(Object)this;
        RikaEntityOwnerGuardMixin.jjkbrp$restoreOwnerAndClearFriendlyTarget(rika, "TAIL");
    }

    @Unique
    private static void jjkbrp$restoreOwnerAndClearFriendlyTarget(Entity rika, String phase) {
        if (!(rika.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag rikaData = rika.getPersistentData();
        if (rikaData.getDouble("despawn_flag") > 0.0D) {
            return;
        }
        ServerPlayer owner = RikaEntityOwnerGuardMixin.jjkbrp$findPersistedOwner(level, rika);
        if (owner == null) {
            return;
        }
        CompoundTag ownerData = owner.getPersistentData();
        boolean changed = false;

        String expectedOwnerUuid = owner.getUUID().toString();
        if (!expectedOwnerUuid.equals(rikaData.getString("OWNER_UUID"))) {
            rikaData.putString("OWNER_UUID", expectedOwnerUuid);
            changed = true;
        }
        if (!rika.getUUID().toString().equals(ownerData.getString("RIKA_UUID"))) {
            ownerData.putString("RIKA_UUID", rika.getUUID().toString());
            changed = true;
        }

        double friend = ownerData.getDouble("friend_num");
        if (friend == 0.0D) {
            friend = rikaData.getDouble("friend_num");
            if (friend != 0.0D) {
                ownerData.putDouble("friend_num", friend);
                ownerData.putDouble("friend_num_worker", friend);
                changed = true;
            }
        }
        if (friend != 0.0D) {
            if (Math.abs(rikaData.getDouble("friend_num") - friend) > 0.001D) {
                rikaData.putDouble("friend_num", friend);
                changed = true;
            }
            if (Math.abs(rikaData.getDouble("friend_num_worker") - friend) > 0.001D) {
                rikaData.putDouble("friend_num_worker", friend);
                changed = true;
            }
        }

        boolean clearedTarget = RikaEntityOwnerGuardMixin.jjkbrp$clearFriendlyTarget(rika, owner, friend);
        if ((changed || clearedTarget) && rika.tickCount % 20 == 0) {
            JJKBRP$LOGGER.debug("[RikaOwnerGuard] phase={} rika={} owner={} restored={} clearedTarget={} friend={} ownerUuid={}", phase, rika.getUUID(), owner.getGameProfile().getName(), changed, clearedTarget, friend, expectedOwnerUuid);
        }
    }

    @Unique
    private static ServerPlayer jjkbrp$findPersistedOwner(ServerLevel level, Entity rika) {
        String rikaUuid = rika.getUUID().toString();
        String ownerUuid = rika.getPersistentData().getString("OWNER_UUID");
        double rikaFriend = rika.getPersistentData().getDouble("friend_num");
        ServerPlayer friendMatch = null;
        for (ServerPlayer player : level.players()) {
            CompoundTag playerData = player.getPersistentData();
            if (rikaUuid.equals(playerData.getString("RIKA_UUID"))) {
                return player;
            }
            if (!ownerUuid.isBlank() && ownerUuid.equals(player.getUUID().toString())) {
                return player;
            }
            if (friendMatch == null && rikaFriend != 0.0D && Math.abs(playerData.getDouble("friend_num") - rikaFriend) <= 0.001D && player.distanceToSqr(rika) <= 64.0D * 64.0D) {
                friendMatch = player;
            }
        }
        return friendMatch;
    }

    @Unique
    private static boolean jjkbrp$clearFriendlyTarget(Entity rika, ServerPlayer owner, double friend) {
        if (!(rika instanceof Mob mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }
        boolean sameOwner = target.getUUID().equals(owner.getUUID());
        boolean sameFriend = friend != 0.0D && Math.abs(target.getPersistentData().getDouble("friend_num") - friend) <= 0.001D;
        boolean targetOwnsRika = rika.getUUID().toString().equals(target.getPersistentData().getString("RIKA_UUID"));
        if (!sameOwner && !sameFriend && !targetOwnsRika) {
            return false;
        }
        rika.getPersistentData().putString("TARGET_UUID", "");
        rika.getPersistentData().putDouble("cnt_target", 0.0D);
        mob.setTarget(null);
        return true;
    }
}
