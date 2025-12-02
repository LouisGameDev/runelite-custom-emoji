package com.customemoji.Panel;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.config.ConfigManager;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;
import net.runelite.client.util.ImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Panel for managing custom emojis with a tree view showing folders and individual emojis.
 * Supports searching by emoji name or folder name, checkbox enabling/disabling, and
 * persistent configuration storage.
 */
public class CustomEmojiPanel extends PluginPanel
{
    private final CustomEmojiPlugin plugin;
    private final CustomEmojiConfig config;
    private final ConfigManager configManager;
    private JTree checkboxTree;
    private DefaultMutableTreeNode rootNode;
    private Set<String> disabledEmojis;
    private Set<String> expandedFolderPaths = new HashSet<>(); // Store folder paths as strings
    private JTextField searchField;
    private String currentSearchFilter = "";
    private volatile boolean isUpdatingStates = false;
    private volatile boolean isRebuildingTree = false;
    private DocumentListener searchDocumentListener;

    public CustomEmojiPanel(CustomEmojiPlugin plugin, CustomEmojiConfig config, ConfigManager configManager)
    {
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        this.disabledEmojis = parseDisabledEmojis(config.disabledEmojis());

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Allow the panel to expand but don't interfere with scrolling
        setMinimumSize(new Dimension(150, 100));

        add(createSearchPanel(), BorderLayout.NORTH);
        add(createScrollableTreePanel(), BorderLayout.CENTER);
    }

    private JScrollPane createScrollableTreePanel()
    {
        // Create tree structure from loaded emojis
        rootNode = new DefaultMutableTreeNode("Emojis");
        buildEmojiTree();

        checkboxTree = new JTree(new DefaultTreeModel(rootNode));
        var renderer = new CheckboxTreeCellRenderer();
        checkboxTree.setCellRenderer(renderer);
        checkboxTree.setEditable(false); // Disable default editing
        checkboxTree.setRootVisible(false);
        checkboxTree.setShowsRootHandles(true);
        checkboxTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        checkboxTree.setRowHeight(28);
        checkboxTree.setToggleClickCount(0); // Disable double-click editing
        checkboxTree.setInvokesStopCellEditing(true); // Ensure no editing sessions

        // Add click listener for checkbox functionality
        checkboxTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                TreePath path = checkboxTree.getPathForLocation(e.getX(), e.getY());
                if (path != null && e.getClickCount() == 1) {
                    handleNodeToggle(path);
                    e.consume(); // Consume the event to prevent default behavior
                }
            }
        });

        // Add expansion listener to track folder states by path strings
        checkboxTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                String folderPath = getPathString(event.getPath());
                if (folderPath != null) {
                    expandedFolderPaths.add(folderPath);
                }
                // Refresh display after expansion
                SwingUtilities.invokeLater(() -> refreshTreeDisplay());
            }
            
            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                String folderPath = getPathString(event.getPath());
                if (folderPath != null) {
                    expandedFolderPaths.remove(folderPath);
                }
                // Refresh display after collapse
                SwingUtilities.invokeLater(() -> refreshTreeDisplay());
            }
        });
        
        
        // Expand all nodes by default
        expandAllNodes();
        
        // Create scroll pane that fills available space
        JScrollPane scrollPane = new JScrollPane(checkboxTree) {
            @Override
            public Dimension getPreferredSize() {
                Container parent = CustomEmojiPanel.this;
                if (parent != null && parent.getHeight() > 0) {
                    // Use available height minus search panel height minus padding
                    int availableHeight = Math.max(200, parent.getHeight() - 50);
                    return new Dimension(230, availableHeight);
                }
                return new Dimension(230, 400); // Fallback
            }
        };
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        return scrollPane;
    }
    
    private JPanel createSearchPanel()
    {
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        // Create search field with magnifying glass icon inside
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(200, 25));
        searchField.setMinimumSize(new Dimension(200, 25));
        searchField.setMaximumSize(new Dimension(200, 25));
        
        // Add magnifying glass icon inside the text field
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        
        // Create a panel to hold the icon and text field
        JPanel searchFieldPanel = new JPanel(new BorderLayout());
        searchFieldPanel.add(searchIcon, BorderLayout.WEST);
        searchFieldPanel.add(searchField, BorderLayout.CENTER);
        searchFieldPanel.setBorder(searchField.getBorder());
        searchFieldPanel.setBackground(searchField.getBackground());
        
        // Remove border from the text field since the panel now has it
        searchField.setBorder(BorderFactory.createEmptyBorder());
        
        // Add document listener for real-time search
        searchDocumentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSearch();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSearch();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSearch();
            }
        };
        searchField.getDocument().addDocumentListener(searchDocumentListener);
        
        // Add the search field panel (with icon) to the main search panel
        searchPanel.add(searchFieldPanel, BorderLayout.CENTER);
        
        return searchPanel;
    }
    
    private void updateSearch()
    {
        String searchText = searchField.getText().toLowerCase().trim();
        
        if (!searchText.equals(currentSearchFilter))
        {
            currentSearchFilter = searchText;
            rebuildTreeWithCurrentState();
        }
    }
    
    @Override
    public Dimension getPreferredSize()
    {
        // Always report a size that fits within the parent to prevent outer scrolling
        return getPanelDimension();
    }

    @Override
    public Dimension getMaximumSize()
    {
        // Ensure we never exceed parent size
        return getPanelDimension();
    }

    private Dimension getPanelDimension() {
        Container parent = getParent();
        if (parent != null && parent.getSize().width > 0 && parent.getSize().height > 0)
        {
            // Report size slightly smaller than parent to prevent outer scroll
            return new Dimension(parent.getSize().width - 5, parent.getSize().height - 5);
        }
        return new Dimension(245, 395);
    }
    
    @Override
    public Dimension getMinimumSize()
    {
        // Keep minimum size reasonable but not too large
        return new Dimension(200, 150);
    }

    private void buildEmojiTree()
    {
        rootNode.removeAllChildren();
        Map<String, DefaultMutableTreeNode> folderNodes = new HashMap<>();
        
        // Get relative path from emojis folder for each emoji
        File emojisFolder = CustomEmojiPlugin.EMOJIS_FOLDER;
        
        // First pass: collect all matching emojis and their folder paths
        Map<String, List<Map.Entry<String, Emoji>>> folderEmojis = new HashMap<>();

        for (Map.Entry<String, Emoji> entry : this.plugin.getEmojis().entrySet())
        {
            String emojiName = entry.getKey();
            Emoji emoji = entry.getValue();
            File emojiFile = emoji.getFile();
            
            // Apply search filter - check both emoji name and folder path
            if (!currentSearchFilter.isEmpty())
            {
                String relativePath = emojisFolder.toPath().relativize(emojiFile.toPath()).toString();
                String[] pathParts = relativePath.split("[\\\\\\/]");
                
                boolean matchFound = false;
                
                // Check if emoji name matches
                if (emojiName.toLowerCase().contains(currentSearchFilter))
                {
                    matchFound = true;
                }
                
                // Check if any folder in the path matches
                if (!matchFound)
                {
                    for (int i = 0; i < pathParts.length - 1; i++) // Exclude filename
                    {
                        if (pathParts[i].toLowerCase().contains(currentSearchFilter))
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
            
            String folderKey = extractFolderPath(emojiFile, emojisFolder);
            folderEmojis.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(entry);
        }
        
        // Second pass: only create folders that have matching emojis, sorted alphabetically
        folderEmojis.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(folderEntry ->
        {
            String folderPath = folderEntry.getKey();
            List<Map.Entry<String, Emoji>> emojisInFolder = folderEntry.getValue();
            
            if (emojisInFolder.isEmpty()) return;
            
            // Create folder structure for this path
            DefaultMutableTreeNode currentParent = rootNode;
            
            if (!folderPath.isEmpty())
            {
                String[] pathParts = folderPath.split("[\\\\/]");
                StringBuilder currentPath = new StringBuilder();
                
                for (String pathPart : pathParts)
                {
                    if (currentPath.length() > 0) {
                        currentPath.append("/");
                    }
                    currentPath.append(pathPart);
                    String fullPath = currentPath.toString();
                    
                    if (!folderNodes.containsKey(fullPath))
                    {
                        // Create folder with placeholder state (will be calculated after all emojis are added)
                        DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(new EmojiTreeNode(pathPart, true, true, null));
                        folderNodes.put(fullPath, folderNode);
                        currentParent.add(folderNode);
                    }
                    currentParent = folderNodes.get(fullPath);
                }
            }
            
            // Sort emojis alphabetically by name before adding them
            emojisInFolder.sort((e1, e2) -> e1.getKey().compareToIgnoreCase(e2.getKey()));
            
            // Add all emojis in this folder
            for (Map.Entry<String, Emoji> emojiEntry : emojisInFolder)
            {
                String emojiName = emojiEntry.getKey();
                Emoji emoji = emojiEntry.getValue();

                boolean isEnabled = !this.disabledEmojis.contains(emojiName);
                BufferedImage emojiImage = this.loadEmojiImage(emoji);
                DefaultMutableTreeNode emojiNode = new DefaultMutableTreeNode(new EmojiTreeNode(emojiName, false, isEnabled, emojiImage));
                currentParent.add(emojiNode);
            }
        });
        
        // Sort all folders above emojis at every level
        sortFoldersAboveEmojis(rootNode);
        
        // Calculate folder states based on their contents
        calculateAllFolderStates(rootNode);
    }
    
    
    private void handleNodeToggle(TreePath path)
    {
        // Prevent concurrent updates
        if (isUpdatingStates || isRebuildingTree) return;
        
        
        try {
            isUpdatingStates = true;
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof EmojiTreeNode)
            {
                EmojiTreeNode treeNode = (EmojiTreeNode) node.getUserObject();
                treeNode.isEnabled = !treeNode.isEnabled;
            
                if (treeNode.isFolder)
                {
                    // Toggle all children and rebuild tree to show correct states
                    toggleChildren(node, treeNode.isEnabled);
                    rebuildTreeWithCurrentState();
                }
                else
                {
                    // Update disabled emojis set
                    if (treeNode.isEnabled)
                    {
                        disabledEmojis.remove(treeNode.name);
                    }
                    else
                    {
                        disabledEmojis.add(treeNode.name);
                    }
                    
                    // Rebuild tree to update folder states correctly
                    rebuildTreeWithCurrentState();
                }
                
                // Save to config
                saveDisabledEmojis();
            }
        } finally {
            isUpdatingStates = false;
        }
    }
    
    private void toggleChildren(DefaultMutableTreeNode parent, boolean enabled)
    {
        for (int i = 0; i < parent.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();
            childNode.isEnabled = enabled;
            
            if (childNode.isFolder)
            {
                toggleChildren(child, enabled);
            }
            else
            {
                // Update disabled emojis set
                if (enabled)
                {
                    disabledEmojis.remove(childNode.name);
                }
                else
                {
                    disabledEmojis.add(childNode.name);
                }
            }
        }
    }
    
    
    private void expandAllNodes()
    {
        // Store all folder paths as initially expanded
        storeAllFolderPaths(rootNode, "");
        
        // Expand all rows
        for (int i = 0; i < checkboxTree.getRowCount(); i++)
        {
            checkboxTree.expandRow(i);
        }
        
        // Refresh display
        SwingUtilities.invokeLater(() -> {
            refreshTreeDisplay();
            revalidate();
            repaint();
        });
    }
    
    private void refreshTreeDisplay()
    {
        if (checkboxTree != null) {
            checkboxTree.revalidate();
            checkboxTree.repaint();
        }
    }
    
    private String extractFolderPath(File emojiFile, File baseFolder)
    {
        String relativePath = baseFolder.toPath().relativize(emojiFile.toPath()).toString();
        String[] pathParts = relativePath.split("[\\\\/]");
        
        // Build folder path (everything except the filename)
        StringBuilder folderPath = new StringBuilder();
        for (int i = 0; i < pathParts.length - 1; i++)
        {
            if (folderPath.length() > 0) {
                folderPath.append("/");
            }
            folderPath.append(pathParts[i]);
        }
        
        return folderPath.toString();
    }
    
    private boolean calculateFolderState(DefaultMutableTreeNode folderNode)
    {
        // A folder is enabled if ANY emoji anywhere within its subtree is enabled
        return hasAnyEnabledEmoji(folderNode);
    }
    
    private boolean hasAnyEnabledEmoji(DefaultMutableTreeNode node)
    {
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();
            
            if (childNode.isFolder)
            {
                // Recursively check nested folders
                if (hasAnyEnabledEmoji(child))
                {
                    return true;
                }
            }
            else
            {
                // It's an emoji - check if it's enabled
                if (childNode.isEnabled)
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void sortFoldersAboveEmojis(DefaultMutableTreeNode node)
    {
        // First, recursively sort all child folders
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();
            
            if (childNode.isFolder)
            {
                sortFoldersAboveEmojis(child);
            }
        }
        
        // Now sort this node's direct children: folders first, then emojis
        if (node.getChildCount() > 1)
        {
            // Get all children
            java.util.List<DefaultMutableTreeNode> children = new java.util.ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++)
            {
                children.add((DefaultMutableTreeNode) node.getChildAt(i));
            }
            
            // Sort: folders first (by name), then emojis (by name)
            children.sort((a, b) -> {
                EmojiTreeNode nodeA = (EmojiTreeNode) a.getUserObject();
                EmojiTreeNode nodeB = (EmojiTreeNode) b.getUserObject();
                
                // Folders come before emojis
                if (nodeA.isFolder && !nodeB.isFolder) return -1;
                if (!nodeA.isFolder && nodeB.isFolder) return 1;
                
                // If both are same type, sort alphabetically
                return nodeA.name.compareToIgnoreCase(nodeB.name);
            });
            
            // Remove all children and re-add in sorted order
            node.removeAllChildren();
            for (DefaultMutableTreeNode child : children)
            {
                node.add(child);
            }
        }
    }
    
    private void calculateAllFolderStates(DefaultMutableTreeNode node)
    {
        // Process children first (bottom-up calculation)
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();
            
            if (childNode.isFolder)
            {
                calculateAllFolderStates(child);
                // After processing children, calculate this folder's state
                childNode.isEnabled = calculateFolderState(child);
            }
        }
    }
    
    private String getPathString(TreePath path)
    {
        if (path == null) return null;
        
        StringBuilder pathBuilder = new StringBuilder();
        Object[] pathComponents = path.getPath();
        
        // Skip root node (index 0)
        for (int i = 1; i < pathComponents.length; i++)
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) pathComponents[i];
            Object userObject = node.getUserObject();
            
            if (userObject instanceof EmojiTreeNode)
            {
                EmojiTreeNode treeNode = (EmojiTreeNode) userObject;
                if (treeNode.isFolder)
                {
                    if (pathBuilder.length() > 0)
                    {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(treeNode.name);
                }
            }
        }
        
        return pathBuilder.toString();
    }
    
    private void storeAllFolderPaths(DefaultMutableTreeNode node, String currentPath)
    {
        if (node.getUserObject() instanceof EmojiTreeNode)
        {
            EmojiTreeNode treeNode = (EmojiTreeNode) node.getUserObject();
            if (treeNode.isFolder)
            {
                String folderPath = currentPath.isEmpty() ? treeNode.name : currentPath + "/" + treeNode.name;
                expandedFolderPaths.add(folderPath);
                
                // Recursively store child folder paths
                for (int i = 0; i < node.getChildCount(); i++)
                {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                    storeAllFolderPaths(child, folderPath);
                }
            }
        }
        else
        {
            // Root node processing
            for (int i = 0; i < node.getChildCount(); i++)
            {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                storeAllFolderPaths(child, currentPath);
            }
        }
    }
    
    private void rebuildTreeWithCurrentState()
    {
        try {
            isRebuildingTree = true;
            
            // Temporarily disable search document listener to prevent interference
            if (searchField != null && searchDocumentListener != null) {
                searchField.getDocument().removeDocumentListener(searchDocumentListener);
            }
            
            // Store current search text
            String savedSearchText = searchField != null ? searchField.getText() : "";
            
            // Rebuild the tree structure (buildEmojiTree uses currentSearchFilter)
            buildEmojiTree();
            
            // Reload the tree model
            ((DefaultTreeModel) checkboxTree.getModel()).reload();
            
            // Apply expansion state immediately to prevent flickering
            restoreExpansionFromPaths();
            
            // Restore search text if it was changed
            if (searchField != null && !savedSearchText.equals(searchField.getText())) {
                searchField.setText(savedSearchText);
            }
            
            // Ensure currentSearchFilter matches what's in the search field
            currentSearchFilter = savedSearchText.toLowerCase().trim();
            
            // Re-enable search document listener
            if (searchField != null && searchDocumentListener != null) {
                searchField.getDocument().addDocumentListener(searchDocumentListener);
            }
            
            // Update size and repaint
            SwingUtilities.invokeLater(() -> {
                refreshTreeDisplay();
                revalidate();
                repaint();
            });
        } finally {
            isRebuildingTree = false;
        }
    }
    
    private void restoreExpansionFromPaths()
    {
        // Disable tree expansion events temporarily to prevent interference
        checkboxTree.setEnabled(false);
        
        try {
            // Expand folders based on stored paths in one efficient pass
            expandFoldersFromStoredPaths(rootNode, "");
        } finally {
            // Re-enable tree
            checkboxTree.setEnabled(true);
        }
    }
    
    private void expandFoldersFromStoredPaths(DefaultMutableTreeNode node, String currentPath)
    {
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object userObject = child.getUserObject();
            
            if (userObject instanceof EmojiTreeNode)
            {
                EmojiTreeNode treeNode = (EmojiTreeNode) userObject;
                if (treeNode.isFolder)
                {
                    String folderPath = currentPath.isEmpty() ? treeNode.name : currentPath + "/" + treeNode.name;
                    
                    if (expandedFolderPaths.contains(folderPath))
                    {
                        TreePath treePath = new TreePath(((DefaultTreeModel) checkboxTree.getModel()).getPathToRoot(child));
                        checkboxTree.expandPath(treePath);
                    }
                    
                    // Recursively check child folders
                    expandFoldersFromStoredPaths(child, folderPath);
                }
            }
        }
    }    
    
    private Set<String> parseDisabledEmojis(String disabledEmojisString)
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
    
    private void saveDisabledEmojis()
    {
        String disabledEmojisString = String.join(",", disabledEmojis);
        configManager.setConfiguration("custom-emote", "disabled_emojis", disabledEmojisString);
    }
    
    public void refreshEmojiTree()
    {
        refreshEmojiTree(true);
    }
    
    public void refreshEmojiTree(boolean clearSearch)
    {
        disabledEmojis = parseDisabledEmojis(config.disabledEmojis());
        
        if (clearSearch) {
            // Reset search when refreshing from external changes (file watcher, etc.)
            currentSearchFilter = "";
            if (searchField != null) {
                searchField.setText("");
            }
        }
        
        rebuildTreeWithCurrentState();
    }
    
    public Set<String> getDisabledEmojis()
    {
        return new HashSet<>(disabledEmojis);
    }

    public void updateFromConfig()
    {
        refreshEmojiTree(false); // Don't clear search when updating from config changes
    }
    
    private BufferedImage loadEmojiImage(Emoji emoji)
    {
        try
        {
            BufferedImage image = CustomEmojiPlugin.loadImage(emoji.getFile()).unwrap();
            if (this.config.resizeEmoji())
            {
                image = CustomEmojiPlugin.scaleDown(image, this.config.maxImageHeight());
            }
            // Scale to fit tree row height (20px with some padding)
            return ImageUtil.resizeImage(image, 20, 20, true);
        }
        catch (Exception e)
        {
            return null; // Return null if image can't be loaded
        }
    }

    // Inner class for tree node data
    public static class EmojiTreeNode
    {
        public final String name;
        public final boolean isFolder;
        public boolean isEnabled;
        public final BufferedImage image;
        
        public EmojiTreeNode(String name, boolean isFolder, boolean isEnabled, BufferedImage image)
        {
            this.name = name;
            this.isFolder = isFolder;
            this.isEnabled = isEnabled;
            this.image = image;
        }
        
        @Override
        public String toString()
        {
            return name;
        }
    }
}
