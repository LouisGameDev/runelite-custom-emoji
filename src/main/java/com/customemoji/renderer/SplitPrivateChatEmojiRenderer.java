package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SplitPrivateChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Inject
	public SplitPrivateChatEmojiRenderer(Client client, CustomEmojiConfig config)
	{
		super(client, config, InterfaceID.PmChat.CONTAINER);
	}
}
