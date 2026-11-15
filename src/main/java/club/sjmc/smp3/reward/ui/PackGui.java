package club.sjmc.smp3.reward.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.reward.PackItem;
import club.sjmc.smp3.reward.RewardItems;
import club.sjmc.smp3.reward.RewardService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PackGui extends SimpleGui {
	private final ItemStack pack;
	private final InteractionHand hand;
	private final boolean choice;
	private int page;
	private final int size, capacity;
	private static final MenuType<?>[] MENUS = {MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5,
	                                            MenuType.GENERIC_9x6};
	private boolean redeemed;
	private PackGui(ServerPlayer player, ItemStack pack, InteractionHand hand, int rows) {
		super(MENUS[rows - 3], player, false);
		size = rows * 9;
		capacity = size - 18;
		this.pack = pack;
		this.hand = hand;
		choice = ((PackItem)pack.getItem()).choice();
		setTitle(pack.getHoverName());
	}
	public static void open(ServerPlayer player, InteractionHand hand) {
		show(player, player.getItemInHand(hand), hand);
	}
	public static void preview(ServerPlayer player, ItemStack pack) { show(player, pack, null); }
	private static void show(ServerPlayer player, ItemStack pack, InteractionHand hand) {
		try {
			RewardItems.validate(RewardItems.json(pack, player.registryAccess()), player.registryAccess());
			int count = pack.get(RewardItems.PACK)
			                .getAsJsonArray(((PackItem)pack.getItem()).choice() ? "options" : "entries")
			                .size();
			PackGui gui = new PackGui(player, pack, hand, Math.min(6, Math.max(3, (count + 8) / 9 + 2)));
			gui.refresh();
			gui.open();
		} catch (RuntimeException e) {
			player.sendSystemMessage(GuiStyle.text("礼包配置无效，请联系管理员", GuiStyle.ERROR));
			Smp3Core.LOGGER.error("Invalid reward pack", e);
		}
	}
	private void refresh() {
		GuiStyle.fill(this, size);
		var definition = pack.get(RewardItems.PACK);
		var entries = definition.getAsJsonArray(choice ? "options" : "entries");
		setSlot(4, new GuiElementBuilder(pack.copyWithCount(1))
		               .addLoreLine(GuiStyle.text(
		                   choice ? "选择一份补给"
						          : "每包抽取 " + (definition.has("draws") ? definition.get("draws").getAsInt() : 1) +
						                " 次",
		                   GuiStyle.MUTED))
		               .build());
		int offset = entries.size() <= 9 ? (9 - entries.size()) / 2 : 0;
		for (int slot = 0; slot < capacity && page * capacity + slot < entries.size(); slot++) {
			int index = page * capacity + slot;
			var entry = entries.get(index).getAsJsonObject();
			List<ItemStack> items = new ArrayList<>();
			if (choice)
				for (var item : entry.getAsJsonArray("items"))
					items.add(RewardItems.stack(item.getAsJsonObject(), player.registryAccess()));
			else
				items.add(RewardItems.stack(entry.getAsJsonObject("item"), player.registryAccess()));
			var icon = new GuiElementBuilder(items.getFirst().copyWithCount(
			    Math.min(items.getFirst().getCount(), items.getFirst().getMaxStackSize())));
			if (choice) {
				icon.setName(GuiStyle.text(entry.get("name").getAsString(), GuiStyle.TITLE));
				for (var item : items)
					icon.addLoreLine(
					    GuiStyle.text(item.getHoverName().getString() + " x " + item.getCount(), GuiStyle.MUTED));
				if (hand != null)
					icon.addLoreLine(GuiStyle.text("点击选择，消耗 1 个礼包", GuiStyle.ACTIVE));
			} else {
				long total =
				    entries.asList().stream().mapToLong(v -> v.getAsJsonObject().get("weight").getAsLong()).sum();
				icon.addLoreLine(GuiStyle.text(
				    "数量 " + entry.get("min").getAsInt() + "–" + entry.get("max").getAsInt(), GuiStyle.MUTED));
				icon.addLoreLine(GuiStyle.text("权重 " + entry.get("weight").getAsInt() + "/" + total, GuiStyle.MUTED));
			}
			setSlot(9 + offset + slot, GuiStyle
			                               .onLeftClick(icon,
			                                            () -> {
				                                            if (choice && hand != null)
					                                            redeem(index);
			                                            })
			                               .build());
		}
		if (page > 0)
			setSlot(size - 9, GuiStyle.button(Items.ARROW, "上一页", GuiStyle.ACTIVE, () -> {
				page--;
				refresh();
			}));
		if ((page + 1) * capacity < entries.size())
			setSlot(size - 1, GuiStyle.button(Items.ARROW, "下一页", GuiStyle.ACTIVE, () -> {
				page++;
				refresh();
			}));
		setSlot(size - 5, GuiStyle.close(this::close));
		if (!choice && hand != null)
			setSlot(size - 7,
			        GuiStyle.button(Items.BUNDLE,
			                        "开启礼包（" + (definition.has("draws") ? definition.get("draws").getAsInt() : 1) +
			                            " 次抽取）",
			                        GuiStyle.READY, () -> redeem(0)));
	}
	private void redeem(int option) {
		if (redeemed)
			return;
		try {
			redeemed = RewardService.open(player, hand, pack, option, new Random());
			close();
			player.sendSystemMessage(GuiStyle.text(redeemed ? "礼包已开启，背包不足的物品保留在待领取奖励中"
			                                                : "手中的礼包已改变，请重新打开",
			                                       redeemed ? GuiStyle.READY : GuiStyle.ERROR));
		} catch (RuntimeException e) {
			close();
			player.sendSystemMessage(GuiStyle.text("礼包暂未开启，请稍后重试", GuiStyle.ERROR));
			Smp3Core.LOGGER.error("Pack redemption failed", e);
		}
	}
}
