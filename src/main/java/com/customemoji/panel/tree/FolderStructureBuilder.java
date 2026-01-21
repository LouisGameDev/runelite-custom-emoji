package com.customemoji.panel.tree;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;
import com.customemoji.service.EmojiStateManager;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and manages the folder structure for the emoji tree.
 */
public class FolderStructureBuilder
{
	public static final String PATH_SEPARATOR = "/";
	public static final String ROOT_FOLDER_NAME = "All Emoji";

	private final Map<String, Emoji> emojis;
	private final EmojiStateManager emojiStateManager;

	private Map<String, List<EmojiTreeNode>> folderContents = new HashMap<>();

	public FolderStructureBuilder(Map<String, Emoji> emojis, EmojiStateManager emojiStateManager)
	{
		this.emojis = emojis;
		this.emojiStateManager = emojiStateManager;
	}

	public Map<String, List<EmojiTreeNode>> build(String searchFilter)
	{
		this.folderContents.clear();
		File emojisFolder = CustomEmojiPlugin.EMOJIS_FOLDER;

		Map<String, Set<String>> subfoldersByPath = new HashMap<>();
		subfoldersByPath.put("", new HashSet<>());
		subfoldersByPath.get("").add(ROOT_FOLDER_NAME);
		subfoldersByPath.put(ROOT_FOLDER_NAME, new HashSet<>());

		for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
		{
			String emojiName = entry.getKey();
			Emoji emoji = entry.getValue();
			File emojiFile = emoji.getFile();

			boolean matchesFilter = this.matchesSearchFilter(emojiName, emojiFile, emojisFolder, searchFilter);
			if (!matchesFilter)
			{
				continue;
			}

			String relativeFolderPath = this.extractFolderPath(emojiFile, emojisFolder);
			String folderPath = relativeFolderPath.isEmpty()
				? ROOT_FOLDER_NAME
				: ROOT_FOLDER_NAME + PATH_SEPARATOR + relativeFolderPath;
			this.registerParentFolders(folderPath, subfoldersByPath);
			this.addEmojiItem(emojiName, emoji, folderPath);
		}

		this.addFolderItems(subfoldersByPath);
		this.sortFolderContents();

		return this.folderContents;
	}

	public Map<String, List<EmojiTreeNode>> getFolderContents()
	{
		return this.folderContents;
	}

	public boolean calculateFolderEnabled(String folderPath)
	{
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			String path = entry.getKey();
			boolean isInFolder = path.equals(folderPath) || path.startsWith(folderPath + PATH_SEPARATOR);

			if (isInFolder)
			{
				for (EmojiTreeNode item : entry.getValue())
				{
					boolean isEmojiEnabled = !item.isFolder() && item.isEnabled();
					if (isEmojiEnabled)
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean calculateFolderResizingEnabled(String folderPath)
	{
		List<String> enabledEmojiNames = this.getEnabledEmojiNamesInFolder(folderPath);
		boolean hasNoEnabledEmojis = enabledEmojiNames.isEmpty();

		if (hasNoEnabledEmojis)
		{
			return false;
		}

		boolean anyHasResizingDisabled = this.anyEmojiHasResizingDisabled(enabledEmojiNames);
		return !anyHasResizingDisabled;
	}

	public String extractFolderPath(File emojiFile, File baseFolder)
	{
		String relativePath = baseFolder.toPath().relativize(emojiFile.toPath()).toString();
		String[] pathParts = relativePath.split("[\\\\/]");

		StringBuilder folderPath = new StringBuilder();
		for (int i = 0; i < pathParts.length - 1; i++)
		{
			if (folderPath.length() > 0)
			{
				folderPath.append(PATH_SEPARATOR);
			}
			folderPath.append(pathParts[i]);
		}

		return folderPath.toString();
	}

	private boolean matchesSearchFilter(String emojiName, File emojiFile, File emojisFolder, String searchFilter)
	{
		if (searchFilter.isEmpty())
		{
			return true;
		}

		boolean nameMatches = emojiName.toLowerCase().contains(searchFilter);
		if (nameMatches)
		{
			return true;
		}

		return this.folderPathMatchesFilter(emojiFile, emojisFolder, searchFilter);
	}

	private boolean folderPathMatchesFilter(File emojiFile, File emojisFolder, String searchFilter)
	{
		String relativePath = emojisFolder.toPath().relativize(emojiFile.toPath()).toString();
		String[] pathParts = relativePath.split("[\\\\\\/]");

		for (int i = 0; i < pathParts.length - 1; i++)
		{
			boolean folderMatches = pathParts[i].toLowerCase().contains(searchFilter);
			if (folderMatches)
			{
				return true;
			}
		}
		return false;
	}

	private void registerParentFolders(String folderPath, Map<String, Set<String>> subfoldersByPath)
	{
		String[] pathParts = folderPath.isEmpty() ? new String[0] : folderPath.split(PATH_SEPARATOR);
		String currentPathStr = "";

		for (int i = 0; i < pathParts.length; i++)
		{
			String parentPath = currentPathStr;
			currentPathStr = this.appendPathSegment(currentPathStr, pathParts[i]);

			subfoldersByPath.computeIfAbsent(parentPath, k -> new HashSet<>()).add(pathParts[i]);
			subfoldersByPath.computeIfAbsent(currentPathStr, k -> new HashSet<>());
		}
	}

	private String appendPathSegment(String basePath, String segment)
	{
		if (basePath.isEmpty())
		{
			return segment;
		}
		return basePath + PATH_SEPARATOR + segment;
	}

	private void addEmojiItem(String emojiName, Emoji emoji, String folderPath)
	{
		BufferedImage emojiImage = this.loadEmojiImage(emoji);
		boolean failedToLoad = (emojiImage == null);
		boolean isEnabled = this.emojiStateManager.isEnabled(emojiName);
		boolean isResizingEnabled = this.emojiStateManager.isResizingEnabled(emojiName);

		EmojiTreeNode item = EmojiTreeNode.createEmoji(emojiName, isEnabled, isResizingEnabled, emojiImage, failedToLoad);
		this.folderContents.computeIfAbsent(folderPath, k -> new ArrayList<>()).add(item);
	}

	private void addFolderItems(Map<String, Set<String>> subfoldersByPath)
	{
		for (Map.Entry<String, Set<String>> entry : subfoldersByPath.entrySet())
		{
			String parentPath = entry.getKey();
			for (String subfolder : entry.getValue())
			{
				String fullSubfolderPath = parentPath.isEmpty() ? subfolder : parentPath + PATH_SEPARATOR + subfolder;
				boolean isEnabled = this.calculateFolderEnabled(fullSubfolderPath);
				boolean isResizingEnabled = this.calculateFolderResizingEnabled(fullSubfolderPath);
				EmojiTreeNode folderItem = EmojiTreeNode.createFolder(subfolder, isEnabled, isResizingEnabled);
				this.folderContents.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(folderItem);
			}
		}
	}

	private void sortFolderContents()
	{
		for (List<EmojiTreeNode> items : this.folderContents.values())
		{
			items.sort(this::compareTreeNodes);
		}
	}

	private int compareTreeNodes(EmojiTreeNode a, EmojiTreeNode b)
	{
		boolean aIsFolder = a.isFolder();
		boolean bIsFolder = b.isFolder();

		if (aIsFolder && !bIsFolder)
		{
			return -1;
		}
		if (!aIsFolder && bIsFolder)
		{
			return 1;
		}
		return a.getName().compareToIgnoreCase(b.getName());
	}

	private List<String> getEnabledEmojiNamesInFolder(String folderPath)
	{
		List<String> result = new ArrayList<>();

		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
			if (!isInFolder)
			{
				continue;
			}

			for (EmojiTreeNode item : entry.getValue())
			{
				boolean isEnabledEmoji = !item.isFolder() && this.emojiStateManager.isEnabled(item.getName());
				if (isEnabledEmoji)
				{
					result.add(item.getName());
				}
			}
		}

		return result;
	}

	private boolean isPathInFolder(String path, String folderPath)
	{
		return path.equals(folderPath) || path.startsWith(folderPath + PATH_SEPARATOR);
	}

	private boolean anyEmojiHasResizingDisabled(List<String> emojiNames)
	{
		for (String name : emojiNames)
		{
			boolean isResizingDisabled = !this.emojiStateManager.isResizingEnabled(name);
			if (isResizingDisabled)
			{
				return true;
			}
		}
		return false;
	}

	private BufferedImage loadEmojiImage(Emoji emoji)
	{
		return emoji.getStaticImage();
	}
}

