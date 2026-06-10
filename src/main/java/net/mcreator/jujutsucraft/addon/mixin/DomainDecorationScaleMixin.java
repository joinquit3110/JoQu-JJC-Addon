package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.PehkuiDomainScaleUtil;
import net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity;
import net.mcreator.jujutsucraft.entity.EntityItemEntity;
import net.minecraft.server.level.ServerLevel;
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

@Mixin(targets = {
        "net.mcreator.jujutsucraft.entity.DomainExpansionEntityEntity",
        "net.mcreator.jujutsucraft.entity.EntityItemEntity",
        "net.mcreator.jujutsucraft.entity.EntityChimeraShadowGardenEntity",
        "net.mcreator.jujutsucraft.entity.EntityAwajiKnotEntity",
        "net.mcreator.jujutsucraft.entity.Gravestone1Entity",
        "net.mcreator.jujutsucraft.entity.Gravestone2Entity",
        "net.mcreator.jujutsucraft.entity.Gravestone3Entity",
        "net.mcreator.jujutsucraft.entity.Gravestone4Entity",
        "net.mcreator.jujutsucraft.entity.CoffinEntity",
        "net.mcreator.jujutsucraft.entity.EntityTreeEntity",
        "net.mcreator.jujutsucraft.entity.EntityTempleMainEntity",
        "net.mcreator.jujutsucraft.entity.EntityHeartEntity",
        "net.mcreator.jujutsucraft.entity.EntityWaterEntity",
        "net.mcreator.jujutsucraft.entity.EntityDomainInumakiEntity",
        "net.mcreator.jujutsucraft.entity.EntitySandStoneEntity",
        "net.mcreator.jujutsucraft.entity.PalmTreeEntity",
        "net.mcreator.jujutsucraft.entity.EntityJinichiDomain1Entity",
        "net.mcreator.jujutsucraft.entity.EntityJinichiDomain2Entity",
        "net.mcreator.jujutsucraft.entity.EntityDomainKugisakiEntity",
        "net.mcreator.jujutsucraft.entity.EntityCockroachEggsEntity",
        "net.mcreator.jujutsucraft.entity.EntityClockEntity",
        "net.mcreator.jujutsucraft.entity.EntityRozetsuDomainEntity",
        "net.mcreator.jujutsucraft.entity.EntityJupiterEntity",
        "net.mcreator.jujutsucraft.entity.TakadaEntity"
}, remap = false)
public abstract class DomainDecorationScaleMixin {
    @Inject(method = {"m_6075_"}, at = {@At(value = "TAIL")}, remap = false)
    private void jjkbrp$scaleDomainDecorationOnTick(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self.level().isClientSide() || !jjkbrp$isDomainDecoration(self)) {
            return;
        }
        if (!AddonGameRules.domainRadiusRules(self.level())) {
            return;
        }
        if (self.getPersistentData().getBoolean("jjkbrp_pehkui_domain_scaled")) {
            return;
        }
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity owner = jjkbrp$findDomainOwnerCovering(level, self);
        if (owner == null) {
            return;
        }
        PehkuiDomainScaleUtil.applyDecorationScale(self, owner.getPersistentData());
        self.getPersistentData().putBoolean("jjkbrp_pehkui_domain_scaled", true);
    }

    private static LivingEntity jjkbrp$findDomainOwnerCovering(ServerLevel level, Entity decoration) {
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        Vec3 pos = decoration.position();
        for (Player player : level.players()) {
            double distance = jjkbrp$domainDistanceIfCovered(level, player, pos);
            if (distance >= 0.0D && distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        double searchRadius = 192.0D;
        AABB scan = new AABB(pos.x - searchRadius, pos.y - searchRadius, pos.z - searchRadius, pos.x + searchRadius, pos.y + searchRadius, pos.z + searchRadius);
        for (LivingEntity caster : level.getEntitiesOfClass(LivingEntity.class, scan, entity -> !(entity instanceof Player))) {
            double distance = jjkbrp$domainDistanceIfCovered(level, caster, pos);
            if (distance >= 0.0D && distance < closestDistance) {
                closest = caster;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static double jjkbrp$domainDistanceIfCovered(ServerLevel level, LivingEntity caster, Vec3 pos) {
        if (!DomainAddonUtils.isDomainBuildOrActive(level, caster)) {
            return -1.0D;
        }
        Vec3 center = DomainAddonUtils.getDomainCenter(caster);
        double radius = DomainAddonUtils.getActualDomainRadius((LevelAccessor)level, caster.getPersistentData());
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        double horizontalSq = dx * dx + dz * dz;
        double radiusSq = Math.max(1.0D, radius * radius);
        double verticalLimit = Math.max(radius + 8.0D, radius * 1.35D);
        if (horizontalSq <= radiusSq && Math.abs(dy) <= verticalLimit) {
            return horizontalSq + dy * dy;
        }
        double distanceSq = horizontalSq + dy * dy;
        return distanceSq <= radiusSq ? distanceSq : -1.0D;
    }

    private static boolean jjkbrp$isDomainDecoration(Entity entity) {
        if (entity instanceof DomainExpansionEntityEntity) {
            return true;
        }
        if (entity instanceof EntityItemEntity itemEntity) {
            return itemEntity.getEntityData().get(EntityItemEntity.DATA_domain_decoration);
        }
        String simpleName = entity.getClass().getSimpleName();
        return simpleName.contains("ChimeraShadowGarden")
                || simpleName.contains("AwajiKnot")
                || simpleName.contains("Gravestone")
                || simpleName.contains("Coffin")
                || simpleName.contains("EntityTree")
                || simpleName.contains("TempleMain")
                || simpleName.contains("EntityHeart")
                || simpleName.contains("EntityWater")
                || simpleName.contains("DomainInumaki")
                || simpleName.contains("SandStone")
                || simpleName.contains("PalmTree")
                || simpleName.contains("JinichiDomain")
                || simpleName.contains("DomainKugisaki")
                || simpleName.contains("CockroachEggs")
                || simpleName.contains("EntityClock")
                || simpleName.contains("RozetsuDomain")
                || simpleName.contains("EntityJupiter")
                || simpleName.contains("Takada");
    }
}
