package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Inject
	public ChatEmojiRenderer(Client client, CustomEmojiConfig config)
	{
		super(client, config, InterfaceID.Chatbox.SCROLLAREA);
		this.setPriority(0.5f);
	}
}
