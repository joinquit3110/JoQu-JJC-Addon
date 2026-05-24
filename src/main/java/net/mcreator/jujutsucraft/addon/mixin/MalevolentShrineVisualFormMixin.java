package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.util.PlayerShrineRiseController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forces Malevolent Shrine's spawned visual model to follow the selected domain
 * form instead of the caster's current HP.
 */
@Mixin(targets = "net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure", remap = false)
public abstract class MalevolentShrineVisualFormMixin {

    @Unique
    private static final float JJKBRP$FORCED_VISUAL_MAX_HEALTH = 2.0F;

    @Unique
    private static final float JJKBRP$FORCED_COMPLETE_VISUAL_HEALTH = 2.0F;

    @Unique
    private static final float JJKBRP$FORCED_INCOMPLETE_VISUAL_HEALTH = 0.0F;

    @Redirect(
            method = {"execute"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21223_()F"),
            remap = false
    )
    private static float jjkbrp$forceShrineVisualHealth(LivingEntity living) {
        int form = MalevolentShrineVisualFormMixin.jjkbrp$resolveLockedDomainForm(living);
        if (form == DomainMasteryData.FORM_INCOMPLETE) {
            return JJKBRP$FORCED_INCOMPLETE_VISUAL_HEALTH;
        }
        if (form == DomainMasteryData.FORM_CLOSED || form == DomainMasteryData.FORM_OPEN) {
            return JJKBRP$FORCED_COMPLETE_VISUAL_HEALTH;
        }
        return living.getHealth();
    }

    @Redirect(
            method = {"execute"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21233_()F"),
            remap = false
    )
    private static float jjkbrp$forceShrineVisualMaxHealth(LivingEntity living) {
        int form = MalevolentShrineVisualFormMixin.jjkbrp$resolveLockedDomainForm(living);
        if (form == DomainMasteryData.FORM_INCOMPLETE
                || form == DomainMasteryData.FORM_CLOSED
                || form == DomainMasteryData.FORM_OPEN) {
            return JJKBRP$FORCED_VISUAL_MAX_HEALTH;
        }
        return living.getMaxHealth();
    }

    @Redirect(
            method = {"execute"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"),
            remap = false
    )
    private static boolean jjkbrp$markPlayerShrineRise(
            ServerLevel level,
            Entity spawned,
            net.minecraft.world.level.LevelAccessor world,
            double x,
            double y,
            double z,
            Entity caster
    ) {
        if (caster instanceof Player player && MalevolentShrineVisualFormMixin.jjkbrp$isShrineDomain(player.getPersistentData())) {
            PlayerShrineRiseController.prepareSpawn(level, spawned, player, MalevolentShrineVisualFormMixin.jjkbrp$resolveLockedDomainForm(player));
        }
        return level.addFreshEntity(spawned);
    }

    @Unique
    private static int jjkbrp$resolveLockedDomainForm(LivingEntity living) {
        if (!(living instanceof Player)) {
            return -1;
        }
        CompoundTag nbt = living.getPersistentData();
        if (!MalevolentShrineVisualFormMixin.jjkbrp$isShrineDomain(nbt)) {
            return -1;
        }
        if (nbt.contains("jjkbrp_domain_form_cast_locked")) {
            return nbt.getInt("jjkbrp_domain_form_cast_locked");
        }
        if (nbt.contains("jjkbrp_domain_form_effective")) {
            return nbt.getInt("jjkbrp_domain_form_effective");
        }
        if (nbt.getBoolean("jjkbrp_incomplete_form_active")) {
            return DomainMasteryData.FORM_INCOMPLETE;
        }
        return -1;
    }

    @Unique
    private static boolean jjkbrp$isShrineDomain(CompoundTag nbt) {
        int domainId = (int)Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("skill_domain"));
        }
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("select"));
        }
        return domainId == 1;
    }
}
