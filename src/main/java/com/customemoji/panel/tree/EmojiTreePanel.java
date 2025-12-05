package com.customemoji.panel.tree;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;
import com.customemoji.panel.PanelConstants;
import net.runelite.api.Client;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Panel containing an explorer-style emoji browser with folder navigation.
 */
public class EmojiTreePanel extends JPanel
{
    private final Client client;
    private final ChatIconManager chatIconManager;
    private final Map<String, Emoji> emojis;
    private final ScheduledExecutorService executor;

    private Set<String> disabledEmojis;
    private Set<String> resizingDisabledEmojis;

    // UI components
    private JPanel contentPanel;
    private JScrollPane scrollPane;
    private JButton resizeModeButton;

    // Extracted components
    private transient FolderStructureBuilder structureBuilder;
    private transient NavigationController navigationController;
    private transient EmojiToggleHandler toggleHandler;

    // Folder structure cache
    private transient Map<String, List<EmojiTreeNode>> folderContents = new HashMap<>();

    @Inject
    public EmojiTreePanel(Client client, ChatIconManager chatIconManager,
                          Map<String, Emoji> emojis,
                          @Named("disabledEmojis") Set<String> disabledEmojis,
                          @Named("resizingDisabledEmojis") Set<String> resizingDisabledEmojis,
                          ScheduledExecutorService executor)
    {
        this.client = client;
        this.chatIconManager = chatIconManager;
        this.emojis = emojis;
        this.executor = executor;
        this.disabledEmojis = new HashSet<>(disabledEmojis);
        this.resizingDisabledEmojis = new HashSet<>(resizingDisabledEmojis);

        this.setLayout(new BorderLayout());
        this.initializeComponents();
        this.buildFolderStructure();
        this.navigationController.navigateToFolder(new ArrayList<>());
    }

    public void setOnDisabledEmojisChanged(Consumer<Set<String>> callback)
    {
        this.toggleHandler.setOnDisabledEmojisChanged(callback);
    }

    public void setOnResizingDisabledEmojisChanged(Consumer<Set<String>> callback)
    {
        this.toggleHandler.setOnResizingDisabledEmojisChanged(callback);
    }

    public void setOnEmojiResizingToggled(Consumer<String> callback)
    {
        this.toggleHandler.setOnEmojiResizingToggled(callback);
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

    public void updateDisabledEmojis(Set<String> disabledEmojis)
    {
        this.disabledEmojis = new HashSet<>(disabledEmojis);
        this.rebuildAndRefresh();
    }

    public Set<String> getDisabledEmojis()
    {
        return new HashSet<>(this.disabledEmojis);
    }

    public void updateResizingDisabledEmojis(Set<String> resizingDisabledEmojis)
    {
        this.resizingDisabledEmojis = new HashSet<>(resizingDisabledEmojis);
        this.rebuildAndRefresh();
    }

    public Set<String> getResizingDisabledEmojis()
    {
        return new HashSet<>(this.resizingDisabledEmojis);
    }

    private void initializeComponents()
    {
        // Content panel inside scroll pane
        this.contentPanel = new JPanel();
        this.contentPanel.setLayout(new BoxLayout(this.contentPanel, BoxLayout.Y_AXIS));
        this.contentPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);

        this.scrollPane = new JScrollPane(this.contentPanel);
        this.scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.scrollPane.getViewport().setBackground(PanelConstants.CONTENT_BACKGROUND);

        // Initialize toggle handler
        this.toggleHandler = new EmojiToggleHandler(
            this.disabledEmojis,
            this.resizingDisabledEmojis,
            this.executor,
            this.scrollPane,
            this.contentPanel,
            this::updateContent,
            this::updateAllFolderStates
        );

        // Header panel with back button and path
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

        // Initialize navigation controller
        this.navigationController = new NavigationController(backButton, pathLabel, this::updateContent);
        backButton.addActionListener(e -> this.navigationController.navigateBack());

        this.resizeModeButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_BOUNDING_BOX)));
        this.resizeModeButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
        this.resizeModeButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
        this.resizeModeButton.setFocusable(false);
        this.resizeModeButton.setToolTipText("Toggle resize mode");
        this.updateResizeModeButtonColor();
        this.resizeModeButton.addActionListener(e -> {
            this.toggleHandler.toggleMode();
            this.updateResizeModeButtonColor();
            this.buildFolderStructure();
            this.updateContent();
        });

        JButton refreshButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_ARROW_CLOCKWISE)));
        refreshButton.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
        refreshButton.setMaximumSize(PanelConstants.HEADER_BUTTON_SIZE);
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh view");
        refreshButton.addActionListener(e -> {
            this.buildFolderStructure();
            this.updateContent();
        });

        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.X_AXIS));
        navPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);
        navPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        navPanel.add(backButton);
        navPanel.add(Box.createHorizontalStrut(4));
        navPanel.add(refreshButton);
        navPanel.add(Box.createHorizontalStrut(4));
        navPanel.add(this.resizeModeButton);
        navPanel.add(Box.createHorizontalStrut(8));
        navPanel.add(pathLabel);

        headerPanel.add(navPanel, BorderLayout.CENTER);

        this.add(headerPanel, BorderLayout.NORTH);
        this.add(this.scrollPane, BorderLayout.CENTER);
    }

    private void buildFolderStructure()
    {
        this.structureBuilder = new FolderStructureBuilder(
            this.client,
            this.chatIconManager,
            this.emojis,
            this.disabledEmojis,
            this.resizingDisabledEmojis
        );
        this.folderContents = this.structureBuilder.build(this.navigationController.getSearchFilter());
        this.toggleHandler.setFolderContents(this.folderContents);
    }

    private void updateAllFolderStates()
    {
        this.toggleHandler.updateAllFolderStates(this.structureBuilder);
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

        // Scroll to top
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(0));
    }

    private List<EmojiTreeNode> getItemsForCurrentView()
    {
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

    private void rebuildAndRefresh()
    {
        Runnable task = () -> {
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
}
