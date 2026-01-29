package com.customemoji.renderer;

import com.customemoji.ChatScrollingManager;
import com.customemoji.CustomEmojiConfig;
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
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.event.MouseEvent;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

@Slf4j
@Singleton
public class NewMessageBannerRenderer extends Overlay
{
	// Banner mode constants
	private static final int BAR_HEIGHT = 16;
	private static final Color BAR_COLOR = new Color(0, 0, 0, 128);
	private static final Color BAR_HOVER_COLOR = new Color(0, 0, 0, 180);
	private static final Color TEXT_COLOR = Color.WHITE;
	private static final String MESSAGE_TEXT = "New Messages Below";

	// Arrow mode constants
	private static final int CIRCLE_DIAMETER = 21;
	private static final int CIRCLE_MARGIN = 4;
	private static final Color CIRCLE_COLOR = new Color(0, 0, 0, 160);
	private static final Color CIRCLE_HOVER_COLOR = new Color(0, 0, 0, 200);
	private static final Color ARROW_COLOR = Color.WHITE;

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final CustomEmojiConfig config;
	private final MouseManager mouseManager;
	private final ChatScrollingManager chatScrollingManager;

	private boolean hasNewMessageWhileScrolledUp = false;
	private Rectangle indicatorBounds = null;
	private volatile boolean chatboxIsClickThrough = false;
	private volatile boolean chatboxIsTransparent = false;
	private boolean consumeNextClick = false;

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (isMouseOverIndicator())
			{
				consumeNextClick = true;
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event)
		{
			if (consumeNextClick)
			{
				consumeNextClick = false;
				scrollToBottom();
				event.consume();
			}
			return event;
		}
	};

	@Inject
	public NewMessageBannerRenderer(Client client, ClientThread clientThread, EventBus eventBus, CustomEmojiConfig config, MouseManager mouseManager, ChatScrollingManager chatScrollingManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.config = config;
		this.mouseManager = mouseManager;
		this.chatScrollingManager = chatScrollingManager;

		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.MANUAL);
		this.setPriority(0.6f);
		int interfaceID = WidgetUtil.componentToInterface(InterfaceID.Chatbox.CHATDISPLAY);
		this.drawAfterInterface(interfaceID);
	}

	public void startUp()
	{
		this.eventBus.register(this);
		this.mouseManager.registerMouseListener(this.mouseListener);
		this.clientThread.invokeLater(() ->
		{
			this.chatboxIsClickThrough = this.client.getVarbitValue(VarbitID.TRANSPARENT_CHATBOX_BLOCKCLICK) == 0;
			this.chatboxIsTransparent = this.client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
		});
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.mouseManager.unregisterMouseListener(this.mouseListener);
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
		CustomEmojiConfig.NewMessageIndicatorMode mode = this.config.newMessageIndicatorMode();

		if (mode == CustomEmojiConfig.NewMessageIndicatorMode.OFF)
		{
			return null;
		}

		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);

		if (chatbox == null || chatbox.isHidden())
		{
			this.indicatorBounds = null;
			return null;
		}

		if (this.chatScrollingManager.getScrolledUpPixels() <= 0)
		{
			this.indicatorBounds = null;
			return null;
		}

		Rectangle bounds = chatbox.getBounds();
		if (bounds == null)
		{
			this.indicatorBounds = null;
			return null;
		}

		boolean shouldShow = this.hasNewMessageWhileScrolledUp && this.chatScrollingManager.getScrolledUpPixels() > 0;

		if (!shouldShow)
		{
			this.indicatorBounds = null;
			return null;
		}

		this.setPreferredLocation(new Point(bounds.x, bounds.y));

		Shape originalClip = graphics.getClip();
		graphics.setClip(new Rectangle(0, 0, bounds.width, bounds.height));

		boolean isClickable = !(this.chatboxIsClickThrough && this.chatboxIsTransparent);

		Dimension result;
		if (mode == CustomEmojiConfig.NewMessageIndicatorMode.ARROW)
		{
			result = this.renderArrowIndicator(graphics, bounds, isClickable);
		}
		else
		{
			result = this.renderBannerIndicator(graphics, bounds, isClickable);
		}

		graphics.setClip(originalClip);
		return result;
	}

	private Dimension renderBannerIndicator(Graphics2D graphics, Rectangle bounds, boolean isClickable)
	{
		int drawX = 0;
		int drawY = bounds.height - BAR_HEIGHT;

		this.indicatorBounds = new Rectangle(bounds.x, bounds.y + drawY, bounds.width, BAR_HEIGHT);

		boolean isHovering = this.handleMouseInput(isClickable);

		Rectangle drawRect = new Rectangle(drawX, drawY, bounds.width, BAR_HEIGHT);
		Color barColor = isHovering ? BAR_HOVER_COLOR : BAR_COLOR;
		graphics.setColor(barColor);
		graphics.fill(drawRect);

		graphics.setColor(TEXT_COLOR);
		Font font = FontManager.getRunescapeSmallFont();

		if (font == null)
		{
			return null;
		}

		graphics.setFont(font);

		FontMetrics fontMetrics = graphics.getFontMetrics();
		int textWidth = fontMetrics.stringWidth(MESSAGE_TEXT);

		int textX = drawX + (bounds.width - textWidth) / 2;
		int textY = drawY + (BAR_HEIGHT + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;

		graphics.drawString(MESSAGE_TEXT, textX, textY);

		return new Dimension(bounds.width, BAR_HEIGHT);
	}

	private Dimension renderArrowIndicator(Graphics2D graphics, Rectangle bounds, boolean isClickable)
	{
		int circleX = bounds.width - CIRCLE_DIAMETER - CIRCLE_MARGIN;
		int circleY = bounds.height - CIRCLE_DIAMETER - CIRCLE_MARGIN;

		this.indicatorBounds = new Rectangle(
			bounds.x + circleX,
			bounds.y + circleY,
			CIRCLE_DIAMETER,
			CIRCLE_DIAMETER
		);

		boolean isHovering = this.handleMouseInput(isClickable);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Ellipse2D circle = new Ellipse2D.Double(circleX, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
		Color circleColor = isHovering ? CIRCLE_HOVER_COLOR : CIRCLE_COLOR;
		graphics.setColor(circleColor);
		graphics.fill(circle);

		// Draw down arrow
		graphics.setColor(ARROW_COLOR);
		graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		double centerX = circleX + CIRCLE_DIAMETER / 2.0;
		double centerY = circleY + CIRCLE_DIAMETER / 2.0;
		int arrowSize = 5;

		// Vertical line
		double lineTop = centerY - arrowSize;
		double lineBottom = centerY + arrowSize - 1;
		java.awt.geom.Line2D verticalLine = new java.awt.geom.Line2D.Double(centerX, lineTop, centerX, lineBottom);
		graphics.draw(verticalLine);

		// Arrow head
		java.awt.geom.Line2D leftHead = new java.awt.geom.Line2D.Double(centerX - arrowSize, centerY, centerX, lineBottom);
		java.awt.geom.Line2D rightHead = new java.awt.geom.Line2D.Double(centerX + arrowSize, centerY, centerX, lineBottom);
		graphics.draw(leftHead);
		graphics.draw(rightHead);

		return new Dimension(CIRCLE_DIAMETER, CIRCLE_DIAMETER);
	}

	private boolean handleMouseInput(boolean isClickable)
	{
		if (this.indicatorBounds == null || !isClickable)
		{
			return false;
		}

		net.runelite.api.Point mousePos = this.client.getMouseCanvasPosition();
		boolean isOverIndicator = this.indicatorBounds.contains(mousePos.getX(), mousePos.getY());

		Cursor cursor = isOverIndicator ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
		this.client.getCanvas().setCursor(cursor);

		return isOverIndicator;
	}

	public void onNewMessage()
	{
		log.info("onNewMessage called, scrolledUpPixels={}", this.chatScrollingManager.getScrolledUpPixels());
		//this.chatScrollingManager.captureScrollPosition();
		if (this.chatScrollingManager.getScrolledUpPixels() > 0)
		{
			this.hasNewMessageWhileScrolledUp = true;
		}
	}

	public void onScrollPositionChanged()
	{
		if (this.chatScrollingManager.getScrolledUpPixels() == 0)
		{
			this.resetIndicator();
		}
	}

	public void resetIndicator()
	{
		this.hasNewMessageWhileScrolledUp = false;
		this.indicatorBounds = null;

		this.client.getCanvas().setCursor(Cursor.getDefaultCursor());
	}

	private void scrollToBottom()
	{
		this.chatScrollingManager.scrollToBottom();
		this.resetIndicator();
	}

	private boolean isMouseOverIndicator()
	{
		if (this.indicatorBounds == null)
		{
			return false;
		}

		boolean isClickable = !(this.chatboxIsClickThrough && this.chatboxIsTransparent);
		if (!isClickable)
		{
			return false;
		}

		net.runelite.api.Point mousePos = this.client.getMouseCanvasPosition();
		return this.indicatorBounds.contains(mousePos.getX(), mousePos.getY());
	}
}
