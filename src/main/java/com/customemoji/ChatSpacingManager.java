package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Singleton
public class ChatSpacingManager
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    private final Map<Integer, List<Widget>> originalChatPositions = new HashMap<>();
    private final int LAST_MESSAGE_PADDING = 4;
    private int scrolledUpPixels = 0;

    public void clearStoredPositions()
    {
        this.originalChatPositions.clear();
    }

    public void captureScrollPosition()
    {
        Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            return;
        }

        int scrollY = chatbox.getScrollY();
        int scrollHeight = chatbox.getScrollHeight();
        int visibleHeight = chatbox.getHeight();

        if (scrollHeight <= visibleHeight) // Cant scroll if there aren't enough messages
        {
            this.scrolledUpPixels = 0;
            return;
        }

        // Calculate how far up from the bottom the user has scrolled (in pixels)
        int newValue = scrollHeight - (visibleHeight + scrollY);

        if (newValue == this.scrolledUpPixels)
        {
            return;
        }

        this.scrolledUpPixels = newValue;
        log.debug("Captured scroll position: {} pixels from bottom", this.scrolledUpPixels);
    }

    public void applyChatSpacing()
    {
        int spacingAdjustment = config.chatMessageSpacing();

        if (spacingAdjustment == 0)
        {
            return; // Setting is essentially disabled
        }

        Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden())
        {
            return;
        }

        Widget[] dynamicChildren = this.getChildren(chatbox::getDynamicChildren);

        // Handle null arrays
        if (dynamicChildren == null) dynamicChildren = new Widget[0];

        Rectangle bounds = this.adjustChildren(dynamicChildren, spacingAdjustment);

        this.updateChatBox(chatbox, bounds);
    }

    private void updateChatBox(Widget chatbox, Rectangle bounds)
    {
        if (bounds == null)
        {
            return;
        }

        int visibleHeight = chatbox.getHeight();

        if (visibleHeight > bounds.height)
        {
            return;
        }

        // Calculate new scroll height based on the bounds of all widgets
        int newScrollHeight = bounds.height + LAST_MESSAGE_PADDING;

        // Update the scroll height
        chatbox.setScrollHeight(newScrollHeight);

        // Restore scroll position based on how many lines the user was scrolled up from bottom
        boolean atBottom = this.scrolledUpPixels == 0.0;

        float scrolledUpPixelsLocal = atBottom ? this.scrolledUpPixels : this.scrolledUpPixels + this.config.chatMessageSpacing();

        int newScrollY = (int) (newScrollHeight - visibleHeight - scrolledUpPixelsLocal);
        newScrollY = Math.max(0, newScrollY);

        chatbox.revalidateScroll();

        this.client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, newScrollY);

        this.captureScrollPosition();
    }

    @Nullable
    private Rectangle adjustChildren(Widget[] children, int spacingAdjustment)
    {
        if (children == null)
        {
            return null;
        }

        // Sort the array so that we adjust them in the proper order. The parent widget
        // has them in the proper order, but split by static or dynamic widget category.
        Widget[] sortedChildren = this.sortByYPosition(children);

        // Reverse the children array so last becomes first.
        // This makes it simpler to adjust the positions of every widget.
        // We start at the oldest message at the very top and work our way down
        Widget[] reversedSortedChildren = reverseArrayOrder(sortedChildren);

        // Group widgets by their original Y position
        Map<Integer, List<Widget>> widgetsByOriginalY = new HashMap<>();

        for (Widget child : reversedSortedChildren)
        {
            if (child == null || child.isHidden())
            {
                continue;
            }

            int storedY = this.resolveOriginalYPosition(child);
            widgetsByOriginalY.computeIfAbsent(storedY, k -> new ArrayList<>()).add(child);
        }

        // Sort the original Y positions and apply spacing to each group
        List<Integer> sortedOriginalYs = new ArrayList<>(widgetsByOriginalY.keySet());
        sortedOriginalYs.sort(Integer::compareTo);

        // Track bounds for the bounding rectangle
        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;

        int lastLineHeight = 0;

        int counter = 0;
        for (Integer originalYPos : sortedOriginalYs)
        {
            List<Widget> widgetsAtThisY = widgetsByOriginalY.get(originalYPos);
            int newY = originalYPos + (counter * spacingAdjustment);
            
            // Apply the same Y position and settings to all widgets at the same original y position
            // This is done so that all elements line up with each other.
            for (Widget child : widgetsAtThisY)
            {
                child.setOriginalY(newY);
                child.revalidate();

                // Update bounding rectangle
                int childX = child.getOriginalX();
                int childY = child.getOriginalY();
                int childRight = childX + child.getOriginalWidth();
                int childBottom = childY + child.getOriginalHeight();

                minX = Math.min(minX, childX);
                minY = Math.min(minY, childY);
                maxX = Math.max(maxX, childRight);
                maxY = Math.max(maxY, childBottom);

                if (counter == 0)
                {
                    lastLineHeight = Math.max(lastLineHeight, child.getOriginalHeight());
                }
                
            }

            counter++;
        }

        boolean hasWidgets = minX != Integer.MAX_VALUE;
        if (!hasWidgets)
        {
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private Widget[] sortByYPosition(Widget[] array)
    {
        // Sort by stored Y position
        Arrays.sort(array, (w1, w2) -> {
            if (w1 == null && w2 == null) return 0;
            if (w1 == null) return 1;
            if (w2 == null) return -1;
            
            // Find the stored Y position for each widget by searching through originalChatPositions
            Integer storedY1 = getStoredYPosition(w1);
            Integer storedY2 = getStoredYPosition(w2);
            
            // Use stored position if available, otherwise use current position
            int y1 = storedY1 != null ? storedY1 : w1.getOriginalY();
            int y2 = storedY2 != null ? storedY2 : w2.getOriginalY();
            
            return Integer.compare(y1, y2);
        });

        return array;
    }
    
    private Integer getStoredYPosition(Widget widget)
    {
        // Search through originalChatPositions to find which Y position this widget belongs to
        for (Map.Entry<Integer, List<Widget>> entry : this.originalChatPositions.entrySet())
        {
            if (entry.getValue().contains(widget))
            {
                return entry.getKey();
            }
        }
        // If not found in stored positions, return null
        return null;
    }

    private int resolveOriginalYPosition(Widget widget)
    {
        int currentY = widget.getOriginalY();

        Integer storedY = this.getStoredYPosition(widget);
        if (storedY != null && storedY != currentY)
        {
            return storedY;
        }

        boolean isAlreadyStored = this.originalChatPositions.values().stream()
            .anyMatch(widgets -> widgets.contains(widget));

        if (!isAlreadyStored)
        {
            this.originalChatPositions
                .computeIfAbsent(currentY, k -> new ArrayList<>())
                .add(widget);
        }

        return currentY;
    }

    private static Widget[] reverseArrayOrder(Widget[] array)
    {
        int len = array.length;
        Widget[] result = new Widget[len];
        
        for (int i = 0; i < len; i++)
        {
            result[i] = array[len - 1 - i];
        }

        return result;
    }

    private Widget[] getChildren(Supplier<Widget[]> childrenSupplier)
    {
        List<Widget> result = new ArrayList<Widget>();
        for (Widget child : childrenSupplier.get()) 
        {
            result.add(child);

            int yPosition = child.getOriginalY();
            int height = child.getOriginalHeight();
            if (child.getOriginalY() == 0 || yPosition < height)
            {
                break;
            }
        }
        return result.toArray(new Widget[0]);
    }
}