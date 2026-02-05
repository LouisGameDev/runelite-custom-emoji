package com.customemoji.renderer;

import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.model.Emoji;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.OverlayLayer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public abstract class EmojiWidgetRenderer extends EmojiRendererBase
{
	protected int widgetId;
	protected Consumer<Set<Integer>> unloadStaleCallback;

	private final Map<PositionCacheKey, List<EmojiPosition>> positionCache = new HashMap<>();
	private final Set<Integer> visibleEmojiIds = new HashSet<>();

	private static class PositionCacheKey
	{
		private final int widgetId;
		private final String text;
		private final int x;
		private final int y;

		PositionCacheKey(int widgetId, String text, int x, int y)
		{
			this.widgetId = widgetId;
			this.text = text;
			this.x = x;
			this.y = y;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
			{
				return true;
			}
			if (obj == null || this.getClass() != obj.getClass())
			{
				return false;
			}
			PositionCacheKey other = (PositionCacheKey) obj;
			boolean result = this.widgetId == other.widgetId
				&& this.x == other.x
				&& this.y == other.y
				&& Objects.equals(this.text, other.text);
				
			return result;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(this.widgetId, this.text, this.x, this.y);
		}
	}

	@Override
	public void startUp()
	{
		super.startUp();
		this.setLayer(OverlayLayer.MANUAL);
		this.setPriority(0.9f);
		int interfaceID = WidgetUtil.componentToInterface(this.widgetId);
		this.drawAfterInterface(interfaceID);
		this.setUnloadStaleCallback(this.animationManager::unloadStaleAnimations);
	}

	@Override
	public void resetCache()
	{
		super.resetCache();
		this.positionCache.clear();
		this.visibleEmojiIds.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (this.emojis == null)
		{
			return null;
		}

		Widget targetWidget = this.client.getWidget(this.widgetId);
		if (targetWidget == null || targetWidget.isHidden())
		{
			return null;
		}

		Rectangle bounds = targetWidget.getBounds();
		if (bounds == null)
		{
			return null;
		}

		List<Widget> visibleChildren = PluginUtils.getVisibleChildWidgets(targetWidget);
		if (visibleChildren == null || visibleChildren.isEmpty())
		{
			return null;
		}

		Map<Integer, Emoji> emojiLookup = this.getOrBuildEmojiLookup();

		this.visibleEmojiIds.clear();
		Shape originalClip = graphics.getClip();

		graphics.setClip(bounds);

		for (Widget widget : visibleChildren)
		{
			this.processWidget(widget, graphics, this.visibleEmojiIds, emojiLookup);
		}

		graphics.setClip(originalClip);

		this.cleanupStaleEmojis(this.visibleEmojiIds);
		if (this.unloadStaleCallback != null)
		{
			this.unloadStaleCallback.accept(this.visibleEmojiIds);
		}

		this.onRenderComplete();

		return null;
	}

	public void setUnloadStaleCallback(Consumer<Set<Integer>> callback)
	{
		this.unloadStaleCallback = callback;
	}

	protected void processWidget(Widget widget, Graphics2D graphics, Set<Integer> visibleEmojiIds, Map<Integer, Emoji> emojiLookup)
	{
		if (widget == null)
		{
			return;
		}

		String text = widget.getText();
		if (!PluginUtils.hasImgTag(text))
		{
			return;
		}

		List<EmojiPosition> positions = this.getOrCalculatePositions(widget, text);

		for (EmojiPosition position : positions)
		{
			this.renderEmoji(position, graphics, visibleEmojiIds, emojiLookup);
		}
	}

	protected void renderEmoji(EmojiPosition position, Graphics2D graphics, Set<Integer> visibleEmojiIds, Map<Integer, Emoji> emojiLookup)
	{
		int imageId = position.getImageId();
		Emoji emoji = emojiLookup.get(imageId);
		if (emoji == null)
		{
			return;
		}

		if (this.isEmojiDisabled(emoji))
		{
			return;
		}

		int emojiId = emoji.getId();
		BufferedImage image = this.resolveEmojiImage(emoji, emojiId, visibleEmojiIds);
		this.drawEmojiImage(graphics, image, position);
	}

	private List<EmojiPosition> getOrCalculatePositions(Widget widget, String text)
	{
		net.runelite.api.Point location = widget.getCanvasLocation();
		if (location == null)
		{
			return List.of();
		}

		int widgetHash = widget.getId();
		int x = location.getX();
		int y = location.getY();

		PositionCacheKey cacheKey = new PositionCacheKey(widgetHash, text, x, y);

		List<EmojiPosition> cached = this.positionCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
			widget,
			text,
			imageId -> PluginUtils.getEmojiDimension(this.client.getModIcons(), imageId)
		);

		Map<Integer, Emoji> emojiLookup = this.getOrBuildEmojiLookup();
		PluginUtils.linkZeroWidthEmojisToTarget(positions, emojiLookup);

		this.positionCache.put(cacheKey, positions);

		return positions;
	}
}
