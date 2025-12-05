package com.customemoji.panel.tree;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages navigation state and header updates for the emoji tree panel.
 */
public class NavigationController
{
    private static final String PATH_SEPARATOR = "/";

    private final JButton backButton;
    private final JLabel pathLabel;
    private final Runnable onNavigationChanged;

    private List<String> currentPath = new ArrayList<>();
    private String currentSearchFilter = "";

    public NavigationController(JButton backButton, JLabel pathLabel, Runnable onNavigationChanged)
    {
        this.backButton = backButton;
        this.pathLabel = pathLabel;
        this.onNavigationChanged = onNavigationChanged;
    }

    public void navigateToFolder(List<String> path)
    {
        this.currentPath = new ArrayList<>(path);
        this.updateHeader();
        this.onNavigationChanged.run();
    }

    public void navigateBack()
    {
        if (!this.currentPath.isEmpty())
        {
            this.currentPath.remove(this.currentPath.size() - 1);
            this.updateHeader();
            this.onNavigationChanged.run();
        }
    }

    public void navigateToRoot()
    {
        this.currentPath.clear();
        this.updateHeader();
        this.onNavigationChanged.run();
    }

    public void updateHeader()
    {
        boolean isSearching = !this.currentSearchFilter.isEmpty();
        boolean isAtRoot = this.currentPath.isEmpty();
        this.backButton.setEnabled(!isAtRoot && !isSearching);

        if (isSearching)
        {
            this.pathLabel.setText("Search results");
        }
        else if (isAtRoot)
        {
            this.pathLabel.setText("Root");
        }
        else
        {
            String currentFolder = this.currentPath.get(this.currentPath.size() - 1);
            this.pathLabel.setText(currentFolder);
        }
    }

    public String getCurrentFolderPath()
    {
        return String.join(PATH_SEPARATOR, this.currentPath);
    }

    public List<String> getCurrentPath()
    {
        return new ArrayList<>(this.currentPath);
    }

    public void setSearchFilter(String filter)
    {
        this.currentSearchFilter = filter;
        this.updateHeader();
    }

    public String getSearchFilter()
    {
        return this.currentSearchFilter;
    }

    public boolean isSearching()
    {
        return !this.currentSearchFilter.isEmpty();
    }

    public boolean pathExists(java.util.Map<String, ?> folderContents)
    {
        if (this.currentPath.isEmpty())
        {
            return true;
        }
        String pathKey = String.join(PATH_SEPARATOR, this.currentPath);
        return folderContents.containsKey(pathKey);
    }

    public void resetPathIfInvalid(java.util.Map<String, ?> folderContents)
    {
        if (!this.currentPath.isEmpty() && !this.pathExists(folderContents))
        {
            this.currentPath.clear();
        }
    }
}
