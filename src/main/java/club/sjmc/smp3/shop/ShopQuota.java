package club.sjmc.smp3.shop;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import club.sjmc.smp3.time.ServerDates;

record ShopQuota(JsonObject entries, LocalDate today) {
	record Usage(LocalDate end, boolean weekly, long count) {}
	static ShopQuota read(JsonObject state) {
		return new ShopQuota(state != null && state.has("shop") ? state.getAsJsonObject("shop") : new JsonObject(),
		                     ServerDates.today());
	}
	Usage usage(ShopEntry entry) {
		if (entries.has(entry.id())) {
			var value = entries.getAsJsonObject(entry.id());
			var end = LocalDate.parse(value.get("end").getAsString());
			if (today.isBefore(end))
				return new Usage(end, value.get("weekly").getAsBoolean(), value.get("count").getAsLong());
		}
		return new Usage(entry.period().end(today), entry.period().weekly(), 0);
	}
	Map<String, Long> used() {
		var result = new HashMap<String, Long>();
		entries.entrySet().forEach(e -> {
			var value = e.getValue().getAsJsonObject();
			if (today.isBefore(LocalDate.parse(value.get("end").getAsString())))
				result.put(e.getKey(), value.get("count").getAsLong());
		});
		return result;
	}
	void record(JsonObject state, ShopEntry entry, int amount) {
		var usage = usage(entry);
		entries.entrySet().removeIf(
		    e -> !today.isBefore(LocalDate.parse(e.getValue().getAsJsonObject().get("end").getAsString())));
		var value = new JsonObject();
		value.addProperty("end", usage.end().toString());
		value.addProperty("weekly", usage.weekly());
		value.addProperty("count", Math.addExact(usage.count(), amount));
		entries.add(entry.id(), value);
		state.add("shop", entries);
	}
}
