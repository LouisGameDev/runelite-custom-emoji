package com.customemoji.animation;

import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.io.EmojiLoader;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class AnimatedEmojiOverlay extends Overlay
{
	private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
	private static final String ANIMATION_COUNT_MESSAGE = "animation-count";
	private static final String ANIMATION_COUNT_KEY = "count";
	private static final int MAX_RENDERED_ANIMATIONS = 50;

	private final Client client;
	private final EmojiLoader emojiLoader;
	private final ChatIconManager chatIconManager;
	private final EventBus eventBus;

	@Inject
	public AnimatedEmojiOverlay(Client client, EmojiLoader emojiLoader, ChatIconManager chatIconManager, EventBus eventBus)
	{
		this.client = client;
		this.emojiLoader = emojiLoader;
		this.chatIconManager = chatIconManager;
		this.eventBus = eventBus;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chatbox == null || chatbox.isHidden())
		{
			return null;
		}

		Widget[] dynamicChildren = chatbox.getDynamicChildren();
		if (dynamicChildren == null)
		{
			return null;
		}

		Rectangle visibleBounds = chatbox.getBounds();
		if (visibleBounds == null)
		{
			return null;
		}

		Shape originalClip = graphics.getClip();
		graphics.setClip(visibleBounds);

		Map<Integer, AnimatedEmoji> animatedEmojiLookup = this.buildAnimatedEmojiLookup();

		Set<Integer> visibleEmojiIds = new HashSet<>();
		int renderedCount = 0;

		for (Widget widget : dynamicChildren)
		{
			renderedCount += this.processWidget(widget, graphics, visibleEmojiIds, visibleBounds, animatedEmojiLookup);
		}

		graphics.setClip(originalClip);

		this.emojiLoader.unloadStaleAnimations(visibleEmojiIds);
		this.broadcastAnimationCount(renderedCount);

		return null;
	}

	private void broadcastAnimationCount(int count)
	{
		Map<String, Object> data = Map.of(ANIMATION_COUNT_KEY, count);
		PluginMessage message = new PluginMessage(PLUGIN_MESSAGE_NAMESPACE, ANIMATION_COUNT_MESSAGE, data);
		this.eventBus.post(message);
	}

	private int processWidget(Widget widget, Graphics2D graphics, Set<Integer> visibleEmojiIds, Rectangle visibleBounds, Map<Integer, AnimatedEmoji> animatedEmojiLookup)
	{
		if (widget == null)
		{
			return 0;
		}

		Rectangle widgetBounds = widget.getBounds();
		boolean isWidgetOffscreen = widgetBounds == null || !visibleBounds.intersects(widgetBounds);
		if (isWidgetOffscreen)
		{
			return 0;
		}

		String text = widget.getText();
		boolean hasNoEmojis = text == null || !text.contains("<img=");
		if (hasNoEmojis)
		{
			return 0;
		}

		List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
			widget,
			text,
			imageId -> PluginUtils.getEmojiDimension(this.client.getModIcons(), imageId)
		);

		int count = 0;
		for (EmojiPosition position : positions)
		{
			boolean rendered = this.renderAnimatedEmoji(position, graphics, visibleEmojiIds, visibleBounds, animatedEmojiLookup);
			if (rendered)
			{
				count++;
			}
		}
		return count;
	}

	private boolean renderAnimatedEmoji(EmojiPosition position, Graphics2D graphics, Set<Integer> visibleEmojiIds, Rectangle visibleBounds, Map<Integer, AnimatedEmoji> animatedEmojiLookup)
	{
		boolean isVisible = visibleBounds.intersects(position.getBounds());
		if (!isVisible)
		{
			return false;
		}

		int imageId = position.getImageId();
		AnimatedEmoji animatedEmoji = animatedEmojiLookup.get(imageId);
		if (animatedEmoji == null)
		{
			return false;
		}

		visibleEmojiIds.add(animatedEmoji.getId());
		this.emojiLoader.markAnimationVisible(animatedEmoji.getId());

		boolean capacityExceeded = visibleEmojiIds.size() > 999;
		GifAnimation animation = null;
		if (!capacityExceeded)
		{
			animation = this.emojiLoader.getOrLoadAnimation(animatedEmoji);
		}

		BufferedImage image = animatedEmoji.getStaticImage();
		boolean isAnimating = false;

		if (animation != null)
		{
			BufferedImage currentFrame = animation.getCurrentFrame();
			if (currentFrame != null)
			{
				image = currentFrame;
				isAnimating = true;
			}
		}

		graphics.drawImage(
			image,
			position.getX(),
			position.getY(),
			position.getWidth(),
			position.getHeight(),
			null
		);
		return isAnimating;
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
