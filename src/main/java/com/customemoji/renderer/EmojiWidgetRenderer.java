package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.model.Emoji;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public abstract class EmojiWidgetRenderer extends EmojiRendererBase
{
	protected final int widgetId;
	protected Consumer<Set<Integer>> unloadStaleCallback;

	protected EmojiWidgetRenderer(Client client, CustomEmojiConfig config, int widgetId)
	{
		super(client, config);
		this.widgetId = widgetId;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.MANUAL);
		this.setPriority(0.9f);
		int interfaceID = WidgetUtil.componentToInterface(this.widgetId);
		this.drawAfterInterface(interfaceID);
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

		Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(this.emojisSupplier);

		Set<Integer> visibleEmojiIds = new HashSet<>();
		Shape originalClip = graphics.getClip();

		graphics.setClip(bounds);

		for (Widget widget : visibleChildren)
		{
			this.processWidget(widget, graphics, visibleEmojiIds, emojiLookup);
		}

		graphics.setClip(originalClip);

		this.cleanupStaleEmojis(visibleEmojiIds);
		if (this.unloadStaleCallback != null)
		{
			this.unloadStaleCallback.accept(visibleEmojiIds);
		}

		this.onRenderComplete();

		return null;
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

		List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
			widget,
			text,
			imageId -> PluginUtils.getEmojiDimension(this.client.getModIcons(), imageId)
		);

		PluginUtils.linkZeroWidthEmojisToTarget(positions, emojiLookup);

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
}
