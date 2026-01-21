package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EmojiStateManager extends StateManagerBase
{
	@Inject
	public EmojiStateManager(ConfigManager configManager, CustomEmojiConfig config)
	{
		super(configManager, config);
	}

	@Override
	protected String getDisabledConfigValue()
	{
		return this.config.disabledEmojis();
	}

	@Override
	protected String getResizingDisabledConfigValue()
	{
		return this.config.resizingDisabledEmojis();
	}

	@Override
	protected String getDisabledConfigKey()
	{
		return CustomEmojiConfig.KEY_DISABLED_EMOJIS;
	}

	@Override
	protected String getResizingDisabledConfigKey()
	{
		return CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS;
	}
}
