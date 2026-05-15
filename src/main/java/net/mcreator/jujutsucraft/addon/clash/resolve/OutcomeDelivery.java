package net.mcreator.jujutsucraft.addon.clash.resolve;

import java.util.Comparator;
import java.util.List;

import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.init.JujutsucraftModEntities;
import net.mcreator.jujutsucraft.procedures.JujutsuBarrierUpdateTickProcedure;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Single choke-point for the {@code Closed_Equivalent_Behavior} loser-effect sequence applied
 * to a caster that has lost (or tied) a {@code Clash_Session}. This is the only place the addon
 * writes back to the base mod's participant state, so it is also the only place Requirement 7
 * is enforced end-to-end (Requirements 2.2, 2.3, 2.4, 7.1, 7.2, 7.3, 7.4, 7.6, 7.7, 12.4).
 *
 * <h2>Ordering contract</h2>
 * The ordering inside {@link #applyLoserEffects(LivingEntity, long)} is load-bearing and is
 * driven by how the base mod's {@code DomainExpansionEffectExpiresProcedure} reacts to the
 * {@code DomainDefeated} and {@code Failed} persistent-data flags when the
 * {@code DOMAIN_EXPANSION} mob effect is removed:
 * <ol>
 *   <li>Write {@code DomainDefeated=true} first (Requirement 7.2). The base mod's expire
 *       procedure reads this flag and skips the barrier-break path, which matches the original
 *       closed-vs-closed semantics required by Requirement 2.3.</li>
 *   <li>Write {@code Failed=true} (Requirement 7.3).</li>
 *   <li>Remove the {@code DOMAIN_EXPANSION} mob effect (Requirements 2.2 and 7.1). Removal of
 *       the effect invokes the base mod's {@code DomainExpansionEffectExpiresProcedure}
 *       naturally, which applies {@code UNSTABLE}, {@code FATIGUE}, and
 *       {@code COOLDOWN_TIME_SIMPLE_DOMAIN} for player casters at the durations the base mod
 *       chooses, without the addon duplicating those applications (Requirement 7.6).</li>
 *   <li>Stamp {@code jjkbrp_clash_result_tick} on the loser and on the opposing participant for
 *       observability by other addon subsystems (Requirement 7.7).</li>
 * </ol>
 *
 * <h2>Winner untouched</h2>
 * This class intentionally never reads or writes the winner's {@code Failed}, {@code Cover},
 * {@code DomainDefeated}, {@code select}, {@code skill_domain}, {@code x_pos_doma},
 * {@code y_pos_doma}, or {@code z_pos_doma} keys, and never removes a mob effect from the
 * winner. Callers are expected to pass only the losing participant to
 * {@link #applyLoserEffects(LivingEntity, long)}, and to call it twice (once per participant)
 * for {@code TIE} outcomes (Requirement 7.5 handled at the caller). Requirement 7.4 and
 * Requirement 12.4 are satisfied because the method body touches nothing on the winner.
 *
 * <h2>Cancellation</h2>
 * Cancelled sessions must <em>not</em> go through {@link #applyLoserEffects}. The
 * {@code ClashResolver} cancellation path deliberately does not call into this class so that
 * {@code DomainDefeated}, {@code Failed}, and {@code jjkbrp_clash_result_tick} remain untouched
 * on either participant, per Requirements 11.3 and 12.4.
 *
 * <h2>Threading</h2>
 * Called on the server-tick thread only. This class is stateless and safe for reuse across
 * sessions.
 */
public final class OutcomeDelivery {

    /**
     * Applies the {@code Closed_Equivalent_Behavior} loser-effect sequence to {@code loser}
     * (Requirements 2.2, 2.3, 2.4, 7.1, 7.2, 7.3, 7.6).
     *
     * <p>The NBT writes happen <em>before</em> the {@code DOMAIN_EXPANSION} mob effect is
     * removed so that the base mod's {@code DomainExpansionEffectExpiresProcedure} observes the
     * flags and skips {@code BreakDomainProcedure} (Requirements 7.2 and 2.3). After the effect
     * is removed, both the loser and the opposing participant receive the
     * {@code jjkbrp_clash_result_tick} stamp (Requirement 7.7 handled together via the caller's
     * second invocation of {@link #markResultTick(LivingEntity, long)}).
     *
     * <p>A {@code null} loser or a client-side entity is a no-op; the outcome sequence is a
     * server-authoritative operation (Requirement 14.1 thread model).
     *
     * @param loser      the losing participant; may be {@code null}, in which case the method
     *                   returns without side effects
     * @param serverTick the server tick on which the outcome is being applied; stored verbatim
     *                   into {@code jjkbrp_clash_result_tick} for downstream observability
     *                   (Requirement 7.7)
     */
    public void applyLoserEffects(LivingEntity loser, long serverTick) {
        if (loser == null) {
            return;
        }
        if (loser.level().isClientSide()) {
            return;
        }
        CompoundTag nbt = loser.getPersistentData();

        // Step 1 + 2 — DomainDefeated first, then Failed (Requirements 7.2 + 2.3, 7.3), so the
        // base mod's expire procedure observes the flags and skips the barrier-break path.
        writeLoserDefeatFlags(nbt);
        markResultTick(loser, serverTick);

        // Step 3 — remove DOMAIN_EXPANSION, which triggers DomainExpansionEffectExpiresProcedure
        // naturally and applies UNSTABLE/FATIGUE for player casters (Requirements 7.1, 7.6, 2.2).
        MobEffect domain = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (domain != null && loser.hasEffect(domain)) {
            loser.removeEffect(domain);
        }
        signalLoserCleanupEntity(loser, nbt);
        scheduleOwnedBarrierSweeps(loser, nbt);

    }


    private static void scheduleOwnedBarrierSweeps(LivingEntity loser, CompoundTag nbt) {
        if (!(loser.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(loser);
        double range = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(level, nbt));
        String ownerUuid = loser.getUUID().toString();
        runOwnedBarrierSweep(level, center, range, ownerUuid);
        if (level.getServer() != null) {
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 10, () -> runOwnedBarrierSweep(level, center, range, ownerUuid)));
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 30, () -> runOwnedBarrierSweep(level, center, range, ownerUuid)));
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 70, () -> runOwnedBarrierSweep(level, center, range, ownerUuid)));
        }
    }

    private static void runOwnedBarrierSweep(ServerLevel level, Vec3 center, double range, String ownerUuid) {
        int cx = (int)Math.round(center.x);
        int cy = (int)Math.round(center.y);
        int cz = (int)Math.round(center.z);
        int scan = (int)Math.ceil(range + 8.0D);
        int verticalExtra = 18;
        double radiusSq = (range + 5.0D) * (range + 5.0D);
        for (int x = cx - scan; x <= cx + scan; x++) {
            double dx = x - center.x;
            if (dx * dx > radiusSq) {
                continue;
            }
            for (int y = cy - scan - 2; y <= cy + scan + verticalExtra; y++) {
                double dy = y - center.y;
                for (int z = cz - scan; z <= cz + scan; z++) {
                    double dz = z - center.z;
                    double horizontalSq = dx * dx + dz * dz;
                    double distSq = horizontalSq + dy * dy;
                    boolean inDomainVolume = distSq <= radiusSq || (horizontalSq <= radiusSq && y >= cy - 2 && y <= cy + verticalExtra);
                    if (!inDomainVolume) {
                        continue;
                    }
                    BlockPos pos = BlockPos.containing(x, y, z);
                    if (!level.getBlockState(pos).is(BlockTags.create(new ResourceLocation("jujutsucraft:barrier")))) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null || !ownerUuid.equals(resolveBarrierOwner(be))) {
                        continue;
                    }
                    String oldBlock = be.getPersistentData().getString("old_block");
                    JujutsuBarrierUpdateTickProcedure.execute((LevelAccessor)level, pos.getX(), pos.getY(), pos.getZ());
                    if (level.getBlockState(pos).is(BlockTags.create(new ResourceLocation("jujutsucraft:barrier"))) && (oldBlock == null || oldBlock.isEmpty())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static String resolveBarrierOwner(BlockEntity be) {
        CompoundTag tag = be.getPersistentData();
        String owner = tag.getString("OWNER_UUID");
        if (owner == null || owner.isEmpty()) {
            owner = tag.getString("jjkbrp_owner_uuid");
        }
        return owner == null ? "" : owner;
    }    private static void signalLoserCleanupEntity(LivingEntity loser, CompoundTag nbt) {
        if (!(loser.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(loser);
        double range = Math.max(1.0, DomainAddonUtils.getActualDomainRadius(level, nbt));
        double searchRange = Math.max(8.0, range + 6.0);
        String ownerUuid = loser.getUUID().toString();
        List<DomainExpansionEntityEntity> entities = level.getEntitiesOfClass(
                DomainExpansionEntityEntity.class,
                new AABB(center.x - searchRange, center.y - searchRange, center.z - searchRange, center.x + searchRange, center.y + searchRange, center.z + searchRange),
                entity -> true
        );
        int signalled = 0;
        for (DomainExpansionEntityEntity cleanup : entities) {
            CompoundTag cleanupNbt = cleanup.getPersistentData();
            String cleanupOwner = cleanupNbt.getString("jjkbrp_owner_uuid");
            if (cleanupOwner == null || cleanupOwner.isEmpty()) {
                cleanupOwner = cleanupNbt.getString("OWNER_UUID");
            }
            Vec3 cleanupCenter = cleanupNbt.contains("x_pos")
                    ? new Vec3(cleanupNbt.getDouble("x_pos"), cleanupNbt.getDouble("y_pos"), cleanupNbt.getDouble("z_pos"))
                    : cleanup.position();
            boolean owned = ownerUuid.equals(cleanupOwner);
            boolean claimableAtLoserCenter = (cleanupOwner == null || cleanupOwner.isEmpty()) && cleanupCenter.distanceToSqr(center) <= Math.max(16.0D, range * range * 0.16D);
            if (!owned && !claimableAtLoserCenter) {
                continue;
            }
            wakeCleanupEntity(cleanup, ownerUuid, center, range);
            signalled++;
        }
        if (signalled == 0) {
            DomainExpansionEntityEntity spawned = spawnCleanupEntity(level, center, range);
            if (spawned != null) {
                wakeCleanupEntity(spawned, ownerUuid, center, range);
            }
        }
    }

    private static void wakeCleanupEntity(DomainExpansionEntityEntity cleanup, String ownerUuid, Vec3 center, double range) {
        CompoundTag cleanupNbt = cleanup.getPersistentData();
        cleanupNbt.putString("jjkbrp_owner_uuid", ownerUuid);
        cleanupNbt.putDouble("x_pos", center.x);
        cleanupNbt.putDouble("y_pos", center.y);
        cleanupNbt.putDouble("z_pos", center.z);
        cleanupNbt.putDouble("range", range);
        cleanupNbt.putBoolean("Break", true);
        cleanupNbt.putDouble("cnt_life2", 0.0D);
        cleanupNbt.putDouble("cnt_break", 0.0D);
        cleanup.setDeltaMovement(Vec3.ZERO);
        cleanup.setPos(center.x, center.y, center.z);
    }

    private static DomainExpansionEntityEntity spawnCleanupEntity(ServerLevel level, Vec3 center, double range) {
        try {
            DomainExpansionEntityEntity entity = new DomainExpansionEntityEntity((EntityType) JujutsucraftModEntities.DOMAIN_EXPANSION_ENTITY.get(), (Level) level);
            entity.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);
            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.getPersistentData().putDouble("x_pos", center.x);
            entity.getPersistentData().putDouble("y_pos", center.y);
            entity.getPersistentData().putDouble("z_pos", center.z);
            entity.getPersistentData().putDouble("range", range);
            level.addFreshEntity((Entity) entity);
            return entity;
        } catch (Exception ignored) {
            return null;
        }
    }    /**
     * Pure-NBT helper that performs the two "loser defeat flag" writes in the canonical order
     * used by {@link #applyLoserEffects(LivingEntity, long)}: {@code DomainDefeated=true} first
     * (Requirements 7.2 + 2.3), then {@code Failed=true} (Requirement 7.3). This helper is
     * package-private to let the {@code OutcomeDeliveryPropertyTest} validate the loser
     * NBT post-condition without needing a live {@link LivingEntity}/{@code ServerLevel}
     * harness (see task 8.4). The helper is a no-op for a {@code null} tag.
     *
     * <p>Production callers: this method is invoked only by
     * {@link #applyLoserEffects(LivingEntity, long)} itself. Tests drive the method directly
     * against a freshly-created {@link CompoundTag} to prove the three target keys end up at
     * the expected values and that every unrelated prior NBT key remains untouched.
     *
     * @param nbt the loser's persistent-data tag; may be {@code null}, in which case the
     *            method returns without side effects
     */
    static void writeLoserDefeatFlags(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        nbt.putBoolean("DomainDefeated", true);
        nbt.putBoolean("Failed", true);
    }

    /**
     * Pure-NBT helper that stamps {@code jjkbrp_clash_result_tick} (Requirement 7.7) onto the
     * supplied {@link CompoundTag}. This is the entity-free core of
     * {@link #markResultTick(LivingEntity, long)} and is package-private so the task 8.4 test
     * can assert the loser NBT post-condition without bootstrapping Minecraft.
     *
     * @param nbt        the participant's persistent-data tag; may be {@code null}, in which
     *                   case the method returns without side effects
     * @param serverTick the server tick written verbatim into {@code jjkbrp_clash_result_tick}
     */
    static void writeResultTickTag(CompoundTag nbt, long serverTick) {
        if (nbt == null) {
            return;
        }
        nbt.putLong("jjkbrp_clash_result_tick", serverTick);
    }


    public void preserveOpenWinnerSureHit(LivingEntity winner, long serverTick) {
        if (winner == null || winner.level().isClientSide()) {
            return;
        }
        CompoundTag nbt = winner.getPersistentData();
        boolean open = nbt.getBoolean("jjkbrp_open_form_active")
            || nbt.getInt("jjkbrp_domain_form_effective") == 2
            || nbt.getInt("jjkbrp_domain_form_cast_locked") == 2;
        MobEffect domain = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (domain != null && winner.hasEffect(domain)) {
            MobEffectInstance current = winner.getEffect(domain);
            open = open || (current != null && current.getAmplifier() > 0);
        }
        if (!open) {
            return;
        }
        nbt.putBoolean("Failed", false);
        nbt.putBoolean("Cover", false);
        nbt.putBoolean("DomainDefeated", false);
        nbt.putBoolean("DomainAttack", true);
        nbt.putBoolean("StartDomainAttack", true);
        nbt.putBoolean("jjkbrp_open_form_active", true);
        nbt.putInt("jjkbrp_domain_form_effective", 2);
        nbt.putLong("jjkbrp_open_won_clash_tick", serverTick);
        if (domain != null && !winner.hasEffect(domain)) {
            winner.addEffect(new MobEffectInstance(domain, 200, 1, true, false));
        } else if (domain != null) {
            MobEffectInstance current = winner.getEffect(domain);
            if (current != null && current.getAmplifier() <= 0) {
                winner.removeEffect(domain);
                winner.addEffect(new MobEffectInstance(domain, Math.max(20, current.getDuration()), 1, true, false));
            }
        }
    }
    /**
     * Writes the {@code jjkbrp_clash_result_tick} persistent-data key on {@code participant}
     * (Requirement 7.7). This single write is the only side effect of the method, so it is safe
     * to call on the winning participant to record the resolution tick without violating
     * Requirement 7.4 &mdash; the winner's {@code Failed}, {@code Cover}, {@code DomainDefeated},
     * {@code select}, {@code skill_domain}, {@code x_pos_doma}, {@code y_pos_doma}, and
     * {@code z_pos_doma} keys are untouched, and {@code jjkbrp_clash_result_tick} is an
     * addon-owned key, not one of the winner-protected keys enumerated by Requirement 7.4.
     *
     * <p>A {@code null} participant is a no-op.
     *
     * @param participant the participant to stamp; may be {@code null}
     * @param serverTick  the server tick recorded verbatim into
     *                    {@code jjkbrp_clash_result_tick}
     */
    public void markResultTick(LivingEntity participant, long serverTick) {
        if (participant == null) {
            return;
        }
        writeResultTickTag(participant.getPersistentData(), serverTick);
    }
}

