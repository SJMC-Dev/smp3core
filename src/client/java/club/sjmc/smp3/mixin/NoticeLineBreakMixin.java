package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import club.sjmc.smp3.ui.ServerBookScreen;
import club.sjmc.smp3.ui.screen.NoticeEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

@Mixin(targets = "net.minecraft.client.StringSplitter$LineBreakFinder")
abstract class NoticeLineBreakMixin {
	@Shadow
	private int lastSpace;
	@Shadow
	private Style lastSpaceStyle;
	@Shadow
	private int offset;
	@Unique
	private boolean smp3$notice;
	@Unique
	private int smp3$previous;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void noticeWrapping(CallbackInfo ci) {
		var screen = Minecraft.getInstance().gui.screen();
		smp3$notice = screen instanceof NoticeEditorScreen || screen instanceof ServerBookScreen;
	}

	@Inject(method = "accept", at = @At("HEAD"))
	private void chineseBoundary(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
		if (!smp3$notice)
			return;
		int previousType = Character.getType(smp3$previous);
		int type = Character.getType(codePoint);
		// Reuse vanilla's break candidate so glyph widths, styles and cursor offsets stay consistent.
		if ((Character.isIdeographic(smp3$previous) || Character.isIdeographic(codePoint)) &&
		    previousType != Character.START_PUNCTUATION && previousType != Character.INITIAL_QUOTE_PUNCTUATION &&
		    type != Character.END_PUNCTUATION && type != Character.FINAL_QUOTE_PUNCTUATION &&
		    type != Character.OTHER_PUNCTUATION) {
			lastSpace = index + offset;
			lastSpaceStyle = style;
		}
		smp3$previous = codePoint;
	}
}
