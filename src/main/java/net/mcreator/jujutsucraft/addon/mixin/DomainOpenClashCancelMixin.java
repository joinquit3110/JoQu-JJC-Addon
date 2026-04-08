package net.mcreator.jujutsucraft.addon.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clash-initialization mixin for `DomainExpansionCreateBarrierProcedure.execute()` that seeds open-versus-closed erosion metadata and incomplete wrap-pressure metadata whenever overlapping domains collide.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionCreateBarrierProcedure.class}, remap=false)
public class DomainOpenClashCancelMixin {
    /**
     * Injects at domain startup to detect overlapping domains and initialize the correct clash metadata for erosion or incomplete wrapping.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects at method head so the addon can validate, cache, or override state before the original procedure runs.
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$initBarrierErosionClash(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (world.isClientSide()) {
            return;
        }
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        CompoundTag nbt = caster.getPersistentData();
        MobEffect domainEffect = (MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (!caster.hasEffect(domainEffect) && !DomainOpenClashCancelMixin.jjkbrp$isDomainBuildState(nbt)) {
            return;
        }
        long currentTick = serverLevel.getGameTime();
        int thisForm = DomainOpenClashCancelMixin.jjkbrp$resolveDomainForm(caster, true);
        boolean thisIsOpen = thisForm == 2;
        boolean thisIsIncomplete = thisForm == 0;
        nbt.putInt("jjkbrp_domain_form_cast_locked", thisForm);
        nbt.putInt("jjkbrp_domain_form_effective", thisForm);
        nbt.putBoolean("jjkbrp_open_form_active", thisIsOpen);
        DomainOpenClashCancelMixin.jjkbrp$clearErosionAttackerState(nbt);
        DomainOpenClashCancelMixin.jjkbrp$clearErosionDefenderState(nbt);
        DomainOpenClashCancelMixin.jjkbrp$clearIncompleteWrapState(nbt);
        DomainOpenClashCancelMixin.jjkbrp$clearWrappedByIncompleteState(nbt);
        double domainRadius = DomainAddonUtils.getActualDomainRadius(world, nbt);
        double casterRange = DomainOpenClashCancelMixin.jjkbrp$baseClashRange(world, caster, nbt);
        if (casterRange <= 0.0) {
            return;
        }
        Vec3 casterCenter = DomainAddonUtils.getDomainCenter((Entity)caster);
        double searchRange = Math.max(domainRadius * 2.0, Math.max(6.0, casterRange * 0.5 + 2.0));
        AABB searchBox = new AABB(casterCenter.x - searchRange, casterCenter.y - searchRange, casterCenter.z - searchRange, casterCenter.x + searchRange, casterCenter.y + searchRange, casterCenter.z + searchRange);
        ArrayList<LivingEntity> candidates = new ArrayList<LivingEntity>();
        for (Entity e : world.getEntities(null, searchBox)) {
            if (e == caster || !(e instanceof LivingEntity)) continue;
            LivingEntity le2 = (LivingEntity)e;
            CompoundTag candNbt = le2.getPersistentData();
            if (!le2.hasEffect(domainEffect) && !DomainOpenClashCancelMixin.jjkbrp$isDomainBuildState(candNbt) || candNbt.getBoolean("Failed") || candNbt.getBoolean("DomainDefeated")) continue;
            candidates.add(le2);
        }
        if (candidates.isEmpty()) {
            return;
        }
        nbt.putLong("jjkbrp_last_clash_contact_tick", currentTick);
        if (thisIsOpen) {
            LivingEntity closestClosed = candidates.stream().filter(le -> {
                int candidateForm = DomainOpenClashCancelMixin.jjkbrp$resolveDomainForm(le, false);
                if (candidateForm != 1) {
                    return false;
                }
                return DomainOpenClashCancelMixin.jjkbrp$isWithinBaseClashWindow(world, caster, nbt, le, le.getPersistentData());
            }).min(Comparator.comparingDouble(le -> le.distanceToSqr((Entity)caster))).orElse(null);
            if (closestClosed != null) {
                        // Open-versus-closed collisions seed the barrier erosion state used by the clash penalty mixin on later ticks.
                DomainOpenClashCancelMixin.initErosionClash(caster, closestClosed);
                nbt.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            } else {
                DomainOpenClashCancelMixin.jjkbrp$clearErosionAttackerState(nbt);
            }
        }
        if (!thisIsOpen && !thisIsIncomplete) {
            LivingEntity closestOpen = candidates.stream().filter(le -> {
                if (DomainOpenClashCancelMixin.jjkbrp$resolveDomainForm(le, false) != 2) {
                    return false;
                }
                return DomainOpenClashCancelMixin.jjkbrp$isWithinBaseClashWindow(world, le, le.getPersistentData(), caster, nbt);
            }).min(Comparator.comparingDouble(le -> le.distanceToSqr((Entity)caster))).orElse(null);
            if (closestOpen != null) {
                        // Open-versus-closed collisions seed the barrier erosion state used by the clash penalty mixin on later ticks.
                DomainOpenClashCancelMixin.initErosionClash(closestOpen, caster);
                nbt.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            } else {
                DomainOpenClashCancelMixin.jjkbrp$clearErosionDefenderState(nbt);
            }
        }
        if (thisIsIncomplete) {
            LivingEntity closestComplete = candidates.stream().filter(le -> {
                if (DomainOpenClashCancelMixin.jjkbrp$resolveDomainForm(le, false) == 0) {
                    return false;
                }
                return DomainOpenClashCancelMixin.jjkbrp$isWithinBaseClashWindow(world, caster, nbt, le, le.getPersistentData());
            }).min(Comparator.comparingDouble(le -> le.distanceToSqr((Entity)caster))).orElse(null);
            if (closestComplete != null) {
                        // Incomplete-versus-complete collisions seed the wrap-pressure state used during later clash ticks.
                DomainOpenClashCancelMixin.initIncompleteWrapClash(caster, closestComplete);
                nbt.putLong("jjkbrp_last_clash_contact_tick", currentTick);
            } else {
                DomainOpenClashCancelMixin.jjkbrp$clearIncompleteWrapState(caster.getPersistentData());
                DomainOpenClashCancelMixin.jjkbrp$clearWrappedByIncompleteState(caster.getPersistentData());
            }
        }
    }

    /**
     * Seeds open-versus-closed clash metadata so later ticks can erode the defending barrier over time.
     * @param openCaster open caster used by this method.
     * @param closedCaster closed caster used by this method.
     */
                        // Open-versus-closed collisions seed the barrier erosion state used by the clash penalty mixin on later ticks.
    private static void initErosionClash(LivingEntity openCaster, LivingEntity closedCaster) {
        CompoundTag openNbt = openCaster.getPersistentData();
        CompoundTag closedNbt = closedCaster.getPersistentData();
        String targetUuid = closedCaster.getStringUUID();
        boolean sameTarget = targetUuid.equals(openNbt.getString("jjkbrp_erosion_target_uuid"));
        DomainOpenClashCancelMixin.jjkbrp$clearErosionDefenderState(openNbt);
        DomainOpenClashCancelMixin.jjkbrp$clearErosionAttackerState(closedNbt);
        openNbt.putBoolean("jjkbrp_is_eroding_barrier", true);
        openNbt.putString("jjkbrp_erosion_target_uuid", targetUuid);
        closedNbt.putBoolean("jjkbrp_barrier_under_attack", true);
        closedNbt.putString("jjkbrp_open_attacker_uuid", openCaster.getStringUUID());
        if (!sameTarget || !closedNbt.contains("jjkbrp_barrier_erosion_total")) {
            closedNbt.putDouble("jjkbrp_barrier_erosion_total", 0.0);
        }
        if (!closedNbt.contains("jjkbrp_barrier_refinement")) {
            closedNbt.putDouble("jjkbrp_barrier_refinement", 0.5);
        }
    }

    /**
     * Seeds incomplete-versus-complete clash metadata so later ticks can apply wrap pressure to the defending domain.
     * @param incompleteCaster incomplete caster used by this method.
     * @param wrappedTarget wrapped target used by this method.
     */
                        // Incomplete-versus-complete collisions seed the wrap-pressure state used during later clash ticks.
    private static void initIncompleteWrapClash(LivingEntity incompleteCaster, LivingEntity wrappedTarget) {
        CompoundTag incompleteNbt = incompleteCaster.getPersistentData();
        CompoundTag targetNbt = wrappedTarget.getPersistentData();
        DomainOpenClashCancelMixin.jjkbrp$clearIncompleteWrapState(incompleteNbt);
        DomainOpenClashCancelMixin.jjkbrp$clearWrappedByIncompleteState(targetNbt);
        incompleteNbt.putBoolean("jjkbrp_incomplete_wrap_active", true);
        incompleteNbt.putString("jjkbrp_incomplete_wrap_target_uuid", wrappedTarget.getStringUUID());
        targetNbt.putBoolean("jjkbrp_wrapped_by_incomplete", true);
        targetNbt.putString("jjkbrp_incomplete_wrapper_uuid", incompleteCaster.getStringUUID());
    }

    /**
     * Performs clear incomplete wrap state for this mixin.
     * @param nbt persistent data container used by this helper.
     */
    private static void jjkbrp$clearIncompleteWrapState(CompoundTag nbt) {
        nbt.remove("jjkbrp_incomplete_wrap_active");
        nbt.remove("jjkbrp_incomplete_wrap_target_uuid");
    }

    /**
     * Performs clear wrapped by incomplete state for this mixin.
     * @param nbt persistent data container used by this helper.
     */
    private static void jjkbrp$clearWrappedByIncompleteState(CompoundTag nbt) {
        nbt.remove("jjkbrp_wrapped_by_incomplete");
        nbt.remove("jjkbrp_incomplete_wrapper_uuid");
    }

    /**
     * Performs clear erosion attacker state for this mixin.
     * @param nbt persistent data container used by this helper.
     */
    private static void jjkbrp$clearErosionAttackerState(CompoundTag nbt) {
        nbt.remove("jjkbrp_is_eroding_barrier");
        nbt.remove("jjkbrp_erosion_target_uuid");
    }

    /**
     * Performs clear erosion defender state for this mixin.
     * @param nbt persistent data container used by this helper.
     */
    private static void jjkbrp$clearErosionDefenderState(CompoundTag nbt) {
        nbt.remove("jjkbrp_barrier_under_attack");
        nbt.remove("jjkbrp_open_attacker_uuid");
        nbt.remove("jjkbrp_barrier_erosion_total");
    }

    /**
     * Performs is domain build state for this mixin.
     * @param nbt persistent data container used by this helper.
     * @return whether is domain build state is true for the current runtime state.
     */
    private static boolean jjkbrp$isDomainBuildState(CompoundTag nbt) {
        if (nbt == null) {
            return false;
        }
        boolean hasDomainSkill = nbt.getDouble("select") != 0.0 || nbt.getDouble("skill_domain") != 0.0 || nbt.getDouble("jjkbrp_domain_id_runtime") != 0.0;
        return hasDomainSkill && (nbt.getDouble("cnt3") > 0.0 || nbt.contains("x_pos_doma"));
    }

    /**
     * Performs is open domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is open domain state is true for the current runtime state.
     */
    private static boolean jjkbrp$isOpenDomainState(LivingEntity entity) {
        return DomainAddonUtils.isOpenDomainState(entity);
    }

    /**
     * Resolves domain form from the currently available runtime data.
     * @param entity entity involved in the current mixin operation.
     * @param preferCapability prefer capability used by this method.
     * @return the resulting resolve domain form value.
     */
    private static int jjkbrp$resolveDomainForm(LivingEntity entity, boolean preferCapability) {
        Integer resolved;
        if (entity == null) {
            return 1;
        }
        if (DomainOpenClashCancelMixin.jjkbrp$isIncompleteDomainState(entity)) {
            return 0;
        }
        if (preferCapability && entity instanceof Player) {
            Player capabilityPlayer = (Player)entity;
            resolved = capabilityPlayer.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).map(data -> {
                boolean hasOpenAdv = DomainOpenClashCancelMixin.jjkbrp$hasOpenBarrierAdvancement(capabilityPlayer);
                return DomainMasteryData.sanitizeFormSelection(data.getDomainTypeSelected(), data.getDomainMasteryLevel(), hasOpenAdv);
            }).orElse(-1);
            if (resolved >= 0 && resolved <= 2) {
                return resolved;
            }
        }
        if (DomainOpenClashCancelMixin.jjkbrp$isOpenDomainState(entity)) {
            return 2;
        }
        if (entity instanceof Player) {
            Player fallbackPlayer = (Player)entity;
            resolved = fallbackPlayer.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).map(data -> {
                boolean hasOpenAdv = DomainOpenClashCancelMixin.jjkbrp$hasOpenBarrierAdvancement(fallbackPlayer);
                return DomainMasteryData.sanitizeFormSelection(data.getDomainTypeSelected(), data.getDomainMasteryLevel(), hasOpenAdv);
            }).orElse(-1);
            if (resolved >= 0 && resolved <= 2) {
                return resolved;
            }
        }
        return 1;
    }

    /**
     * Performs has open barrier advancement for this mixin.
     * @param player entity involved in the current mixin operation.
     * @return whether has open barrier advancement is true for the current runtime state.
     */
    private static boolean jjkbrp$hasOpenBarrierAdvancement(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer sp = (ServerPlayer)player;
        if (sp.server == null) {
            return false;
        }
        try {
            Advancement adv = sp.server.getAdvancements().getAdvancement(new ResourceLocation("jujutsucraft", "mastery_open_barrier_type_domain"));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = sp.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Performs is incomplete domain state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @return whether is incomplete domain state is true for the current runtime state.
     */
    private static boolean jjkbrp$isIncompleteDomainState(LivingEntity entity) {
        return DomainAddonUtils.isIncompleteDomainState(entity);
    }

    /**
     * Performs is within base clash window for this mixin.
     * @param world world access used by the current mixin callback.
     * @param source source used by this method.
     * @param sourceNbt source nbt used by this method.
     * @param target entity involved in the current mixin operation.
     * @param targetNbt target nbt used by this method.
     * @return whether is within base clash window is true for the current runtime state.
     */
    private static boolean jjkbrp$isWithinBaseClashWindow(LevelAccessor world, LivingEntity source, CompoundTag sourceNbt, LivingEntity target, CompoundTag targetNbt) {
        if (source == null || target == null) {
            return false;
        }
        Vec3 sourceCenter = DomainAddonUtils.getDomainCenter((Entity)source);
        Vec3 targetBody = new Vec3(target.getX(), target.getY() + (double)target.getBbHeight() * 0.5, target.getZ());
        double dx = sourceCenter.x - targetBody.x;
        double dy = sourceCenter.y - targetBody.y;
        double dz = sourceCenter.z - targetBody.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double sourceRange = DomainOpenClashCancelMixin.jjkbrp$baseClashRange(world, source, sourceNbt);
        if (sourceRange <= 0.0) {
            return false;
        }
        double threshold = Math.max(2.0, sourceRange * 0.5);
        return distanceSq < threshold * threshold;
    }

    /**
     * Performs base clash range for this mixin.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return the resulting base clash range value.
     */
    private static double jjkbrp$baseClashRange(LevelAccessor world, LivingEntity entity, CompoundTag nbt) {
        double radius = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(world, nbt));
        return radius * (DomainOpenClashCancelMixin.jjkbrp$isOpenDomainState(entity) ? 18.0 : 2.0);
    }

    /**
     * Performs is base startup open state for this mixin.
     * @param entity entity involved in the current mixin operation.
     * @param nbt persistent data container used by this helper.
     * @return whether is base startup open state is true for the current runtime state.
     */
    private static boolean jjkbrp$isBaseStartupOpenState(LivingEntity entity, CompoundTag nbt) {
        int resolvedId;
        if (entity == null || nbt == null) {
            return false;
        }
        if (!nbt.contains("cnt2") || nbt.getDouble("cnt2") <= 0.0) {
            return false;
        }
        if (nbt.getDouble("cnt7") <= 0.0 && !nbt.contains("x_pos_doma")) {
            return false;
        }
        double domainId = nbt.getDouble("select");
        if (domainId == 0.0) {
            domainId = nbt.getDouble("skill_domain");
        }
        if (domainId == 0.0) {
            domainId = nbt.getDouble("jjkbrp_domain_id_runtime");
        }
        return (resolvedId = (int)Math.round(domainId)) == 1 || resolvedId == 18;
    }
}
