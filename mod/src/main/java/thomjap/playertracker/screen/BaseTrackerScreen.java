package thomjap.playertracker.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Common base for the mod's screens: does not pause the game and provides a
 * background dim.
 */
abstract class BaseTrackerScreen extends Screen {

	protected BaseTrackerScreen(Text title) {
		super(title);
	}

	/**
	 * Dims the screen. We do not use {@code renderBackground()}: its blur
	 * crashes on a non-pausing screen ("Can only blur once per frame").
	 */
	protected void dimBackground(DrawContext ctx, int argb) {
		ctx.fill(0, 0, this.width, this.height, argb);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
