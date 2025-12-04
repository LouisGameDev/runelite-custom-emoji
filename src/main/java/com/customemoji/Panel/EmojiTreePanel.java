package com.customemoji.Panel;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;
import net.runelite.api.Client;
import net.runelite.client.game.ChatIconManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel containing an explorer-style emoji browser with folder navigation.
 */
public class EmojiTreePanel extends JPanel
{
    private final Client client;
    private final ChatIconManager chatIconManager;
    private final Map<String, Emoji> emojis;
    private Consumer<Set<String>> onDisabledEmojisChanged;

    private Set<String> disabledEmojis;
    private String currentSearchFilter = "";

    // Navigation state
    private List<String> currentPath = new ArrayList<>();

    // UI components
    private JPanel headerPanel;
    private JButton backButton;
    private JButton homeButton;
    private JLabel pathLabel;
    private JPanel contentPanel;
    private JScrollPane scrollPane;

    // Folder structure cache
    private Map<String, List<FolderItem>> folderContents = new HashMap<>();

    @Inject
    public EmojiTreePanel(Client client, ChatIconManager chatIconManager,
                          Map<String, Emoji> emojis, Set<String> disabledEmojis)
    {
        this.client = client;
        this.chatIconManager = chatIconManager;
        this.emojis = emojis;
        this.disabledEmojis = new HashSet<>(disabledEmojis);

        this.setLayout(new BorderLayout());
        this.initializeComponents();
        this.buildFolderStructure();
        this.navigateToFolder(new ArrayList<>());
    }

    public void setOnDisabledEmojisChanged(Consumer<Set<String>> callback)
    {
        this.onDisabledEmojisChanged = callback;
    }



    private void initializeComponents()
    {
        // Header panel with back button and path
        this.headerPanel = new JPanel(new BorderLayout());
        this.headerPanel.setBackground(new Color(30, 30, 30));
        this.headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        Dimension buttonSize = new Dimension(30, 24);

        this.backButton = new JButton("◀");
        this.backButton.setPreferredSize(buttonSize);
        this.backButton.setMaximumSize(buttonSize);
        this.backButton.setFocusable(false);
        this.backButton.setEnabled(false);
        this.backButton.setToolTipText("Go back a directory");
        this.backButton.addActionListener(e -> this.navigateBack());

        this.homeButton = new JButton("🏠");
        this.homeButton.setPreferredSize(buttonSize);
        this.homeButton.setMaximumSize(buttonSize);
        this.homeButton.setFocusable(false);
        this.homeButton.setEnabled(false);
        this.homeButton.setToolTipText("Go to root");
        this.homeButton.addActionListener(e -> this.navigateToFolder(new ArrayList<>()));

        JButton refreshButton = new JButton("🔄");
        refreshButton.setPreferredSize(buttonSize);
        refreshButton.setMaximumSize(buttonSize);
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh view");
        refreshButton.addActionListener(e -> {
            this.buildFolderStructure();
            this.updateContent();
        });

        this.pathLabel = new JLabel("Emojis");
        this.pathLabel.setForeground(new Color(220, 138, 0));
        this.pathLabel.setFont(this.pathLabel.getFont().deriveFont(Font.BOLD));

        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.X_AXIS));
        navPanel.setBackground(new Color(40, 40, 40));
        navPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        navPanel.add(this.backButton);
        navPanel.add(Box.createHorizontalStrut(4));
        navPanel.add(refreshButton);
        navPanel.add(Box.createHorizontalStrut(4));
        navPanel.add(this.homeButton);
        navPanel.add(Box.createHorizontalStrut(8));
        navPanel.add(this.pathLabel);

        this.headerPanel.add(navPanel, BorderLayout.CENTER);

        // Content panel inside scroll pane
        this.contentPanel = new JPanel();
        this.contentPanel.setLayout(new BoxLayout(this.contentPanel, BoxLayout.Y_AXIS));
        this.contentPanel.setBackground(new Color(40, 40, 40));

        this.scrollPane = new JScrollPane(this.contentPanel);
        this.scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.scrollPane.getViewport().setBackground(new Color(40, 40, 40));

        this.add(this.headerPanel, BorderLayout.NORTH);
        this.add(this.scrollPane, BorderLayout.CENTER);
    }

    private void buildFolderStructure()
    {
        this.folderContents.clear();
        File emojisFolder = CustomEmojiPlugin.EMOJIS_FOLDER;

        // Map to track which folders exist at each path level
        Map<String, Set<String>> subfoldersByPath = new HashMap<>();
        subfoldersByPath.put("", new HashSet<>());

        for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
        {
            String emojiName = entry.getKey();
            Emoji emoji = entry.getValue();
            File emojiFile = emoji.getFile();

            // Apply search filter
            if (!this.currentSearchFilter.isEmpty())
            {
                String relativePath = emojisFolder.toPath().relativize(emojiFile.toPath()).toString();
                String[] pathParts = relativePath.split("[\\\\\\/]");

                boolean matchFound = emojiName.toLowerCase().contains(this.currentSearchFilter);

                if (!matchFound)
                {
                    for (int i = 0; i < pathParts.length - 1; i++)
                    {
                        if (pathParts[i].toLowerCase().contains(this.currentSearchFilter))
                        {
                            matchFound = true;
                            break;
                        }
                    }
                }

                if (!matchFound)
                {
                    continue;
                }
            }

            String folderPath = this.extractFolderPath(emojiFile, emojisFolder);

            // Register all parent folders
            String[] pathParts = folderPath.isEmpty() ? new String[0] : folderPath.split("/");
            String currentPathStr = "";
            for (int i = 0; i < pathParts.length; i++)
            {
                String parentPath = currentPathStr;
                if (!currentPathStr.isEmpty())
                {
                    currentPathStr += "/";
                }
                currentPathStr += pathParts[i];

                subfoldersByPath.computeIfAbsent(parentPath, k -> new HashSet<>()).add(pathParts[i]);
                subfoldersByPath.computeIfAbsent(currentPathStr, k -> new HashSet<>());
            }

            // Add emoji to its folder
            BufferedImage emojiImage = this.loadEmojiImage(emoji);
            boolean failedToLoad = (emojiImage == null);
            boolean isEnabled = !this.disabledEmojis.contains(emojiName);

            FolderItem item = new FolderItem(emojiName, false, isEnabled, emojiImage, failedToLoad);
            this.folderContents.computeIfAbsent(folderPath, k -> new ArrayList<>()).add(item);
        }

        // Add folder items for each folder level
        for (Map.Entry<String, Set<String>> entry : subfoldersByPath.entrySet())
        {
            String parentPath = entry.getKey();
            for (String subfolder : entry.getValue())
            {
                String fullSubfolderPath = parentPath.isEmpty() ? subfolder : parentPath + "/" + subfolder;
                boolean isEnabled = this.calculateFolderEnabled(fullSubfolderPath);
                FolderItem folderItem = new FolderItem(subfolder, true, isEnabled, null, false);
                this.folderContents.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(folderItem);
            }
        }

        // Sort each folder's contents: folders first, then alphabetically
        for (List<FolderItem> items : this.folderContents.values())
        {
            items.sort((a, b) -> {
                if (a.isFolder && !b.isFolder) return -1;
                if (!a.isFolder && b.isFolder) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });
        }
    }

    private boolean calculateFolderEnabled(String folderPath)
    {
        // Check if any emoji in this folder or subfolders is enabled
        for (Map.Entry<String, List<FolderItem>> entry : this.folderContents.entrySet())
        {
            String path = entry.getKey();
            boolean isInFolder = path.equals(folderPath) || path.startsWith(folderPath + "/");

            if (isInFolder)
            {
                for (FolderItem item : entry.getValue())
                {
                    if (!item.isFolder && item.isEnabled)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void navigateToFolder(List<String> path)
    {
        this.currentPath = new ArrayList<>(path);
        this.updateHeader();
        this.updateContent();
    }

    private void navigateBack()
    {
        if (!this.currentPath.isEmpty())
        {
            this.currentPath.remove(this.currentPath.size() - 1);
            this.updateHeader();
            this.updateContent();
        }
    }

    private void updateHeader()
    {
        // Disable back/home buttons when searching or at root
        boolean isSearching = !this.currentSearchFilter.isEmpty();
        boolean isAtRoot = this.currentPath.isEmpty();
        this.backButton.setEnabled(!isAtRoot && !isSearching);
        this.homeButton.setEnabled(!isAtRoot && !isSearching);

        if (isSearching)
        {
            this.pathLabel.setText("Search results");
        }
        else if (isAtRoot)
        {
            this.pathLabel.setText("Emojis");
        }
        else
        {
            // Show only current folder name
            String currentFolder = this.currentPath.get(this.currentPath.size() - 1);
            this.pathLabel.setText(currentFolder);
        }
    }

    private void updateContent()
    {
        this.contentPanel.removeAll();

        List<FolderItem> items;

        // When searching, flatten all results into a single list (no folders)
        if (!this.currentSearchFilter.isEmpty())
        {
            items = new ArrayList<>();
            for (List<FolderItem> folderItems : this.folderContents.values())
            {
                for (FolderItem item : folderItems)
                {
                    if (!item.isFolder)
                    {
                        items.add(item);
                    }
                }
            }
            items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        else
        {
            String pathKey = String.join("/", this.currentPath);
            items = this.folderContents.getOrDefault(pathKey, new ArrayList<>());
        }

        for (FolderItem item : items)
        {
            JPanel rowPanel = this.createRowPanel(item);
            this.contentPanel.add(rowPanel);
        }

        // Add glue at the end to push items to top
        this.contentPanel.add(Box.createVerticalGlue());

        this.contentPanel.revalidate();
        this.contentPanel.repaint();

        // Scroll to top
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(0));
    }

    private JPanel createRowPanel(FolderItem item)
    {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBackground(new Color(40, 40, 40));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        rowPanel.setPreferredSize(new Dimension(200, 28));


        // Left panel with image and name
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        int maxImageWidth = 20;

        if (item.isFolder)
        {
            JLabel folderIcon = new JLabel("📁");
            leftPanel.add(folderIcon);
            leftPanel.add(Box.createHorizontalStrut(5));
        }
        else if (item.image != null)
        {
            BufferedImage resizedImage = item.image;
            int imageHeight = resizedImage.getHeight();
            int imageWidth = resizedImage.getWidth();

            JPanel imageContainer = new JPanel();
            imageContainer.setLayout(new BoxLayout(imageContainer, BoxLayout.X_AXIS));
            imageContainer.setOpaque(false);
            imageContainer.setPreferredSize(new Dimension(maxImageWidth, imageHeight));
            imageContainer.setMinimumSize(new Dimension(maxImageWidth, imageHeight));
            imageContainer.setMaximumSize(new Dimension(maxImageWidth, imageHeight));

            imageContainer.add(Box.createHorizontalGlue());

            JLabel imageLabel = new JLabel(new ImageIcon(resizedImage));
            imageLabel.setPreferredSize(new Dimension(imageWidth, imageHeight));
            imageLabel.setMinimumSize(new Dimension(imageWidth, imageHeight));
            imageLabel.setMaximumSize(new Dimension(imageWidth, imageHeight));
            imageContainer.add(imageLabel);

            leftPanel.add(imageContainer);
            leftPanel.add(Box.createHorizontalStrut(5));
        }
        else if (!item.isFolder)
        {
            if (item.failedToLoad)
            {
                JPanel warningContainer = new JPanel();
                warningContainer.setLayout(new BoxLayout(warningContainer, BoxLayout.X_AXIS));
                warningContainer.setOpaque(false);
                warningContainer.setPreferredSize(new Dimension(maxImageWidth, 16));
                warningContainer.setMinimumSize(new Dimension(maxImageWidth, 16));
                warningContainer.setMaximumSize(new Dimension(maxImageWidth, 16));

                warningContainer.add(Box.createHorizontalGlue());

                JLabel warningLabel = new JLabel(this.createWarningIcon());
                warningLabel.setToolTipText("Failed to load emoji image");
                warningContainer.add(warningLabel);

                leftPanel.add(warningContainer);
                leftPanel.add(Box.createHorizontalStrut(5));
            }
            else
            {
                leftPanel.add(Box.createHorizontalStrut(maxImageWidth + 5));
            }
        }

        // Name label
        JLabel nameLabel = new JLabel(item.name);
        nameLabel.setToolTipText(item.name);

        if (item.isFolder)
        {
            nameLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        }

        if (item.isEnabled)
        {
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            if (item.isFolder)
            {
                nameLabel.setForeground(new Color(220, 138, 0));
            }
            else
            {
                nameLabel.setForeground(Color.WHITE);
            }
        }
        else
        {
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));
            nameLabel.setForeground(Color.GRAY);
        }

        if (!item.isFolder && item.failedToLoad)
        {
            nameLabel.setForeground(new Color(255, 150, 50));
            nameLabel.setText(item.name + " (!)");
        }

        leftPanel.add(nameLabel);

        rowPanel.add(leftPanel, BorderLayout.CENTER);

        // Add checkbox
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(item.isEnabled);
        checkBox.setOpaque(false);
        checkBox.setFocusable(false);
        checkBox.addActionListener(e -> this.handleItemToggle(item, checkBox.isSelected()));
        rowPanel.add(checkBox, BorderLayout.EAST);

        // Click handler for folders - add to leftPanel since it covers the clickable area
        if (item.isFolder)
        {
            rowPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            leftPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            MouseAdapter folderClickHandler = new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    List<String> newPath = new ArrayList<>(currentPath);
                    newPath.add(item.name);
                    navigateToFolder(newPath);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    rowPanel.setBackground(new Color(50, 50, 50));
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    rowPanel.setBackground(new Color(40, 40, 40));
                }
            };

            leftPanel.addMouseListener(folderClickHandler);
            nameLabel.addMouseListener(folderClickHandler);
        }

        return rowPanel;
    }

    private void handleItemToggle(FolderItem item, boolean enabled)
    {
        item.isEnabled = enabled;

        if (item.isFolder)
        {
            // Toggle all emojis in folder and subfolders
            String folderPath = this.getCurrentFolderPath();
            String targetPath = folderPath.isEmpty() ? item.name : folderPath + "/" + item.name;
            this.toggleFolderEmojis(targetPath, enabled);

            // Need to rebuild UI to update child checkboxes
            this.updateAllFolderStates();
            int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
            this.updateContent();
            SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
        }
        else
        {
            if (enabled)
            {
                this.disabledEmojis.remove(item.name);
            }
            else
            {
                this.disabledEmojis.add(item.name);
            }
            // No rebuild needed - checkbox is already updated
            this.updateAllFolderStates();
        }

        this.notifyDisabledEmojisChanged();
    }

    private void toggleFolderEmojis(String folderPath, boolean enabled)
    {
        for (Map.Entry<String, List<FolderItem>> entry : this.folderContents.entrySet())
        {
            String path = entry.getKey();
            boolean isInFolder = path.equals(folderPath) || path.startsWith(folderPath + "/");

            if (isInFolder)
            {
                for (FolderItem item : entry.getValue())
                {
                    if (!item.isFolder)
                    {
                        item.isEnabled = enabled;
                        if (enabled)
                        {
                            this.disabledEmojis.remove(item.name);
                        }
                        else
                        {
                            this.disabledEmojis.add(item.name);
                        }
                    }
                }
            }
        }
    }

    private void updateAllFolderStates()
    {
        for (Map.Entry<String, List<FolderItem>> entry : this.folderContents.entrySet())
        {
            String parentPath = entry.getKey();
            for (FolderItem item : entry.getValue())
            {
                if (item.isFolder)
                {
                    String fullPath = parentPath.isEmpty() ? item.name : parentPath + "/" + item.name;
                    item.isEnabled = this.calculateFolderEnabled(fullPath);
                }
            }
        }
    }

    private String getCurrentFolderPath()
    {
        return String.join("/", this.currentPath);
    }

    private String extractFolderPath(File emojiFile, File baseFolder)
    {
        String relativePath = baseFolder.toPath().relativize(emojiFile.toPath()).toString();
        String[] pathParts = relativePath.split("[\\\\/]");

        StringBuilder folderPath = new StringBuilder();
        for (int i = 0; i < pathParts.length - 1; i++)
        {
            if (folderPath.length() > 0)
            {
                folderPath.append("/");
            }
            folderPath.append(pathParts[i]);
        }

        return folderPath.toString();
    }

    public void setSearchFilter(String filter)
    {
        String normalizedFilter = filter.toLowerCase().trim();
        if (!normalizedFilter.equals(this.currentSearchFilter))
        {
            this.currentSearchFilter = normalizedFilter;
            this.rebuildAndRefresh();
        }
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

    public void clearSearchFilter()
    {
        this.currentSearchFilter = "";
    }

    private void rebuildAndRefresh()
    {
        Runnable task = () -> {
            this.buildFolderStructure();

            // If current path no longer exists, go back to root
            String pathKey = String.join("/", this.currentPath);
            if (!this.currentPath.isEmpty() && !this.folderContents.containsKey(pathKey))
            {
                this.currentPath.clear();
            }

            this.updateHeader();
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

    private void notifyDisabledEmojisChanged()
    {
        if (this.onDisabledEmojisChanged != null)
        {
            this.onDisabledEmojisChanged.accept(new HashSet<>(this.disabledEmojis));
        }
    }


    private BufferedImage loadEmojiImage(Emoji emoji)
    {
        try
        {
            BufferedImage image = emoji.getCacheImage(this.client, this.chatIconManager);
            return image;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Icon createWarningIcon()
    {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int[] xPoints = {size / 2, 2, size - 2};
        int[] yPoints = {2, size - 2, size - 2};

        g2d.setColor(new Color(255, 150, 50));
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.setColor(new Color(200, 100, 0));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawPolygon(xPoints, yPoints, 3);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        String exclamation = "!";
        int textX = (size - fm.stringWidth(exclamation)) / 2;
        int textY = size - 4;
        g2d.drawString(exclamation, textX, textY);

        g2d.dispose();
        return new ImageIcon(image);
    }

    /**
     * Simple data class for items in the folder view.
     */
    private static class FolderItem
    {
        String name;
        boolean isFolder;
        boolean isEnabled;
        BufferedImage image;
        boolean failedToLoad;

        FolderItem(String name, boolean isFolder, boolean isEnabled, BufferedImage image, boolean failedToLoad)
        {
            this.name = name;
            this.isFolder = isFolder;
            this.isEnabled = isEnabled;
            this.image = image;
            this.failedToLoad = failedToLoad;
        }
    }
}
