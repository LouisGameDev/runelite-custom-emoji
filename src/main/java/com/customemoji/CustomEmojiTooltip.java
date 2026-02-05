package com.customemoji;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.model.Emoji;
import com.customemoji.service.EmojiStateManager;
import net.runelite.client.eventbus.Subscribe;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.api.IconID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class CustomEmojiTooltip extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private EventBus eventBus;

    private Map<String, Emoji> emojis = new HashMap<>();

    @Inject
    private EmojiStateManager emojiStateManager;

    private static final String MENU_OPTION_EMOJI = "Emoji";

    protected void startUp()
    {
        this.eventBus.register(this);
    }

    protected void shutDown()
    {
        this.eventBus.unregister(this);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (this.client.isMenuOpen() || !this.config.showEmojiTooltips())
        {
            return null;
        }

        if (PluginUtils.getVisibleChatWidgets(client) == null)
        {
            return null;
        }

        List<String> hoveredEmojis = this.findHoveredEmojis();
        if (hoveredEmojis != null && !hoveredEmojis.isEmpty())
        {
            String tooltipText = String.join(" + ", hoveredEmojis);
            this.tooltipManager.add(new Tooltip(tooltipText));
        }

        return null;
    }

    @Subscribe
    public void onAfterEmojisLoaded(AfterEmojisLoaded event)
    {
        this.emojis.putAll(event.getEmojis());
    }

    private List<String> findHoveredEmojis()
    {
        net.runelite.api.Point mouseCanvasPosition = this.client.getMouseCanvasPosition();

        if (mouseCanvasPosition == null)
        {
            return null;
        }

        Point mousePoint = new Point(mouseCanvasPosition.getX(), mouseCanvasPosition.getY());

        List<Widget> children = PluginUtils.getVisibleChatWidgets(this.client);

        if (children == null)
        {
            return null;
        }

        for (Widget widget : children)
        {
            String text = widget.getText();
            if (text == null || !PluginUtils.hasImgTag(text))
            {
                continue;
            }

            // Only check X bounds here - tall emojis can extend above/below the widget's Y bounds
            // The emoji vertical filtering in findEmojisAtPosition handles Y checking
            Rectangle bounds = widget.getBounds();
            if (bounds == null)
            {
                continue;
            }
            boolean mouseInXRange = mousePoint.x >= bounds.x && mousePoint.x <= bounds.x + bounds.width;
            if (!mouseInXRange)
            {
                continue;
            }

            // Mouse X is within this widget - check for emojis
            List<String> emojisInWidget = this.findEmojisAtPosition(widget, text, mousePoint, bounds);
            if (!emojisInWidget.isEmpty())
            {
                return emojisInWidget;
            }
        }

        return new ArrayList<>();
    }

    private List<String> findEmojisAtPosition(Widget widget, String text, Point mousePoint, Rectangle widgetBounds)
    {
        List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
            widget,
            text,
            id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
        );

        this.linkZeroWidthEmojisToTarget(positions);

        List<String> emojiNames = new ArrayList<>();
        for (EmojiPosition position : positions)
        {
            // Skip emojis whose vertical bounds don't contain the mouse Y
            int emojiTop = position.getY();
            int emojiBottom = emojiTop + position.getHeight();
            boolean mouseInVerticalRange = mousePoint.y >= emojiTop && mousePoint.y <= emojiBottom;
            if (!mouseInVerticalRange)
            {
                continue;
            }

            boolean isHovered = this.isPositionHovered(position, mousePoint);
            if (isHovered)
            {
                String name = this.findEmojiNameById(position.getImageId());
                if (name != null)
                {
                    emojiNames.add(name);
                }
            }
        }

        return emojiNames;
    }

    private void linkZeroWidthEmojisToTarget(List<EmojiPosition> positions)
    {
        Rectangle lastBaseEmojiBounds = null;

        for (EmojiPosition position : positions)
        {
            Emoji emoji = PluginUtils.findEmojiByImageId(position.getImageId(), this.emojis);
            boolean isZeroWidth = PluginUtils.isZeroWidthId(emoji, position.getImageId());

            if (isZeroWidth)
            {
                position.setBaseEmojiBounds(lastBaseEmojiBounds);
            }

            if (!isZeroWidth && emoji != null)
            {
                lastBaseEmojiBounds = position.getBounds();
            }
        }
    }

    private boolean isPositionHovered(EmojiPosition position, Point mousePoint)
    {
        if (position.hasBaseEmojiBounds())
        {
            return position.getBaseEmojiBounds().contains(mousePoint.x, mousePoint.y);
        }
        return position.containsPoint(mousePoint.x, mousePoint.y);
    }

    private String findEmojiNameById(int imageId)
    {
        Emoji emoji = PluginUtils.findEmojiByImageId(imageId, this.emojis);
        if (emoji != null)
        {
            return emoji.getText();
        }

        // Check built-in RuneLite IconIDs
        for (IconID icon : IconID.values())
        {
            if (icon.getIndex() == imageId)
            {
                return this.formatIconName(icon.name());
            }
        }

        return null;
    }

    private String formatIconName(String enumName)
    {
        // Convert PLAYER_MODERATOR to "Player Moderator"
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words)
        {
            if (result.length() > 0)
            {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    public void onMenuOpened()
    {
        if (this.emojis == null || this.emojis.isEmpty())
        {
            return;
        }    

        if (PluginUtils.getVisibleChatWidgets(this.client) == null || !PluginUtils.getIsMouseInChatWidget(this.client))
        {
            return;
        }

        List<String> emojiNames = this.findHoveredEmojis();

        List<String> customEmojiNames = this.filterCustomEmojis(emojiNames);
        if (!customEmojiNames.isEmpty())
        {
            this.addContextMenuEntries(customEmojiNames);
        }
    }

    private List<String> filterCustomEmojis(List<String> emojiNames)
    {
        List<String> customEmojis = new ArrayList<>();
        for (String name : emojiNames)
        {
            boolean isCustomEmoji = this.emojis.containsKey(name.toLowerCase());
            if (isCustomEmoji)
            {
                customEmojis.add(name);
            }
        }
        return customEmojis;
    }

    private void addContextMenuEntries(List<String> emojiNames)
    {
        for (int i = emojiNames.size() - 1; i >= 0; i--) // Reverse order to maintain correct menu order
        {
            String emojiName = emojiNames.get(i);
            this.addEmojiMenuEntry(emojiName);
        }
    }

    private void addEmojiMenuEntry(String emojiName)
    {
        boolean isEnabled = this.emojiStateManager.isEmojiEnabled(emojiName);
        boolean isResizingEnabled = this.emojiStateManager.isResizingEnabled(emojiName);

        MenuEntry parent = this.client.getMenu().createMenuEntry(-1)
            .setOption(MENU_OPTION_EMOJI)
            .setTarget("<col=ffff00>" + emojiName + "</col>")
            .setType(MenuAction.RUNELITE);

        Menu submenu = parent.createSubMenu();

        String enableOption = isEnabled ? "Hide" : "Show";
        submenu.createMenuEntry(0)
            .setOption(enableOption)
            .setType(MenuAction.RUNELITE)
            .onClick(e -> this.emojiStateManager.setEmojiEnabled(emojiName, !isEnabled));

        if (isEnabled)
        {
            String resizeOption = isResizingEnabled ? "Full Size" : "Scale Down";
            submenu.createMenuEntry(0)
                .setOption(resizeOption)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> this.emojiStateManager.setEmojiResizing(emojiName, !isResizingEnabled));
        }
    }
}
