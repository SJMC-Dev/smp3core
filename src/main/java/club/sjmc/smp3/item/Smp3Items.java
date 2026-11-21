package club.sjmc.smp3.item;

import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.block.Smp3Blocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ItemLore;

public final class Smp3Items {

	public static final Identifier ANY_ENCHANTED_BOOK_ID =
	    Identifier.fromNamespaceAndPath("smp3", "any_enchanted_book");

	private static final ResourceKey<Item> ANY_ENCHANTED_BOOK_KEY =
	    ResourceKey.create(Registries.ITEM, ANY_ENCHANTED_BOOK_ID);

	public static final Item ANY_ENCHANTED_BOOK = Registry.register(
	    BuiltInRegistries.ITEM, ANY_ENCHANTED_BOOK_ID,
	    new AnyEnchantedBookItem(
	        new Item.Properties()
	            .setId(ANY_ENCHANTED_BOOK_KEY)
	            .component(
	                DataComponents.LORE,
	                new ItemLore(List.of(
	                    Component.translatable("tooltip.smp3.any_enchanted_book.usage").withStyle(ChatFormatting.GRAY),
	                    Component.translatable("tooltip.smp3.any_enchanted_book.cost")
	                        .withStyle(ChatFormatting.GRAY))))));

	public static final Item NETHER_REACTOR_CORE =
	    Registry.register(BuiltInRegistries.ITEM, Smp3Blocks.REACTOR_ID,
		                  new BlockItem(Smp3Blocks.NETHER_REACTOR_CORE,
		                                new Item.Properties()
		                                    .setId(ResourceKey.create(Registries.ITEM, Smp3Blocks.REACTOR_ID))
		                                    .useBlockDescriptionPrefix()));

	public static final Item CHOICE_PACK = pack("choice_pack", true);
	public static final Item RANDOM_PACK = pack("random_pack", false);
	public static final Identifier CORAL_TOOL_ID = Identifier.fromNamespaceAndPath("smp3", "coral_tool");
	public static final Item CORAL_TOOL = Registry.register(
	    BuiltInRegistries.ITEM, CORAL_TOOL_ID,
	    new CoralToolItem(
	        new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CORAL_TOOL_ID)).durability(250)));
	public static final Item TICKET_SINGLE = ticket("ticket_single", false);
	public static final Item TICKET_TEN = ticket("ticket_ten", true);
	private static Item ticket(String name, boolean ten) {
		var id = Identifier.fromNamespaceAndPath("smp3", name);
		var usage = Component.translatable("tooltip.smp3.ticket.usage").withStyle(ChatFormatting.GRAY);
		return Registry.register(
		    BuiltInRegistries.ITEM, id,
		    new Item(new Item.Properties()
			             .setId(ResourceKey.create(Registries.ITEM, id))
			             .component(DataComponents.ITEM_NAME,
			                        Component.translatable("item.smp3." + name).withStyle(ChatFormatting.GOLD))
			             .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			             .component(DataComponents.LORE,
			                        new ItemLore(
		                                ten ? List.of(usage, Component.translatable("tooltip.smp3.ticket.ten_guarantee")
										                         .withStyle(ChatFormatting.YELLOW))
										    : List.of(usage)))));
	}
	private static Item pack(String name, boolean choice) {
		var id = Identifier.fromNamespaceAndPath("smp3", name);
		return Registry.register(
		    BuiltInRegistries.ITEM, id,
		    new PackItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)), choice));
	}
	private Smp3Items() {}

	public static void init() {
		ItemDefinitions.register();
		Smp3CreativeTab.register();
		ItemDelivery.register();
		Smp3Core.LOGGER.info("[SMP3] 已注册物品 {}", ANY_ENCHANTED_BOOK_ID);
	}
}
