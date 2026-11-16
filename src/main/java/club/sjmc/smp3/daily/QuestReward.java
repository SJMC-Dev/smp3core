package club.sjmc.smp3.daily;

import java.util.List;

import com.google.gson.JsonObject;

public record QuestReward(int tongbao, int coffeeStamp, List<JsonObject> items) {
	public QuestReward { items = items == null ? List.of() : items.stream().map(JsonObject::deepCopy).toList(); }
	public QuestReward(int tongbao, int coffeeStamp) { this(tongbao, coffeeStamp, List.of()); }
}
