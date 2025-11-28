package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Singleton
public class ChatSpacingManager
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    private final Map<Integer, List<Widget>> originalChatPositions = new HashMap<>();
    private Integer originalScrollAreaHeight = null;

    public void clearStoredPositions()
    {
        originalChatPositions.clear();
        originalScrollAreaHeight = null;
    }

    public void applyChatSpacing()
    {
        int spacingAdjustment = config.chatMessageSpacing();

        Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden())
        {
            return;
        }

        if (originalScrollAreaHeight == null)
        {
            originalScrollAreaHeight = chatbox.getScrollHeight();
        }

        // Store current scroll state to preserve relative position
        int currentScrollY = chatbox.getScrollY();
        int currentScrollHeight = chatbox.getScrollHeight();
        int visibleHeight = chatbox.getHeight();
        
        // Calculate scroll position relative to bottom (more intuitive for chat)
        boolean wasAtBottom = (currentScrollY + visibleHeight >= currentScrollHeight - 5); // 5px tolerance
        double scrollPercentage = currentScrollHeight > 0 ? (double) (currentScrollY - visibleHeight) / currentScrollHeight : 0;

        Widget[] dynamicChildren = chatbox.getDynamicChildren();
        Widget[] staticChildren = chatbox.getStaticChildren();

        // Handle null arrays
        if (dynamicChildren == null) dynamicChildren = new Widget[0];
        if (staticChildren == null) staticChildren = new Widget[0];

        // Merge the arrays
        Widget[] allChildren = new Widget[dynamicChildren.length + staticChildren.length];
        System.arraycopy(dynamicChildren, 0, allChildren, 0, dynamicChildren.length);
        System.arraycopy(staticChildren, 0, allChildren, dynamicChildren.length, staticChildren.length);

        adjustChildren(allChildren, spacingAdjustment);

        // Calculate new scroll height based on all content
        int maxY = 0;
        int maxHeight = 0;
        for (Widget child : allChildren)
        {
            if (child != null && !child.isHidden())
            {
                int childBottom = child.getOriginalY() + child.getOriginalHeight();
                if (childBottom > maxY + maxHeight)
                {
                    maxY = child.getOriginalY();
                    maxHeight = child.getOriginalHeight();
                }
            }
        }
        
        Widget firstWidget = chatbox.getStaticChildren()[0];

        if (firstWidget == null)
        {
            return;
        }
        int newScrollHeight = firstWidget.getOriginalY() + firstWidget.getHeight() + 2;
        chatbox.setScrollHeight(newScrollHeight);
        
        // Restore scroll position intelligently
        if (wasAtBottom)
        {
            // Keep user at bottom if they were at bottom before
            int newScrollY = Math.max(0, newScrollHeight - visibleHeight);
            chatbox.setScrollY(newScrollY);
        }
        else
        {
            // Try to maintain relative scroll position
            int newScrollY = (int) (scrollPercentage * newScrollHeight);
            newScrollY = Math.max(0, Math.min(newScrollY, newScrollHeight - visibleHeight));
            chatbox.setScrollY(newScrollY);
        }
    }

    private void adjustChildren(Widget[] children, int spacingAdjustment)
    {
        if (children == null)
        {
            return;
        }

        // Sort the array so that we adjust them in the proper order. The parent widget 
        // has them in the proper order, but split by static or dynamic widget category.
        Widget[] sortedChildren = sortByYPosition(children);

        // Reverse the children array so last becomes first. 
        // This makes it simpler to adjust the positions of every widget.
        // We start at the oldest message at the very top and work our way down
        Widget[] reversedSortedChildren = reverseArrayOrder(sortedChildren);

        // Group widgets by their original Y position
        Map<Integer, List<Widget>> widgetsByOriginalY = new HashMap<>();
        
        for (int i = 0; i < reversedSortedChildren.length; i++)
        {
            Widget child = reversedSortedChildren[i];

            if (child.isHidden() || child == null)
            {
                continue;
            }

            int currentY = child.getOriginalY();
            int storedY;
            
            // Check if we have a stored position for this widget
            Integer foundStoredY = getStoredYPosition(child);
            if (foundStoredY != null && foundStoredY != currentY)
            {
                // Widget already has a stored position, use it
                storedY = foundStoredY;
            }
            else
            {
                // New widget or position hasn't been stored yet
                // For new widgets, we need to figure out their TRUE original position
                // by removing any spacing that might have been applied
                
                // Check if this widget is already stored
                boolean isAlreadyStored = false;
                for (List<Widget> widgets : originalChatPositions.values())
                {
                    if (widgets.contains(child))
                    {
                        isAlreadyStored = true;
                        break;
                    }
                }
                
                if (!isAlreadyStored)
                {
                    // This is a new widget, store it at current position
                    // (which should be the original position if it's truly new)
                    storedY = currentY;
                    
                    // Add to stored positions
                    originalChatPositions.computeIfAbsent(storedY, k -> new ArrayList<>()).add(child);
                }
                else
                {
                    // Widget is stored but we couldn't find it (shouldn't happen)
                    storedY = currentY;
                }
            }

            // Use stored Y position for grouping
            widgetsByOriginalY.computeIfAbsent(storedY, k -> new ArrayList<>()).add(child);
        }

        // Sort the original Y positions and apply spacing to each group
        List<Integer> sortedOriginalYs = new ArrayList<>(widgetsByOriginalY.keySet());
        sortedOriginalYs.sort(Integer::compareTo);
        
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
            }
            
            counter++;
        }
        
        return;
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
        for (Map.Entry<Integer, List<Widget>> entry : originalChatPositions.entrySet())
        {
            if (entry.getValue().contains(widget))
            {
                return entry.getKey();
            }
        }
        // If not found in stored positions, return null
        return null;
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
}