package club.sjmc.smp3.daily.ui;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.sgui.api.elements.GuiElementBuilder;

import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.PackItem;
import club.sjmc.smp3.item.RewardBundle;
import club.sjmc.smp3.item.ui.PackGui;
import club.sjmc.smp3.ui.GuiStyle;
import club.sjmc.smp3.ui.ReturningGui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class QuestRewardsGui extends ReturningGui {
	private final List<ItemStack> items;
	private int page;
	private QuestRewardsGui(ServerPlayer player, List<ItemStack> items, boolean pending) {
		super(MenuType.GENERIC_9x6, player, () -> DailyQuestGui.show(player));
		this.items = items;
		setTitle(GuiStyle.windowTitle(pending ? "待领取奖励" : "任务物品奖励"));
		refresh();
	}
	public static void pending(ServerPlayer player) {
		ItemDelivery.deliver(player);
		var items = new ArrayList<>(ItemDelivery.pending(player));
		for (var effect : ItemDelivery.pendingEffects(player)) {
			var icon = new ItemStack(Items.PAPER);
			icon.set(DataComponents.CUSTOM_NAME, RewardBundle.description(effect, player.registryAccess()));
			items.add(icon);
		}
		new QuestRewardsGui(player, items, true).open();
	}
	public static void preview(ServerPlayer player, List<ItemStack> items) {
		new QuestRewardsGui(player, items, false).open();
	}
	private void refresh() {
		GuiStyle.fill(this, 54);
		for (int i = 0; i < 45 && page * 45 + i < items.size(); i++) {
			ItemStack stack = items.get(page * 45 + i);
			var icon = new GuiElementBuilder(stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())))
			               .addLoreLine(
			                   stack.getHoverName().copy().append(" x " + stack.getCount()).withStyle(GuiStyle.MUTED));
			if (stack.getItem() instanceof PackItem)
				icon.addLoreLine(GuiStyle.text("点击查看礼包内容", GuiStyle.ACTION));
			setSlot(i, GuiStyle
			               .onLeftClick(icon,
			                            () -> {
				                            if (stack.getItem() instanceof PackItem)
					                            PackGui.preview(player, stack, this::open);
			                            })
			               .build());
		}
		if (page > 0)
			setSlot(45, GuiStyle.button(Items.ARROW, "上一页", GuiStyle.ACTIVE, () -> {
				page--;
				refresh();
			}));
		if ((page + 1) * 45 < items.size())
			setSlot(53, GuiStyle.button(Items.ARROW, "下一页", GuiStyle.ACTIVE, () -> {
				page++;
				refresh();
			}));
		setSlot(49, GuiStyle.back(this::back));
	}
}
