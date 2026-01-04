package com.customemoji.service;

import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.model.Emoji;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ChatIconManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * Handles right-click context menu for emojis in chat.
 * Provides options to enable/disable emojis and toggle resizing.
 */
@Singleton
public class EmojiContextMenuHandler
{
	private static final String MENU_OPTION_EMOJI = "Custom Emoji";

	private final Client client;
	private final ChatIconManager chatIconManager;
	private final EmojiStateManager emojiStateManager;
	private Map<String, Emoji> emojis;

	@Inject
	public EmojiContextMenuHandler(Client client, ChatIconManager chatIconManager, EmojiStateManager emojiStateManager)
	{
		this.client = client;
		this.chatIconManager = chatIconManager;
		this.emojiStateManager = emojiStateManager;
	}

	public void setEmojis(Map<String, Emoji> emojis)
	{
		this.emojis = emojis;
	}

	public void onMenuOpened()
	{
		if (this.emojis == null || this.emojis.isEmpty())
		{
			return;
		}

		String emojiName = this.findEmojiAtCursor();
		if (emojiName == null)
		{
			return;
		}

		this.addContextMenuEntries(emojiName);
	}

	private String findEmojiAtCursor()
	{
		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chatbox == null || chatbox.isHidden())
		{
			return null;
		}

		Point mousePosition = this.client.getMouseCanvasPosition();
		if (mousePosition == null)
		{
			return null;
		}

		int mouseX = mousePosition.getX();
		int mouseY = mousePosition.getY();

		List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);
		for (Widget widget : visibleWidgets)
		{
			String text = widget.getText();
			if (!PluginUtils.hasImgTag(text))
			{
				continue;
			}

			int imageId = EmojiPositionCalculator.findEmojiAtPoint(
				widget, text, mouseX, mouseY,
				id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
			);

			if (imageId >= 0)
			{
				String foundName = this.findCustomEmojiNameByImageId(imageId);
				if (foundName != null)
				{
					return foundName;
				}
			}
		}

		return null;
	}

	private String findCustomEmojiNameByImageId(int imageId)
	{
		for (Emoji emoji : this.emojis.values())
		{
			int emojiImageId = this.chatIconManager.chatIconIndex(emoji.getId());
			if (emojiImageId == imageId)
			{
				return emoji.getText();
			}
		}
		return null;
	}

	private void addContextMenuEntries(String emojiName)
	{
		boolean isEnabled = this.emojiStateManager.isEmojiEnabled(emojiName);
		boolean isResizingEnabled = this.emojiStateManager.isResizingEnabled(emojiName);

		MenuEntry parent = this.client.createMenuEntry(-1)
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
