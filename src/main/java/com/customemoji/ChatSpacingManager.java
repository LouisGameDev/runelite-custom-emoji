package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

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

    private final List<Rectangle> appliedChatboxYBounds = new ArrayList<>();
    private final List<Rectangle> appliedPmChatYBounds = new ArrayList<>();

    private long lastExecutionTime = 0;

    public void setEmojiLookupSupplier(Supplier<Map<Integer, Emoji>> supplier)
    {
        this.emojiLookupSupplier = supplier;
    }

    public void clearStoredPositions()
    {
        this.appliedChatboxYBounds.clear();
        this.appliedPmChatYBounds.clear();
    }

    public void applyChatSpacing()
    {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastExecution = currentTime - this.lastExecutionTime;
        boolean rateLimitExceeded = timeSinceLastExecution < 50;
        if (rateLimitExceeded)
        {
            return;
        }

        this.lastExecutionTime = currentTime;

        int spacingAdjustment = this.config.chatMessageSpacing();
        boolean dynamicSpacing = this.config.dynamicEmojiSpacing();

        boolean noSpacingNeeded = spacingAdjustment == 0 && !dynamicSpacing;
        if (noSpacingNeeded)
        {
            return;
        }

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        this.processWidget(chatbox, this.appliedChatboxYBounds, spacingAdjustment, dynamicSpacing, false);

        /*boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;
        if (splitChatEnabled)
        {
            Widget pmChat = this.client.getWidget(InterfaceID.PmChat.CONTAINER);
            this.processWidget(pmChat, this.appliedPmChatYBounds, spacingAdjustment, dynamicSpacing, true);
        }*/
    }

    private void processWidget(Widget widget, List<Rectangle> appliedYBounds, int spacing, boolean dynamic, boolean invert)
    {
        if (widget == null || widget.isHidden())
        {
            return;
        }

        Widget[] children = this.getCombinedChildren(widget);
        int height = this.adjustChildren(children, appliedYBounds, spacing, dynamic, invert);

        if (height < widget.getHeight())
        {
            this.applyStaticYOffset(children, widget.getHeight() - height);
        }

        if (!invert)
        {
            this.chatScrollingManager.update(widget, height);
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

    private int adjustChildren(Widget[] children, List<Rectangle> appliedYBounds, int spacingAdjustment, boolean dynamicSpacing, boolean invert)
    {
        if (children == null)
        {
            return 0;
        }

        List<List<Widget>> messageList = this.groupWidgetsByOriginalYPosition(children);

        int startIndex = Math.max(0, messageList.size() - this.config.messageProcessLimit());
        int maxY = 0;

        if (startIndex > 0 && !messageList.isEmpty())
        {
            List<Widget> firstMessage = messageList.get(startIndex);
            if (!firstMessage.isEmpty())
            {
                Widget widget = firstMessage.get(0);
                maxY = widget.getOriginalY();
            }
        }

        for (int i = startIndex; i < messageList.size(); i++)
        {
            List<Widget> message = messageList.get(i);

            if (!message.isEmpty() && invert && maxY == 0)
            {
                Widget widget = message.get(0);
                int originalY = widget.getOriginalY();
                maxY = originalY;
            }

            Rectangle messageBounds = this.getMessageBounds(message, maxY, invert);

            for (Widget messagePart : message)
            {

                messagePart.setOriginalY(messageBounds.y);
                messagePart.revalidate();
            }

            maxY += messageBounds.height + spacingAdjustment;
        }

        return maxY;
    }
    
    private Rectangle getMessageBounds(List<Widget> messageWidgets, int startY, boolean invert)
    {
        boolean dynamicSpacing = this.config.dynamicEmojiSpacing();
        IndexedSprite[] modIcons = this.config.dynamicEmojiSpacing() ? this.client.getModIcons() : null;
        EmojiPositionCalculator.DimensionLookup dimensionLookup = dynamicSpacing
           ? imageId -> PluginUtils.getEmojiDimension(modIcons, imageId)
           : null;

        Set<Integer> customEmojiIds = Collections.emptySet();
        if (dynamicSpacing && this.emojiLookupSupplier != null)
        {
           customEmojiIds = this.emojiLookupSupplier.get().keySet();
        }

        int maxAboveSpacing = 0;
        int maxWidgetHeight = 0;
        int maxBelowSpacing = 0;
        int totalWidth = 0;

        for (Widget child : messageWidgets)
        {
            
            EmojiPositionCalculator.SpacingInfo spacing = EmojiPositionCalculator.calculateSpacingForWidget(child, dimensionLookup, customEmojiIds);
            maxAboveSpacing = Math.max(maxAboveSpacing, spacing.aboveSpacing);
            maxWidgetHeight = Math.max(maxWidgetHeight, child.getHeight());
            maxBelowSpacing = Math.max(maxBelowSpacing, spacing.belowSpacing);
            totalWidth += child.getWidth();
        }

        Rectangle bounds = new Rectangle();
        if (invert)
        {
            bounds.y = startY + maxBelowSpacing;
            bounds.height = maxAboveSpacing + maxWidgetHeight + maxBelowSpacing;
            bounds.width = totalWidth;
        }
        else
        {
            bounds.y = startY + maxAboveSpacing;
            bounds.height = maxAboveSpacing + maxWidgetHeight + maxBelowSpacing;
            bounds.width = totalWidth;
        }
        
        return bounds;
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
            result.add(child);
        }

        return result.toArray(new Widget[0]);
    }

    private List<List<Widget>> groupWidgetsByOriginalYPosition(Widget[] widgets)
    {
        if (widgets == null)
        {
            return new ArrayList<>();
        }

        Map<Integer, List<Widget>> tempMap = new TreeMap<>();

        for (Widget widget : widgets)
        {
            if (widget == null || 
                widget.isHidden() || 
                widget.getOriginalHeight() == 0 || 
                widget.getOriginalWidth() == 0)
            {
                continue;
            }

            int originalY = widget.getOriginalY();
            tempMap.computeIfAbsent(originalY, k -> new ArrayList<>()).add(widget);
        }

        return new ArrayList<>(tempMap.values());
    }
    
    private void applyStaticYOffset(Widget[] children, int offset)
    {
        List<List<Widget>> messageList = this.groupWidgetsByOriginalYPosition(children);

        for (List<Widget> message : messageList)
        {
            for (Widget messagePart : message)
            {
                int newY = messagePart.getOriginalY() + offset;
                messagePart.setOriginalY(newY);
                messagePart.revalidate();
            }
        }
    }
}