package com.customemoji;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PluginUtils
{
	private static final int MAX_FOLDER_DEPTH = 8;
	private static final float NOISE_FLOOR = -60f;

	private PluginUtils()
	{
		// Utility class
	}

	public static List<File> flattenFolder(@NonNull File folder)
	{
		return flattenFolder(folder, 0);
	}

	private static List<File> flattenFolder(@NonNull File folder, int depth)
	{
		if (depth > MAX_FOLDER_DEPTH)
		{
			log.warn("Max depth of {} was reached path:{}", depth, folder);
			return List.of();
		}

		if (!folder.isDirectory())
		{
			return List.of(folder);
		}

		File[] children = folder.listFiles();
		if (children == null)
		{
			return List.of();
		}

		List<File> flattened = new ArrayList<>();
		for (File child : children)
		{
			flattened.addAll(flattenFolder(child, depth + 1));
		}

		return flattened;
	}

	public static String extractFileName(String errorMessage)
	{
		if (errorMessage == null)
		{
			return "";
		}

		if (errorMessage.contains("<col="))
		{
			int start = errorMessage.indexOf(">");
			int end = errorMessage.indexOf("</col>");
			if (start != -1 && end != -1 && start < end)
			{
				String fullPath = errorMessage.substring(start + 1, end);
				return fullPath.substring(fullPath.lastIndexOf(File.separator) + 1);
			}
		}

		if (errorMessage.contains(File.separator))
		{
			String[] parts = errorMessage.split("[" + Pattern.quote(File.separator) + "]");
			if (parts.length > 0)
			{
				return parts[parts.length - 1];
			}
		}

		return errorMessage;
	}

	/**
	 * Parses the comma-separated disabled emojis string into a Set.
	 *
	 * @param disabledEmojisString The comma-separated string of disabled emoji names
	 * @return Set of disabled emoji names (never null)
	 */
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

	/**
	 * Checks if an emoji is enabled based on the disabled emojis configuration.
	 *
	 * @param emojiName The name of the emoji to check
	 * @param disabledEmojisConfig The comma-separated string of disabled emoji names
	 * @return true if the emoji is enabled, false if disabled
	 */
	public static boolean isEmojiEnabled(String emojiName, String disabledEmojisConfig)
	{
		return !parseDisabledEmojis(disabledEmojisConfig).contains(emojiName);
	}

	/**
	 * Parses the comma-separated resizing disabled emojis string into a Set.
	 *
	 * @param resizingDisabledEmojisString The comma-separated string of emoji names with resizing disabled
	 * @return Set of emoji names with resizing disabled (never null)
	 */
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

	/**
	 * Converts a volume percentage (0-100) to a gain value in decibels.
	 *
	 * @param volume100 Volume as a percentage (0-100)
	 * @return Gain in decibels
	 */
	public static float volumeToGain(int volume100)
	{
		float gainDB;

		float volume = Math.min(100, volume100);

		if (volume <= 0.1)
		{
			gainDB = NOISE_FLOOR;
		}
		else
		{
			gainDB = (float) (10 * (Math.log(volume / 100)));
		}

		return gainDB;
	}
}