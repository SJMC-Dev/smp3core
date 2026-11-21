package club.sjmc.smp3.item;

import java.util.Optional;

import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;

public final class Smp3CreativeTab {
	private Smp3CreativeTab() {}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("smp3", "items"),
		                  FabricCreativeModeTab.builder()
		                      .title(Component.literal("SMP3"))
		                      .icon(() -> new ItemStack(Smp3Items.CORAL_TOOL))
		                      .displayItems((parameters, output) -> {
			                      output.accept(Smp3Items.ANY_ENCHANTED_BOOK);
			                      output.accept(Smp3Items.CORAL_TOOL);
			                      output.accept(Smp3Items.NETHER_REACTOR_CORE);
			                      output.accept(Smp3Items.TICKET_SINGLE);
			                      output.accept(Smp3Items.TICKET_TEN);
			                      String supplies = """
			                          {"options":[
			                            {"name":"Iron x 16","grants":[{"type":"item","id":"minecraft:iron_ingot","count":16}]},
			                            {"name":"Gold x 8","grants":[{"type":"item","id":"minecraft:gold_ingot","count":8}]}
			                          ]}
			                          """;
			                      output.accept(pack(Smp3Items.CHOICE_PACK, supplies));
			                      output.accept(pack(Smp3Items.RANDOM_PACK, supplies));
			                      var map = pack(Smp3Items.RANDOM_PACK, """
			                          {"options":[{"name":"Explorer Map","grants":[
			                            {"type":"loot_table","id":"smp3:quests/explorer_maps","rolls":1}
			                          ]}]}
			                          """);
			                      var model = Identifier.fromNamespaceAndPath("smp3", "map_pouch");
			                      map.set(DataComponents.ITEM_NAME, Component.translatable("item.smp3.map_pouch"));
			                      map.set(DataComponents.ITEM_MODEL, model);
			                      map.set(DataComponents.USE_COOLDOWN, new UseCooldown(5, Optional.of(model)));
			                      output.accept(map);
		                      })
		                      .build());
	}

	private static ItemStack pack(Item item, String contents) {
		var stack = new ItemStack(item);
		stack.set(ItemDefinitions.PACK, JsonParser.parseString(contents).getAsJsonObject());
		return stack;
	}
}
