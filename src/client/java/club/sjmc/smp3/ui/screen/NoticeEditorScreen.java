package club.sjmc.smp3.ui.screen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.notice.NoticeCatalog;
import club.sjmc.smp3.notice.NoticeEditing;
import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.ServerBookScreen;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.BookText;
import club.sjmc.smp3.ui.widget.PaperButton;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NoticeEditorScreen extends Screen {
	private static final int INK = PaperStyle.INK;
	private final String session;
	private final List<NoticeCatalog.Category> categories;
	private final NoticeEditing.Draft original;
	private final String[] fields;
	private String body, status = "";
	private boolean markdown, pinned, notify, acknowledgement, publishing, discard, confirmDelete;
	private int tab;
	private int x, y, panelWidth, panelHeight, contentTop, contentBottom, previewX, previewWidth, scroll;
	private boolean narrow;
	private BookText.Layout textLayout = BookText.Layout.EMPTY;
	private String renderedBody;
	private int renderedWidth;
	private boolean renderedMarkdown;

	public NoticeEditorScreen(NoticePayloads.Editor payload) {
		super(Component.literal("编辑公告"));
		session = payload.session();
		categories = payload.categories();
		original = NoticeEditing.JSON.fromJson(payload.draft(), NoticeEditing.Draft.class);
		fields = new String[] {original.id(), original.title(), original.category(), original.categoryName(),
		                       original.date()};
		body = original.body();
		markdown = original.markdown();
		pinned = original.pinned();
		notify = original.notifyUnread();
		acknowledgement = original.acknowledgement();
	}

	private NoticeEditing.Draft draft() {
		return new NoticeEditing.Draft(fields[0], fields[1], fields[2], fields[3], fields[4], body, markdown, pinned,
		                               notify, acknowledgement);
	}

	@Override
	protected void init() {
		x = Math.max(8, (width - 720) / 2);
		y = Math.max(8, (height - 440) / 2);
		panelWidth = width - 2 * x;
		panelHeight = height - 2 * y;
		narrow = panelWidth < 500;
		int left = x + 16, inner = panelWidth - 32, half = (inner - 8) / 2;
		if (confirmDelete) {
			int bottom = y + panelHeight - 28;
			addRenderableWidget(new PaperButton(left, bottom, 94, publishing ? "正在删除…" : "确认删除", false, () -> {
				publishing = true;
				rebuildWidgets();
				ClientPlayNetworking.send(new NoticePayloads.Delete(session));
			})).active = !publishing;
			addRenderableWidget(new PaperButton(left + 102, bottom, 64, "取消", false, this::onClose)).active =
			    !publishing;
			return;
		}
		int metadataY = narrow ? y + 60 : y + 34;
		if (!narrow || tab == 0) {
			field(0, left, metadataY, half, "公告 ID", 64, original.id().isEmpty());
			field(1, left + half + 8, metadataY, half, "标题", 128, true);
			field(2, left, metadataY + 32, half, "分类 ID", 64, true);
			field(3, left + half + 8, metadataY + 32, half, "分类名称（新分类可直接填写）", 32, true);
			field(4, left, metadataY + 64, Math.min(108, inner / 3), "日期 YYYY-MM-DD（可留空）", 10, true);
			int flagX = left + Math.min(108, inner / 3) + 6;
			int flagWidth = (left + inner - flagX - 12) / 3;
			toggle(flagX, metadataY + 64, flagWidth, "置顶", pinned, () -> pinned = !pinned);
			toggle(flagX + flagWidth + 6, metadataY + 64, flagWidth, "提醒", notify, () -> notify = !notify);
			toggle(flagX + 2 * (flagWidth + 6), metadataY + 64, flagWidth, "需确认", acknowledgement,
			       () -> acknowledgement = !acknowledgement);
		}
		if (narrow) {
			String[] tabs = {"信息", "正文", "预览"};
			for (int i = 0; i < tabs.length; i++) {
				int selected = i;
				addRenderableWidget(new PaperButton(left + i * 70, y + 28, 64, tabs[i], true, () -> {
					tab = selected;
					rebuildWidgets();
				})).active = tab != i;
			}
		}
		if (!narrow || tab == 1)
			addRenderableWidget(new PaperButton(left, narrow ? y + 53 : y + 123, 84, markdown ? "Markdown" : "纯文本",
			                                    true,
			                                    () -> {
				                                    markdown = !markdown;
				                                    rebuildWidgets();
			                                    }))
			    .active = !publishing;
		if ((!narrow || tab == 0) && !categories.isEmpty())
			addRenderableWidget(new PaperButton(narrow ? left : left + 92, narrow ? y + 153 : y + 123, 100,
			                                    "选择已有分类", false,
			                                    () -> {
				                                    int index = 0;
				                                    for (int i = 0; i < categories.size(); i++)
					                                    if (categories.get(i).id().equals(fields[2]))
						                                    index = (i + 1) % categories.size();
				                                    fields[2] = categories.get(index).id();
				                                    fields[3] = categories.get(index).name();
				                                    rebuildWidgets();
			                                    }))
			    .active = !publishing;
		contentTop = y + (narrow ? tab == 2 ? 55 : 77 : 147);
		contentBottom = y + panelHeight - 50;
		previewX = narrow ? left : left + half + 8;
		previewWidth = narrow ? inner : half;
		if (!narrow || tab == 1) {
			var text = MultiLineEditBox.builder()
			               .setX(left)
			               .setY(contentTop)
			               .setTextColor(INK)
			               .setCursorColor(INK)
			               .setTextShadow(false)
			               .setShowBackground(false)
			               .setShowDecorations(false)
			               .setPlaceholder(Component.literal("在这里编写公告正文…"))
			               .build(font, narrow ? inner : half, Math.max(20, contentBottom - contentTop),
			                      Component.literal("公告正文"));
			text.setCharacterLimit(32768);
			text.setValue(body);
			text.setValueListener(value -> {
				body = value;
				scroll = 0;
			});
			text.active = !publishing;
			addRenderableWidget(text);
		}
		int buttonY = y + panelHeight - 28;
		if (discard) {
			addRenderableWidget(
			    new PaperButton(left, buttonY, 94, "放弃修改", false, () -> minecraft.gui.setScreen(null)));
			addRenderableWidget(new PaperButton(left + 102, buttonY, 94, "继续编辑", false, () -> {
				discard = false;
				status = "";
				rebuildWidgets();
			}));
		} else {
			addRenderableWidget(
			    new PaperButton(left, buttonY, 94, publishing ? "正在发布…" : "发布公告", false, this::publish))
			    .active = !publishing;
			addRenderableWidget(new PaperButton(left + 102, buttonY, 64, "返回", false, this::onClose)).active =
			    !publishing;
			if (!original.id().isEmpty())
				addRenderableWidget(new PaperButton(x + panelWidth - 100, buttonY, 84, "删除公告", false, () -> {
					confirmDelete = true;
					rebuildWidgets();
				})).active = !publishing;
		}
	}

	private void field(int index, int left, int top, int size, String label, int limit, boolean editable) {
		var input = new EditBox(font, left, top, size, 18, Component.literal(label));
		input.setBordered(false);
		input.setTextShadow(false);
		input.setTextColor(INK);
		input.setTextColorUneditable(0xFF827058);
		input.setMaxLength(limit);
		input.setValue(fields[index]);
		input.setEditable(editable && !publishing);
		input.setResponder(value -> fields[index] = value);
		input.setTooltip(Tooltip.create(Component.literal(label)));
		addRenderableWidget(input);
	}

	private void toggle(int left, int top, int size, String label, boolean value, Runnable action) {
		addRenderableWidget(new PaperButton(left, top, size, label + (value ? "：是" : "：否"), false, () -> {
			action.run();
			rebuildWidgets();
		})).active = !publishing;
	}

	private void publish() {
		if (body.getBytes(StandardCharsets.UTF_8).length > 32768) {
			status = "正文超过 32768 UTF-8 字节，请缩短内容";
			return;
		}
		String json = NoticeEditing.JSON.toJson(draft());
		List<String> parts = new ArrayList<>();
		for (int start = 0; start < json.length();) {
			int end = Math.min(json.length(), start + 8192);
			if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1)))
				end--;
			parts.add(json.substring(start, end));
			start = end;
		}
		publishing = true;
		status = "正在保存并更新公告…";
		rebuildWidgets();
		for (int i = 0; i < parts.size(); i++)
			ClientPlayNetworking.send(new NoticePayloads.Publish(session, i, parts.size(), parts.get(i)));
	}

	public void result(NoticePayloads.Editor payload) {
		if (!session.equals(payload.session()))
			return;
		publishing = false;
		confirmDelete = false;
		if (payload.message().isEmpty()) {
			minecraft.gui.setScreen(new ServerBookScreen());
		} else {
			status = payload.message();
			rebuildWidgets();
		}
	}

	@Override
	public void onClose() {
		if (publishing)
			return;
		if (confirmDelete) {
			confirmDelete = false;
			rebuildWidgets();
			return;
		}
		if (original.equals(draft()))
			minecraft.gui.setScreen(null);
		else {
			discard = true;
			status = "修改尚未发布，要放弃吗？";
			rebuildWidgets();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x66000000);
		PaperStyle.frame(graphics, x, y, panelWidth, panelHeight);
		graphics.text(font, title, x + 16, y + 11, INK, false);
		if (confirmDelete) {
			int top = y + 48;
			for (var line : font.split(Component.literal("删除公告「" + original.title() +
			                                             "」？公告将从目录和提醒中移除，正文文件保留。"),
			                           panelWidth - 32)) {
				graphics.text(font, line, x + 16, top, INK, false);
				top += 12;
			}
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			return;
		}
		int half = (panelWidth - 40) / 2;
		String[] labels = {"公告 ID", "标题", "分类 ID", "分类名称", "日期"};
		for (int i = 0; (!narrow || tab == 0) && i < labels.length; i++) {
			int left = x + 16 + (i % 2) * (half + 8), top = y + (narrow ? 50 : 24) + (i / 2) * 32;
			graphics.text(font, labels[i], left, top, 0xFF827058, false);
			graphics.horizontalLine(left, left + (i == 4 ? Math.min(108, (panelWidth - 32) / 3) : half), top + 29,
			                        0xFFB7A17D);
		}
		if (!narrow || tab == 2) {
			if (!body.equals(renderedBody) || renderedWidth != previewWidth || renderedMarkdown != markdown) {
				textLayout = BookText.layout(new NoticePayloads.Body("", "", body, markdown), font, previewWidth - 12);
				renderedBody = body;
				renderedWidth = previewWidth;
				renderedMarkdown = markdown;
			}
			scroll = Math.clamp(scroll, 0, Math.max(0, textLayout.height() - (contentBottom - contentTop)));
			if (!narrow)
				graphics.text(font, "预览", previewX, y + 128, INK, false);
			graphics.enableScissor(previewX, contentTop, previewX + previewWidth, contentBottom);
			textLayout.render(graphics, font, previewX + 6, contentTop, contentBottom, scroll);
			graphics.disableScissor();
		}
		graphics.text(font, font.plainSubstrByWidth(status, panelWidth - 32), x + 16, y + panelHeight - 42, 0xFF8C4B30,
		              false);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if ((!narrow || tab == 2) && mouseX >= previewX && mouseX < previewX + previewWidth && mouseY >= contentTop &&
		    mouseY < contentBottom) {
			scroll = Math.max(0, scroll - (int)(vertical * 36));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
