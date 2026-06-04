package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.logic.SlashVfxPolicy;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side visual/terrain effects for the active Sukuna Incomplete Domain Shrine that carries
 * the Cleave Covenant (sure-hit) upgrade.
 *
 * <p>The base mod only runs the Malevolent Shrine's particle field + terrain pulverization inside
 * its {@code amplifier > 0} block. The addon runs the incomplete shrine at amplifier {@code 0}, so
 * that block never fires and the shrine has neither the dramatic VFX that Closed/Open domains show
 * nor any terrain destruction. This helper reproduces both, scaled to the actual mastery-modified
 * domain radius, and is driven every server tick from the reliable per-tick reward handler
 * ({@code CooldownTrackerEvents.handleSukunaIncompleteSureHitReward}) so it does not depend on the
 * base active procedure being reached for the player.</p>
 */
public final class SureHitShrineFx {

    // Barrier block tag id: domain barrier blocks must never be pulverized by the shrine sweep.
    private static final ResourceLocation BARRIER_TAG_ID = new ResourceLocation("jujutsucraft", "barrier");
    // Run the terrain-break sweep every N server ticks to spread the world-edit work out.
    private static final int BREAK_PERIOD_TICKS = 3;
    // Hard per-tick cap on broken blocks so a large mastery radius never stalls the server tick.
    private static final int MAX_BREAKS_PER_TICK = 96;
    // Number of random sample points probed per break tick; each clears a small cluster.
    private static final int BREAK_SAMPLES = 40;
    // Blocks at or above this hardness survive (mirrors the base shrine's "very tough block" cutoff).
    private static final double MAX_BREAK_HARDNESS = 99.0;

    private SureHitShrineFx() {
    }

    /**
     * Drives one tick of sure-hit shrine VFX + terrain destruction for the given player.
     *
     * @param player the shrine caster (must be server-side; caller guarantees the active sure-hit gate)
     * @param data   the player's persistent data (radius/center lookups)
     */
    public static void tick(ServerPlayer player, CompoundTag data) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = Math.max(8.0, DomainAddonUtils.getActualDomainRadius(level, data));
        Vec3 center = SureHitShrineFx.resolveCenter(player, data);
        SureHitShrineFx.spawnVfx(level, player.tickCount, center, radius);
        // Radius-scaled cleave "slash splash" (jujutsucraft:particle_slash_large), driven from this
        // Failed-tolerant latch path so it renders for the full domain duration just like the
        // Closed/Open forms. Emitted AFTER the ambient field above; the ambient emission is left
        // untouched (no duplication). The call-site emitter never fires for the incomplete shrine
        // (its gate is Failed-sensitive and excludes the incomplete form), so the two paths stay
        // mutually exclusive and no shrine is double-slashed.
        SureHitShrineFx.tickSlash(player, level, data, center);
        SureHitShrineFx.pulverizeTerrain(level, player.tickCount, center, radius);
    }

    /**
     * Emits the radius-scaled Malevolent Shrine slash splash for the active Sukuna incomplete
     * sure-hit shrine, reusing the SAME scale math ({@link SlashVfxPolicy}) and broadcast
     * ({@link SlashVfxEmitter}) that the Closed/Open call-site path uses, so the two forms are
     * visually identical at equal radius.
     *
     * <p>The cadence + "shrine is up" gate is the {@code Failed}-independent
     * {@link SlashVfxPolicy#shouldEmitIncompleteSurehitSlash(SlashVfxPolicy.SurehitLatchInputs)}
     * decision. Its inputs are resolved from the same live sources the activation gate and the
     * reward handler use:</p>
     * <ul>
     *   <li>{@code charId} / {@code surehitPurchased} from the player-scoped DomainMastery
     *       capability + {@code PlayerVariables} (Sukuna == 1, Cleave Covenant unlocked);</li>
     *   <li>{@code incompleteForm} / {@code runtimeDomainId} / {@code hasDomainEffect} /
     *       {@code domainDefeatedFlag} from {@link DomainAddonUtils};</li>
     *   <li>the surehit session/active latch flags from the caster's persistent NBT;</li>
     *   <li>{@code gameTime} from the server level (the cadence source the call-site path uses).</li>
     * </ul>
     *
     * <p>The {@code Failed} flag is deliberately NOT read: the latch path must keep rendering the
     * slash on the ticks the base mod stamps {@code Failed = true} on the incomplete shell.</p>
     */
    private static void tickSlash(ServerPlayer player, ServerLevel level, CompoundTag data, Vec3 center) {
        SlashVfxPolicy.SurehitLatchInputs inputs = SureHitShrineFx.resolveLatchInputs(player, level, data);
        if (!SlashVfxPolicy.shouldEmitIncompleteSurehitSlash(inputs)) {
            return;
        }
        // Resolve the radius-derived scale exactly as supplementMalevolentShrineVFX does: base
        // domain radius (default 16.0) and the radius multiplier, with the same near-zero floor.
        double radiusMul = data.getDouble("jjkbrp_radius_multiplier");
        if (Math.abs(radiusMul) < 1.0E-4) {
            radiusMul = 1.0;
        }
        double baseRadius = data.contains("jjkbrp_base_domain_radius") ? data.getDouble("jjkbrp_base_domain_radius") : 16.0;
        double normalizedRadiusMul = SlashVfxPolicy.normalizedRadiusMul(radiusMul);
        double range = SlashVfxPolicy.scaledRange(baseRadius, radiusMul);
        // Reuse the shared broadcast so Closed/Open and incomplete emit a byte-for-byte identical
        // slash; the slash center matches the existing ambient VFX anchor.
        SlashVfxEmitter.emitScaledSlash(level, center, range, normalizedRadiusMul);
    }

    /**
     * Builds the {@link SlashVfxPolicy.SurehitLatchInputs} for this tick from the same live
     * reads the activation gate ({@code MalevolentShrineActiveSukunaIncompleteMixin}) and the
     * reward handler ({@code CooldownTrackerEvents.handleSukunaIncompleteSureHitReward}) use, so
     * the slash decision tracks the exact set of ticks on which the shrine is latched-and-up.
     */
    private static SlashVfxPolicy.SurehitLatchInputs resolveLatchInputs(ServerPlayer player, ServerLevel level, CompoundTag data) {
        JujutsucraftModVariables.PlayerVariables vars = player.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        int charId = (int) Math.round(vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique);
        boolean surehitPurchased = player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null)
                .map(DomainMasteryData::isSukunaIncompleteSureHitUnlocked)
                .orElse(false);
        boolean hasDomainEffect = player.hasEffect((MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get());
        return new SlashVfxPolicy.SurehitLatchInputs(
                charId,
                DomainAddonUtils.isIncompleteDomainState(player),
                DomainAddonUtils.resolveRuntimeDomainId(player),
                surehitPurchased,
                data.getBoolean("jjkbrp_sukuna_incomplete_surehit_session"),
                data.getBoolean("jjkbrp_sukuna_incomplete_surehit_active"),
                hasDomainEffect,
                data.getBoolean("DomainDefeated"),
                level.getGameTime());
    }

    /**
     * Resolves the shrine center, preferring the saved domain center and falling back to the
     * player's feet so VFX always has a valid anchor even before {@code x_pos_doma} is written.
     */
    private static Vec3 resolveCenter(ServerPlayer player, CompoundTag data) {
        if (data.contains("x_pos_doma") && data.contains("y_pos_doma") && data.contains("z_pos_doma")) {
            double cx = data.getDouble("x_pos_doma");
            double cy = data.getDouble("y_pos_doma");
            double cz = data.getDouble("z_pos_doma");
            if (cx != 0.0 || cy != 0.0 || cz != 0.0) {
                return new Vec3(cx, cy, cz);
            }
        }
        return new Vec3(player.getX(), player.getY(), player.getZ());
    }

    /**
     * Ambient sure-hit cursed-dust field, scaled to the actual mastery radius. The radius-scaled
     * cleave slash (jujutsucraft:particle_slash_large) is emitted separately by
     * {@link #tickSlash(ServerPlayer, ServerLevel, CompoundTag, Vec3)} on this same driver, reusing
     * the shared {@link SlashVfxEmitter} / {@link SlashVfxPolicy} path so Closed/Open and the
     * incomplete sure-hit shrine look identical; this method only adds the ambient smoke/ash
     * atmosphere unique to the sure-hit shrine and is left unchanged by the slash fix.
     */
    private static void spawnVfx(ServerLevel level, long tick, Vec3 center, double radius) {
        // Ambient cursed-dust field every 5 ticks across the whole shrine volume.
        if (tick % 5L == 0L) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 1.0, center.z, 26, radius * 0.4, 1.2, radius * 0.4, 0.035);
            level.sendParticles(ParticleTypes.ASH, center.x, center.y + 1.2, center.z, 24, radius * 0.36, 1.4, radius * 0.36, 0.02);
            level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.5, center.z, 18, radius * 0.38, 0.8, radius * 0.38, 0.01);
        }
    }

    /**
     * Radius-bounded terrain pulverization for the active sure-hit shrine. Reproduces the base
     * shrine's {@code BlockDestroyAllDirectionProcedure} carving, clamped to within the
     * mastery-modified radius. Bounded by {@link #MAX_BREAKS_PER_TICK} so a large radius cannot
     * stall the tick.
     */
    private static void pulverizeTerrain(ServerLevel level, long tick, Vec3 center, double radius) {
        if (tick % BREAK_PERIOD_TICKS != 0L) {
            return;
        }
        double radiusSq = radius * radius;
        int broken = 0;
        for (int s = 0; s < BREAK_SAMPLES && broken < MAX_BREAKS_PER_TICK; ++s) {
            double ang = Math.toRadians(level.random.nextDouble() * 360.0);
            double horiz = radius * Math.sqrt(level.random.nextDouble());
            double px = center.x + Math.cos(ang) * horiz;
            double pz = center.z + Math.sin(ang) * horiz;
            double py = center.y + (level.random.nextDouble() * 2.0 - 1.0) * radius * 0.6;
            int cx = (int)Math.floor(px);
            int cy = (int)Math.floor(py);
            int cz = (int)Math.floor(pz);
            for (int dx = -1; dx <= 1 && broken < MAX_BREAKS_PER_TICK; ++dx) {
                for (int dy = -1; dy <= 1 && broken < MAX_BREAKS_PER_TICK; ++dy) {
                    for (int dz = -1; dz <= 1 && broken < MAX_BREAKS_PER_TICK; ++dz) {
                        BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                        if (pos.getY() < level.getMinBuildHeight() || pos.getY() > level.getMaxBuildHeight()) {
                            continue;
                        }
                        double ddx = (pos.getX() + 0.5) - center.x;
                        double ddy = (pos.getY() + 0.5) - center.y;
                        double ddz = (pos.getZ() + 0.5) - center.z;
                        if (ddx * ddx + ddy * ddy + ddz * ddz > radiusSq) {
                            continue;
                        }
                        if (SureHitShrineFx.tryBreak(level, pos)) {
                            ++broken;
                        }
                    }
                }
            }
        }
    }

    /**
     * Attempts to break a single block, respecting unbreakable/barrier/air/too-tough rules.
     * @return {@code true} when a block was actually removed.
     */
    private static boolean tryBreak(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (state.is(BlockTags.create(BARRIER_TAG_ID))) {
            return false;
        }
        double hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0 || hardness >= MAX_BREAK_HARDNESS) {
            return false;
        }
        level.destroyBlock(pos, false);
        return true;
    }
}
