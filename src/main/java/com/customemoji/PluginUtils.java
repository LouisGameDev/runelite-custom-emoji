package com.customemoji;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.customemoji.model.Emoji;
import net.runelite.api.IconID;
import net.runelite.api.IndexedSprite;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ChatIconManager;

public final class PluginUtils
{
	public static final float NOISE_FLOOR = -60f;
	private static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");

	private PluginUtils()
	{
	}

	public static boolean isEmojiEnabled(String emojiName, Set<String> disabledEmojis)
	{
		return !disabledEmojis.contains(emojiName);
	}

	public static float volumeToGain(int volume100)
	{
		// range[NOISE_FLOOR, 0]
		float gainDB;

		// Graph of the function
		// https://www.desmos.com/calculator/wdhsfbxgeo

		// clamp to 0-100
		float volume = Math.min(100, volume100);
		// convert linear volume 0-100 to log control
		boolean isSilent = volume <= 0.1;
		if (isSilent)
		{
			gainDB = NOISE_FLOOR;
		}
		else
		{
			gainDB = (float) (10 * (Math.log(volume / 100)));
		}

		return gainDB;
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

	public static Emoji getEmojiById(Map<String, Emoji> emojis, int id)
	{
		if (emojis == null)
		{
			return null;
		}

		for (Emoji emoji : emojis.values())
		{
			if (emoji.getId() == id)
			{
				return emoji;
			}
		}

		return null;
	}

	public static List<Emoji> getEmojisFromWidget(Widget widget, Map<String, Emoji> emojis)
	{
		List<Emoji> result = new ArrayList<>();

		if (widget == null || emojis == null)
		{
			return result;
		}

		String text = widget.getText();
		List<Integer> imageIds = PluginUtils.getImageIdsFromText(text);

		for (int id : imageIds)
		{
			Emoji emoji = PluginUtils.getEmojiById(emojis, id);
			if (emoji != null)
			{
				result.add(emoji);
			}
		}

		return result;
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

	public static String findEmojiNameByImageId(int imageId, Map<String, Emoji> emojis, ChatIconManager chatIconManager)
	{
		// Check custom emojis first
		for (Emoji emoji : emojis.values())
		{
			if (chatIconManager.chatIconIndex(emoji.getId()) == imageId)
			{
				return emoji.getText();
			}
		}

		// Check built-in RuneLite IconIDs
		for (IconID icon : IconID.values())
		{
			if (icon.getIndex() == imageId)
			{
				return PluginUtils.formatIconName(icon.name());
			}
		}

		return null;
	}

	private static String formatIconName(String enumName)
	{
		String[] words = enumName.toLowerCase().split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words)
		{
			if (result.length() > 0)
			{
				result.append(" ");
			}
			result.append(Character.toUpperCase(word.charAt(0)));
			result.append(word.substring(1));
		}
		return result.toString();
	}
}
