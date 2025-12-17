package com.customemoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;

import org.junit.Test;

import com.customemoji.EmojiPosition;

public class EmojiPositionTest
{
    @Test
    public void containsPoint_pointInside_returnsTrue()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertTrue(position.containsPoint(15, 15));
    }

    @Test
    public void containsPoint_pointOutside_returnsFalse()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertFalse(position.containsPoint(5, 5));
    }

    @Test
    public void containsPoint_pointOnLeftEdge_returnsTrue()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertTrue(position.containsPoint(10, 15));
    }

    @Test
    public void containsPoint_pointOnTopEdge_returnsTrue()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertTrue(position.containsPoint(15, 10));
    }

    @Test
    public void containsPoint_pointOnRightEdge_returnsFalse()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertFalse(position.containsPoint(30, 15));
    }

    @Test
    public void containsPoint_pointOnBottomEdge_returnsFalse()
    {
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertFalse(position.containsPoint(15, 30));
    }

    @Test
    public void getImageId_returnsCorrectId()
    {
        Rectangle bounds = new Rectangle(0, 0, 10, 10);
        EmojiPosition position = new EmojiPosition(42, bounds);

        assertEquals(42, position.getImageId());
    }

    @Test
    public void getX_returnsCorrectX()
    {
        Rectangle bounds = new Rectangle(100, 200, 10, 10);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertEquals(100, position.getX());
    }

    @Test
    public void getY_returnsCorrectY()
    {
        Rectangle bounds = new Rectangle(100, 200, 10, 10);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertEquals(200, position.getY());
    }

    @Test
    public void getWidth_returnsCorrectWidth()
    {
        Rectangle bounds = new Rectangle(0, 0, 50, 30);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertEquals(50, position.getWidth());
    }

    @Test
    public void getHeight_returnsCorrectHeight()
    {
        Rectangle bounds = new Rectangle(0, 0, 50, 30);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertEquals(30, position.getHeight());
    }

    @Test
    public void getBounds_returnsSameRectangle()
    {
        Rectangle bounds = new Rectangle(10, 20, 30, 40);
        EmojiPosition position = new EmojiPosition(1, bounds);

        assertEquals(bounds, position.getBounds());
    }
}
