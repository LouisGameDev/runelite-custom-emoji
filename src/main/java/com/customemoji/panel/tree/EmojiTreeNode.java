package com.customemoji.panel.tree;

import java.awt.image.BufferedImage;

/**
 * Represents a node in the emoji tree structure.
 * Can be either a folder or an individual emoji.
 */
public class EmojiTreeNode
{
    private final String name;
    private final boolean isFolder;
    private boolean isEnabled;
    private boolean resizingEnabled;
    private BufferedImage image;
    private final boolean failedToLoad;

    private EmojiTreeNode(String name, boolean isFolder, boolean isEnabled,
                          boolean resizingEnabled, BufferedImage image, boolean failedToLoad)
    {
        this.name = name;
        this.isFolder = isFolder;
        this.isEnabled = isEnabled;
        this.resizingEnabled = resizingEnabled;
        this.image = image;
        this.failedToLoad = failedToLoad;
    }

    public static EmojiTreeNode createFolder(String name, boolean enabled, boolean resizingEnabled)
    {
        return new EmojiTreeNode(name, true, enabled, resizingEnabled, null, false);
    }

    public static EmojiTreeNode createEmoji(String name, boolean enabled, boolean resizingEnabled,
                                            BufferedImage image, boolean failedToLoad)
    {
        return new EmojiTreeNode(name, false, enabled, resizingEnabled, image, failedToLoad);
    }

    public String getName()
    {
        return this.name;
    }

    public boolean isFolder()
    {
        return this.isFolder;
    }

    public boolean isEnabled()
    {
        return this.isEnabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.isEnabled = enabled;
    }

    public boolean isResizingEnabled()
    {
        return this.resizingEnabled;
    }

    public void setResizingEnabled(boolean resizingEnabled)
    {
        this.resizingEnabled = resizingEnabled;
    }

    public BufferedImage getImage()
    {
        return this.image;
    }

    public void setImage(BufferedImage image)
    {
        this.image = image;
    }

    public boolean isFailedToLoad()
    {
        return this.failedToLoad;
    }

    @Override
    public String toString()
    {
        return this.name;
    }
}
