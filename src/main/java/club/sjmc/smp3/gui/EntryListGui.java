package club.sjmc.smp3.gui;

import club.sjmc.smp3.perm.IdentityService;
import club.sjmc.smp3.quota.QuotaStore;
import club.sjmc.smp3.shop.Messages;
import club.sjmc.smp3.shop.ShopCategory;
import club.sjmc.smp3.shop.ShopEntry;
import club.sjmc.smp3.shop.ShopService;
import club.sjmc.smp3.shop.TradeDirection;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 条目页：某类别下的商品 / 回收项。
 *
 * <p>每次点击后重新生成 lore，跨天后的剩余额度随即刷新；额度为 0 的条目仍然显示，
 * 但用屏障图标置灰。</p>
 */
public final class EntryListGui extends ShopGui {

	private final ServerPlayer player;
	private final ShopCategory category;
	private final ShopGui parent;

	public EntryListGui(ServerPlayer player, ShopCategory category, ShopGui parent) {
		super(player, Component.literal(category.name()));
		this.player = player;
		this.category = category;
		this.parent = parent;
	}

	@Override
	public boolean open() {
		populate();
		return super.open();
	}

	private void populate() {
		clearContent();
		setBackButton(parent);

		boolean advanced = IdentityService.isAdvanced(player);
		int slot = 0;
		for (ShopEntry entry : category.entries()) {
			if (slot >= CONTENT_SLOTS) {
				break;
			}
			setSlot(slot++, element(entry, advanced));
		}
	}

	private GuiElement element(ShopEntry entry, boolean advanced) {
		int used = QuotaStore.used(player, entry);
		boolean locked = entry.isLockedFor(advanced);
		boolean exhausted = !locked && entry.exhausted(advanced, used);

		ItemStack stack = locked ? new ItemStack(Items.BARRIER) : new ItemStack(entry.icon());
		String pricePrefix = entry.direction() == TradeDirection.BUY ? "价格：" : "收购价：";

		GuiElementBuilder builder = new GuiElementBuilder(stack)
				.setName(entry.name().copy().withStyle(locked ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE))
				.addLoreLine(Component.literal(pricePrefix + entry.priceLabel()).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("你今日剩余：" + entry.remainingLabel(advanced, used))
						.withStyle(ChatFormatting.GRAY));

		if (locked) {
			builder.addLoreLine(Component.literal("你当前身份今日不可交易该条目").withStyle(ChatFormatting.RED));
		} else if (exhausted) {
			builder.addLoreLine(Messages.quotaExhausted(entry, advanced, used));
		} else {
			builder.addLoreLine(Component.literal(entry.actionHint()).withStyle(ChatFormatting.YELLOW));
		}

		builder.setCallback((index, type, action, gui) -> {
			if (!isLeftClick(type)) {
				return;
			}
			if (entry.direction() == TradeDirection.BUY) {
				ShopService.buy(player, entry);
			} else {
				ShopService.sell(player, entry);
			}
			// 交易后重新生成 lore（价格、今日剩余）
			populate();
		});

		return builder.build();
	}
}
