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

    private EmojiTreeNode(Builder builder)
    {
        this.name = builder.name;
        this.isFolder = builder.isFolder;
        this.isEnabled = builder.isEnabled;
        this.image = builder.image;
        this.failedToLoad = builder.failedToLoad;
    }

    public static Builder builder(String name)
    {
        return new Builder(name);
    }

    @Override
    public String toString()
    {
        return name;
    }

    public static class Builder
    {
        private final String name;
        private boolean isFolder = false;
        private boolean isEnabled = true;
        private BufferedImage image = null;
        private boolean failedToLoad = false;

        private Builder(String name)
        {
            this.name = name;
        }

        public Builder folder(boolean isFolder)
        {
            this.isFolder = isFolder;
            return this;
        }

        public Builder enabled(boolean isEnabled)
        {
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder image(BufferedImage image)
        {
            this.image = image;
            return this;
        }

        public Builder failedToLoad(boolean failedToLoad)
        {
            this.failedToLoad = failedToLoad;
            return this;
        }

        public EmojiTreeNode build()
        {
            return new EmojiTreeNode(this);
        }
    }
}