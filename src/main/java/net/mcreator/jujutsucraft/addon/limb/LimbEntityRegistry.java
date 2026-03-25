package net.mcreator.jujutsucraft.addon.limb;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LimbEntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "jjkblueredpurple");

    public static final RegistryObject<EntityType<SeveredLimbEntity>> SEVERED_LIMB = ENTITIES.register("severed_limb",
        () -> EntityType.Builder.<SeveredLimbEntity>of(SeveredLimbEntity::new, MobCategory.MISC)
            .sized(0.4f, 0.4f)
            .fireImmune()
            .clientTrackingRange(10)
            .updateInterval(20)
            .build("severed_limb"));
}
