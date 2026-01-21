package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FolderStateManager extends StateManagerBase
{
	@Inject
	public FolderStateManager(ConfigManager configManager, CustomEmojiConfig config)
	{
		super(configManager, config);
	}

	@Override
	protected String getDisabledConfigValue()
	{
		return this.config.disabledFolders();
	}

	@Override
	protected String getResizingDisabledConfigValue()
	{
		return this.config.resizingDisabledFolders();
	}

	@Override
	protected String getDisabledConfigKey()
	{
		return CustomEmojiConfig.KEY_DISABLED_FOLDERS;
	}

	@Override
	protected String getResizingDisabledConfigKey()
	{
		return CustomEmojiConfig.KEY_RESIZING_DISABLED_FOLDERS;
	}
}
