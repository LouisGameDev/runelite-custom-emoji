package com.customemoji;

import com.customemoji.animation.GifAnimation;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
public class ChatEmojiRenderer extends Overlay
{
	private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
	private static final String ANIMATION_COUNT_MESSAGE = "animation-count";
	private static final String ANIMATION_COUNT_KEY = "count";
	private static final int MAX_RENDERED_ANIMATIONS = 300;
	private static final long LOAD_DEBOUNCE_MS = 150;

	private final Client client;
	private final ChatIconManager chatIconManager;
	private final EventBus eventBus;
	private final CustomEmojiConfig config;

	private final Map<Integer, Long> emojiFirstSeenTime = new HashMap<>();

	private Supplier<Map<String, Emoji>> emojisSupplier;
	private Function<AnimatedEmoji, GifAnimation> animationLoader;
	private Consumer<Integer> markVisibleCallback;
	private Consumer<Set<Integer>> unloadStaleCallback;

	@Inject
	public ChatEmojiRenderer(Client client, ChatIconManager chatIconManager, EventBus eventBus, CustomEmojiConfig config)
	{
		this.client = client;
		this.chatIconManager = chatIconManager;
		this.eventBus = eventBus;
		this.config = config;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_WIDGETS);
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

	public void setUnloadStaleCallback(Consumer<Set<Integer>> callback)
	{
		this.unloadStaleCallback = callback;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (this.emojisSupplier == null)
		{
			return null;
		}

		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chatbox == null || chatbox.isHidden())
		{
			return null;
		}

		List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);
		if (visibleWidgets.isEmpty())
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

		Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(this.emojisSupplier, this.chatIconManager);
		Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());

		Set<Integer> visibleEmojiIds = new HashSet<>();
		int renderedCount = 0;

		for (Widget widget : visibleWidgets)
		{
			renderedCount += this.processWidget(widget, graphics, visibleEmojiIds, visibleBounds, emojiLookup, disabledEmojis);
		}

		graphics.setClip(originalClip);

		this.emojiFirstSeenTime.keySet().retainAll(visibleEmojiIds);
		if (this.unloadStaleCallback != null)
		{
			this.unloadStaleCallback.accept(visibleEmojiIds);
		}
		this.broadcastAnimationCount(renderedCount);

		return null;
	}

	private void broadcastAnimationCount(int count)
	{
		Map<String, Object> data = Map.of(ANIMATION_COUNT_KEY, count);
		PluginMessage message = new PluginMessage(PLUGIN_MESSAGE_NAMESPACE, ANIMATION_COUNT_MESSAGE, data);
		this.eventBus.post(message);
	}

	private int processWidget(Widget widget, Graphics2D graphics, Set<Integer> visibleEmojiIds, Rectangle visibleBounds, Map<Integer, Emoji> emojiLookup, Set<String> disabledEmojis)
	{
		if (widget == null)
		{
			return 0;
		}

		String text = widget.getText();
		if (!PluginUtils.hasImgTag(text))
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
			boolean isVisible = visibleBounds.intersects(position.getBounds());
			if (!isVisible)
			{
				continue;
			}

			boolean rendered = this.renderEmoji(position, graphics, visibleEmojiIds, emojiLookup, disabledEmojis);
			if (rendered)
			{
				count++;
			}
		}
		return count;
	}

	private boolean renderEmoji(EmojiPosition position, Graphics2D graphics, Set<Integer> visibleEmojiIds, Map<Integer, Emoji> emojiLookup, Set<String> disabledEmojis)
	{
		int imageId = position.getImageId();
		Emoji emoji = emojiLookup.get(imageId);
		if (emoji == null)
		{
			return false;
		}

		boolean isDisabled = disabledEmojis.contains(emoji.getText());
		if (isDisabled)
		{
			return false;
		}

		int emojiId = emoji.getId();
		visibleEmojiIds.add(emojiId);

		BufferedImage image = emoji.getStaticImage();
		boolean isAnimating = false;

		boolean isAnimatedEmoji = emoji instanceof AnimatedEmoji;
		if (isAnimatedEmoji)
		{
			AnimatedEmoji animatedEmoji = (AnimatedEmoji) emoji;

			long currentTime = System.currentTimeMillis();
			long firstSeenTime = this.emojiFirstSeenTime.computeIfAbsent(emojiId, k -> currentTime);
			long visibleDuration = currentTime - firstSeenTime;
			boolean hasPassedDebounce = visibleDuration >= LOAD_DEBOUNCE_MS;

			boolean capacityExceeded = visibleEmojiIds.size() > MAX_RENDERED_ANIMATIONS;
			boolean shouldLoadAnimation = this.config.enableAnimatedEmojis() && !capacityExceeded && hasPassedDebounce && this.animationLoader != null;
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
						isAnimating = true;
					}
				}
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

}
