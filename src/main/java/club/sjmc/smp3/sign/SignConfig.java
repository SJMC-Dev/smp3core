package club.sjmc.smp3.sign;

import static club.sjmc.smp3.config.JsonConfig.array;
import static club.sjmc.smp3.config.JsonConfig.fields;
import static club.sjmc.smp3.config.JsonConfig.positive;
import static club.sjmc.smp3.config.JsonConfig.require;
import static club.sjmc.smp3.config.JsonConfig.text;
import static club.sjmc.smp3.config.JsonConfig.title;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import club.sjmc.smp3.config.JsonConfig;
import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public record SignConfig(List<Daily> daily, List<Milestone> monthly, List<Milestone> lifetime) {
	public record Daily(Smp3Currency currency, List<Integer> amounts) {
		int amount(int streak) { return amounts.get(Math.clamp(streak - 1, 0, amounts.size() - 1)); }
	}
	public record Milestone(int days, List<SignReward> rewards) {}

	public Map<Smp3Currency, Integer> dailyRewards(int streak) {
		var rewards = new LinkedHashMap<Smp3Currency, Integer>();
		daily.forEach(reward -> rewards.put(reward.currency(), reward.amount(streak)));
		return rewards;
	}

	public static SignConfig parse(JsonObject root) {
		try {
			fields(root, "daily monthly lifetime", "");
			var daily = new ArrayList<Daily>();
			var currencies = new HashSet<Smp3Currency>();
			for (var value : array(root, "daily")) {
				var entry = value.getAsJsonObject();
				fields(entry, "currency amounts", "");
				var currency = JsonConfig.currency(text(entry, "currency"));
				require(currencies.add(currency), "duplicate daily currency: " + currency.id());
				var amounts = array(entry, "amounts").asList().stream().map(JsonConfig::positive).toList();
				require(!amounts.isEmpty(), "amounts must not be empty");
				daily.add(new Daily(currency, amounts));
			}
			require(!daily.isEmpty(), "daily must not be empty");
			return new SignConfig(List.copyOf(daily), milestones(array(root, "monthly"), "monthly"),
			                      milestones(array(root, "lifetime"), "lifetime"));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("sign.json: " + error.getMessage(), error);
		}
	}

	private static List<Milestone> milestones(JsonArray entries, String section) {
		var result = new ArrayList<Milestone>();
		var days = new HashSet<Integer>();
		for (int i = 0; i < entries.size(); i++) {
			try {
				var entry = entries.get(i).getAsJsonObject();
				fields(entry, "days grants", "");
				int day = positive(entry.get("days"));
				require(!section.equals("monthly") || day <= 31, "monthly days must be at most 31");
				require(days.add(day), "duplicate days: " + day);
				var rewards = new ArrayList<SignReward>();
				var credits = new HashMap<String, Integer>();
				var grants = array(entry, "grants");
				require(!grants.isEmpty(), "grants must not be empty");
				for (int j = 0; j < grants.size(); j++) {
					try {
						var grant = grants.get(j).getAsJsonObject();
						rewards.add(switch (text(grant, "type")) {
							case "currency" -> {
								fields(grant, "type id count", "");
								var rewardCurrency = JsonConfig.currency(text(grant, "id"));
								int count = positive(grant.get("count"));
								credits.merge(rewardCurrency.id().toString(), count, Math::addExact);
								yield SignReward.currency(rewardCurrency, count);
							}
							case "item" -> {
								fields(grant, "type id count", "");
								var id = Identifier.parse(text(grant, "id"));
								require(BuiltInRegistries.ITEM.containsKey(id) &&
								            !id.equals(Identifier.withDefaultNamespace("air")),
								        "unknown item: " + id);
								yield SignReward.item(BuiltInRegistries.ITEM.getValue(id),
								                      positive(grant.get("count")));
							}
							case "title" -> SignReward.title(title(grant));
							case "command" -> {
								fields(grant, "type command", "");
								yield SignReward.command(text(grant, "command"));
							}
							default -> throw new IllegalArgumentException("unknown grant type");
						});
					} catch (RuntimeException error) {
						throw new IllegalArgumentException("grants[" + j + "]: " + error.getMessage(), error);
					}
				}
				result.add(new Milestone(day, List.copyOf(rewards)));
			} catch (RuntimeException error) {
				throw new IllegalArgumentException(section + "[" + i + "]: " + error.getMessage(), error);
			}
		}
		return List.copyOf(result);
	}
}
