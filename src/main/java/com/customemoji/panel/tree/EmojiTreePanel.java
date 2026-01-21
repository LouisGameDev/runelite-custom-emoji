package com.customemoji.panel.tree;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.io.GitHubEmojiDownloader.DownloadProgress;
import com.customemoji.model.Emoji;
import com.customemoji.panel.DownloadProgressPanel;
import com.customemoji.panel.PanelConstants;
import com.customemoji.panel.StatusMessagePanel;
import com.customemoji.service.EmojiStateManager;
import com.customemoji.service.FolderStateManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel containing an explorer-style emoji browser with folder navigation.
 */
public class EmojiTreePanel extends JPanel
{
	private final Map<String, Emoji> emojis;
	private final EmojiStateManager emojiStateManager;
	private final FolderStateManager folderStateManager;

	private JPanel contentPanel;
	private JScrollPane scrollPane;
	private JButton resizeModeButton;
	private JButton downloadButton;
	private DownloadProgressPanel downloadProgressPanel;
	private StatusMessagePanel statusMessagePanel;
	private transient Runnable onDownloadClicked;
	private transient Runnable onReloadClicked;
	private transient FolderStructureBuilder structureBuilder;
	private transient NavigationController navigationController;
	private transient EmojiToggleHandler toggleHandler;
	private transient Map<String, List<EmojiTreeNode>> folderContents = new HashMap<>();

	@Inject
	public EmojiTreePanel(Map<String, Emoji> emojis, EmojiStateManager emojiStateManager, FolderStateManager folderStateManager)
	{
		this.emojis = emojis;
		this.emojiStateManager = emojiStateManager;
		this.folderStateManager = folderStateManager;

		this.setLayout(new BorderLayout());
		this.initializeComponents();
		this.buildFolderStructure();
		this.navigationController.navigateToFolder(new ArrayList<>());
	}

	public void setSearchFilter(String filter)
	{
		String normalizedFilter = filter.toLowerCase().trim();
		if (!normalizedFilter.equals(this.navigationController.getSearchFilter()))
		{
			this.navigationController.setSearchFilter(normalizedFilter);
			this.rebuildAndRefresh();
		}
	}

	public void clearSearchFilter()
	{
		this.navigationController.setSearchFilter("");
	}

	public void refreshDisabledState()
	{
		this.rebuildAndRefresh();
	}

	private void initializeComponents()
	{
		this.contentPanel = new JPanel();
		this.contentPanel.setLayout(new BoxLayout(this.contentPanel, BoxLayout.Y_AXIS));
		this.contentPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);

		this.scrollPane = new JScrollPane(this.contentPanel);
		this.scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		this.scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		this.scrollPane.setBorder(BorderFactory.createEmptyBorder());
		this.scrollPane.getViewport().setBackground(PanelConstants.CONTENT_BACKGROUND);

		this.toggleHandler = new EmojiToggleHandler(
			this.emojiStateManager,
			this.folderStateManager,
			this.scrollPane,
			this.contentPanel,
			this::updateContent,
			this::updateAllFolderStates
		);

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(PanelConstants.HEADER_BACKGROUND);
		headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, PanelConstants.HEADER_BORDER));

		JButton backButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_ARROW_LEFT)));
		backButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
		backButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
		backButton.setFocusable(false);
		backButton.setEnabled(false);
		backButton.setToolTipText("Go back a directory");

		JLabel pathLabel = new JLabel("Emojis");
		pathLabel.setForeground(PanelConstants.FOLDER_TEXT);
		pathLabel.setFont(pathLabel.getFont().deriveFont(Font.BOLD));

		this.navigationController = new NavigationController(backButton, pathLabel, this::updateContent);
		backButton.addActionListener(e -> this.navigationController.navigateBack());

		this.resizeModeButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_BOUNDING_BOX)));
		this.resizeModeButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
		this.resizeModeButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
		this.resizeModeButton.setFocusable(false);
		this.resizeModeButton.setToolTipText("Toggle resize mode");
		this.updateResizeModeButtonColor();
		this.resizeModeButton.addActionListener(e ->
		{
			this.toggleHandler.toggleMode();
			this.updateResizeModeButtonColor();
			this.buildFolderStructure();
			this.updateContent();
		});

		JButton refreshButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_ARROW_CLOCKWISE)));
		refreshButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
		refreshButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
		refreshButton.setFocusable(false);
		refreshButton.setToolTipText("Reload all emojis");
		refreshButton.addActionListener(e ->
		{
			if (this.onReloadClicked != null)
			{
				this.onReloadClicked.run();
			}
		});

		this.downloadButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_DOWNLOAD)));
		this.downloadButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
		this.downloadButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
		this.downloadButton.setFocusable(false);
		this.downloadButton.setToolTipText("Download emojis from GitHub");
		this.downloadButton.setVisible(false);
		this.downloadButton.addActionListener(e ->
		{
			if (this.onDownloadClicked != null)
			{
				this.onDownloadClicked.run();
			}
		});

		JPanel navPanel = new JPanel();
		navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.X_AXIS));
		navPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);
		navPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		navPanel.add(backButton);
		navPanel.add(Box.createHorizontalStrut(4));
		navPanel.add(refreshButton);
		navPanel.add(Box.createHorizontalStrut(4));
		navPanel.add(this.downloadButton);
		navPanel.add(Box.createHorizontalStrut(4));
		navPanel.add(this.resizeModeButton);
		navPanel.add(Box.createHorizontalStrut(8));
		navPanel.add(pathLabel);

		headerPanel.add(navPanel, BorderLayout.CENTER);

		this.downloadProgressPanel = new DownloadProgressPanel();
		this.statusMessagePanel = new StatusMessagePanel();

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
		bottomPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);
		bottomPanel.add(this.downloadProgressPanel);
		bottomPanel.add(this.statusMessagePanel);

		this.add(headerPanel, BorderLayout.NORTH);
		this.add(this.scrollPane, BorderLayout.CENTER);
		this.add(bottomPanel, BorderLayout.SOUTH);
	}

	private void buildFolderStructure()
	{
		this.structureBuilder = new FolderStructureBuilder(this.emojis, this.emojiStateManager, this.folderStateManager);
		this.folderContents = this.structureBuilder.build(this.navigationController.getSearchFilter());
		this.toggleHandler.setStructureBuilder(this.structureBuilder);
	}

	private void updateAllFolderStates()
	{
		this.toggleHandler.updateAllFolderStates();
	}

	private void updateContent()
	{
		this.contentPanel.removeAll();

		List<EmojiTreeNode> items = this.getItemsForCurrentView();

		String currentFolderPath = this.navigationController.getCurrentFolderPath();
		RowPanelBuilder rowBuilder = new RowPanelBuilder(
			this.toggleHandler,
			this.navigationController::navigateToFolder,
			this.navigationController.getCurrentPath()
		);

		for (EmojiTreeNode item : items)
		{
			JPanel rowPanel = rowBuilder.createRowPanel(item, currentFolderPath);
			this.contentPanel.add(rowPanel);
		}

		this.contentPanel.add(Box.createVerticalGlue());

		this.contentPanel.revalidate();
		this.contentPanel.repaint();

		SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(0));
	}

	private List<EmojiTreeNode> getItemsForCurrentView()
	{
		if (this.navigationController.isShowingRecentlyDownloaded())
		{
			return this.getRecentlyDownloadedItems();
		}

		if (this.navigationController.isSearching())
		{
			List<EmojiTreeNode> items = new ArrayList<>();
			for (List<EmojiTreeNode> folderItems : this.folderContents.values())
			{
				for (EmojiTreeNode item : folderItems)
				{
					if (!item.isFolder())
					{
						items.add(item);
					}
				}
			}
			items.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
			return items;
		}
		else
		{
			String pathKey = this.navigationController.getCurrentFolderPath();
			return this.folderContents.getOrDefault(pathKey, new ArrayList<>());
		}
	}

	private List<EmojiTreeNode> getRecentlyDownloadedItems()
	{
		List<String> emojiNames = this.navigationController.getRecentlyDownloadedEmojis();
		List<EmojiTreeNode> items = new ArrayList<>();

		for (List<EmojiTreeNode> folderItems : this.folderContents.values())
		{
			for (EmojiTreeNode item : folderItems)
			{
				boolean isMatchingEmoji = !item.isFolder() && emojiNames.contains(item.getName());
				if (isMatchingEmoji)
				{
					items.add(item);
				}
			}
		}

		items.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return items;
	}

	private void rebuildAndRefresh()
	{
		Runnable task = () ->
		{
			this.buildFolderStructure();

			this.navigationController.resetPathIfInvalid(this.folderContents);

			this.navigationController.updateHeader();
			this.updateContent();
		};

		if (SwingUtilities.isEventDispatchThread())
		{
			task.run();
		}
		else
		{
			SwingUtilities.invokeLater(task);
		}
	}

	private void updateResizeModeButtonColor()
	{
		boolean isResizeMode = this.toggleHandler.isResizingMode();
		if (isResizeMode)
		{
			this.resizeModeButton.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.GREEN));
			this.resizeModeButton.setToolTipText("Resize mode active - click to switch to enable/disable mode");
		}
		else
		{
			this.resizeModeButton.setBorder(BorderFactory.createEmptyBorder());
			this.resizeModeButton.setToolTipText("Click to toggle resize mode");
		}
	}

	public void setOnDownloadClicked(Runnable callback)
	{
		this.onDownloadClicked = callback;
	}

	public void setOnReloadClicked(Runnable callback)
	{
		this.onReloadClicked = callback;
	}

	public void setDownloadButtonVisible(boolean visible)
	{
		this.downloadButton.setVisible(visible);
	}

	public void setProgressSupplier(Supplier<DownloadProgress> progressSupplier)
	{
		this.downloadProgressPanel.setProgressSupplier(progressSupplier);
	}

	public void stopProgressPolling()
	{
		this.downloadProgressPanel.stopPolling();
	}

	public void showStatusMessage(String message, StatusMessagePanel.MessageType type)
	{
		this.statusMessagePanel.showMessage(message, type);
	}

	public void showStatusMessage(String message, StatusMessagePanel.MessageType type, boolean autoDismiss)
	{
		this.statusMessagePanel.showMessage(message, type, autoDismiss);
	}

	public void showRecentlyDownloaded(List<String> emojiNames)
	{
		this.navigationController.setRecentlyDownloadedEmojis(emojiNames);
		this.rebuildAndRefresh();
	}
}

