package net.mcreator.jujutsucraft.addon;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry of all techniques available per character.
 * Maps each character ID to their specific technique list.
 */
public class TechniqueRegistry {

    private static final Map<Integer, List<Technique>> CHAR_TECHNIQUES = new LinkedHashMap<>();

    private static final int COLOR_GRAY = 0xFF6B6B6B;
    private static final int COLOR_RED = 0xFF1C1C1C;
    private static final int COLOR_BLUE = 0xFF0048FF;
    private static final int COLOR_CYAN = 0xFF00D4FF;
    private static final int COLOR_PURPLE = 0xFFAA00FF;
    private static final int COLOR_GOLD = 0xFFFFAA00;
    private static final int COLOR_ORANGE = 0xFFFF6600;
    private static final int COLOR_GREEN = 0xFF55AA55;
    private static final int COLOR_TEAL = 0xFF00AAAA;
    private static final int COLOR_PINK = 0xFFFF66AA;
    private static final int COLOR_DARK_RED = 0xFFCC3311;
    private static final int COLOR_YELLOW = 0xFFFFDD00;
    private static final int COLOR_BLOOD = 0xFF990033;

    private static final List<Technique> COMMON_TECHNIQUES;
    private static final Technique CANCEL_DOMAIN;

    private static Technique t(double id, String langKey, double cost, int color) {
        return new Technique(id, langKey, cost, color);
    }

    private static void registerChar(int charId, List<Technique> techniques) {
        CHAR_TECHNIQUES.put(charId, techniques);
    }

    public static List<Technique> getForCharacter(int charId) {
        ArrayList<Technique> result = new ArrayList<>(COMMON_TECHNIQUES);
        List<Technique> charSpecific = CHAR_TECHNIQUES.get(charId);
        if (charSpecific != null) {
            result.addAll(charSpecific);
        }
        result.add(CANCEL_DOMAIN);
        result.sort(Comparator.comparingDouble(Technique::selectId));
        return Collections.unmodifiableList(result);
    }

    public static List<Technique> getSpecificForCharacter(int charId) {
        List<Technique> charSpecific = CHAR_TECHNIQUES.get(charId);
        if (charSpecific == null || charSpecific.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(COMMON_TECHNIQUES));
        }
        return Collections.unmodifiableList(charSpecific);
    }

    public static boolean isValidForCharacter(int charId, double selectId) {
        return getForCharacter(charId).stream().anyMatch(t -> t.selectId() == selectId);
    }

    static {
        // Character 1: Sukuna
        registerChar(1, List.of(
            t(5, "jujutsu.technique.dismantle", 100, COLOR_RED),
            t(6, "jujutsu.technique.cleave", 100, COLOR_RED),
            t(7, "jujutsu.technique.open", 500, COLOR_DARK_RED),
            t(20, "jujutsu.technique.malevolent_shrine", 1250, COLOR_GOLD)
        ));

        // Character 2: Gojo
        registerChar(2, List.of(
            t(5, "jujutsu.technique.infinity", 0, COLOR_CYAN),
            t(6, "jujutsu.technique.blue", 200, COLOR_BLUE),
            t(7, "jujutsu.technique.red", 500, COLOR_RED),
            t(15, "jujutsu.technique.purple", 1000, COLOR_PURPLE),
            t(20, "jujutsu.technique.unlimited_void", 1250, COLOR_GOLD)
        ));

        // Character 3: Toji
        registerChar(3, List.of(
            t(5, "jujutsu.technique.explode", 400, COLOR_ORANGE),
            t(6, "jujutsu.technique.get_crushed", 250, COLOR_ORANGE),
            t(7, "jujutsu.technique.crumble_away", 500, COLOR_ORANGE),
            t(8, "jujutsu.technique.dont_move", 150, COLOR_ORANGE),
            t(9, "jujutsu.technique.blast_away", 300, COLOR_ORANGE),
            t(20, "effect.domain_expansion", 1000, COLOR_GOLD)
        ));

        // Character 4: Yuki
        registerChar(4, List.of(
            t(5, "jujutsu.technique.flame_fire", 180, COLOR_ORANGE),
            t(6, "jujutsu.technique.flame_fire2", 120, COLOR_ORANGE),
            t(7, "jujutsu.technique.ember_insects", 150, COLOR_ORANGE),
            t(8, "jujutsu.technique.flame_fire3", 150, COLOR_ORANGE),
            t(9, "jujutsu.technique.flame_fire4", 500, COLOR_ORANGE),
            t(15, "jujutsu.technique.meteor", 1250, COLOR_GOLD),
            t(20, "jujutsu.technique.coffinofthe_iron_mountain", 1250, COLOR_GOLD)
        ));

        // Character 5: Geto
        registerChar(5, List.of(
            t(3, "jujutsu.technique.attack6", 50, COLOR_GRAY),
            t(5, "jujutsu.technique.copy1", 500, COLOR_PURPLE),
            t(6, "advancements.skill_copy_dhruv_lakdawalla.title", 300, COLOR_PURPLE),
            t(7, "advancements.skill_copy_takako_uro.title", 200, COLOR_PURPLE),
            t(10, "entity.jujutsucraft.rika", 150, COLOR_PINK),
            t(15, "entity.jujutsucraft.pure_love_cannon", 1000, COLOR_PINK),
            t(19, "jujutsu.technique.rika2", 0, COLOR_PINK),
            t(20, "jujutsu.technique.okkotsu20", 1250, COLOR_GOLD)
        ));

        // Character 6: Megumi
        registerChar(6, List.of(
            t(4, "jujutsu.technique.cancel", 0, COLOR_GRAY),
            t(5, "entity.jujutsucraft.divine_dog_white", 100, COLOR_TEAL),
            t(6, "entity.jujutsucraft.divine_dog_black", 100, COLOR_TEAL),
            t(7, "entity.jujutsucraft.divine_dog_totality", 400, COLOR_TEAL),
            t(8, "entity.jujutsucraft.nue", 250, COLOR_YELLOW),
            t(9, "entity.jujutsucraft.great_serpent", 300, COLOR_GREEN),
            t(10, "entity.jujutsucraft.toad", 150, COLOR_GREEN),
            t(11, "entity.jujutsucraft.max_elephant", 750, COLOR_BLUE),
            t(12, "entity.jujutsucraft.rabbit_escape", 125, COLOR_GREEN),
            t(13, "entity.jujutsucraft.round_deer", 600, COLOR_GREEN),
            t(14, "entity.jujutsucraft.piercing_ox", 400, COLOR_ORANGE),
            t(15, "entity.jujutsucraft.tiger_funeral", 400, COLOR_ORANGE),
            t(17, "entity.jujutsucraft.merged_beast_agito", 600, COLOR_PURPLE),
            t(18, "entity.jujutsucraft.eight_handled_swrod_divergent_sila_divine_general_mahoraga", 1000, COLOR_GOLD),
            t(20, "jujutsu.technique.chimera_shadow_garden", 1250, COLOR_GOLD)
        ));

        // Character 7: Kashimo
        registerChar(7, List.of(
            t(5, "jujutsu.technique.kashimo1", 200, COLOR_YELLOW),
            t(10, "jujutsu.technique.kashimo2", 100, COLOR_YELLOW),
            t(15, "effect.mythical_beast_amber_effect", 0, COLOR_GOLD),
            t(16, "jujutsu.technique.kashimo_ah", 100, COLOR_YELLOW),
            t(17, "jujutsu.technique.kashimo_energy_wave", 250, COLOR_YELLOW)
        ));

        // Character 8: Dagon
        registerChar(8, List.of(
            t(5, "jujutsu.technique.dagon1", 100, COLOR_TEAL),
            t(6, "jujutsu.technique.dagon2", 150, COLOR_TEAL),
            t(7, "jujutsu.technique.dagon3", 150, COLOR_TEAL),
            t(9, "jujutsu.technique.dagon5", 250, COLOR_TEAL),
            t(10, "entity.jujutsucraft.bathynomus_giganteus", 400, COLOR_TEAL),
            t(20, "jujutsu.technique.dagon20", 1250, COLOR_GOLD)
        ));

        // Character 9: Angel
        registerChar(9, List.of(
            t(4, "jujutsu.technique.attack7", 50, COLOR_GRAY),
            t(5, "jujutsu.technique.shoot", 500, COLOR_PINK),
            t(6, "entity.jujutsucraft.garuda", 75, COLOR_PINK),
            t(10, "effect.star_rage", 0, COLOR_GOLD),
            t(20, "jujutsu.technique.tsukumo_domain", 1250, COLOR_GOLD)
        ));

        // Character 10: Choso
        registerChar(10, List.of(
            t(5, "jujutsu.technique.choso1", 120, COLOR_BLOOD),
            t(6, "jujutsu.technique.choso2", 25, COLOR_BLOOD),
            t(7, "jujutsu.technique.choso3", 200, COLOR_BLOOD),
            t(8, "jujutsu.technique.choso4", 100, COLOR_BLOOD),
            t(9, "jujutsu.technique.choso5", 100, COLOR_BLOOD),
            t(16, "jujutsu.technique.choso6", 400, COLOR_BLOOD),
            t(18, "item.jujutsucraft.wing_king_chestplate", 150, COLOR_BLOOD),
            t(20, "effect.domain_expansion", 1250, COLOR_GOLD)
        ));

        // Character 11
        registerChar(11, List.of(
            t(5, "jujutsu.technique.bird_strike", 200, COLOR_BLUE),
            t(20, "effect.domain_expansion", 1250, COLOR_GOLD)
        ));

        // Character 12: Eso
        registerChar(12, List.of(
            t(5, "jujutsu.technique.granite_blast", 200, COLOR_ORANGE),
            t(6, "jujutsu.technique.granite_blast", 300, COLOR_ORANGE),
            t(20, "jujutsu.technique.ishigori_domain", 1000, COLOR_GOLD)
        ));

        // Character 13: Nanami
        registerChar(13, List.of(
            t(5, "jujutsu.technique.nanami1", 25, COLOR_YELLOW),
            t(10, "jujutsu.technique.nanami2", 300, COLOR_YELLOW),
            t(20, "jujutsu.technique.nanami_domain", 1000, COLOR_GOLD)
        ));

        // Character 14: Hanami
        registerChar(14, List.of(
            t(5, "jujutsu.technique.hanami1", 120, COLOR_GREEN),
            t(6, "jujutsu.technique.hanami2", 80, COLOR_GREEN),
            t(10, "jujutsu.technique.hanami10", 500, COLOR_GREEN),
            t(15, "jujutsu.technique.hanami15", 0, COLOR_GREEN),
            t(20, "jujutsu.technique.hanami_domain", 1100, COLOR_GOLD)
        ));

        // Character 21: Yuji
        registerChar(21, List.of(
            t(5, "jujutsu.technique.itadori1", 50, COLOR_PINK),
            t(10, "effect.sukuna_effect", 0, COLOR_RED),
            t(20, "effect.domain_expansion", 1000, COLOR_GOLD)
        ));

        // Character 22: Jinichi
        registerChar(22, List.of(
            t(5, "jujutsu.technique.jinichi5", 100, COLOR_GREEN),
            t(7, "jujutsu.technique.jinichi7", 300, COLOR_GREEN),
            t(8, "jujutsu.technique.jinichi8", 200, COLOR_GREEN),
            t(10, "jujutsu.technique.jinichi10", 500, COLOR_GREEN),
            t(20, "effect.domain_expansion", 1000, COLOR_GOLD)
        ));

        // Character 23
        registerChar(23, List.of(
            t(5, "jujutsu.technique.attack4", 50, COLOR_GRAY),
            t(6, "jujutsu.technique.cockroach1", 100, COLOR_PURPLE),
            t(7, "jujutsu.technique.cockroach2", 150, COLOR_PURPLE),
            t(8, "jujutsu.technique.cockroach3", 250, COLOR_PURPLE),
            t(9, "entity.jujutsucraft.earthen_insect_trance", 100, COLOR_PURPLE),
            t(20, "effect.domain_expansion", 1250, COLOR_GOLD)
        ));

        // Character 24: Uraume
        registerChar(24, List.of(
            t(5, "jujutsu.technique.uraume_5", 50, COLOR_CYAN),
            t(6, "jujutsu.technique.uraume_6", 120, COLOR_CYAN),
            t(8, "jujutsu.technique.uraume_8", 250, COLOR_CYAN),
            t(9, "jujutsu.technique.uraume_9", 300, COLOR_CYAN),
            t(15, "jujutsu.technique.uraume_5", 800, COLOR_CYAN),
            t(20, "effect.domain_expansion", 1250, COLOR_GOLD)
        ));

        // Character 25
        registerChar(25, List.of(
            t(5, "entity.jujutsucraft.gravestone_3", 100, COLOR_PURPLE),
            t(20, "effect.domain_expansion", 1250, COLOR_GOLD)
        ));

        COMMON_TECHNIQUES = List.of(
            t(0, "jujutsu.technique.attack1", 0, COLOR_GRAY),
            t(1, "jujutsu.technique.attack2", 0, COLOR_GRAY),
            t(2, "jujutsu.technique.attack3", 0, COLOR_GRAY)
        );

        CANCEL_DOMAIN = t(21, "jujutsu.technique.cancel_domain", 0, COLOR_GRAY);
    }

    public record Technique(double selectId, String nameKey, double baseCost, int color) {
        public String getDisplayName() {
            return Component.translatable(nameKey).getString();
        }
    }
}
