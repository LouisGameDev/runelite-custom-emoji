package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

import javax.annotation.Nullable;
import java.awt.Rectangle;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Supplier;

import com.customemoji.model.Emoji;

@Slf4j
@Singleton
public class ChatSpacingManager
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private ChatScrollingManager chatScrollingManager;

    private Supplier<Map<Integer, Emoji>> emojiLookupSupplier;

    private final Map<Integer, List<Widget>> chatboxPositions = new HashMap<>();
    private final Map<Integer, List<Widget>> pmChatPositions = new HashMap<>();

    public void setEmojiLookupSupplier(Supplier<Map<Integer, Emoji>> supplier)
    {
        this.emojiLookupSupplier = supplier;
    }

    public void clearStoredPositions()
    {
        this.chatboxPositions.clear();
        this.pmChatPositions.clear();
    }

    public void applyChatSpacing()
    {
        int spacingAdjustment = this.config.chatMessageSpacing();
        boolean dynamicSpacing = this.config.dynamicEmojiSpacing();

        boolean noSpacingNeeded = spacingAdjustment == 0 && !dynamicSpacing;
        if (noSpacingNeeded)
        {
            return;
        }

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        this.processWidget(chatbox, this.chatboxPositions, spacingAdjustment, dynamicSpacing, false, true);

        boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;
        if (splitChatEnabled)
        {
            Widget pmChat = this.client.getWidget(InterfaceID.PmChat.CONTAINER);
            this.processWidget(pmChat, this.pmChatPositions, spacingAdjustment, dynamicSpacing, true, false);
        }
    }

    private void processWidget(Widget widget, Map<Integer, List<Widget>> positionMap, int spacing, boolean dynamic, boolean invert, boolean scrollable)
    {
        if (widget == null || widget.isHidden())
        {
            return;
        }

        Widget[] children = this.getCombinedChildren(widget);
        Rectangle bounds = this.adjustChildren(children, spacing, dynamic, invert, positionMap);

        if (scrollable)
        {
            this.chatScrollingManager.update(widget, bounds);
        }
    }

    private Widget[] getCombinedChildren(Widget parent)
    {
        Widget[] dynamicChildren = this.getChildren(parent::getDynamicChildren);
        Widget[] staticChildren = this.getChildren(parent::getStaticChildren);

        if (dynamicChildren == null)
        {
            dynamicChildren = new Widget[0];
        }
        if (staticChildren == null)
        {
            staticChildren = new Widget[0];
        }

        Widget[] allChildren = new Widget[dynamicChildren.length + staticChildren.length];
        System.arraycopy(dynamicChildren, 0, allChildren, 0, dynamicChildren.length);
        System.arraycopy(staticChildren, 0, allChildren, dynamicChildren.length, staticChildren.length);
        return allChildren;
    }


    @Nullable
    private Rectangle adjustChildren(Widget[] children, int spacingAdjustment, boolean dynamicSpacing, boolean invertSpacing, Map<Integer, List<Widget>> positionMap)
    {
        if (children == null)
        {
            return null;
        }

        boolean anyAdjustmentPerformed = false;

        // Sort the array so that we adjust them in the proper order. The parent widget
        // has them in the proper order, but split by static or dynamic widget category.
        Widget[] sortedChildren = this.sortByYPosition(children, positionMap);

        // Reverse the children array so last becomes first.
        // This makes it simpler to adjust the positions of every widget.
        // We start at the oldest message at the very top and work our way down
        Widget[] reversedSortedChildren = ChatSpacingManager.reverseArrayOrder(sortedChildren);

        // Group widgets by their original Y position
        Map<Integer, List<Widget>> widgetsByOriginalY = new HashMap<>();

        for (Widget child : reversedSortedChildren)
        {
            if (child == null || child.isHidden())
            {
                continue;
            }

            int storedY = this.resolveOriginalYPosition(child, positionMap);
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

        IndexedSprite[] modIcons = dynamicSpacing ? this.client.getModIcons() : null;
        EmojiPositionCalculator.DimensionLookup dimensionLookup = dynamicSpacing
            ? imageId -> PluginUtils.getEmojiDimension(modIcons, imageId)
            : null;

        Set<Integer> customEmojiIds = Collections.emptySet();
        if (dynamicSpacing && this.emojiLookupSupplier != null)
        {
            customEmojiIds = this.emojiLookupSupplier.get().keySet();
        }

        int cumulativeEmojiSpacing = 0;
        int counter = 0;
        for (Integer originalYPos : sortedOriginalYs)
        {
            List<Widget> widgetsAtThisY = widgetsByOriginalY.get(originalYPos);

            // Calculate spacing needed for this row based on emoji positions
            int aboveSpacing = 0;
            int belowSpacing = 0;
            if (dynamicSpacing)
            {
                for (Widget child : widgetsAtThisY)
                {
                    EmojiPositionCalculator.SpacingInfo spacing = EmojiPositionCalculator.calculateSpacingForWidget(child, dimensionLookup, customEmojiIds);
                    aboveSpacing = Math.max(aboveSpacing, spacing.aboveSpacing);
                    belowSpacing = Math.max(belowSpacing, spacing.belowSpacing);
                }

                // Add extra spacing for THIS row if it has tall emojis on the first line
                // (first-line emoji extend upward, so push this row down)
                cumulativeEmojiSpacing += invertSpacing ? belowSpacing : aboveSpacing;
            }

            int newY = originalYPos + (counter * spacingAdjustment) + cumulativeEmojiSpacing;

            // Apply the same Y position and settings to all widgets at the same original y position
            // This is done so that all elements line up with each other.
            for (Widget child : widgetsAtThisY)
            {
                boolean needsAdjustment = child.getOriginalY() != newY;
                if (needsAdjustment)
                {
                    child.setOriginalY(newY);
                    child.revalidate();
                    anyAdjustmentPerformed = true;
                }

                // Update bounding rectangle
                int childX = child.getOriginalX();
                int childY = newY;
                int childRight = childX + child.getOriginalWidth();
                int childBottom = childY + child.getOriginalHeight();

                // Add extra height for wrapped line emoji that extend beyond widget bounds
                childBottom += belowSpacing;

                minX = Math.min(minX, childX);
                minY = Math.min(minY, childY);
                maxX = Math.max(maxX, childRight);
                maxY = Math.max(maxY, childBottom);

                if (counter == 0)
                {
                    lastLineHeight = Math.max(lastLineHeight, child.getOriginalHeight());
                }
            }

            // Add extra spacing for tall emojis on wrapped lines
            // (wrapped-line emoji extend downward, so push subsequent rows down)
            if (dynamicSpacing)
            {
                cumulativeEmojiSpacing += invertSpacing ? aboveSpacing : belowSpacing;
            }

            counter++;
        }

        boolean hasWidgets = minX != Integer.MAX_VALUE;
        if (!hasWidgets)
        {
            return null;
        }

        if (!anyAdjustmentPerformed)
        {
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private Widget[] sortByYPosition(Widget[] array, Map<Integer, List<Widget>> positionMap)
    {
        // Sort by stored Y position
        Arrays.sort(array, (w1, w2) -> {
            if (w1 == null && w2 == null) return 0;
            if (w1 == null) return 1;
            if (w2 == null) return -1;

            // Find the stored Y position for each widget
            Integer storedY1 = this.getStoredYPosition(w1, positionMap);
            Integer storedY2 = this.getStoredYPosition(w2, positionMap);

            // Use stored position if available, otherwise use current position
            int y1 = storedY1 != null ? storedY1 : w1.getOriginalY();
            int y2 = storedY2 != null ? storedY2 : w2.getOriginalY();

            return Integer.compare(y1, y2);
        });

        return array;
    }

    private Integer getStoredYPosition(Widget widget, Map<Integer, List<Widget>> positionMap)
    {
        for (Map.Entry<Integer, List<Widget>> entry : positionMap.entrySet())
        {
            if (entry.getValue().contains(widget))
            {
                return entry.getKey();
            }
        }
        return null;
    }

    private int resolveOriginalYPosition(Widget widget, Map<Integer, List<Widget>> positionMap)
    {
        int currentY = widget.getOriginalY();

        Integer storedY = this.getStoredYPosition(widget, positionMap);
        if (storedY != null && storedY != currentY)
        {
            return storedY;
        }

        boolean isAlreadyStored = positionMap.values().stream()
            .anyMatch(widgets -> widgets.contains(widget));

        if (!isAlreadyStored)
        {
            positionMap
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
        Widget[] children = childrenSupplier.get();
        if (children == null)
        {
            return new Widget[0];
        }

        List<Widget> result = new ArrayList<>();
        for (Widget child : children)
        {
            int height = child.getHeight();

            if (height == 0)
            {
                continue;
            }

            result.add(child);
        }

        return result.toArray(new Widget[0]);
    }
}