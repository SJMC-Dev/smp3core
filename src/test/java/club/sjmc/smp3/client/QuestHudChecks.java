package club.sjmc.smp3.client;

import java.util.List;

import io.netty.buffer.Unpooled;

import club.sjmc.smp3.daily.QuestHudPayload;

import net.minecraft.resources.Identifier;

public final class QuestHudChecks {
	public static void run() {
		QuestHudState state = new QuestHudState();
		var pending = entry("one", 2, false);
		var complete = entry("one", 3, false);
		var claimed = entry("one", 3, true);
		state.accept(snapshot(1, pending), true);
		state.accept(snapshot(1, complete), true);
		assert state.rows.getFirst().checkTime == 0;
		state.advance(.1F);
		state.accept(snapshot(1, complete), true);
		assert state.rows.getFirst().checkTime == .1F;
		state.advance(1);
		assert state.rows.size() == 1 && state.rows.getFirst().checkTime == QuestHudState.DURATION;

		state.accept(snapshot(1, claimed), true);
		state.accept(snapshot(1, claimed), true);
		assert state.rows.size() == 1 && state.rows.getFirst().slideTime == 0;
		state.advance(.1F);
		state.accept(snapshot(1, claimed), true);
		assert state.rows.getFirst().slideTime == .1F;
		state.advance(.11F);
		assert state.rows.isEmpty();
		state.accept(snapshot(1, claimed), true);
		assert state.rows.isEmpty();

		state.clear();
		state.accept(snapshot(1, complete), true);
		assert state.rows.getFirst().checkTime == QuestHudState.DURATION;
		state.accept(snapshot(1, claimed), true);
		state.accept(snapshot(1, entry("replacement", 0, false)), true);
		assert state.rows.getFirst().slideTime == -1;
		state.accept(snapshot(2, pending), true);
		assert state.rows.size() == 1 && state.rows.getFirst().entry.token().equals("one");
		state.accept(snapshot(2, complete), false);
		assert state.rows.getFirst().checkTime == QuestHudState.DURATION;
		state.accept(snapshot(2, claimed), false);
		assert state.rows.isEmpty();
		state.accept(snapshot(2, complete), true);
		state.accept(snapshot(2, claimed), true);
		state.finishAnimations();
		assert state.rows.isEmpty();
		state.accept(snapshot(2, pending), true);
		state.clear();
		assert state.rows.isEmpty();

		state.accept(new QuestHudPayload(3, List.of(pending, entry("two", 1, false))), true);
		state.setVisible(false);
		assert state.hiding() && state.rows.stream().allMatch(row -> state.slideTime(row) == 0);
		state.advance(.1F);
		state.accept(new QuestHudPayload(3, List.of(claimed, entry("two", 2, false))), false);
		assert state.rows.size() == 2 && state.rows.stream().allMatch(row -> state.slideTime(row) == .1F);
		state.advance(.11F);
		assert !state.hiding() && state.rows.size() == 1;
		state.setVisible(true);
		assert state.rows.getFirst().entry.token().equals("two") && state.rows.getFirst().entry.progress() == 2;
		assert state.slideTime(state.rows.getFirst()) == QuestHudState.DURATION;
		state.advance(.1F);
		assert state.slideTime(state.rows.getFirst()) == .1F;
		state.advance(.11F);
		assert state.slideTime(state.rows.getFirst()) == -1;
		state.setVisible(false);
		state.advance(.1F);
		state.accept(snapshot(4, pending), false);
		assert state.hiding() && state.slideTime(state.rows.getFirst()) == .1F;
		state.setVisible(true);
		assert !state.hiding() && state.slideTime(state.rows.getFirst()) == .1F;
		state.advance(.025F);
		float position = state.slideTime(state.rows.getFirst());
		assert position > 0 && position < .1F;
		state.setVisible(false);
		assert state.hiding() && state.slideTime(state.rows.getFirst()) == position;
		state.setVisible(true);
		state.advance(1);
		assert !state.hiding() && state.rows.size() == 1 && state.slideTime(state.rows.getFirst()) == -1;
		state.setVisible(false);
		state.advance(1);
		state.setVisible(true);
		state.finishAnimations();
		assert state.slideTime(state.rows.getFirst()) == -1;
		state.clear();
		state.setVisible(false);
		assert !state.hiding();
		state.setVisible(true);
		state.accept(snapshot(5, pending), true);
		assert state.slideTime(state.rows.getFirst()) == -1;

		var buffer = Unpooled.buffer();
		try {
			QuestHudPayload payload = new QuestHudPayload(2, List.of(pending, complete, claimed));
			QuestHudPayload.CODEC.encode(buffer, payload);
			assert QuestHudPayload.CODEC.decode(buffer).equals(payload);
		} finally {
			buffer.release();
		}
		System.out.println("任务 HUD 验证通过：快照编解码、完成保留、领取滑出、重复同步、任务替换、隐藏与断开清理。");
	}

	private static QuestHudPayload.Entry entry(String token, int progress, boolean claimed) {
		return new QuestHudPayload.Entry(token, Identifier.withDefaultNamespace("stone"), progress, 3, claimed);
	}

	private static QuestHudPayload snapshot(long date, QuestHudPayload.Entry entry) {
		return new QuestHudPayload(date, List.of(entry));
	}
}
