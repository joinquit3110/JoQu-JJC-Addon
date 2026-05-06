package net.mcreator.jujutsucraft.addon.util;

import com.mojang.logging.LogUtils;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Multi-wave rebuild request for a surviving closed/incomplete winner. */
public final class DomainRebuildPlan {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DomainRuntimeSnapshot snapshot;
    private final int totalWaves;
    private int wave;

    public DomainRebuildPlan(DomainRuntimeSnapshot snapshot) {
        this(snapshot, snapshot != null ? (int)Math.ceil(snapshot.getRadius() * 2.0 + 1.0) : 1);
    }

    public DomainRebuildPlan(DomainRuntimeSnapshot snapshot, int totalWaves) {
        this.snapshot = snapshot;
        this.totalWaves = Math.max(1, totalWaves);
    }

    public boolean tick(ServerLevel level, LivingEntity owner) {
        if (level == null || owner == null || snapshot == null || !snapshot.isValid() || snapshot.isOpen()) return true;
        if (!owner.isAlive() || owner.isRemoved()) return true;
        Vec3 c = snapshot.getCenter();
        CompoundTag nbt = owner.getPersistentData();
        boolean oldFailed = nbt.getBoolean("Failed");
        boolean oldDomainDefeated = nbt.getBoolean("DomainDefeated");
        boolean oldCover = nbt.getBoolean("Cover");
        double oldCnt1 = nbt.getDouble("cnt1");
        double oldCntCover = nbt.getDouble("cnt_cover");
        nbt.putBoolean("Failed", false);
        nbt.putBoolean("DomainDefeated", false);
        nbt.putBoolean("Cover", true);
        nbt.putBoolean("jjkbrp_clash_rebuild_active", true);
        nbt.putInt("jjkbrp_domain_form_cast_locked", snapshot.getEffectiveForm().getId());
        nbt.putInt("jjkbrp_domain_form_effective", snapshot.getEffectiveForm().getId());
        nbt.putDouble("select", snapshot.getDomainId());
        nbt.putDouble("skill_domain", snapshot.getDomainId());
        nbt.putDouble("x_pos_doma", c.x);
        nbt.putDouble("y_pos_doma", c.y);
        nbt.putDouble("z_pos_doma", c.z);
        nbt.putDouble("cnt6", snapshot.getRadius());
        nbt.putDouble("range", snapshot.getRadius());
        nbt.putDouble("cnt_cover", Math.max(1.0, oldCntCover + wave));
        nbt.putDouble("cnt1", 0.0);
        if (!owner.hasEffect(JujutsucraftModMobEffects.DOMAIN_EXPANSION.get())) {
            owner.addEffect(new MobEffectInstance(JujutsucraftModMobEffects.DOMAIN_EXPANSION.get(), 80, 0, false, false));
        }
        try (DomainRadiusUtils.Scope scope = DomainRadiusUtils.pushRadius(level, snapshot.getRadius(), "domain-rebuild-plan")) {
            DomainExpansionBattleProcedure.execute(level, c.x, c.y, c.z, owner);
            LOGGER.info("[DomainRebuildPlan] wave {}/{} owner={} form={} domainId={} radius={} center={} tick={}",
                    wave + 1, totalWaves, owner.getUUID(), snapshot.getEffectiveForm(), snapshot.getDomainId(),
                    String.format("%.1f", snapshot.getRadius()), c, level.getGameTime());
        } catch (Exception ex) {
            LOGGER.warn("[DomainRebuildPlan] rebuild wave failed owner={} wave={} radius={} center={} tick={}",
                    owner.getUUID(), wave + 1, String.format("%.1f", snapshot.getRadius()), c, level.getGameTime(), ex);
        } finally {
            nbt.putBoolean("jjkbrp_clash_rebuild_active", false);
            nbt.putDouble("cnt1", oldCnt1);
            if (wave + 1 >= totalWaves) {
                nbt.putBoolean("Cover", false);
                nbt.putBoolean("Failed", oldFailed);
                nbt.putBoolean("DomainDefeated", oldDomainDefeated);
            } else {
                nbt.putBoolean("Cover", true);
                nbt.putBoolean("Failed", false);
                nbt.putBoolean("DomainDefeated", false);
            }
        }
        wave++;
        return wave >= totalWaves;
    }

    public DomainRuntimeSnapshot getSnapshot() { return snapshot; }
    public int getTotalWaves() { return totalWaves; }
}
