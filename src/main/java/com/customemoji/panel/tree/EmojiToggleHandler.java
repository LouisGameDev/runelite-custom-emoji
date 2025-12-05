package com.customemoji.panel.tree;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Handles toggle operations for enabling/disabling emojis and resizing mode.
 */
public class EmojiToggleHandler
{
    public static final String PATH_SEPARATOR = "/";
    public static final String MODE_ENABLE_DISABLE = "Enable/Disable";
    public static final String MODE_RESIZING = "Resizing";

    private final Set<String> disabledEmojis;
    private final Set<String> resizingDisabledEmojis;
    private final ScheduledExecutorService executor;
    private final Runnable onContentRefreshNeeded;
    private final Runnable onFolderStatesUpdateNeeded;
    private final JScrollPane scrollPane;
    private final JPanel contentPanel;

    private Consumer<Set<String>> onDisabledEmojisChanged;
    private Consumer<Set<String>> onResizingDisabledEmojisChanged;
    private Consumer<String> onEmojiResizingToggled;

    private String currentMode = MODE_ENABLE_DISABLE;
    private boolean isLoading = false;

    private Map<String, List<EmojiTreeNode>> folderContents;

    public EmojiToggleHandler(Set<String> disabledEmojis,
                               Set<String> resizingDisabledEmojis,
                               ScheduledExecutorService executor,
                               JScrollPane scrollPane,
                               JPanel contentPanel,
                               Runnable onContentRefreshNeeded,
                               Runnable onFolderStatesUpdateNeeded)
    {
        this.disabledEmojis = disabledEmojis;
        this.resizingDisabledEmojis = resizingDisabledEmojis;
        this.executor = executor;
        this.scrollPane = scrollPane;
        this.contentPanel = contentPanel;
        this.onContentRefreshNeeded = onContentRefreshNeeded;
        this.onFolderStatesUpdateNeeded = onFolderStatesUpdateNeeded;
    }

    public void setFolderContents(Map<String, List<EmojiTreeNode>> folderContents)
    {
        this.folderContents = folderContents;
    }

    public void setOnDisabledEmojisChanged(Consumer<Set<String>> callback)
    {
        this.onDisabledEmojisChanged = callback;
    }

    public void setOnResizingDisabledEmojisChanged(Consumer<Set<String>> callback)
    {
        this.onResizingDisabledEmojisChanged = callback;
    }

    public void setOnEmojiResizingToggled(Consumer<String> callback)
    {
        this.onEmojiResizingToggled = callback;
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
            this.toggleFolderEmojisEnabled(targetPath, enabled);

            this.onFolderStatesUpdateNeeded.run();
            int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
            this.onContentRefreshNeeded.run();
            SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
        }
        else
        {
            if (enabled)
            {
                this.disabledEmojis.remove(item.getName());
            }
            else
            {
                this.disabledEmojis.add(item.getName());
            }
            this.onFolderStatesUpdateNeeded.run();
            int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
            this.onContentRefreshNeeded.run();
            SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
        }

        this.notifyDisabledEmojisChanged();
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

        if (resizingEnabled)
        {
            this.resizingDisabledEmojis.remove(item.getName());
        }
        else
        {
            this.resizingDisabledEmojis.add(item.getName());
        }

        this.notifyResizingDisabledEmojisChanged();

        this.executor.execute(() -> {
            this.notifyEmojiResizingToggled(item.getName());

            SwingUtilities.invokeLater(() -> {
                this.isLoading = false;
                this.onContentRefreshNeeded.run();
                SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(scrollPosition));
            });
        });
    }

    private void handleFolderResizingToggle(EmojiTreeNode item, boolean resizingEnabled, String currentFolderPath)
    {
        int scrollPosition = this.scrollPane.getVerticalScrollBar().getValue();
        this.disableAllButtons();

        String targetPath = currentFolderPath.isEmpty() ? item.getName() : currentFolderPath + PATH_SEPARATOR + item.getName();
        List<String> emojisToReload = this.toggleFolderEmojisResizing(targetPath, resizingEnabled);

        this.notifyResizingDisabledEmojisChanged();

        this.executor.execute(() -> {
            for (String emojiName : emojisToReload)
            {
                this.notifyEmojiResizingToggled(emojiName);
            }

            SwingUtilities.invokeLater(() -> {
                this.isLoading = false;
                this.onFolderStatesUpdateNeeded.run();
                this.onContentRefreshNeeded.run();
                this.scrollPane.getVerticalScrollBar().setValue(scrollPosition);
            });
        });
    }

    private void toggleFolderEmojisEnabled(String folderPath, boolean enabled)
    {
        for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
        {
            boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
            if (isInFolder)
            {
                this.toggleEmojisEnabledInList(entry.getValue(), enabled);
            }
        }
    }

    private List<String> toggleFolderEmojisResizing(String folderPath, boolean resizingEnabled)
    {
        List<String> toggledEmojis = new ArrayList<>();
        for (Map.Entry<String, List<EmojiTreeNode>> entry : this.folderContents.entrySet())
        {
            boolean isInFolder = this.isPathInFolder(entry.getKey(), folderPath);
            if (isInFolder)
            {
                List<String> toggled = this.toggleEmojisResizingInList(entry.getValue(), resizingEnabled);
                toggledEmojis.addAll(toggled);
            }
        }
        return toggledEmojis;
    }

    private boolean isPathInFolder(String path, String folderPath)
    {
        boolean isExactMatch = path.equals(folderPath);
        boolean isSubfolder = path.startsWith(folderPath + PATH_SEPARATOR);
        return isExactMatch || isSubfolder;
    }

    private void toggleEmojisEnabledInList(List<EmojiTreeNode> items, boolean enabled)
    {
        for (EmojiTreeNode item : items)
        {
            if (!item.isFolder())
            {
                this.updateEmojiEnabledState(item, enabled);
            }
        }
    }

    private List<String> toggleEmojisResizingInList(List<EmojiTreeNode> items, boolean resizingEnabled)
    {
        List<String> toggledEmojis = new ArrayList<>();
        for (EmojiTreeNode item : items)
        {
            boolean isEnabledEmoji = !item.isFolder() && item.isEnabled();
            if (isEnabledEmoji)
            {
                this.updateEmojiResizingState(item, resizingEnabled);
                toggledEmojis.add(item.getName());
            }
        }
        return toggledEmojis;
    }

    private void updateEmojiEnabledState(EmojiTreeNode item, boolean enabled)
    {
        item.setEnabled(enabled);
        if (enabled)
        {
            this.disabledEmojis.remove(item.getName());
        }
        else
        {
            this.disabledEmojis.add(item.getName());
        }
    }

    private void updateEmojiResizingState(EmojiTreeNode item, boolean resizingEnabled)
    {
        item.setResizingEnabled(resizingEnabled);
        if (resizingEnabled)
        {
            this.resizingDisabledEmojis.remove(item.getName());
        }
        else
        {
            this.resizingDisabledEmojis.add(item.getName());
        }
    }

    private void notifyDisabledEmojisChanged()
    {
        if (this.onDisabledEmojisChanged != null)
        {
            this.onDisabledEmojisChanged.accept(new HashSet<>(this.disabledEmojis));
        }
    }

    private void notifyResizingDisabledEmojisChanged()
    {
        if (this.onResizingDisabledEmojisChanged != null)
        {
            this.onResizingDisabledEmojisChanged.accept(new HashSet<>(this.resizingDisabledEmojis));
        }
    }

    private void notifyEmojiResizingToggled(String emojiName)
    {
        if (this.onEmojiResizingToggled != null)
        {
            this.onEmojiResizingToggled.accept(emojiName);
        }
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
