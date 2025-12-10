package com.customemoji.features.tooltip;

import com.customemoji.module.PluginLifecycleComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Manages the emoji tooltip overlay lifecycle.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TooltipComponent implements PluginLifecycleComponent
{
	private final OverlayManager overlayManager;
	private final EmojiTooltip tooltip;

	@Override
	public void startUp()
	{
		this.tooltip.startUp();
		this.overlayManager.add(this.tooltip);
	}

	@Override
	public void shutDown()
	{
		this.tooltip.shutDown();
		this.overlayManager.remove(this.tooltip);
	}
}
