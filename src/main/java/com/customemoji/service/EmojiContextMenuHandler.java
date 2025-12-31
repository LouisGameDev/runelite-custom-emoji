package com.customemoji.service;

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
import java.util.Map;

/**
 * Handles right-click context menu for emojis in chat.
 * Provides options to toggle emoji visibility and resizing.
 */
@Singleton
public class EmojiContextMenuHandler
{
	private final Client client;
	private final EmojiStateManager emojiStateManager;
	private final Map<String, Emoji> emojis;
	private final ChatIconManager chatIconManager;

	@Inject
	public EmojiContextMenuHandler(Client client, EmojiStateManager emojiStateManager,
								   Map<String, Emoji> emojis, ChatIconManager chatIconManager)
	{
		this.client = client;
		this.emojiStateManager = emojiStateManager;
		this.emojis = emojis;
		this.chatIconManager = chatIconManager;
	}

	public void onMenuOpened()
	{
		String emojiName = this.findEmojiAtMousePosition();
		if (emojiName == null)
		{
			return;
		}

		this.addMenuEntries(emojiName);
	}

	private String findEmojiAtMousePosition()
	{
		Point mousePos = this.client.getMouseCanvasPosition();
		Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);

		return PluginUtils.findEmojiNameAtPoint(chatbox, mousePos.getX(), mousePos.getY(),
			this.emojis, this.chatIconManager, this.client.getModIcons());
	}

	private void addMenuEntries(String emojiName)
	{
		MenuEntry parentEntry = this.client.getMenu().createMenuEntry(-1)
			.setOption("Emoji: ")
			.setTarget("<col=00FF00>" + emojiName + "</col>")
			.setType(MenuAction.RUNELITE);

		Menu submenu = parentEntry.createSubMenu();

		boolean isEnabled = this.emojiStateManager.isEmojiEnabled(emojiName);
		String enableOption = isEnabled ? "Disable" : "Enable";
		submenu.createMenuEntry(-1)
			.setOption(enableOption)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> this.emojiStateManager.toggleEmojiEnabled(emojiName));

		boolean resizingEnabled = this.emojiStateManager.isResizingEnabled(emojiName);
		String resizeOption = resizingEnabled ? "Full Size" : "Scale Down";
		submenu.createMenuEntry(-1)
			.setOption(resizeOption)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> this.emojiStateManager.toggleEmojiResizing(emojiName));
	}
}
