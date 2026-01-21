package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.model.Emoji;
import com.customemoji.panel.tree.FolderStructureBuilder;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.List;

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

	public void applyInheritedFolderStates(List<Emoji> emojis, File baseFolder, FolderStateManager folderStateManager)
	{
		for (Emoji emoji : emojis)
		{
			String folderPath = FolderStructureBuilder.getFullFolderPath(emoji.getFile(), baseFolder);
			this.applyInheritedFolderState(emoji.getText(), folderPath, folderStateManager);
		}
	}

	private void applyInheritedFolderState(String emojiName, String folderPath, FolderStateManager folderStateManager)
	{
		if (folderPath.isEmpty())
		{
			return;
		}

		boolean folderDisabled = !folderStateManager.isEnabled(folderPath);
		if (folderDisabled)
		{
			this.setEnabled(emojiName, false);
		}

		boolean folderResizingDisabled = !folderStateManager.isResizingEnabled(folderPath);
		if (folderResizingDisabled)
		{
			this.setResizingEnabled(emojiName, false);
		}
	}
}
