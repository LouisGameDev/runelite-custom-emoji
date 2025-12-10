package com.customemoji;

import com.customemoji.module.ComponentManager;
import com.customemoji.module.CustomEmojiModule;
import com.google.inject.Binder;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Custom Emoji plugin - allows custom emojis and soundojis in chat messages.
 * All functionality is delegated to components managed by ComponentManager.
 */
@Slf4j
@PluginDescriptor(
	name = "Custom Emoji",
	description = "Allows you to use custom emojis in chat messages",
	tags = {"emoji", "chat", "message", "custom", "icon", "emote", "text", "clan", "notification"}
)
public class CustomEmojiPlugin extends Plugin
{
	@Inject
	private ComponentManager componentManager;

	@Override
	public void configure(Binder binder)
	{
		binder.install(new CustomEmojiModule());
	}

	@Override
	protected void startUp()
	{
		this.componentManager.onPluginStart();
	}

	@Override
	protected void shutDown()
	{
		this.componentManager.onPluginStop();
	}
}
