package com.customemoji;

import net.runelite.api.FontTypeFace;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for calculating emoji positions within chat widgets.
 * Shared between CustomEmojiTooltip and debug overlay.
 */
@Slf4j
public class EmojiPositionCalculator
{
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

        Matcher matcher = PluginUtils.IMAGE_TAG_PATTERN.matcher(text);
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
            int emojiBottomY = lineBottomY;
            int emojiTopY = emojiBottomY - emojiHeight;

            // Adjust Y for tall emoji on wrapped lines to prevent overlap with previous line
            boolean isOnWrappedLine = currentLine > 0;
            boolean isTooTall = emojiHeight > LINE_HEIGHT;
            boolean isTooWide = emojiWidth > widget.getWidth();
            if (isOnWrappedLine && isTooTall && !isTooWide)
            {
                emojiTopY += emojiHeight - LINE_HEIGHT + (LINE_HEIGHT * (currentLine - 1));
            }
            else if (isOnWrappedLine && isTooWide)
            {
                //continue; // Skip rendering overly wide emojis that wrap
            }

            int absoluteX = widgetPos.getX() + emojiStartX;
            int absoluteY = widgetPos.getY() + emojiTopY;

            Rectangle bounds = new Rectangle(absoluteX, absoluteY, emojiWidth, emojiHeight);
            positions.add(new EmojiPosition(imageId, bounds, currentLine));

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

    public static List<EmojiPosition> calculateOverheadEmojiPositions(Graphics2D graphics, String text, int centerX, int baseY, DimensionLookup dimensionLookup)
    {
        List<EmojiPosition> positions = new ArrayList<>();

        Font font = FontManager.getRunescapeBoldFont();
        FontMetrics metrics = graphics.getFontMetrics(font);

        int totalWidth = EmojiPositionCalculator.calculateOverheadTotalWidth(text, metrics, dimensionLookup);
        int xOffset = totalWidth / 2;
        int startX = centerX - xOffset;

        int currentX = startX;
        int textIndex = 0;
        Matcher matcher = PluginUtils.IMAGE_TAG_PATTERN.matcher(text);

        while (matcher.find())
        {
            String textBefore = text.substring(textIndex, matcher.start());
            String cleanText = EmojiPositionCalculator.removeHtmlTags(textBefore);
            currentX += metrics.stringWidth(cleanText);

            int imageId = Integer.parseInt(matcher.group(1));
            Dimension emojiDim = dimensionLookup.getDimension(imageId);
            int emojiWidth = emojiDim != null ? emojiDim.width : DEFAULT_EMOJI_SIZE;
            int emojiHeight = emojiDim != null ? emojiDim.height : DEFAULT_EMOJI_SIZE;

            int emojiY = baseY - emojiHeight + VERTICAL_OFFSET;
            Rectangle bounds = new Rectangle(currentX, emojiY, emojiWidth, emojiHeight);
            positions.add(new EmojiPosition(imageId, bounds, 0));

            currentX += emojiWidth;
            textIndex = matcher.end();
        }

        return positions;
    }

    private static int calculateOverheadTotalWidth(String text, FontMetrics metrics, DimensionLookup dimensionLookup)
    {
        int totalWidth = 0;
        int textIndex = 0;
        Matcher matcher = PluginUtils.IMAGE_TAG_PATTERN.matcher(text);

        while (matcher.find())
        {
            String textBefore = text.substring(textIndex, matcher.start());
            String cleanText = EmojiPositionCalculator.removeHtmlTags(textBefore);
            totalWidth += metrics.stringWidth(cleanText);

            int imageId = Integer.parseInt(matcher.group(1));
            Dimension emojiDim = dimensionLookup.getDimension(imageId);
            int emojiWidth = emojiDim != null ? emojiDim.width : DEFAULT_EMOJI_SIZE;
            totalWidth += emojiWidth;

            textIndex = matcher.end();
        }

        String remainingText = text.substring(textIndex);
        String cleanRemaining = EmojiPositionCalculator.removeHtmlTags(remainingText);
        totalWidth += metrics.stringWidth(cleanRemaining);

        return totalWidth;
    }

    private static String removeHtmlTags(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replaceAll("<[^>]*>", "");
    }

    public static class SpacingInfo
    {
        public final int aboveSpacing;
        public final int belowSpacing;

        public SpacingInfo(int aboveSpacing, int belowSpacing)
        {
            this.aboveSpacing = aboveSpacing;
            this.belowSpacing = belowSpacing;
        }
    }

    public static SpacingInfo calculateSpacingForWidget(Widget widget, DimensionLookup dimensionLookup, Set<Integer> customEmojiImageIds)
    {
        String text = widget.getText();
        if (text == null || !PluginUtils.hasImgTag(text))
        {
            return new SpacingInfo(0, 0);
        }

        List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(widget, text, dimensionLookup);
        if (positions.isEmpty())
        {
            return new SpacingInfo(0, 0);
        }

        net.runelite.api.Point widgetPos = widget.getCanvasLocation();
        if (widgetPos == null)
        {
            return new SpacingInfo(0, 0);
        }

        int widgetTop = widgetPos.getY();
        int widgetBottom = widgetTop + widget.getHeight();

        int minEmojiTop = Integer.MAX_VALUE;
        int maxEmojiBottom = Integer.MIN_VALUE;

        for (EmojiPosition position : positions)
        {
            boolean isCustomEmoji = customEmojiImageIds.contains(position.getImageId());
            if (!isCustomEmoji)
            {
                continue;
            }

            int emojiTop = position.getY();
            int emojiBottom = emojiTop + position.getHeight();

            minEmojiTop = Math.min(minEmojiTop, emojiTop);
            maxEmojiBottom = Math.max(maxEmojiBottom, emojiBottom);
        }

        if (minEmojiTop == Integer.MAX_VALUE)
        {
            return new SpacingInfo(0, 0);
        }

        int aboveSpacing = Math.max(0, widgetTop - minEmojiTop);
        int belowSpacing = Math.max(0, maxEmojiBottom - widgetBottom);

        return new SpacingInfo(aboveSpacing, belowSpacing);
    }
}