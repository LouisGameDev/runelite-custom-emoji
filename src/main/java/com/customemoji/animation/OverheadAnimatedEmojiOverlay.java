package com.customemoji.animation;

import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.io.EmojiLoader;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ChatIconManager;
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
import java.util.stream.Collectors;

@Singleton
public class OverheadAnimatedEmojiOverlay extends Overlay
{
	private static final long LOAD_DEBOUNCE_MS = 100;

	private final Client client;
	private final EmojiLoader emojiLoader;
	private final ChatIconManager chatIconManager;

	private final Map<Integer, Long> emojiFirstSeenTime = new HashMap<>();

	@Inject
	public OverheadAnimatedEmojiOverlay(Client client, EmojiLoader emojiLoader, ChatIconManager chatIconManager)
	{
		this.client = client;
		this.emojiLoader = emojiLoader;
		this.chatIconManager = chatIconManager;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (this.client.getTopLevelWorldView() == null)
		{
			return null;
		}

		List<Player> players = this.client.getTopLevelWorldView().players().stream().collect(Collectors.toList());
		if (players.isEmpty())
		{
			return null;
		}

		Map<Integer, AnimatedEmoji> animatedEmojiLookup = this.buildAnimatedEmojiLookup();
		Set<Integer> visibleEmojiIds = new HashSet<>();

		for (Player player : players)
		{
			this.renderPlayerOverhead(graphics, player, visibleEmojiIds, animatedEmojiLookup);
		}

		this.emojiFirstSeenTime.keySet().retainAll(visibleEmojiIds);

		return null;
	}

	private void renderPlayerOverhead(Graphics2D graphics, Player player, Set<Integer> visibleEmojiIds, Map<Integer, AnimatedEmoji> animatedEmojiLookup)
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

		for (EmojiPosition position : positions)
		{
			AnimatedEmoji animatedEmoji = animatedEmojiLookup.get(position.getImageId());
			if (animatedEmoji != null)
			{
				this.renderAnimatedEmoji(graphics, animatedEmoji, position.getBounds(), visibleEmojiIds);
			}
		}
	}

	private void renderAnimatedEmoji(Graphics2D graphics, AnimatedEmoji emoji, Rectangle bounds, Set<Integer> visibleEmojiIds)
	{
		int emojiId = emoji.getId();
		visibleEmojiIds.add(emojiId);

		long currentTime = System.currentTimeMillis();
		long firstSeenTime = this.emojiFirstSeenTime.computeIfAbsent(emojiId, k -> currentTime);
		long visibleDuration = currentTime - firstSeenTime;
		boolean hasPassedDebounce = visibleDuration >= LOAD_DEBOUNCE_MS;

		BufferedImage image = emoji.getStaticImage();

		if (hasPassedDebounce)
		{
			this.emojiLoader.markAnimationVisible(emojiId);
			GifAnimation animation = this.emojiLoader.getOrLoadAnimation(emoji);

			if (animation != null)
			{
				BufferedImage currentFrame = animation.getCurrentFrame();
				if (currentFrame != null)
				{
					image = currentFrame;
				}
			}
		}

		graphics.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
	}

	private Map<Integer, AnimatedEmoji> buildAnimatedEmojiLookup()
	{
		Map<Integer, AnimatedEmoji> lookup = new HashMap<>();

		for (Emoji emoji : this.emojiLoader.getEmojis().values())
		{
			boolean isAnimated = emoji instanceof AnimatedEmoji;
			if (!isAnimated)
			{
				continue;
			}

			int imageId = this.chatIconManager.chatIconIndex(emoji.getId());
			lookup.put(imageId, (AnimatedEmoji) emoji);
		}

		return lookup;
	}
}
