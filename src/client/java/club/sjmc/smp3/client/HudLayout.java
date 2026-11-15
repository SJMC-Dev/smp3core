package club.sjmc.smp3.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

final class HudLayout {
	static final int PANEL = 0x88000000, TEXT = 0xFFF3EEE0, MUTED = 0xFFAAAAAA;
	static final int GREEN = 0xFF55FF55, RED = 0xFFFF5555;
	Placement currency = new Placement(.5F, 1);
	Placement quests = new Placement(1, 0);
	boolean currencyVisible = true, noticesVisible = true;

	static final class Placement {
		float x, y, scale = .8F;
		Placement(float x, float y) {
			this.x = x;
			this.y = y;
		}

		Box box(int width, int height, int contentWidth, int contentHeight, int bottom) {
			float size = Math.min(Math.clamp(scale, .6F, 1.4F), (width - 16F) / contentWidth);
			int w = Math.round(contentWidth * size), h = Math.round(contentHeight * size);
			return new Box(8 + Math.round(Math.max(0, width - 16 - w) * Math.clamp(x, 0, 1)),
			               8 + Math.round(Math.max(0, height - 16 - bottom - h) * Math.clamp(y, 0, 1)), w, h, size);
		}

		void move(double left, double top, int width, int height, Box box, int bottom) {
			x = (float)Math.clamp((left - 8) / Math.max(1, width - 16 - box.width), 0, 1);
			y = (float)Math.clamp((top - 8) / Math.max(1, height - 16 - bottom - box.height), 0, 1);
		}
	}

	record Box(int x, int y, int width, int height, float scale) {
		boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
	}

	static int statusSpace() {
		var player = Minecraft.getInstance().player;
		if (player == null)
			return 42;
		int rows = (int)Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 20);
		int extra = Math.max(0, rows - 1) * Math.max(3, 12 - rows);
		if (player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater())
			extra += 10;
		if (player.getVehicle() instanceof LivingEntity mount)
			extra = Math.max(extra, Math.min(3, (int)Math.ceil(mount.getMaxHealth() / 20)) * 10);
		return 42 + extra;
	}
}
