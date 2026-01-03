package com.customemoji;

import java.awt.Rectangle;

/**
 * Represents the calculated position and bounds of an emoji within a chat widget.
 */
public class EmojiPosition
{
    private final int imageId;
    private final Rectangle bounds;
    private final int lineNumber;

    public EmojiPosition(int imageId, Rectangle bounds, int lineNumber)
    {
        this.imageId = imageId;
        this.bounds = bounds;
        this.lineNumber = lineNumber;
    }

    public int getImageId()
    {
        return this.imageId;
    }

    public Rectangle getBounds()
    {
        return this.bounds;
    }

    public int getX()
    {
        return this.bounds.x;
    }

    public int getY()
    {
        return this.bounds.y;
    }

    public int getWidth()
    {
        return this.bounds.width;
    }

    public int getHeight()
    {
        return this.bounds.height;
    }

    public boolean containsPoint(int x, int y)
    {
        return this.bounds.contains(x, y);
    }

    public int getLineNumber()
    {
        return this.lineNumber;
    }
}