package com.customemoji.features.animation;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.module.PluginLifecycleComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Manages animated emoji overlay registration and config toggle handling.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class AnimationComponent implements PluginLifecycleComponent
{
	private final OverlayManager overlayManager;
	private final CustomEmojiConfig config;
	private final AnimatedEmojiOverlay animatedEmojiOverlay;
	private final OverheadAnimatedEmojiOverlay overheadAnimatedEmojiOverlay;

	@Override
	public void startUp()
	{
		if (this.config.enableAnimatedEmojis())
		{
			this.overlayManager.add(this.animatedEmojiOverlay);
			this.overlayManager.add(this.overheadAnimatedEmojiOverlay);
		}
	}

	@Override
	public void shutDown()
	{
		this.overlayManager.remove(this.animatedEmojiOverlay);
		this.overlayManager.remove(this.overheadAnimatedEmojiOverlay);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		boolean isCustomEmojiConfig = CustomEmojiConfig.KEY_CONFIG_GROUP.equals(event.getGroup());
		if (!isCustomEmojiConfig)
		{
			return;
		}

		boolean isAnimatedToggle = CustomEmojiConfig.KEY_ENABLE_ANIMATED_EMOJIS.equals(event.getKey());
		if (isAnimatedToggle)
		{
			this.handleAnimatedEmojisToggle();
		}
	}

	private void handleAnimatedEmojisToggle()
	{
		if (this.config.enableAnimatedEmojis())
		{
			this.overlayManager.add(this.animatedEmojiOverlay);
			this.overlayManager.add(this.overheadAnimatedEmojiOverlay);
		}
		else
		{
			this.overlayManager.remove(this.animatedEmojiOverlay);
			this.overlayManager.remove(this.overheadAnimatedEmojiOverlay);
		}
	}
}
