package com.customemoji;

import javax.inject.Inject;

import com.customemoji.CustomEmojiPlugin.Emoji;

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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.event.MouseEvent;

public class CustomEmojiTooltip extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private CustomEmojiPlugin plugin;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ChatIconManager chatIconManager;

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
        if (chatbox == null)
        {
            this.hoveredEmojiName = null;
            return;
        }

        if (isPointInWidget(chatbox, mousePoint) == false)
        {
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
            
            // Check if mouse is within widget bounds (with expanded Y for tall emojis)
            if (isPointInWidgetWithEmojiPadding(widget, mousePoint))
            {
                String text = widget.getText();
                if (text != null && text.contains("<img="))
                {
                    String hoveredEmoji = findEmojiAtPosition(widget, text, mousePoint);
                    if (hoveredEmoji != null)
                    {
                        return hoveredEmoji;
                    }
                }
            }
        }
        return null;
    }

    private boolean isPointInWidgetWithEmojiPadding(Widget widget, Point point)
    {
        int x = widget.getCanvasLocation().getX();
        int y = widget.getCanvasLocation().getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        // Emojis can extend above and below the widget's 14px height
        // Add padding to account for taller emojis (up to ~32px tall emojis)
        int verticalPadding = config.chatMessageSpacing() + config.chatMessageSpacing();

        return point.x >= x && point.x <= x + width &&
               point.y >= y - verticalPadding && point.y <= y + height + verticalPadding;
    }

    private boolean isPointInWidget(Widget widget, Point point)
    {
        int x = widget.getCanvasLocation().getX();
        int y = widget.getCanvasLocation().getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        
        return point.x >= x && point.x <= x + width && 
               point.y >= y && point.y <= y + height;
    }

    private String findEmojiAtPosition(Widget widget, String text, Point mousePoint)
    {
        // Create a pattern to find all <img=ID> tags
        Pattern pattern = Pattern.compile("<img=(\\d+)>");
        Matcher matcher = pattern.matcher(text);
        
        // Get widget position and font metrics
        net.runelite.api.Point widgetPos = widget.getCanvasLocation();
        FontMetrics fm = this.client.getCanvas().getFontMetrics(this.client.getCanvas().getFont());

        // Calculate relative mouse position within the widget
        int relativeX = mousePoint.x - widgetPos.getX();
        int relativeY = mousePoint.y - widgetPos.getY();

        // Widget baseline - emojis are typically bottom-aligned with text
        int widgetHeight = widget.getHeight();

        // Parse text and find emoji positions
        int textIndex = 0;
        int currentX = 0;
        
        while (matcher.find())
        {
            // Calculate text before this emoji
            String textBefore = text.substring(textIndex, matcher.start());
            // Remove any HTML tags from text before for width calculation
            String cleanTextBefore = this.removeHtmlTags(textBefore);

            // Calculate X position of this emoji
            int textBeforeWidth = fm.stringWidth(cleanTextBefore);
            int emojiStartX = currentX + textBeforeWidth;

            // Look up the emoji to get its actual dimensions
            String imageIdStr = matcher.group(1);
            int imageId = Integer.parseInt(imageIdStr);
            String emojiName = this.findEmojiNameById(imageId);

            // Use actual emoji dimensions if available, otherwise use defaults
            int emojiWidth = 18; // Default width estimate
            int emojiHeight = 18; // Default height estimate
            if (emojiName != null)
            {
                Emoji emoji = this.plugin.emojis.get(emojiName);
                if (emoji != null && emoji.getDimension() != null)
                {
                    emojiWidth = emoji.getDimension().width;
                    emojiHeight = emoji.getDimension().height;
                }
            }

            int emojiEndX = emojiStartX + emojiWidth;
            
            // Check if mouse is within X bounds of this emoji
            boolean withinXBounds = relativeX >= emojiStartX && relativeX <= emojiEndX;

            // Calculate Y bounds - emoji extends above the widget baseline
            // Emojis are bottom-aligned, so they extend upward from the widget
            int emojiTopY = widgetHeight - emojiHeight;
            int emojiBottomY = widgetHeight;
            boolean withinYBounds = relativeY >= emojiTopY && relativeY <= emojiBottomY;

            if (withinXBounds && withinYBounds)
            {
                return emojiName;
            }
            
            // Update position for next iteration
            currentX = emojiEndX;
            textIndex = matcher.end();
        }
        
        return null;
    }

    private String removeHtmlTags(String text)
    {
        if (text == null)
        {
            return "";
        }
        // Remove HTML tags but preserve the text content
        return text.replaceAll("<[^>]*>", "");
    }

    private String findEmojiNameById(int imageId)
    {
        for (CustomEmojiPlugin.Emoji emoji : plugin.emojis.values())
        {
            if (chatIconManager.chatIconIndex(emoji.getId()) == imageId)
            {
                return emoji.getText();
            }
        }
        return null;
    }
}
