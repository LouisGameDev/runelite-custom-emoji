package com.customemoji.Panel;

import java.awt.image.BufferedImage;

/**
 * Represents a node in the emoji tree structure.
 * Can be either a folder or an individual emoji.
 */
public class EmojiTreeNode
{
    public final String name;
    public final boolean isFolder;
    public boolean isEnabled;
    public final BufferedImage image;
    public final boolean failedToLoad;

    public EmojiTreeNode(String name, boolean isFolder, boolean isEnabled, BufferedImage image)
    {
        this(name, isFolder, isEnabled, image, false);
    }

    public EmojiTreeNode(String name, boolean isFolder, boolean isEnabled, BufferedImage image, boolean failedToLoad)
    {
        this.name = name;
        this.isFolder = isFolder;
        this.isEnabled = isEnabled;
        this.image = image;
        this.failedToLoad = failedToLoad;
    }

    @Override
    public String toString()
    {
        return name;
    }
}