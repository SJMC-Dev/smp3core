package club.sjmc.smp3.daily.ui;

import club.sjmc.smp3.daily.DailyQuestService;

final class QuestMessages {
	private QuestMessages() {}

	static String message(DailyQuestService.Outcome result) {
		return switch (result.status()) {
			case STALE -> "任务已更新，请重新操作";
			case COMPLETE -> "已完成的任务不能更换";
			case LIMIT_REACHED -> "今日刷新次数已用完";
			case NO_CANDIDATE -> "没有可替换的同难度任务";
			case REROLLED -> "已更换为：" + result.taskName();
			case ALREADY_CLAIMED -> "奖励已领取";
			case INCOMPLETE -> "任务尚未完成";
			case CLAIMED ->
				"奖励已到账：" + result.reward().tongbao() + " 通宝 + " + result.reward().coffeeStamp() + " 咖啡印章";
			case DELIVERY_FAILED -> "奖励发放失败，请稍后重试";
		};
	}
}
