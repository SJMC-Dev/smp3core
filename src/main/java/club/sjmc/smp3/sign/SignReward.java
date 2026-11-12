package club.sjmc.smp3.sign;

import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public record SignReward(Kind kind, String value, int amount) {
	public enum Kind { CURRENCY, ITEM, TITLE, COMMAND }

	public static SignReward currency(Smp3Currency currency, int amount) {
		return new SignReward(Kind.CURRENCY, currency.id().toString(), amount);
	}

	public static SignReward item(Item item, int count) {
		return new SignReward(Kind.ITEM, BuiltInRegistries.ITEM.getKey(item).toString(), count);
	}

	public static SignReward title(String title) { return new SignReward(Kind.TITLE, title, 1); }

	public static SignReward command(String command) { return new SignReward(Kind.COMMAND, command, 1); }
}
