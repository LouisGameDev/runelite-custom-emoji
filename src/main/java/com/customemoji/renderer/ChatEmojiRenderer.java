package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Inject
	public ChatEmojiRenderer(Client client, CustomEmojiConfig config, EventBus eventBus)
	{
		super(client, config, eventBus, InterfaceID.Chatbox.SCROLLAREA);
		this.setPriority(0.5f);
	}
}
