package club.sjmc.smp3.ui;

import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.daily.QuestHudPayload;

final class QuestHudState {
	static final float DURATION = .5F;
	private static final float COMPLETION_DURATION = DURATION + 2;
	private long date;
	private float hideTime = -1;
	private boolean showing;
	final List<Row> rows = new ArrayList<>();

	static final class Row {
		QuestHudPayload.Entry entry;
		float checkTime;
		float slideTime = -1;

		Row(QuestHudPayload.Entry entry) {
			this.entry = entry;
			checkTime = entry.complete() ? COMPLETION_DURATION : 0;
		}
	}

	void accept(QuestHudPayload snapshot, boolean visible) {
		if (date != snapshot.date())
			rows.clear();
		date = snapshot.date();
		List<Row> next = new ArrayList<>();
		for (var entry : snapshot.entries()) {
			Row row = rows.stream().filter(r -> r.entry.token().equals(entry.token())).findFirst().orElse(null);
			if (row == null) {
				if (entry.claimed())
					continue;
				row = new Row(entry);
			} else {
				if (!row.entry.complete() && entry.complete())
					row.checkTime = 0;
				if (!row.entry.claimed() && entry.claimed())
					row.slideTime = 0;
				row.entry = entry;
			}
			next.add(row);
		}
		rows.clear();
		rows.addAll(next);
		if (!visible && !hiding())
			finishAnimations();
	}

	void setVisible(boolean visible) {
		float position = hideTime;
		showing = visible;
		if (visible) {
			finishAnimations();
			hideTime = rows.isEmpty() ? -1 : position >= 0 ? position : DURATION;
		} else
			hideTime = rows.isEmpty() ? DURATION : Math.max(0, position);
	}

	boolean hiding() { return !showing && hideTime >= 0 && hideTime < DURATION; }

	float slideTime(Row row) { return hiding() ? hideTime : Math.max(hideTime, row.slideTime); }

	void advance(float seconds) {
		if (showing && hideTime >= 0) {
			hideTime = Math.max(0, hideTime - seconds);
			if (hideTime == 0)
				hideTime = -1;
		}
		if (hiding()) {
			hideTime = Math.min(DURATION, hideTime + seconds);
			if (!hiding())
				finishAnimations();
			return;
		}
		for (Row row : rows) {
			float remaining = seconds;
			if (row.entry.complete()) {
				float check = Math.min(remaining, COMPLETION_DURATION - row.checkTime);
				row.checkTime += check;
				remaining -= check;
			}
			if (row.slideTime >= 0)
				row.slideTime += remaining;
		}
		rows.removeIf(row -> row.slideTime >= DURATION);
	}

	void finishAnimations() {
		if (hideTime >= 0)
			hideTime = showing ? -1 : DURATION;
		rows.removeIf(row -> row.entry.claimed());
		rows.forEach(row -> row.checkTime = row.entry.complete() ? COMPLETION_DURATION : 0);
	}

	void clear() {
		rows.clear();
		date = 0;
		hideTime = -1;
		showing = false;
	}
}
