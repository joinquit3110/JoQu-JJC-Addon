package net.mcreator.jujutsucraft.addon.yuta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.mcreator.jujutsucraft.addon.ModItems;
import net.mcreator.jujutsucraft.addon.limb.LimbType;
import net.mcreator.jujutsucraft.entity.Rika2Entity;
import net.mcreator.jujutsucraft.entity.RikaEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModItems;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainActiveProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "jjkblueredpurple")
public final class YutaCopyStore {
    public static final String KEY_STORE = "jjkaddon_yuta_rika_copy_store";
    public static final String KEY_SELECTED = "selected_yuta_copied_technique";
    public static final String KEY_SELECTED_RECORD = "selected_yuta_copied_record_uuid";
    public static final String KEY_SUREHIT_RECORD = "jjkaddon_yuta_surehit_record_uuid";
    public static final String KEY_SUREHIT_SKILL = "jjkaddon_yuta_surehit_skill";
    public static final String KEY_PURE_LOVE_CLEARED = "jjkaddon_yuta_pure_love_store_cleared";
    public static final String KEY_HAD_CURSE_LIFTED = "jjkaddon_yuta_had_curse_lifted";
    public static final String ITEM_MARKER = "jjkaddon_yuta_limb_copy_item";
    public static final String ITEM_SOURCE_UUID = "jjkaddon_source_uuid";
    public static final String ITEM_SOURCE_NAME = "jjkaddon_source_name";
    public static final String ITEM_EVENT_ID = "jjkaddon_sever_event_id";
    public static final String ITEM_PART = "jjkaddon_body_part";
    public static final String ITEM_CT1 = "jjkaddon_source_PlayerCurseTechnique";
    public static final String ITEM_CT2 = "jjkaddon_source_PlayerCurseTechnique2";
    public static final String ITEM_TEMPORARY = "jjkaddon_temporary";
    public static final String ITEM_USES = "jjkaddon_uses_remaining";
    private static final int MOB_COPY_USES = 5;
    private static final int MIN_COPY_COOLDOWN_TICKS = 200;
    private static final double MIN_COPY_CE_COST = 120.0D;
    private static final double COPY_CE_COST_MULTIPLIER = 1.75D;
    private static final String KEY_COPY_COOLDOWN_UNTIL_PREFIX = "jjkaddon_yuta_copy_cooldown_until_";
    private static final Random RANDOM = new Random();

    private YutaCopyStore() {
    }

    public static void spawnLimbCopyItem(LivingEntity owner, String partName) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        spawnLimbCopyItem(owner, partName, vars.PlayerCurseTechnique, vars.PlayerCurseTechnique2, true, true);
    }

    public static void spawnLimbCopyItem(LivingEntity owner, String partName, double technique1, double technique2, boolean temporary, boolean rememberEvent) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isFakeYutaSource(owner) && (isRealYutaPlayer(owner) || isRikaEntity(owner))) {
            return;
        }
        if (!isTechniqueCopyable(technique1) && !isTechniqueCopyable(technique2)) {
            return;
        }
        if (isHeadPart(partName)) {
            return;
        }
        ItemStack stack = createLimbCopyItem(owner, partName, technique1, technique2, temporary);
        ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.6D, owner.getZ(), stack);
        drop.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.2D, 0.25D, (level.random.nextDouble() - 0.5D) * 0.2D);
        level.addFreshEntity(drop);
        if (rememberEvent && owner instanceof ServerPlayer player) {
            rememberSeverEvent(player, stack.getOrCreateTag().getString(ITEM_EVENT_ID), partName);
        }
    }

    public static ItemStack createLimbCopyItem(LivingEntity owner, String partName, double technique1, double technique2, boolean temporary) {
        ItemStack stack = new ItemStack(ModItems.YUTA_FINGER.get());
        CompoundTag tag = stack.getOrCreateTag();
        String eventId = UUID.randomUUID().toString();
        tag.putBoolean(ITEM_MARKER, true);
        tag.putString(ITEM_SOURCE_UUID, owner.getUUID().toString());
        tag.putString(ITEM_SOURCE_NAME, owner.getName().getString());
        tag.putString(ITEM_EVENT_ID, eventId);
        tag.putString(ITEM_PART, partName);
        tag.putDouble(ITEM_CT1, technique1);
        tag.putDouble(ITEM_CT2, technique2);
        tag.putBoolean(ITEM_TEMPORARY, temporary);
        if (owner.getPersistentData().getBoolean(YutaFakePlayerEntity.KEY_FAKE)) {
            tag.putBoolean("jjkaddon_fake_source", true);
        }
        tag.putString("jjkaddon_limb_item_uuid", UUID.randomUUID().toString());
        String partDisplay = prettyPart(partName);
        stack.setHoverName(Component.literal("Cursed Finger").withStyle(ChatFormatting.LIGHT_PURPLE));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Rika copy catalyst: " + techniqueName(technique1) + (isTechniqueCopyable(technique2) ? " / " + techniqueName(technique2) : "")).withStyle(ChatFormatting.GRAY))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Bound source: " + owner.getName().getString()).withStyle(ChatFormatting.DARK_PURPLE))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Memory anchor: " + partDisplay).withStyle(ChatFormatting.DARK_GRAY))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    public static void spawnMobKillCopyItem(ServerPlayer yuta, LivingEntity source) {
        if (yuta == null || source == null || !(source.level() instanceof ServerLevel level) || source instanceof Player || !isYuta(yuta)) return;
        CompoundTag data = source.getPersistentData();
        double ct1 = data.getDouble("PlayerCurseTechnique");
        double ct2 = data.getDouble("PlayerCurseTechnique2");
        if (!isTechniqueCopyable(ct1) && !isTechniqueCopyable(ct2)) return;
        ItemStack stack = createLimbCopyItem(source, "cursed_technique", ct1, ct2, false);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("jjkaddon_mob_kill_source", true);
        tag.putInt(ITEM_USES, MOB_COPY_USES);
        stack.setHoverName(Component.literal(source.getName().getString() + "'s Cursed Technique Copy").withStyle(ChatFormatting.LIGHT_PURPLE));
        ItemEntity drop = new ItemEntity(level, source.getX(), source.getY() + 0.6D, source.getZ(), stack);
        drop.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.2D, 0.25D, (level.random.nextDouble() - 0.5D) * 0.2D);
        level.addFreshEntity(drop);
    }

    private static void rememberSeverEvent(ServerPlayer player, String eventId, String partName) {
        CompoundTag data = player.getPersistentData();
        ListTag events = data.getList("jjkaddon_yuta_source_sever_events", 10);
        CompoundTag event = new CompoundTag();
        event.putString("eventId", eventId);
        event.putString("part", partName);
        event.putBoolean("active", true);
        event.putBoolean("dead", false);
        events.add(event);
        data.put("jjkaddon_yuta_source_sever_events", events);
    }

    public static void onLimbRegrown(LivingEntity entity, String partName) {
        if (entity == null || entity.level().isClientSide || entity.getServer() == null) {
            return;
        }
        UUID source = entity.getUUID();
        String normalized = partName == null ? "" : partName;
        if (entity instanceof ServerPlayer sourcePlayer) {
            markSourceSeverEvents(sourcePlayer, normalized, false, false);
        }
        MinecraftServer server = entity.getServer();
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            boolean changed = false;
            ListTag store = getStore(owner);
            for (int i = 0; i < store.size(); ++i) {
                CompoundTag rec = store.getCompound(i);
                if (!rec.getBoolean("valid") || !rec.getBoolean("temporary")) continue;
                if (!source.toString().equals(rec.getString("sourceUuid"))) continue;
                if (!normalized.equals(rec.getString("bodyPart"))) continue;
                rec.putBoolean("valid", false);
                rec.putString("invalidation", "source_regrown");
                changed = true;
                if (rec.getString("recordUuid").equals(owner.getPersistentData().getString(KEY_SELECTED_RECORD))) {
                    clearSelection(owner);
                }
            }
            boolean removedFinger = removeMatchingFingerFromInventory(owner, source, normalized);
            if (changed) {
                owner.getPersistentData().put(KEY_STORE, store);
                owner.displayClientMessage(Component.literal("§d[Rika] Temporary copied technique revoked: source regenerated " + prettyPart(normalized) + "."), false);
            } else if (removedFinger) {
                owner.displayClientMessage(Component.literal("§d[Rika] A stale finger faded as the source regenerated " + prettyPart(normalized) + "."), false);
            }
        }
        removeMatchingDroppedFingers(server, source, normalized);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof ServerPlayer)) {
            Entity killer = event.getSource().getEntity();
            if (killer instanceof ServerPlayer yuta) {
                spawnMobKillCopyItem(yuta, victim);
            }
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer source)) {
            return;
        }
        markSourceSeverEvents(source, null, true, true);
        for (ServerPlayer owner : source.server.getPlayerList().getPlayers()) {
            ListTag store = getStore(owner);
            boolean changed = false;
            for (int i = 0; i < store.size(); ++i) {
                CompoundTag rec = store.getCompound(i);
                if (rec.getBoolean("valid") && rec.getBoolean("temporary") && source.getUUID().toString().equals(rec.getString("sourceUuid"))) {
                    rec.putBoolean("temporary", false);
                    rec.putBoolean("permanent", true);
                    rec.putString("invalidation", "");
                    changed = true;
                }
            }
            if (changed) {
                owner.getPersistentData().put(KEY_STORE, store);
                owner.displayClientMessage(Component.literal("§d[Rika] Temporary copied techniques from " + source.getName().getString() + " became permanent."), false);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!isLimbCopyItem(stack)) {
            return;
        }
        Entity target = event.getTarget();
        if (!isYuta(serverPlayer) || !isValidCopyStoreRika(serverPlayer, target)) {
            serverPlayer.displayClientMessage(Component.literal("§c[Rika] Only Yuta with his valid Rika can consume this."), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        if (consumeLimbItem(serverPlayer, target, stack)) {
            clearFedItem(serverPlayer, event.getHand(), stack);
            serverPlayer.swing(event.getHand(), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            event.setCancellationResult(InteractionResult.FAIL);
        }
        event.setCanceled(true);
    }

    private static void clearFedItem(ServerPlayer player, InteractionHand hand, ItemStack usedStack) {
        if (player == null || hand == null) {
            return;
        }
        if (usedStack != null && !usedStack.isEmpty()) {
            usedStack.setCount(0);
        }
        player.setItemInHand(hand, ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean consumeLimbItem(ServerPlayer owner, Entity rika, ItemStack stack) {
        if (!isLimbCopyItem(stack)) {
            owner.displayClientMessage(Component.literal("§c[Rika] Invalid limb item."), true);
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        UUID sourceUuid;
        try {
            sourceUuid = UUID.fromString(tag.getString(ITEM_SOURCE_UUID));
        } catch (Exception ex) {
            owner.displayClientMessage(Component.literal("§c[Rika] Invalid limb memory."), true);
            return false;
        }
        double ct1 = tag.getDouble(ITEM_CT1);
        double ct2 = tag.getDouble(ITEM_CT2);
        String eventId = tag.getString(ITEM_EVENT_ID);
        String part = tag.getString(ITEM_PART);
        String sourceName = tag.getString(ITEM_SOURCE_NAME);
        SeverEventState severState = getSourceSeverEventState(owner.server.getPlayerList().getPlayer(sourceUuid), eventId, part);
        if (severState == SeverEventState.UNKNOWN && tag.getBoolean("jjkaddon_fake_source")) {
            severState = isSourceEntityCurrentlyAlive(owner.server, sourceUuid) ? SeverEventState.ACTIVE : SeverEventState.DEAD;
        } else if (severState == SeverEventState.UNKNOWN && tag.getBoolean("jjkaddon_mob_kill_source")) {
            severState = tag.getBoolean(ITEM_TEMPORARY) ? SeverEventState.ACTIVE : SeverEventState.DEAD;
        }
        if (severState == SeverEventState.REGROWN) {
            owner.displayClientMessage(Component.literal("§d[Rika] This finger has gone stale."), true);
            stack.setCount(0);
            return true;
        }
        boolean permanent = severState == SeverEventState.DEAD || !tag.getBoolean(ITEM_TEMPORARY);
        ArrayList<Candidate> pool = new ArrayList<>();
        addCandidates(owner, pool, ct1, "PlayerCurseTechnique");
        addCandidates(owner, pool, ct2, "PlayerCurseTechnique2");
        if (pool.isEmpty()) {
            owner.displayClientMessage(Component.literal("§d[Rika] Rika burps: No new flavors left!"), false);
            return true;
        }
        Candidate chosen = pool.get(0);
        CompoundTag rec = new CompoundTag();
        String recordUuid = UUID.randomUUID().toString();
        rec.putString("recordUuid", recordUuid);
        rec.putString("sourceUuid", sourceUuid.toString());
        rec.putString("sourceName", sourceName == null || sourceName.isBlank() ? "Unknown" : sourceName);
        rec.putString("sourceItemUuid", stack.getOrCreateTag().getString("jjkaddon_limb_item_uuid").isBlank() ? UUID.randomUUID().toString() : stack.getOrCreateTag().getString("jjkaddon_limb_item_uuid"));
        rec.putDouble("techniqueId", chosen.techniqueId);
        rec.putInt("moveSelectId", chosen.moveSelectId);
        rec.putDouble("runtimeSkill", runtimeSkill(chosen.techniqueId, chosen.moveSelectId));
        rec.putString("moveName", chosen.moveName);
        rec.putString("sourceSlot", chosen.slot);
        rec.putBoolean("temporary", !permanent);
        rec.putBoolean("permanent", permanent);
        rec.putString("severEventId", eventId);
        rec.putString("bodyPart", part);
        rec.putBoolean("valid", true);
        rec.putString("invalidation", "");
        rec.putLong("learnedTimestamp", owner.level().getGameTime());
        rec.putDouble("skill", chosen.runtimeSkill());
        rec.putDouble("effect", chosen.techniqueId);
        rec.putDouble("COOLDOWN_TICKS", defaultCooldown(chosen.techniqueId, chosen.moveSelectId));
        rec.putDouble("cost", defaultCost(chosen.techniqueId, chosen.moveSelectId));
        rec.putString("displayName", displayName(chosen.techniqueId, chosen.moveName, sourceName));
        if (tag.contains(ITEM_USES)) {
            rec.putInt("usesRemaining", tag.getInt(ITEM_USES));
        }
        ListTag store = getStore(owner);
        store.add(rec);
        owner.getPersistentData().put(KEY_STORE, store);
        owner.getPersistentData().putString(KEY_SELECTED_RECORD, recordUuid);
        owner.getPersistentData().putDouble(KEY_SELECTED, rec.getDouble("skill"));
        owner.displayClientMessage(Component.literal("§d[Rika] Learned " + techniqueName(chosen.techniqueId) + ": " + chosen.moveName + " from " + sourceName + "."), false);
        return true;
    }

    private static void addCandidates(ServerPlayer owner, List<Candidate> pool, double techniqueId, String slot) {
        if (!isTechniqueCopyable(techniqueId)) {
            return;
        }
        for (MoveInfo move : copyableMoves(techniqueId)) {
            if (!hasDuplicate(owner, techniqueId, move.selectId())) {
                pool.add(new Candidate(techniqueId, move.selectId(), move.name(), slot));
                return;
            }
        }
    }

    private static void markSourceSeverEvents(ServerPlayer source, String partName, boolean active, boolean dead) {
        ListTag events = source.getPersistentData().getList("jjkaddon_yuta_source_sever_events", 10);
        boolean changed = false;
        String normalized = partName == null ? null : partName;
        for (int i = 0; i < events.size(); ++i) {
            CompoundTag event = events.getCompound(i);
            if (normalized != null && !normalized.equals(event.getString("part"))) continue;
            event.putBoolean("active", active);
            event.putBoolean("dead", dead);
            changed = true;
        }
        if (changed) {
            source.getPersistentData().put("jjkaddon_yuta_source_sever_events", events);
        }
    }

    private static SeverEventState getSourceSeverEventState(ServerPlayer source, String eventId, String partName) {
        if (source == null || eventId == null || eventId.isBlank()) return SeverEventState.UNKNOWN;
        ListTag events = source.getPersistentData().getList("jjkaddon_yuta_source_sever_events", 10);
        for (int i = 0; i < events.size(); ++i) {
            CompoundTag event = events.getCompound(i);
            if (!eventId.equals(event.getString("eventId"))) continue;
            if (partName != null && !partName.isBlank() && !partName.equals(event.getString("part"))) return SeverEventState.UNKNOWN;
            if (event.getBoolean("dead")) return SeverEventState.DEAD;
            return event.getBoolean("active") ? SeverEventState.ACTIVE : SeverEventState.REGROWN;
        }
        return SeverEventState.UNKNOWN;
    }

    private static boolean isTechniqueCopyable(double techniqueId) {
        int id = (int)Math.round(techniqueId);
        return id > 0 && id != 5 && id != 20;
    }

    private static boolean hasDuplicate(ServerPlayer owner, double techniqueId, int moveSelectId) {
        ListTag store = getStore(owner);
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            if (!rec.getBoolean("valid")) continue;
            int recMove = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
            if (Math.abs(rec.getDouble("techniqueId") - techniqueId) < 0.001D && recMove == moveSelectId) return true;
        }
        return false;
    }

    public static boolean isYuta(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        return vars.PlayerCurseTechnique == 5.0D || vars.PlayerCurseTechnique2 == 5.0D;
    }

    public static boolean hasValidRikaOrDomain(ServerPlayer player) {
        if (resolveValidRika(player).isPresent()) return true;
        return player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) && !player.getPersistentData().getBoolean("Failed") && Math.abs(player.getPersistentData().getDouble("skill_domain") - 5.0D) < 0.001D;
    }

    public static Optional<Entity> resolveValidRika(ServerPlayer owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        String uuid = owner.getPersistentData().getString("RIKA_UUID");
        if (uuid != null && !uuid.isBlank()) {
            try {
                UUID expectedUuid = UUID.fromString(uuid);
                Entity target = level.getEntity(expectedUuid);
                if (target != null && target.getUUID().equals(expectedUuid) && isValidCopyStoreRika(owner, target)) {
                    return Optional.of(target);
                }
            } catch (Exception ignored) {
            }
            return Optional.empty();
        }
        return findNearbyValidRika(owner, level);
    }

    public static boolean isValidCopyStoreRika(Player owner, Entity target) {
        if (owner == null || target == null || !isCopyStoreRikaType(target)) return false;
        CompoundTag ownerData = owner.getPersistentData();
        CompoundTag targetData = target.getPersistentData();
        String ownerUuid = targetData.getString("OWNER_UUID");
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            return ownerUuid.equals(owner.getUUID().toString());
        }
        String rikaUuid = ownerData.getString("RIKA_UUID");
        if (rikaUuid != null && !rikaUuid.isBlank()) {
            if (!rikaUuid.equals(target.getUUID().toString())) return false;
            restoreRikaOwnerData(owner, target);
            return true;
        }
        return owner instanceof ServerPlayer serverOwner && isNearbyActiveRikaFallback(serverOwner, target);
    }

    private static boolean isCopyStoreRikaType(Entity target) {
        return target instanceof RikaEntity || target instanceof Rika2Entity;
    }

    public static boolean isRikaEntity(Entity target) {
        return isCopyStoreRikaType(target);
    }

    public static boolean isFakeYutaSource(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(YutaFakePlayerEntity.KEY_FAKE);
    }

    public static boolean isRealYutaPlayer(Entity entity) {
        return entity instanceof ServerPlayer player && !isFakeYutaSource(entity) && isYuta(player);
    }

    private static void restoreRikaOwnerData(Player owner, Entity target) {
        CompoundTag ownerData = owner.getPersistentData();
        CompoundTag targetData = target.getPersistentData();
        targetData.putString("OWNER_UUID", owner.getUUID().toString());
        double friend = ownerData.getDouble("friend_num");
        if (friend != 0.0D) {
            targetData.putDouble("friend_num", friend);
            targetData.putDouble("friend_num_worker", friend);
        } else {
            double targetFriend = targetData.getDouble("friend_num");
            if (targetFriend != 0.0D) {
                ownerData.putDouble("friend_num", targetFriend);
                ownerData.putDouble("friend_num_worker", targetFriend);
            }
        }
    }

    private static Optional<Entity> findNearbyValidRika(ServerPlayer owner, ServerLevel level) {
        AABB box = owner.getBoundingBox().inflate(32.0D);
        for (Entity candidate : level.getEntities(owner, box, YutaCopyStore::isCopyStoreRikaType)) {
            if (isValidCopyStoreRika(owner, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isNearbyActiveRikaFallback(ServerPlayer owner, Entity target) {
        String rikaUuid = owner.getPersistentData().getString("RIKA_UUID");
        if (rikaUuid != null && !rikaUuid.isBlank()) return false;
        if (owner.distanceToSqr(target) > 8.0D * 8.0D) return false;
        CompoundTag data = target.getPersistentData();
        String ownerUuid = data.getString("OWNER_UUID");
        if (ownerUuid != null && !ownerUuid.isBlank() && !ownerUuid.equals(owner.getUUID().toString())) return false;
        double friend = owner.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D || Math.abs(friend - data.getDouble("friend_num")) > 0.001D) return false;
        return target.isAlive() && (data.getDouble("despawn_flag") == 0.0D || data.getDouble("despawn_flag") == 2.0D);
    }

    public static boolean isLimbCopyItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getOrCreateTag().getBoolean(ITEM_MARKER);
    }

    private static boolean isMatchingFinger(ItemStack stack, UUID sourceUuid, String part) {
        if (!isLimbCopyItem(stack) || sourceUuid == null) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ITEM_SOURCE_UUID) || !tag.contains(ITEM_PART)) {
            return false;
        }
        return sourceUuid.toString().equals(tag.getString(ITEM_SOURCE_UUID)) && (part == null ? "" : part).equals(tag.getString(ITEM_PART));
    }

    private static boolean removeMatchingFingerFromInventory(ServerPlayer player, UUID sourceUuid, String part) {
        boolean removed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); ++slot) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isMatchingFinger(stack, sourceUuid, part)) {
                continue;
            }
            stack.setCount(0);
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            removed = true;
        }
        if (removed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return removed;
    }

    private static void removeMatchingDroppedFingers(MinecraftServer server, UUID sourceUuid, String part) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity && isMatchingFinger(itemEntity.getItem(), sourceUuid, part)) {
                    itemEntity.getItem().setCount(0);
                    itemEntity.discard();
                }
            }
        }
    }

    private static boolean isSourceEntityCurrentlyAlive(MinecraftServer server, UUID sourceUuid) {
        if (server == null || sourceUuid == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(sourceUuid);
        if (player != null) {
            return player.isAlive() && !player.isDeadOrDying();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(sourceUuid);
            if (entity instanceof LivingEntity living) {
                return living.isAlive() && !living.isDeadOrDying();
            }
        }
        return false;
    }

    public static ListTag getStore(ServerPlayer player) {
        return player.getPersistentData().getList(KEY_STORE, 10).copy();
    }

    public static List<CompoundTag> validRecords(ServerPlayer player) {
        ArrayList<CompoundTag> out = new ArrayList<>();
        ListTag store = getStore(player);
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            if (rec.getBoolean("valid") && rec.getDouble("skill") > 0.0D) out.add(rec.copy());
        }
        return out;
    }

    public static Optional<CompoundTag> selectedRecord(ServerPlayer player) {
        String selectedUuid = player.getPersistentData().getString(KEY_SELECTED_RECORD);
        List<CompoundTag> valid = validRecords(player);
        for (CompoundTag rec : valid) {
            if (rec.getString("recordUuid").equals(selectedUuid)) return Optional.of(rec);
        }
        return valid.isEmpty() ? Optional.empty() : Optional.of(valid.get(0));
    }

    public static boolean selectRecord(ServerPlayer player, String recordUuid) {
        for (CompoundTag rec : validRecords(player)) {
            if (!rec.getString("recordUuid").equals(recordUuid)) continue;
            player.getPersistentData().putString(KEY_SELECTED_RECORD, recordUuid);
            player.getPersistentData().putDouble(KEY_SELECTED, rec.getDouble("skill"));
            player.displayClientMessage(Component.literal("§d[Rika] Selected " + rec.getString("displayName") + "."), true);
            return true;
        }
        return false;
    }

    public static void clearSelection(ServerPlayer player) {
        player.getPersistentData().remove(KEY_SELECTED_RECORD);
        player.getPersistentData().putDouble(KEY_SELECTED, 0.0D);
    }

    public static Optional<CompoundTag> sureHitRecord(ServerPlayer player) {
        String recordUuid = player.getPersistentData().getString(KEY_SUREHIT_RECORD);
        if (recordUuid == null || recordUuid.isBlank()) return Optional.empty();
        for (CompoundTag rec : validRecords(player)) {
            if (recordUuid.equals(rec.getString("recordUuid")) && isValidSureHitRecord(rec)) return Optional.of(rec);
        }
        clearSureHit(player);
        return Optional.empty();
    }

    public static boolean setSureHitToSelected(ServerPlayer player) {
        Optional<CompoundTag> opt = selectedRecord(player);
        return opt.isPresent() && setSureHitRecord(player, opt.get().getString("recordUuid"));
    }

    public static boolean setSureHitRecord(ServerPlayer player, String recordUuid) {
        if (player == null || recordUuid == null || recordUuid.isBlank()) return false;
        for (CompoundTag rec : validRecords(player)) {
            if (!recordUuid.equals(rec.getString("recordUuid")) || !isValidSureHitRecord(rec)) continue;
            player.getPersistentData().putString(KEY_SUREHIT_RECORD, rec.getString("recordUuid"));
            player.getPersistentData().putDouble(KEY_SUREHIT_SKILL, rec.getDouble("skill"));
            player.getPersistentData().putDouble("jjkaddon_yuta_surehit_runtime_skill", rec.getDouble("skill"));
            return true;
        }
        return false;
    }

    public static void clearSureHit(ServerPlayer player) {
        player.getPersistentData().remove(KEY_SUREHIT_RECORD);
        player.getPersistentData().remove(KEY_SUREHIT_SKILL);
        player.getPersistentData().remove("jjkaddon_yuta_surehit_runtime_skill");
    }

    public static boolean isValidSureHitRecord(CompoundTag rec) {
        if (rec == null || !rec.getBoolean("valid")) return false;
        double skill = rec.getDouble("skill");
        double techniqueId = rec.getDouble("techniqueId");
        int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        return skill >= 100.0D && !(skill >= 500.0D && skill < 600.0D) && skill % 100.0D <= 20.0D && !isDomainMove(techniqueId, moveSelectId, rec.getString("moveName"));
    }

    public static int resetCopies(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        ListTag store = getStore(player);
        int count = store.size();
        CompoundTag data = player.getPersistentData();
        for (int i = 0; i < store.size(); ++i) {
            data.remove(cooldownKey(store.getCompound(i)));
        }
        data.put(KEY_STORE, new ListTag());
        clearSelection(player);
        clearSureHit(player);
        return count;
    }

    public static boolean activateSelected(ServerPlayer player) {
        if (!isYuta(player) || !hasValidRikaOrDomain(player)) {
            player.displayClientMessage(Component.literal("§c[Rika] Rika or Authentic Mutual Love is required."), true);
            return false;
        }
        if (player.getPersistentData().getDouble("skill") != 0.0D) {
            player.displayClientMessage(Component.literal("§c[Rika] Finish your current technique first."), true);
            return false;
        }
        Optional<CompoundTag> opt = selectedRecord(player);
        if (opt.isEmpty()) {
            player.displayClientMessage(Component.literal("§e[Rika] No copied technique selected."), true);
            return false;
        }
        CompoundTag rec = opt.get();
        if (rec.contains("usesRemaining") && rec.getInt("usesRemaining") <= 0) {
            invalidateRecord(player, rec.getString("recordUuid"), "uses_exhausted");
            player.displayClientMessage(Component.literal("§e[Rika] This copied technique has no uses remaining."), true);
            return false;
        }
        int cooldownTicks = normalizedCooldown(rec);
        double cost = normalizedCost(rec);
        long now = player.level().getGameTime();
        String cooldownKey = cooldownKey(rec);
        long cooldownUntil = player.getPersistentData().getLong(cooldownKey);
        if (cooldownUntil > now) {
            long remaining = cooldownUntil - now;
            player.displayClientMessage(Component.literal("§e[Rika] This copied skill is still on cooldown: " + ((remaining + 19L) / 20L) + "s."), true);
            return false;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (!player.isCreative() && vars.PlayerCursePower < cost) {
            player.displayClientMessage(Component.literal("§c[Rika] Not enough cursed energy. Need " + (int)Math.ceil(cost) + "."), true);
            return false;
        }
        if (!player.isCreative()) {
            player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
                cap.PlayerCursePower = Math.max(0.0D, cap.PlayerCursePower - cost);
                cap.syncPlayerVariables((Entity)player);
            });
        }
        player.getPersistentData().putLong(cooldownKey, now + (long)cooldownTicks);
        player.getPersistentData().putDouble("skill", rec.getDouble("skill"));
        player.getPersistentData().putDouble("effect", rec.getDouble("effect"));
        player.getPersistentData().putDouble("COOLDOWN_TICKS", cooldownTicks);
        player.getPersistentData().putBoolean("PRESS_Z", true);
        player.addEffect(new MobEffectInstance((MobEffect)JujutsucraftModMobEffects.CURSED_TECHNIQUE.get(), Integer.MAX_VALUE, 0, false, false));
        if (rec.contains("usesRemaining")) {
            decrementRecordUse(player, rec.getString("recordUuid"));
        }
        player.displayClientMessage(Component.literal("§d[Rika] Activated " + rec.getString("displayName") + "."), true);
        return true;
    }

    public static String cooldownKey(CompoundTag rec) {
        if (rec != null) {
            String recordUuid = rec.getString("recordUuid");
            if (recordUuid != null && !recordUuid.isBlank()) {
                return KEY_COPY_COOLDOWN_UNTIL_PREFIX + recordUuid;
            }
            int techniqueId = (int)Math.round(rec.getDouble("techniqueId"));
            int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
            return KEY_COPY_COOLDOWN_UNTIL_PREFIX + techniqueId + "_" + moveSelectId;
        }
        return KEY_COPY_COOLDOWN_UNTIL_PREFIX + "unknown";
    }

    public static int cooldownRemainingTicks(ServerPlayer player, CompoundTag rec) {
        if (player == null || rec == null) {
            return 0;
        }
        long now = player.level().getGameTime();
        long until = player.getPersistentData().getLong(cooldownKey(rec));
        return until > now ? (int)Math.min(Integer.MAX_VALUE, until - now) : 0;
    }

    public static boolean isOwnedRika(Player owner, Entity target) {
        return isValidCopyStoreRika(owner, target);
    }

    private static void decrementRecordUse(ServerPlayer player, String recordUuid) {
        ListTag store = getStore(player);
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            if (!recordUuid.equals(rec.getString("recordUuid"))) continue;
            int remaining = Math.max(0, rec.getInt("usesRemaining") - 1);
            rec.putInt("usesRemaining", remaining);
            if (remaining <= 0) {
                rec.putBoolean("valid", false);
                rec.putString("invalidation", "uses_exhausted");
                clearSelection(player);
            }
            player.getPersistentData().put(KEY_STORE, store);
            return;
        }
    }

    private static void invalidateRecord(ServerPlayer player, String recordUuid, String reason) {
        ListTag store = getStore(player);
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            if (!recordUuid.equals(rec.getString("recordUuid"))) continue;
            rec.putBoolean("valid", false);
            rec.putString("invalidation", reason);
            player.getPersistentData().put(KEY_STORE, store);
            clearSelection(player);
            return;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        if (isYuta(player)) {
            cleanupVanillaPlayerCopy(player);
        }
        boolean hasLifted = hasAdvancement(player, "jujutsucraft:skill_curseis_lifted");
        CompoundTag data = player.getPersistentData();
        boolean hadLifted = data.getBoolean(KEY_HAD_CURSE_LIFTED);
        if (hasLifted && !hadLifted && !data.getBoolean(KEY_PURE_LOVE_CLEARED)) {
            data.put(KEY_STORE, new ListTag());
            clearSelection(player);
            clearSureHit(player);
            data.putBoolean(KEY_PURE_LOVE_CLEARED, true);
            player.displayClientMessage(Component.literal("§d[Rika] Pure Love lifted the curse. Rika's old copy store was released."), false);
        }
        data.putBoolean(KEY_HAD_CURSE_LIFTED, hasLifted);
    }

    public static void bridgeAuthenticMutualLove(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (Math.abs(entity.getPersistentData().getDouble("skill_domain") - 5.0D) > 0.001D) return;
        Optional<CompoundTag> opt = sureHitRecord(player).or(() -> selectedRecord(player).filter(YutaCopyStore::isValidSureHitRecord));
        if (opt.isEmpty()) return;
        CompoundTag rec = opt.get();
        double skill = rec.getDouble("skill");
        if (!(skill % 100.0D <= 20.0D && skill >= 100.0D) || (skill >= 500.0D && skill < 600.0D)) return;
        double oldDomain = entity.getPersistentData().getDouble("skill_domain");
        double oldSkill = entity.getPersistentData().getDouble("skill");
        double oldCooldown = entity.getPersistentData().getDouble("COOLDOWN_TICKS");
        entity.getPersistentData().putDouble("skill", skill);
        entity.getPersistentData().putDouble("COOLDOWN_TICKS", rec.getDouble("COOLDOWN_TICKS"));
        entity.getPersistentData().putDouble("skill_domain", Math.floor(skill / 100.0D));
        DomainActiveProcedure.execute(world, x, y, z, entity);
        entity.getPersistentData().putDouble("skill_domain", oldDomain);
        entity.getPersistentData().putDouble("skill", oldSkill);
        entity.getPersistentData().putDouble("COOLDOWN_TICKS", oldCooldown);
    }

    public static double runtimeSkill(double techniqueId) {
        return runtimeSkill(techniqueId, 5);
    }

    public static double runtimeSkill(double techniqueId, int moveSelectId) {
        return Math.round(techniqueId) * 100.0D + moveSelectId;
    }

    public static double defaultCooldown(double techniqueId) {
        return defaultCooldown(techniqueId, 5);
    }

    public static double defaultCooldown(double techniqueId, int moveSelectId) {
        int id = (int)Math.round(techniqueId);
        double base;
        if (id == 2 || id == 4 || id == 15) {
            base = 360.0D;
        } else if (id == 1 || id == 6 || id == 10) {
            base = 260.0D;
        } else {
            base = 240.0D;
        }
        if (moveSelectId == 15) {
            base = Math.max(base, 500.0D);
        }
        return Math.max(base, MIN_COPY_COOLDOWN_TICKS);
    }

    public static double defaultCost(double techniqueId) {
        return defaultCost(techniqueId, 5);
    }

    public static double defaultCost(double techniqueId, int moveSelectId) {
        double base = Math.max(defaultCooldown(techniqueId, moveSelectId) * COPY_CE_COST_MULTIPLIER / 2.0D, MIN_COPY_CE_COST);
        if (moveSelectId == 15) {
            base = Math.max(base, 450.0D);
        }
        return base;
    }

    private static int normalizedCooldown(CompoundTag rec) {
        double techniqueId = rec.getDouble("techniqueId");
        int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        return (int)Math.round(Math.max(rec.contains("COOLDOWN_TICKS") ? rec.getDouble("COOLDOWN_TICKS") : 0.0D, defaultCooldown(techniqueId, moveSelectId)));
    }

    private static double normalizedCost(CompoundTag rec) {
        double techniqueId = rec.getDouble("techniqueId");
        int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        return Math.max(rec.contains("cost") ? rec.getDouble("cost") : 0.0D, defaultCost(techniqueId, moveSelectId));
    }

    public static String displayName(double techniqueId, String sourceName) {
        return displayName(techniqueId, moveName(techniqueId, 5), sourceName);
    }

    public static String displayName(double techniqueId, String moveName, String sourceName) {
        String name = techniqueName(techniqueId) + ": " + (moveName == null || moveName.isBlank() ? "Move" : moveName);
        return name + (sourceName == null || sourceName.isBlank() ? "" : " (" + sourceName + ")");
    }

    public static String techniqueName(double techniqueId) {
        int id = (int)Math.round(techniqueId);
        return switch (id) {
            case 1 -> "Shrine";
            case 2 -> "Limitless";
            case 3 -> "Cursed Speech";
            case 4 -> "Disaster Flames";
            case 6 -> "Ten Shadows";
            case 7 -> "Blood Manipulation";
            case 8 -> "Projection Sorcery";
            case 9 -> "Idle Transfiguration";
            case 10 -> "Boogie Woogie";
            case 11 -> "Black Bird Manipulation";
            case 12 -> "Cursed Spirit Manipulation";
            case 13 -> "Sky Manipulation";
            case 14 -> "Comedian";
            case 15 -> "Construction";
            case 16 -> "Ice Formation";
            case 17 -> "Star Rage";
            case 18 -> "Cursed Spirit Manipulation";
            case 19 -> "Mythical Beast Amber";
            default -> "CT " + id;
        };
    }

    private static boolean isHeadPart(String partName) {
        if (partName == null || partName.isBlank()) return false;
        LimbType type = LimbType.fromName(partName.toLowerCase());
        return type == LimbType.HEAD || partName.toLowerCase().contains("head");
    }

    private static String prettyPart(String partName) {
        if (partName == null || partName.isBlank()) return "Limb";
        String[] words = partName.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.length() == 0 ? "Limb" : builder.toString();
    }

    public static List<MoveInfo> copyableMoves(double techniqueId) {
        int id = (int)Math.round(techniqueId);
        ArrayList<MoveInfo> moves = new ArrayList<>();
        int[] whitelist = switch (id) {
            case 1 -> new int[]{6, 5, 7};
            case 2 -> new int[]{6, 7, 15, 8};
            case 3 -> new int[]{5, 6, 7, 8, 9};
            case 4 -> new int[]{5, 6, 7, 8, 15};
            case 6 -> new int[]{5, 6, 7, 9, 15};
            case 7 -> new int[]{5, 6, 7, 8};
            case 8 -> new int[]{5, 6, 7};
            case 9 -> new int[]{5, 6, 7, 8};
            case 10 -> new int[]{5};
            case 11 -> new int[]{5, 6, 7};
            case 12, 18 -> new int[]{5, 6, 7, 8};
            case 13 -> new int[]{5, 6, 7};
            case 14 -> new int[]{5, 6, 7};
            case 15 -> new int[]{5, 6, 7, 8};
            case 16 -> new int[]{5, 6, 7, 8};
            case 17 -> new int[]{5, 6, 7};
            case 19 -> new int[]{5, 6, 7, 8};
            default -> new int[]{5, 6, 7};
        };
        for (int selectId : whitelist) {
            String name = moveName(techniqueId, selectId);
            if (isDomainMove(techniqueId, selectId, name)) continue;
            moves.add(new MoveInfo(selectId, name));
            if (id != 1 && id != 2 && moves.size() >= 5) {
                break;
            }
        }
        return moves;
    }

    private static boolean isDomainMove(double techniqueId, int selectId, String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return selectId >= 20 || lower.contains("domain") || lower.contains("authentic mutual love") || lower.contains("malevolent shrine") || lower.contains("infinite void");
    }

    public static String moveName(double techniqueId, int moveSelectId) {
        return switch ((int)Math.round(techniqueId)) {
            case 1 -> switch (moveSelectId) { case 5 -> "Dismantle"; case 6 -> "Cleave"; case 7 -> "Fuga"; case 15 -> "Maximum Output"; default -> "Shrine Move " + moveSelectId; };
            case 2 -> switch (moveSelectId) { case 5 -> "Infinity"; case 6 -> "Blue"; case 7 -> "Red"; case 8 -> "Blue Strike"; case 15 -> "Hollow Purple"; default -> "Limitless Move " + moveSelectId; };
            case 3 -> switch (moveSelectId) { case 5 -> "Cursed Speech"; default -> "Cursed Speech Move " + moveSelectId; };
            case 6 -> switch (moveSelectId) { case 5 -> "Divine Dogs"; case 6 -> "Nue"; case 7 -> "Toad"; case 8 -> "Great Serpent"; case 9 -> "Max Elephant"; case 10 -> "Rabbit Escape"; case 11 -> "Round Deer"; case 12 -> "Piercing Ox"; case 13 -> "Tiger Funeral"; case 15 -> "Mahoraga"; default -> "Ten Shadows Move " + moveSelectId; };
            case 7 -> switch (moveSelectId) { case 5 -> "Piercing Blood"; case 6 -> "Convergence"; case 7 -> "Blood Edge"; case 8 -> "Flowing Red Scale"; default -> "Blood Manipulation Move " + moveSelectId; };
            case 10 -> switch (moveSelectId) { case 5 -> "Boogie Woogie"; default -> "Boogie Woogie Move " + moveSelectId; };
            default -> techniqueName(techniqueId) + " Move " + moveSelectId;
        };
    }

    public record MoveInfo(int selectId, String name) {
    }

    public static boolean isVanillaHardcodedCopySelect(double selectId) {
        int id = (int)Math.round(selectId);
        return id == 5 || id == 6 || id == 7;
    }

    public static boolean isVanillaHardcodedCopyRuntime(double skill) {
        int id = (int)Math.round(skill);
        return id == 505 || id == 506 || id == 507;
    }

    public static boolean sanitizeVanillaHardcodedCopyState(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        CompoundTag data = player.getPersistentData();
        boolean changed = false;
        if (isVanillaHardcodedCopySelect(vars.PlayerSelectCurseTechnique)) {
            vars.PlayerSelectCurseTechnique = 0.0D;
            vars.PlayerSelectCurseTechniqueName = Component.translatable("jujutsu.technique.attack1").getString();
            vars.PlayerSelectCurseTechniqueCost = 0.0D;
            vars.PlayerSelectCurseTechniqueCostOrgin = 0.0D;
            vars.PassiveTechnique = false;
            vars.PhysicalAttack = true;
            vars.OverlayCost = "";
            vars.OverlayCursePower = "";
            changed = true;
        }
        if (isVanillaHardcodedCopyRuntime(data.getDouble("skill")) && Math.abs(data.getDouble(KEY_SELECTED) - data.getDouble("skill")) > 0.001D) {
            data.putDouble("skill", 0.0D);
            data.putDouble("effect", 0.0D);
            data.putDouble("COOLDOWN_TICKS", 0.0D);
            data.putBoolean("PRESS_Z", false);
            changed = true;
        }
        if (changed) {
            vars.syncPlayerVariables(player);
        }
        return changed;
    }

    public static void cleanupVanillaPlayerCopy(ServerPlayer player) {
        sanitizeVanillaHardcodedCopyState(player);
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(JujutsucraftModItems.COPIED_CURSED_TECHNIQUE.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else if (stack.is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get())) {
                clearVanillaCopiedNbt(stack);
            }
        }
        clearVanillaCopiedNbt(player.getMainHandItem());
        clearVanillaCopiedNbt(player.getOffhandItem());
        for (ItemStack armor : player.getArmorSlots()) {
            clearVanillaCopiedNbt(armor);
        }
        revokeAdvancement(player, "jujutsucraft:skill_copy_cursed_speech");
        revokeAdvancement(player, "jujutsucraft:skill_copy_dhruv_lakdawalla");
        revokeAdvancement(player, "jujutsucraft:skill_copy_takako_uro");
    }

    private static void clearVanillaCopiedNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove("skill");
        tag.remove("effect");
        tag.remove("COOLDOWN_TICKS");
        tag.remove("SHIKIGAMI_NAME");
        tag.remove("SHIKIGAMI_HP");
        tag.remove("Used");
        tag.remove("used_item");
    }

    private static void revokeAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(id));
            if (adv == null) return;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (!progress.isDone()) return;
            for (String criteria : progress.getCompletedCriteria()) {
                player.getAdvancements().revoke(adv, criteria);
            }
        } catch (Exception ex) {
        }
    }

    private static boolean hasAdvancement(ServerPlayer player, String id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(id));
            if (adv == null) return false;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        } catch (Exception ex) {
            return false;
        }
    }

    private enum SeverEventState {
        UNKNOWN,
        ACTIVE,
        REGROWN,
        DEAD
    }

    private record Candidate(double techniqueId, int moveSelectId, String moveName, String slot) {
        double runtimeSkill() {
            return YutaCopyStore.runtimeSkill(this.techniqueId, this.moveSelectId);
        }
    }
}
