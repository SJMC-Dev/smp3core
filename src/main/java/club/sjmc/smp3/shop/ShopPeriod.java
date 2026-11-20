package club.sjmc.smp3.shop;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

public record ShopPeriod(boolean weekly, DayOfWeek weekStartsOn) {
	public static final ShopPeriod DAILY = new ShopPeriod(false, DayOfWeek.MONDAY);
	static ShopPeriod parse(JsonObject json) {
		String period = GsonHelper.getAsString(json, "period");
		if (!period.equals("daily") && !period.equals("weekly"))
			throw new IllegalArgumentException("period must be daily or weekly");
		if (period.equals("daily") && json.has("weekStartsOn"))
			throw new IllegalArgumentException("weekStartsOn requires weekly period");
		String day = GsonHelper.getAsString(json, "weekStartsOn", "monday");
		if (!day.equals(day.toLowerCase(Locale.ROOT)))
			throw new IllegalArgumentException("weekStartsOn must be lowercase");
		return new ShopPeriod(period.equals("weekly"), DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
	}
	LocalDate start(LocalDate day) { return weekly ? day.with(TemporalAdjusters.previousOrSame(weekStartsOn)) : day; }
	LocalDate end(LocalDate day) { return start(day).plusDays(weekly ? 7 : 1); }
}
