package club.sjmc.smp3.item;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class Smp3Items {

	public static final Identifier ANY_ENCHANTED_BOOK_ID =
	    Identifier.fromNamespaceAndPath("smp3", "any_enchanted_book");

	private static final ResourceKey<Item> ANY_ENCHANTED_BOOK_KEY =
	    ResourceKey.create(Registries.ITEM, ANY_ENCHANTED_BOOK_ID);

	public static final Item ANY_ENCHANTED_BOOK =
	    Registry.register(BuiltInRegistries.ITEM, ANY_ENCHANTED_BOOK_ID,
		                  new AnyEnchantedBookItem(new Item.Properties().setId(ANY_ENCHANTED_BOOK_KEY)));

	private Smp3Items() {}

	public static void init() { Smp3Core.LOGGER.info("[SMP3] 已注册物品 {}", ANY_ENCHANTED_BOOK_ID); }
}
