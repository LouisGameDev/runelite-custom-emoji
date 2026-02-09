package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.EmojiPosition;
import com.customemoji.PluginUtils;
import com.customemoji.animation.AnimationManager;
import com.customemoji.animation.GifAnimation;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import com.customemoji.model.Lifecycle;

import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class EmojiRendererBase extends Overlay implements Lifecycle
{
	protected static final int MAX_RENDERED_ANIMATIONS = 300;
	protected static final long LOAD_DEBOUNCE_MS = 150;

	@Inject
	protected AnimationManager animationManager;

	@Inject
	private OverlayManager overlayManager;

	protected final Client client;
	protected final CustomEmojiConfig config;

	protected final Map<Integer, Long> emojiFirstSeenTime = new HashMap<>();

	protected Supplier<Map<String, Emoji>> emojisSupplier;
	protected Function<AnimatedEmoji, GifAnimation> animationLoader;
	protected Consumer<Integer> markVisibleCallback;

	private Map<Integer, Emoji> cachedEmojiLookup = null;
	private int cachedEmojiCount = -1;

	private Set<String> cachedDisabledEmojis = null;
	private String cachedDisabledEmojisConfig = null;

	protected EmojiRendererBase(Client client, CustomEmojiConfig config, EventBus eventBus)
	{
		this.client = client;
		this.config = config;
		this.setPosition(OverlayPosition.DYNAMIC);

		eventBus.register(this);
	}

	@Override
	public void startUp()
	{
		this.animationLoader = this.animationManager::getOrLoadAnimation;
		this.markVisibleCallback = this.animationManager::markAnimationVisible;
		this.overlayManager.add(this);
	}

	@Override
	public void shutDown()
	{
		this.overlayManager.remove(this);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	@Subscribe
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		Map<String, Emoji> emojis = event.getEmojis();
		this.emojisSupplier = () -> emojis;
		this.resetCache();
	}

	public void resetCache()
	{
		this.cachedEmojiLookup = null;
		this.cachedEmojiCount = -1;
	}

	protected BufferedImage resolveEmojiImage(Emoji emoji, int emojiId, Set<Integer> visibleEmojiIds)
	{
		visibleEmojiIds.add(emojiId);

		boolean isAnimatedEmoji = emoji instanceof AnimatedEmoji;
		if (!isAnimatedEmoji)
		{
			return emoji.getStaticImage();
		}

		AnimatedEmoji animatedEmoji = (AnimatedEmoji) emoji;
		BufferedImage animatedFrame = this.tryGetAnimatedFrame(animatedEmoji, emojiId, visibleEmojiIds);
		if (animatedFrame != null)
		{
			return animatedFrame;
		}

		return emoji.getStaticImage();
	}

	protected BufferedImage tryGetAnimatedFrame(AnimatedEmoji animatedEmoji, int emojiId, Set<Integer> visibleEmojiIds)
	{
		boolean animationsEnabled = this.config.animationLoadingMode() != CustomEmojiConfig.AnimationLoadingMode.OFF;
		boolean hasAnimationLoader = this.animationLoader != null;
		boolean hasPassedDebounce = this.hasPassedLoadDebounce(emojiId);
		boolean capacityExceeded = visibleEmojiIds.size() > MAX_RENDERED_ANIMATIONS;
		boolean shouldLoadAnimation = animationsEnabled && hasAnimationLoader && hasPassedDebounce && !capacityExceeded;
		if (!shouldLoadAnimation)
		{
			return null;
		}

		if (this.markVisibleCallback != null)
		{
			this.markVisibleCallback.accept(emojiId);
		}

		GifAnimation animation = this.animationLoader.apply(animatedEmoji);
		if (animation == null)
		{
			return null;
		}

		return animation.getCurrentFrame();
	}

	protected boolean hasPassedLoadDebounce(int emojiId)
	{
		long currentTime = System.currentTimeMillis();
		long firstSeenTime = this.emojiFirstSeenTime.computeIfAbsent(emojiId, k -> currentTime);
		long visibleDuration = currentTime - firstSeenTime;
		return visibleDuration >= LOAD_DEBOUNCE_MS;
	}

	protected void drawEmojiImage(Graphics2D graphics, BufferedImage image, EmojiPosition position)
	{
		int drawX = position.getX();
		int drawY = position.getY();
		int drawWidth = image.getWidth();
		int drawHeight = image.getHeight();

		if (position.hasBaseEmojiBounds())
		{
			Rectangle baseEmojiBounds = position.getBaseEmojiBounds();
			drawX = baseEmojiBounds.x + (baseEmojiBounds.width - drawWidth) / 2;
			drawY = baseEmojiBounds.y + (baseEmojiBounds.height - drawHeight) / 2;
		}

		graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
	}

	protected boolean isEmojiDisabled(Emoji emoji)
	{
		Set<String> disabledEmojis = this.getDisabledEmojisSet();
		return disabledEmojis.contains(emoji.getText());
	}

	protected void cleanupStaleEmojis(Set<Integer> visibleEmojiIds)
	{
		this.emojiFirstSeenTime.keySet().retainAll(visibleEmojiIds);
	}

	protected void onRenderComplete()
	{
	}

	protected Map<Integer, Emoji> getOrBuildEmojiLookup()
	{
		if (this.emojisSupplier == null)
		{
			return new HashMap<>();
		}

		Map<String, Emoji> emojis = this.emojisSupplier.get();
		int currentEmojiCount = emojis.size();

		boolean needsRebuild = this.cachedEmojiLookup == null || this.cachedEmojiCount != currentEmojiCount;
		if (needsRebuild)
		{
			this.cachedEmojiLookup = PluginUtils.buildEmojiLookup(this.emojisSupplier);
			this.cachedEmojiCount = currentEmojiCount;
		}

		return this.cachedEmojiLookup;
	}

	private Set<String> getDisabledEmojisSet()
	{
		String currentConfig = this.config.disabledEmojis();

		boolean needsRebuild = this.cachedDisabledEmojis == null
			|| !this.safeEquals(this.cachedDisabledEmojisConfig, currentConfig);

		if (needsRebuild)
		{
			this.cachedDisabledEmojis = PluginUtils.parseDisabledEmojis(currentConfig);
			this.cachedDisabledEmojisConfig = currentConfig;
		}

		return this.cachedDisabledEmojis;
	}

	private boolean safeEquals(String a, String b)
	{
		if (a == null && b == null)
		{
			return true;
		}
		if (a == null || b == null)
		{
			return false;
		}
		return a.equals(b);
	}
}
