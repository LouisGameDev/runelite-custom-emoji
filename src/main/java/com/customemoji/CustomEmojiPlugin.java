package com.customemoji;

import com.customemoji.service.LifecycleManager;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
		name = "Custom Emoji",
		description = "Allows you to use custom emojis in chat messages",
		tags = {"emoji", "chat", "message", "custom", "icon", "emote", "text", "clan", "notification"}
)
public class CustomEmojiPlugin extends Plugin
{
	@Inject
	private LifecycleManager lifecycleManager;

	@Override
	protected void startUp() throws Exception
	{
		this.lifecycleManager.startUp();
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.lifecycleManager.shutDown();
	}

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}
}
