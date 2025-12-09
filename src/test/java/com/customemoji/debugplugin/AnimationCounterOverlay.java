package com.customemoji.debugplugin;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

@Singleton
public class AnimationCounterOverlay extends Overlay
{
	private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
	private static final String ANIMATION_COUNT_MESSAGE = "animation-count";
	private static final String ANIMATION_COUNT_KEY = "count";

	private final Client client;
	private final CustomEmojiDebugConfig config;
	private final EventBus eventBus;

	private int animatedCount = 0;

	@Inject
	public AnimationCounterOverlay(Client client, CustomEmojiDebugConfig config, EventBus eventBus)
	{
		this.client = client;
		this.config = config;
		this.eventBus = eventBus;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void startUp()
	{
		this.eventBus.register(this);
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.animatedCount = 0;
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		boolean isOurNamespace = PLUGIN_MESSAGE_NAMESPACE.equals(event.getNamespace());
		boolean isCountMessage = ANIMATION_COUNT_MESSAGE.equals(event.getName());

		if (!isOurNamespace || !isCountMessage)
		{
			return;
		}

		Object countValue = event.getData().get(ANIMATION_COUNT_KEY);
		if (countValue instanceof Integer)
		{
			this.animatedCount = (Integer) countValue;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!this.config.showAnimationCounter())
		{
			return null;
		}

		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chatbox == null)
		{
			return null;
		}

		Rectangle chatboxBounds = chatbox.getBounds();
		if (chatboxBounds == null)
		{
			return null;
		}

		this.renderCounter(graphics, chatboxBounds);

		return null;
	}

	private void renderCounter(Graphics2D graphics, Rectangle chatboxBounds)
	{
		String text = "Animated: " + this.animatedCount;
		int x = chatboxBounds.x;
		int y = chatboxBounds.y - 5;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(Color.YELLOW);
		graphics.drawString(text, x, y);
	}
}
