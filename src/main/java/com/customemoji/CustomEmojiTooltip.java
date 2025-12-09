package com.customemoji;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.io.EmojiLoader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;

@Slf4j
@Singleton
public class CustomEmojiTooltip extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private EmojiLoader emojiLoader;

    // Tooltip state
    private String hoveredEmojiName = null;
    private Point mousePosition = null;

    private final MouseListener mouseListener = new MouseListener()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseEntered(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseExited(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent mouseEvent)
		{
			Point currentPoint = mouseEvent.getPoint();

			// Only update if mouse actually moved a good bit
			if (mousePosition == null ||
				Math.abs(currentPoint.x - mousePosition.x) > 2 ||
				Math.abs(currentPoint.y - mousePosition.y) > 2)
			{
				mousePosition = currentPoint;

				// Delegate to overlay for tooltip handling
				updateHoveredEmoji(currentPoint);
			}
			return mouseEvent;
		}
	};

    protected void startUp()
    {
        if (mouseManager != null)
        {
            mouseManager.registerMouseListener(mouseListener);
        }
    }

    protected void shutDown()
    {
        if (mouseManager != null)
        {
            mouseManager.unregisterMouseListener(mouseListener);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        showTooltip();
        return null;
    }

    private void showTooltip()
    {
        if (hoveredEmojiName != null && !hoveredEmojiName.isEmpty() && config.showEmojiTooltips())
        {
            tooltipManager.add(new Tooltip(hoveredEmojiName));
        }
    }

    private void updateHoveredEmoji(Point mousePoint)
    {
        this.mousePosition = mousePoint;

        String foundEmoji = null;

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || !isPointInWidget(chatbox, mousePoint))
        {
            this.hoveredEmojiName = null;
            return;
        }

        Widget[] dynamicChildren = chatbox.getDynamicChildren();

        foundEmoji = this.checkWidgetsForEmoji(dynamicChildren, mousePoint);
        this.hoveredEmojiName = foundEmoji;
    }

    private String checkWidgetsForEmoji(Widget[] widgets, Point mousePoint)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            if (widget == null)
            {
                continue;
            }

            String text = widget.getText();
            if (text != null && text.contains("<img="))
            {
                String hoveredEmoji = this.findEmojiAtPosition(widget, text, mousePoint);
                if (hoveredEmoji != null)
                {
                    return hoveredEmoji;
                }
            }
        }
        return null;
    }

    private boolean isPointInWidget(Widget widget, Point point)
    {
        net.runelite.api.Point canvasLocation = widget.getCanvasLocation();
        if (canvasLocation == null)
        {
            return false;
        }

        int x = canvasLocation.getX();
        int y = canvasLocation.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        return point.x >= x && point.x <= x + width &&
               point.y >= y && point.y <= y + height;
    }

    private String findEmojiAtPosition(Widget widget, String text, Point mousePoint)
    {
        int imageId = EmojiPositionCalculator.findEmojiAtPoint(
            widget,
            text,
            mousePoint.x,
            mousePoint.y,
            id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
        );

        if (imageId >= 0)
        {
            return PluginUtils.findEmojiNameByImageId(imageId, this.emojiLoader.getEmojis(), this.chatIconManager);
        }

        return null;
    }
}
