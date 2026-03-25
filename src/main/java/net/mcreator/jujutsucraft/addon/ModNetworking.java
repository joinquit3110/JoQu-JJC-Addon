package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.mcreator.jujutsucraft.addon.SkillWheelScreen;
import net.mcreator.jujutsucraft.addon.limb.LimbSyncPacket;
import net.mcreator.jujutsucraft.addon.limb.NearDeathPacket;
import net.mcreator.jujutsucraft.addon.limb.RCTLevel3Handler;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.ChangeTechniqueTestProcedure;
import net.mcreator.jujutsucraft.procedures.KeyChangeTechniqueOnKeyPressedProcedure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static final String DATA_KEY_TECHNIQUE_CD_MAX = "jjkbrp_technique_cd_max";
    private static final String DATA_KEY_COMBAT_CD_MAX = "jjkbrp_combat_cd_max";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("jjkblueredpurple", "main"),
        () -> "1", "1"::equals, "1"::equals
    );
    private static int packetId = 0;

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
    }

    private static boolean isTechniqueLocked(ServerPlayer player, int charId, double selectId) {
        return ChangeTechniqueTestProcedure.execute(player.level(), player.getX(), player.getY(), player.getZ(), player, charId, selectId);
    }

    private static void applyOriginalTechniqueSelection(ServerPlayer player, double selectId) {
        player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(cap -> {
            boolean previousNoChange = cap.noChangeTechnique;
            cap.noChangeTechnique = true;
            cap.PlayerSelectCurseTechnique = selectId;
            cap.syncPlayerVariables(player);
            KeyChangeTechniqueOnKeyPressedProcedure.execute(player.level(), player.getX(), player.getY(), player.getZ(), player);
            cap.noChangeTechnique = previousNoChange;
            cap.syncPlayerVariables(player);
        });
    }

    private static int getActiveCharacterId(JujutsucraftModVariables.PlayerVariables vars) {
        double activeTechnique = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        return (int)Math.round(activeTechnique);
    }

    private static boolean isValidSelectId(double selectId) {
        return selectId >= 0.0 && selectId <= 21.0;
    }

    private static int asSelectId(double selectId) {
        return (int)Math.round(selectId);
    }

    public static int getTechniqueCooldownTicks(ServerPlayer player) {
        return player.hasEffect(JujutsucraftModMobEffects.COOLDOWN_TIME.get()) ? player.getEffect(JujutsucraftModMobEffects.COOLDOWN_TIME.get()).getDuration() : 0;
    }

    public static int getCombatCooldownTicks(ServerPlayer player) {
        return player.hasEffect(JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get()) ? player.getEffect(JujutsucraftModMobEffects.COOLDOWN_TIME_COMBAT.get()).getDuration() : 0;
    }

    public static int getCooldownTicks(ServerPlayer player) {
        return Math.max(ModNetworking.getTechniqueCooldownTicks(player), ModNetworking.getCombatCooldownTicks(player));
    }

    public static void captureActiveSkillCooldown(ServerPlayer player, JujutsucraftModVariables.PlayerVariables vars) {
    }

    public static void sendCooldownSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int techRemaining = ModNetworking.getTechniqueCooldownTicks(player);
        int combatRemaining = ModNetworking.getCombatCooldownTicks(player);
        int techMax = (int)data.getDouble(DATA_KEY_TECHNIQUE_CD_MAX);
        int combatMax = (int)data.getDouble(DATA_KEY_COMBAT_CD_MAX);
        if (techRemaining > techMax) {
            techMax = techRemaining;
            data.putDouble(DATA_KEY_TECHNIQUE_CD_MAX, techMax);
        }
        if (techRemaining <= 0) {
            techMax = 0;
            data.putDouble(DATA_KEY_TECHNIQUE_CD_MAX, 0.0);
        }
        if (combatRemaining > combatMax) {
            combatMax = combatRemaining;
            data.putDouble(DATA_KEY_COMBAT_CD_MAX, combatMax);
        }
        if (combatRemaining <= 0) {
            combatMax = 0;
            data.putDouble(DATA_KEY_COMBAT_CD_MAX, 0.0);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CooldownSyncPacket(techRemaining, techMax, combatRemaining, combatMax));
    }

    private static int computeTechniqueColor(String displayName, boolean passive, boolean physical, double selectId) {
        String n;
        String string = n = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
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

    private static int generateVibrantColor(String displayName, double selectId) {
        String key = (displayName == null ? "technique" : displayName.toLowerCase(Locale.ROOT)) + "#" + (int)Math.round(selectId);
        int hash = key.hashCode();
        float hue = (hash & Integer.MAX_VALUE) % 360;
        float saturation = 0.72f;
        float value = 0.95f;
        return ModNetworking.hsvToRgb(hue, saturation, value);
    }

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

    private static PlayerSelectionSnapshot snapshot(JujutsucraftModVariables.PlayerVariables vars) {
        return new PlayerSelectionSnapshot(vars.PlayerSelectCurseTechnique, vars.PlayerSelectCurseTechniqueName, vars.PlayerSelectCurseTechniqueCost, vars.PlayerSelectCurseTechniqueCostOrgin, vars.PassiveTechnique, vars.PhysicalAttack, vars.noChangeTechnique, vars.OverlayCost, vars.OverlayCursePower);
    }

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
            cap.syncPlayerVariables(player);
        });
    }

    private static int getPerSkillCooldownTicks(ServerPlayer player, int charId, int selectId) {
        String effectId = ModNetworking.getPerSkillEffectId(charId, selectId);
        if (effectId == null) {
            return -1;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft_plus", effectId));
        if (effect == null) {
            return -1;
        }
        if (!player.hasEffect(effect)) {
            return 0;
        }
        return player.getEffect(effect).getDuration();
    }

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
                    break;
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
                    break;
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

    private static List<WheelTechniqueEntry> buildWheelEntries(ServerPlayer player, int charId) {
        JujutsucraftModVariables.PlayerVariables baseVars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        PlayerSelectionSnapshot baseline = ModNetworking.snapshot(baseVars);
        ArrayList<WheelTechniqueEntry> entries = new ArrayList<WheelTechniqueEntry>();
        ArrayList<Double> seenIds = new ArrayList<Double>();
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
                int perSkill = ModNetworking.getPerSkillCooldownTicks(player, charId, sid);
                if (perSkill >= 0) {
                    cdRemaining = perSkill;
                } else {
                    int n = cdRemaining = after.PhysicalAttack ? ModNetworking.getCombatCooldownTicks(player) : ModNetworking.getTechniqueCooldownTicks(player);
                }
            }
            if (cdRemaining <= 0 && sid > 2 && !after.PhysicalAttack && player.hasEffect(JujutsucraftModMobEffects.UNSTABLE.get())) {
                cdRemaining = player.getEffect(JujutsucraftModMobEffects.UNSTABLE.get()).getDuration();
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
            entries.add(new WheelTechniqueEntry(resolvedId, displayName, after.PlayerSelectCurseTechniqueCost, after.PlayerSelectCurseTechniqueCostOrgin, ModNetworking.computeTechniqueColor(displayName, after.PassiveTechnique, after.PhysicalAttack, resolvedId), after.PassiveTechnique, after.PhysicalAttack, cdRemaining, cdMax));
            seenIds.add(resolvedId);
        }
        ModNetworking.restore(player, baseline);
        entries.sort(Comparator.comparingDouble(WheelTechniqueEntry::selectId));
        if (entries.isEmpty()) {
            entries.add(new WheelTechniqueEntry(baseline.selectId(), baseline.name() == null || baseline.name().isBlank() ? "Technique" : baseline.name(), baseline.finalCost(), baseline.baseCost(), ModNetworking.computeTechniqueColor(baseline.name(), baseline.passive(), baseline.physical(), baseline.selectId()), baseline.passive(), baseline.physical(), 0, 0));
        }
        return entries;
    }

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

    private static int getSpiritGradeColor(int grade) {
        return switch (grade) {
            case 0 -> 13127872;
            case 1 -> 13934615;
            case 2 -> 4886745;
            default -> 7048811;
        };
    }

    private static List<List<WheelTechniqueEntry>> buildGetoPages(ServerPlayer player, int charId) {
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
                String displayName = s.count > 1 ? s.name + " x" + s.count : s.name;
                page.add(new WheelTechniqueEntry(100 + s.slot, displayName, 0.0, 0.0, ModNetworking.getSpiritGradeColor(s.grade), false, false, 0, 0));
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
                String displayName = s.count > 1 ? s.name + " x" + s.count : s.name;
                page.add(new WheelTechniqueEntry(100 + s.slot, displayName, 0.0, 0.0, ModNetworking.getSpiritGradeColor(s.grade), false, false, 0, 0));
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

    public static void sendBlackFlashSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        float pct = (float)data.getDouble("addon_bf_chance");
        boolean mastery = data.getBoolean("addon_bf_mastery");
        boolean charging = data.getBoolean("addon_bf_charging");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlackFlashSyncPacket(pct, mastery, charging));
    }

    public static void sendNearDeathCdSync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int cd = data.getInt("jjkbrp_near_death_cd");
        boolean rctL3 = RCTLevel3Handler.hasAdvancement(player, "jjkblueredpurple:rct_level_3");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NearDeathCdSyncPacket(cd, 6000, rctL3));
    }

    public static class SelectTechniquePacket {
        private final double selectId;

        public SelectTechniquePacket(double selectId) {
            this.selectId = selectId;
        }

        public static void encode(SelectTechniquePacket pkt, FriendlyByteBuf buf) {
            buf.writeDouble(pkt.selectId);
        }

        public static SelectTechniquePacket decode(FriendlyByteBuf buf) {
            return new SelectTechniquePacket(buf.readDouble());
        }

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

    public static class RequestWheelPacket {
        public static void encode(RequestWheelPacket pkt, FriendlyByteBuf buf) {
        }

        public static RequestWheelPacket decode(FriendlyByteBuf buf) {
            return new RequestWheelPacket();
        }

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
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenWheelPacket(pages, currentSelect));
                } else {
                    List<WheelTechniqueEntry> entries = ModNetworking.buildWheelEntries(player, charId);
                    ArrayList<List<WheelTechniqueEntry>> singlePage = new ArrayList<List<WheelTechniqueEntry>>();
                    singlePage.add(entries);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenWheelPacket(singlePage, currentSelect));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class OpenWheelPacket {
        final List<List<WheelTechniqueEntry>> pages;
        private final double currentSelect;

        public OpenWheelPacket(List<List<WheelTechniqueEntry>> pages, double currentSelect) {
            this.pages = pages;
            this.currentSelect = currentSelect;
        }

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
                    buf.writeInt(entry.cooldownRemainingTicks());
                    buf.writeInt(entry.cooldownMaxTicks());
                }
            }
            buf.writeDouble(pkt.currentSelect);
        }

        public static OpenWheelPacket decode(FriendlyByteBuf buf) {
            int numPages = buf.readInt();
            ArrayList<List<WheelTechniqueEntry>> pages = new ArrayList<List<WheelTechniqueEntry>>();
            for (int p = 0; p < numPages; ++p) {
                int size = buf.readInt();
                ArrayList<WheelTechniqueEntry> page = new ArrayList<WheelTechniqueEntry>();
                for (int i = 0; i < size; ++i) {
                    page.add(new WheelTechniqueEntry(buf.readDouble(), buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt()));
                }
                pages.add(page);
            }
            return new OpenWheelPacket(pages, buf.readDouble());
        }

        public static void handle(OpenWheelPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new SkillWheelScreen(pkt.pages, pkt.currentSelect))));
            ctx.setPacketHandled(true);
        }
    }

    public static class CooldownSyncPacket {
        private final int techRemaining;
        private final int techMax;
        private final int combatRemaining;
        private final int combatMax;

        public CooldownSyncPacket(int techRemaining, int techMax, int combatRemaining, int combatMax) {
            this.techRemaining = techRemaining;
            this.techMax = techMax;
            this.combatRemaining = combatRemaining;
            this.combatMax = combatMax;
        }

        public static void encode(CooldownSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.techRemaining);
            buf.writeInt(pkt.techMax);
            buf.writeInt(pkt.combatRemaining);
            buf.writeInt(pkt.combatMax);
        }

        public static CooldownSyncPacket decode(FriendlyByteBuf buf) {
            return new CooldownSyncPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(CooldownSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientCooldownCache.updateTechnique(pkt.techRemaining, pkt.techMax);
                ClientCooldownCache.updateCombat(pkt.combatRemaining, pkt.combatMax);
            }));
            ctx.setPacketHandled(true);
        }
    }

    public static class BlackFlashSyncPacket {
        private final float bfPercent;
        private final boolean mastery;
        private final boolean charging;

        public BlackFlashSyncPacket(float bfPercent, boolean mastery, boolean charging) {
            this.bfPercent = bfPercent;
            this.mastery = mastery;
            this.charging = charging;
        }

        public static void encode(BlackFlashSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeFloat(pkt.bfPercent);
            buf.writeBoolean(pkt.mastery);
            buf.writeBoolean(pkt.charging);
        }

        public static BlackFlashSyncPacket decode(FriendlyByteBuf buf) {
            return new BlackFlashSyncPacket(buf.readFloat(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(BlackFlashSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientBlackFlashCache.bfPercent = pkt.bfPercent;
                ClientBlackFlashCache.mastery = pkt.mastery;
                ClientBlackFlashCache.charging = pkt.charging;
            }));
            ctx.setPacketHandled(true);
        }
    }

    public static class SelectSpiritPacket {
        private final int spiritSlot;

        public SelectSpiritPacket(int spiritSlot) {
            this.spiritSlot = spiritSlot;
        }

        public static void encode(SelectSpiritPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.spiritSlot);
        }

        public static SelectSpiritPacket decode(FriendlyByteBuf buf) {
            return new SelectSpiritPacket(buf.readInt());
        }

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
                    cap.syncPlayerVariables(player);
                });
            });
            ctx.setPacketHandled(true);
        }
    }

    public record NearDeathCdSyncPacket(int cdRemaining, int cdMax, boolean rctLevel3Unlocked) {
        public static void encode(NearDeathCdSyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.cdRemaining);
            buf.writeInt(pkt.cdMax);
            buf.writeBoolean(pkt.rctLevel3Unlocked);
        }

        public static NearDeathCdSyncPacket decode(FriendlyByteBuf buf) {
            return new NearDeathCdSyncPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        public static void handle(NearDeathCdSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientNearDeathCdCache.cdRemaining = pkt.cdRemaining;
                ClientNearDeathCdCache.cdMax = pkt.cdMax;
                ClientNearDeathCdCache.rctLevel3Unlocked = pkt.rctLevel3Unlocked;
            }));
            ctx.setPacketHandled(true);
        }
    }

    private record PlayerSelectionSnapshot(double selectId, String name, double finalCost, double baseCost, boolean passive, boolean physical, boolean noChangeTechnique, String overlayCost, String overlayCursePower) {
    }

    public record WheelTechniqueEntry(double selectId, String displayName, double finalCost, double baseCost, int color, boolean passive, boolean physical, int cooldownRemainingTicks, int cooldownMaxTicks) {
    }

    private record SpiritData(int slot, String name, int count, int grade) {
    }

    public static final class ClientCooldownCache {
        private static int techRemaining = 0;
        private static int techMax = 0;
        private static int combatRemaining = 0;
        private static int combatMax = 0;

        public static void updateTechnique(int remaining, int max) {
            techRemaining = Math.max(0, remaining);
            techMax = Math.max(techRemaining, Math.max(0, max));
        }

        public static void updateCombat(int remaining, int max) {
            combatRemaining = Math.max(0, remaining);
            combatMax = Math.max(combatRemaining, Math.max(0, max));
        }

        public static int getRemaining(boolean physical) {
            return physical ? Math.max(0, combatRemaining) : Math.max(0, techRemaining);
        }

        public static int getMax(boolean physical) {
            return physical ? Math.max(0, combatMax) : Math.max(0, techMax);
        }

        public static void tickDecay() {
            if (techRemaining > 0 && --techRemaining <= 0) {
                techMax = 0;
            }
            if (combatRemaining > 0 && --combatRemaining <= 0) {
                combatMax = 0;
            }
        }
    }

    public static final class ClientNearDeathCdCache {
        public static int cdRemaining = 0;
        public static int cdMax = 6000;
        public static boolean rctLevel3Unlocked = false;
    }

    public static final class ClientBlackFlashCache {
        public static float bfPercent = 0.0f;
        public static boolean mastery = false;
        public static boolean charging = false;
    }
}
