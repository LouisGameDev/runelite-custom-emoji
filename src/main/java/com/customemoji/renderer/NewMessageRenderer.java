package com.customemoji.renderer;

import com.customemoji.PluginUtils;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;

@Slf4j
@Singleton
public class NewMessageRenderer extends Overlay
{
	private static final int BAR_HEIGHT = 16;
	private static final Color BAR_COLOR = new Color(0, 0, 0, 128);
	private static final Color BAR_HOVER_COLOR = new Color(0, 0, 0, 180);
	private static final Color TEXT_COLOR = Color.WHITE;
	private static final String MESSAGE_TEXT = "New Messages Below";

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;

	private boolean hasNewMessageWhileScrolledUp = false;
	private Rectangle barBounds = null;
	private int scrolledUpPixels = 0;
	private volatile boolean chatboxIsClickThrough = false;
	private volatile boolean chatboxIsTransparent = false;
	private boolean wasMousePressed = false;

	@Inject
	public NewMessageRenderer(Client client, ClientThread clientThread, EventBus eventBus)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.MANUAL);
		this.setPriority(0.6f);
		int interfaceID = WidgetUtil.componentToInterface(InterfaceID.Chatbox.CHATDISPLAY);
		this.drawAfterInterface(interfaceID);
	}

	public void startUp()
	{
		this.eventBus.register(this);
		this.clientThread.invokeLater(() ->
		{
			this.chatboxIsClickThrough = this.client.getVarbitValue(VarbitID.TRANSPARENT_CHATBOX_BLOCKCLICK) == 0;
			this.chatboxIsTransparent = this.client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;

			this.captureScrollPosition();
		});
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.resetIndicator();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		switch (event.getVarbitId()) {
			case VarbitID.TRANSPARENT_CHATBOX_BLOCKCLICK:
				this.chatboxIsClickThrough = event.getValue() == 0;
				break;
			case VarbitID.CHATBOX_TRANSPARENCY:
				this.chatboxIsTransparent = event.getValue() == 1;
			default:
				break;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);

		if (chatbox == null || chatbox.isHidden())
		{
			this.barBounds = null;
			return null;
		}

		if (this.scrolledUpPixels <= 0)
		{
			this.barBounds = null;
			return null;
		}

		Rectangle bounds = chatbox.getBounds();
		if (bounds == null)
		{
			this.barBounds = null;
			return null;
		}

		boolean shouldShow = this.hasNewMessageWhileScrolledUp && this.scrolledUpPixels > 0;

		if (!shouldShow)
		{
			this.barBounds = null;
			return null;
		}

		this.setPreferredLocation(new Point(bounds.x, bounds.y));

		Shape originalClip = graphics.getClip();
		graphics.setClip(new Rectangle(0, 0, bounds.width, bounds.height));

		int drawX = 0;
		int drawY = bounds.height - BAR_HEIGHT;

		this.barBounds = new Rectangle(bounds.x, bounds.y + drawY, bounds.width, BAR_HEIGHT);

		// Handle mouse hover and click detection
		boolean isClickable = !(this.chatboxIsClickThrough && this.chatboxIsTransparent);
		boolean isHovering = this.handleMouseInput(isClickable);

		Rectangle drawRect = new Rectangle(drawX, drawY, bounds.width, BAR_HEIGHT);
		Color barColor = isHovering ? BAR_HOVER_COLOR : BAR_COLOR;
		graphics.setColor(barColor);
		graphics.fill(drawRect);

		graphics.setColor(TEXT_COLOR);
		Font font = FontManager.getRunescapeSmallFont();

		if (font == null)
		{
			graphics.setClip(originalClip);
			return null;
		}

		graphics.setFont(font);

		FontMetrics fontMetrics = graphics.getFontMetrics();
		int textWidth = fontMetrics.stringWidth(MESSAGE_TEXT);

		int textX = drawX + (bounds.width - textWidth) / 2;
		int textY = drawY + (BAR_HEIGHT + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;

		graphics.drawString(MESSAGE_TEXT, textX, textY);

		graphics.setClip(originalClip);

		return new Dimension(bounds.width, BAR_HEIGHT);
	}

	private boolean handleMouseInput(boolean isClickable)
	{
		if (this.barBounds == null || !isClickable)
		{
			this.wasMousePressed = false;
			return false;
		}

		net.runelite.api.Point mousePos = this.client.getMouseCanvasPosition();
		boolean isOverBar = this.barBounds.contains(mousePos.getX(), mousePos.getY());

		int currentButton = this.client.getMouseCurrentButton();
		boolean clickedThisFrame = this.wasMousePressed && currentButton == 0 && isOverBar;
		this.wasMousePressed = currentButton == 1 && isOverBar;

		if (clickedThisFrame)
		{
			this.scrollToBottom();
			return false;
		}

		Cursor cursor = isOverBar ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
		this.client.getCanvas().setCursor(cursor);

		return isOverBar;
	}

	public void onNewMessage()
	{
		log.info("onNewMessage called, scrolledUpPixels={}", this.scrolledUpPixels);
		this.captureScrollPosition();
		if (this.scrolledUpPixels > 0)
		{
			this.hasNewMessageWhileScrolledUp = true;
		}
	}

	public void onScrollPositionChanged()
	{
		this.captureScrollPosition();

		if (this.scrolledUpPixels == 0)
		{
			this.resetIndicator();
		}
	}

	public void resetIndicator()
	{
		this.hasNewMessageWhileScrolledUp = false;
		this.barBounds = null;

		this.client.getCanvas().setCursor(Cursor.getDefaultCursor());
	}

	public void scrollToBottom()
	{
		this.clientThread.invokeLater(() ->
		{
			Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);

			if (chatbox == null || chatbox.isHidden())
			{
				return;
			}

			int scrollHeight = chatbox.getScrollHeight();
			this.client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, scrollHeight);
			this.resetIndicator();
		});
	}

	public void captureScrollPosition()
	{
		int newValue = PluginUtils.getScrolledUpPixels(this.client);

		if (newValue == this.scrolledUpPixels)
		{
			return;
		}

		this.scrolledUpPixels = newValue;
	}
}
