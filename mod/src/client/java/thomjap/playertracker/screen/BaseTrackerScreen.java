package thomjap.playertracker.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Common base for the mod's screens: does not pause the game and provides a
 * background dim.
 */
abstract class BaseTrackerScreen extends Screen {

	protected BaseTrackerScreen(Component title) {
		super(title);
	}

	/**
	 * Dims the screen. We do not use {@code renderBackground()}: its blur crashes
	 * on a non-pausing screen ("Can only blur once per frame").
	 */
	protected void dimBackground(GuiGraphicsExtractor g, int argb) {
		g.fill(0, 0, this.width, this.height, argb);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
