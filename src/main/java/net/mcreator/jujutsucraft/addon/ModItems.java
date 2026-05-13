package net.mcreator.jujutsucraft.addon;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"jjkblueredpurple");
    public static final RegistryObject<Item> YUTA_FINGER = ITEMS.register("yuta_finger", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> YUTA_HAND = ITEMS.register("yuta_hand", () -> new Item(new Item.Properties().stacksTo(64)));

    private ModItems() {
    }
}

