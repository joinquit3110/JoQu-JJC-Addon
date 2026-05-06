package net.mcreator.jujutsucraft.addon.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.DoubleSupplier;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

/** Computes addon-adjusted domain radii without publishing long-lived global state. */
public final class DomainRadiusUtils {
    private static final double MIN_RADIUS = 1.0;
    private static final ThreadLocal<Deque<Scope>> RADIUS_SCOPE_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private DomainRadiusUtils() {
    }

    public static double resolveActualRadius(LevelAccessor world, CompoundTag nbt) {
        if (nbt != null) {
            if (nbt.contains("jjkbrp_actual_domain_radius")) {
                return Math.max(MIN_RADIUS, nbt.getDouble("jjkbrp_actual_domain_radius"));
            }
            if (nbt.contains("jjkbrp_base_domain_radius")) {
                double resolved = computeEffectiveRadius(nbt.getDouble("jjkbrp_base_domain_radius"), nbt.getDouble("jjkbrp_radius_multiplier"));
                nbt.putDouble("jjkbrp_actual_domain_radius", resolved);
                return resolved;
            }
        }
        return DomainAddonUtils.getActualDomainRadius(world, nbt);
    }

    public static Scope pushRadius(LevelAccessor world, double radius, String reason) {
        return new Scope(world, Math.max(MIN_RADIUS, radius), reason);
    }

    public static Scope pushEntityRadius(LevelAccessor world, Entity entity, String reason) {
        double radius = entity != null ? resolveActualRadius(world, entity.getPersistentData()) : currentAppliedRadius();
        if (radius <= 0.0) {
            radius = currentSharedRadius(world, 16.0);
        }
        return pushRadius(world, radius, reason);
    }

    public static double currentActualRadiusOverride() {
        Deque<Scope> stack = RADIUS_SCOPE_STACK.get();
        return stack.isEmpty() ? -1.0 : stack.peek().appliedRadius;
    }

    @Deprecated
    public static double currentAppliedRadius() {
        return currentActualRadiusOverride();
    }

    public static double computeEffectiveRadius(double baseOgRadius, double multiplier) {
        double safeMultiplier = Math.abs(multiplier) < 1.0E-4 ? 1.0 : multiplier;
        return Math.max(MIN_RADIUS, Math.max(MIN_RADIUS, baseOgRadius) * Math.max(0.5, safeMultiplier));
    }

    public static double computeOgInputRadiusForForm(LevelAccessor world, CompoundTag nbt, int form) {
        return Math.max(MIN_RADIUS, resolveActualRadius(world, nbt));
    }

    public static double currentSharedRadius(LevelAccessor world, double fallback) {
        try {
            return Math.max(MIN_RADIUS, JujutsucraftModVariables.MapVariables.get(world).DomainExpansionRadius);
        } catch (Exception ignored) {
            return Math.max(MIN_RADIUS, fallback);
        }
    }

    public static double withScopedRadius(LevelAccessor world, double effectiveRadius, DoubleSupplier action) {
        JujutsucraftModVariables.MapVariables mapVariables = JujutsucraftModVariables.MapVariables.get(world);
        double previous = mapVariables.DomainExpansionRadius;
        mapVariables.DomainExpansionRadius = Math.max(MIN_RADIUS, effectiveRadius);
        try {
            return action.getAsDouble();
        } finally {
            mapVariables.DomainExpansionRadius = previous;
        }
    }

    public static void withScopedRadius(LevelAccessor world, double effectiveRadius, Runnable action) {
        JujutsucraftModVariables.MapVariables mapVariables = JujutsucraftModVariables.MapVariables.get(world);
        double previous = mapVariables.DomainExpansionRadius;
        mapVariables.DomainExpansionRadius = Math.max(MIN_RADIUS, effectiveRadius);
        try {
            action.run();
        } finally {
            mapVariables.DomainExpansionRadius = previous;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final LevelAccessor world;
        private final double previousRadius;
        private final double appliedRadius;
        private final String reason;
        private boolean closed;

        private Scope(LevelAccessor world, double appliedRadius, String reason) {
            this.world = world;
            this.appliedRadius = Math.max(MIN_RADIUS, appliedRadius);
            this.reason = reason != null ? reason : "unspecified";
            this.previousRadius = currentSharedRadius(world, 16.0);
            RADIUS_SCOPE_STACK.get().push(this);
            writeRadius(world, this.appliedRadius);
        }

        public double getPreviousRadius() {
            return previousRadius;
        }

        public double getAppliedRadius() {
            return appliedRadius;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Deque<Scope> stack = RADIUS_SCOPE_STACK.get();
            if (!stack.isEmpty() && stack.peek() == this) {
                stack.pop();
            } else {
                stack.remove(this);
            }
            Scope parent = stack.peek();
            writeRadius(world, parent != null ? parent.appliedRadius : previousRadius);
            if (stack.isEmpty()) {
                RADIUS_SCOPE_STACK.remove();
            }
        }

        private static void writeRadius(LevelAccessor world, double radius) {
            try {
                JujutsucraftModVariables.MapVariables.get(world).DomainExpansionRadius = Math.max(MIN_RADIUS, radius);
            } catch (Exception ignored) {
                // Best-effort scoped bridge.
            }
        }
    }
}
