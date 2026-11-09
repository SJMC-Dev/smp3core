package club.sjmc.smp3.shop;

import java.util.List;

/**
 * 一个工作表页（例如「建筑材料商店」「回收商店」）。交易方向由各条目自身携带。
 *
 * @param name       工作表显示名
 * @param categories 工作表内的类别
 */
public record ShopSheet(String name, List<ShopCategory> categories) {

	public ShopSheet {
		categories = List.copyOf(categories);
	}

	public int entryCount() {
		return categories.stream().mapToInt(ShopCategory::size).sum();
	}

	public boolean isEmpty() {
		return entryCount() == 0;
	}
}
