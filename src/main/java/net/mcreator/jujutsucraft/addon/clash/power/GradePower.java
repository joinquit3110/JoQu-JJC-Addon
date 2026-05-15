package net.mcreator.jujutsucraft.addon.clash.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.world.effect.MobEffect;

public final class GradePower {
    private GradePower() {
    }

    public static double multiplier(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return npcMultiplier(entity);
        }
        if (has(player, "sorcerer_grade_special")) {
            return 1.45;
        }
        if (has(player, "sorcerer_grade_1")) {
            return 1.32;
        }
        if (has(player, "sorcerer_grade_1_semi")) {
            return 1.24;
        }
        if (has(player, "sorcerer_grade_2")) {
            return 1.16;
        }
        if (has(player, "sorcerer_grade_2_semi")) {
            return 1.10;
        }
        if (has(player, "sorcerer_grade_3")) {
            return 1.05;
        }
        if (has(player, "sorcerer_grade_4")) {
            return 1.02;
        }
        return 1.0;
    }


    private static double npcMultiplier(LivingEntity entity) {
        if (entity == null) {
            return 1.0;
        }
        String typeName = entity.getType().toString().toLowerCase(java.util.Locale.ROOT);
        String displayName = entity.getName().getString().toLowerCase(java.util.Locale.ROOT);
        boolean sukuna = typeName.contains("sukuna") || displayName.contains("sukuna")
            || entity.hasEffect((MobEffect)JujutsucraftModMobEffects.SUKUNA_EFFECT.get());
        if (sukuna) {
            return 1.12;
        }
        double maxHealth = Math.max(20.0, entity.getMaxHealth());
        double healthTier = Math.min(0.35, (maxHealth - 20.0) / 220.0);
        return 1.30 + healthTier;
    }
    private static boolean has(ServerPlayer player, String path) {
        if (player == null || player.server == null) {
            return false;
        }
        Advancement advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation("jujutsucraft", path));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}


