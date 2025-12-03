package com.customemoji.Panel;

import net.runelite.client.ui.PluginPanel;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatIconManager;

import javax.inject.Inject;
import javax.swing.*;
import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;

import java.awt.*;
import java.util.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Panel for managing custom emojis with a tree view showing folders and individual emojis.
 * Supports searching by emoji name or folder name, checkbox enabling/disabling, and
 * persistent configuration storage.
 */
public class CustomEmojiPanel extends PluginPanel
{
    private final CustomEmojiConfig config;
    private final ConfigManager configManager;
    private final ScheduledExecutorService executor;
    private Set<String> disabledEmojis = new HashSet<>();
    private SearchPanel searchPanel;
    private EmojiTreePanel emojiTreePanel;

    @Inject
    public CustomEmojiPanel(Client client, ChatIconManager chatIconManager, ConfigManager configManager,
                            CustomEmojiConfig config, Map<String, Emoji> emojis, Set<String> disabledEmojis,
                            ScheduledExecutorService executor)
    {
        this.configManager = configManager;
        this.config = config;
        this.executor = executor;
        this.disabledEmojis = disabledEmojis;

        this.setLayout(new BorderLayout());
        this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        this.setMinimumSize(new Dimension(150, 100));

        this.searchPanel = new SearchPanel(this::onSearchChanged);
        this.emojiTreePanel = new EmojiTreePanel(client, chatIconManager, emojis, disabledEmojis,
            this::onDisabledEmojisChanged);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new HeaderPanel(), BorderLayout.NORTH);
        topPanel.add(this.searchPanel, BorderLayout.CENTER);

        this.add(topPanel, BorderLayout.NORTH);
        this.add(this.emojiTreePanel, BorderLayout.CENTER);
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

    public void refreshEmojiTree()
    {
        this.refreshEmojiTree(true);
    }

    public void refreshEmojiTree(boolean clearSearch)
    {
        this.disabledEmojis = CustomEmojiPlugin.parseDisabledEmojis(this.config.disabledEmojis());

        if (clearSearch)
        {
            this.searchPanel.clearSearch();
            this.emojiTreePanel.clearSearchFilter();
        }

        this.emojiTreePanel.updateDisabledEmojis(this.disabledEmojis);
    }

    public Set<String> getDisabledEmojis()
    {
        return new HashSet<>(this.disabledEmojis);
    }

    public void updateFromConfig()
    {
        this.refreshEmojiTree(false);
    }

    private void onSearchChanged(String searchText)
    {
        this.emojiTreePanel.setSearchFilter(searchText);
    }

    private void onDisabledEmojisChanged(Set<String> newDisabledEmojis)
    {
        this.disabledEmojis = newDisabledEmojis;
        this.saveDisabledEmojis();
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

    private void saveDisabledEmojis()
    {
        String disabledEmojisString = String.join(",", this.disabledEmojis);
        this.executor.execute(() -> this.configManager.setConfiguration("custom-emote", "disabled_emojis", disabledEmojisString));
    }
}