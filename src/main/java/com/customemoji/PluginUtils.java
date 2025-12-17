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
