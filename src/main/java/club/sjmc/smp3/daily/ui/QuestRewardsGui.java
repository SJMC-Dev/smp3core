package club.sjmc.smp3.daily.ui;

import java.util.List;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.PackItem;
import club.sjmc.smp3.item.ui.PackGui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class QuestRewardsGui extends SimpleGui {
	private final List<ItemStack> items;
	private int page;
	private QuestRewardsGui(ServerPlayer player, List<ItemStack> items, boolean pending) {
		super(MenuType.GENERIC_9x6, player, false);
		this.items = items;
		setTitle(GuiStyle.text(pending ? "待领取奖励" : "任务物品奖励", GuiStyle.TITLE));
		refresh();
	}
	public static void pending(ServerPlayer player) {
		ItemDelivery.deliver(player);
		new QuestRewardsGui(player, ItemDelivery.pending(player), true).open();
	}
	public static void preview(ServerPlayer player, List<ItemStack> items) {
		new QuestRewardsGui(player, items, false).open();
	}
	private void refresh() {
		GuiStyle.fill(this, 54);
		for (int i = 0; i < 45 && page * 45 + i < items.size(); i++) {
			ItemStack stack = items.get(page * 45 + i);
			var icon = new GuiElementBuilder(stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())))
			               .addLoreLine(GuiStyle.text(stack.getHoverName().getString() + " x " + stack.getCount(),
			                                          GuiStyle.MUTED));
			if (stack.getItem() instanceof PackItem)
				icon.addLoreLine(GuiStyle.text("点击查看礼包内容", GuiStyle.ACTIVE));
			setSlot(i, GuiStyle
			               .onLeftClick(icon,
			                            () -> {
				                            if (stack.getItem() instanceof PackItem)
					                            PackGui.preview(player, stack);
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
		setSlot(49, GuiStyle.close(this::close));
	}
}
