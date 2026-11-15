package club.sjmc.smp3.reward;

import java.util.List;

import com.google.gson.JsonObject;

public record Reward(int tongbao, int coffeeStamp, List<JsonObject> items) {
	public Reward { items = items == null ? List.of() : items.stream().map(JsonObject::deepCopy).toList(); }
	public Reward(int tongbao, int coffeeStamp) { this(tongbao, coffeeStamp, List.of()); }
}
