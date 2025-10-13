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
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            hoveredEmojiName = null;
            return;
        }

        Widget[] dynamicChildren = chatbox.getDynamicChildren();
        foundEmoji = checkWidgetsForEmoji(dynamicChildren, mousePoint);
        
        hoveredEmojiName = foundEmoji;
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

            // Check if mouse is within widget bounds
            if (isPointInWidget(widget, mousePoint))
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
        FontMetrics fm = client.getCanvas().getFontMetrics(client.getCanvas().getFont());
        
        // Calculate relative mouse position within the widget
        int relativeX = mousePoint.x - widgetPos.getX();
        int relativeY = mousePoint.y - widgetPos.getY();
        
        // Simple approach: check if we're on the first line or not
        int lineHeight = 14; // Each line is exactly 14px tall
        int currentLine = relativeY / lineHeight;
        
        // Parse text and find emoji positions
        int textIndex = 0;
        int currentX = 0;
        int currentEmojiLine = 0;
        
        while (matcher.find())
        {
            // Calculate text before this emoji
            String textBefore = text.substring(textIndex, matcher.start());
            // Remove any HTML tags from text before for width calculation
            String cleanTextBefore = removeHtmlTags(textBefore);
            
            // Simulate line wrapping for the text before this emoji
            for (char c : cleanTextBefore.toCharArray())
            {
                int charWidth = fm.charWidth(c);
                
                // Check if this character would wrap to next line
                if (currentX + charWidth > widget.getWidth() && currentX > 0)
                {
                    currentX = 0; // Reset to start of new line
                    currentEmojiLine++; // Move to next line
                }
                
                currentX += charWidth;
            }
            
            // Look up the emoji to get its actual dimensions
            String imageIdStr = matcher.group(1);
            int imageId = Integer.parseInt(imageIdStr);
            String emojiName = findEmojiNameById(imageId);
            
            // Use actual emoji dimensions if available, otherwise use defaults
            int emojiWidth = 18; // Default width estimate
            if (emojiName != null)
            {
                Emoji emoji = plugin.emojis.get(emojiName);
                if (emoji != null && emoji.getDimension() != null)
                {
                    emojiWidth = emoji.getDimension().width;
                }
            }
            
            // Check if emoji itself would wrap to next line
            if (currentX + emojiWidth > widget.getWidth() && currentX > 0)
            {
                currentX = 0; // Reset to start of new line
                currentEmojiLine++; // Move to next line
            }
            
            int emojiStartX = currentX;
            int emojiEndX = emojiStartX + emojiWidth;
            
            // Check if mouse is within X bounds AND on the correct line
            if (currentLine == currentEmojiLine && 
                relativeX >= emojiStartX && relativeX <= emojiEndX)
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
