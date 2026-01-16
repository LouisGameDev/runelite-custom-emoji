package com.customemoji;

import com.customemoji.animation.GifAnimation;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
public class OverheadEmojiRenderer extends Overlay
{
	private static final long LOAD_DEBOUNCE_MS = 100;

	private final Client client;
	private final CustomEmojiConfig config;

	private final Map<Integer, Long> emojiFirstSeenTime = new HashMap<>();

	private Supplier<Map<String, Emoji>> emojisSupplier;
	private Function<AnimatedEmoji, GifAnimation> animationLoader;
	private Consumer<Integer> markVisibleCallback;

	@Inject
	public OverheadEmojiRenderer(Client client, CustomEmojiConfig config)
	{
		this.client = client;
		this.config = config;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setEmojisSupplier(Supplier<Map<String, Emoji>> supplier)
	{
		this.emojisSupplier = supplier;
	}

	public void setAnimationLoader(Function<AnimatedEmoji, GifAnimation> loader)
	{
		this.animationLoader = loader;
	}

	public void setMarkVisibleCallback(Consumer<Integer> callback)
	{
		this.markVisibleCallback = callback;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (this.emojisSupplier == null)
		{
			return null;
		}

		if (this.client.getTopLevelWorldView() == null)
		{
			return null;
		}

		List<Player> players = this.client.getTopLevelWorldView().players().stream().collect(Collectors.toList());
		if (players.isEmpty())
		{
			return null;
		}

		Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(this.emojisSupplier);
		Set<Integer> visibleEmojiIds = new HashSet<>();

		for (Player player : players)
		{
			this.renderPlayerOverhead(graphics, player, visibleEmojiIds, emojiLookup);
		}

		this.emojiFirstSeenTime.keySet().retainAll(visibleEmojiIds);

		return null;
	}

	private void renderPlayerOverhead(Graphics2D graphics, Player player, Set<Integer> visibleEmojiIds, Map<Integer, Emoji> emojiLookup)
	{
		if (player == null)
		{
			return;
		}

		String overheadText = player.getOverheadText();
		if (overheadText == null || overheadText.isEmpty())
		{
			return;
		}

		Point centerPoint = player.getCanvasTextLocation(graphics, "", player.getLogicalHeight());
		if (centerPoint == null)
		{
			return;
		}

		int centerX = centerPoint.getX();
		int baseY = centerPoint.getY();

		IndexedSprite[] modIcons = this.client.getModIcons();
		EmojiPositionCalculator.DimensionLookup dimensionLookup = imageId -> PluginUtils.getEmojiDimension(modIcons, imageId);

		List<EmojiPosition> positions = EmojiPositionCalculator.calculateOverheadEmojiPositions(graphics, overheadText, centerX, baseY, dimensionLookup);

		PluginUtils.linkZeroWidthEmojisToTarget(positions, emojiLookup);

		for (EmojiPosition position : positions)
		{
			Emoji emoji = emojiLookup.get(position.getImageId());
			if (emoji != null)
			{
				this.renderEmoji(graphics, emoji, position, visibleEmojiIds);
			}
		}
	}

	private void renderEmoji(Graphics2D graphics, Emoji emoji, EmojiPosition position, Set<Integer> visibleEmojiIds)
	{
		Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
		boolean isDisabled = disabledEmojis.contains(emoji.getText());
		if (isDisabled)
		{
			return;
		}

		int emojiId = emoji.getId();
		visibleEmojiIds.add(emojiId);

		BufferedImage image = emoji.getStaticImage();

		boolean isAnimatedEmoji = emoji instanceof AnimatedEmoji;
		if (isAnimatedEmoji)
		{
			AnimatedEmoji animatedEmoji = (AnimatedEmoji) emoji;

			long currentTime = System.currentTimeMillis();
			long firstSeenTime = this.emojiFirstSeenTime.computeIfAbsent(emojiId, k -> currentTime);
			long visibleDuration = currentTime - firstSeenTime;
			boolean hasPassedDebounce = visibleDuration >= LOAD_DEBOUNCE_MS;

			boolean shouldLoadAnimation = this.config.enableAnimatedEmojis() && hasPassedDebounce && this.animationLoader != null;
			if (shouldLoadAnimation)
			{
				if (this.markVisibleCallback != null)
				{
					this.markVisibleCallback.accept(emojiId);
				}
				GifAnimation animation = this.animationLoader.apply(animatedEmoji);

				if (animation != null)
				{
					BufferedImage currentFrame = animation.getCurrentFrame();
					if (currentFrame != null)
					{
						image = currentFrame;
					}
				}
			}
		}

		Rectangle bounds = position.getBounds();
		int drawX = bounds.x;
		int drawY = bounds.y;
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
}
