package com.customemoji.panel.tree;

import com.customemoji.service.EmojiStateManager;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles toggle operations for enabling/disabling emojis and resizing mode.
 */
public class EmojiToggleHandler
{
	public static final String PATH_SEPARATOR = "/";
	public static final String MODE_ENABLE_DISABLE = "Enable/Disable";
	public static final String MODE_RESIZING = "Resizing";

	private final EmojiStateManager emojiStateManager;
	private final Runnable onContentRefreshNeeded;
	private final Runnable onFolderStatesUpdateNeeded;
	private final JScrollPane scrollPane;
	private final JPanel contentPanel;

	private String currentMode = MODE_ENABLE_DISABLE;
	private boolean isLoading = false;

	private Map<String, List<EmojiTreeNode>> folderContents;

	public EmojiToggleHandler(EmojiStateManager emojiStateManager,
							   JScrollPane scrollPane,
							   JPanel contentPanel,
							   Runnable onContentRefreshNeeded,
							   Runnable onFolderStatesUpdateNeeded)
	{
		this.emojiStateManager = emojiStateManager;
		this.scrollPane = scrollPane;
		this.contentPanel = contentPanel;
		this.onContentRefreshNeeded = onContentRefreshNeeded;
		this.onFolderStatesUpdateNeeded = onFolderStatesUpdateNeeded;
	}

	public void setFolderContents(Map<String, List<EmojiTreeNode>> folderContents)
	{
		this.folderContents = folderContents;
	}

	public String getCurrentMode()
	{
		return this.currentMode;
	}

	public void setCurrentMode(String mode)
	{
		this.currentMode = mode;
	}

	public boolean isResizingMode()
	{
		return MODE_RESIZING.equals(this.currentMode);
	}

	public void toggleMode()
	{
		boolean isCurrentlyResizeMode = MODE_RESIZING.equals(this.currentMode);
		this.currentMode = isCurrentlyResizeMode ? MODE_ENABLE_DISABLE : MODE_RESIZING;
	}

	public boolean isLoading()
	{
		return this.isLoading;
	}

	public void handleItemToggle(EmojiTreeNode item, boolean checked, String currentFolderPath)
	{
		if (this.isResizingMode())
		{
			this.handleResizingToggle(item, checked, currentFolderPath);
		}
		else
		{
			this.handleEnableDisableToggle(item, checked, currentFolderPath);
		}
	}

	public void updateAllFolderStates(FolderStructureBuilder structureBuilder)
	{
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			String parentPath = entry.getKey();
			for (EmojiTreeNode item : entry.getValue())
			{
				if (item.isFolder())
				{
					String fullPath = parentPath.isEmpty() ? item.getName() : parentPath + PATH_SEPARATOR + item.getName();
					item.setEnabled(structureBuilder.calculateFolderEnabled(fullPath));
					item.setResizingEnabled(structureBuilder.calculateFolderResizingEnabled(fullPath));
				}
			}
		}
	}

	private void handleEnableDisableToggle(EmojiTreeNode item, boolean enabled, String currentFolderPath)
	{
		item.setEnabled(enabled);

		if (item.isFolder())
		{
			String targetPath = currentFolderPath.isEmpty() ? item.getName() : currentFolderPath + PATH_SEPARATOR + item.getName();
			Set<String> emojisToToggle = this.collectFolderEmojis(targetPath);
			this.updateFolderEmojiNodes(targetPath, enabled);

			this.emojiStateManager.setMultipleEmojisEnabled(emojisToToggle, enabled);

			this.onFolderStatesUpdateNeeded.run();
			int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
			this.onContentRefreshNeeded.run();
			SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
		}
		else
		{
			this.emojiStateManager.setEmojiEnabled(item.getName(), enabled);

			this.onFolderStatesUpdateNeeded.run();
			int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
			this.onContentRefreshNeeded.run();
			SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
		}
	}

	private void handleResizingToggle(EmojiTreeNode item, boolean resizingEnabled, String currentFolderPath)
	{
		if (this.isLoading)
		{
			return;
		}

		this.isLoading = true;
		item.setResizingEnabled(resizingEnabled);

		if (item.isFolder())
		{
			this.handleFolderResizingToggle(item, resizingEnabled, currentFolderPath);
		}
		else
		{
			this.handleSingleEmojiResizingToggle(item, resizingEnabled);
		}
	}

	private void handleSingleEmojiResizingToggle(EmojiTreeNode item, boolean resizingEnabled)
	{
		int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
		this.disableAllButtons();

		this.emojiStateManager.setEmojiResizing(item.getName(), resizingEnabled);

		SwingUtilities.invokeLater(() ->
		{
			this.isLoading = false;
			this.onContentRefreshNeeded.run();
			SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
		});
	}

	private void handleFolderResizingToggle(EmojiTreeNode item, boolean resizingEnabled, String currentFolderPath)
	{
		int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
		this.disableAllButtons();

		String targetPath = currentFolderPath.isEmpty() ? item.getName() : currentFolderPath + PATH_SEPARATOR + item.getName();
		Set<String> emojisToToggle = this.collectEnabledFolderEmojis(targetPath);
		this.updateFolderEmojiResizingNodes(targetPath, resizingEnabled);

		this.emojiStateManager.setMultipleEmojisResizing(emojisToToggle, resizingEnabled);

		SwingUtilities.invokeLater(() ->
		{
			this.isLoading = false;
			this.onFolderStatesUpdateNeeded.run();
			this.onContentRefreshNeeded.run();
			this.scrollPane.getVerticalScrollBar().setValue(scrollPosition);
		});
	}

	private Set<String> collectFolderEmojis(String folderPath)
	{
		Set<String> emojis = new HashSet<>();
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
			if (isInFolder)
			{
				for (EmojiTreeNode item : entry.getValue())
				{
					if (!item.isFolder())
					{
						emojis.add(item.getName());
					}
				}
			}
		}
		return emojis;
	}

	private Set<String> collectEnabledFolderEmojis(String folderPath)
	{
		Set<String> emojis = new HashSet<>();
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
			if (isInFolder)
			{
				for (EmojiTreeNode item : entry.getValue())
				{
					boolean isEnabledEmoji = !item.isFolder() && item.isEnabled();
					if (isEnabledEmoji)
					{
						emojis.add(item.getName());
					}
				}
			}
		}
		return emojis;
	}

	private void updateFolderEmojiNodes(String folderPath, boolean enabled)
	{
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
			if (isInFolder)
			{
				for (EmojiTreeNode item : entry.getValue())
				{
					if (!item.isFolder())
					{
						item.setEnabled(enabled);
					}
				}
			}
		}
	}

	private void updateFolderEmojiResizingNodes(String folderPath, boolean resizingEnabled)
	{
		for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
		{
			boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
			if (isInFolder)
			{
				for (EmojiTreeNode item : entry.getValue())
				{
					boolean isEnabledEmoji = !item.isFolder() && item.isEnabled();
					if (isEnabledEmoji)
					{
						item.setResizingEnabled(resizingEnabled);
					}
				}
			}
		}
	}

	private boolean isPathInFolder(String path, String folderPath)
	{
		boolean isExactMatch = path.equals(folderPath);
		boolean isSubfolder = path.startsWith(folderPath + PATH_SEPARATOR);
		return isExactMatch || isSubfolder;
	}

	private void disableAllButtons()
	{
		for (Component component : this.contentPanel.getComponents())
		{
			boolean isRowPanel = component instanceof JPanel;
			if (!isRowPanel)
			{
				continue;
			}

			JPanel rowPanel = (JPanel) component;
			for (Component child : rowPanel.getComponents())
			{
				boolean isButton = child instanceof JButton;
				if (isButton)
				{
					JButton button = (JButton) child;
					button.setEnabled(false);
					button.paintImmediately(button.getBounds());
				}
			}
		}
	}
}
