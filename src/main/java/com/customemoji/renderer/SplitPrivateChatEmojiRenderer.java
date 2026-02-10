package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.EventBus;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SplitPrivateChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Inject
	public SplitPrivateChatEmojiRenderer(Client client, CustomEmojiConfig config, EventBus eventBus)
	{
		super(client, config, eventBus, InterfaceID.PmChat.CONTAINER);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return false;
	}
}
