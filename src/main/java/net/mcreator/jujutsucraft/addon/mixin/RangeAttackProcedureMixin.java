package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.RangeAttackProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into the range attack (Black Flash) procedure to:
 * 1. Suppress curse energy drain while BF cooldown is active
 * 2. Track Zone duration changes to detect BF procs
 * 3. Increment BF hit counter and reduce near-death cooldown
 */
@Mixin(value = RangeAttackProcedure.class, remap = false)
public class RangeAttackProcedureMixin {

    private static final String KEY_LAST_ZONE_DUR = "jjkbrp_last_zone_dur";
    private static final String KEY_BF_CD = "jjkbrp_bf_cd";
    private static final String KEY_CNT6_BACKUP = "jjkbrp_cnt6_backup";
    private static final String KEY_CNT6_SUPPRESSED = "jjkbrp_cnt6_suppressed";
    private static final String KEY_BF_ND_HANDLED = "jjkbrp_bf_nd_handled";
    private static final int BF_PROC_COOLDOWN_TICKS = 60;
    private static final int BF_ND_CD_REDUCTION = 600;

    @Inject(method = "execute", at = @At(value = "HEAD"), remap = false)
    private static void jjkblueredpurple$boostBlackFlash(LevelAccessor world, double x, double y, double z,
                                                         Entity entity, CallbackInfo ci) {
        if (entity == null) return;

        CompoundTag data = entity.getPersistentData();
        int bfCd = data.getInt(KEY_BF_CD);
        if (bfCd > 0 && !data.getBoolean(KEY_CNT6_SUPPRESSED)) {
            data.putDouble(KEY_CNT6_BACKUP, data.getDouble("cnt6"));
            data.putDouble("cnt6", 0.0);
            data.putBoolean(KEY_CNT6_SUPPRESSED, true);
        }
    }

    @Inject(method = "execute", at = @At(value = "RETURN"), remap = false)
    private static void jjkblueredpurple$resetBlackFlash(LevelAccessor world, double x, double y, double z,
                                                         Entity entity, CallbackInfo ci) {
        if (entity == null) return;

        CompoundTag data = entity.getPersistentData();

        if (data.getBoolean(KEY_CNT6_SUPPRESSED)) {
            data.putDouble("cnt6", data.getDouble(KEY_CNT6_BACKUP));
            data.remove(KEY_CNT6_BACKUP);
            data.putBoolean(KEY_CNT6_SUPPRESSED, false);
        }

        if (entity instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) entity;
            MobEffectInstance zone = le.getEffect(JujutsucraftModMobEffects.ZONE.get());
            int dur = zone != null ? zone.getDuration() : 0;
            int lastDur = data.getInt(KEY_LAST_ZONE_DUR);
            data.putInt(KEY_LAST_ZONE_DUR, dur);

            boolean bfJustProcced = dur >= 5990 && dur > lastDur;
            if (bfJustProcced) {
                int totalHits = data.getInt("addon_bf_total_hits");
                data.putInt("addon_bf_total_hits", totalHits + 1);

                int ndCd = data.getInt("jjkbrp_near_death_cd");
                if (ndCd > 0) {
                    data.putInt("jjkbrp_near_death_cd", Math.max(0, ndCd - BF_ND_CD_REDUCTION));
                }
                data.putInt(KEY_BF_CD, BF_PROC_COOLDOWN_TICKS);
                data.putBoolean(KEY_BF_ND_HANDLED, true);

                // Black Flash grants a temporary limb regen boost
                data.putBoolean("jjkbrp_bf_regen_boost", true);
            }
        }
    }
}
