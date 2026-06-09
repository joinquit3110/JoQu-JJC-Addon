package net.mcreator.jujutsucraft.addon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

public final class PehkuiDomainScaleUtil {
    private static final float MIN_DECORATION_SCALE = 0.1F;
    private static final float MAX_DECORATION_SCALE = 3.0F;

    private PehkuiDomainScaleUtil() {
    }

    public static float resolveDecorationScale(CompoundTag sourceData) {
        if (sourceData == null || !sourceData.contains("jjkbrp_radius_multiplier")) {
            return 1.0F;
        }
        double radiusMultiplier = sourceData.getDouble("jjkbrp_radius_multiplier");
        if (Math.abs(radiusMultiplier) < 1.0E-4D) {
            radiusMultiplier = 1.0D;
        }
        return (float)Math.max(MIN_DECORATION_SCALE, Math.min(MAX_DECORATION_SCALE, radiusMultiplier));
    }

    public static void applyDecorationScale(Entity entity, CompoundTag sourceData) {
        if (entity == null) {
            return;
        }
        applyScale(entity, resolveDecorationScale(sourceData));
    }

    public static void applyScale(Entity entity, float scale) {
        if (entity == null) {
            return;
        }
        float clamped = Math.max(MIN_DECORATION_SCALE, Math.min(MAX_DECORATION_SCALE, scale));
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setPersistence(true);
        data.setScaleTickDelay(0);
        data.setBaseScale(clamped);
        data.setScale(clamped);
        data.setTargetScale(clamped);
    }
}
