package net.mcreator.jujutsucraft.addon.logic;

/**
 * Immutable result of attempting to fire Fuga (Divine Flame Arrow) against a
 * {@link FugaRewardState}.
 *
 * <p>It bundles the dust value transferred into the flame-arrow projectile's
 * {@code cnt6} boost field with the resulting reward state. When no reward is
 * consumed (the identity gate fails or no reward flag is present) the transferred
 * dust is {@code 0.0} and {@link #next()} is the unchanged input state, so the
 * reward can never boost more than one Fuga.</p>
 *
 * <p>This type is intentionally free of Minecraft imports so the firing logic can
 * be unit- and property-tested without a running client or server.</p>
 *
 * @param transferredDust the real (clamped) dust transferred into the projectile's
 *                        {@code cnt6}; {@code 0.0} when no reward is consumed
 * @param next            the reward state after the fire attempt
 */
public record FugaFireResult(double transferredDust, FugaRewardState next) {
}
