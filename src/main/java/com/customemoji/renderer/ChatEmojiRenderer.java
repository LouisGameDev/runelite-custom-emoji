package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;

import net.runelite.api.gameval.InterfaceID;

import javax.inject.Singleton;

@Singleton
public class ChatEmojiRenderer extends EmojiWidgetRenderer
{
	@Override
	public void startUp()
	{
		this.widgetId = InterfaceID.Chatbox.SCROLLAREA;
		this.setPriority(0.5f);
		super.startUp();
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}
}
