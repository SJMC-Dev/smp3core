package club.sjmc.smp3.shop;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import club.sjmc.smp3.time.ServerDates;

record ShopQuota(LocalDate date, Map<String, Long> used) {
	static ShopQuota read(JsonObject state) {
		LocalDate today = ServerDates.today();
		if (state == null || !state.has("shop"))
			return new ShopQuota(today, new HashMap<>());
		JsonObject shop = state.getAsJsonObject("shop");
		LocalDate date = LocalDate.parse(shop.get("date").getAsString());
		if (today.isAfter(date))
			return new ShopQuota(today, new HashMap<>());
		Map<String, Long> used = new HashMap<>();
		shop.getAsJsonObject("used").entrySet().forEach(e -> used.put(e.getKey(), e.getValue().getAsLong()));
		return new ShopQuota(date, used);
	}
	void record(JsonObject state, ShopEntry entry, int amount) {
		used.merge(entry.id(), (long)amount, Math::addExact);
		JsonObject shop = new JsonObject(), quantities = new JsonObject();
		used.forEach(quantities::addProperty);
		shop.addProperty("date", date.toString());
		shop.add("used", quantities);
		state.add("shop", shop);
	}
}
