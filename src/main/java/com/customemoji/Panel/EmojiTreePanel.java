package com.customemoji.Panel;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Emoji;
import com.customemoji.model.EmojiTreeNode;
import net.runelite.api.Client;
import net.runelite.client.game.ChatIconManager;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel containing the emoji tree view with checkbox functionality.
 */
public class EmojiTreePanel extends JScrollPane
{
    private final Client client;
    private final ChatIconManager chatIconManager;
    private final Map<String, Emoji> emojis;
    private final Consumer<Set<String>> onDisabledEmojisChanged;

    private JTree checkboxTree;
    private DefaultMutableTreeNode rootNode;
    private Set<String> disabledEmojis;
    private Set<String> expandedFolderPaths = new HashSet<>();
    private String currentSearchFilter = "";
    private volatile boolean isUpdatingStates = false;
    private volatile boolean isRebuildingTree = false;

    public EmojiTreePanel(Client client, ChatIconManager chatIconManager, Map<String, Emoji> emojis,
                          Set<String> disabledEmojis, Consumer<Set<String>> onDisabledEmojisChanged)
    {
        this.client = client;
        this.chatIconManager = chatIconManager;
        this.emojis = emojis;
        this.disabledEmojis = new HashSet<>(disabledEmojis);
        this.onDisabledEmojisChanged = onDisabledEmojisChanged;

        this.initializeTree();
        this.configureScrollPane();
    }

    private void initializeTree()
    {
        this.rootNode = new DefaultMutableTreeNode("Emojis");
        this.buildEmojiTree();

        this.checkboxTree = new JTree(new DefaultTreeModel(this.rootNode));
        CheckboxTreeCellRenderer renderer = new CheckboxTreeCellRenderer();
        this.checkboxTree.setCellRenderer(renderer);
        this.checkboxTree.setEditable(false);
        this.checkboxTree.setRootVisible(false);
        this.checkboxTree.setShowsRootHandles(true);
        this.checkboxTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.checkboxTree.setRowHeight(28);
        this.checkboxTree.setToggleClickCount(0);
        this.checkboxTree.setInvokesStopCellEditing(true);

        this.checkboxTree.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                TreePath path = checkboxTree.getPathForLocation(e.getX(), e.getY());
                if (path != null && e.getClickCount() == 1)
                {
                    handleNodeToggle(path);
                    e.consume();
                }
            }
        });

        this.checkboxTree.addTreeExpansionListener(new TreeExpansionListener()
        {
            @Override
            public void treeExpanded(TreeExpansionEvent event)
            {
                String folderPath = getPathString(event.getPath());
                if (folderPath != null)
                {
                    expandedFolderPaths.add(folderPath);
                }
                SwingUtilities.invokeLater(() -> refreshTreeDisplay());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event)
            {
                String folderPath = getPathString(event.getPath());
                if (folderPath != null)
                {
                    expandedFolderPaths.remove(folderPath);
                }
                SwingUtilities.invokeLater(() -> refreshTreeDisplay());
            }
        });

        this.expandAllNodes();
        this.setViewportView(this.checkboxTree);
    }

    private void configureScrollPane()
    {
        this.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.setBorder(BorderFactory.createEmptyBorder());
    }

    public void setSearchFilter(String filter)
    {
        String normalizedFilter = filter.toLowerCase().trim();
        if (!normalizedFilter.equals(this.currentSearchFilter))
        {
            this.currentSearchFilter = normalizedFilter;
            this.rebuildTreeWithCurrentState();
        }
    }

    public void updateDisabledEmojis(Set<String> disabledEmojis)
    {
        this.disabledEmojis = new HashSet<>(disabledEmojis);
        this.rebuildTreeWithCurrentState();
    }

    public Set<String> getDisabledEmojis()
    {
        return new HashSet<>(this.disabledEmojis);
    }

    public void clearSearchFilter()
    {
        this.currentSearchFilter = "";
    }

    private void buildEmojiTree()
    {
        this.rootNode.removeAllChildren();
        Map<String, DefaultMutableTreeNode> folderNodes = new HashMap<>();

        File emojisFolder = CustomEmojiPlugin.EMOJIS_FOLDER;

        Map<String, List<Map.Entry<String, Emoji>>> folderEmojis = new HashMap<>();

        for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
        {
            String emojiName = entry.getKey();
            Emoji emoji = entry.getValue();
            File emojiFile = emoji.getFile();

            if (!this.currentSearchFilter.isEmpty())
            {
                String relativePath = emojisFolder.toPath().relativize(emojiFile.toPath()).toString();
                String[] pathParts = relativePath.split("[\\\\\\/]");

                boolean matchFound = false;

                if (emojiName.toLowerCase().contains(this.currentSearchFilter))
                {
                    matchFound = true;
                }

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

            String folderKey = this.extractFolderPath(emojiFile, emojisFolder);
            folderEmojis.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(entry);
        }

        folderEmojis.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(folderEntry ->
            {
                String folderPath = folderEntry.getKey();
                List<Map.Entry<String, Emoji>> emojisInFolder = folderEntry.getValue();

                if (emojisInFolder.isEmpty()) return;

                DefaultMutableTreeNode currentParent = this.rootNode;

                if (!folderPath.isEmpty())
                {
                    String[] pathParts = folderPath.split("[\\\\/]");
                    StringBuilder currentPath = new StringBuilder();

                    for (String pathPart : pathParts)
                    {
                        if (currentPath.length() > 0)
                        {
                            currentPath.append("/");
                        }
                        currentPath.append(pathPart);
                        String fullPath = currentPath.toString();

                        if (!folderNodes.containsKey(fullPath))
                        {
                            DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(
                                new EmojiTreeNode(pathPart, true, true, null));
                            folderNodes.put(fullPath, folderNode);
                            currentParent.add(folderNode);
                        }
                        currentParent = folderNodes.get(fullPath);
                    }
                }

                emojisInFolder.sort((e1, e2) -> e1.getKey().compareToIgnoreCase(e2.getKey()));

                for (Map.Entry<String, Emoji> emojiEntry : emojisInFolder)
                {
                    String emojiName = emojiEntry.getKey();
                    Emoji emoji = emojiEntry.getValue();

                    boolean isEnabled = !this.disabledEmojis.contains(emojiName);
                    BufferedImage emojiImage = this.loadEmojiImage(emoji);
                    boolean failedToLoad = (emojiImage == null);
                    DefaultMutableTreeNode emojiNode = new DefaultMutableTreeNode(
                        new EmojiTreeNode(emojiName, false, isEnabled, emojiImage, failedToLoad));
                    currentParent.add(emojiNode);
                }
            });

        this.sortFoldersAboveEmojis(this.rootNode);
        this.calculateAllFolderStates(this.rootNode);
    }

    private void handleNodeToggle(TreePath path)
    {
        if (this.isUpdatingStates || this.isRebuildingTree) return;

        try
        {
            this.isUpdatingStates = true;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof EmojiTreeNode)
            {
                EmojiTreeNode treeNode = (EmojiTreeNode) node.getUserObject();
                treeNode.isEnabled = !treeNode.isEnabled;

                DefaultTreeModel model = (DefaultTreeModel) this.checkboxTree.getModel();

                if (treeNode.isFolder)
                {
                    this.toggleChildren(node, treeNode.isEnabled);
                    this.updateParentFolderStates(node);
                    model.reload(node);
                }
                else
                {
                    if (treeNode.isEnabled)
                    {
                        this.disabledEmojis.remove(treeNode.name);
                    }
                    else
                    {
                        this.disabledEmojis.add(treeNode.name);
                    }

                    model.nodeChanged(node);
                    this.updateParentFolderStates(node);
                }

                this.notifyDisabledEmojisChanged();
            }
        }
        finally
        {
            this.isUpdatingStates = false;
        }
    }

    private void updateParentFolderStates(DefaultMutableTreeNode node)
    {
        DefaultTreeModel model = (DefaultTreeModel) this.checkboxTree.getModel();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

        while (parent != null && parent != this.rootNode)
        {
            Object userObject = parent.getUserObject();
            if (userObject instanceof EmojiTreeNode)
            {
                EmojiTreeNode folderNode = (EmojiTreeNode) userObject;
                if (folderNode.isFolder)
                {
                    boolean newState = this.calculateFolderState(parent);
                    if (folderNode.isEnabled != newState)
                    {
                        folderNode.isEnabled = newState;
                        model.nodeChanged(parent);
                    }
                }
            }
            parent = (DefaultMutableTreeNode) parent.getParent();
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
                this.toggleChildren(child, enabled);
            }
            else
            {
                if (enabled)
                {
                    this.disabledEmojis.remove(childNode.name);
                }
                else
                {
                    this.disabledEmojis.add(childNode.name);
                }
            }
        }
    }

    private void expandAllNodes()
    {
        this.storeAllFolderPaths(this.rootNode, "");

        for (int i = 0; i < this.checkboxTree.getRowCount(); i++)
        {
            this.checkboxTree.expandRow(i);
        }

        SwingUtilities.invokeLater(() ->
        {
            this.refreshTreeDisplay();
            this.revalidate();
            this.repaint();
        });
    }

    private void refreshTreeDisplay()
    {
        if (this.checkboxTree != null)
        {
            this.checkboxTree.revalidate();
            this.checkboxTree.repaint();
        }
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

    private boolean calculateFolderState(DefaultMutableTreeNode folderNode)
    {
        return this.hasAnyEnabledEmoji(folderNode);
    }

    private boolean hasAnyEnabledEmoji(DefaultMutableTreeNode node)
    {
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();

            if (childNode.isFolder)
            {
                if (this.hasAnyEnabledEmoji(child))
                {
                    return true;
                }
            }
            else
            {
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
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();

            if (childNode.isFolder)
            {
                this.sortFoldersAboveEmojis(child);
            }
        }

        if (node.getChildCount() > 1)
        {
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++)
            {
                children.add((DefaultMutableTreeNode) node.getChildAt(i));
            }

            children.sort((a, b) ->
            {
                EmojiTreeNode nodeA = (EmojiTreeNode) a.getUserObject();
                EmojiTreeNode nodeB = (EmojiTreeNode) b.getUserObject();

                if (nodeA.isFolder && !nodeB.isFolder) return -1;
                if (!nodeA.isFolder && nodeB.isFolder) return 1;

                return nodeA.name.compareToIgnoreCase(nodeB.name);
            });

            node.removeAllChildren();
            for (DefaultMutableTreeNode child : children)
            {
                node.add(child);
            }
        }
    }

    private void calculateAllFolderStates(DefaultMutableTreeNode node)
    {
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            EmojiTreeNode childNode = (EmojiTreeNode) child.getUserObject();

            if (childNode.isFolder)
            {
                this.calculateAllFolderStates(child);
                childNode.isEnabled = this.calculateFolderState(child);
            }
        }
    }

    private String getPathString(TreePath path)
    {
        if (path == null) return null;

        StringBuilder pathBuilder = new StringBuilder();
        Object[] pathComponents = path.getPath();

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
                this.expandedFolderPaths.add(folderPath);

                for (int i = 0; i < node.getChildCount(); i++)
                {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                    this.storeAllFolderPaths(child, folderPath);
                }
            }
        }
        else
        {
            for (int i = 0; i < node.getChildCount(); i++)
            {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                this.storeAllFolderPaths(child, currentPath);
            }
        }
    }

    private void rebuildTreeWithCurrentState()
    {
        try
        {
            this.isRebuildingTree = true;

            this.buildEmojiTree();

            ((DefaultTreeModel) this.checkboxTree.getModel()).reload();

            this.restoreExpansionFromPaths();

            SwingUtilities.invokeLater(() ->
            {
                this.refreshTreeDisplay();
                this.revalidate();
                this.repaint();
            });
        }
        finally
        {
            this.isRebuildingTree = false;
        }
    }

    private void restoreExpansionFromPaths()
    {
        this.checkboxTree.setEnabled(false);

        try
        {
            this.expandFoldersFromStoredPaths(this.rootNode, "");
        }
        finally
        {
            this.checkboxTree.setEnabled(true);
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

                    if (this.expandedFolderPaths.contains(folderPath))
                    {
                        TreePath treePath = new TreePath(
                            ((DefaultTreeModel) this.checkboxTree.getModel()).getPathToRoot(child));
                        this.checkboxTree.expandPath(treePath);
                    }

                    this.expandFoldersFromStoredPaths(child, folderPath);
                }
            }
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
}
