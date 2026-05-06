package net.mcreator.jujutsucraft.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Hard-coded registry of selectable techniques for supported characters. It exposes shared attacks, character-specific techniques, and display metadata such as base costs and wheel colors.
 */
public class TechniqueRegistry {
    private static final Map<Integer, List<Technique>> CHAR_TECHNIQUES = new LinkedHashMap<Integer, List<Technique>>();
    // Fallback neutral color used for shared attacks and cancel entries.
    private static final int COLOR_GRAY = -9737365;
    // Display color used for aggressive red-themed techniques.
    private static final int COLOR_RED = -14935012;
    // Display color used for blue-themed techniques.
    private static final int COLOR_BLUE = -16758529;
    // Display color used for cyan-themed techniques.
    private static final int COLOR_CYAN = -16722689;
    // Display color used for purple or copy-themed techniques.
    private static final int COLOR_PURPLE = -5635841;
    // Display color used for domain and maximum techniques.
    private static final int COLOR_GOLD = -22016;
    // Display color used for flame and explosive techniques.
    private static final int COLOR_ORANGE = -39424;
    // Display color used for plant or support techniques.
    private static final int COLOR_GREEN = -11163051;
    // Display color used for shikigami and teal-tinted techniques.
    private static final int COLOR_TEAL = -16733526;
    // Display color used for pink or love-themed techniques.
    private static final int COLOR_PINK = -39254;
    // Display color used for darker cursed flame or shrine effects.
    private static final int COLOR_DARK_RED = -3394799;
    // Display color used for lightning or precision techniques.
    private static final int COLOR_YELLOW = -8960;
    // Display color used for blood manipulation techniques.
    private static final int COLOR_BLOOD = -6750157;
    // Shared baseline attacks that every supported character can select.
    private static final List<Technique> COMMON_TECHNIQUES;
    // Always-available cancel entry appended to every character technique list.
    private static final Technique CANCEL_DOMAIN;

    /**
     * Performs t for this addon component.
     * @param id identifier used to resolve the requested entry or state.
     * @param langKey lang key used by this method.
     * @param cost cost used by this method.
     * @param color color used by this method.
     * @return the resulting t value.
     */
    private static Technique t(double id, String langKey, double cost, int color) {
        return new Technique(id, langKey, cost, color);
    }

    /**
     * Registers char with the appropriate Forge or client system.
     * @param charId identifier used to resolve the requested entry or state.
     * @param techniques techniques used by this method.
     */
    private static void registerChar(int charId, List<Technique> techniques) {
        CHAR_TECHNIQUES.put(charId, techniques);
    }

    /**
     * Returns for character for the current addon state.
     * @param charId identifier used to resolve the requested entry or state.
     * @return the resolved for character.
     */
    public static List<Technique> getForCharacter(int charId) {
        ArrayList<Technique> result = new ArrayList<Technique>(COMMON_TECHNIQUES);
        List<Technique> charSpecific = CHAR_TECHNIQUES.get(charId);
        if (charSpecific != null) {
            result.addAll(charSpecific);
        }
        result.add(CANCEL_DOMAIN);
        result.sort(Comparator.comparingDouble(Technique::selectId));
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns specific for character for the current addon state.
     * @param charId identifier used to resolve the requested entry or state.
     * @return the resolved specific for character.
     */
    public static List<Technique> getSpecificForCharacter(int charId) {
        List<Technique> charSpecific = CHAR_TECHNIQUES.get(charId);
        if (charSpecific == null || charSpecific.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<Technique>(COMMON_TECHNIQUES));
        }
        return Collections.unmodifiableList(charSpecific);
    }

    /**
     * Checks whether is valid for character is true for the current addon state.
     * @param charId identifier used to resolve the requested entry or state.
     * @param selectId identifier used to resolve the requested entry or state.
     * @return true when is valid for character succeeds; otherwise false.
     */
    public static boolean isValidForCharacter(int charId, double selectId) {
        return TechniqueRegistry.getForCharacter(charId).stream().anyMatch(t -> t.selectId() == selectId);
    }

    static {
        TechniqueRegistry.registerChar(1, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.dismantle", 100.0, -14935012), TechniqueRegistry.t(6.0, "jujutsu.technique.cleave", 100.0, -14935012), TechniqueRegistry.t(7.0, "jujutsu.technique.open", 500.0, -3394799), TechniqueRegistry.t(20.0, "jujutsu.technique.malevolent_shrine", 1250.0, -22016)));
        TechniqueRegistry.registerChar(2, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.infinity", 0.0, -16722689), TechniqueRegistry.t(6.0, "jujutsu.technique.blue", 200.0, -16758529), TechniqueRegistry.t(7.0, "jujutsu.technique.red", 500.0, -14935012), TechniqueRegistry.t(15.0, "jujutsu.technique.purple", 1000.0, -5635841), TechniqueRegistry.t(20.0, "jujutsu.technique.unlimited_void", 1250.0, -22016)));
        TechniqueRegistry.registerChar(3, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.explode", 400.0, -39424), TechniqueRegistry.t(6.0, "jujutsu.technique.get_crushed", 250.0, -39424), TechniqueRegistry.t(7.0, "jujutsu.technique.crumble_away", 500.0, -39424), TechniqueRegistry.t(8.0, "jujutsu.technique.dont_move", 150.0, -39424), TechniqueRegistry.t(9.0, "jujutsu.technique.blast_away", 300.0, -39424), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1000.0, -22016)));
        TechniqueRegistry.registerChar(4, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.flame_fire", 180.0, -39424), TechniqueRegistry.t(6.0, "jujutsu.technique.flame_fire2", 120.0, -39424), TechniqueRegistry.t(7.0, "jujutsu.technique.ember_insects", 150.0, -39424), TechniqueRegistry.t(8.0, "jujutsu.technique.flame_fire3", 150.0, -39424), TechniqueRegistry.t(9.0, "jujutsu.technique.flame_fire4", 500.0, -39424), TechniqueRegistry.t(15.0, "jujutsu.technique.meteor", 1250.0, -22016), TechniqueRegistry.t(20.0, "jujutsu.technique.coffinofthe_iron_mountain", 1250.0, -22016)));
        TechniqueRegistry.registerChar(5, List.of(TechniqueRegistry.t(3.0, "jujutsu.technique.attack6", 50.0, -9737365), TechniqueRegistry.t(5.0, "jujutsu.technique.copy1", 500.0, -5635841), TechniqueRegistry.t(6.0, "advancements.skill_copy_dhruv_lakdawalla.title", 300.0, -5635841), TechniqueRegistry.t(7.0, "advancements.skill_copy_takako_uro.title", 200.0, -5635841), TechniqueRegistry.t(10.0, "entity.jujutsucraft.rika", 150.0, -39254), TechniqueRegistry.t(15.0, "entity.jujutsucraft.pure_love_cannon", 1000.0, -39254), TechniqueRegistry.t(19.0, "jujutsu.technique.rika2", 0.0, -39254), TechniqueRegistry.t(20.0, "jujutsu.technique.okkotsu20", 1250.0, -22016)));
        TechniqueRegistry.registerChar(6, List.of(TechniqueRegistry.t(4.0, "jujutsu.technique.cancel", 0.0, -9737365), TechniqueRegistry.t(5.0, "entity.jujutsucraft.divine_dog_white", 100.0, -16733526), TechniqueRegistry.t(6.0, "entity.jujutsucraft.divine_dog_black", 100.0, -16733526), TechniqueRegistry.t(7.0, "entity.jujutsucraft.divine_dog_totality", 400.0, -16733526), TechniqueRegistry.t(8.0, "entity.jujutsucraft.nue", 250.0, -8960), TechniqueRegistry.t(9.0, "entity.jujutsucraft.great_serpent", 300.0, -11163051), TechniqueRegistry.t(10.0, "entity.jujutsucraft.toad", 150.0, -11163051), TechniqueRegistry.t(11.0, "entity.jujutsucraft.max_elephant", 750.0, -16758529), TechniqueRegistry.t(12.0, "entity.jujutsucraft.rabbit_escape", 125.0, -11163051), TechniqueRegistry.t(13.0, "entity.jujutsucraft.round_deer", 600.0, -11163051), TechniqueRegistry.t(14.0, "entity.jujutsucraft.piercing_ox", 400.0, -39424), TechniqueRegistry.t(15.0, "entity.jujutsucraft.tiger_funeral", 400.0, -39424), TechniqueRegistry.t(17.0, "entity.jujutsucraft.merged_beast_agito", 600.0, -5635841), TechniqueRegistry.t(18.0, "entity.jujutsucraft.eight_handled_swrod_divergent_sila_divine_general_mahoraga", 1000.0, -22016), TechniqueRegistry.t(20.0, "jujutsu.technique.chimera_shadow_garden", 1250.0, -22016)));
        TechniqueRegistry.registerChar(7, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.kashimo1", 200.0, -8960), TechniqueRegistry.t(10.0, "jujutsu.technique.kashimo2", 100.0, -8960), TechniqueRegistry.t(15.0, "effect.mythical_beast_amber_effect", 0.0, -22016), TechniqueRegistry.t(16.0, "jujutsu.technique.kashimo_ah", 100.0, -8960), TechniqueRegistry.t(17.0, "jujutsu.technique.kashimo_energy_wave", 250.0, -8960)));
        TechniqueRegistry.registerChar(8, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.dagon1", 100.0, -16733526), TechniqueRegistry.t(6.0, "jujutsu.technique.dagon2", 150.0, -16733526), TechniqueRegistry.t(7.0, "jujutsu.technique.dagon3", 150.0, -16733526), TechniqueRegistry.t(9.0, "jujutsu.technique.dagon5", 250.0, -16733526), TechniqueRegistry.t(10.0, "entity.jujutsucraft.bathynomus_giganteus", 400.0, -16733526), TechniqueRegistry.t(20.0, "jujutsu.technique.dagon20", 1250.0, -22016)));
        TechniqueRegistry.registerChar(9, List.of(TechniqueRegistry.t(4.0, "jujutsu.technique.attack7", 50.0, -9737365), TechniqueRegistry.t(5.0, "jujutsu.technique.shoot", 500.0, -39254), TechniqueRegistry.t(6.0, "entity.jujutsucraft.garuda", 75.0, -39254), TechniqueRegistry.t(10.0, "effect.star_rage", 0.0, -22016), TechniqueRegistry.t(20.0, "jujutsu.technique.tsukumo_domain", 1250.0, -22016)));
        TechniqueRegistry.registerChar(10, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.choso1", 120.0, -6750157), TechniqueRegistry.t(6.0, "jujutsu.technique.choso2", 25.0, -6750157), TechniqueRegistry.t(7.0, "jujutsu.technique.choso3", 200.0, -6750157), TechniqueRegistry.t(8.0, "jujutsu.technique.choso4", 100.0, -6750157), TechniqueRegistry.t(9.0, "jujutsu.technique.choso5", 100.0, -6750157), TechniqueRegistry.t(16.0, "jujutsu.technique.choso6", 400.0, -6750157), TechniqueRegistry.t(18.0, "item.jujutsucraft.wing_king_chestplate", 150.0, -6750157), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1250.0, -22016)));
        TechniqueRegistry.registerChar(11, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.bird_strike", 200.0, -16758529), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1250.0, -22016)));
        TechniqueRegistry.registerChar(12, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.granite_blast", 200.0, -39424), TechniqueRegistry.t(6.0, "jujutsu.technique.granite_blast", 300.0, -39424), TechniqueRegistry.t(20.0, "jujutsu.technique.ishigori_domain", 1000.0, -22016)));
        TechniqueRegistry.registerChar(13, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.nanami1", 25.0, -8960), TechniqueRegistry.t(10.0, "jujutsu.technique.nanami2", 300.0, -8960), TechniqueRegistry.t(20.0, "jujutsu.technique.nanami_domain", 1000.0, -22016)));
        TechniqueRegistry.registerChar(14, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.hanami1", 120.0, -11163051), TechniqueRegistry.t(6.0, "jujutsu.technique.hanami2", 80.0, -11163051), TechniqueRegistry.t(10.0, "jujutsu.technique.hanami10", 500.0, -11163051), TechniqueRegistry.t(15.0, "jujutsu.technique.hanami15", 0.0, -11163051), TechniqueRegistry.t(20.0, "jujutsu.technique.hanami_domain", 1100.0, -22016)));
        TechniqueRegistry.registerChar(21, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.itadori1", 50.0, -39254), TechniqueRegistry.t(10.0, "effect.sukuna_effect", 0.0, -14935012), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1000.0, -22016)));
        TechniqueRegistry.registerChar(22, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.jinichi5", 100.0, -11163051), TechniqueRegistry.t(7.0, "jujutsu.technique.jinichi7", 300.0, -11163051), TechniqueRegistry.t(8.0, "jujutsu.technique.jinichi8", 200.0, -11163051), TechniqueRegistry.t(10.0, "jujutsu.technique.jinichi10", 500.0, -11163051), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1000.0, -22016)));
        TechniqueRegistry.registerChar(23, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.attack4", 50.0, -9737365), TechniqueRegistry.t(6.0, "jujutsu.technique.cockroach1", 100.0, -5635841), TechniqueRegistry.t(7.0, "jujutsu.technique.cockroach2", 150.0, -5635841), TechniqueRegistry.t(8.0, "jujutsu.technique.cockroach3", 250.0, -5635841), TechniqueRegistry.t(9.0, "entity.jujutsucraft.earthen_insect_trance", 100.0, -5635841), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1250.0, -22016)));
        TechniqueRegistry.registerChar(24, List.of(TechniqueRegistry.t(5.0, "jujutsu.technique.uraume_5", 50.0, -16722689), TechniqueRegistry.t(6.0, "jujutsu.technique.uraume_6", 120.0, -16722689), TechniqueRegistry.t(8.0, "jujutsu.technique.uraume_8", 250.0, -16722689), TechniqueRegistry.t(9.0, "jujutsu.technique.uraume_9", 300.0, -16722689), TechniqueRegistry.t(15.0, "jujutsu.technique.uraume_5", 800.0, -16722689), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1250.0, -22016)));
        TechniqueRegistry.registerChar(25, List.of(TechniqueRegistry.t(5.0, "entity.jujutsucraft.gravestone_3", 100.0, -5635841), TechniqueRegistry.t(20.0, "effect.domain_expansion", 1250.0, -22016)));
        COMMON_TECHNIQUES = List.of(TechniqueRegistry.t(0.0, "jujutsu.technique.attack1", 0.0, -9737365), TechniqueRegistry.t(1.0, "jujutsu.technique.attack2", 0.0, -9737365), TechniqueRegistry.t(2.0, "jujutsu.technique.attack3", 0.0, -9737365));
        CANCEL_DOMAIN = TechniqueRegistry.t(21.0, "jujutsu.technique.cancel_domain", 0.0, -9737365);
    }

    /**
     * Immutable technique descriptor used by the registry and skill wheel builders.
     */
    public record Technique(double selectId, String nameKey, double baseCost, int color) {
        /**
         * Returns display name for the current addon state.
         * @return the resolved display name.
         */
        public String getDisplayName() {
            return Component.translatable((String)this.nameKey).getString();
        }
    }
}

