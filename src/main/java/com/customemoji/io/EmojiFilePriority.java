package com.customemoji.io;

import com.customemoji.model.Holiday;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EmojiFilePriority
{
	public static final String SEASONAL_FOLDER_NAME = "Seasonal";

	private static final int PRIORITY_EXCLUDED = -1;
	private static final int PRIORITY_GITHUB = 0;
	private static final int PRIORITY_SEASONAL = 1;
	private static final int PRIORITY_LOCAL = 2;
	
	private EmojiFilePriority()
	{
	}

	// Returns one file per emoji name: highest rank wins, ties keep the last file
	public static List<File> resolveWinners(List<File> files, File githubPackFolder, Set<Holiday> activeHolidays)
	{
		Map<String, File> winnersByName = new HashMap<>();
		Map<String, Integer> ranksByName = new HashMap<>();

		for (File file : files)
		{
			String name = FileUtils.getNameWithoutExtension(file);
			if (name == null)
			{
				continue;
			}

			int rank = EmojiFilePriority.rankOf(file, githubPackFolder, activeHolidays);
			if (rank == EmojiFilePriority.PRIORITY_EXCLUDED)
			{
				continue;
			}

			Integer currentRank = ranksByName.get(name);
			boolean winsTie = currentRank == null || rank >= currentRank;

			if (winsTie)
			{
				winnersByName.put(name, file);
				ranksByName.put(name, rank);
			}
		}

		List<File> resolved = new ArrayList<>();

		for (File file : files)
		{
			String name = FileUtils.getNameWithoutExtension(file);
			if (name == null)
			{
				continue;
			}

			if (file.equals(winnersByName.get(name)))
			{
				resolved.add(file);
			}
		}

		return resolved;
	}

	// True when the path sits in a Seasonal folder whose holiday is not currently active
	public static boolean isInactiveSeasonalPath(String path, Set<Holiday> activeHolidays)
	{
		boolean isSeasonal = EmojiFilePriority.isInSeasonalFolder(path);
		if (!isSeasonal)
		{
			return false;
		}

		Holiday holiday = EmojiFilePriority.seasonalHolidayOf(path);
		return holiday == null || !activeHolidays.contains(holiday);
	}

	private static int rankOf(File file, File githubPackFolder, Set<Holiday> activeHolidays)
	{
		String path = file.getPath();

		if (EmojiFilePriority.isInactiveSeasonalPath(path, activeHolidays))
		{
			return EmojiFilePriority.PRIORITY_EXCLUDED;
		}

		boolean isSeasonal = EmojiFilePriority.isInSeasonalFolder(path);
		boolean isLocal = !EmojiFilePriority.isInsideFolder(file, githubPackFolder);
		int sourceRank = isLocal ? EmojiFilePriority.PRIORITY_LOCAL : EmojiFilePriority.PRIORITY_GITHUB;
		int seasonalRank = isSeasonal ? EmojiFilePriority.PRIORITY_SEASONAL : 0;

		return sourceRank + seasonalRank;
	}

	private static boolean isInSeasonalFolder(String path)
	{
		String[] segments = EmojiFilePriority.pathSegments(path);

		// Stop before the last segment, which is the file name
		for (int i = 0; i < segments.length - 1; i++)
		{
			if (EmojiFilePriority.SEASONAL_FOLDER_NAME.equalsIgnoreCase(segments[i]))
			{
				return true;
			}
		}

		return false;
	}

	private static Holiday seasonalHolidayOf(String path)
	{
		String[] segments = EmojiFilePriority.pathSegments(path);

		// Both the Seasonal segment and the holiday segment after it must be folders
		for (int i = 0; i < segments.length - 2; i++)
		{
			boolean isSeasonalSegment = EmojiFilePriority.SEASONAL_FOLDER_NAME.equalsIgnoreCase(segments[i]);
			if (!isSeasonalSegment)
			{
				continue;
			}

			Holiday holiday = EmojiFilePriority.holidayByFolderName(segments[i + 1]);
			if (holiday != null)
			{
				return holiday;
			}
		}

		return null;
	}

	private static Holiday holidayByFolderName(String folderName)
	{
		for (Holiday holiday : Holiday.values())
		{
			if (holiday.getFolderName().equalsIgnoreCase(folderName))
			{
				return holiday;
			}
		}

		return null;
	}

	private static boolean isInsideFolder(File file, File folder)
	{
		if (folder == null)
		{
			return false;
		}

		return file.toPath().toAbsolutePath().normalize()
				   .startsWith(folder.toPath().toAbsolutePath().normalize());
	}

	private static String[] pathSegments(String path)
	{
		return path.split("[\\\\/]");
	}
}
