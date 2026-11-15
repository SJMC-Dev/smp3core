package club.sjmc.smp3.client;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import club.sjmc.smp3.daily.QuestHudPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Preview data belongs to the client session and never enters the economy or quest services.
final class HudPreview {
	static final int CURRENCY_WIDTH = 272, NOTICE_WIDTH = 216;
	static final float FLOAT_SECONDS = 1.4F;
	private ItemStack coin, stamp, book;
	final QuestHudState quests = new QuestHudState();
	final Counter coins = new Counter(12_450), stamps = new Counter(18);
	final Set<Integer> opened = new HashSet<>(), acknowledged = new HashSet<>();
	boolean enabled;
	int selected, category;

	record Message(String category, String title, String date, String body, boolean acknowledgement) {}
	final List<Message> messages = List.of(
	    new Message(
	        "公告", "周末活动", "活动公告 · 示例内容",
	        "一起建设我们的公共花园\n\n这个周末，带上喜欢的花卉、木材和一点灵感，到公共区域一起布置花园。\n\n"
	            + "活动安排\n周六下午：整理道路与种植区域\n周日晚上：参观大家的作品\n\n参与方式\n准备好材料后，与其"
	            + "他玩家商量分工。新玩家也可以参与，不需要提前报名。\n\n温馨提示\n请保留他人的建筑与标记，使用公共"
	            + "材料后及时补充。\n\n本页是界面预览，活动时间与内容均为示例。",
	        false),
	    new Message("公告", "服务器维护提醒", "运维公告 · 示例内容",
		            "例行维护通知\n\n维护前请将物品收好，并在安全的位置退出游戏。\n\n维护完成后，管理员会发布新的通知"
	                    + "。\n\n这是用于检查列表与未读状态的示例消息。",
		            false),
	    new Message(
	        "服规", "共同维护友善社区", "行为准则 · 需要确认",
	        "尊重其他玩家\n\n友善交流，不骚扰、不辱骂，不泄露他人信息。\n\n尊重劳动成果\n未经允许，不拆除建筑、不取走"
	            + "他人物品。公共设施使用完毕后，请恢复原状。\n\n照顾服务器环境\n离开前关闭高频红石设备，及时整理掉落"
	            + "物。遇"
	            + "到异常或争议，请联系管理员。\n\n阅读后可点击下方按钮，预览确认状态。此操作仅影响本次预览。",
	        true),
	    new Message(
	        "帮助", "每日任务与签到", "操作指南 · 示例内容",
	        "每日任务\n\n右上角清单显示任务进度。完成任务并收到奖励后，清单会勾选并移除对应条目。\n\n显示设置\n按 H "
	            + "可以隐藏或显示任务清单，也可以在按键设置中重新绑定。\n\n签到与商店\n使用服务器提供的签到和商店菜单"
	            + "，查看"
	            + "奖励、商品和剩余额度。\n\nHUD 布局\n点击本页下方的 HUD "
	            + "设置，拖动显示区域，并调整大小。\n\n本原型中的余额与通知都是演示数据，不会更改实际账户。",
	        false),
	    new Message(
	        "帮助", "货币与界面设置", "操作指南 · 示例内容",
	        "铁镐通宝与咖啡印章\n\n铁镐图标代表通宝，纸张图标代表咖啡印章。余额显示在状态栏附近。\n\n金额变化"
	            + "\n绿色数字代表收入，红色数字代表支出。变化提示会向上浮动并逐渐消失。\n\n布局调整\n打开 HUD "
	            + "设置后，可拖动货币区和任务通知区。滚轮调整选中区域的大小。\n\n点击恢复默认可以撤销布局调整。位置"
	            + "、缩放与显示偏好保存在本机。",
	        false));

	void enable() {
		if (enabled)
			return;
		enabled = true;
		coin = new ItemStack(Items.IRON_PICKAXE);
		stamp = new ItemStack(Items.PAPER);
		book = new ItemStack(Items.BOOK);
		quests.accept(new QuestHudPayload(1, List.of(entry("stone", "stone", 24, 64), entry("wheat", "wheat", 8, 16),
		                                             entry("iron", "iron_ingot", 3, 8), entry("fish", "cod", 4, 4))),
		              Smp3Client.HUD.visible);
	}

	private static QuestHudPayload.Entry entry(String token, String item, int value, int target) {
		return new QuestHudPayload.Entry(token, Identifier.withDefaultNamespace(item), value, target, false);
	}

	void clear() {
		enabled = false;
		quests.clear();
		coins.reset(12_450);
		stamps.reset(18);
		opened.clear();
		acknowledged.clear();
		selected = 0;
		category = 0;
	}

	boolean unread(int index) {
		return messages.get(index).acknowledgement ? !acknowledged.contains(index) : !opened.contains(index);
	}

	int unreadCount() {
		int count = 0;
		for (int i = 0; i < messages.size(); i++)
			if (unread(i))
				count++;
		return count;
	}

	boolean noticeVisible() { return enabled && Smp3Client.layout.noticesVisible && unreadCount() > 0; }

	void renderCurrency(GuiGraphicsExtractor graphics, float seconds) {
		if (!enabled || !Smp3Client.layout.currencyVisible)
			return;
		var box = Smp3Client.layout.currency.box(graphics.guiWidth(), graphics.guiHeight(), CURRENCY_WIDTH, 20,
		                                         HudLayout.statusSpace());
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		coins.render(graphics, coin, 0, seconds);
		stamps.render(graphics, stamp, 136, seconds);
		graphics.pose().popMatrix();
	}

	void renderNotice(GuiGraphicsExtractor graphics, int x, int y) {
		if (!noticeVisible())
			return;
		int first = 0;
		while (!unread(first))
			first++;
		var font = Minecraft.getInstance().font;
		graphics.fill(x, y, x + NOTICE_WIDTH, y + 38, HudLayout.PANEL);
		graphics.item(book, x + 5, y + 9);
		graphics.fill(x + 18, y + 8, x + 23, y + 13, HudLayout.RED);
		String title = "新公告：" + messages.get(first).title;
		graphics.text(font, font.plainSubstrByWidth(title, NOTICE_WIDTH - 56), x + 29, y + 7, HudLayout.TEXT);
		graphics.text(font, "/smpinfo 查看  ·  预览", x + 29, y + 23, HudLayout.MUTED);
		graphics.text(font, Integer.toString(unreadCount()), x + NOTICE_WIDTH - 16, y + 7, HudLayout.RED);
	}

	static final class Counter {
		long value, change;
		float age = FLOAT_SECONDS;
		String text;
		Counter(long value) { reset(value); }
		void reset(long value) {
			this.value = value;
			text = String.format(Locale.ROOT, "%,d", value);
			age = FLOAT_SECONDS;
			change = 0;
		}
		void add(long amount) {
			long next = Math.max(0, value + amount);
			change = (age < FLOAT_SECONDS ? change : 0) + next - value;
			value = next;
			text = String.format(Locale.ROOT, "%,d", value);
			age = 0;
		}
		void render(GuiGraphicsExtractor graphics, ItemStack icon, int x, float seconds) {
			var font = Minecraft.getInstance().font;
			graphics.item(icon, x, 2);
			graphics.text(font, text, x + 22, 6, HudLayout.TEXT);
			age = Math.min(FLOAT_SECONDS, age + seconds);
			if (change == 0 || age >= FLOAT_SECONDS)
				return;
			int alpha = Math.round(255 * Math.min(1, (FLOAT_SECONDS - age) / .6F));
			if (alpha < 4)
				return;
			int color = (alpha << 24) | ((change > 0 ? HudLayout.GREEN : HudLayout.RED) & 0xFFFFFF);
			graphics.text(font, (change > 0 ? "+" : "") + change, x + 28 + font.width(text),
			              6 - Math.round(16 * age / FLOAT_SECONDS), color);
		}
	}
}
