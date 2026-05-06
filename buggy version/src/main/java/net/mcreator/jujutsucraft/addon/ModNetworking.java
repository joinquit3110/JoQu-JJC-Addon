package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.mcreator.jujutsucraft.addon.ClientPacketHandler;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.limb.LimbSyncPacket;
import net.mcreator.jujutsucraft.addon.limb.NearDeathPacket;
import net.mcreator.jujutsucraft.addon.limb.RCTLevel3Handler;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.DomainCostUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.ChangeTechniqueTestProcedure;
import net.mcreator.jujutsucraft.procedures.KeyChangeTechniqueOnKeyPressedProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Central networking hub for addon packets. It registers every packet type, builds technique wheel payloads, synchronizes cooldown and mastery data, and processes domain mastery mutations on the server.
 */
public class ModNetworking {
    // Shared network protocol version string used when establishing the addon channel.
    private static final String PROTOCOL_VERSION = "1";
    // Persistent data key used to remember the peak value of the current technique cooldown cycle.
    private static final String DATA_KEY_TECHNIQUE_CD_MAX = "jjkbrp_technique_cd_max";
    // Persistent data key used to remember the peak value of the current combat cooldown cycle.
    private static final String DATA_KEY_COMBAT_CD_MAX = "jjkbrp_combat_cd_max";
    // SimpleChannel used for every addon packet exchanged between client and server.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation("jjkblueredpurple", "main"), () -> "1", "1"::equals, "1"::equals);
    // Monotonically increasing discriminator assigned as packet types are registered.
    private static int packetId = 0;

    // ===== CHANNEL REGISTRATION =====
    public static void register() {
        CHANNEL.registerMessage(packetId++, SelectTechniquePacket.class, SelectTechniquePacket::encode, SelectTechniquePacket::decode, SelectTechniquePacket::handle);
        CHANNEL.registerMessage(packetId++, RequestWheelPacket.class, RequestWheelPacket::encode, RequestWheelPacket::decode, RequestWheelPacket::handle);
        CHANNEL.registerMessage(packetId++, OpenWheelPacket.class, OpenWheelPacket::encode, OpenWheelPacket::decode, OpenWheelPacket::handle);
        CHANNEL.registerMessage(packetId++, CooldownSyncPacket.class, CooldownSyncPacket::encode, CooldownSyncPacket::decode, CooldownSyncPacket::handle);
        CHANNEL.registerMessage(packetId++, BlackFlashSyncPacket.class, BlackFlashSyncPacket::encode, BlackFlashSyncPacket::decode, BlackFlashSyncPacket::handle);
        CHANNEL.registerMessage(packetId++, SelectSpiritPacket.class, SelectSpiritPacket::encode, SelectSpiritPacket::decode, SelectSpiritPacket::handle);
        CHANNEL.registerMessage(packetId++, LimbSyncPacket.class, LimbSyncPacket::encode, LimbSyncPacket::decode, LimbSyncPacket::handle);
        CHANNEL.registerMessage(packetId++, NearDeathPacket.class, NearDeathPacket::encode, NearDeathPacket::decode, NearDeathPacket::handle);
        CHANNEL.registerMessage(packetId++, NearDeathCdSyncPacket.class, NearDeathCdSyncPacket::encode, NearDeathCdSyncPacket::decode, NearDeathCdSyncPacket::handle);
        CHANNEL.registerMessage(packetId++, DomainPropertyPacket.class, DomainPropertyPacket::encode, DomainPropertyPacket::decode, DomainPropertyPacket::handle);
        CHANNEL.registerMessage(packetId++, DomainMasteryOpenPacket.class, DomainMasteryOpenPacket::encode, DomainMasteryOpenPacket::decode, DomainMasteryOpenPacket::handle);
        CHANNEL.registerMessage(packetId++, DomainMasteryOpenScreenPacket.class, DomainMasteryOpenScreenPacket::encode, DomainMasteryOpenScreenPacket::decode, DomainMasteryOpenScreenPacket::handle);
        CHANNEL.registerMessage(packetId++, DomainMasterySyncPacket.class, DomainMasterySyncPacket::encode, DomainMasterySyncPacket::decode, DomainMasterySyncPacket::handle);
        CHANNEL.registerMessage(packetId++, DomainClashMultiSyncPacket.class, DomainClashMultiSyncPacket::encode, DomainClashMultiSyncPacket::decode, DomainClashMultiSyncPacket::handle);
    }

    // ===== TECHNIQUE SELECTION HELPERS =====
    private static boolean isTechniqueLocked(ServerPlayer player, int charId, double selectId) {
        return ChangeTechniqueTestProcedure.execute((LevelAccessor)player.level(), (double)player.getX(), (double)player.getY(), (double)player.getZ(), (Entity)player, (double)charId, (double)selectId);
    }

    /**
     * Applies original technique selection to the current addon state.
     * @param player player instance involved in this operation.
     * @param selectId identifier used to resolve the requested entry or state.
     */
    private static void applyOriginalTechniqueSelection(ServerPlayer player, double selectId) {
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            boolean previousNoChange = cap.noChangeTechnique;
            cap.noChangeTechnique = true;
            cap.PlayerSelectCurseTechnique = selectId;
            cap.syncPlayerVariables((Entity)player);
            KeyChangeTechniqueOnKeyPressedProcedure.execute((LevelAccessor)player.level(), (double)player.getX(), (double)player.getY(), (double)player.getZ(), (Entity)player);
            cap.noChangeTechnique = previousNoChange;
            cap.syncPlayerVariables((Entity)player);
        });
    }

    /**
     * Returns active character id for the current addon state.
     * @param vars vars used by this method.
     * @return the resolved active character id.
     */
    private static int getActiveCharacterId(JujutsucraftModVariables.PlayerVariables vars) {
        double activeTechnique = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        return (int)Math.round(activeTechnique);
    }

    /**
     * Checks whether is valid select id is true for the current addon state.
     * @param selectId identifier used to resolve the requested entry or state.
     * @return true when is valid select id succeeds; otherwise false.
     */
    private static boolean isValidSelectId(double selectId) {
        return selectId >= 0.0 && selectId <= 21.0;
    }

    /**
     * Performs as select id for this addon component.
     * @param selectId identifier used to resolve the requested entry or state.
     * @return the resulting as select id value.
     */
    private static int asSelectId(double selectId) {
        return (int)Math.round(selectId);
    }

    // ===== COOLDOWN HELPERS =====
    public static int getTechniqueCooldownTicks(ServerPlayer player) {
        return player.hasEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME.get()) ? player.getEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME.get()).getDuration() : 0;
    }

    /**
     * Returns combat cooldown ticks for the current addon state.
     * @param player player instance involved in this operation.
     * @return the resolved combat cooldown ticks.
     */
    public static int getCombatCooldownTicks(ServerPlayer player) {
        return player.hasEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get()) ? player.getEffect((MobEffect)JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get()).getDuration() : 0;
    }

    /**
     * Returns cooldown ticks for the current addon state.
     * @param player player instance involved in this operation.
     * @return the resolved cooldown ticks.
     */
    public static int getCooldownTicks(ServerPlayer player) {
        return Math.max(ModNetworking.getTechniqueCooldownTicks(player), ModNetworking.getCombatCooldownTicks(player));
    }

    /**
     * Performs capture active skill cooldown for this addon component.
     * @param player player instance involved in this operation.
     * @param vars vars used by this method.
     */
    public static void captureActiveSkillCooldown(ServerPlayer player, JujutsucraftModVariables.PlayerVariables vars) {
    }

    /**
     * Performs send cooldown sync for this addon component.
     * @param player player instance involved in this operation.
     */
    public static void sendCooldownSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int techRemaining = ModNetworking.getTechniqueCooldownTicks(player);
        int combatRemaining = ModNetworking.getCombatCooldownTicks(player);
        int techMax = (int)data.getDouble(DATA_KEY_TECHNIQUE_CD_MAX);
        int combatMax = (int)data.getDouble(DATA_KEY_COMBAT_CD_MAX);
        if (techRemaining > techMax) {
            techMax = techRemaining;
            data.putDouble(DATA_KEY_TECHNIQUE_CD_MAX, (double)techMax);
        }
        if (techRemaining <= 0) {
            techMax = 0;
            data.putDouble(DATA_KEY_TECHNIQUE_CD_MAX, 0.0);
        }
        if (combatRemaining > combatMax) {
            combatMax = combatRemaining;
            data.putDouble(DATA_KEY_COMBAT_CD_MAX, (double)combatMax);
        }
        if (combatRemaining <= 0) {
            combatMax = 0;
            data.putDouble(DATA_KEY_COMBAT_CD_MAX, 0.0);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new CooldownSyncPacket(techRemaining, techMax, combatRemaining, combatMax));
    }

    // ===== WHEEL DISPLAY HELPERS =====
    private static int computeTechniqueColor(String displayName, boolean passive, boolean physical, double selectId) {
        String n = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String string = n;
        if (n.contains("purple") || n.contains("hollow")) {
            return 11032055;
        }
        if (n.contains("blue") || n.contains("ice") || n.contains("water") || n.contains("frost")) {
            return 3900150;
        }
        if (n.contains("red") || n.contains("blood") || n.contains("fire") || n.contains("flame") || n.contains("meteor")) {
            return 0xEF4444;
        }
        if (n.contains("electric") || n.contains("lightning") || n.contains("thunder") || n.contains("kirin")) {
            return 16436245;
        }
        if (n.contains("shadow") || n.contains("shikigami") || n.contains("dog") || n.contains("serpent") || n.contains("rabbit") || n.contains("deer") || n.contains("ox") || n.contains("tiger") || n.contains("mahoraga")) {
            return 2278750;
        }
        if (n.contains("soul") || n.contains("idle transfiguration") || n.contains("mahito")) {
            return 1357990;
        }
        if (n.contains("domain") || n.contains("void") || n.contains("shrine")) {
            return 16096779;
        }
        if (n.contains("copy") || n.contains("rika")) {
            return 15485081;
        }
        if (physical) {
            return 16486972;
        }
        if (passive) {
            return 3462041;
        }
        if (selectId >= 20.0) {
            return 16096779;
        }
        return ModNetworking.generateVibrantColor(displayName, selectId);
    }

    /**
     * Performs generate vibrant color for this addon component.
     * @param displayName display name used by this method.
     * @param selectId identifier used to resolve the requested entry or state.
     * @return the resulting generate vibrant color value.
     */
    private static int generateVibrantColor(String displayName, double selectId) {
        String key = (displayName == null ? "technique" : displayName.toLowerCase(Locale.ROOT)) + "#" + (int)Math.round(selectId);
        int hash = key.hashCode();
        float hue = (hash & Integer.MAX_VALUE) % 360;
        float saturation = 0.72f;
        float value = 0.95f;
        return ModNetworking.hsvToRgb(hue, saturation, value);
    }

    /**
     * Performs hsv to rgb for this addon component.
     * @param hue hue used by this method.
     * @param saturation saturation used by this method.
     * @param value value used by this method.
     * @return the resulting hsv to rgb value.
     */
    private static int hsvToRgb(float hue, float saturation, float value) {
        float bp;
        float gp;
        float rp;
        float c = value * saturation;
        float x = c * (1.0f - Math.abs(hue / 60.0f % 2.0f - 1.0f));
        float m = value - c;
        if (hue < 60.0f) {
            rp = c;
            gp = x;
            bp = 0.0f;
        } else if (hue < 120.0f) {
            rp = x;
            gp = c;
            bp = 0.0f;
        } else if (hue < 180.0f) {
            rp = 0.0f;
            gp = c;
            bp = x;
        } else if (hue < 240.0f) {
            rp = 0.0f;
            gp = x;
            bp = c;
        } else if (hue < 300.0f) {
            rp = x;
            gp = 0.0f;
            bp = c;
        } else {
            rp = c;
            gp = 0.0f;
            bp = x;
        }
        int r = Math.min(255, Math.max(0, Math.round((rp + m) * 255.0f)));
        int g = Math.min(255, Math.max(0, Math.round((gp + m) * 255.0f)));
        int b = Math.min(255, Math.max(0, Math.round((bp + m) * 255.0f)));
        return r << 16 | g << 8 | b;
    }

    // ===== PLAYER STATE SNAPSHOTS =====
    private static PlayerSelectionSnapshot snapshot(JujutsucraftModVariables.PlayerVariables vars) {
        return new PlayerSelectionSnapshot(vars.PlayerSelectCurseTechnique, vars.PlayerSelectCurseTechniqueName, vars.PlayerSelectCurseTechniqueCost, vars.PlayerSelectCurseTechniqueCostOrgin, vars.PassiveTechnique, vars.PhysicalAttack, vars.noChangeTechnique, vars.OverlayCost, vars.OverlayCursePower);
    }

    /**
     * Performs restore for this addon component.
     * @param player player instance involved in this operation.
     * @param snap snap used by this method.
     */
    private static void restore(ServerPlayer player, PlayerSelectionSnapshot snap) {
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            cap.PlayerSelectCurseTechnique = snap.selectId();
            cap.PlayerSelectCurseTechniqueName = snap.name();
            cap.PlayerSelectCurseTechniqueCost = snap.finalCost();
            cap.PlayerSelectCurseTechniqueCostOrgin = snap.baseCost();
            cap.PassiveTechnique = snap.passive();
            cap.PhysicalAttack = snap.physical();
            cap.noChangeTechnique = snap.noChangeTechnique();
            cap.OverlayCost = snap.overlayCost();
            cap.OverlayCursePower = snap.overlayCursePower();
            cap.syncPlayerVariables((Entity)player);
        });
    }

    // ===== PER-SKILL COOLDOWN LOOKUPS =====
    private static int getPerSkillCooldownTicks(ServerPlayer player, int charId, int selectId) {
        String effectId = ModNetworking.getPerSkillEffectId(charId, selectId);
        if (effectId == null) {
            return -1;
        }
        MobEffect effect = (MobEffect)ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft_plus", effectId));
        if (effect == null) {
            return -1;
        }
        if (!player.hasEffect(effect)) {
            return 0;
        }
        return player.getEffect(effect).getDuration();
    }

    /**
     * Returns per skill effect id for the current addon state.
     * @param charId identifier used to resolve the requested entry or state.
     * @param selectId identifier used to resolve the requested entry or state.
     * @return the resolved per skill effect id.
     */
    private static String getPerSkillEffectId(int charId, int selectId) {
        if (charId == 2) {
            String gojoEffect = null;
            switch (selectId) {
                case 6: {
                    gojoEffect = "blue";
                    break;
                }
                case 7: {
                    gojoEffect = "red";
                    break;
                }
                case 8: {
                    gojoEffect = "blue_strike";
                    break;
                }
                case 15: {
                    gojoEffect = "purple";
                }
            }
            if (gojoEffect != null) {
                return gojoEffect;
            }
        }
        if (charId == 1) {
            String sukunaEffect = null;
            switch (selectId) {
                case 5: {
                    sukunaEffect = "dismantle";
                    break;
                }
                case 6: {
                    sukunaEffect = "cleave";
                    break;
                }
                case 7: {
                    sukunaEffect = "fuga";
                }
            }
            if (sukunaEffect != null) {
                return sukunaEffect;
            }
        }
        return switch (selectId) {
            case 5 -> "innate_cooldown";
            case 6 -> "extension_cooldown_2";
            case 7 -> "extension_cooldown_3";
            case 8 -> "extension_cooldown_4";
            case 9 -> "extension_cooldown_5";
            case 10 -> "extension_cooldown_6";
            case 11 -> "extension_cooldown_7";
            case 12 -> "extension_cooldown_8";
            case 13 -> "extension_cooldown_9";
            case 14 -> "extension_cooldown_10";
            case 15 -> "maximum";
            case 16 -> "extension_cooldown_11";
            case 17 -> "extension_cooldown_12";
            case 18 -> "extension_cooldown_13";
            case 19 -> "extension_cooldown_14";
            default -> null;
        };
    }

    // ===== ADVANCEMENT AND LOCK HELPERS =====
    private static boolean hasAdvancement(ServerPlayer player, ResourceLocation id) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(id);
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether has open barrier advancement is true for the current addon state.
     * @param player player instance involved in this operation.
     * @return true when has open barrier advancement succeeds; otherwise false.
     */
    private static boolean hasOpenBarrierAdvancement(ServerPlayer player) {
        return ModNetworking.hasAdvancement(player, new ResourceLocation("jujutsucraft", "mastery_open_barrier_type_domain"));
    }

    /**
     * Checks whether has domain expansion advancement is true for the current addon state.
     * @param player player instance involved in this operation.
     * @return true when has domain expansion advancement succeeds; otherwise false.
     */
    private static boolean hasDomainExpansionAdvancement(ServerPlayer player) {
        return ModNetworking.hasAdvancement(player, new ResourceLocation("jujutsucraft", "mastery_domain_expansion"));
    }

    /**
     * Checks whether is domain mastery mutation operation is true for the current addon state.
     * @param operation operation used by this method.
     * @return true when is domain mastery mutation operation succeeds; otherwise false.
     */
    private static boolean isDomainMasteryMutationOperation(int operation) {
        return operation == 0 || operation == 1 || operation == 2 || operation == 3 || operation == 4 || operation == 5 || operation == 6;
    }

    /**
     * Rejects property mutation packets whenever gameplay state says mastery editing should be locked.
     * @param player player instance involved in this operation.
     * @param operation operation used by this method.
     * @return true when reject locked domain mastery mutation succeeds; otherwise false.
     */
    private static boolean rejectLockedDomainMasteryMutation(ServerPlayer player, int operation) {
        // Only mutation operations are blocked here; informational packets can still pass even when the player is combat locked.
        if (player == null || !ModNetworking.isDomainMasteryMutationOperation(operation)) {
            return false;
        }
        if (!DomainAddonUtils.isDomainMasteryMutationLocked((LivingEntity)player)) {
            return false;
        }
        player.displayClientMessage(ModNetworking.domainMsgError(DomainAddonUtils.getDomainMasteryMutationLockReason((LivingEntity)player)), false);
        return true;
    }

    /**
     * Performs domain msg error for this addon component.
     * @param text text used by this method.
     * @return the resulting domain msg error value.
     */
    private static Component domainMsgError(String text) {
        return Component.literal((String)"\u2716 ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Mastery] ").withStyle(ChatFormatting.DARK_RED)).append((Component)Component.literal((String)text).withStyle(ChatFormatting.RED));
    }

    /**
     * Performs domain msg info for this addon component.
     * @param icon icon used by this method.
     * @param iconColor icon color used by this method.
     * @param text text used by this method.
     * @return the resulting domain msg info value.
     */
    private static Component domainMsgInfo(String icon, ChatFormatting iconColor, String text) {
        return Component.literal((String)(icon + " ")).withStyle(new ChatFormatting[]{iconColor, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Mastery] ").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)text).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Performs domain msg success for this addon component.
     * @param icon icon used by this method.
     * @param iconColor icon color used by this method.
     * @param text text used by this method.
     * @return the resulting domain msg success value.
     */
    private static Component domainMsgSuccess(String icon, ChatFormatting iconColor, String text) {
        return Component.literal((String)(icon + " ")).withStyle(new ChatFormatting[]{iconColor, ChatFormatting.BOLD}).append((Component)Component.literal((String)"[Domain Mastery] ").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)text).withStyle(ChatFormatting.WHITE));
    }

    /**
     * Performs domain prop label for this addon component.
     * @param prop property identifier involved in this operation.
     * @return the resulting domain prop label value.
     */
    private static String domainPropLabel(DomainMasteryProperties prop) {
        String[] words = prop.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    // ===== SKILL WHEEL PAYLOAD BUILDERS =====
    private static List<WheelTechniqueEntry> buildWheelEntries(ServerPlayer player, int charId) {
        JujutsucraftModVariables.PlayerVariables baseVars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        PlayerSelectionSnapshot baseline = ModNetworking.snapshot(baseVars);
        ArrayList<WheelTechniqueEntry> entries = new ArrayList<WheelTechniqueEntry>();
        ArrayList<Double> seenIds = new ArrayList<Double>();
        // Probe every vanilla select id slot so the wheel can discover the actual techniques exposed by the current character without maintaining a second hard-coded list.
        for (int i = 0; i <= 21; ++i) {
            String displayName;
            double selectId = i;
            ModNetworking.restore(player, baseline);
            ModNetworking.applyOriginalTechniqueSelection(player, selectId);
            JujutsucraftModVariables.PlayerVariables after = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
            double resolvedId = after.PlayerSelectCurseTechnique;
            if (!ModNetworking.isValidSelectId(resolvedId) || seenIds.stream().anyMatch(v -> Math.abs(v - resolvedId) < 0.001) || (displayName = after.PlayerSelectCurseTechniqueName) == null || displayName.isBlank() || "-----".equals(displayName)) continue;
            int sid = (int)Math.round(resolvedId);
            int cdRemaining = 0;
            int cdMax = 0;
            if (after.PhysicalAttack && sid <= 2) {
                cdRemaining = ModNetworking.getCombatCooldownTicks(player);
            } else {
                // Prefer per-skill cooldown effects when they exist so the wheel can gray out only the entry that is actually locked.
                int perSkill = ModNetworking.getPerSkillCooldownTicks(player, charId, sid);
                if (perSkill >= 0) {
                    cdRemaining = perSkill;
                } else {
                    cdRemaining = after.PhysicalAttack ? ModNetworking.getCombatCooldownTicks(player) : ModNetworking.getTechniqueCooldownTicks(player);
                    int n = cdRemaining;
                }
            }
            if (cdRemaining <= 0 && sid > 2 && !after.PhysicalAttack && player.hasEffect((MobEffect)JujutsucraftModMobEffects.UNSTABLE.get())) {
                cdRemaining = player.getEffect((MobEffect)JujutsucraftModMobEffects.UNSTABLE.get()).getDuration();
            }
            if (cdRemaining > 0) {
                String maxKey = "jjkbrp_cd_max_" + sid;
                int storedMax = player.getPersistentData().getInt(maxKey);
                if (cdRemaining > storedMax) {
                    storedMax = cdRemaining;
                }
                player.getPersistentData().putInt(maxKey, storedMax);
                cdMax = storedMax;
            }
            double entryFinalCost = after.PlayerSelectCurseTechniqueCost;
            double entryBaseCost = after.PlayerSelectCurseTechniqueCostOrgin;
            int domainForm = -1;
            double domainMultiplier = 0.0;
            // Domain techniques are displayed with their effective form-adjusted costs instead of the raw base value shown by the original selection state.
            if (DomainCostUtils.isDomainTechniqueSelected(after)) {
                domainForm = DomainCostUtils.resolveEffectiveForm((Player)player);
                domainMultiplier = DomainCostUtils.formMultiplier(domainForm);
                entryBaseCost = DomainCostUtils.resolveTechniqueBaseCost((Player)player, after);
                entryFinalCost = DomainCostUtils.resolveExpectedDomainCastCost((Player)player, after);
            }
            entries.add(new WheelTechniqueEntry(resolvedId, displayName, entryFinalCost, entryBaseCost, ModNetworking.computeTechniqueColor(displayName, after.PassiveTechnique, after.PhysicalAttack, resolvedId), after.PassiveTechnique, after.PhysicalAttack, domainForm, domainMultiplier, cdRemaining, cdMax));
            seenIds.add(resolvedId);
        }
        ModNetworking.restore(player, baseline);
        entries.sort(Comparator.comparingDouble(WheelTechniqueEntry::selectId));
        if (entries.isEmpty()) {
            entries.add(new WheelTechniqueEntry(baseline.selectId(), baseline.name() == null || baseline.name().isBlank() ? "Technique" : baseline.name(), baseline.finalCost(), baseline.baseCost(), ModNetworking.computeTechniqueColor(baseline.name(), baseline.passive(), baseline.physical(), baseline.selectId()), baseline.passive(), baseline.physical(), -1, 0.0, 0, 0));
        }
        return entries;
    }

    /**
     * Performs detect spirit grade for this addon component.
     * @param name name used by this method.
     * @return the resulting detect spirit grade value.
     */
    private static int detectSpiritGrade(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("special grade") || lower.contains("grade 0")) {
            return 0;
        }
        if (lower.contains("semi grade 1") || lower.contains("semi-grade 1")) {
            return 1;
        }
        if (lower.contains("grade 1")) {
            return 1;
        }
        if (lower.contains("grade 2")) {
            return 2;
        }
        if (lower.contains("grade 3")) {
            return 3;
        }
        if (lower.contains("grade 4")) {
            return 4;
        }
        return 0;
    }

    /**
     * Returns spirit grade color for the current addon state.
     * @param grade grade used by this method.
     * @return the resolved spirit grade color.
     */
    private static int getSpiritGradeColor(int grade) {
        return switch (grade) {
            case 0 -> 13127872;
            case 1 -> 13934615;
            case 2 -> 4886745;
            default -> 7048811;
        };
    }

    /**
     * Builds the multi-page wheel payload for Geto, separating normal entries from stored cursed spirits by grade.
     * @param player player instance involved in this operation.
     * @param charId identifier used to resolve the requested entry or state.
     * @return the resulting build geto pages value.
     */
    private static List<List<WheelTechniqueEntry>> buildGetoPages(ServerPlayer player, int charId) {
        String displayName;
        ArrayList<WheelTechniqueEntry> page;
        String key;
        double yPos;
        ArrayList<List<WheelTechniqueEntry>> pages = new ArrayList<List<WheelTechniqueEntry>>();
        List<WheelTechniqueEntry> normalEntries = ModNetworking.buildWheelEntries(player, charId);
        normalEntries.removeIf(e -> {
            int sid = (int)Math.round(e.selectId());
            return sid >= 11 && sid <= 13;
        });
        pages.add(normalEntries);
        CompoundTag data = player.getPersistentData();
        ArrayList<SpiritData> allSpirits = new ArrayList<SpiritData>();
        // Geto spirit pages are rebuilt by walking the stored manipulation slots until the first empty sentinel entry is encountered.
        for (int n = 1; n < 10000 && (yPos = data.getDouble(key = "data_cursed_spirit_manipulation" + n)) != 0.0; ++n) {
            String name = data.getString(key + "_name");
            double count = data.getDouble(key + "_num");
            if (name.isEmpty() || count <= 0.0) continue;
            allSpirits.add(new SpiritData(n, name, (int)Math.round(count), ModNetworking.detectSpiritGrade(name)));
        }
        ArrayList<SpiritData> lowerGrade = new ArrayList<SpiritData>();
        ArrayList<SpiritData> upperGrade = new ArrayList<SpiritData>();
        for (SpiritData s : allSpirits) {
            if (s.grade >= 2) {
                lowerGrade.add(s);
                continue;
            }
            upperGrade.add(s);
        }
        if (!lowerGrade.isEmpty()) {
            page = new ArrayList<WheelTechniqueEntry>();
            for (SpiritData s : lowerGrade) {
                displayName = s.count > 1 ? s.name + " x" + s.count : s.name;
                page.add(new WheelTechniqueEntry(100 + s.slot, displayName, 0.0, 0.0, ModNetworking.getSpiritGradeColor(s.grade), false, false, -1, 0.0, 0, 0));
                if (page.size() < 12) continue;
                pages.add(page);
                page = new ArrayList();
            }
            if (!page.isEmpty()) {
                pages.add(page);
            }
        }
        if (!upperGrade.isEmpty()) {
            page = new ArrayList();
            for (SpiritData s : upperGrade) {
                displayName = s.count > 1 ? s.name + " x" + s.count : s.name;
                page.add(new WheelTechniqueEntry(100 + s.slot, displayName, 0.0, 0.0, ModNetworking.getSpiritGradeColor(s.grade), false, false, -1, 0.0, 0, 0));
                if (page.size() < 12) continue;
                pages.add(page);
                page = new ArrayList();
            }
            if (!page.isEmpty()) {
                pages.add(page);
            }
        }
        return pages;
    }

    // ===== CLIENT SYNC SENDERS =====
    public static void sendBlackFlashSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        float pct = (float)data.getDouble("addon_bf_chance");
        boolean mastery = data.getBoolean("addon_bf_mastery");
        boolean charging = data.getBoolean("addon_bf_charging");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new BlackFlashSyncPacket(pct, mastery, charging));
    }

    /**
     * Performs send near death cd sync for this addon component.
     * @param player player instance involved in this operation.
     */
    public static void sendNearDeathCdSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int cd = data.getInt("jjkbrp_near_death_cd");
        boolean rctL3 = RCTLevel3Handler.hasAdvancement(player, "jjkblueredpurple:rct_level_3");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new NearDeathCdSyncPacket(cd, 6000, rctL3));
    }

    /**
     * Pushes the current domain mastery capability snapshot to the specified client after applying open-barrier unlock state.
     * @param player player instance involved in this operation.
     * @param data data used by this method.
     */
    public static void syncDomainMasteryToClient(ServerPlayer player, DomainMasteryData data) {
        boolean hasOpenAdvancement = ModNetworking.hasOpenBarrierAdvancement(player);
        data.setOpenBarrierAdvancementUnlocked(hasOpenAdvancement);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new DomainMasterySyncPacket(data, hasOpenAdvancement));
    }

    /**
     * Server-bound packet that requests a specific technique selection from the skill wheel.
     */
    // ===== PACKET TYPES =====
    public static class SelectTechniquePacket {
        // Requested technique select id carried by this packet.
        private final double selectId;

        /**
         * Creates a new select technique packet instance and initializes its addon state.
         * @param selectId identifier used to resolve the requested entry or state.
         */
        public SelectTechniquePacket(double selectId) {
            this.selectId = selectId;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(SelectTechniquePacket pkt, FriendlyByteBuf buf) {
            buf.writeDouble(pkt.selectId);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static SelectTechniquePacket decode(FriendlyByteBuf buf) {
            return new SelectTechniquePacket(buf.readDouble());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(SelectTechniquePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
                if (vars.noChangeTechnique) {
                    return;
                }
                int charId = ModNetworking.getActiveCharacterId(vars);
                if (ModNetworking.isTechniqueLocked(player, charId, pkt.selectId)) {
                    return;
                }
                ModNetworking.applyOriginalTechniqueSelection(player, pkt.selectId);
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Server-bound packet asking the server to build and send the current skill wheel payload.
     */
    public static class RequestWheelPacket {
        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(RequestWheelPacket pkt, FriendlyByteBuf buf) {
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static RequestWheelPacket decode(FriendlyByteBuf buf) {
            return new RequestWheelPacket();
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(RequestWheelPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
                if (vars.noChangeTechnique) {
                    return;
                }
                int charId = ModNetworking.getActiveCharacterId(vars);
                double currentSelect = vars.PlayerSelectCurseTechnique;
                if (charId == 18) {
                    List<List<WheelTechniqueEntry>> pages = ModNetworking.buildGetoPages(player, charId);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new OpenWheelPacket(pages, currentSelect));
                } else {
                    List<WheelTechniqueEntry> entries = ModNetworking.buildWheelEntries(player, charId);
                    ArrayList<List<WheelTechniqueEntry>> singlePage = new ArrayList<List<WheelTechniqueEntry>>();
                    singlePage.add(entries);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)new OpenWheelPacket(singlePage, currentSelect));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet containing one or more wheel pages plus the currently selected technique id.
     */
    public static class OpenWheelPacket {
        final List<List<WheelTechniqueEntry>> pages;
        // Currently selected technique id shown when the skill wheel opens.
        private final double currentSelect;

        /**
         * Creates a new open wheel packet instance and initializes its addon state.
         * @param pages pages used by this method.
         * @param currentSelect current select used by this method.
         */
        public OpenWheelPacket(List<List<WheelTechniqueEntry>> pages, double currentSelect) {
            this.pages = pages;
            this.currentSelect = currentSelect;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(OpenWheelPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.pages.size());
            for (List<WheelTechniqueEntry> page : pkt.pages) {
                buf.writeInt(page.size());
                for (WheelTechniqueEntry entry : page) {
                    buf.writeDouble(entry.selectId());
                    buf.writeUtf(entry.displayName(), 256);
                    buf.writeDouble(entry.finalCost());
                    buf.writeDouble(entry.baseCost());
                    buf.writeInt(entry.color());
                    buf.writeBoolean(entry.passive());
                    buf.writeBoolean(entry.physical());
                    buf.writeInt(entry.domainForm());
                    buf.writeDouble(entry.domainMultiplier());
                    buf.writeInt(entry.cooldownRemainingTicks());
                    buf.writeInt(entry.cooldownMaxTicks());
                }
            }
            buf.writeDouble(pkt.currentSelect);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static OpenWheelPacket decode(FriendlyByteBuf buf) {
            int numPages = buf.readInt();
            ArrayList<List<WheelTechniqueEntry>> pages = new ArrayList<List<WheelTechniqueEntry>>();
            for (int p = 0; p < numPages; ++p) {
                int size = buf.readInt();
                ArrayList<WheelTechniqueEntry> page = new ArrayList<WheelTechniqueEntry>();
                for (int i = 0; i < size; ++i) {
                    page.add(new WheelTechniqueEntry(buf.readDouble(), buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readDouble(), buf.readInt(), buf.readInt()));
                }
                pages.add(page);
            }
            return new OpenWheelPacket(pages, buf.readDouble());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(OpenWheelPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.openSkillWheel(pkt.pages, pkt.currentSelect)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet that synchronizes technique and combat cooldown progress.
     */
    public static class CooldownSyncPacket {
        // Remaining technique cooldown ticks sent to the client.
        private final int techRemaining;
        // Maximum technique cooldown ticks used to render normalized client progress.
        private final int techMax;
        // Remaining combat cooldown ticks sent to the client.
        private final int combatRemaining;
        // Maximum combat cooldown ticks used to render normalized client progress.
        private final int combatMax;

        /**
         * Creates a new cooldown sync packet instance and initializes its addon state.
         * @param techRemaining tech remaining used by this method.
         * @param techMax tech max used by this method.
         * @param combatRemaining combat remaining used by this method.
         * @param combatMax combat max used by this method.
         */
        public CooldownSyncPacket(int techRemaining, int techMax, int combatRemaining, int combatMax) {
            this.techRemaining = techRemaining;
            this.techMax = techMax;
            this.combatRemaining = combatRemaining;
            this.combatMax = combatMax;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(CooldownSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.techRemaining);
            buf.writeInt(pkt.techMax);
            buf.writeInt(pkt.combatRemaining);
            buf.writeInt(pkt.combatMax);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static CooldownSyncPacket decode(FriendlyByteBuf buf) {
            return new CooldownSyncPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(CooldownSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.updateCooldowns(pkt.techRemaining, pkt.techMax, pkt.combatRemaining, pkt.combatMax)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet that synchronizes Black Flash HUD values.
     */
    public static class BlackFlashSyncPacket {
        // Black Flash percentage sent to the client.
        private final float bfPercent;
        // Whether Black Flash mastery is unlocked in the sync packet.
        private final boolean mastery;
        // Whether Black Flash charge state is active in the sync packet.
        private final boolean charging;

        /**
         * Creates a new black flash sync packet instance and initializes its addon state.
         * @param bfPercent bf percent used by this method.
         * @param mastery mastery used by this method.
         * @param charging charging used by this method.
         */
        public BlackFlashSyncPacket(float bfPercent, boolean mastery, boolean charging) {
            this.bfPercent = bfPercent;
            this.mastery = mastery;
            this.charging = charging;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(BlackFlashSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeFloat(pkt.bfPercent);
            buf.writeBoolean(pkt.mastery);
            buf.writeBoolean(pkt.charging);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static BlackFlashSyncPacket decode(FriendlyByteBuf buf) {
            return new BlackFlashSyncPacket(buf.readFloat(), buf.readBoolean(), buf.readBoolean());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(BlackFlashSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.updateBlackFlash(pkt.bfPercent, pkt.mastery, pkt.charging)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Server-bound packet used when the skill wheel selects a stored cursed spirit instead of a normal technique.
     */
    public static class SelectSpiritPacket {
        // Stored cursed spirit slot selected from the multi-page Geto wheel.
        private final int spiritSlot;

        /**
         * Creates a new select spirit packet instance and initializes its addon state.
         * @param spiritSlot spirit slot used by this method.
         */
        public SelectSpiritPacket(int spiritSlot) {
            this.spiritSlot = spiritSlot;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(SelectSpiritPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.spiritSlot);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static SelectSpiritPacket decode(FriendlyByteBuf buf) {
            return new SelectSpiritPacket(buf.readInt());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(SelectSpiritPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                String key;
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                CompoundTag data = player.getPersistentData();
                if (data.getDouble(key = "data_cursed_spirit_manipulation" + pkt.spiritSlot) == 0.0) {
                    return;
                }
                String name = data.getString(key + "_name");
                double count = data.getDouble(key + "_num");
                String displayName = name + " \u00d7" + (int)Math.round(count);
                player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
                    cap.PlayerSelectCurseTechnique = 12.0;
                    cap.PlayerSelectCurseTechniqueName = displayName;
                    cap.syncPlayerVariables((Entity)player);
                });
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet that synchronizes near-death cooldown progress and unlock state.
     */
    public record NearDeathCdSyncPacket(int cdRemaining, int cdMax, boolean rctLevel3Unlocked) {
        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(NearDeathCdSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.cdRemaining);
            buf.writeInt(pkt.cdMax);
            buf.writeBoolean(pkt.rctLevel3Unlocked);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static NearDeathCdSyncPacket decode(FriendlyByteBuf buf) {
            return new NearDeathCdSyncPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctxSupplier context data supplied by the current callback or network pipeline.
         */
        public static void handle(NearDeathCdSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.updateNearDeathCooldown(pkt.cdRemaining, pkt.cdMax, pkt.rctLevel3Unlocked)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Server-bound packet that upgrades, refunds, resets, or changes domain mastery properties and forms.
     */
    public static class DomainPropertyPacket {
        // Named addon constant for op upgrade.
        public static final int OP_UPGRADE = 0;
        // Named addon constant for op refund.
        public static final int OP_REFUND = 1;
        // Named addon constant for op reset all.
        public static final int OP_RESET_ALL = 2;
        // Named addon constant for op set form.
        public static final int OP_SET_FORM = 3;
        // Named addon constant for op cycle form.
        public static final int OP_CYCLE_FORM = 4;
        // Named addon constant for op negative decrease.
        public static final int OP_NEGATIVE_DECREASE = 5;
        // Named addon constant for op negative increase.
        public static final int OP_NEGATIVE_INCREASE = 6;
        // Encoded domain mastery mutation operation handled by the server packet.
        private final int operation;
        // Runtime index used for property index.
        private final int propertyIndex;

        /**
         * Creates a new domain property packet instance and initializes its addon state.
         * @param operation operation used by this method.
         * @param propertyIndex property index used by this method.
         */
        public DomainPropertyPacket(int operation, int propertyIndex) {
            this.operation = operation;
            this.propertyIndex = propertyIndex;
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(DomainPropertyPacket pkt, FriendlyByteBuf buf) {
            buf.writeByte(pkt.operation);
            buf.writeByte(pkt.propertyIndex);
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static DomainPropertyPacket decode(FriendlyByteBuf buf) {
            return new DomainPropertyPacket(buf.readByte(), buf.readByte());
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctx context data supplied by the current callback or network pipeline.
         */
        public static void handle(DomainPropertyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ((NetworkEvent.Context)ctx.get()).getSender();
                if (sender == null) {
                    return;
                }
                if (ModNetworking.rejectLockedDomainMasteryMutation(sender, pkt.operation)) {
                    return;
                }
                sender.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
                    boolean hasOpenAdvancement = ModNetworking.hasOpenBarrierAdvancement(sender);
                    data.setOpenBarrierAdvancementUnlocked(hasOpenAdvancement);
                    switch (pkt.operation) {
                        case 0: {
                            if (pkt.propertyIndex < 0 || pkt.propertyIndex >= DomainMasteryProperties.values().length) break;
                            DomainMasteryProperties prop = DomainMasteryProperties.values()[pkt.propertyIndex];
                            if (data.getDomainMasteryLevel() < prop.unlockLevel()) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Property locked \u2022 Reach Domain Mastery Lv." + prop.unlockLevel()), false);
                                return;
                            }
                            if (!data.upgradeProperty(prop)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Not enough Property Points or property is currently negative"), false);
                                return;
                            }
                            sender.displayClientMessage(ModNetworking.domainMsgSuccess("\u25b2", ChatFormatting.GREEN, ModNetworking.domainPropLabel(prop) + " -> Lv." + data.getPropertyLevel(prop) + " (" + prop.formatLevelValue(data.getPropertyLevel(prop)) + ")"), false);
                            break;
                        }
                        case 1: {
                            if (pkt.propertyIndex < 0 || pkt.propertyIndex >= DomainMasteryProperties.values().length) break;
                            DomainMasteryProperties prop = DomainMasteryProperties.values()[pkt.propertyIndex];
                            if (!data.downgradeProperty(prop)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Property already at Lv.0 or currently negative"), false);
                                return;
                            }
                            sender.displayClientMessage(ModNetworking.domainMsgInfo("\u21ba", ChatFormatting.GOLD, ModNetworking.domainPropLabel(prop) + " refunded \u2022 +" + prop.getPointCost() + " Point(s)"), false);
                            break;
                        }
                        case 2: {
                            int refunded = data.getDomainPropertyPoints();
                            data.refundAllProperties();
                            int gained = data.getDomainPropertyPoints();
                            sender.displayClientMessage(ModNetworking.domainMsgInfo("\u2727", ChatFormatting.AQUA, "All properties and negative modify reset \u2022 +" + (gained - refunded) + " Property Points restored"), false);
                            break;
                        }
                        case 3: {
                            if (!ModNetworking.hasDomainExpansionAdvancement(sender)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Requires the Domain Expansion achievement"), false);
                                break;
                            }
                            int form = Math.max(0, Math.min(2, pkt.propertyIndex));
                            int level = data.getDomainMasteryLevel();
                            if (form == 1 && !DomainMasteryData.isClosedFormUnlocked(level)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Closed Domain requires Domain Mastery Lv.1"), false);
                                break;
                            }
                            if (form == 2 && !DomainMasteryData.isOpenFormUnlocked(level, hasOpenAdvancement)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Open Domain requires Domain Mastery Lv.5 and the Open Domain achievement"), false);
                                break;
                            }
                            data.setDomainTypeSelected(form, hasOpenAdvancement);
                            sender.displayClientMessage(ModNetworking.domainMsgSuccess("\u25c8", ChatFormatting.LIGHT_PURPLE, "Domain Form -> " + data.getDomainFormName()), false);
                            break;
                        }
                        case 4: {
                            int next;
                            if (!ModNetworking.hasDomainExpansionAdvancement(sender)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Requires the Domain Expansion achievement"), false);
                                break;
                            }
                            int level = data.getDomainMasteryLevel();
                            boolean hasOpen = DomainMasteryData.isOpenFormUnlocked(level, hasOpenAdvancement);
                            int current = DomainMasteryData.sanitizeFormSelection(data.getDomainTypeSelected(), level, hasOpenAdvancement);
                            if (!DomainMasteryData.isClosedFormUnlocked(level)) {
                                next = 0;
                            } else if (hasOpen) {
                                next = switch (current) {
                                    case 0 -> 1;
                                    case 1 -> 2;
                                    default -> 0;
                                };
                            } else {
                                next = current == 0 ? 1 : 0;
                            }
                            data.setDomainTypeSelected(next, hasOpenAdvancement);
                            sender.displayClientMessage(ModNetworking.domainMsgSuccess("\u25c8", ChatFormatting.LIGHT_PURPLE, "Domain Form -> " + data.getDomainFormName()), false);
                            break;
                        }
                        case 5: {
                            if (pkt.propertyIndex < 0 || pkt.propertyIndex >= DomainMasteryProperties.values().length) break;
                            DomainMasteryProperties prop = DomainMasteryProperties.values()[pkt.propertyIndex];
                            if (data.getDomainMasteryLevel() < 5) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Negative Modify unlocks at Domain Mastery Lv.5"), false);
                                return;
                            }
                            if (!prop.supportsNegativeModify()) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Only Duration, Radius, and Clash Power can become negative"), false);
                                return;
                            }
                            if (!data.canSetNegative(prop)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Another property is already negative"), false);
                                return;
                            }
                            if (data.getPropertyLevel(prop) > 0) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Refund this property to Lv.0 before applying Negative Modify"), false);
                                return;
                            }
                            if (!data.decreaseNegative(prop)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Negative Modify is already at minimum Lv.-5"), false);
                                return;
                            }
                            int negativeLevel = data.getNegativeLevel();
                            sender.displayClientMessage(ModNetworking.domainMsgInfo("\u2212", ChatFormatting.RED, ModNetworking.domainPropLabel(prop) + " -> Lv." + negativeLevel + " \u2022 " + prop.formatNegativeValue(Math.abs(negativeLevel)) + " \u2022 +1 Property Point"), false);
                            break;
                        }
                        case 6: {
                            if (pkt.propertyIndex < 0 || pkt.propertyIndex >= DomainMasteryProperties.values().length) break;
                            DomainMasteryProperties prop = DomainMasteryProperties.values()[pkt.propertyIndex];
                            if (!data.increaseNegative(prop)) {
                                sender.displayClientMessage(ModNetworking.domainMsgError("Not enough Property Points to reduce negative modify"), false);
                                return;
                            }
                            if (data.isNegativeProperty(prop)) {
                                int negativeLevel = data.getNegativeLevel();
                                sender.displayClientMessage(ModNetworking.domainMsgSuccess("+", ChatFormatting.GREEN, ModNetworking.domainPropLabel(prop) + " -> Lv." + negativeLevel + " \u2022 " + prop.formatNegativeValue(Math.abs(negativeLevel))), false);
                                break;
                            }
                            sender.displayClientMessage(ModNetworking.domainMsgSuccess("+", ChatFormatting.GREEN, ModNetworking.domainPropLabel(prop) + " negative modify cleared"), false);
                        }
                    }
                    data.syncToClient(sender);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Server-bound packet requesting that the server validate and open the domain mastery screen.
     */
    public static class DomainMasteryOpenPacket {
        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(DomainMasteryOpenPacket pkt, FriendlyByteBuf buf) {
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static DomainMasteryOpenPacket decode(FriendlyByteBuf buf) {
            return new DomainMasteryOpenPacket();
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctx context data supplied by the current callback or network pipeline.
         */
        public static void handle(DomainMasteryOpenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ((NetworkEvent.Context)ctx.get()).getSender();
                if (sender == null) {
                    return;
                }
                if (!ModNetworking.hasDomainExpansionAdvancement(sender)) {
                    sender.displayClientMessage(ModNetworking.domainMsgError("Requires the Domain Expansion achievement"), false);
                    return;
                }
                sender.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY).ifPresent(data -> {
                    data.syncToClient(sender);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), (Object)new DomainMasteryOpenScreenPacket());
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet that opens the domain mastery screen after server validation succeeds.
     */
    public static class DomainMasteryOpenScreenPacket {
        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(DomainMasteryOpenScreenPacket pkt, FriendlyByteBuf buf) {
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static DomainMasteryOpenScreenPacket decode(FriendlyByteBuf buf) {
            return new DomainMasteryOpenScreenPacket();
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctx context data supplied by the current callback or network pipeline.
         */
        public static void handle(DomainMasteryOpenScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.openDomainMasteryScreen()));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Client-bound packet that transfers the full domain mastery capability snapshot.
     */
    public static class DomainMasterySyncPacket {
        // Domain mastery XP value transmitted to the client.
        private final double xp;
        // Domain mastery level transmitted to the client.
        private final int level;
        // Selected domain form transmitted to the client.
        private final int form;
        // Remaining property points transmitted to the client.
        private final int points;
        // Per-property level array transmitted to the client in enum order.
        private final int[] propLevels;
        // Name of the property currently assigned as the negative modify target.
        private final String negativeProperty;
        // Current negative modify level transmitted to the client.
        private final int negativeLevel;
        // Whether the server confirmed the open-barrier advancement for the receiving player.
        private final boolean hasOpenBarrierAdvancement;

        /**
         * Creates a new domain mastery sync packet instance and initializes its addon state.
         * @param data data used by this method.
         * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
         */
        public DomainMasterySyncPacket(DomainMasteryData data, boolean hasOpenBarrierAdvancement) {
            this.xp = data.getDomainXP();
            this.level = data.getDomainMasteryLevel();
            this.form = data.getDomainTypeSelected();
            this.points = data.getDomainPropertyPoints();
            this.negativeProperty = data.getNegativeProperty();
            this.negativeLevel = data.getNegativeLevel();
            this.hasOpenBarrierAdvancement = hasOpenBarrierAdvancement;
            this.propLevels = new int[DomainMasteryProperties.values().length];
            for (int i = 0; i < this.propLevels.length; ++i) {
                this.propLevels[i] = data.getPropertyLevel(DomainMasteryProperties.values()[i]);
            }
        }

        /**
         * Writes this packet payload into the provided network buffer.
         * @param pkt pkt used by this method.
         * @param buf serialized data container used by this operation.
         */
        public static void encode(DomainMasterySyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeDouble(pkt.xp);
            buf.writeByte(pkt.level);
            buf.writeByte(pkt.form);
            buf.writeVarInt(pkt.points);
            buf.writeUtf(pkt.negativeProperty);
            buf.writeInt(pkt.negativeLevel);
            buf.writeBoolean(pkt.hasOpenBarrierAdvancement);
            buf.writeByte(pkt.propLevels.length);
            for (int lvl : pkt.propLevels) {
                buf.writeByte(lvl);
            }
        }

        /**
         * Reads this packet payload from the provided network buffer and rebuilds the packet instance.
         * @param buf serialized data container used by this operation.
         * @return the resulting decode value.
         */
        public static DomainMasterySyncPacket decode(FriendlyByteBuf buf) {
            double xp = buf.readDouble();
            byte level = buf.readByte();
            byte form = buf.readByte();
            int points = buf.readVarInt();
            String negativeProperty = buf.readUtf();
            int negativeLevel = buf.readInt();
            boolean hasOpenBarrierAdvancement = buf.readBoolean();
            int count = buf.readByte();
            int[] levels = new int[count];
            for (int i = 0; i < count; ++i) {
                levels[i] = buf.readByte();
            }
            return new DomainMasterySyncPacket(xp, level, form, points, levels, negativeProperty, negativeLevel, hasOpenBarrierAdvancement);
        }

        /**
         * Creates a new domain mastery sync packet instance and initializes its addon state.
         * @param xp xp used by this method.
         * @param level level value used by this operation.
         * @param form form used by this method.
         * @param points points used by this method.
         * @param levels levels used by this method.
         * @param negativeProperty property identifier involved in this operation.
         * @param negativeLevel level value used by this operation.
         * @param hasOpenBarrierAdvancement has open barrier advancement used by this method.
         */
        private DomainMasterySyncPacket(double xp, int level, int form, int points, int[] levels, String negativeProperty, int negativeLevel, boolean hasOpenBarrierAdvancement) {
            this.xp = xp;
            this.level = level;
            this.form = form;
            this.points = points;
            this.propLevels = levels;
            this.negativeProperty = negativeProperty;
            this.negativeLevel = negativeLevel;
            this.hasOpenBarrierAdvancement = hasOpenBarrierAdvancement;
        }

        /**
         * Handles  for the addon system.
         * @param pkt pkt used by this method.
         * @param ctx context data supplied by the current callback or network pipeline.
         */
        public static void handle(DomainMasterySyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandler.syncDomainMastery(pkt.xp, pkt.level, pkt.form, pkt.points, pkt.propLevels, pkt.negativeProperty, pkt.negativeLevel, pkt.hasOpenBarrierAdvancement)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ===== DOMAIN CLASH SYNC =====
    public static void sendDomainClashSync(ServerPlayer player, float casterPower,
                                           int casterDomainId, int casterForm,
                                           String casterName, boolean active,
                                           long syncedGameTime,
                                           List<DomainClashOpponentPayload> opponents) {
        if (player == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                (Object)new DomainClashMultiSyncPacket(casterPower, casterDomainId, casterForm,
                        casterName, active, syncedGameTime, opponents));
    }

    public static class DomainClashMultiSyncPacket {
        private static final int MAX_OPPONENTS = 8;
        private final float casterPower;
        private final int casterDomainId;
        private final int casterForm;
        private final String casterName;
        private final boolean active;
        private final long syncedGameTime;
        private final List<DomainClashOpponentPayload> opponents;

        public DomainClashMultiSyncPacket(float casterPower, int casterDomainId, int casterForm,
                                          String casterName, boolean active,
                                          long syncedGameTime,
                                          List<DomainClashOpponentPayload> opponents) {
            this.casterPower = Math.max(0.0f, casterPower);
            this.casterDomainId = casterDomainId;
            this.casterForm = casterForm;
            this.casterName = casterName == null ? "" : casterName;
            this.active = active;
            this.syncedGameTime = syncedGameTime;
            List<DomainClashOpponentPayload> copy = new ArrayList<>();
            if (opponents != null) {
                for (DomainClashOpponentPayload opponent : opponents) {
                    if (opponent == null) {
                        continue;
                    }
                    copy.add(opponent);
                    if (copy.size() >= MAX_OPPONENTS) {
                        break;
                    }
                }
            }
            this.opponents = copy;
        }

        public static void encode(DomainClashMultiSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeFloat(pkt.casterPower);
            buf.writeByte(pkt.casterForm);
            buf.writeInt(pkt.casterDomainId);
            buf.writeUtf(pkt.casterName, 64);
            buf.writeBoolean(pkt.active);
            buf.writeLong(pkt.syncedGameTime);
            buf.writeByte(pkt.opponents.size());
            for (DomainClashOpponentPayload opponent : pkt.opponents) {
                buf.writeFloat(opponent.power());
                buf.writeByte(opponent.form());
                buf.writeInt(opponent.domainId());
                buf.writeUtf(opponent.name(), 64);
            }
        }

        public static DomainClashMultiSyncPacket decode(FriendlyByteBuf buf) {
            float casterPower = buf.readFloat();
            int casterForm = buf.readByte();
            int casterDomainId = buf.readInt();
            String casterName = buf.readUtf(64);
            boolean active = buf.readBoolean();
            long syncedGameTime = buf.readLong();
            int opponentCount = Math.min(MAX_OPPONENTS, Math.max(0, buf.readByte()));
            List<DomainClashOpponentPayload> opponents = new ArrayList<>(opponentCount);
            for (int i = 0; i < opponentCount; ++i) {
                opponents.add(new DomainClashOpponentPayload(
                        buf.readFloat(),
                        buf.readByte(),
                        buf.readInt(),
                        buf.readUtf(64)));
            }
            return new DomainClashMultiSyncPacket(casterPower, casterDomainId, casterForm,
                    casterName, active, syncedGameTime, opponents);
        }

        public static void handle(DomainClashMultiSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () ->
                    ClientPacketHandler.updateDomainClash(pkt.casterPower, pkt.casterDomainId,
                            pkt.casterForm, pkt.casterName, pkt.active,
                            pkt.syncedGameTime, pkt.opponents)));
            ctx.setPacketHandled(true);
        }
    }

    public record DomainClashOpponentPayload(float power, int form, int domainId, String name) {
        public DomainClashOpponentPayload {
            power = Math.max(0.0f, power);
            name = name == null ? "" : name;
        }
    }

    /**
     * Immutable snapshot of vanilla technique selection state used while the server probes wheel entries.
     */
    // ===== PAYLOAD RECORDS =====
    private record PlayerSelectionSnapshot(double selectId, String name, double finalCost, double baseCost, boolean passive, boolean physical, boolean noChangeTechnique, String overlayCost, String overlayCursePower) {
    }

    /**
     * Immutable data row describing a single skill wheel entry, its colors, costs, cooldowns, and optional domain metadata.
     */
    public record WheelTechniqueEntry(double selectId, String displayName, double finalCost, double baseCost, int color, boolean passive, boolean physical, int domainForm, double domainMultiplier, int cooldownRemainingTicks, int cooldownMaxTicks) {
    }

    /**
     * Small helper record describing a stored cursed spirit slot for Geto's paged wheel entries.
     */
    private record SpiritData(int slot, String name, int count, int grade) {
    }
}

