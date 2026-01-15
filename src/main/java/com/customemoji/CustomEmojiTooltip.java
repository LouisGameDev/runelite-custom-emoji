package com.customemoji;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.model.Emoji;
import com.customemoji.service.EmojiStateManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.IconID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
    private MouseManager mouseManager;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private Map<String, Emoji> emojis;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EmojiStateManager emojiStateManager;

    private static final String MENU_OPTION_EMOJI = "Emoji";

    // Tooltip state
    private List<String> hoveredEmojiNames = new ArrayList<>();
    private Point mousePosition = null;

    private final MouseListener mouseListener = new MouseListener()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseEntered(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseExited(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent mouseEvent)
		{
			Point currentPoint = mouseEvent.getPoint();

			// Only update if mouse actually moved a good bit
			if (mousePosition == null ||
				Math.abs(currentPoint.x - mousePosition.x) > 2 ||
				Math.abs(currentPoint.y - mousePosition.y) > 2)
			{
				mousePosition = currentPoint;

				// Run on client thread to avoid race conditions with widget rendering
				clientThread.invokeLater(() -> updateHoveredEmoji(currentPoint));
			}
			return mouseEvent;
		}
	};

    protected void startUp()
    {
        if (mouseManager != null)
        {
            mouseManager.registerMouseListener(mouseListener);
        }
    }

    protected void shutDown()
    {
        if (mouseManager != null)
        {
            mouseManager.unregisterMouseListener(mouseListener);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        showTooltip();
        return null;
    }

    private void showTooltip()
    {
        if (this.client.isMenuOpen())
        {
            return;
        }

        if (!this.hoveredEmojiNames.isEmpty() && this.config.showEmojiTooltips())
        {
            String tooltipText = String.join(" + ", this.hoveredEmojiNames);
            this.tooltipManager.add(new Tooltip(tooltipText));
        }
    }

    private void updateHoveredEmoji(Point mousePoint)
    {
        this.mousePosition = mousePoint;

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden() || !this.isPointInWidget(chatbox, mousePoint))
        {
            this.hoveredEmojiNames.clear();
            return;
        }

        List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);
        this.hoveredEmojiNames = this.checkWidgetsForEmoji(visibleWidgets, mousePoint);
    }

    private List<String> checkWidgetsForEmoji(List<Widget> widgets, Point mousePoint)
    {
        for (Widget widget : widgets)
        {
            String text = widget.getText();
            if (!PluginUtils.hasImgTag(text))
            {
                continue;
            }

            List<String> hoveredEmojis = this.findEmojisAtPosition(widget, text, mousePoint);
            if (!hoveredEmojis.isEmpty())
            {
                return hoveredEmojis;
            }
        }
        return new ArrayList<>();
    }

    private boolean isPointInWidget(Widget widget, Point point)
    {
        net.runelite.api.Point canvasLocation = widget.getCanvasLocation();
        if (canvasLocation == null)
        {
            return false;
        }

        int x = canvasLocation.getX();
        int y = canvasLocation.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        return point.x >= x && point.x <= x + width &&
               point.y >= y && point.y <= y + height;
    }

    private List<String> findEmojisAtPosition(Widget widget, String text, Point mousePoint)
    {
        List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
            widget,
            text,
            id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
        );

        Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(() -> this.emojis, this.chatIconManager);
        PluginUtils.linkZeroWidthEmojisToTarget(positions, emojiLookup, this.chatIconManager);

        List<String> emojiNames = new ArrayList<>();
        for (EmojiPosition position : positions)
        {
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
        // Check custom emojis first (both main ID and zero width ID)
        for (Emoji emoji : this.emojis.values())
        {
            int mainImageId = this.chatIconManager.chatIconIndex(emoji.getId());
            if (mainImageId == imageId)
            {
                return emoji.getText();
            }

            if (emoji.hasZeroWidthId())
            {
                int zeroWidthId = this.chatIconManager.chatIconIndex(emoji.getZeroWidthId());
                if (zeroWidthId == imageId)
                {
                    return emoji.getText();
                }
            }
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

        net.runelite.api.Point mouseCanvasPosition = this.client.getMouseCanvasPosition();
        if (mouseCanvasPosition == null)
        {
            return;
        }

        Point mousePoint = new Point(mouseCanvasPosition.getX(), mouseCanvasPosition.getY());

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden() || !this.isPointInWidget(chatbox, mousePoint))
        {
            return;
        }

        List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);
        List<String> emojiNames = this.checkWidgetsForEmoji(visibleWidgets, mousePoint);

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
