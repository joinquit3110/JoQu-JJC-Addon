package net.mcreator.jujutsucraft.addon.limb;

import net.mcreator.jujutsucraft.addon.yuta.YutaFakePlayerEntity;
import net.mcreator.jujutsucraft.addon.util.ShrinePlatformEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class LimbEntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ENTITY_TYPES, (String)"jjkblueredpurple");
    public static final RegistryObject<EntityType<SeveredLimbEntity>> SEVERED_LIMB = ENTITIES.register("severed_limb", () -> EntityType.Builder.<SeveredLimbEntity>of(SeveredLimbEntity::new, (MobCategory)MobCategory.MISC).sized(0.4f, 0.4f).fireImmune().clientTrackingRange(10).updateInterval(20).build("severed_limb"));
    public static final RegistryObject<EntityType<YutaFakePlayerEntity>> YUTA_FAKE_PLAYER = ENTITIES.register("yuta_fake_player", () -> EntityType.Builder.<YutaFakePlayerEntity>of(YutaFakePlayerEntity::new, (MobCategory)MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(8).updateInterval(3).build("yuta_fake_player"));
    // Invisible solid collision slab the player stands on top of for the Malevolent Shrine. Sized at runtime; the registered defaults are only placeholders. updateInterval(1) keeps the moving platform position tightly synced so client prediction can stand on it.
    public static final RegistryObject<EntityType<ShrinePlatformEntity>> SHRINE_PLATFORM = ENTITIES.register("shrine_platform", () -> EntityType.Builder.<ShrinePlatformEntity>of(ShrinePlatformEntity::new, (MobCategory)MobCategory.MISC).sized(8.0f, 0.5f).fireImmune().noSummon().clientTrackingRange(12).updateInterval(1).build("shrine_platform"));
}
