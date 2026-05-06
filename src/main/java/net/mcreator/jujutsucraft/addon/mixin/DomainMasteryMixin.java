package net.mcreator.jujutsucraft.addon.mixin;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.List;
import net.mcreator.jujutsucraft.addon.DomainFormPolicy;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.DomainActiveProcedure;
import net.mcreator.jujutsucraft.procedures.DomainExpansionOnEffectActiveTickProcedure;
import net.mcreator.jujutsucraft.procedures.EffectCharactorProcedure;
import net.mcreator.jujutsucraft.procedures.PlayAnimationProcedure;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Primary domain runtime mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()`. It blocks incomplete-domain sure-hit behavior, prevents invalid neutralization paths, applies mastery effects each tick, coordinates open-domain visuals, synchronizes runtime state, and enforces open or incomplete range-cancel rules.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionOnEffectActiveTickProcedure.class}, remap=false)
public abstract class DomainMasteryMixin {
    // Logger used for incomplete-domain and open-domain runtime diagnostics.
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();
    @Unique
    private static final ThreadLocal<CompoundTag> JJKBRP$maskedIncompleteRuntime = new ThreadLocal();


    // ===== INCOMPLETE ACTIVE-BEHAVIOR GATING =====
    /**
     * Masks domain-routing identifiers during the base active tick for incomplete summon or control archetypes so the vanilla active path cannot leak full-domain summons or special active behavior.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    @Inject(method={"execute"}, at={@At(value="HEAD")}, remap=false)
    private static void jjkbrp$maskIncompleteActiveBehavior(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        JJKBRP$maskedIncompleteRuntime.remove();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        if (!DomainMasteryMixin.jjkbrp$shouldMaskIncompleteActiveBehavior(caster)) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        CompoundTag backup = new CompoundTag();
        backup.putBoolean("has_select", data.contains("select"));
        if (data.contains("select")) {
            backup.putDouble("select", data.getDouble("select"));
        }
        backup.putBoolean("has_skill_domain", data.contains("skill_domain"));
        if (data.contains("skill_domain")) {
            backup.putDouble("skill_domain", data.getDouble("skill_domain"));
        }
        backup.putBoolean("has_runtime_domain_id", data.contains("jjkbrp_domain_id_runtime"));
        if (data.contains("jjkbrp_domain_id_runtime")) {
            backup.putDouble("jjkbrp_domain_id_runtime", data.getDouble("jjkbrp_domain_id_runtime"));
        }
        backup.putBoolean("DomainAttack", data.getBoolean("DomainAttack"));
        JJKBRP$maskedIncompleteRuntime.set(backup);
        data.putDouble("select", 0.0);
        data.putDouble("skill_domain", 0.0);
        data.remove("jjkbrp_domain_id_runtime");
        data.putBoolean("DomainAttack", false);
    }

    @Unique
    private static boolean jjkbrp$shouldMaskIncompleteActiveBehavior(LivingEntity caster) {
        int domainId;
        if (caster == null || !DomainAddonUtils.isIncompleteDomainState(caster)) {
            return false;
        }
        CompoundTag data = caster.getPersistentData();
        String archetypeName = data.getString("jjkbrp_domain_archetype");
        DomainFormPolicy.Archetype archetype = null;
        if (archetypeName != null && !archetypeName.isEmpty()) {
            try {
                archetype = DomainFormPolicy.Archetype.valueOf(archetypeName);
            }
            catch (IllegalArgumentException ignored) {
                archetype = null;
            }
        }
        if (archetype == null) {
            domainId = (int)Math.round(data.getDouble("jjkbrp_domain_id_runtime"));
            if (domainId <= 0) {
                domainId = (int)Math.round(data.getDouble("skill_domain"));
            }
            if (domainId <= 0) {
                domainId = (int)Math.round(data.getDouble("select"));
            }
            archetype = DomainFormPolicy.policyOf(domainId).archetype();
        }
        return archetype == DomainFormPolicy.Archetype.SUMMON || archetype == DomainFormPolicy.Archetype.CONTROL;
    }

    @Unique
    private static void jjkbrp$restoreMaskedIncompleteActiveBehavior(LivingEntity caster) {
        CompoundTag backup = JJKBRP$maskedIncompleteRuntime.get();
        JJKBRP$maskedIncompleteRuntime.remove();
        if (caster == null || backup == null) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        if (backup.getBoolean("has_select")) {
            data.putDouble("select", backup.getDouble("select"));
        } else {
            data.remove("select");
        }
        if (backup.getBoolean("has_skill_domain")) {
            data.putDouble("skill_domain", backup.getDouble("skill_domain"));
        } else {
            data.remove("skill_domain");
        }
        if (backup.getBoolean("has_runtime_domain_id")) {
            data.putDouble("jjkbrp_domain_id_runtime", backup.getDouble("jjkbrp_domain_id_runtime"));
        } else {
            data.remove("jjkbrp_domain_id_runtime");
        }
        data.putBoolean("DomainAttack", backup.getBoolean("DomainAttack"));
    }


    // ===== INCOMPLETE SURE-HIT GATING =====
    /**
     * Redirects the call to `DomainActiveProcedure.execute()` so incomplete domains can skip sure-hit and normal domain-effect application entirely.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/DomainActiveProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", remap=false), remap=false)
    private static void jjkbrp$runDomainActiveProcedure(LevelAccessor world, double x, double y, double z, Entity entity) {
        // Re-stamp runtime form flags first because several older base procedure paths can partially clear the incomplete-domain markers.
        DomainMasteryMixin.jjkbrp$reStampIncompleteFlags(entity);
        // Keep non-hazardous incomplete active visuals/support, but suppress full-domain active procedures that are sure-hit carriers.
        boolean incomplete = entity instanceof LivingEntity
                && DomainAddonUtils.isIncompleteDomainState((LivingEntity)entity);
        if (incomplete) {
            entity.getPersistentData().putBoolean("DomainAttack", false);
            if (DomainMasteryMixin.jjkbrp$shouldSuppressIncompleteDomainActive((LivingEntity)entity)) {
                return;
            }
        }
        DomainActiveProcedure.execute((LevelAccessor)world, (double)x, (double)y, (double)z, (Entity)entity);
        if (incomplete) {
            entity.getPersistentData().putBoolean("DomainAttack", false);
        }
    }

    /**
     * Redirects the character-effect procedure call and suppresses it for incomplete domains that should not apply full sure-hit behavior.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param target entity involved in the current mixin operation.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/mcreator/jujutsucraft/procedures/EffectCharactorProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V", remap=false), remap=false)
    private static void jjkbrp$skipEffectCharacterForIncomplete(LevelAccessor world, Entity caster, Entity target) {
        LivingEntity livingCaster;
        DomainMasteryMixin.jjkbrp$reStampIncompleteFlags(caster);
        if (caster instanceof LivingEntity && DomainAddonUtils.isIncompleteDomainState(livingCaster = (LivingEntity)caster)) {
            return;
        }
        EffectCharactorProcedure.execute((LevelAccessor)world, (Entity)caster, (Entity)target);
    }

    /**
     * Redirects `LivingEntity.addEffect` so incomplete domains cannot incorrectly apply neutralization to other targets.
     * @param target entity involved in the current mixin operation.
     * @param effect effect instance processed by this helper.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param caster entity involved in the current mixin operation.
     * @return whether skip neutralization for incomplete is true for the current runtime state.
     */
    // Redirects the targeted invocation so the addon can selectively replace that single call without copying the whole original method.
    @Redirect(method={"execute"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z", remap=false), remap=false)
    private static boolean jjkbrp$skipNeutralizationForIncomplete(LivingEntity target, MobEffectInstance effect, LevelAccessor world, double x, double y, double z, Entity caster) {
        DomainMasteryMixin.jjkbrp$reStampIncompleteFlags(caster);
        if (effect != null && caster instanceof LivingEntity) {
            LivingEntity livingCaster = (LivingEntity)caster;
            // Prevent incomplete domains from applying the neutralization effect to other entities through the normal base pathway.
            if (target != caster && effect.getEffect() == JujutsucraftModMobEffects.NEUTRALIZATION.get() && DomainAddonUtils.isIncompleteDomainState(livingCaster)) {
                return false;
            }
        }
        return target.addEffect(effect);
    }


    // ===== STATE RESTAMPING =====
    /**
     * Rebuilds incomplete-domain runtime flags from the stored cast-lock state whenever older base logic risks clearing them.
     * @param entity entity involved in the current mixin operation.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static void jjkbrp$reStampIncompleteFlags(Entity entity) {
        if (entity == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains("jjkbrp_domain_form_cast_locked")) {
            return;
        }
        if (data.getInt("jjkbrp_domain_form_cast_locked") == 0) {
            if (!data.getBoolean("jjkbrp_incomplete_form_active")) {
                data.putBoolean("jjkbrp_incomplete_form_active", true);
            }
            data.remove("jjkbrp_incomplete_session_active");
            data.putBoolean("DomainAttack", false);
        }
    }


    // ===== POST-TICK MASTERY FLOW =====
    /**
     * Injects after each active domain tick to synchronize mastery state, award XP, apply property effects, maintain cleanup entities, fire opening VFX, and enforce range cancellation rules.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$applyDomainMasteryEffects(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity)) {
            JJKBRP$maskedIncompleteRuntime.remove();
            return;
        }
        LivingEntity caster = (LivingEntity)entity;
        DomainMasteryMixin.jjkbrp$restoreMaskedIncompleteActiveBehavior(caster);
        if (world.isClientSide()) {
            return;
        }
        CompoundTag casterNbt = caster.getPersistentData();
        int runtimeDomainId = DomainMasteryMixin.jjkbrp$resolveRuntimeDomainId(casterNbt);
        if (DomainMasteryMixin.jjkbrp$shouldReplayDomainSpecificActive(runtimeDomainId, caster) && casterNbt.getBoolean("StartDomainAttack")) {
            DomainActiveProcedure.execute((LevelAccessor)world, x, y, z, (Entity)caster);
        }
        if (caster instanceof Player) {
            Player masteryPlayer = (Player)caster;
            masteryPlayer.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
                // Mirror mastery state into persistent data so other mixins and deferred cleanup paths can read it without reopening the capability.
                masteryPlayer.getPersistentData().putInt("jjkbrp_domain_mastery_level", data.getDomainMasteryLevel());
                masteryPlayer.getPersistentData().putInt("jjkbrp_domain_form", data.getDomainTypeSelected());
            });
        }
        DomainMasteryMixin.jjkbrp$reStampIncompleteFlags((Entity)caster);
        if (DomainAddonUtils.isIncompleteDomainState(caster)) {
            caster.getPersistentData().putBoolean("DomainAttack", false);
        }
        if (caster instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)caster;
            if (world instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)world;
                double cnt3 = caster.getPersistentData().getDouble("cnt3");
                if (cnt3 > 0.0) {
                    double xpPerTick = cnt3 >= 20.0 ? 1.0 : 0.5;
                    sp.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
                        data.addDomainXP(xpPerTick);
                        if (serverLevel.getGameTime() % 20L == 0L) {
                            data.syncToClient(sp);
                        }
                    });
                }
            }
        }
        // Apply every mastery property after the base active tick so the addon effects layer cleanly on top of the original domain behavior.
        DomainMasteryMixin.applyPropertyEffects(world, caster);
        DomainMasteryMixin.jjkbrp$applyIncompleteZoneOnlyBuff(caster);
        if (!DomainAddonUtils.isIncompleteDomainState(caster) && world instanceof ServerLevel) {
            DomainMasteryMixin.jjkbrp$supplementMalevolentShrineVFX((ServerLevel)world, caster);
        }
        if (caster instanceof Player) {
            Player domainPlayer = (Player)caster;
            if (world instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)world;
                // Keep the cleanup entity alive and centered while the domain is active so expiration cleanup has a reliable restore anchor later.
                DomainMasteryMixin.jjkbrp$stabilizeDomainCleanupEntity(serverLevel, domainPlayer);
                DomainMasteryMixin.jjkbrp$decrementGracePeriod(serverLevel, domainPlayer);
                boolean openDomainActive = DomainMasteryMixin.jjkbrp$isOpenDomainActive((LivingEntity)domainPlayer);
                if (openDomainActive) {
                    // Open domains cache a stable center because later visuals and range checks must keep using the opening point instead of the moving caster.
                    DomainMasteryMixin.jjkbrp$cacheOpenDomainCenter((LivingEntity)domainPlayer);
                }
                if (DomainMasteryMixin.jjkbrp$shouldFireOpeningVFX(serverLevel, domainPlayer, openDomainActive)) {
                    // Fire the one-shot opening burst only after all center and timing state is fully prepared.
                    DomainMasteryMixin.jjkbrp$fireOpeningVFX(serverLevel, domainPlayer);
                }
                if (openDomainActive) {
                    // After the opening burst, keep the domain visually alive with the continuous open-domain particle and debuff loop.
                    DomainMasteryMixin.applyOpenDomainVFX(serverLevel, caster);
                    // Open domains cancel themselves if the caster moves too far from the allowed operating center.
                    DomainMasteryMixin.checkOpenDomainRangeCancel(serverLevel, domainPlayer);
                } else if (DomainAddonUtils.isIncompleteDomainState((LivingEntity)domainPlayer)) {
                    // Incomplete domains use a separate range-cancel rule because they behave like unstable partial shells rather than true open domains.
                    DomainMasteryMixin.checkIncompleteDomainRangeCancel(serverLevel, domainPlayer);
                }
            }
        }
    }

    @Unique
    private static int jjkbrp$resolveRuntimeDomainId(CompoundTag nbt) {
        if (nbt == null) {
            return 0;
        }
        int domainId = (int)Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("skill_domain"));
        }
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("select"));
        }
        return domainId;
    }

    @Unique
    private static boolean jjkbrp$shouldSuppressIncompleteDomainActive(LivingEntity caster) {
        if (caster == null || !DomainAddonUtils.isIncompleteDomainState(caster)) {
            return false;
        }
        int domainId = DomainMasteryMixin.jjkbrp$resolveRuntimeDomainId(caster.getPersistentData());
        // Unlimited Void's active procedure applies BRAIN_DAMAGE / hard stun as its sure-hit carrier.
        // Idle Death Gamble's active procedure owns title/slot spin and should not run from incomplete form as a full-domain replay.
        return domainId == 2 || domainId == 29;
    }

    @Unique
    private static boolean jjkbrp$shouldReplayDomainSpecificActive(int runtimeDomainId, LivingEntity caster) {
        // OG routes reviewed domain-specific active effects through DomainActiveProcedure; only Idle Death Gamble needs the legacy replay hook.
        if (runtimeDomainId != 29 || caster == null) {
            return false;
        }
        return !DomainAddonUtils.isIncompleteDomainState(caster);
    }

    /**
     * Keeps the cleanup entity centered on the live domain and resets its break counters so it does not tear down the barrier while the domain still exists.
     * @param world world access used by the current mixin callback.
    * @param caster entity involved in the current mixin operation.
     */
 
    private static void jjkbrp$supplementMalevolentShrineVFX(ServerLevel world, LivingEntity caster) {
        CompoundTag nbt = caster.getPersistentData();
        int domainId = (int)Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("skill_domain"));
        }
        if (domainId != 1) {
            return;
        }
        double radiusMul = nbt.getDouble("jjkbrp_radius_multiplier");
        if (Math.abs(radiusMul) < 1.0E-4) {
            radiusMul = 1.0;
        }
        long gameTime = world.getGameTime();
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)caster);
        double baseRadius = nbt.contains("jjkbrp_base_domain_radius") ? nbt.getDouble("jjkbrp_base_domain_radius") : 16.0;
        double scaledRadius = Math.max(1.0, baseRadius * Math.max(0.5, radiusMul));
        double range = scaledRadius * 2.0;
        if (!nbt.getBoolean("Failed") && gameTime % 5L == 0L) {
            DomainMasteryMixin.jjkbrp$sendMalevolentShrineSlashVFX(world, center, range);
        }
        if (radiusMul < 1.15) {
            return;
        }
        if (gameTime % 3L != 0L) {
            return;
        }
        int extraCount = (int)Math.round(Math.max(8.0, (radiusMul - 1.0) * 60.0));
        for (int i = 0; i < extraCount; ++i) {
            double ox = (Math.random() - 0.5) * range * 0.5;
            double oy = (Math.random() - 0.5) * range * 0.25;
            double oz = (Math.random() - 0.5) * range * 0.5;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, center.x + ox, center.y + 1.0 + oy, center.z + oz, 1, 0.6, 0.3, 0.6, 0.03);
        }
        int sparkCount = Math.max(4, extraCount / 3);
        for (int i = 0; i < sparkCount; ++i) {
            double angle = Math.random() * Math.PI * 2.0;
            double r = Math.random() * range * 0.4;
            double px = center.x + Math.cos(angle) * r;
            double pz = center.z + Math.sin(angle) * r;
            double py = center.y + 0.5 + Math.random() * 3.0;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.CRIT, px, py, pz, 2, 0.8, 0.4, 0.8, 0.05);
        }
    }

    @Unique
    private static void jjkbrp$sendMalevolentShrineSlashVFX(ServerLevel world, Vec3 center, double range) {
        if (world == null || center == null) {
            return;
        }
        double spread = Math.max(1.0, range * 0.25);
        int count = (int)Math.round(Math.max(16.0, Math.min(4.0 * range, 256.0)));
        String command = "particle jujutsucraft:particle_slash_large " + center.x + " " + center.y + " " + center.z + " " + spread + " " + spread + " " + spread + " 0.01 " + count + " normal";
        world.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, center, Vec2.ZERO, world, 4, "", net.minecraft.network.chat.Component.literal(""), world.getServer(), null).withSuppressedOutput(), command);
    }

    // ===== CLEANUP ENTITY SUPPORT =====
    private static void jjkbrp$stabilizeDomainCleanupEntity(ServerLevel world, Player player) {
        double actualRadius;
        if (!player.getPersistentData().contains("x_pos_doma")) {
            return;
        }
        if (!player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)player);
        DomainExpansionEntityEntity domainEntity = DomainMasteryMixin.jjkbrp$findCleanupEntity(world, center, actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, player.getPersistentData()));
        if (domainEntity == null) {
            return;
        }
        CompoundTag entityNbt = domainEntity.getPersistentData();
        entityNbt.putDouble("x_pos", center.x);
        entityNbt.putDouble("y_pos", center.y);
        entityNbt.putDouble("z_pos", center.z);
        entityNbt.putDouble("range", actualRadius);
        entityNbt.putBoolean("Break", false);
        entityNbt.putDouble("cnt_life2", 0.0);
        entityNbt.putDouble("cnt_break", 0.0);
    }

    /**
     * Finds the best cleanup entity for the current domain center by preferring the closest match inside the expected radius.
     * @param world world access used by the current mixin callback.
     * @param center center used by this method.
     * @param actualRadius distance value used by this runtime calculation.
     * @return the resulting find cleanup entity value.
     */
    private static DomainExpansionEntityEntity jjkbrp$findCleanupEntity(ServerLevel world, Vec3 center, double actualRadius) {
        List<DomainExpansionEntityEntity> nearby = world.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(center.x - 1.5, center.y - 1.5, center.z - 1.5, center.x + 1.5, center.y + 1.5, center.z + 1.5), e -> true);
        if (nearby.isEmpty()) {
            nearby = world.getEntitiesOfClass(DomainExpansionEntityEntity.class, new AABB(center.x - actualRadius, center.y - actualRadius, center.z - actualRadius, center.x + actualRadius, center.y + actualRadius, center.z + actualRadius), e -> true);
        }
        return nearby.stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center.x, center.y, center.z))).orElse(null);
    }

    /**
     * Checks whether the caster is currently operating an open domain using runtime flags or the active domain effect amplifier.
     * @param entity entity involved in the current mixin operation.
     * @return whether is open domain active is true for the current runtime state.
     */

    // ===== OPEN DOMAIN STATE HELPERS =====
    private static boolean jjkbrp$isOpenDomainActive(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.getBoolean("jjkbrp_open_form_active")) {
            return true;
        }
        if (nbt.getInt("jjkbrp_domain_form_effective") == 2) {
            return true;
        }
        MobEffectInstance inst = entity.getEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        return inst != null && inst.getAmplifier() > 0;
    }

    /**
     * Caches and locks the open-domain center so later VFX and cancellation checks use a stable point.
     * @param entity entity involved in the current mixin operation.
     */
    private static void jjkbrp$cacheOpenDomainCenter(LivingEntity entity) {
        boolean hasBaseCenter;
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        boolean bl = hasBaseCenter = nbt.contains("x_pos_doma") && nbt.getDouble("cnt1") > 0.0;
        // Lock the open-domain center as soon as the base domain center becomes trustworthy so later drift cannot move the VFX origin.
        if (hasBaseCenter && !nbt.getBoolean("jjkbrp_open_center_locked")) {
            Vec3 center = DomainAddonUtils.getDomainCenter((Entity)entity);
            nbt.putDouble("jjkbrp_open_domain_cx", center.x);
            nbt.putDouble("jjkbrp_open_domain_cy", center.y);
            nbt.putDouble("jjkbrp_open_domain_cz", center.z);
            nbt.putBoolean("jjkbrp_open_center_locked", true);
            return;
        }
        if (nbt.contains("jjkbrp_open_domain_cx")) {
            return;
        }
        if (nbt.contains("jjkbrp_caster_x_at_cast")) {
            nbt.putDouble("jjkbrp_open_domain_cx", nbt.getDouble("jjkbrp_caster_x_at_cast"));
            nbt.putDouble("jjkbrp_open_domain_cy", nbt.getDouble("jjkbrp_caster_y_at_cast"));
            nbt.putDouble("jjkbrp_open_domain_cz", nbt.getDouble("jjkbrp_caster_z_at_cast"));
            return;
        }
        if (!hasBaseCenter) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)entity);
        nbt.putDouble("jjkbrp_open_domain_cx", center.x);
        nbt.putDouble("jjkbrp_open_domain_cy", center.y);
        nbt.putDouble("jjkbrp_open_domain_cz", center.z);
        nbt.putBoolean("jjkbrp_open_center_locked", true);
    }

    /**
     * Determines whether the one-time open-domain opening burst should be fired on the current tick.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     * @param openDomainActive open domain active used by this method.
     * @return whether should fire opening vfx is true for the current runtime state.
     */

    // ===== OPEN DOMAIN OPENING VFX =====
    private static boolean jjkbrp$shouldFireOpeningVFX(ServerLevel world, Player player, boolean openDomainActive) {
        CompoundTag nbt = player.getPersistentData();
        if (nbt.getBoolean("jjkbrp_opening_vfx_fired")) {
            return false;
        }
        if (nbt.getDouble("cnt3") >= 20.0) {
            return true;
        }
        if (!openDomainActive) {
            return false;
        }
        if (!player.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return false;
        }
        long castGameTime = nbt.contains("jjkbrp_open_cast_game_time") ? nbt.getLong("jjkbrp_open_cast_game_time") : world.getGameTime();
        // Allow a short startup delay before firing the burst when the domain opened through the faster alternate path.
        return world.getGameTime() - castGameTime >= 6L;
    }

    /**
     * Counts down the short post-open grace window used by other startup-sensitive checks.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */
    private static void jjkbrp$decrementGracePeriod(ServerLevel world, Player player) {
        CompoundTag nbt = player.getPersistentData();
        if (!nbt.contains("jjkbrp_domain_grace_ticks")) {
            return;
        }
        int grace = nbt.getInt("jjkbrp_domain_grace_ticks");
        if (grace > 0) {
            nbt.putInt("jjkbrp_domain_grace_ticks", grace - 1);
        } else {
            nbt.remove("jjkbrp_domain_grace_ticks");
            nbt.remove("jjkbrp_domain_just_opened");
        }
    }

    /**
     * Builds and emits the initial open-domain opening burst, including sound, particles, and darkness staging.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */
    private static void jjkbrp$fireOpeningVFX(ServerLevel world, Player player) {
        double oz;
        double oy;
        int i;
        CompoundTag nbt = player.getPersistentData();
        nbt.putBoolean("jjkbrp_opening_vfx_fired", true);
        boolean isOpenForm = DomainMasteryMixin.jjkbrp$isOpenDomainActive((LivingEntity)player);
        if (isOpenForm) {
            DomainMasteryMixin.jjkbrp$cacheOpenDomainCenter((LivingEntity)player);
        }
        Vec3 center = isOpenForm ? DomainAddonUtils.getOpenDomainCenter((Entity)player) : DomainAddonUtils.getDomainCenter((Entity)player);
        double cx = center.x;
        double cy = center.y;
        double cz = center.z;
        nbt.putDouble("jjkbrp_open_domain_cx", cx);
        nbt.putDouble("jjkbrp_open_domain_cy", cy);
        nbt.putDouble("jjkbrp_open_domain_cz", cz);
        int domainId = (int)Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("skill_domain"));
        }
        if (domainId <= 0) {
            domainId = 1;
        }
        OpenVfxPreset preset = DomainMasteryMixin.presetForDomain(domainId);
        double baseRange = DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, nbt);
        double vfxScale = Math.max(1.0, baseRange / 16.0);
        double blindRange = Math.max(12.0, baseRange * 3.0);
        Vector3f fogColor = preset.fogColor();
        float dustScale = preset.dustScale();
        DomainMasteryMixin.jjkbrp$playReferenceSound(world, cx, cy, cz, "jujutsucraft:wind_chime", 3.0f, 1.0f);
        DomainMasteryMixin.jjkbrp$playReferenceSound(world, cx, cy, cz, "jujutsucraft:slow_motion_end", 3.0f, 1.0f);
        DomainMasteryMixin.jjkbrp$playReferenceSound(world, cx, cy, cz, "jujutsucraft:piano_horror", 2.2f, 0.78f);
        AABB blindBox = new AABB(cx - blindRange, cy - 4.0, cz - blindRange, cx + blindRange, cy + 6.0, cz + blindRange);
        for (LivingEntity nearby : world.getEntitiesOfClass(LivingEntity.class, blindBox, e -> e != player)) {
            MobEffectInstance existing = nearby.getEffect(MobEffects.BLINDNESS);
            if (existing != null && existing.getDuration() >= 30) continue;
            nearby.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        }
        for (i = 0; i < (int)(18.0 * Math.min(vfxScale, 3.0)); ++i) {
            double ox = (Math.random() - 0.5) * 2.5 * vfxScale;
            oy = Math.random() * (double)player.getBbHeight() * 1.4;
            oz = (Math.random() - 0.5) * 2.5 * vfxScale;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, cx + ox, cy + oy, cz + oz, 1, 0.25 * vfxScale, 0.25, 0.25 * vfxScale, 0.04);
        }
        for (i = 0; i < (int)(12.0 * Math.min(vfxScale, 2.5)); ++i) {
            double ox = (Math.random() - 0.5) * 3.0 * vfxScale;
            oy = Math.random() * 2.0;
            oz = (Math.random() - 0.5) * 3.0 * vfxScale;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz, 1, 0.15 * vfxScale, 0.4, 0.15 * vfxScale, 0.06);
        }
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5f, 0.9f);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + (double)player.getBbHeight() * 0.6, player.getZ(), (int)(18.0 * Math.min(vfxScale, 2.5)), 0.65 * vfxScale, 0.4, 0.65 * vfxScale, 0.02);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fogColor, dustScale + 0.25f), player.getX(), player.getY() + 1.0, player.getZ(), (int)(14.0 * Math.min(vfxScale, 2.2)), 0.95 * vfxScale, 0.5, 0.95 * vfxScale, 0.0);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), (int)(10.0 * Math.min(vfxScale, 2.0)), 0.8 * vfxScale, 0.25, 0.8 * vfxScale, 0.01);
        PlayAnimationProcedure.execute((LevelAccessor)world, (Entity)player);
        int ringCount = (int)(50.0 * Math.min(vfxScale, 2.5));
        for (int i2 = 0; i2 < ringCount; ++i2) {
            double angle = (double)i2 / (double)ringCount * Math.PI * 2.0;
            double r = baseRange * 0.1 + Math.random() * baseRange * 0.2;
            double px = cx + r * Math.cos(angle);
            double pz = cz + r * Math.sin(angle);
            double py = cy + (double)player.getBbHeight() * (0.3 + Math.random() * 0.5);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fogColor, dustScale), px, py, pz, 1, (px - cx) * 0.12, (py - cy) * 0.04, (pz - cz) * 0.12, 0.025);
            if (i2 % 4 != 0) continue;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.END_ROD, px, py, pz, 1, (px - cx) * 0.08, 0.08, (pz - cz) * 0.08, 0.015);
        }
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), dustScale * 1.4f), cx, cy + (double)player.getBbHeight() * 0.5, cz, 22, 0.9 * vfxScale, (double)player.getBbHeight() * 0.5, 0.9 * vfxScale, 0.06);
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.2f, 0.45f);
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9f, 1.15f);
        if (!isOpenForm) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 8, 0, false, false));
        } else {
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
            nbt.remove("jjkbrp_open_darkness_start");
            nbt.remove("jjkbrp_open_darkness_stage");
            double shellRadius = DomainAddonUtils.getOpenDomainShellRadius((LevelAccessor)world, (Entity)player);
            double visualRange = DomainAddonUtils.getOpenDomainVisualRange((LevelAccessor)world, (Entity)player);
            DomainMasteryMixin.jjkbrp$spawnOpenDomainBoundaryCurtain(world, (LivingEntity)player, center, shellRadius, preset, false, 1.75f);
            DomainMasteryMixin.jjkbrp$spawnOpenDomainParticleBarrier(world, (LivingEntity)player, center, shellRadius, preset, false, 1.55f);
            DomainMasteryMixin.jjkbrp$spawnOpenDomainGroundHaze(world, (LivingEntity)player, center, visualRange, preset, domainId, 1.55f);
            DomainMasteryMixin.jjkbrp$emitOpenDomainCorePulse(world, (LivingEntity)player, center, visualRange, preset, 1.35f);
        }
    }

    /**
     * Performs play reference sound for this mixin.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param soundId identifier used to resolve runtime state for this operation.
     * @param volume volume used by this method.
     * @param pitch pitch used by this method.
     */
    private static void jjkbrp$playReferenceSound(ServerLevel world, double x, double y, double z, String soundId, float volume, float pitch) {
        SoundEvent sound = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
        if (sound != null) {
            world.playSound(null, BlockPos.containing((double)x, (double)y, (double)z), sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    /**
     * Performs fog color for domain for this mixin.
     * @param id identifier used to resolve runtime state for this operation.
     * @return the resulting fog color for domain value.
     */
    private static Vector3f fogColorForDomain(int id) {
        return switch (id) {
            case 1 -> new Vector3f(0.95f, 0.12f, 0.1f);
            case 2 -> new Vector3f(0.42f, 0.56f, 1.0f);
            case 3 -> new Vector3f(0.35f, 0.78f, 0.98f);
            case 4 -> new Vector3f(0.98f, 0.45f, 0.12f);
            case 5 -> new Vector3f(0.88f, 0.7f, 0.3f);
            case 6 -> new Vector3f(0.2f, 0.18f, 0.28f);
            case 7 -> new Vector3f(0.85f, 0.95f, 1.0f);
            case 8 -> new Vector3f(0.28f, 0.85f, 0.92f);
            case 9 -> new Vector3f(0.92f, 0.84f, 0.42f);
            case 10 -> new Vector3f(0.95f, 0.15f, 0.18f);
            case 11 -> new Vector3f(0.52f, 0.92f, 0.58f);
            case 12 -> new Vector3f(0.82f, 0.3f, 0.22f);
            case 13 -> new Vector3f(0.65f, 0.65f, 0.72f);
            case 14 -> new Vector3f(0.72f, 0.95f, 0.8f);
            case 15 -> new Vector3f(0.9f, 0.1f, 0.16f);
            case 18 -> new Vector3f(0.92f, 0.12f, 0.12f);
            case 21 -> new Vector3f(0.26f, 0.22f, 0.42f);
            case 22 -> new Vector3f(0.48f, 0.64f, 1.0f);
            case 23 -> new Vector3f(0.86f, 0.18f, 0.22f);
            case 24 -> new Vector3f(0.64f, 0.95f, 1.0f);
            case 27 -> new Vector3f(0.25f, 0.25f, 0.35f);
            case 29 -> new Vector3f(0.98f, 0.98f, 1.0f);
            default -> new Vector3f(0.5f, 0.1f, 0.8f);
        };
    }

    /**
     * Performs dust scale for domain for this mixin.
     * @param id identifier used to resolve runtime state for this operation.
     * @return the resulting dust scale for domain value.
     */
    private static float dustScaleForDomain(int id) {
        return switch (id) {
            case 1, 10, 15 -> 1.35f;
            case 2, 6, 18, 27 -> 1.22f;
            case 24, 29 -> 1.1f;
            default -> 1.2f;
        };
    }

    /**
     * Performs p for this mixin.
     * @param r r used by this method.
     * @param g g used by this method.
     * @param b b used by this method.
     * @param ring ring used by this method.
     * @param inner inner used by this method.
     * @param pulse pulse used by this method.
     * @param ringI ring i used by this method.
     * @param innerI inner i used by this method.
     * @param pulseI pulse i used by this method.
     * @param density density used by this method.
     * @param dustScale dust scale used by this method.
     * @param dark dark used by this method.
     * @param weak weak used by this method.
     * @return the resulting p value.
     */
    private static OpenVfxPreset p(float r, float g, float b, ParticleOptions ring, ParticleOptions inner, ParticleOptions pulse, int ringI, int innerI, int pulseI, int density, float dustScale, int dark, int weak) {
        return new OpenVfxPreset(new Vector3f(r, g, b), ring, inner, pulse, ringI, innerI, pulseI, density, dustScale, dark, weak);
    }

    /**
     * Performs fog profile for domain for this mixin.
     * @param domainId identifier used to resolve runtime state for this operation.
     * @param openRange distance value used by this runtime calculation.
     * @param preset preset used by this method.
     * @param intensityScale intensity scale used by this method.
     * @return the resulting fog profile for domain value.
     */
    private static OpenFogProfile jjkbrp$fogProfileForDomain(int domainId, double openRange, OpenVfxPreset preset, float intensityScale) {
        float densityScale = 0.95f + (float)Math.max(0, preset.ringDensity() - 5) * 0.1f + Math.max(0.0f, intensityScale - 1.0f) * 0.35f + (float)Math.floorMod(domainId, 4) * 0.08f;
        int perimeterCount = Math.max(20, Math.min(72, Math.round((float)(openRange * (double)0.46f * (double)densityScale))));
        int interiorCount = Math.max(8, Math.min(30, Math.round((4.0f + (float)preset.ringDensity()) * densityScale)));
        float radiusFactor = 0.7f + (float)Math.floorMod(domainId, 5) * 0.035f;
        float heightFactor = 0.85f + (float)Math.floorMod(domainId + preset.innerInterval(), 4) * 0.1f;
        float driftStrength = 0.05f + (float)Math.floorMod(domainId + preset.pulseInterval(), 3) * 0.025f;
        Vector3f baseColor = DomainMasteryMixin.jjkbrp$mixColor(preset.fogColor(), new Vector3f(0.05f, 0.05f, 0.07f), 0.28f + (float)Math.floorMod(domainId, 3) * 0.07f);
        Vector3f accentColor = DomainMasteryMixin.jjkbrp$mixColor(preset.fogColor(), new Vector3f(0.96f, 0.98f, 1.0f), 0.16f + (float)Math.floorMod(domainId + 1, 4) * 0.05f);
        ParticleOptions floorParticle = switch (Math.floorMod(domainId, 6)) {
            case 0 -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case 1 -> ParticleTypes.CLOUD;
            case 2 -> ParticleTypes.SMOKE;
            case 3 -> ParticleTypes.ASH;
            case 4 -> preset.ringParticle();
            default -> preset.innerParticle();
        };
        ParticleOptions accentParticle = switch (Math.floorMod(domainId + preset.ringDensity(), 5)) {
            case 0 -> preset.innerParticle();
            case 1 -> preset.ringParticle();
            case 2 -> preset.pulseParticle();
            case 3 -> ParticleTypes.END_ROD;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
        return new OpenFogProfile(baseColor, accentColor, (ParticleOptions)floorParticle, (ParticleOptions)accentParticle, perimeterCount, interiorCount, radiusFactor, heightFactor, driftStrength);
    }

    /**
     * Performs mix color for this mixin.
     * @param from from used by this method.
     * @param to to used by this method.
     * @param amount amount used by this method.
     * @return the resulting mix color value.
     */
    private static Vector3f jjkbrp$mixColor(Vector3f from, Vector3f to, float amount) {
        float mix = Math.max(0.0f, Math.min(1.0f, amount));
        return new Vector3f(from.x() + (to.x() - from.x()) * mix, from.y() + (to.y() - from.y()) * mix, from.z() + (to.z() - from.z()) * mix);
    }

    /**
     * Performs preset for domain for this mixin.
     * @param id identifier used to resolve runtime state for this operation.
     * @return the resulting preset for domain value.
     */
    private static OpenVfxPreset presetForDomain(int id) {
        return switch (id) {
            case 1 -> DomainMasteryMixin.p(0.95f, 0.12f, 0.1f, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 3, 22, 7, 1.35f, 0, 0);
            case 2 -> DomainMasteryMixin.p(0.42f, 0.56f, 1.0f, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.SOUL, 4, 2, 18, 6, 1.3f, 0, -1);
            case 3 -> DomainMasteryMixin.p(0.35f, 0.78f, 0.98f, (ParticleOptions)ParticleTypes.BUBBLE, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.END_ROD, 5, 3, 24, 6, 1.2f, -1, -1);
            case 4 -> DomainMasteryMixin.p(0.98f, 0.45f, 0.12f, (ParticleOptions)ParticleTypes.LAVA, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.FLAME, 4, 3, 20, 8, 1.28f, 0, 0);
            case 5 -> DomainMasteryMixin.p(0.88f, 0.7f, 0.3f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 26, 6, 1.15f, -1, -1);
            case 6 -> DomainMasteryMixin.p(0.2f, 0.18f, 0.28f, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.SOUL, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 4, 2, 19, 7, 1.22f, 1, 0);
            case 7 -> DomainMasteryMixin.p(0.85f, 0.95f, 1.0f, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 3, 25, 6, 1.16f, -1, -1);
            case 8 -> DomainMasteryMixin.p(0.28f, 0.85f, 0.92f, (ParticleOptions)ParticleTypes.BUBBLE, (ParticleOptions)ParticleTypes.BUBBLE, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 4, 2, 20, 7, 1.22f, 0, -1);
            case 9 -> DomainMasteryMixin.p(0.92f, 0.84f, 0.42f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 24, 6, 1.14f, -1, -1);
            case 10 -> DomainMasteryMixin.p(0.95f, 0.15f, 0.18f, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, 4, 3, 21, 8, 1.34f, 0, 0);
            case 11 -> DomainMasteryMixin.p(0.52f, 0.92f, 0.58f, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.END_ROD, 5, 2, 24, 5, 1.12f, -1, -1);
            case 12 -> DomainMasteryMixin.p(0.82f, 0.3f, 0.22f, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.LAVA, 4, 3, 19, 7, 1.24f, 0, 0);
            case 13 -> DomainMasteryMixin.p(0.65f, 0.65f, 0.72f, (ParticleOptions)ParticleTypes.CRIT, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 3, 25, 5, 1.1f, -1, -1);
            case 14 -> DomainMasteryMixin.p(0.72f, 0.95f, 0.8f, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.END_ROD, 6, 2, 28, 5, 1.08f, -1, -1);
            case 15 -> DomainMasteryMixin.p(0.9f, 0.1f, 0.16f, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 3, 20, 8, 1.36f, 0, 0);
            case 16 -> DomainMasteryMixin.p(0.65f, 0.6f, 1.0f, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.CRIT, 4, 2, 18, 6, 1.26f, 0, -1);
            case 17 -> DomainMasteryMixin.p(0.7f, 0.45f, 0.95f, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, 5, 2, 23, 6, 1.2f, 0, 0);
            case 18 -> DomainMasteryMixin.p(0.92f, 0.12f, 0.12f, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.LAVA, 4, 3, 20, 8, 1.35f, 0, 0);
            case 19 -> DomainMasteryMixin.p(0.62f, 0.72f, 0.9f, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.CRIT, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 3, 26, 5, 1.12f, -1, -1);
            case 20 -> DomainMasteryMixin.p(0.98f, 0.86f, 0.32f, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.CRIT, 6, 2, 30, 5, 1.08f, -1, -1);
            case 21 -> DomainMasteryMixin.p(0.26f, 0.22f, 0.42f, (ParticleOptions)ParticleTypes.SOUL, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 4, 2, 18, 7, 1.25f, 1, 0);
            case 22 -> DomainMasteryMixin.p(0.48f, 0.64f, 1.0f, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 2, 18, 6, 1.24f, 0, -1);
            case 23 -> DomainMasteryMixin.p(0.86f, 0.18f, 0.22f, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, 4, 3, 20, 7, 1.3f, 0, 0);
            case 24 -> DomainMasteryMixin.p(0.64f, 0.95f, 1.0f, (ParticleOptions)ParticleTypes.SNOWFLAKE, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.END_ROD, 5, 3, 24, 7, 1.18f, -1, -1);
            case 25 -> DomainMasteryMixin.p(0.55f, 0.44f, 0.36f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 26, 6, 1.14f, 0, 0);
            case 26 -> DomainMasteryMixin.p(0.45f, 0.36f, 0.32f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 25, 6, 1.14f, 0, 0);
            case 27 -> DomainMasteryMixin.p(0.25f, 0.25f, 0.35f, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.SOUL, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 2, 17, 7, 1.22f, 1, 0);
            case 28 -> DomainMasteryMixin.p(0.44f, 0.88f, 0.92f, (ParticleOptions)ParticleTypes.BUBBLE, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 5, 2, 24, 6, 1.18f, -1, -1);
            case 29 -> DomainMasteryMixin.p(0.98f, 0.98f, 1.0f, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.CRIT, 3, 2, 14, 8, 1.1f, -1, -1);
            case 30 -> DomainMasteryMixin.p(0.78f, 0.52f, 0.32f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 24, 6, 1.16f, 0, 0);
            case 31 -> DomainMasteryMixin.p(0.52f, 0.42f, 0.88f, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, 5, 2, 22, 6, 1.18f, 0, 0);
            case 32 -> DomainMasteryMixin.p(0.92f, 0.82f, 0.55f, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.CRIT, 5, 3, 25, 6, 1.12f, -1, -1);
            case 33 -> DomainMasteryMixin.p(0.34f, 0.22f, 0.52f, (ParticleOptions)ParticleTypes.SOUL, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 4, 2, 19, 7, 1.23f, 1, 0);
            case 34 -> DomainMasteryMixin.p(0.38f, 0.64f, 0.32f, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.HAPPY_VILLAGER, (ParticleOptions)ParticleTypes.CRIT, 6, 2, 28, 5, 1.08f, -1, -1);
            case 35 -> DomainMasteryMixin.p(0.9f, 0.2f, 0.26f, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 3, 20, 8, 1.34f, 0, 0);
            case 36 -> DomainMasteryMixin.p(0.62f, 0.86f, 0.92f, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 3, 24, 6, 1.14f, -1, -1);
            case 37 -> DomainMasteryMixin.p(0.84f, 0.24f, 0.2f, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.LAVA, 4, 3, 20, 7, 1.26f, 0, 0);
            case 38 -> DomainMasteryMixin.p(0.52f, 0.7f, 1.0f, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.CRIT, 4, 2, 18, 6, 1.24f, 0, -1);
            case 39 -> DomainMasteryMixin.p(0.94f, 0.94f, 0.98f, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, (ParticleOptions)ParticleTypes.CRIT, 4, 2, 16, 7, 1.1f, -1, -1);
            case 40 -> DomainMasteryMixin.p(0.3f, 0.3f, 0.34f, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 3, 23, 6, 1.16f, 0, 0);
            case 41 -> DomainMasteryMixin.p(0.46f, 0.74f, 0.88f, (ParticleOptions)ParticleTypes.BUBBLE, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 5, 2, 24, 6, 1.18f, -1, -1);
            case 42 -> DomainMasteryMixin.p(0.74f, 0.36f, 0.88f, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.CRIT, 5, 2, 22, 6, 1.18f, 0, 0);
            case 43 -> DomainMasteryMixin.p(0.24f, 0.24f, 0.28f, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.ASH, (ParticleOptions)ParticleTypes.SOUL, 5, 3, 21, 6, 1.17f, 0, 0);
            case 44 -> DomainMasteryMixin.p(0.96f, 0.96f, 1.0f, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.CRIT, 4, 2, 16, 7, 1.1f, -1, -1);
            case 45 -> DomainMasteryMixin.p(0.88f, 0.16f, 0.14f, (ParticleOptions)ParticleTypes.FLAME, (ParticleOptions)ParticleTypes.CAMPFIRE_COSY_SMOKE, (ParticleOptions)ParticleTypes.LAVA, 4, 3, 20, 8, 1.35f, 0, 0);
            case 46 -> DomainMasteryMixin.p(0.92f, 0.18f, 0.16f, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, (ParticleOptions)ParticleTypes.SMOKE, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 3, 20, 8, 1.34f, 0, 0);
            case 47 -> DomainMasteryMixin.p(0.58f, 0.86f, 0.92f, (ParticleOptions)ParticleTypes.CLOUD, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, 5, 2, 24, 6, 1.16f, -1, -1);
            default -> DomainMasteryMixin.p(0.3f, 0.7f, 1.0f, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, (ParticleOptions)ParticleTypes.END_ROD, (ParticleOptions)ParticleTypes.ELECTRIC_SPARK, 4, 3, 24, 6, 1.2f, 0, 0);
        };
    }

    /**
     * Applies archetype signature for the current mixin flow.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param openRange distance value used by this runtime calculation.
     * @param gameTime tick-based timing value used by this helper.
     */

    // ===== OPEN DOMAIN VISUAL LOOP =====
    private static void applyArchetypeSignature(ServerLevel world, LivingEntity caster, Vec3 center, double openRange, long gameTime) {
        // Archetype signatures let open-domain visuals differ by policy without hardcoding the entire visual loop for every domain id.
        String arch = caster.getPersistentData().getString("jjkbrp_domain_archetype");
        if (arch == null || arch.isEmpty()) {
            return;
        }
        double cx = center.x;
        double cy = center.y;
        double cz = center.z;
        double archScale = Math.max(1.0, openRange / 40.0);
        switch (arch) {
            case "REFINED": {
                if (gameTime % 6L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.END_ROD, cx, cy + 1.0, cz, 4, 1.6 * archScale, 0.7, 1.6 * archScale, 0.02);
                world.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, cx, cy + 1.0, cz, 2, 1.2 * archScale, 0.5, 1.2 * archScale, 0.01);
                break;
            }
            case "CONTROL": {
                if (gameTime % 5L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.SMOKE, cx, cy + 1.0, cz, 7, 2.0 * archScale, 0.8, 2.0 * archScale, 0.01);
                world.sendParticles((ParticleOptions)ParticleTypes.SOUL, cx, cy + 1.0, cz, 3, 1.4 * archScale, 0.6, 1.4 * archScale, 0.0);
                break;
            }
            case "SUMMON": {
                if (gameTime % 4L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 1.0, cz, 4, 2.0 * archScale, 0.8, 2.0 * archScale, 0.01);
                world.sendParticles((ParticleOptions)ParticleTypes.END_ROD, cx, cy + 1.1, cz, 2, 1.3 * archScale, 0.6, 1.3 * archScale, 0.0);
                break;
            }
            case "AOE": {
                if (gameTime % 7L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, cx, cy + (double)caster.getBbHeight(), cz, 7, 2.4 * archScale, 1.0, 2.4 * archScale, 0.05);
                world.sendParticles((ParticleOptions)ParticleTypes.FLAME, cx, cy + 1.0, cz, 4, 2.2 * archScale, 0.8, 2.2 * archScale, 0.01);
                break;
            }
            case "UTILITY": {
                if (gameTime % 8L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.HAPPY_VILLAGER, cx, cy + 1.0, cz, 4, 2.0 * archScale, 0.8, 2.0 * archScale, 0.0);
                world.sendParticles((ParticleOptions)ParticleTypes.CLOUD, cx, cy + 1.0, cz, 3, 1.7 * archScale, 0.6, 1.7 * archScale, 0.01);
                break;
            }
            default: {
                if (gameTime % 6L != 0L) break;
                world.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, cx, cy + 1.0, cz, 4, 2.0 * archScale, 0.8, 2.0 * archScale, 0.01);
            }
        }
    }

    /**
     * Performs spawn open domain boundary curtain for this mixin.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param openRange distance value used by this runtime calculation.
     * @param preset preset used by this method.
     * @param collapsing collapsing used by this method.
     * @param densityScale density scale used by this method.
     */
    private static void jjkbrp$spawnOpenDomainBoundaryCurtain(ServerLevel world, LivingEntity caster, Vec3 center, double openRange, OpenVfxPreset preset, boolean collapsing, float densityScale) {
        double centerX = center.x;
        double centerY = center.y;
        double centerZ = center.z;
        double curtainHeight = Math.max(10.0, Math.min(24.0, openRange * 1.05));
        int segmentCount = Math.max(24, Math.min(56, (int)Math.round(openRange * (0.8 + (double)densityScale * 0.18))));
        int layerCount = Math.max(6, Math.min(12, (int)Math.ceil(curtainHeight / 2.0)));
        double boundaryRadius = openRange * (collapsing ? 0.96 : 1.0);
        double driftScale = collapsing ? 0.18 : 0.08;
        float dustScale = preset.dustScale() + (collapsing ? 0.45f : 0.3f) * densityScale;
        SimpleParticleType fogParticle = collapsing ? ParticleTypes.SMOKE : ParticleTypes.CLOUD;
        for (int index = 0; index < segmentCount; ++index) {
            double angle = Math.PI * 2 * (double)index / (double)segmentCount;
            double radius = boundaryRadius + (Math.random() - 0.5) * 1.6;
            double px = centerX + Math.cos(angle) * radius;
            double pz = centerZ + Math.sin(angle) * radius;
            double inwardX = (centerX - px) / Math.max(1.0, radius);
            double inwardZ = (centerZ - pz) / Math.max(1.0, radius);
            for (int layer = 0; layer < layerCount; ++layer) {
                double py = centerY + 0.35 + (double)layer * (curtainHeight / (double)layerCount) + Math.random() * 0.45;
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), dustScale), px, py, pz, 1, inwardX * driftScale, collapsing ? -0.05 : 0.03, inwardZ * driftScale, 0.0);
                if (layer % 2 != 0) continue;
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)fogParticle, px, py, pz, 1, inwardX * driftScale * 0.6, collapsing ? -0.02 : 0.05, inwardZ * driftScale * 0.6, 0.0);
            }
            if (index % 3 != 0) continue;
            double accentY = centerY + 0.5 + Math.random() * curtainHeight;
            DomainAddonUtils.sendLongDistanceParticles(world, preset.ringParticle(), px, accentY, pz, 1, inwardX * 0.18, collapsing ? -0.03 : 0.04, inwardZ * 0.18, 0.0);
        }
    }

    /**
     * Performs spawn open domain particle barrier for this mixin.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param openRange distance value used by this runtime calculation.
     * @param preset preset used by this method.
     * @param collapsing collapsing used by this method.
     * @param densityScale density scale used by this method.
     */
    private static void jjkbrp$spawnOpenDomainParticleBarrier(ServerLevel world, LivingEntity caster, Vec3 center, double openRange, OpenVfxPreset preset, boolean collapsing, float densityScale) {
        double centerX = center.x;
        double centerY = center.y;
        double centerZ = center.z;
        double shellRadius = Math.max(4.0, openRange * (collapsing ? 0.98 : 1.0));
        double minElevation = -0.5654866776461628;
        double maxElevation = 1.5393804002589986;
        int verticalBands = Math.max(7, Math.min(15, (int)Math.round(6.0 + (double)densityScale * 2.0 + shellRadius / 5.0)));
        int baseSegments = Math.max(24, Math.min(96, (int)Math.round(shellRadius * (0.85 + (double)densityScale * 0.18))));
        int accentStep = Math.max(2, 6 - Math.max(1, preset.ringDensity() / 3));
        float shellDustScale = preset.dustScale() + (collapsing ? 0.42f : 0.28f) * densityScale;
        ParticleOptions shellAccent = collapsing ? ParticleTypes.SMOKE : preset.ringParticle();
        ParticleOptions shellHighlight = collapsing ? ParticleTypes.REVERSE_PORTAL : preset.innerParticle();
        for (int band = 0; band < verticalBands; ++band) {
            double bandProgress = verticalBands <= 1 ? 0.5 : (double)band / (double)(verticalBands - 1);
            double elevation = minElevation + (maxElevation - minElevation) * bandProgress;
            double ringRadius = Math.max(shellRadius * 0.12, shellRadius * Math.cos(elevation));
            double py = centerY + Math.sin(elevation) * shellRadius;
            double verticalDrift = collapsing ? -0.05 : 0.01 + Math.max(0.0, Math.sin(elevation)) * 0.02;
            int segmentCount = Math.max(12, (int)Math.round((double)baseSegments * Math.max(0.18, ringRadius / Math.max(1.0, shellRadius))));
            for (int segment = 0; segment < segmentCount; ++segment) {
                double pz;
                double inwardZ;
                double inwardY;
                double angle = Math.PI * 2 * (double)segment / (double)segmentCount;
                double radialJitter = (Math.random() - 0.5) * 0.35;
                double px = centerX + Math.cos(angle) * (ringRadius + radialJitter);
                double inwardX = centerX - px;
                double inwardLength = Math.sqrt(inwardX * inwardX + (inwardY = centerY - py) * inwardY + (inwardZ = centerZ - (pz = centerZ + Math.sin(angle) * (ringRadius + radialJitter))) * inwardZ);
                if (inwardLength > 0.001) {
                    inwardX /= inwardLength;
                    inwardY /= inwardLength;
                    inwardZ /= inwardLength;
                }
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), shellDustScale), px, py, pz, 1, inwardX * 0.06, verticalDrift + inwardY * 0.05, inwardZ * 0.06, 0.0);
                if ((segment + band) % accentStep != 0) continue;
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)shellAccent, px, py, pz, 1, inwardX * 0.1, collapsing ? -0.02 : 0.03 + inwardY * 0.04, inwardZ * 0.1, 0.0);
            }
        }
        int meridianCount = Math.max(10, Math.min(18, 8 + preset.ringDensity()));
        int meridianSteps = Math.max(8, verticalBands + 3);
        for (int meridian = 0; meridian < meridianCount; ++meridian) {
            double angle = Math.PI * 2 * (double)meridian / (double)meridianCount;
            for (int step = 0; step < meridianSteps; ++step) {
                double pz;
                double inwardZ;
                double py;
                double inwardY;
                double stepProgress = (double)step / (double)(meridianSteps - 1);
                double elevation = minElevation + (maxElevation - minElevation) * stepProgress;
                double ringRadius = Math.max(shellRadius * 0.12, shellRadius * Math.cos(elevation));
                double px = centerX + Math.cos(angle) * ringRadius;
                double inwardX = centerX - px;
                double inwardLength = Math.sqrt(inwardX * inwardX + (inwardY = centerY - (py = centerY + Math.sin(elevation) * shellRadius)) * inwardY + (inwardZ = centerZ - (pz = centerZ + Math.sin(angle) * ringRadius)) * inwardZ);
                if (inwardLength > 0.001) {
                    inwardX /= inwardLength;
                    inwardY /= inwardLength;
                    inwardZ /= inwardLength;
                }
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)shellHighlight, px, py, pz, 1, inwardX * 0.1, collapsing ? -0.03 : 0.04 + inwardY * 0.04, inwardZ * 0.1, 0.0);
            }
        }
    }

    /**
     * Performs spawn open domain ground haze for this mixin.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param openRange distance value used by this runtime calculation.
     * @param preset preset used by this method.
     * @param domainId identifier used to resolve runtime state for this operation.
     * @param intensityScale intensity scale used by this method.
     */
    private static void jjkbrp$spawnOpenDomainGroundHaze(ServerLevel world, LivingEntity caster, Vec3 center, double openRange, OpenVfxPreset preset, int domainId, float intensityScale) {
        double py;
        double pz;
        double px;
        double radius;
        double angle;
        int index;
        double centerX = center.x;
        double centerY = center.y;
        double centerZ = center.z;
        OpenFogProfile fog = DomainMasteryMixin.jjkbrp$fogProfileForDomain(domainId, openRange, preset, intensityScale);
        double hazeRadius = openRange * (double)fog.radiusFactor();
        int perimeterCount = fog.perimeterCount();
        int interiorCount = fog.interiorCount();
        float baseDustScale = preset.dustScale() + 0.14f * intensityScale;
        float accentDustScale = baseDustScale + 0.18f;
        int accentStep = Math.max(2, 7 - Math.max(1, preset.ringDensity() / 2));
        int volumeCount = Math.max(18, Math.min(84, perimeterCount / 2 + interiorCount));
        int accentVolumeCount = Math.max(10, Math.min(44, interiorCount + perimeterCount / 6));
        DomainAddonUtils.sendLongDistanceParticles(world, fog.floorParticle(), centerX, centerY + 0.12, centerZ, Math.max(14, perimeterCount / 4), openRange * 0.34, 0.16 * (double)fog.heightFactor(), openRange * 0.34, 0.0);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.baseColor(), baseDustScale + 0.12f), centerX, centerY + 0.72 + 0.18 * (double)fog.heightFactor(), centerZ, volumeCount, openRange * 0.38, 0.75 + 0.35 * (double)fog.heightFactor(), openRange * 0.38, 0.0);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.accentColor(), accentDustScale + 0.1f), centerX, centerY + 1.1 + 0.12 * (double)fog.heightFactor(), centerZ, accentVolumeCount, openRange * 0.28, 0.55 + 0.25 * (double)fog.heightFactor(), openRange * 0.28, 0.0);
        DomainAddonUtils.sendLongDistanceParticles(world, fog.accentParticle(), centerX, centerY + 0.95, centerZ, Math.max(6, accentVolumeCount / 2), openRange * 0.2, 0.5, openRange * 0.2, 0.0);
        for (index = 0; index < perimeterCount; ++index) {
            angle = Math.PI * 2 * (double)index / (double)perimeterCount;
            radius = hazeRadius + (Math.random() - 0.5) * 2.6;
            px = centerX + Math.cos(angle) * radius;
            pz = centerZ + Math.sin(angle) * radius;
            py = centerY + 0.05 + Math.random() * (0.45 + 0.35 * (double)fog.heightFactor());
            double inwardX = (centerX - px) / Math.max(1.0, radius);
            double inwardZ = (centerZ - pz) / Math.max(1.0, radius);
            DomainAddonUtils.sendLongDistanceParticles(world, fog.floorParticle(), px, py, pz, 2, inwardX * (double)fog.driftStrength() * 0.7, 0.02, inwardZ * (double)fog.driftStrength() * 0.7, 0.0);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.baseColor(), baseDustScale + 0.08f), px, py + 0.1, pz, 2, inwardX * (double)fog.driftStrength(), 0.02, inwardZ * (double)fog.driftStrength(), 0.0);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.accentColor(), accentDustScale), px, py + 0.28, pz, 1, inwardX * (double)fog.driftStrength() * 1.2, 0.03, inwardZ * (double)fog.driftStrength() * 1.2, 0.0);
            if (index % accentStep != 0) continue;
            DomainAddonUtils.sendLongDistanceParticles(world, fog.accentParticle(), px, py + 0.42, pz, 1, inwardX * 0.05, 0.03, inwardZ * 0.05, 0.0);
        }
        for (index = 0; index < interiorCount; ++index) {
            angle = Math.random() * Math.PI * 2.0;
            radius = Math.random() * openRange * 0.42;
            px = centerX + Math.cos(angle) * radius;
            pz = centerZ + Math.sin(angle) * radius;
            py = centerY + 0.1 + Math.random() * (0.55 + (double)caster.getBbHeight() * 0.15 + (double)fog.heightFactor());
            double randomX = (Math.random() - 0.5) * 0.08;
            double randomZ = (Math.random() - 0.5) * 0.08;
            DomainAddonUtils.sendLongDistanceParticles(world, fog.floorParticle(), px, py, pz, 1, randomX * 0.7, 0.01, randomZ * 0.7, 0.0);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.baseColor(), Math.max(0.92f, baseDustScale - 0.02f)), px, py + 0.08, pz, 2, randomX, 0.01, randomZ, 0.0);
            if (index % 2 == 0) {
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(fog.accentColor(), Math.max(1.0f, accentDustScale - 0.04f)), px, py + 0.32, pz, 1, randomX * 1.2, 0.03, randomZ * 1.2, 0.0);
            }
            if (index % 3 != 0) continue;
            DomainAddonUtils.sendLongDistanceParticles(world, preset.innerParticle(), px, py + 0.24, pz, 1, randomX * 0.6, 0.03, randomZ * 0.6, 0.0);
        }
    }

    /**
     * Performs emit open domain core pulse for this mixin.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param openRange distance value used by this runtime calculation.
     * @param preset preset used by this method.
     * @param intensityScale intensity scale used by this method.
     */
    private static void jjkbrp$emitOpenDomainCorePulse(ServerLevel world, LivingEntity caster, Vec3 center, double openRange, OpenVfxPreset preset, float intensityScale) {
        double centerX = center.x;
        double centerY = center.y;
        double centerZ = center.z;
        double pulseScale = Math.max(1.0, openRange / 40.0) * (double)intensityScale;
        DomainAddonUtils.sendLongDistanceParticles(world, preset.pulseParticle(), centerX, centerY + (double)caster.getBbHeight(), centerZ, Math.max(10, (int)(10.0 * Math.min(pulseScale, 3.0))), 1.7 * pulseScale, caster.getBbHeight(), 1.7 * pulseScale, 0.04);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), Math.max(0.9f, preset.dustScale() + 0.15f * intensityScale)), centerX, centerY + 1.0, centerZ, Math.max(12, (int)(12.0 * Math.min(pulseScale, 3.0))), 3.2 * pulseScale, 1.2, 3.2 * pulseScale, 0.0);
    }

    /**
     * Applies the ongoing open-domain visual loop around the cached center using the selected domain preset.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     */
    private static void applyOpenDomainVFX(ServerLevel world, LivingEntity caster) {
        long gameTime = world.getGameTime();
        int domainId = (int)Math.round(caster.getPersistentData().getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(caster.getPersistentData().getDouble("skill_domain"));
        }
        if (domainId <= 0) {
            domainId = 1;
        }
        OpenVfxPreset preset = DomainMasteryMixin.presetForDomain(domainId);
        Vec3 center = DomainAddonUtils.getOpenDomainCenter((Entity)caster);
        double centerX = center.x;
        double centerY = center.y;
        double centerZ = center.z;
        double shellRadius = DomainAddonUtils.getOpenDomainShellRadius((LevelAccessor)world, (Entity)caster);
        double visualRange = DomainAddonUtils.getOpenDomainVisualRange((LevelAccessor)world, (Entity)caster);
        DomainMasteryMixin.applyArchetypeSignature(world, caster, center, visualRange, gameTime);
        if (preset.ringInterval() > 0 && gameTime % (long)preset.ringInterval() == 0L) {
            int groundRingCount = Math.max(16, preset.ringDensity() * 3);
            for (int i = 0; i < groundRingCount; ++i) {
                double angle = (double)i / (double)groundRingCount * Math.PI * 2.0;
                double r = shellRadius + (Math.random() - 0.5) * 0.85;
                double px = centerX + r * Math.cos(angle);
                double pz = centerZ + r * Math.sin(angle);
                double py = centerY + Math.random() * 2.5;
                DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), preset.dustScale() + 0.3f), px, py, pz, 1, 0.1, 0.3, 0.1, 0.0);
            }
            int columnHeight = Math.max(6, Math.min(14, (int)Math.round(shellRadius * 0.6)));
            for (int i = 0; i < 8; ++i) {
                double angle = (double)i * Math.PI / 4.0;
                double r = shellRadius;
                double px = centerX + r * Math.cos(angle);
                double pz = centerZ + r * Math.sin(angle);
                double py = centerY;
                for (int h = 0; h < columnHeight; ++h) {
                    DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), preset.dustScale() + 0.5f), px, py + (double)h + Math.random(), pz, 2, 0.15, 0.2, 0.15, 0.0);
                }
            }
        }
        if (gameTime % (long)Math.max(6, preset.ringInterval() + 1) == 0L) {
            DomainMasteryMixin.jjkbrp$spawnOpenDomainParticleBarrier(world, caster, center, shellRadius, preset, false, 1.0f);
        }
        if (gameTime % (long)Math.max(8, preset.ringInterval() * 2) == 0L) {
            DomainMasteryMixin.jjkbrp$spawnOpenDomainBoundaryCurtain(world, caster, center, shellRadius, preset, false, 1.0f);
        }
        if (gameTime % (long)Math.max(3, preset.innerInterval()) == 0L) {
            DomainMasteryMixin.jjkbrp$spawnOpenDomainGroundHaze(world, caster, center, visualRange, preset, domainId, 1.0f);
        }
        if (preset.innerInterval() > 0 && gameTime % (long)preset.innerInterval() == 0L) {
            double innerScale = Math.max(1.0, visualRange / 40.0);
            double ox = (Math.random() - 0.5) * 6.0 * innerScale;
            double oy = Math.random() * ((double)caster.getBbHeight() + 2.0);
            double oz = (Math.random() - 0.5) * 6.0 * innerScale;
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)new DustParticleOptions(preset.fogColor(), preset.dustScale() + 0.1f), centerX + ox, centerY + oy, centerZ + oz, 1, 0.03, 0.02, 0.03, 0.0);
            DomainAddonUtils.sendLongDistanceParticles(world, preset.innerParticle(), centerX + ox * 0.8, centerY + oy * 0.8, centerZ + oz * 0.8, 1, 0.02, 0.03, 0.02, 0.005);
        }
        if (preset.pulseInterval() > 0 && gameTime % (long)preset.pulseInterval() == 0L) {
            DomainMasteryMixin.jjkbrp$emitOpenDomainCorePulse(world, caster, center, visualRange, preset, 1.0f);
        }
    }

    /**
     * Cancels open domains when the caster moves too far from the allowed open-domain operating range.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */

    // ===== RANGE CANCEL GUARDS =====
    private static void checkOpenDomainRangeCancel(ServerLevel world, Player player) {
        double dz;
        long sinceContact;
        CompoundTag nbt = player.getPersistentData();
        int domainId = (int)Math.round(nbt.getDouble("jjkbrp_domain_id_runtime"));
        if (domainId <= 0) {
            domainId = (int)Math.round(nbt.getDouble("skill_domain"));
        }
        OpenVfxPreset preset = DomainMasteryMixin.presetForDomain(domainId);
        if (nbt.getBoolean("jjkbrp_domain_just_opened")) {
            return;
        }
        if (nbt.contains("jjkbrp_domain_grace_ticks") && nbt.getInt("jjkbrp_domain_grace_ticks") > 0) {
            return;
        }
        DomainMasteryMixin.jjkbrp$cacheOpenDomainCenter((LivingEntity)player);
        if (!nbt.contains("jjkbrp_open_domain_cx")) {
            return;
        }
        Vec3 center = DomainAddonUtils.getOpenDomainCenter((Entity)player);
        double cx = center.x;
        double cy = center.y;
        double cz = center.z;
        double actualRadius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, nbt);
        double shellRadius = DomainAddonUtils.getOpenDomainShellRadius((LevelAccessor)world, (Entity)player);
        double visualRange = DomainAddonUtils.getOpenDomainVisualRange((LevelAccessor)world, (Entity)player);
        // Keep self-cancel aligned with the actual open-domain shell so leaving the visible domain edge reliably cancels.
        double cancelRange = Math.max(6.0, shellRadius);
        double dx = player.getX() - cx;
        double horizontalDistSq = dx * dx + (dz = player.getZ() - cz) * dz;
        if (horizontalDistSq <= cancelRange * cancelRange) {
            nbt.putBoolean("jjkbrp_open_cancelled", false);
            return;
        }
        if (nbt.getBoolean("jjkbrp_open_cancelled")) {
            return;
        }
        nbt.putBoolean("jjkbrp_open_cancelled", true);
        player.removeEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        nbt.putBoolean("jjkbrp_open_form_active", false);
        nbt.remove("jjkbrp_open_domain_cx");
        nbt.remove("jjkbrp_open_domain_cy");
        nbt.remove("jjkbrp_open_domain_cz");
        nbt.remove("jjkbrp_open_center_locked");
        nbt.remove("jjkbrp_opening_vfx_fired");
        nbt.remove("jjkbrp_opening_prefire_fired");
        nbt.remove("jjkbrp_open_cast_game_time");
        nbt.remove("jjkbrp_domain_just_opened");
        nbt.remove("jjkbrp_domain_grace_ticks");
        DomainAddonUtils.cleanupBFBoost((LivingEntity)player);
        double cancelScale = Math.max(1.0, actualRadius / 16.0);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, cx, cy + 1.0, cz, (int)(3.0 * cancelScale), 0.7 * cancelScale, 0.7, 0.7 * cancelScale, 0.0);
        DomainMasteryMixin.jjkbrp$spawnOpenDomainBoundaryCurtain(world, (LivingEntity)player, center, shellRadius, preset, true, 1.85f);
        DomainMasteryMixin.jjkbrp$spawnOpenDomainParticleBarrier(world, (LivingEntity)player, center, shellRadius, preset, true, 1.95f);
        DomainMasteryMixin.jjkbrp$emitOpenDomainCorePulse(world, (LivingEntity)player, center, visualRange, preset, 1.65f);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.SMOKE, cx, cy + 1.0, cz, (int)(55.0 * Math.min(cancelScale, 2.0)), 2.0 * cancelScale, 1.1, 2.0 * cancelScale, 0.02);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, cx, cy + 1.0, cz, (int)(70.0 * Math.min(cancelScale, 2.0)), 2.2 * cancelScale, 1.1, 2.2 * cancelScale, 0.05);
        int ringCount = Math.max(24, (int)(shellRadius * 1.5));
        for (int i = 0; i < ringCount; ++i) {
            double angle = Math.PI * 2 * (double)i / (double)ringCount;
            double ringR = shellRadius * (0.9 + Math.random() * 0.08);
            double px = cx + Math.cos(angle) * ringR;
            double py = cy + 0.5 + Math.random() * 2.0;
            double pz = cz + Math.sin(angle) * ringR;
            double inwardX = (cx - px) / Math.max(1.0, ringR);
            double inwardZ = (cz - pz) / Math.max(1.0, ringR);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.END_ROD, px, py, pz, 1, 0.12, 0.12, 0.12, 0.0);
            DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.REVERSE_PORTAL, px, py, pz, 1, inwardX * 0.4, -0.02, inwardZ * 0.4, 0.01);
        }
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.4f, 0.35f);
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.9f, 0.55f);
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);
        world.playSound(null, BlockPos.containing((double)cx, (double)cy, (double)cz), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
    }

    /**
     * Cancels incomplete domains when the caster leaves the supported range around the cast center.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */
    private static void checkIncompleteDomainRangeCancel(ServerLevel world, Player player) {
        double dz;
        CompoundTag nbt = player.getPersistentData();
        if (nbt.getBoolean("jjkbrp_domain_just_opened")) {
            return;
        }
        if (nbt.contains("jjkbrp_domain_grace_ticks") && nbt.getInt("jjkbrp_domain_grace_ticks") > 0) {
            return;
        }
        if (!nbt.contains("x_pos_doma") && !nbt.contains("jjkbrp_caster_x_at_cast")) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter((Entity)player);
        double baseRadius = Math.max(1.0, DomainAddonUtils.getActualDomainRadius((LevelAccessor)world, nbt));
        double leashMultiplier = 1.35;
        double cancelRange = baseRadius * leashMultiplier;
        double dx = player.getX() - center.x;
        double horizontalDistSq = dx * dx + (dz = player.getZ() - center.z) * dz;
        if (horizontalDistSq <= cancelRange * cancelRange) {
            nbt.putBoolean("jjkbrp_incomplete_cancelled", false);
            return;
        }
        if (nbt.getBoolean("jjkbrp_incomplete_cancelled")) {
            return;
        }
        nbt.putBoolean("jjkbrp_incomplete_cancelled", true);
        player.removeEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        double scale = Math.max(1.0, cancelRange / 16.0);
        DomainAddonUtils.sendLongDistanceParticles(world, (ParticleOptions)ParticleTypes.SMOKE, center.x, center.y + 1.0, center.z, (int)(26.0 * Math.min(scale, 2.0)), 1.4 * scale, 0.7, 1.4 * scale, 0.02);
        world.playSound(null, BlockPos.containing((double)center.x, (double)center.y, (double)center.z), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.2f, 0.75f);
    }

    /**
     * Applies incomplete zone only buff for the current mixin flow.
     * @param caster entity involved in the current mixin operation.
     */
    private static void jjkbrp$applyIncompleteZoneOnlyBuff(LivingEntity caster) {
        MobEffectInstance resistance;
        if (caster == null) {
            return;
        }
        if (!caster.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return;
        }
        if (!DomainAddonUtils.isIncompleteDomainState(caster)) {
            return;
        }
        MobEffectInstance zone = caster.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
        if (zone == null || zone.getAmplifier() < 1 || zone.getDuration() <= 8) {
            caster.addEffect(new MobEffectInstance((MobEffect)JujutsucraftModMobEffects.ZONE.get(), 30, 1, false, false));
        }
        if ((resistance = caster.getEffect(MobEffects.DAMAGE_RESISTANCE)) == null || resistance.getAmplifier() <= 0 && resistance.getDuration() <= 8) {
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
        }
    }

    /**
     * Applies all mastery property side effects that should run while the domain is active.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     */

    // ===== DOMAIN PROPERTY EFFECTS =====
    private static void applyPropertyEffects(LevelAccessor world, LivingEntity caster) {
        if (caster == null) {
            return;
        }
        if (!caster.isAlive() || caster.isDeadOrDying() || caster.isRemoved() || caster.getHealth() <= 0.0f) {
            return;
        }
        caster.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            if (data.getDomainMasteryLevel() == 0) {
                return;
            }
            boolean openFormActive = DomainMasteryMixin.jjkbrp$isOpenDomainActive(caster);
            if (openFormActive) {
                DomainMasteryMixin.jjkbrp$cacheOpenDomainCenter(caster);
            }
            Vec3 effectCenter = openFormActive ? DomainAddonUtils.getOpenDomainCenter((Entity)caster) : DomainAddonUtils.getDomainCenter((Entity)caster);
            double effectRange = openFormActive ? DomainAddonUtils.getOpenDomainRange(world, (Entity)caster) : DomainAddonUtils.getActualDomainRadius(world, caster.getPersistentData());
            effectRange = Math.max(1.0, effectRange);
            boolean incompleteFormActive = DomainAddonUtils.isIncompleteDomainState(caster);
            if (incompleteFormActive) {
                DomainMasteryMixin.applyBFChanceBoost(caster, data);
                DomainMasteryMixin.applyRCTHealBoost(caster, data);
                return;
            }
            if (world instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel)world;
                DomainMasteryMixin.applyVictimCEDrain(sl, caster, data, effectCenter, effectRange);
                DomainMasteryMixin.applyBlindEffect(sl, caster, data, effectCenter, effectRange);
                DomainMasteryMixin.applySlowEffect(sl, caster, data, effectCenter, effectRange);
            }
            DomainMasteryMixin.applyBFChanceBoost(caster, data);
            DomainMasteryMixin.applyRCTHealBoost(caster, data);
        });
    }

    /**
     * Drains cursed energy from victims standing inside the active domain radius.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param effectCenter effect center used by this method.
     * @param effectRange distance value used by this runtime calculation.
     */
    private static void applyVictimCEDrain(ServerLevel world, LivingEntity caster, DomainMasteryData data, Vec3 effectCenter, double effectRange) {
        double openDrainMultiplier;
        int lvl = data.getPropertyLevel(DomainMasteryProperties.VICTIM_CE_DRAIN);
        if (lvl <= 0 || world.getGameTime() % 10L != 0L) {
            return;
        }
        double drain = (double)lvl * DomainMasteryProperties.VICTIM_CE_DRAIN.getValuePerLevel();
        if (caster.getPersistentData().getBoolean("jjkbrp_open_form_active") && (openDrainMultiplier = caster.getPersistentData().getDouble("jjkbrp_open_ce_drain_multiplier")) > 0.0) {
            drain *= openDrainMultiplier;
        }
        double drainAmount = drain;
        AABB effectBounds = DomainMasteryMixin.jjkbrp$effectBounds(effectCenter, effectRange);
        double effectRangeSq = effectRange * effectRange;
        for (Player nearby : world.getEntitiesOfClass(Player.class, effectBounds, p -> p != caster)) {
            if (nearby.distanceToSqr(effectCenter.x, effectCenter.y, effectCenter.z) > effectRangeSq || DomainMasteryMixin.jjkbrp$hasBaseDomainCounterImmunity((LivingEntity)nearby)) continue;
            nearby.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).ifPresent(vars -> {
                vars.PlayerCursePower = Math.max(0.0, vars.PlayerCursePower - drainAmount);
                vars.syncPlayerVariables((Entity)nearby);
            });
        }
    }

    /**
     * Applies the Black Flash chance runtime bonus provided by the mastery property system.
     * @param caster entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     */
    private static void applyBFChanceBoost(LivingEntity caster, DomainMasteryData data) {
        // Each property effect reads its own invested mastery level and exits early when the player has not invested in that branch.
        int lvl = data.getPropertyLevel(DomainMasteryProperties.BF_CHANCE_BOOST);
        CompoundTag nbt = caster.getPersistentData();
        if (lvl <= 0) {
            DomainAddonUtils.cleanupBFBoost(caster);
            return;
        }
        double desiredBoost = (double)lvl * 1.25;
        if (Math.abs(nbt.getDouble("jjkbrp_domain_bf_bonus") - desiredBoost) > 0.01) {
            nbt.putDouble("jjkbrp_domain_bf_bonus", desiredBoost);
        }
        if (nbt.contains("jjkbrp_bf_cnt6_boost")) {
            double legacyBoost = nbt.getDouble("jjkbrp_bf_cnt6_boost");
            if (legacyBoost > 0.0) {
                nbt.putDouble("cnt6", Math.max(0.0, nbt.getDouble("cnt6") - legacyBoost));
            }
            nbt.remove("jjkbrp_bf_cnt6_boost");
        }
    }

    /**
     * Applies the reverse cursed technique healing bonus granted by mastery.
     * @param caster entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     */
    private static void applyRCTHealBoost(LivingEntity caster, DomainMasteryData data) {
        int lvl = data.getPropertyLevel(DomainMasteryProperties.RCT_HEAL_BOOST);
        if (lvl <= 0) {
            return;
        }
        if (!caster.isAlive() || caster.isDeadOrDying() || caster.isRemoved() || caster.getHealth() <= 0.0f) {
            return;
        }
        if (caster.tickCount % 20 != 0) {
            return;
        }
        float maxHp = caster.getMaxHealth();
        float hp = caster.getHealth();
        if (maxHp <= 0.0f || hp >= maxHp) {
            return;
        }
        float healPerTick = (float)((double)lvl * DomainMasteryProperties.RCT_HEAL_BOOST.getValuePerLevel());
        if (healPerTick <= 0.0f) {
            return;
        }
        caster.setHealth(Math.min(maxHp, hp + healPerTick));
    }

    /**
     * Applies or refreshes blindness on valid victims inside the active domain.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param effectCenter effect center used by this method.
     * @param effectRange distance value used by this runtime calculation.
     */
    private static void applyBlindEffect(ServerLevel world, LivingEntity caster, DomainMasteryData data, Vec3 effectCenter, double effectRange) {
        int lvl = data.getPropertyLevel(DomainMasteryProperties.BLIND_EFFECT);
        if (lvl <= 0 || world.getGameTime() % 20L != 0L) {
            return;
        }
        int amp = lvl - 1;
        AABB effectBounds = DomainMasteryMixin.jjkbrp$effectBounds(effectCenter, effectRange);
        double effectRangeSq = effectRange * effectRange;
        for (LivingEntity e2 : world.getEntitiesOfClass(LivingEntity.class, effectBounds, e -> e != caster)) {
            if (e2.distanceToSqr(effectCenter.x, effectCenter.y, effectCenter.z) > effectRangeSq || DomainMasteryMixin.jjkbrp$hasBaseDomainCounterImmunity(e2) || e2.hasEffect(MobEffects.BLINDNESS)) continue;
            e2.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, amp, false, false));
        }
    }

    /**
     * Applies or refreshes movement slowdown on valid victims inside the active domain.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param data persistent data container used by this helper.
     * @param effectCenter effect center used by this method.
     * @param effectRange distance value used by this runtime calculation.
     */
    private static void applySlowEffect(ServerLevel world, LivingEntity caster, DomainMasteryData data, Vec3 effectCenter, double effectRange) {
        int lvl = data.getPropertyLevel(DomainMasteryProperties.SLOW_EFFECT);
        if (lvl <= 0 || world.getGameTime() % 20L != 0L) {
            return;
        }
        int amp = lvl - 1;
        AABB effectBounds = DomainMasteryMixin.jjkbrp$effectBounds(effectCenter, effectRange);
        double effectRangeSq = effectRange * effectRange;
        for (LivingEntity e2 : world.getEntitiesOfClass(LivingEntity.class, effectBounds, e -> e != caster)) {
            if (e2.distanceToSqr(effectCenter.x, effectCenter.y, effectCenter.z) > effectRangeSq || DomainMasteryMixin.jjkbrp$hasBaseDomainCounterImmunity(e2) || e2.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) continue;
            e2.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, amp, false, false));
        }
    }


    // ===== COUNTER AND OPPONENT HELPERS =====
    /**
     * Performs has base domain counter immunity for this mixin.
     * @param target entity involved in the current mixin operation.
     * @return whether has base domain counter immunity is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$hasBaseDomainCounterImmunity(LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (target.hasEffect((MobEffect)JujutsucraftModMobEffects.SIMPLE_DOMAIN.get())) {
            return true;
        }
        if (target.hasEffect((MobEffect)JujutsucraftModMobEffects.FALLING_BLOSSOM_EMOTION.get())) {
            return true;
        }
        if (target.hasEffect((MobEffect)JujutsucraftModMobEffects.PHYSICAL_GIFTED_EFFECT.get())) {
            return true;
        }
        if (target.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        CompoundTag nbt = target.getPersistentData();
        return nbt.getBoolean("Failed") || nbt.getBoolean("Cover") || nbt.getBoolean("DomainDefeated");
    }

    /**
     * Performs effect bounds for this mixin.
     * @param center center used by this method.
     * @param effectRange distance value used by this runtime calculation.
     * @return the resulting effect bounds value.
     */
    private static AABB jjkbrp$effectBounds(Vec3 center, double effectRange) {
        return new AABB(center.x - effectRange, center.y - effectRange, center.z - effectRange, center.x + effectRange, center.y + effectRange, center.z + effectRange);
    }

    /**
     * Performs has nearby domain opponent for this mixin.
     * @param world world access used by the current mixin callback.
     * @param caster entity involved in the current mixin operation.
     * @param center center used by this method.
     * @param range distance value used by this runtime calculation.
     * @return whether has nearby domain opponent is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$hasNearbyDomainOpponent(ServerLevel world, Player caster, Vec3 center, double range) {
        if (world == null || caster == null || center == null || range <= 0.0) {
            return false;
        }
        double rangeSq = range * range;
        AABB box = new AABB(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);
        for (LivingEntity other : world.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster)) {
            if (!DomainMasteryMixin.jjkbrp$isLiveDomainOpponent(world, other) || other.distanceToSqr(center.x, center.y, center.z) > rangeSq) continue;
            return true;
        }
        return false;
    }

    /**
     * Performs is live domain opponent for this mixin.
     * @param world world access used by the current mixin callback.
     * @param entity entity involved in the current mixin operation.
     * @return whether is live domain opponent is true for the current runtime state.
     */
    // Marks this helper member as mixin-unique so it cannot collide with names inside the target class.
    @Unique
    private static boolean jjkbrp$isLiveDomainOpponent(ServerLevel world, LivingEntity entity) {
        if (world == null || entity == null) {
            return false;
        }
        if (entity.hasEffect((MobEffect)JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            return true;
        }
        if (DomainAddonUtils.isDomainBuildOrActive(world, entity)) {
            return true;
        }
        CompoundTag nbt = entity.getPersistentData();

        // Only treat short startup grace as live-opponent fallback; broad stale NBT checks can block range-cancel forever.
        if (nbt.getBoolean("jjkbrp_domain_just_opened")) {
            return true;
        }
        if (nbt.contains("jjkbrp_domain_grace_ticks") && nbt.getInt("jjkbrp_domain_grace_ticks") > 0) {
            return nbt.getDouble("cnt3") > 0.0 || nbt.contains("x_pos_doma");
        }
        return false;
    }

    /**
     * Primary domain runtime mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()`. It blocks incomplete-domain sure-hit behavior, prevents invalid neutralization paths, applies mastery effects each tick, coordinates open-domain visuals, synchronizes runtime state, and enforces open or incomplete range-cancel rules.
     */
    private record OpenVfxPreset(Vector3f fogColor, ParticleOptions ringParticle, ParticleOptions innerParticle, ParticleOptions pulseParticle, int ringInterval, int innerInterval, int pulseInterval, int ringDensity, float dustScale, int darknessAmp, int weaknessAmp) {
    }

    /**
     * Primary domain runtime mixin for `DomainExpansionOnEffectActiveTickProcedure.execute()`. It blocks incomplete-domain sure-hit behavior, prevents invalid neutralization paths, applies mastery effects each tick, coordinates open-domain visuals, synchronizes runtime state, and enforces open or incomplete range-cancel rules.
     */
    private record OpenFogProfile(Vector3f baseColor, Vector3f accentColor, ParticleOptions floorParticle, ParticleOptions accentParticle, int perimeterCount, int interiorCount, float radiusFactor, float heightFactor, float driftStrength) {
    }
}
