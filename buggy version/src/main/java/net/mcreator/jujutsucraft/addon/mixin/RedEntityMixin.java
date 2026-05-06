package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.addon.BlueRedPurpleNukeMod;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.RedEntity;
import net.mcreator.jujutsucraft.procedures.AIRedProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Entity AI mixin targeting `RedEntity` tick logic. It redirects the base Red AI call, resolves the owning caster, and decides when the addon Red override should replace the vanilla behavior.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={RedEntity.class}, remap=false)
public class RedEntityMixin {
    // Logger used for Red-orb diagnostics when addon override routing disagrees with base entity state.
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Redirects the Red orb AI call and decides whether the addon Red implementation should replace the original procedure for this tick.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"m_6075_"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/AIRedProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V", remap=false), remap=false)
    private void jjkblueredpurple$redirectAIRed(LevelAccessor world, Entity entity) {
        boolean ownerDomainActive;
        if (!(entity instanceof LivingEntity)) {
            AIRedProcedure.execute((LevelAccessor)world, (Entity)entity);
            return;
        }
        LivingEntity livingRed = (LivingEntity)entity;
        // Resolve the Red orb owner up front because addon override rules depend on the caster's current domain state.
        LivingEntity owner = DomainAddonUtils.resolveOwnerEntity(world, (Entity)livingRed);
        boolean entityOpen = livingRed.getPersistentData().getBoolean("jjkbrp_open_form_active");
        boolean entityIncomplete = livingRed.getPersistentData().getBoolean("jjkbrp_incomplete_form_active");
        boolean ownerOpen = owner != null && DomainAddonUtils.isOpenDomainState(owner);
        boolean ownerIncomplete = owner != null && DomainAddonUtils.isIncompleteDomainState(owner);
        boolean ownerClosed = owner != null && DomainAddonUtils.isClosedDomainActive(owner);
        boolean bl = ownerDomainActive = ownerOpen || ownerIncomplete || ownerClosed;
        if (owner != null && livingRed.tickCount % 20 == 0 && (!entityOpen && ownerOpen || !entityIncomplete && ownerIncomplete || ownerClosed && !entityOpen && !entityIncomplete)) {
            LOGGER.debug("[GojoDomainDiag] Red entity state mismatch entity={} owner={} entityOpen={} entityIncomplete={} ownerOpen={} ownerIncomplete={} ownerClosed={} ownerDomainActive={} cnt1={} cnt6={}", new Object[]{livingRed.getClass().getSimpleName(), owner.getName().getString(), entityOpen, entityIncomplete, ownerOpen, ownerIncomplete, ownerClosed, ownerDomainActive, livingRed.getPersistentData().getDouble("cnt1"), livingRed.getPersistentData().getDouble("cnt6")});
        }
        // Only replace the original Red AI when the addon routing says this cast should stay on the addon path.
        // This must still allow crouch low-charge Red to fall back to the original AIRed flow.
        if (RedEntityMixin.jjkblueredpurple$shouldUseAddonOverride(livingRed, owner)) {
            if (owner != null && ownerDomainActive && livingRed.tickCount % 20 == 0) {
                LOGGER.debug("[GojoDomainDiag] Red using addon override inside domain owner={} open={} incomplete={} closed={} cnt1={} cnt6={}", new Object[]{owner.getName().getString(), ownerOpen, ownerIncomplete, ownerClosed, livingRed.getPersistentData().getDouble("cnt1"), livingRed.getPersistentData().getDouble("cnt6")});
            }
            // Delegate to the addon Red state machine instead of duplicating that large control flow inside the mixin itself.
            BlueRedPurpleNukeMod.handleRedFromMixin(livingRed);
            return;
        }
        AIRedProcedure.execute((LevelAccessor)world, (Entity)entity);
    }

    /**
     * Determines whether this Red entity should use the addon override based on the live addon routing check.
     * The routing helper inside Blue/Red/Purple mod is authoritative because it explicitly returns false for crouch low-charge Red.
     * @param redEntity entity involved in the current mixin operation.
     * @param owner entity involved in the current mixin operation.
     * @return whether should use addon override is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkblueredpurple$shouldUseAddonOverride(LivingEntity redEntity, LivingEntity owner) {
        if (!(redEntity instanceof RedEntity)) {
            return false;
        }
        RedEntity re = (RedEntity)redEntity;
        if (redEntity.level().isClientSide()) {
            return false;
        }
        try {
            if (((Boolean)re.getEntityData().get(RedEntity.DATA_flag_purple)).booleanValue()) {
                return false;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        // Purple-bound Red orbs must continue down the original path so the fusion sequence is not interrupted.
        if (redEntity.getPersistentData().getBoolean("flag_purple")) {
            return false;
        }
        return BlueRedPurpleNukeMod.shouldOverrideBaseRed(redEntity);
    }
}
