package net.mcreator.jujutsucraft.addon.yuta;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Collections;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

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
    public static final String ITEM_KIND = "jjkaddon_yuta_catalyst_kind";
    public static final String ITEM_SUCCESS_CHANCE = "jjkaddon_yuta_success_chance";
    public static final String ITEM_PLAYER_LIMB_PENDING = "jjkaddon_yuta_player_limb_pending";
    public static final String DOMAIN_SWORD_MARKER = "jjkbrp_yuta_domain_sword";
    public static final String DOMAIN_SWORD_OWNER = "jjkbrp_yuta_domain_sword_owner";
    private static final String KEY_TEN_SHADOWS_PENDING_RECORD = "jjkaddon_yuta_ts_pending_record";
    private static final String KEY_TEN_SHADOWS_PENDING_NUM = "jjkaddon_yuta_ts_pending_num";
    private static final String KEY_TEN_SHADOWS_PENDING_SEEN = "jjkaddon_yuta_ts_pending_seen";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MOB_COPY_USES = 5;
    private static final int MIN_COPY_COOLDOWN_TICKS = 200;
    private static final double HAND_DROP_CHANCE = 0.10D;
    private static final double MOB_COPY_DAMAGE_SHARE = 0.20D;
    private static final String MOB_COPY_DAMAGE_PREFIX = "jjkaddon_yuta_copy_damage_";
    private static final double MIN_COPY_CE_COST = 120.0D;
    private static final double COPY_CE_COST_MULTIPLIER = 1.75D;
    private static final String KEY_COPY_COOLDOWN_UNTIL_PREFIX = "jjkaddon_yuta_copy_cooldown_until_";
    private static final long JOIN_GRACE_TICKS = 100L;
    private static final String KEY_JOIN_TICK = "jjkaddon_yuta_join_tick";
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
        boolean hand = shouldCreateHand(partName);
        ItemStack stack = new ItemStack(hand ? ModItems.YUTA_HAND.get() : ModItems.YUTA_FINGER.get());
        LOGGER.info("[Yuta Copy] Created {} catalyst from source='{}' part='{}' ct1={} ct2={} temporary={} handChance={}", hand ? "hand" : "finger", owner.getName().getString(), partName, technique1, technique2, temporary, HAND_DROP_CHANCE);
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
        tag.putString(ITEM_KIND, hand ? "hand" : "finger");
        tag.putDouble(ITEM_SUCCESS_CHANCE, hand ? 1.0D : 0.5D);
        if (owner.getPersistentData().getBoolean(YutaFakePlayerEntity.KEY_FAKE)) {
            tag.putBoolean("jjkaddon_fake_source", true);
        }
        tag.putString("jjkaddon_limb_item_uuid", UUID.randomUUID().toString());
        String partDisplay = prettyPart(partName);
        stack.setHoverName(Component.literal(hand ? "Cursed Hand" : "Cursed Finger").withStyle(ChatFormatting.LIGHT_PURPLE));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Rika copy catalyst: " + techniqueName(technique1) + (isTechniqueCopyable(technique2) ? " / " + techniqueName(technique2) : "")).withStyle(ChatFormatting.GRAY))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Bound source: " + owner.getName().getString()).withStyle(ChatFormatting.DARK_PURPLE))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Memory anchor: " + partDisplay).withStyle(ChatFormatting.DARK_GRAY))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    public static void spawnMobKillCopyItem(ServerPlayer yuta, LivingEntity source) {
        if (yuta == null || source == null || !(source.level() instanceof ServerLevel level) || source instanceof Player || !isYuta(yuta)) return;
        double ct1 = resolveCopyTechnique(source, true);
        double ct2 = resolveCopyTechnique(source, false);
        if (!isTechniqueCopyable(ct1) && !isTechniqueCopyable(ct2)) {
            LOGGER.info("[Yuta Copy] No catalyst for source='{}': no copyable CT resolved (ct1={} ct2={} skill={} effect={} entityType={} class={}).", source.getName().getString(), ct1, ct2, source.getPersistentData().getDouble("skill"), source.getPersistentData().getDouble("effect"), source.getType().toString(), source.getClass().getSimpleName());
            return;
        }
        LOGGER.info("[Yuta Copy] Valid Yuta mob kill resolved 100% catalyst drop: Yuta='{}' source='{}' ct1={} ct2={} entityType={} class={}", yuta.getName().getString(), source.getName().getString(), ct1, ct2, source.getType().toString(), source.getClass().getSimpleName());
        ItemStack stack = createLimbCopyItem(source, "cursed_technique", ct1, ct2, false);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("jjkaddon_mob_kill_source", true);
        tag.putInt(ITEM_USES, MOB_COPY_USES);
        stack.setHoverName(Component.literal(source.getName().getString() + "'s Cursed Technique Copy").withStyle(ChatFormatting.LIGHT_PURPLE));
        ListTag lore = stack.getOrCreateTagElement("display").getList("Lore", 8);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Uses: " + MOB_COPY_USES).withStyle(ChatFormatting.GRAY))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        ItemEntity drop = new ItemEntity(level, source.getX(), source.getY() + 0.6D, source.getZ(), stack);
        drop.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.2D, 0.25D, (level.random.nextDouble() - 0.5D) * 0.2D);
        level.addFreshEntity(drop);
        LOGGER.info("[Yuta Copy] Dropped mob catalyst for Yuta='{}' source='{}' ct1={} ct2={} uses={} successChance=100%", yuta.getName().getString(), source.getName().getString(), ct1, ct2, MOB_COPY_USES);
    }

    private static double resolveCopyTechnique(LivingEntity source, boolean primary) {
        if (source == null) return 0.0D;
        CompoundTag data = source.getPersistentData();
        String directKey = primary ? "PlayerCurseTechnique" : "PlayerCurseTechnique2";
        double direct = data.getDouble(directKey);
        if (isKnownCopyableTechnique(direct)) return normalizedTechniqueId(direct);
        if (!primary) return 0.0D;

        double fromMobType = techniqueFromMobType(source);
        if (isKnownCopyableTechnique(fromMobType)) {
            LOGGER.info("[Yuta Copy] Resolved CT by exact mob-type fallback for source='{}' class={} entityType={} registry={} ct={}", source.getName().getString(), source.getClass().getSimpleName(), source.getType().toString(), entityRegistryPath(source), fromMobType);
            return normalizedTechniqueId(fromMobType);
        }

        double skill = data.getDouble("skill");
        double fromSkill = techniqueFromRuntimeSkill(skill);
        if (isKnownCopyableTechnique(fromSkill) && runtimeTechniqueMatchesExactMobType(source, fromSkill)) {
            return normalizedTechniqueId(fromSkill);
        }
        double skillDomain = data.getDouble("skill_domain");
        double fromDomain = techniqueFromRuntimeSkill(skillDomain);
        if (isKnownCopyableTechnique(fromDomain) && runtimeTechniqueMatchesExactMobType(source, fromDomain)) {
            return normalizedTechniqueId(fromDomain);
        }
        if (isKnownCopyableTechnique(fromSkill) || isKnownCopyableTechnique(fromDomain) || isKnownCopyableTechnique(data.getDouble("effect"))) {
            LOGGER.info("[Yuta Copy] Ignored unsafe runtime CT for source='{}' class={} registry={} skill={} effect={} skill_domain={} resolvedSkill={} resolvedDomain={}", source.getName().getString(), source.getClass().getSimpleName(), entityRegistryPath(source), skill, data.getDouble("effect"), skillDomain, fromSkill, fromDomain);
        }
        return 0.0D;
    }

    private static double techniqueFromMobType(LivingEntity source) {
        if (source == null) return 0.0D;
        String className = source.getClass().getSimpleName();
        String registryPath = entityRegistryPath(source);
        if (className == null || className.isBlank() || registryPath.isBlank()) return 0.0D;
        return switch (className + "|" + registryPath) {
            case "SukunaEntity|sukuna", "SukunaFushiguroEntity|sukuna_fushiguro", "RyomenSukunaEntity|ryomen_sukuna" -> 1.0D;
            case "GojoSatoruEntity|gojo_satoru", "GojoSatoruSchoolDaysEntity|gojo_satoru_school_days" -> 2.0D;
            case "InumakiTogeEntity|inumaki_toge" -> 3.0D;
            case "JogoEntity|jogo" -> 4.0D;
            case "FushiguroMegumiEntity|fushiguro_megumi", "FushiguroMegumiShibuyaEntity|fushiguro_megumi_shibuya" -> 6.0D;
            case "ChosoEntity|choso", "EsoEntity|eso", "KechizuEntity|kechizu" -> 7.0D;
            case "NaoyaZeninEntity|naoya_zenin", "NaobitoZeninEntity|naobito_zenin" -> 8.0D;
            case "MahitoEntity|mahito" -> 9.0D;
            case "TodoAoiEntity|todo_aoi" -> 10.0D;
            case "MeiMeiEntity|mei_mei", "CrowEntity|crow" -> 11.0D;
            case "GetoSuguruEntity|geto_suguru", "GetoSuguruCurseUserEntity|geto_suguru_curse_user" -> 12.0D;
            case "UroTakakoEntity|uro_takako" -> 13.0D;
            case "TakabaFumihikoEntity|takaba_fumihiko" -> 14.0D;
            case "YorozuEntity|yorozu" -> 15.0D;
            case "UraumeEntity|uraume" -> 16.0D;
            case "TsukumoYukiEntity|tsukumo_yuki" -> 17.0D;
            case "KashimoHajimeEntity|kashimo_hajime" -> 19.0D;
            default -> 0.0D;
        };
    }

    private static String entityRegistryPath(LivingEntity source) {
        if (source == null || source.getType() == null) return "";
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(source.getType());
        return key == null ? "" : key.getPath();
    }

    private static boolean runtimeTechniqueMatchesExactMobType(LivingEntity source, double techniqueId) {
        double expected = techniqueFromMobType(source);
        return isKnownCopyableTechnique(expected) && Math.abs(normalizedTechniqueId(expected) - normalizedTechniqueId(techniqueId)) < 0.001D;
    }

    private static double techniqueFromRuntimeSkill(double runtimeSkill) {
        if (runtimeSkill <= 0.0D) return 0.0D;
        if (runtimeSkill < 100.0D) return runtimeSkill;
        return Math.floor(runtimeSkill / 100.0D);
    }

    private static ServerPlayer resolveYutaContributor(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof ServerPlayer player && isYuta(player)) return player;
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof ServerPlayer player && isYuta(player)) return player;
        }
        if (isRikaEntity(entity)) {
            String ownerUuid = entity.getPersistentData().getString("OWNER_UUID");
            if (ownerUuid != null && !ownerUuid.isBlank() && entity.getServer() != null) {
                try {
                    ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(UUID.fromString(ownerUuid));
                    if (owner != null && isYuta(owner)) return owner;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static ServerPlayer resolveBestYutaContributor(LivingEntity victim) {
        if (victim == null || victim.getServer() == null) return null;
        CompoundTag data = victim.getPersistentData();
        ServerPlayer best = null;
        float bestDamage = 0.0F;
        float required = victim.getMaxHealth() * (float)MOB_COPY_DAMAGE_SHARE;
        for (ServerPlayer player : victim.getServer().getPlayerList().getPlayers()) {
            if (!isYuta(player)) continue;
            float damage = data.getFloat(MOB_COPY_DAMAGE_PREFIX + player.getUUID());
            if (damage >= required && damage > bestDamage) {
                bestDamage = damage;
                best = player;
            }
        }
        if (best != null) {
            LOGGER.info("[Yuta Copy] Best contributor for source='{}' is Yuta='{}' damage={} required={}", victim.getName().getString(), best.getName().getString(), bestDamage, required);
        }
        return best;
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

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide || victim instanceof Player) return;
        ServerPlayer yuta = resolveYutaContributor(event.getSource().getEntity());
        if (yuta == null) {
            yuta = resolveYutaContributor(event.getSource().getDirectEntity());
        }
        if (yuta == null) return;
        CompoundTag data = victim.getPersistentData();
        String key = MOB_COPY_DAMAGE_PREFIX + yuta.getUUID();
        float total = data.getFloat(key) + event.getAmount();
        data.putFloat(key, total);
        LOGGER.debug("[Yuta Copy] Tracked assist damage Yuta='{}' source='{}' added={} total={}", yuta.getName().getString(), victim.getName().getString(), event.getAmount(), total);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof ServerPlayer)) {
            ServerPlayer yuta = resolveYutaContributor(event.getSource().getEntity());
            if (yuta == null) {
                yuta = resolveYutaContributor(event.getSource().getDirectEntity());
            }
            if (yuta == null) {
                yuta = resolveBestYutaContributor(victim);
            }
            if (yuta != null) {
                spawnMobKillCopyItem(yuta, victim);
            } else {
                LOGGER.info("[Yuta Copy] No mob catalyst for source='{}': no Yuta killer/contributor met damage threshold.", victim.getName().getString());
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
            owner.displayClientMessage(Component.literal("§d[Rika] Rika already knows every move this catalyst can offer."), false);
            playCopyFeedback(owner, false, ct1);
            return true;
        }
        boolean guaranteedGrant = isFiveUseCatalyst(tag);
        double chance = guaranteedGrant ? 1.0D : (tag.contains(ITEM_SUCCESS_CHANCE) ? tag.getDouble(ITEM_SUCCESS_CHANCE) : 1.0D);
        String kind = tag.getString(ITEM_KIND).isBlank() ? "finger" : tag.getString(ITEM_KIND);
        if (!guaranteedGrant && RANDOM.nextDouble() > chance) {
            owner.displayClientMessage(Component.literal("§7[Rika] The " + kind + " held no usable technique this time."), false);
            playCopyFeedback(owner, false, ct1);
            return true;
        }
        Collections.shuffle(pool, RANDOM);
        Candidate chosen = pool.get(0);
        ListTag store = getStore(owner);
        String recordUuid = UUID.randomUUID().toString();
        CompoundTag rec = new CompoundTag();
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
        store.add(rec);
        owner.getPersistentData().put(KEY_STORE, store);
        owner.getPersistentData().putString(KEY_SELECTED_RECORD, recordUuid);
        owner.getPersistentData().putDouble(KEY_SELECTED, rec.getDouble("skill"));
        playCopyFeedback(owner, true, chosen.techniqueId);
        owner.displayClientMessage(Component.literal("§d[Rika] Learned " + techniqueName(chosen.techniqueId) + ": " + chosen.moveName + " from " + sourceName + "."), false);
        owner.displayClientMessage(Component.literal("§d[Rika] Digested 1 copied move from this catalyst."), false);
        int learnedTechniqueId = (int)Math.round(chosen.techniqueId);
        {
            boolean complete = true;
            for (MoveInfo move : copyableMoves(learnedTechniqueId)) {
                if (!hasDuplicate(owner, learnedTechniqueId, move.selectId())) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                owner.displayClientMessage(Component.literal("§d[Rika] " + techniqueName(learnedTechniqueId) + " is now fully digested."), false);
                playFullBuffFeedback(owner, learnedTechniqueId);
            }
        }
        return true;
    }

    private static boolean isFiveUseCatalyst(CompoundTag tag) {
        return tag != null && tag.contains(ITEM_USES) && tag.getInt(ITEM_USES) == MOB_COPY_USES;
    }

    private static boolean shouldCreateHand(String partName) {
        String part = partName == null ? "" : partName.toLowerCase();
        boolean eligible = part.contains("arm") || part.contains("hand");
        double roll = RANDOM.nextDouble();
        boolean hand = eligible && roll < HAND_DROP_CHANCE;
        LOGGER.info("[Yuta Copy] Catalyst roll part='{}' eligibleHand={} roll={} threshold={} result={}", partName, eligible, roll, HAND_DROP_CHANCE, hand ? "hand" : "finger");
        return hand;
    }

    public static void attachPlayerLimbCopyData(LivingEntity owner, String partName, Entity limbEntity) {
        if (!(owner instanceof ServerPlayer player) || limbEntity == null || isHeadPart(partName)) {
            return;
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (!isTechniqueCopyable(vars.PlayerCurseTechnique) && !isTechniqueCopyable(vars.PlayerCurseTechnique2)) {
            return;
        }
        ItemStack stack = createLimbCopyItem(owner, partName, vars.PlayerCurseTechnique, vars.PlayerCurseTechnique2, true);
        CompoundTag src = stack.getOrCreateTag();
        CompoundTag data = limbEntity.getPersistentData();
        data.putBoolean(ITEM_PLAYER_LIMB_PENDING, true);
        for (String key : src.getAllKeys()) {
            data.put("copy_" + key, src.get(key).copy());
        }
        rememberSeverEvent(player, src.getString(ITEM_EVENT_ID), partName);
    }

    private static void playCopyFeedback(ServerPlayer owner, boolean success, double techniqueId) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        int id = (int)Math.round(techniqueId);
        level.sendParticles(success ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE, owner.getX(), owner.getY() + 1.0D, owner.getZ(), success ? 36 : 16, 0.55D, 0.45D, 0.55D, success ? 0.08D : 0.02D);
        level.playSound(null, owner.blockPosition(), success ? (id == 3 ? SoundEvents.NOTE_BLOCK_BELL.get() : SoundEvents.ENCHANTMENT_TABLE_USE) : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, success ? 0.85F : 0.55F, success ? (0.85F + (id % 7) * 0.08F) : 0.7F);
    }

    private static void playFullBuffFeedback(ServerPlayer owner, double techniqueId) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getY() + 1.0D, owner.getZ(), 48, 0.7D, 0.7D, 0.7D, 0.12D);
        level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.25F);
    }

    private static void addCandidates(ServerPlayer owner, List<Candidate> pool, double techniqueId, String slot) {
        if (!isTechniqueCopyable(techniqueId)) {
            return;
        }
        for (MoveInfo move : copyableMoves(techniqueId)) {
            if (!hasDuplicate(owner, techniqueId, move.selectId())) {
                pool.add(new Candidate(techniqueId, move.selectId(), move.name(), slot));
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
        return isKnownCopyableTechnique(techniqueId);
    }

    private static boolean isKnownCopyableTechnique(double techniqueId) {
        int id = (int)Math.round(techniqueId);
        return Math.abs(techniqueId - id) < 0.001D && switch (id) {
            case 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 -> true;
            default -> false;
        };
    }

    private static double normalizedTechniqueId(double techniqueId) {
        return (double)Math.round(techniqueId);
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

    public static boolean isActiveYuta(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double activeTechnique = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        return Math.abs(activeTechnique - 5.0D) < 0.001D;
    }

    public static boolean hasValidRikaOrDomain(ServerPlayer player) {
        if (resolveValidRika(player).isPresent()) return true;
        return hasValidYutaDomain(player);
    }

    private static boolean hasValidYutaDomain(ServerPlayer player) {
        return player != null && player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get()) && !player.getPersistentData().getBoolean("Failed") && Math.abs(player.getPersistentData().getDouble("skill_domain") - 5.0D) < 0.001D;
    }

    private static boolean inPostLoginGrace(ServerPlayer player) {
        long joinTick = player.getPersistentData().getLong(KEY_JOIN_TICK);
        return joinTick > 0L && player.level().getGameTime() - joinTick < JOIN_GRACE_TICKS;
    }

    public static void markDomainSword(ItemStack stack, ServerPlayer owner) {
        if (stack == null || stack.isEmpty() || !stack.is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get())) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(DOMAIN_SWORD_MARKER, true);
        if (owner != null) {
            tag.putString(DOMAIN_SWORD_OWNER, owner.getUUID().toString());
        }
    }

    private static boolean isAddonDomainSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get()) && stack.hasTag() && stack.getOrCreateTag().getBoolean(DOMAIN_SWORD_MARKER);
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

    private static boolean isBannedCopiedTenShadowsMove(double techniqueId, int moveSelectId) {
        if (Math.round(techniqueId) != 6L) return false;
        return switch (moveSelectId) {
            case 7, 17, 18 -> true;
            default -> false;
        };
    }

    private static void sanitizeBannedCopiedTenShadowsRecords(ServerPlayer player) {
        if (player == null) return;
        ListTag store = getStore(player);
        boolean changed = false;
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
            if (rec.getBoolean("valid") && isBannedCopiedTenShadowsMove(rec.getDouble("techniqueId"), moveSelectId)) {
                rec.putBoolean("valid", false);
                rec.putString("invalidation", "banned_ten_shadows_copy");
                changed = true;
                player.getPersistentData().remove(cooldownKey(rec));
                clearCopiedTenShadowsPending(player, rec.getString("recordUuid"));
            }
        }
        if (changed) {
            player.getPersistentData().put(KEY_STORE, store);
            clearSelection(player);
            clearSureHit(player);
        }
    }

    public static List<CompoundTag> validRecords(ServerPlayer player) {
        sanitizeBannedCopiedTenShadowsRecords(player);
        repairCopiedRecordRuntimeSkills(player);        ArrayList<CompoundTag> out = new ArrayList<>();
        ListTag store = getStore(player);
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
            if (rec.getBoolean("valid") && rec.getDouble("skill") > 0.0D && !isBannedCopiedTenShadowsMove(rec.getDouble("techniqueId"), moveSelectId)) out.add(rec.copy());
        }
        return out;
    }

    private static void repairCopiedRecordRuntimeSkills(ServerPlayer player) {
        if (player == null) return;
        ListTag store = getStore(player);
        boolean changed = false;
        for (int i = 0; i < store.size(); ++i) {
            CompoundTag rec = store.getCompound(i);
            if (!rec.getBoolean("valid")) continue;
            double techniqueId = rec.getDouble("techniqueId");
            int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
            double expectedSkill = runtimeSkill(techniqueId, moveSelectId);
            if (Math.abs(rec.getDouble("skill") - expectedSkill) > 0.001D || Math.abs(rec.getDouble("runtimeSkill") - expectedSkill) > 0.001D) {
                LOGGER.info("[Yuta Copy] Repaired copied record runtime mapping player={} record={} technique={} moveSelect={} oldSkill={} newSkill={}", player.getGameProfile().getName(), rec.getString("recordUuid"), techniqueId, moveSelectId, rec.getDouble("skill"), expectedSkill);
                rec.putDouble("skill", expectedSkill);
                rec.putDouble("runtimeSkill", expectedSkill);
                rec.putDouble("effect", techniqueId);
                rec.putString("moveName", moveName(techniqueId, moveSelectId));
                rec.putString("displayName", displayName(techniqueId, rec.getString("moveName"), rec.getString("sourceName")));
                changed = true;
            }
        }
        if (changed) {
            player.getPersistentData().put(KEY_STORE, store);
            String selectedUuid = player.getPersistentData().getString(KEY_SELECTED_RECORD);
            for (int i = 0; i < store.size(); ++i) {
                CompoundTag rec = store.getCompound(i);
                if (selectedUuid.equals(rec.getString("recordUuid"))) {
                    player.getPersistentData().putDouble(KEY_SELECTED, rec.getDouble("skill"));
                    break;
                }
            }
        }
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

    public static void clearTransientYutaRuntimeState(ServerPlayer player) {
        if (player == null || isActiveYuta(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        double selectedSkill = data.getDouble(KEY_SELECTED);
        double skill = data.getDouble("skill");
        clearSelection(player);
        clearSureHit(player);
        if (isVanillaHardcodedCopyRuntime(skill) || (selectedSkill > 0.0D && Math.abs(skill - selectedSkill) <= 0.001D)) {
            data.putDouble("skill", 0.0D);
            data.putDouble("effect", 0.0D);
            data.putBoolean("PRESS_Z", false);
        }
        data.remove("jjkaddon_yuta_surehit_runtime_skill");
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
        return activateRecord(player, selectedRecord(player), false);
    }

    public static boolean activateDomainSwordCopy(ServerPlayer player) {
        if (!isActiveYuta(player) || !hasValidRikaOrDomain(player)) {
            player.displayClientMessage(Component.literal("§c[Rika] Authentic Mutual Love or Rika is required."), true);
            return false;
        }
        List<CompoundTag> candidates = new ArrayList<>();
        for (CompoundTag rec : validRecords(player)) {
            if (isUsableDomainSwordRecord(rec)) {
                candidates.add(rec);
            }
        }
        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.literal("§e[Rika] No copied technique available for this sword."), true);
            return false;
        }
        CompoundTag chosen = candidates.get(RANDOM.nextInt(candidates.size()));
        int chosenMoveSelectId = chosen.contains("moveSelectId") ? chosen.getInt("moveSelectId") : 5;
        LOGGER.info("[Yuta Copy] Domain sword cast player={} recordUuid={} techniqueId={} moveSelectId={} candidates={}", player.getGameProfile().getName(), chosen.getString("recordUuid"), chosen.getDouble("techniqueId"), chosenMoveSelectId, candidates.size());
        return activateRecord(player, Optional.of(chosen), true);
    }

    private static boolean isUsableDomainSwordRecord(CompoundTag rec) {
        int moveSelectId = rec != null && rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        return rec != null && rec.getBoolean("valid") && rec.getDouble("skill") > 0.0D && !isBannedCopiedTenShadowsMove(rec.getDouble("techniqueId"), moveSelectId) && !isDomainMove(rec.getDouble("techniqueId"), moveSelectId, rec.getString("moveName"));
    }

    private static boolean activateRecord(ServerPlayer player, Optional<CompoundTag> opt, boolean fromDomainSword) {
        if (player.getPersistentData().getDouble("skill") != 0.0D) {
            player.displayClientMessage(Component.literal("§c[Rika] Finish your current technique first."), true);
            return false;
        }
        if (opt.isEmpty()) {
            player.displayClientMessage(Component.literal(fromDomainSword ? "§e[Rika] No copied technique available for this sword." : "§e[Rika] No copied technique selected."), true);
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
        boolean copiedTenShadows = isCopiedTenShadowsRecord(rec);
        int copiedTenShadowsMoveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        int copiedTenShadowsNum = tenShadowsTechniqueNumForMove(copiedTenShadowsMoveSelectId);
        String copiedTenShadowsMoveName = copiedTenShadowsDisplayName(rec);
        if (copiedTenShadows && copiedTenShadowsNum > 0 && isCopiedTenShadowsActive(player, copiedTenShadowsNum)) {
            int recalled = recallCopiedTenShadows(player, copiedTenShadowsNum);
            if (recalled > 0) {
                markCopiedTenShadowsPending(player, rec, copiedTenShadowsNum, true);
                player.displayClientMessage(Component.literal("§d[Rika] Recalled Ten Shadows: " + copiedTenShadowsMoveName + "."), true);
                LOGGER.info("[Yuta Copy] Recalled copied Ten Shadows player={} moveSelect={} shikigamiNum={} recalled={}", player.getGameProfile().getName(), copiedTenShadowsMoveSelectId, copiedTenShadowsNum, recalled);
                return true;
            }
            clearStaleCopiedTenShadowsState(player, copiedTenShadowsNum);
            player.displayClientMessage(Component.literal("§e[Rika] No active Ten Shadows shikigami found to recall."), true);
            return false;
        }
        long cooldownUntil = player.getPersistentData().getLong(cooldownKey);
        if (cooldownUntil > now) {
            long remaining = cooldownUntil - now;
            String message = copiedTenShadows
                ? "§e[Rika] Ten Shadows: " + copiedTenShadowsMoveName + " is still on cooldown: " + ((remaining + 19L) / 20L) + "s."
                : "§e[Rika] This copied skill is still on cooldown: " + ((remaining + 19L) / 20L) + "s.";
            player.displayClientMessage(Component.literal(message), true);
            return false;
        }
        if (copiedTenShadows && copiedTenShadowsNum > 0) {
            if (isCopiedTenShadowsActive(player, copiedTenShadowsNum) || hasActiveTenShadowsEntity(player, copiedTenShadowsNum)) {
                return false;
            }
            cullLowestWeightShikigamiIfNeeded(player, copiedTenShadowsNum, shikigamiWeightForNum(copiedTenShadowsNum));
        }
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        if (!player.isCreative() && vars.PlayerCursePower < cost) {
            String message = copiedTenShadows
                ? "§c[Rika] Not enough cursed energy for Ten Shadows: " + copiedTenShadowsMoveName + ". Need " + (int)Math.ceil(cost) + "."
                : "§c[Rika] Not enough cursed energy. Need " + (int)Math.ceil(cost) + ".";
            player.displayClientMessage(Component.literal(message), true);
            return false;
        }
        if (!player.isCreative()) {
            player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
                cap.PlayerCursePower = Math.max(0.0D, cap.PlayerCursePower - cost);
                cap.syncPlayerVariables((Entity)player);
            });
        }
        if (!copiedTenShadows) {
            player.getPersistentData().putLong(cooldownKey, now + (long)cooldownTicks);
        }
        prepareCopiedTenShadowsState(player, rec);
        player.getPersistentData().putDouble("skill", rec.getDouble("skill"));
        player.getPersistentData().putDouble("effect", rec.getDouble("effect"));
        player.getPersistentData().putDouble("COOLDOWN_TICKS", cooldownTicks);
        player.getPersistentData().putBoolean("PRESS_Z", true);
        player.addEffect(new MobEffectInstance((MobEffect)JujutsucraftModMobEffects.CURSED_TECHNIQUE.get(), Integer.MAX_VALUE, 0, false, false));
        boolean freeDomainSwordCast = fromDomainSword && hasValidYutaDomain(player);
        if (rec.contains("usesRemaining") && !freeDomainSwordCast) {
            decrementRecordUse(player, rec.getString("recordUuid"));
        }
        if (copiedTenShadows && copiedTenShadowsNum > 0) {
            markCopiedTenShadowsPending(player, rec, copiedTenShadowsNum, false);
            player.displayClientMessage(Component.literal("§d[Rika] Activated Ten Shadows: " + copiedTenShadowsMoveName + (fromDomainSword ? " from Authentic Mutual Love." : ".")), true);
        } else {
            player.displayClientMessage(Component.literal("§d[Rika] Activated " + rec.getString("displayName") + (fromDomainSword ? " from Authentic Mutual Love." : ".")), true);
        }
        return true;
    }

    private static boolean isCopiedTenShadowsRecord(CompoundTag rec) {
        return rec != null && Math.round(rec.getDouble("techniqueId")) == 6L;
    }

    private static String copiedTenShadowsDisplayName(CompoundTag rec) {
        int moveSelectId = rec != null && rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        return tenShadowsWheelMoveName(moveSelectId);
    }

    public static boolean isCopiedTenShadowsMoveActive(ServerPlayer player, CompoundTag rec) {
        if (!isCopiedTenShadowsRecord(rec)) return false;
        int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        int tenShadowsNum = tenShadowsTechniqueNumForMove(moveSelectId);
        return tenShadowsNum > 0 && isCopiedTenShadowsActive(player, tenShadowsNum);
    }

    private static void markCopiedTenShadowsPending(ServerPlayer player, CompoundTag rec, int tenShadowsNum, boolean alreadySeenActive) {
        CompoundTag data = player.getPersistentData();
        data.putString(KEY_TEN_SHADOWS_PENDING_RECORD, rec.getString("recordUuid"));
        data.putInt(KEY_TEN_SHADOWS_PENDING_NUM, tenShadowsNum);
        data.putBoolean(KEY_TEN_SHADOWS_PENDING_SEEN, alreadySeenActive);
    }

    private static void clearCopiedTenShadowsPending(ServerPlayer player, String recordUuid) {
        CompoundTag data = player.getPersistentData();
        if (recordUuid == null || recordUuid.isBlank() || recordUuid.equals(data.getString(KEY_TEN_SHADOWS_PENDING_RECORD))) {
            data.remove(KEY_TEN_SHADOWS_PENDING_RECORD);
            data.remove(KEY_TEN_SHADOWS_PENDING_NUM);
            data.remove(KEY_TEN_SHADOWS_PENDING_SEEN);
        }
    }

    private static int countCopiedTenShadowsActive(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel)) return 0;
        ServerLevel level = (ServerLevel)player.level();
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return 0;
        int count = 0;
        for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(128.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
            count += tenShadowsWeight(candidate);
            if (count >= 2) return count;
        }
        return count;
    }

    private static boolean isCopiedTenShadowsActive(ServerPlayer player, int tenShadowsNum) {
        if (player == null || tenShadowsNum <= 0) return false;
        return player.getPersistentData().getDouble("TenShadowsTechnique" + tenShadowsNum) == -1.0D || hasActiveTenShadowsEntity(player, tenShadowsNum);
    }

    private static boolean hasActiveTenShadowsEntity(ServerPlayer player, int tenShadowsNum) {
        if (player == null || tenShadowsNum <= 0 || !(player.level() instanceof ServerLevel)) return false;
        ServerLevel level = (ServerLevel)player.level();
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return false;
        for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(128.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
            if (returnTenShadowsNum(candidate) == tenShadowsNum) return true;
        }
        return false;
    }

    private static boolean isActiveTenShadowsEntityFor(ServerPlayer player, Entity candidate, double friend) {
        if (candidate == null || !candidate.isAlive() || candidate == player) return false;
        CompoundTag data = candidate.getPersistentData();
        return candidate.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("jujutsucraft:ten_shadows_technique")))
            && !data.getBoolean("domain_entity")
            && data.getBoolean("Ambush")
            && Math.abs(data.getDouble("friend_num") - friend) < 0.001D;
    }

    private static int recallCopiedTenShadows(ServerPlayer player, int tenShadowsNum) {
        if (player == null || tenShadowsNum <= 0 || !(player.level() instanceof ServerLevel level)) return 0;
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return 0;
        int recalled = 0;
        for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(128.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
            int candidateNum = returnTenShadowsNum(candidate);
            if (candidateNum != tenShadowsNum) continue;
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(candidate.getType());
            String registryPath = key == null ? "" : key.getPath();
            boolean isRabbit = "rabbit_escape".equals(registryPath);
            int particleCount = isRabbit ? 10 : 80;
            double spread = isRabbit ? 0.25D : 0.5D;
            level.sendParticles(ParticleTypes.PORTAL,
                candidate.getX(),
                candidate.getY() + candidate.getBbHeight() * 0.5D,
                candidate.getZ(),
                particleCount, spread, spread, spread, 0.0D);
            candidate.getPersistentData().putBoolean("flag_despawn", true);
            recalled++;
        }
        if (recalled > 0) {
            clearStaleCopiedTenShadowsState(player, tenShadowsNum);
            player.getPersistentData().putDouble("skill", 0.0D);
            player.getPersistentData().putBoolean("PRESS_Z", false);
        }
        return recalled;
    }

    private static void clearStaleCopiedTenShadowsState(ServerPlayer player, int tenShadowsNum) {
        if (player == null || tenShadowsNum <= 0) return;
        CompoundTag data = player.getPersistentData();
        String key = "TenShadowsTechnique" + tenShadowsNum;
        if (data.getDouble(key) == -1.0D) {
            data.putDouble(key, 1.0D);
        }
        if (tenShadowsNum == 3) {
            if (data.getDouble("TenShadowsTechnique1") > -2.0D) data.putDouble("TenShadowsTechnique1", 1.0D);
            if (data.getDouble("TenShadowsTechnique2") > -2.0D) data.putDouble("TenShadowsTechnique2", 1.0D);
        }
        if (tenShadowsNum == 13 && data.getDouble("TenShadowsTechnique4") > -2.0D) {
            data.putDouble("TenShadowsTechnique4", 1.0D);
        }
        data.putDouble("NUM_TenShadowsTechnique", Math.max(0.0D, data.getDouble("NUM_TenShadowsTechnique") - 1.0D));
    }

    private static int tenShadowsWeight(Entity entity) {
        return entity != null && entity.getType().toString().contains("rabbit_escape") ? 0 : 1;
    }

    private static int returnTenShadowsNum(Entity entity) {
        if (entity == null) return -1;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String path = key == null ? "" : key.getPath();
        if ("divine_dog_white".equals(path)) return 1;
        if ("divine_dog_black".equals(path)) return 2;
        if ("divine_dog_totality".equals(path)) return 3;
        if ("nue".equals(path)) return 4;
        if ("great_serpent".equals(path)) return 5;
        if ("toad".equals(path) || "toad2".equals(path) || "mahoraga_frog".equals(path)) return 6;
        if ("max_elephant".equals(path)) return 7;
        if ("rabbit_escape".equals(path)) return 8;
        if ("round_deer".equals(path)) return 9;
        if ("piercing_ox".equals(path)) return 10;
        if ("tiger_funeral".equals(path)) return 11;
        if ("eight_handled_sword_divergent_sila_divine_general_mahoraga".equals(path)) return 14;
        return -1;
    }

    private static double shikigamiWeightForEntity(Entity e) {
        if (e == null) return 1.0D;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        String path = key == null ? "" : key.getPath();
        if ("max_elephant".equals(path)) return 2.0D;
        if ("rabbit_escape".equals(path)) return 0.025D;
        return 1.0D;
    }

    private static double shikigamiWeightForNum(int tenShadowsNum) {
        if (tenShadowsNum == 7) return 2.0D;
        if (tenShadowsNum == 8) return 0.025D;
        return 1.0D;
    }

    private static double totalCopiedShikigamiWeight(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0.0D;
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return 0.0D;
        double total = 0.0D;
        for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(128.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
            total += shikigamiWeightForEntity(candidate);
        }
        return total;
    }

    private static void cullLowestWeightShikigamiIfNeeded(ServerPlayer player, int newTenShadowsNum, double newWeight) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return;
        while (totalCopiedShikigamiWeight(player) + newWeight > 2.0D) {
            List<Entity> candidates = new ArrayList<>();
            for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(128.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
                if (returnTenShadowsNum(candidate) == newTenShadowsNum) continue;
                if (candidate.getPersistentData().getBoolean("flag_despawn")) continue;
                candidates.add(candidate);
            }
            if (candidates.isEmpty()) break;
            candidates.sort((a, b) -> {
                int cmp = Double.compare(shikigamiWeightForEntity(a), shikigamiWeightForEntity(b));
                if (cmp != 0) return cmp;
                return a.getUUID().compareTo(b.getUUID());
            });
            Entity victim = candidates.get(0);
            victim.getPersistentData().putBoolean("flag_despawn", true);
        }
    }

    private static void despawnAllCopiedShikigami(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        double friend = player.getPersistentData().getDouble("friend_num");
        if (friend == 0.0D) return;
        boolean despawned = false;
        for (Entity candidate : level.getEntities(player, player.getBoundingBox().inflate(256.0D), e -> isActiveTenShadowsEntityFor(player, e, friend))) {
            candidate.getPersistentData().putBoolean("flag_despawn", true);
            despawned = true;
        }
        if (despawned) {
            player.getPersistentData().putDouble("NUM_TenShadowsTechnique", 0.0D);
            clearCopiedTenShadowsPending(player, null);
        }
    }

    private static void prepareCopiedTenShadowsState(ServerPlayer player, CompoundTag rec) {
        if (player == null || rec == null || Math.round(rec.getDouble("techniqueId")) != 6) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        for (int i = 1; i <= 14; ++i) {
            String key = "TenShadowsTechnique" + i;
            if (!data.contains(key)) {
                data.putDouble(key, -2.0D);
            }
        }
        allowCopiedTenShadowsTame(data, 1);
        allowCopiedTenShadowsTame(data, 2);
        allowCopiedTenShadowsTame(data, 4);
        allowCopiedTenShadowsTame(data, 6);
        int moveSelectId = rec.contains("moveSelectId") ? rec.getInt("moveSelectId") : 5;
        int tenShadowsNum = tenShadowsTechniqueNumForMove(moveSelectId);
        if (tenShadowsNum > 0) {
            allowCopiedTenShadowsTame(data, tenShadowsNum);
        }
        data.putBoolean("flag_mahoraga", false);
        data.putBoolean("flag_agito", false);
        if (data.getDouble("NUM_TenShadowsTechnique") < 0.0D || data.getDouble("NUM_TenShadowsTechnique") > 2.0D) {
            data.putDouble("NUM_TenShadowsTechnique", 0.0D);
        }
        LOGGER.debug("[Yuta Copy] Prepared copied Ten Shadows state player={} moveSelect={} shikigamiNum={} numActive={}", player.getGameProfile().getName(), moveSelectId, tenShadowsNum, data.getDouble("NUM_TenShadowsTechnique"));
    }


    private static void allowCopiedTenShadowsTame(CompoundTag data, int tenShadowsNum) {
        String key = "TenShadowsTechnique" + tenShadowsNum;
        if (data.getDouble(key) <= -2.0D) {
            data.putDouble(key, 1.0D);
        }
    }

    private static int tenShadowsTechniqueNumForMove(int moveSelectId) {
        return switch (moveSelectId) {
            case 5 -> 1;
            case 6 -> 2;
            case 7 -> 3;
            case 8 -> 4;
            case 9 -> 5;
            case 10 -> 6;
            case 11 -> 7;
            case 12 -> 8;
            case 13 -> 9;
            case 14 -> 10;
            case 15 -> 11;
            case 17 -> 13;
            case 18 -> 14;
            default -> -1;
        };
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
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().putLong(KEY_JOIN_TICK, player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        claimNearbyPlayerLimb(player);
        if (isActiveYuta(player)) {
            cleanupVanillaPlayerCopy(player);            tickCopiedTenShadowsCooldown(player);
        }
        if (!hasValidYutaDomain(player) && !inPostLoginGrace(player)) {
            cleanupDomainSwords(player);
        }
        if (isActiveYuta(player) && !hasValidRikaOrDomain(player) && !inPostLoginGrace(player)) {
            despawnAllCopiedShikigami(player);
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

    private static void tickCopiedTenShadowsCooldown(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        String recordUuid = data.getString(KEY_TEN_SHADOWS_PENDING_RECORD);
        if (recordUuid == null || recordUuid.isBlank()) return;
        int tenShadowsNum = data.getInt(KEY_TEN_SHADOWS_PENDING_NUM);
        if (tenShadowsNum <= 0) {
            clearCopiedTenShadowsPending(player, recordUuid);
            return;
        }
        if (isCopiedTenShadowsActive(player, tenShadowsNum)) {
            data.putBoolean(KEY_TEN_SHADOWS_PENDING_SEEN, true);
            return;
        }
        if (!data.getBoolean(KEY_TEN_SHADOWS_PENDING_SEEN) && data.getDouble("skill") != 0.0D) {
            return;
        }
        for (CompoundTag rec : validRecords(player)) {
            if (!recordUuid.equals(rec.getString("recordUuid"))) continue;
            long now = player.level().getGameTime();
            player.getPersistentData().putLong(cooldownKey(rec), now + (long)normalizedCooldown(rec));
            clearCopiedTenShadowsPending(player, recordUuid);
            LOGGER.debug("[Yuta Copy] Applied deferred Ten Shadows cooldown player={} record={} shikigamiNum={}", player.getGameProfile().getName(), recordUuid, tenShadowsNum);
            return;
        }
        clearCopiedTenShadowsPending(player, recordUuid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer) || !isAddonDomainSword(event.getEntity().getItem())) {
            return;
        }
        event.getEntity().discard();
    }

    private static void cleanupDomainSwords(ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            if (isAddonDomainSword(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (isAddonDomainSword(player.getMainHandItem())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            changed = true;
        }
        if (isAddonDomainSword(player.getOffhandItem())) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void claimNearbyPlayerLimb(ServerPlayer player) {
        if (!isYuta(player) || !(player.level() instanceof ServerLevel level)) return;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(1.7D), e -> e.getPersistentData().getBoolean(ITEM_PLAYER_LIMB_PENDING))) {
            CompoundTag data = entity.getPersistentData();
            ItemStack stack = new ItemStack("hand".equals(data.getString("copy_" + ITEM_KIND)) ? ModItems.YUTA_HAND.get() : ModItems.YUTA_FINGER.get());
            CompoundTag tag = stack.getOrCreateTag();
            for (String key : data.getAllKeys()) {
                if (key.startsWith("copy_")) tag.put(key.substring(5), data.get(key).copy());
            }
            if (player.getInventory().add(stack)) {
                player.displayClientMessage(Component.literal("§d[Rika] Collected " + stack.getHoverName().getString() + " from severed limb."), true);
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
                level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 0.3D, entity.getZ(), 18, 0.25D, 0.25D, 0.25D, 0.04D);
                entity.discard();
            }
            return;
        }
    }

    public static void bridgeAuthenticMutualLove(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (Math.abs(entity.getPersistentData().getDouble("skill_domain") - 5.0D) > 0.001D) return;
        clearSureHit(player);
        List<CompoundTag> candidates = new ArrayList<>();
        for (CompoundTag rec : validRecords(player)) {
            if (isValidSureHitRecord(rec)) {
                candidates.add(rec);
            }
        }
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, new Random(player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits()));
        CompoundTag rec = candidates.get(0);
        double skill = rec.getDouble("skill");
        if (!(skill % 100.0D <= 20.0D && skill >= 100.0D) || (skill >= 500.0D && skill < 600.0D)) return;
        double oldDomain = entity.getPersistentData().getDouble("skill_domain");
        double oldSkill = entity.getPersistentData().getDouble("skill");
        double oldCooldown = entity.getPersistentData().getDouble("COOLDOWN_TICKS");
        if (isCopiedTenShadowsRecord(rec)) {
            prepareCopiedTenShadowsState(player, rec);
        }
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
        int id = (int)Math.round(techniqueId);
        int select = moveSelectId;
        int base;
        switch (id) {
            case 1: base = 100; break;
            case 2: base = 200; break;
            case 3: base = 300; break;
            case 4: base = 400; break;
            case 6: base = 600; break;
            case 7: base = 1000; break;
            case 8: base = 1900; break;
            case 9: base = 1500; break;
            case 10: base = 2000; break;
            case 11: base = 1100; break;
            case 12:
            case 18: base = 1800; break;
            case 13: base = 3800; break;
            case 14: base = 1700; break;
            case 15: base = 3900; break;
            case 16: base = 2400; break;
            case 17: base = 900; break;
            case 19: base = 700; break;
            default: base = id * 100; break;
        }
        return base + select;
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
            case 4 -> new int[]{5, 6, 7, 8, 9, 15};
            case 6 -> new int[]{5, 6, 8, 9, 10, 11, 12, 13, 14, 15};
            case 7 -> new int[]{5, 6, 7, 8, 9, 16, 18, 19};
            case 8 -> new int[]{5, 7, 10, 15, 16};
            case 9 -> new int[]{5, 6, 7, 8, 9, 10, 15, 16};
            case 10 -> new int[]{5, 6, 7, 15};
            case 11 -> new int[]{5};
            case 12, 18 -> new int[]{5, 10, 15, 16, 17, 18, 19};
            case 13 -> new int[]{10};
            case 14 -> new int[]{5, 6, 10, 15};
            case 15 -> new int[]{5, 6, 7, 8, 9, 12, 14, 15};
            case 16 -> new int[]{5, 6, 8, 9};
            case 17 -> new int[]{5, 6, 10, 15};
            case 19 -> new int[]{5, 10, 15, 16, 17};
            default -> new int[]{5, 6, 7};
        };
        for (int selectId : whitelist) {
            String name = moveName(techniqueId, selectId);
            if (isBannedCopiedTenShadowsMove(techniqueId, selectId) || isDomainMove(techniqueId, selectId, name)) continue;
            moves.add(new MoveInfo(selectId, name));
            if (id != 1 && id != 2 && id != 4 && id != 6 && moves.size() >= 5) {
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
            case 4 -> switch (moveSelectId) { case 5 -> "Flame Fire"; case 6 -> "Flame Fire 2"; case 7 -> "Ember Insects"; case 8 -> "Flame Fire 3"; case 9 -> "Flame Fire 4"; case 15 -> "Maximum Meteor"; case 20 -> "Coffin of the Iron Mountain"; default -> "Disaster Flames Move " + moveSelectId; };
            case 6 -> tenShadowsWheelMoveName(moveSelectId);
            case 7 -> switch (moveSelectId) { case 5 -> "Slicing Exorcism"; case 6 -> "Convergence"; case 7 -> "Piercing Blood"; case 8 -> "Supernova"; case 9 -> "Flowing Red Scale"; case 16 -> "Blood Wave"; case 18 -> "Wing King"; case 19 -> "Rot Technique"; default -> "Blood Manipulation Move " + moveSelectId; };
            case 8 -> switch (moveSelectId) { case 5 -> "Projection Sorcery"; case 7 -> "Speed Is Power"; case 10 -> "Explode Air"; case 15 -> "Projection Attack"; case 16 -> "Flying Tackle"; default -> "Projection Sorcery Move " + moveSelectId; };
            case 9 -> switch (moveSelectId) { case 5 -> "Idle Transfiguration"; case 6 -> "Soul Hand"; case 7 -> "Polymorphic Gun"; case 8 -> "Body Repel"; case 9 -> "Body Repel Barrage"; case 10 -> "Soul Isomer"; case 15 -> "Instant Spirit Body"; case 16 -> "Mahito Strong Attack"; default -> "Idle Transfiguration Move " + moveSelectId; };
            case 10 -> switch (moveSelectId) { case 5 -> "Boogie Woogie"; case 6 -> "Lariat"; case 7 -> "Consecutive Attacks"; case 15 -> "Todo Black Flash"; default -> "Boogie Woogie Move " + moveSelectId; };
            case 11 -> switch (moveSelectId) { case 5 -> "Bird Strike"; default -> "Black Bird Manipulation Move " + moveSelectId; };
            case 12, 18 -> switch (moveSelectId) { case 5 -> "Cursed Spirit Manipulation"; case 10 -> "Cancel Curse"; case 15 -> "Uzumaki"; case 16 -> "Mini Uzumaki"; case 17 -> "Happy Set 1"; case 18 -> "Happy Set 2"; case 19 -> "Happy Set 3"; default -> "Cursed Spirit Manipulation Move " + moveSelectId; };
            case 13 -> switch (moveSelectId) { case 10 -> "Thin Ice Breaker"; default -> "Sky Manipulation Move " + moveSelectId; };
            case 14 -> switch (moveSelectId) { case 5 -> "Comedian"; case 6 -> "Takaba Kick"; case 10 -> "Look Out"; case 15 -> "Wi-Fi"; default -> "Comedian Move " + moveSelectId; };
            case 15 -> switch (moveSelectId) { case 5 -> "Construction Overhead"; case 6 -> "Liquid Metal Speed"; case 7 -> "Liquid Metal Jump"; case 8 -> "Liquid Arrow"; case 9 -> "Needle"; case 12 -> "Liquid Metal"; case 14 -> "Insect Armor"; case 15 -> "True Sphere"; default -> "Construction Move " + moveSelectId; };
            case 16 -> switch (moveSelectId) { case 5 -> "Ice Spear"; case 6 -> "Ice Formation"; case 8 -> "Frost Calm"; case 9 -> "Icefall"; default -> "Ice Formation Move " + moveSelectId; };
            case 17 -> switch (moveSelectId) { case 5 -> "Garuda Ball"; case 6 -> "Garuda"; case 10 -> "Star Rage"; case 15 -> "Star Rage"; default -> "Star Rage Move " + moveSelectId; };
            case 19 -> switch (moveSelectId) { case 5 -> "Lightning Strike"; case 10 -> "Turn Up The Volume"; case 15 -> "Mythical Beast Amber"; case 16 -> "Ah"; case 17 -> "Energy Wave"; default -> "Mythical Beast Amber Move " + moveSelectId; };
            default -> techniqueName(techniqueId) + " Move " + moveSelectId;
        };
    }

    public static String tenShadowsWheelMoveName(int moveSelectId) {
        return switch (moveSelectId) {
            case 5 -> "Divine Dog White";
            case 6 -> "Divine Dog Black";
            case 7 -> "Divine Dog Totality";
            case 8 -> "Nue";
            case 9 -> "Great Serpent";
            case 10 -> "Toad";
            case 11 -> "Max Elephant";
            case 12 -> "Rabbit Escape";
            case 13 -> "Round Deer";
            case 14 -> "Piercing Ox";
            case 15 -> "Tiger Funeral";
            case 17 -> "Merged Beast Agito";
            case 18 -> "Eight-Handled Sword Divergent Sila Divine General Mahoraga";
            default -> "Ten Shadows Move " + moveSelectId;
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
        if (player == null || !isActiveYuta(player)) {
            return;
        }
        sanitizeVanillaHardcodedCopyState(player);
        boolean inventoryChanged = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(JujutsucraftModItems.COPIED_CURSED_TECHNIQUE.get()) || stack.is(JujutsucraftModItems.LOUDSPEAKER.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                inventoryChanged = true;
            } else if (stack.is(JujutsucraftModItems.SWORD_OKKOTSU_YUTA.get())) {
                clearVanillaCopiedNbt(stack);
            }
        }
        if (player.getMainHandItem().is(JujutsucraftModItems.LOUDSPEAKER.get())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            inventoryChanged = true;
        }
        if (player.getOffhandItem().is(JujutsucraftModItems.LOUDSPEAKER.get())) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            inventoryChanged = true;
        }
        clearVanillaCopiedNbt(player.getMainHandItem());
        clearVanillaCopiedNbt(player.getOffhandItem());
        for (ItemStack armor : player.getArmorSlots()) {
            clearVanillaCopiedNbt(armor);
        }
        if (inventoryChanged) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
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

