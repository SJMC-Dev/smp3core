package club.sjmc.smp3.daily;

import java.util.List;

import com.google.gson.JsonObject;

public record QuestReward(int tongbao, int coffeeStamp, List<JsonObject> items, List<JsonObject> effects) {
	public QuestReward {
		effects = effects == null ? List.of() : effects.stream().map(JsonObject::deepCopy).toList();
		items = items == null ? List.of() : items.stream().map(JsonObject::deepCopy).toList();
	}
	public QuestReward(int tongbao, int coffeeStamp) { this(tongbao, coffeeStamp, List.of(), List.of()); }
	public QuestReward(int tongbao, int coffeeStamp, List<JsonObject> items) {
		this(tongbao, coffeeStamp, items, List.of());
	}
}
