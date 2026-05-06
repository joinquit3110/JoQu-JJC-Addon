package net.mcreator.jujutsucraft.addon.limb;

import net.mcreator.jujutsucraft.addon.limb.SeveredLimbEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers custom entities used by the limb system.
 *
 * <p>At present this registry only exposes the detached severed limb entity used as physical visual
 * debris after a limb loss event.</p>
 */
public class LimbEntityRegistry {
    /** Deferred register for all limb-related entity types under the addon mod id. */
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ENTITY_TYPES, (String)"jjkblueredpurple");
    /**
     * Detached limb entity registration.
     *
     * <p>The entity is small, misc-category, fire immune, and uses a modest tracking range because it is
     * purely a short-lived visual/world object.</p>
     */
    public static final RegistryObject<EntityType<SeveredLimbEntity>> SEVERED_LIMB = ENTITIES.register("severed_limb", () -> EntityType.Builder.<SeveredLimbEntity>of(SeveredLimbEntity::new, (MobCategory)MobCategory.MISC).sized(0.4f, 0.4f).fireImmune().clientTrackingRange(10).updateInterval(20).build("severed_limb"));
}
