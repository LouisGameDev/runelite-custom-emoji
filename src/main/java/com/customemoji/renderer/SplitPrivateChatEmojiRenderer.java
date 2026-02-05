package com.customemoji.renderer;

import net.runelite.api.gameval.InterfaceID;

import javax.inject.Singleton;

import com.customemoji.CustomEmojiConfig;

@Singleton
public class SplitPrivateChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Override
	public void startUp()
	{
		this.widgetId = InterfaceID.PmChat.CONTAINER;
		super.startUp();
		this.setPriority(0.5f);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return false;
	}
}
