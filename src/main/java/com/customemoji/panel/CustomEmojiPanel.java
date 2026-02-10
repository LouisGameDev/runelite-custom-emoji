package com.customemoji.panel;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.DownloadEmojisRequested;
import com.customemoji.event.GitHubDownloadCompleted;
import com.customemoji.event.GitHubDownloadStarted;
import com.customemoji.event.ReloadEmojisRequested;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.io.EmojiLoader;
import com.customemoji.panel.StatusMessagePanel.MessageType;
import com.customemoji.panel.tree.EmojiTreePanel;
import com.customemoji.service.EmojiStateManager;
import com.google.inject.Provider;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Panel for managing custom emojis with a tree view showing folders and individual emojis.
 * Supports searching by emoji name or folder name, checkbox enabling/disabling, and
 * persistent configuration storage.
 */
public class CustomEmojiPanel extends PluginPanel
{
	private final CustomEmojiConfig config;
	private final EmojiStateManager emojiStateManager;
	private final ClientToolbar clientToolbar;
	private final EventBus eventBus;
	private transient NavigationButton navigationButton;
	private Set<String> disabledEmojis = new HashSet<>();
	private Set<String> resizingDisabledEmojis = new HashSet<>();
	private List<String> pendingRecentlyDownloaded = new ArrayList<>();
	private SearchPanel searchPanel;
	private EmojiTreePanel emojiTreePanel;

	@Inject
	public CustomEmojiPanel(CustomEmojiConfig config,
							EmojiStateManager emojiStateManager,
							EmojiLoader emojiLoader,
							Provider<EmojiTreePanel> emojiTreePanelProvider,
							ClientToolbar clientToolbar,
							EventBus eventBus)
	{
		this.config = config;
		this.emojiStateManager = emojiStateManager;
		this.clientToolbar = clientToolbar;
		this.eventBus = eventBus;
		this.disabledEmojis = this.emojiStateManager.getDisabledEmojis();
		this.resizingDisabledEmojis = this.emojiStateManager.getResizingDisabledEmojis();

		this.setLayout(new BorderLayout());
		this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.setMinimumSize(new Dimension(150, 100));

		this.searchPanel = new SearchPanel(this::onSearchChanged);
		this.emojiTreePanel = emojiTreePanelProvider.get();
		this.emojiTreePanel.setEmojis(emojiLoader.getEmojis());
		this.emojiTreePanel.setOnDownloadClicked(() -> this.eventBus.post(new DownloadEmojisRequested()));
		this.emojiTreePanel.setOnReloadClicked(() -> this.eventBus.post(new ReloadEmojisRequested()));
		this.emojiTreePanel.setDownloadButtonVisible(PluginUtils.isGitHubDownloadConfigured(this.config));

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(new HeaderPanel(this.eventBus), BorderLayout.NORTH);
		topPanel.add(this.searchPanel, BorderLayout.CENTER);

		this.add(topPanel, BorderLayout.NORTH);
		this.add(this.emojiTreePanel, BorderLayout.CENTER);

		this.eventBus.register(this);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return this.getPanelDimension();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return this.getPanelDimension();
	}

	@Override
	public Dimension getMinimumSize()
	{
		return new Dimension(200, 150);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("custom-emote"))
		{
			return;
		}

		switch (event.getKey())
		{
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
			case CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS:
				SwingUtilities.invokeLater(this::updateFromConfig);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onReloadEmojisRequested(ReloadEmojisRequested event)
	{
		List<String> newEmojis = event.getNewEmojis();
		if (!newEmojis.isEmpty())
		{
			this.pendingRecentlyDownloaded = new ArrayList<>(newEmojis);
		}
	}

	@Subscribe
	public void onGitHubDownloadStarted(GitHubDownloadStarted event)
	{
		if (this.navigationButton != null)
		{
			SwingUtilities.invokeLater(() -> this.clientToolbar.openPanel(this.navigationButton));
		}
	}

	@Subscribe
	public void onGitHubDownloadCompleted(GitHubDownloadCompleted event)
	{
		GitHubEmojiDownloader.DownloadResult result = event.getResult();

		if (result.isSuccess())
		{
			SwingUtilities.invokeLater(() -> this.emojiTreePanel.showStatusMessage(result.formatPanelMessage(), MessageType.SUCCESS));
		}

		if (result.hasChanges() && event.isHadPreviousDownload())
		{
			this.setPendingRecentlyDownloaded(result.getChangedEmojiNames());
		}
	}

	@Subscribe
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.emojiTreePanel.setEmojis(event.getEmojis());
			this.refreshEmojiTree();
			this.emojiTreePanel.showStatusMessage(event.getEmojis().keySet().size() + " emojis loaded", MessageType.SUCCESS, true);
		});
	}

	public void shutdown()
	{
		this.eventBus.unregister(this);
		this.emojiTreePanel.shutDownProgressPanel();
	}

	public void refreshEmojiTree()
	{
		this.refreshEmojiTree(true);
	}

	public void refreshEmojiTree(boolean clearSearch)
	{
		this.disabledEmojis = this.emojiStateManager.getDisabledEmojis();
		this.resizingDisabledEmojis = this.emojiStateManager.getResizingDisabledEmojis();

		if (clearSearch)
		{
			this.searchPanel.clearSearch();
			this.emojiTreePanel.clearSearchFilter();
		}

		this.emojiTreePanel.updateDisabledEmojis(this.disabledEmojis);
		this.emojiTreePanel.updateResizingDisabledEmojis(this.resizingDisabledEmojis);

		if (!this.pendingRecentlyDownloaded.isEmpty())
		{
			List<String> toShow = new ArrayList<>(this.pendingRecentlyDownloaded);
			this.pendingRecentlyDownloaded.clear();
			this.emojiTreePanel.showRecentlyDownloaded(toShow);
		}
	}

	public void setPendingRecentlyDownloaded(List<String> emojiNames)
	{
		this.pendingRecentlyDownloaded = new ArrayList<>(emojiNames);
	}

	public Set<String> getDisabledEmojis()
	{
		return new HashSet<>(this.disabledEmojis);
	}

	public void updateFromConfig()
	{
		this.refreshEmojiTree(false);
		this.emojiTreePanel.setDownloadButtonVisible(PluginUtils.isGitHubDownloadConfigured(this.config));
	}

	public void setNavigationButton(NavigationButton navigationButton)
	{
		this.navigationButton = navigationButton;
	}

	private void onSearchChanged(String searchText)
	{
		this.emojiTreePanel.setSearchFilter(searchText);
	}

	private Dimension getPanelDimension()
	{
		Container parent = this.getParent();
		if (parent != null && parent.getSize().width > 0 && parent.getSize().height > 0)
		{
			return new Dimension(parent.getSize().width - 5, parent.getSize().height - 5);
		}
		return new Dimension(245, 395);
	}

}
