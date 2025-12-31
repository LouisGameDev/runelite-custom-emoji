package com.customemoji;

import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import net.runelite.api.IndexedSprite;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ChatIconManager;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils
{
	public static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");

	private PluginUtils()
	{
	}

	public static Dimension getEmojiDimension(IndexedSprite[] modIcons, int imageId)
	{
		if (modIcons == null || imageId < 0 || imageId >= modIcons.length)
		{
			return null;
		}

		IndexedSprite sprite = modIcons[imageId];
		if (sprite == null)
		{
			return null;
		}

		return new Dimension(sprite.getWidth(), sprite.getHeight());
	}

	public static List<Integer> getImageIdsFromText(String text)
	{
		List<Integer> imageIds = new ArrayList<>();

		if (text == null || text.isEmpty())
		{
			return imageIds;
		}

		Matcher matcher = IMAGE_TAG_PATTERN.matcher(text);
		while (matcher.find())
		{
			String idString = matcher.group(1);
			int id = Integer.parseInt(idString);
			imageIds.add(id);
		}

		return imageIds;
	}

	public static int findMaxEmojiHeightInWidget(Widget widget, IndexedSprite[] modIcons)
	{
		if (widget == null)
		{
			return 0;
		}

		int maxHeight = 0;
		String text = widget.getText();
		List<Integer> imageIds = PluginUtils.getImageIdsFromText(text);

		for (int imageId : imageIds)
		{
			Dimension dimension = PluginUtils.getEmojiDimension(modIcons, imageId);
			if (dimension != null)
			{
				maxHeight = Math.max(maxHeight, dimension.height);
			}
		}

		return maxHeight;
	}

	/**
	 * Finds the maximum emoji height for emoji on the FIRST line only.
	 * This is used for dynamic spacing - emoji on wrapped lines are handled separately
	 * by the overflow calculation.
	 */
	public static int findMaxFirstLineEmojiHeight(Widget widget, IndexedSprite[] modIcons)
	{
		if (widget == null || modIcons == null)
		{
			return 0;
		}

		String text = widget.getText();
		if (text == null || text.isEmpty())
		{
			return 0;
		}

		int widgetWidth = widget.getWidth();
		if (widgetWidth <= 0)
		{
			return 0;
		}

		net.runelite.api.FontTypeFace font = null;
		try
		{
			font = widget.getFont();
		}
		catch (Exception e)
		{
			// Fall through to fallback
		}

		if (font != null)
		{
			return PluginUtils.findMaxFirstLineEmojiHeightWithFont(text, font, widgetWidth, modIcons);
		}

		// Fallback: if single-line widget, return max emoji height; otherwise return 0
		// (can't determine which line emoji are on without font)
		int lineHeight = 14;
		int widgetHeight = widget.getOriginalHeight();
		boolean isSingleLine = widgetHeight <= lineHeight;
		if (isSingleLine)
		{
			return PluginUtils.findMaxEmojiHeightInWidget(widget, modIcons);
		}

		return 0;
	}

	private static int findMaxFirstLineEmojiHeightWithFont(String text, net.runelite.api.FontTypeFace font,
		int widgetWidth, IndexedSprite[] modIcons)
	{
		Matcher matcher = IMAGE_TAG_PATTERN.matcher(text);

		int textIndex = 0;
		int currentX = 0;
		int currentLine = 0;
		int maxFirstLineHeight = 0;

		while (matcher.find())
		{
			String textBefore = text.substring(textIndex, matcher.start());
			String cleanTextBefore = PluginUtils.removeHtmlTags(textBefore);

			// Simulate word-based line wrapping
			String[] words = cleanTextBefore.split("(?<= )");
			for (String word : words)
			{
				int wordWidth = font.getTextWidth(word);
				if (currentX + wordWidth > widgetWidth && currentX > 0)
				{
					currentX = 0;
					currentLine++;
				}
				currentX += wordWidth;
			}

			int imageId = Integer.parseInt(matcher.group(1));

			Dimension dimension = PluginUtils.getEmojiDimension(modIcons, imageId);
			int emojiWidth = dimension != null ? dimension.width : 18;
			int emojiHeight = dimension != null ? dimension.height : 18;

			// Check if emoji itself would wrap to next line
			if (currentX + emojiWidth > widgetWidth && currentX > 0)
			{
				currentX = 0;
				currentLine++;
			}

			// Only count emoji on line 0 (first line)
			boolean isOnFirstLine = currentLine == 0;
			if (isOnFirstLine)
			{
				maxFirstLineHeight = Math.max(maxFirstLineHeight, emojiHeight);
			}

			currentX += emojiWidth;
			textIndex = matcher.end();
		}

		return maxFirstLineHeight;
	}

	/**
	 * Calculates the extra space needed below a widget due to clamped emoji on wrapped lines.
	 * When a tall emoji is on line 2+, it gets pushed down by (height - 14) to prevent upward
	 * overlap, and that amount needs to be added to the widget's effective height.
	 *
	 * @param widget The widget containing text with emoji
	 * @param modIcons The mod icons array for dimension lookup
	 * @return The extra pixels needed below the standard widget height, or 0 if none needed
	 */
	public static int calculateBottomOverflow(Widget widget, IndexedSprite[] modIcons)
	{
		if (widget == null || modIcons == null)
		{
			return 0;
		}

		String text = widget.getText();
		if (text == null || text.isEmpty())
		{
			return 0;
		}

		int lineHeight = 14;
		int widgetHeight = widget.getOriginalHeight();
		int widgetWidth = widget.getWidth();

		// If widget is single line height, emoji can't be on a wrapped line
		boolean isMultiLine = widgetHeight > lineHeight;
		if (!isMultiLine)
		{
			return 0;
		}

		if (widgetWidth <= 0)
		{
			return 0;
		}

		net.runelite.api.FontTypeFace font = null;
		try
		{
			font = widget.getFont();
		}
		catch (Exception e)
		{
			// Fall through to fallback
		}

		// If we can get the font, do precise line calculation
		if (font != null)
		{
			return PluginUtils.calculateOverflowWithFont(text, font, widgetWidth, modIcons);
		}

		// Fallback: if widget has multiple lines and contains tall emoji, assume worst case
		// (emoji on wrapped line) and return (maxEmojiHeight - lineHeight)
		return PluginUtils.calculateOverflowFallback(text, modIcons);
	}

	private static int calculateOverflowWithFont(String text, net.runelite.api.FontTypeFace font,
		int widgetWidth, IndexedSprite[] modIcons)
	{
		int lineHeight = 14;
		int verticalOffset = 2;

		Matcher matcher = IMAGE_TAG_PATTERN.matcher(text);

		int textIndex = 0;
		int currentX = 0;
		int currentLine = 0;

		// Track cumulative overflow across lines (matches EmojiPositionCalculator logic)
		int cumulativeOverflow = 0;
		int maxOverflowCurrentLine = 0;
		int previousLine = -1;

		while (matcher.find())
		{
			String textBefore = text.substring(textIndex, matcher.start());
			String cleanTextBefore = PluginUtils.removeHtmlTags(textBefore);

			// Simulate word-based line wrapping
			String[] words = cleanTextBefore.split("(?<= )");
			for (String word : words)
			{
				int wordWidth = font.getTextWidth(word);
				if (currentX + wordWidth > widgetWidth && currentX > 0)
				{
					currentX = 0;
					currentLine++;
				}
				currentX += wordWidth;
			}

			int imageId = Integer.parseInt(matcher.group(1));

			Dimension dimension = PluginUtils.getEmojiDimension(modIcons, imageId);
			int emojiWidth = dimension != null ? dimension.width : 18;
			int emojiHeight = dimension != null ? dimension.height : 18;

			// Check if emoji itself would wrap to next line
			if (currentX + emojiWidth > widgetWidth && currentX > 0)
			{
				currentX = 0;
				currentLine++;
			}

			// When moving to a new line, apply overflow from the previous line
			if (currentLine != previousLine && previousLine >= 0)
			{
				cumulativeOverflow += maxOverflowCurrentLine;
				maxOverflowCurrentLine = 0;
			}
			previousLine = currentLine;

			// Calculate overflow for emoji on wrapped lines
			if (currentLine > 0)
			{
				int lineTopY = currentLine * lineHeight + cumulativeOverflow;
				int lineBottomY = (currentLine + 1) * lineHeight + cumulativeOverflow;
				int emojiBottomY = lineBottomY - verticalOffset;
				int emojiTopY = emojiBottomY - emojiHeight;

				// After clamping
				if (emojiTopY < lineTopY)
				{
					emojiTopY = lineTopY;
				}

				int emojiActualBottom = emojiTopY + emojiHeight;
				int overflow = emojiActualBottom - lineBottomY;
				if (overflow > 0)
				{
					maxOverflowCurrentLine = Math.max(maxOverflowCurrentLine, overflow);
				}
			}

			currentX += emojiWidth;
			textIndex = matcher.end();
		}

		// Add any remaining overflow from the last line
		cumulativeOverflow += maxOverflowCurrentLine;

		return cumulativeOverflow;
	}

	private static int calculateOverflowFallback(String text, IndexedSprite[] modIcons)
	{
		int lineHeight = 14;

		// Find the tallest emoji in the widget
		int maxEmojiHeight = 0;
		List<Integer> imageIds = PluginUtils.getImageIdsFromText(text);
		for (int imageId : imageIds)
		{
			Dimension dimension = PluginUtils.getEmojiDimension(modIcons, imageId);
			if (dimension != null && dimension.height > maxEmojiHeight)
			{
				maxEmojiHeight = dimension.height;
			}
		}

		// If there's a tall emoji in a multi-line widget, assume it might be on a wrapped line
		boolean hasTallEmoji = maxEmojiHeight > lineHeight;
		if (hasTallEmoji)
		{
			return maxEmojiHeight - lineHeight;
		}

		return 0;
	}

	private static String removeHtmlTags(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replaceAll("<[^>]*>", "");
	}

	public static Set<String> parseDisabledEmojis(String disabledEmojisString)
	{
		Set<String> result = new HashSet<>();

		if (disabledEmojisString != null && !disabledEmojisString.trim().isEmpty())
		{
			String[] parts = disabledEmojisString.split(",");
			for (String part : parts)
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					result.add(trimmed);
				}
			}
		}

		return result;
	}

	public static Set<String> parseResizingDisabledEmojis(String resizingDisabledEmojisString)
	{
		Set<String> result = new HashSet<>();

		if (resizingDisabledEmojisString != null && !resizingDisabledEmojisString.trim().isEmpty())
		{
			String[] parts = resizingDisabledEmojisString.split(",");
			for (String part : parts)
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					result.add(trimmed);
				}
			}
		}

		return result;
	}

	public static Map<Integer, AnimatedEmoji> buildAnimatedEmojiLookup(
		Supplier<Map<String, Emoji>> emojisSupplier,
		ChatIconManager chatIconManager)
	{
		Map<Integer, AnimatedEmoji> lookup = new HashMap<>();

		if (emojisSupplier == null)
		{
			return lookup;
		}

		for (Emoji emoji : emojisSupplier.get().values())
		{
			boolean isAnimated = emoji instanceof AnimatedEmoji;
			if (!isAnimated)
			{
				continue;
			}

			int imageId = chatIconManager.chatIconIndex(emoji.getId());
			lookup.put(imageId, (AnimatedEmoji) emoji);
		}

		return lookup;
	}

	public static Map<Integer, Emoji> buildAllEmojiLookup(
		Supplier<Map<String, Emoji>> emojisSupplier,
		ChatIconManager chatIconManager)
	{
		Map<Integer, Emoji> lookup = new HashMap<>();

		if (emojisSupplier == null)
		{
			return lookup;
		}

		for (Emoji emoji : emojisSupplier.get().values())
		{
			int imageId = chatIconManager.chatIconIndex(emoji.getId());
			lookup.put(imageId, emoji);
		}

		return lookup;
	}

	public static boolean hasImgTag(String text)
	{
		return text != null && text.contains("<img=");
	}

	public static List<Widget> getVisibleChatWidgets(Widget chatbox)
	{
		List<Widget> result = new ArrayList<>();

		if (chatbox == null)
		{
			return result;
		}

		Widget[] dynamicChildren = chatbox.getDynamicChildren();
		if (dynamicChildren == null)
		{
			return result;
		}

		for (Widget widget : dynamicChildren)
		{
			if (widget == null || widget.isSelfHidden())
			{
				continue;
			}
			result.add(widget);
		}

		return result;
	}
}
