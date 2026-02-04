package com.customemoji.panel;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.io.GitHubEmojiDownloader.DownloadProgress;
import com.customemoji.model.Emoji;
import com.customemoji.panel.StatusMessagePanel.MessageType;
import com.customemoji.panel.tree.EmojiTreePanel;
import com.customemoji.service.EmojiStateManager;
import com.google.inject.Provider;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Panel for managing custom emojis with a tree view showing folders and individual emojis.
 * Supports searching by emoji name or folder name, checkbox enabling/disabling, and
 * persistent configuration storage.
 */
public class CustomEmojiPanel extends PluginPanel
{
	private final CustomEmojiPlugin plugin;
	private final EmojiStateManager emojiStateManager;
	private final EventBus eventBus;
	private Set<String> disabledEmojis = new HashSet<>();
	private Set<String> resizingDisabledEmojis = new HashSet<>();
	private List<String> pendingRecentlyDownloaded = new ArrayList<>();
	private SearchPanel searchPanel;
	private EmojiTreePanel emojiTreePanel;

	@Inject
	public CustomEmojiPanel(CustomEmojiPlugin plugin, EmojiStateManager emojiStateManager,
							Provider<EmojiTreePanel> emojiTreePanelProvider, EventBus eventBus)
	{
		this.plugin = plugin;
		this.emojiStateManager = emojiStateManager;
		this.eventBus = eventBus;
		this.disabledEmojis = this.emojiStateManager.getDisabledEmojis();
		this.resizingDisabledEmojis = this.emojiStateManager.getResizingDisabledEmojis();

		this.setLayout(new BorderLayout());
		this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.setMinimumSize(new Dimension(150, 100));

		this.searchPanel = new SearchPanel(this::onSearchChanged);
		this.emojiTreePanel = emojiTreePanelProvider.get();
		this.emojiTreePanel.setOnDownloadClicked(this.plugin::triggerGitHubDownloadAndReload);
		this.emojiTreePanel.setOnReloadClicked(() -> this.plugin.scheduleReload(true));
		this.emojiTreePanel.setDownloadButtonVisible(this.plugin.isGitHubDownloadConfigured());

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(new HeaderPanel(plugin::openConfiguration), BorderLayout.NORTH);
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
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.emojiTreePanel.setEmojis(event.getEmojis());
			this.refreshEmojiTree();
			this.showStatusMessage(event.getEmojis().keySet().size() + " emojis loaded", MessageType.SUCCESS, true);
		});
	}

	public void shutdown()
	{
		this.eventBus.unregister(this);
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
		this.emojiTreePanel.setDownloadButtonVisible(this.plugin.isGitHubDownloadConfigured());
	}

	public void setProgressSupplier(Supplier<DownloadProgress> progressSupplier)
	{
		this.emojiTreePanel.setProgressSupplier(progressSupplier);
	}

	public void stopProgressPolling()
	{
		this.emojiTreePanel.stopProgressPolling();
	}

	public void showStatusMessage(String message, StatusMessagePanel.MessageType type)
	{
		this.emojiTreePanel.showStatusMessage(message, type);
	}

	public void showStatusMessage(String message, StatusMessagePanel.MessageType type, boolean autoDismiss)
	{
		this.emojiTreePanel.showStatusMessage(message, type, autoDismiss);
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
