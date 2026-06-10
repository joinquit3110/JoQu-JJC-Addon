package net.mcreator.jujutsucraft.addon.mixin;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.util.PlayerShrineRiseController;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = {
                "net.mcreator.jujutsucraft.entity.EntityMalevolentShrineEntity",
                "net.mcreator.jujutsucraft.entity.EntityMalevolentShrine2Entity"
        },
        remap = false
)
public abstract class PlayerShrineRiseTickMixin {
    @Inject(method = {"m_6075_"}, at = {@At(value = "TAIL")}, remap = false)
    private void jjkbrp$tickPlayerShrineRise(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (!AddonGameRules.domainForms(self)) {
            return;
        }
        PlayerShrineRiseController.tick(self);
    }
}
