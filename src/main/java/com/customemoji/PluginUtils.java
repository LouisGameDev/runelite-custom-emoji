package com.customemoji;

import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;

import net.runelite.api.IndexedSprite;

public final class PluginUtils
{
	public static final float NOISE_FLOOR = -60f;

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
}
