package thomjap.playertracker.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base commune aux écrans du mod : ne met pas le jeu en pause et fournit un
 * assombrissement de fond.
 */
abstract class BaseTrackerScreen extends Screen {

	protected BaseTrackerScreen(Text title) {
		super(title);
	}

	/**
	 * Assombrit l'écran. On n'utilise pas {@code renderBackground()} : son flou
	 * plante sur un écran non-pausant ("Can only blur once per frame").
	 */
	protected void dimBackground(DrawContext ctx, int argb) {
		ctx.fill(0, 0, this.width, this.height, argb);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
