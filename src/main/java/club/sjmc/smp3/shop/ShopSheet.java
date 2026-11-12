package club.sjmc.smp3.shop;

import java.util.List;

public record ShopSheet(String name, List<ShopCategory> categories) {

	public ShopSheet { categories = List.copyOf(categories); }

	public int entryCount() { return categories.stream().mapToInt(ShopCategory::size).sum(); }

	public boolean isEmpty() { return entryCount() == 0; }
}
