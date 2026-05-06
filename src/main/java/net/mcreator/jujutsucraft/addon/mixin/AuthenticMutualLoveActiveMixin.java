package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.yuta.YutaCopyStore;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.AuthenticMutualLoveActiveProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Domain-specific safety and radius tuning for Authentic Mutual Love's active sword/summon path.
 */
@Mixin(value = {AuthenticMutualLoveActiveProcedure.class}, remap = false)
public class AuthenticMutualLoveActiveMixin {
    private static final ThreadLocal<LevelAccessor> jjkbrp$activeWorld = new ThreadLocal<>();
    private static final ThreadLocal<Entity> jjkbrp$activeEntity = new ThreadLocal<>();

    /**
     * Incomplete Authentic Mutual Love must not spawn copied-technique swords or Jacob's Ladder helpers.
     */
    @Inject(method = {"execute"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private static void jjkbrp$cancelIncompleteAuthenticMutualLove(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        jjkbrp$activeWorld.set(world);
        jjkbrp$activeEntity.set(entity);
        if (entity instanceof LivingEntity && DomainAddonUtils.isIncompleteDomainState((LivingEntity)entity)) {
            jjkbrp$clearAuthenticMutualLoveContext();
            ci.cancel();
        }
    }

    @Inject(method = {"execute"}, at = {@At(value = "TAIL")}, remap = false)
    private static void jjkbrp$bridgeRikaCopyStore(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        YutaCopyStore.bridgeAuthenticMutualLove(world, x, y, z, entity);
    }

    @Inject(method = {"execute"}, at = {@At(value = "RETURN")}, remap = false)
    private static void jjkbrp$clearAuthenticMutualLoveContext(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        jjkbrp$clearAuthenticMutualLoveContext();
    }

    private static void jjkbrp$clearAuthenticMutualLoveContext() {
        jjkbrp$activeWorld.remove();
        jjkbrp$activeEntity.remove();
    }

    /**
     * Scales only the Math.round(num3) argument used by the sword spawn loop count.
     *
     * <p>The original method places swords on a ring at {@code DomainExpansionRadius - 4}. When the
     * domain radius grows, the distribution ring grows too, but vanilla {@code num3} stays based only on
     * copied-technique cooldown. A low linear cap makes large domains look sparse, so use a guarded
     * super-linear radius multiplier: denser than pure radius scaling, still far below full area scaling.</p>
     *
     * <p>The first Math.round(double) call in the original method is the attack interval; the second is
     * the spawn-loop count. HP and rotation rounding occur later and are intentionally untouched.</p>
     */
    @ModifyArg(method = {"execute"}, at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(D)J", ordinal = 1, remap = false), index = 0, remap = false)
    private static double jjkbrp$scaleAuthenticMutualLoveSwordCount(double originalCount) {
        LevelAccessor world = jjkbrp$activeWorld.get();
        Entity entity = jjkbrp$activeEntity.get();
        if (world == null || !(entity instanceof LivingEntity) || DomainAddonUtils.isIncompleteDomainState((LivingEntity)entity)) {
            return originalCount;
        }
        CompoundTag nbt = entity.getPersistentData();
        double baseRadius = nbt.contains("jjkbrp_base_domain_radius") ? Math.max(1.0, nbt.getDouble("jjkbrp_base_domain_radius")) : 16.0;
        double actualRadius = DomainAddonUtils.getActualDomainRadius(world, nbt);
        double radiusScale = Math.max(0.1, actualRadius / baseRadius);
        double multiplier = Math.max(0.6, Math.min(Math.pow(radiusScale, 1.35), 2.75));
        return Math.max(1.0, originalCount * multiplier);
    }
}
