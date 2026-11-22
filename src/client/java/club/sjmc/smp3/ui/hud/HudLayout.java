package club.sjmc.smp3.ui.hud;

public final class HudLayout {
	public static final int PANEL = 0x88000000, TEXT = 0xFFF3EEE0, MUTED = 0xFFAAAAAA;
	public static final int RED = 0xFFFF5555;
	public Placement quests = new Placement(1, 0);
	public Placement notices = new Placement(1, .25F);
	public boolean followQuests = true;

	public static final class Placement {
		public float x, y, scale = .8F;

		public Placement(float x, float y) {
			this.x = x;
			this.y = y;
		}

		public Box box(int width, int height, int contentWidth, int contentHeight) {
			float size = Math.min(Math.clamp(scale, .6F, 1.4F),
			                      Math.min((width - 16F) / contentWidth, (height - 16F) / contentHeight));
			int w = Math.round(contentWidth * size), h = Math.round(contentHeight * size);
			return new Box(8 + Math.round(Math.max(0, width - 16 - w) * Math.clamp(x, 0, 1)),
			               8 + Math.round(Math.max(0, height - 16 - h) * Math.clamp(y, 0, 1)), w, h, size);
		}

		public void move(double left, double top, Box box, int width, int height) {
			x = (float)Math.clamp((left - 8) / Math.max(1, width - 16 - box.width()), 0, 1);
			y = (float)Math.clamp((top - 8) / Math.max(1, height - 16 - box.height()), 0, 1);
		}
	}

	public record Box(int x, int y, int width, int height, float scale) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
