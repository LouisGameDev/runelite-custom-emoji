package com.customemoji;

import net.runelite.api.FontTypeFace;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for calculating emoji positions within chat widgets.
 * Shared between CustomEmojiTooltip and debug overlay.
 */
@Slf4j
public class EmojiPositionCalculator
{
    private static final Pattern IMG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final int LINE_HEIGHT = 14;
    private static final int VERTICAL_OFFSET = 2;
    private static final int DEFAULT_EMOJI_SIZE = 18;

    /**
     * Functional interface for looking up emoji dimensions by image ID.
     */
    @FunctionalInterface
    public interface DimensionLookup
    {
        Dimension getDimension(int imageId);
    }

    /**
     * Calculates the positions of all emojis in the given widget text.
     *
     * @param widget The widget containing the text
     * @param text The text content (with img tags)
     * @param dimensionLookup Function to look up emoji dimensions by image ID
     * @return List of EmojiPosition objects with absolute coordinates
     */
    public static List<EmojiPosition> calculateEmojiPositions(Widget widget, String text, DimensionLookup dimensionLookup)
    {
        List<EmojiPosition> positions = new ArrayList<>();

        Matcher matcher = IMG_PATTERN.matcher(text);
        Point widgetPos = widget.getCanvasLocation();
        if (widgetPos == null)
        {
            return positions;
        }

        FontTypeFace font;
        try
        {
            font = widget.getFont();
        }
        catch (Exception e)
        {
            log.error("Error getting font for widget", e);
            return positions;
        }

        if (font == null)
        {
            return positions;
        }

        int textIndex = 0;
        int currentX = 0;
        int currentLine = 0;

        while (matcher.find())
        {
            String textBefore = text.substring(textIndex, matcher.start());
            String cleanTextBefore = removeHtmlTags(textBefore);

            // Simulate word-based line wrapping (OSRS wraps at spaces, not mid-word)
            String[] words = cleanTextBefore.split("(?<= )");
            for (String word : words)
            {
                int wordWidth = font.getTextWidth(word);
                if (currentX + wordWidth > widget.getWidth() && currentX > 0)
                {
                    currentX = 0;
                    currentLine++;
                }
                currentX += wordWidth;
            }

            String imageIdStr = matcher.group(1);
            int imageId = Integer.parseInt(imageIdStr);

            // Get emoji dimensions
            int emojiWidth = DEFAULT_EMOJI_SIZE;
            int emojiHeight = DEFAULT_EMOJI_SIZE;

            Dimension dimension = dimensionLookup.getDimension(imageId);
            if (dimension != null)
            {
                emojiWidth = dimension.width;
                emojiHeight = dimension.height;
            }

            // Check if emoji itself would wrap to next line
            if (currentX + emojiWidth > widget.getWidth() && currentX > 0)
            {
                currentX = 0;
                currentLine++;
            }

            int emojiStartX = currentX;

            // Calculate Y position based on which line the emoji is on
            // Emoji is bottom-aligned within the line, but offset 2px up from the bottom
            int lineBottomY = (currentLine + 1) * LINE_HEIGHT;
            int emojiBottomY = lineBottomY - VERTICAL_OFFSET;
            int emojiTopY = emojiBottomY - emojiHeight;

            int absoluteX = widgetPos.getX() + emojiStartX;
            int absoluteY = widgetPos.getY() + emojiTopY;

            Rectangle bounds = new Rectangle(absoluteX, absoluteY, emojiWidth, emojiHeight);
            positions.add(new EmojiPosition(imageId, bounds));

            currentX = emojiStartX + emojiWidth;
            textIndex = matcher.end();
        }

        return positions;
    }

    /**
     * Finds the emoji at the given point within a widget.
     *
     * @param widget The widget containing the text
     * @param text The text content (with img tags)
     * @param pointX Absolute X coordinate to check
     * @param pointY Absolute Y coordinate to check
     * @param dimensionLookup Function to look up emoji dimensions by image ID
     * @return The image ID of the emoji at the point, or -1 if none found
     */
    public static int findEmojiAtPoint(Widget widget, String text, int pointX, int pointY, DimensionLookup dimensionLookup)
    {
        List<EmojiPosition> positions = calculateEmojiPositions(widget, text, dimensionLookup);

        for (EmojiPosition position : positions)
        {
            if (position.containsPoint(pointX, pointY))
            {
                return position.getImageId();
            }
        }

        return -1;
    }

    private static String removeHtmlTags(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replaceAll("<[^>]*>", "");
    }
}