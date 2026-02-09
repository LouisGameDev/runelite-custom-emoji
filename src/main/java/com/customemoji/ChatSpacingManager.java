package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.awt.Rectangle;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.model.Emoji;
import com.customemoji.model.Lifecycle;

@Slf4j
@Singleton
public class ChatSpacingManager implements Lifecycle
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private ChatScrollingManager chatScrollingManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    private Map<String, Emoji> emojis = new HashMap<>();
    
    private final List<Rectangle> appliedChatboxYBounds = new ArrayList<>();
    private final List<Rectangle> appliedPmChatYBounds = new ArrayList<>();

    private ScheduledExecutorService debounceExecutor;
    private ScheduledFuture<?> pendingTask = null;

    @Override
    public void startUp()
    {
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor();
        this.eventBus.register(this);
    }

    @Subscribe
    public void onAfterEmojisLoaded(AfterEmojisLoaded event)
    {
        this.emojis = event.getEmojis();
        this.clearStoredPositions();
        this.applyChatSpacing();
    }

    public void clearStoredPositions()
    {
        this.appliedChatboxYBounds.clear();
        this.appliedPmChatYBounds.clear();
    }

    public void applyChatSpacing()
    {
        if (this.debounceExecutor == null || this.debounceExecutor.isShutdown())
        {
            return;
        }

        boolean isFirstCall = this.pendingTask == null || this.pendingTask.isDone();

        if (!isFirstCall)
        {
            this.pendingTask.cancel(false);
        }

        if (isFirstCall)
        {
            this.clientThread.invokeAtTickEnd(this::executeApplyChatSpacing);
        }

        this.pendingTask = this.debounceExecutor.schedule(
            () -> this.clientThread.invokeAtTickEnd(this::executeApplyChatSpacing),
            10,
            TimeUnit.MILLISECONDS
        );
    }

    private void executeApplyChatSpacing()
    {
        int spacingAdjustment = this.config.chatMessageSpacing();
        boolean dynamicSpacing = this.config.dynamicEmojiSpacing();

        boolean noSpacingNeeded = spacingAdjustment == 0 && !dynamicSpacing;
        if (noSpacingNeeded)
        {
            return;
        }

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        this.processWidget(chatbox, false);
    }

    @Override
    public void shutDown()
    {
        this.eventBus.unregister(this);
        if (this.debounceExecutor != null)
        {
            this.debounceExecutor.shutdownNow();
            this.debounceExecutor = null;
        }
        this.clearStoredPositions();
    }

    @Override
    public boolean isEnabled(CustomEmojiConfig config)
    {
        return config.dynamicEmojiSpacing() || config.chatMessageSpacing() != 0;
    }

    private void processWidget(Widget widget, boolean invert)
    {
        if (widget == null || widget.isHidden())
        {
            return;
        }

        Widget[] children = this.getCombinedChildren(widget);
        int height = this.adjustChildren(children, invert);

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

    private int adjustChildren(Widget[] children, boolean invert)
    {
        if (children == null)
        {
            return 0;
        }

        List<List<Widget>> messageList = PluginUtils.groupWidgetsByOriginalYPosition(children);

        int startIndex = Math.max(0, messageList.size() - this.config.messageProcessLimit());
        int maxY = 0;

        if (this.config.messageProcessLimit() == 0)
        {
            startIndex = 0; // 0 = unlimited
        }

        if (startIndex > 0 && !messageList.isEmpty())
        {
            List<Widget> prevMessage = messageList.get(startIndex-1);
            if (!prevMessage.isEmpty())
            {
                Widget widget = prevMessage.get(0);
                maxY = widget.getOriginalY() + widget.getOriginalHeight();
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

            boolean onLatestMessage = i == messageList.size() - 1;
            if (onLatestMessage)
            {
                maxY += messageBounds.height; // Don't need extra spacing below latest message
            }
            else
            {
                maxY += messageBounds.height + this.config.chatMessageSpacing();
            }
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
        if (dynamicSpacing)
        {
           Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(() -> this.emojis);
           customEmojiIds = emojiLookup.keySet();
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

        return Arrays.copyOf(children, children.length);
    }
}