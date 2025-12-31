package com.customemoji;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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
import java.awt.event.MouseEvent;
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

    // Tooltip state
    private String hoveredEmojiName = null;
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
        if (hoveredEmojiName != null && !hoveredEmojiName.isEmpty() && config.showEmojiTooltips())
        {
            tooltipManager.add(new Tooltip(hoveredEmojiName));
        }
    }

    private void updateHoveredEmoji(Point mousePoint)
    {
        this.mousePosition = mousePoint;

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden())
        {
            this.hoveredEmojiName = null;
            return;
        }

        // Check custom emojis first
        String customEmoji = PluginUtils.findEmojiNameAtPoint(chatbox, mousePoint.x, mousePoint.y,
            this.emojis, this.chatIconManager, this.client.getModIcons());
        if (customEmoji != null)
        {
            this.hoveredEmojiName = customEmoji;
            return;
        }

        // Fall back to checking built-in RuneLite icons
        String builtInIcon = this.findBuiltInIconAtPoint(chatbox, mousePoint);
        this.hoveredEmojiName = builtInIcon;
    }

    private String findBuiltInIconAtPoint(Widget chatbox, Point mousePoint)
    {
        List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);
        for (Widget widget : visibleWidgets)
        {
            String text = widget.getText();
            if (!PluginUtils.hasImgTag(text))
            {
                continue;
            }

            int imageId = EmojiPositionCalculator.findEmojiAtPoint(
                widget, text, mousePoint.x, mousePoint.y,
                id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
            );

            if (imageId >= 0)
            {
                for (IconID icon : IconID.values())
                {
                    if (icon.getIndex() == imageId)
                    {
                        return this.formatIconName(icon.name());
                    }
                }
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
}
