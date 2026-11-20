package club.sjmc.smp3.milestone;

import static club.sjmc.smp3.config.JsonConfig.array;
import static club.sjmc.smp3.config.JsonConfig.currency;
import static club.sjmc.smp3.config.JsonConfig.fields;
import static club.sjmc.smp3.config.JsonConfig.object;
import static club.sjmc.smp3.config.JsonConfig.positive;
import static club.sjmc.smp3.config.JsonConfig.require;
import static club.sjmc.smp3.config.JsonConfig.text;
import static club.sjmc.smp3.config.JsonConfig.title;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

record MilestoneConfig(boolean enabled, Map<String, Rule> rules, Map<Identifier, List<Rule>> advancements,
                       Map<Smp3Currency, List<Rule>> balances) {
	record Rule(String id, Set<Identifier> requirements, Smp3Currency currency, long minimum,
	            Map<Smp3Currency, Integer> credits, List<String> titles) {
		boolean completed(ServerPlayer player) {
			var manager = player.level().getServer().getAdvancements();
			return requirements.stream().allMatch(id -> {
				var advancement = manager.get(id);
				return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
			});
		}
	}

	static MilestoneConfig parse(JsonObject root, MinecraftServer server) {
		fields(root, "enabled rules", "");
		require(root.get("enabled").isJsonPrimitive() && root.getAsJsonPrimitive("enabled").isBoolean(),
		        "enabled: expected boolean");
		var rules = new HashMap<String, Rule>();
		var advancements = new HashMap<Identifier, List<Rule>>();
		var balances = new HashMap<Smp3Currency, List<Rule>>();
		var entries = array(root, "rules");
		for (int i = 0; i < entries.size(); i++) {
			try {
				var entry = entries.get(i).getAsJsonObject();
				fields(entry, "id trigger grants", "");
				String id = text(entry, "id");
				require(!rules.containsKey(id), "duplicate rule id: " + id);
				var trigger = object(entry, "trigger");
				var requirements = new HashSet<Identifier>();
				Smp3Currency currency = null;
				long minimum = 0;
				switch (text(trigger, "type")) {
				case "advancement" -> {
					fields(trigger, "type id", "");
					var target = Identifier.parse(text(trigger, "id"));
					require(server.getAdvancements().get(target) != null, "unknown advancement: " + target);
					requirements.add(target);
				}
				case "advancement_tree" -> {
					fields(trigger, "type root", "exclude namespace");
					var roots = trigger.get("root");
					String namespace = trigger.has("namespace") ? text(trigger, "namespace") : null;
					for (var value : roots.isJsonArray() ? roots.getAsJsonArray().asList() : List.of(roots)) {
						require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
						        "root: expected advancement IDs");
						var target = Identifier.parse(value.getAsString());
						var node = server.getAdvancements().tree().get(target);
						require(node != null, "unknown root: " + target);
						descendants(node, namespace, requirements);
					}
					if (trigger.has("exclude")) {
						for (var excluded : array(trigger, "exclude")) {
							require(excluded.isJsonPrimitive() && excluded.getAsJsonPrimitive().isString(),
							        "exclude: expected advancement IDs");
							var excludedId = Identifier.parse(excluded.getAsString());
							require(requirements.remove(excludedId),
							        "exclude: duplicate or not in tree: " + excludedId);
						}
					}
					require(!requirements.isEmpty(), "tree requirements must not be empty");
				}
				case "balance" -> {
					fields(trigger, "type currency minimum", "");
					currency = currency(text(trigger, "currency"));
					minimum = Smp3Currency.rawUnits(positive(trigger.get("minimum")));
				}
				default -> throw new IllegalArgumentException("unknown trigger type");
				}
				var credits = new HashMap<Smp3Currency, Integer>();
				var titles = new ArrayList<String>();
				var grants = array(entry, "grants");
				require(!grants.isEmpty(), "grants must not be empty");
				for (int j = 0; j < grants.size(); j++) {
					try {
						var grant = grants.get(j).getAsJsonObject();
						switch (text(grant, "type")) {
						case "currency" -> {
							fields(grant, "type id count", "");
							credits.merge(currency(text(grant, "id")), positive(grant.get("count")), Math::addExact);
						}
						case "title" -> titles.add(title(grant));
						default -> throw new IllegalArgumentException("expected currency or title");
						}
					} catch (RuntimeException error) {
						throw new IllegalArgumentException("grants[" + j + "]: " + error.getMessage(), error);
					}
				}
				var rule =
				    new Rule(id, Set.copyOf(requirements), currency, minimum, Map.copyOf(credits), List.copyOf(titles));
				rules.put(id, rule);
				requirements.forEach(
				    required -> advancements.computeIfAbsent(required, key -> new ArrayList<>()).add(rule));
				if (currency != null)
					balances.computeIfAbsent(currency, key -> new ArrayList<>()).add(rule);
			} catch (RuntimeException error) {
				throw new IllegalArgumentException("milestone-rewards.json rules[" + i + "]: " + error.getMessage(),
				                                   error);
			}
		}
		return new MilestoneConfig(root.get("enabled").getAsBoolean(), Map.copyOf(rules), Map.copyOf(advancements),
		                           Map.copyOf(balances));
	}

	private static void descendants(AdvancementNode node, String namespace, Set<Identifier> result) {
		if (namespace == null || node.holder().id().getNamespace().equals(namespace))
			result.add(node.holder().id());
		node.children().forEach(child -> descendants(child, namespace, result));
	}
}
