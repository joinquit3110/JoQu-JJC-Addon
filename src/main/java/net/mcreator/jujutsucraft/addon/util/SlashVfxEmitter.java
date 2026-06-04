package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.addon.logic.SlashVfxPolicy;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Minecraft-facing emitter for the Malevolent Shrine radius-scaled
 * {@code jujutsucraft:particle_slash_large} "slash splash" VFX.
 *
 * <p>This is the single broadcast path used by BOTH slash sources so they are visually identical:</p>
 * <ul>
 *   <li>the Closed/Open Malevolent Shrine call-site path
 *       ({@code DomainMasteryMixin.jjkbrp$supplementMalevolentShrineVFX} →
 *       {@code jjkbrp$sendMalevolentShrineSlashVFX}); and</li>
 *   <li>the {@code Failed}-tolerant latch-driven incomplete sure-hit path
 *       ({@code CooldownTrackerEvents.handleSukunaIncompleteSureHitReward} →
 *       {@code SureHitShrineFx}).</li>
 * </ul>
 *
 * <p>The broadcast body here was lifted verbatim out of
 * {@code DomainMasteryMixin.jjkbrp$sendMalevolentShrineSlashVFX} so the {@code particle} command
 * string and the server command broadcast are <b>byte-for-byte identical</b> for both paths. The
 * count/spread/range numbers are produced by the pure {@link SlashVfxPolicy} so Closed/Open and the
 * incomplete sure-hit shrine render the same number of slashes at the same spread for an equal
 * radius. The visibility of the broadcast is whatever the {@code particle} command applies to the
 * players in range, exactly as it did before this extraction.</p>
 *
 * <p>This class is final, stateless, and holds only the Minecraft-facing broadcast; the deterministic
 * numeric decision lives in {@link SlashVfxPolicy} so it can be unit/property-tested off-server.</p>
 */
public final class SlashVfxEmitter {

    private SlashVfxEmitter() {
        // Pure utility class; no instances.
    }

    /**
     * Computes the slash spread/count from the given range/radius multiplier via {@link SlashVfxPolicy}
     * and broadcasts the slash splash. This is the shared entry point both the Closed/Open call-site
     * path and the incomplete sure-hit latch path call, so the two are guaranteed to use the same
     * scale and the same broadcast.
     *
     * @param world     the server level to broadcast into (no-op if {@code null})
     * @param center    the slash center (no-op if {@code null})
     * @param range     the emission range (from {@link SlashVfxPolicy#scaledRange(double, double)})
     * @param radiusMul the normalized radius multiplier
     *                  (from {@link SlashVfxPolicy#normalizedRadiusMul(double)})
     */
    public static void emitScaledSlash(ServerLevel world, Vec3 center, double range, double radiusMul) {
        if (world == null || center == null) {
            return;
        }
        double spread = SlashVfxPolicy.slashSpread(range);
        int count = SlashVfxPolicy.slashParticleCount(range, radiusMul);
        SlashVfxEmitter.broadcastSlash(world, center, spread, count);
    }

    /**
     * Broadcasts the radius-scaled {@code jujutsucraft:particle_slash_large} slash splash with the
     * given spread and count, using the exact server {@code particle} command broadcast that the
     * Closed/Open Malevolent Shrine has always used.
     *
     * <p>The command string and the suppressed-output {@link CommandSourceStack} broadcast are an
     * exact copy of the original {@code DomainMasteryMixin.jjkbrp$sendMalevolentShrineSlashVFX} body,
     * so the visual is byte-for-byte unchanged for the existing Closed/Open call site.</p>
     *
     * @param world  the server level to broadcast into (no-op if {@code null})
     * @param center the slash center (no-op if {@code null})
     * @param spread the per-axis particle spread (from {@link SlashVfxPolicy#slashSpread(double)})
     * @param count  the particle count (from {@link SlashVfxPolicy#slashParticleCount(double, double)})
     */
    public static void broadcastSlash(ServerLevel world, Vec3 center, double spread, int count) {
        if (world == null || center == null) {
            return;
        }
        String command = "particle jujutsucraft:particle_slash_large " + center.x + " " + center.y + " " + center.z + " " + spread + " " + spread + " " + spread + " 0.01 " + count + " normal";
        world.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, center, Vec2.ZERO, world, 4, "", Component.literal(""), world.getServer(), null).withSuppressedOutput(), command);
    }
}
