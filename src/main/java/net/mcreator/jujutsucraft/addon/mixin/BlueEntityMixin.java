package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.BlueEntity;
import net.mcreator.jujutsucraft.procedures.AIBlueProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Entity AI mixin targeting `BlueEntity` tick logic. It redirects the Blue AI procedure so addon aim mode can be skipped during domain states, clamps unstable charge state, and restores owner motion after the original AI finishes.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={BlueEntity.class}, remap=false)
public class BlueEntityMixin {
    // Logger used for Blue-orb diagnostics and owner-velocity restoration traces.
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Redirects the Blue orb AI call so addon aim rules can be skipped during domain or lingering states while preserving owner motion after the base AI runs.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"m_6075_"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/AIBlueProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", remap=false), remap=false)
    private void jjkblueredpurple$redirectAIBlue(LevelAccessor world, double x, double y, double z, Entity entity) {
        Vec3 ownerVelBefore;
        double cnt1;
        LivingEntity owner = DomainAddonUtils.resolveOwnerEntity(world, entity);
        boolean ownerOpen = owner != null && DomainAddonUtils.isOpenDomainState(owner);
        boolean ownerIncomplete = owner != null && DomainAddonUtils.isIncompleteDomainState(owner);
        boolean ownerClosed = owner != null && DomainAddonUtils.isClosedDomainActive(owner);
        boolean ownerDomainActive = ownerOpen || ownerIncomplete || ownerClosed;
        boolean lingering = entity.getPersistentData().getBoolean("linger_active");
        // Domain control and lingering Blue states intentionally bypass the addon aim override so the orb keeps its special scripted behavior.
        boolean skipAddonAim = ownerDomainActive || lingering;
        boolean aimActive = entity.getPersistentData().getBoolean("addon_aim_active");
        boolean addonControlledState = lingering || aimActive;
        if (!skipAddonAim && aimActive && (cnt1 = entity.getPersistentData().getDouble("cnt1")) > 35.0) {
            // Clamp `cnt1` so the addon aim path cannot push the Blue orb beyond the supported timing window.
            entity.getPersistentData().putDouble("cnt1", 35.0);
        }
        Vec3 vec3 = ownerVelBefore = owner != null ? owner.getDeltaMovement() : null;
        if (entity.tickCount % 20 == 0) {
            if (owner != null && ownerVelBefore != null) {
                LOGGER.debug("[GojoDomainDiag] Blue owner snapshot entity={} owner={} domainActive={} open={} incomplete={} closed={} linger={} aim={} addonControlled={} cnt1={} velBefore={}", new Object[]{entity.getClass().getSimpleName(), owner.getName().getString(), ownerDomainActive, ownerOpen, ownerIncomplete, ownerClosed, lingering, aimActive, addonControlledState, entity.getPersistentData().getDouble("cnt1"), ownerVelBefore});
            } else {
                LOGGER.debug("[GojoDomainDiag] Blue owner snapshot skipped entity={} ownerResolved={} linger={} aim={} addonControlled={} cnt1={}", new Object[]{entity.getClass().getSimpleName(), owner != null, lingering, aimActive, addonControlledState, entity.getPersistentData().getDouble("cnt1")});
            }
        }
        if (addonControlledState) {
            // When the addon owns Blue aim or lingering, the base Blue AI is skipped entirely so pull and damage do not double-run on top of the addon state machine.
            entity.setDeltaMovement(Vec3.ZERO);
            return;
        }
        // Run the original Blue AI after the addon preconditions are prepared so the redirect remains narrowly scoped.
        AIBlueProcedure.execute((LevelAccessor)world, (double)x, (double)y, (double)z, (Entity)entity);
        if (owner != null && ownerVelBefore != null) {
            Vec3 ownerVelAfter = owner.getDeltaMovement();
            if (!ownerVelAfter.equals((Object)ownerVelBefore)) {
                LOGGER.debug("[GojoDomainDiag] Restoring Blue owner velocity owner={} before={} after={} bluePos={} domainActive={}", new Object[]{owner.getName().getString(), ownerVelBefore, ownerVelAfter, entity.position(), ownerDomainActive});
            }
            // Restore owner velocity because the base Blue AI can unintentionally disturb the caster while it manipulates the orb.
            owner.setDeltaMovement(ownerVelBefore);
            owner.hurtMarked = true;
        }
    }
}
