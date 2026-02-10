package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.EmojiStateChanged;
import com.customemoji.event.EmojiStateChanged.ChangeType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class EmojiStateManager
{
	private final ConfigManager configManager;
	private final CustomEmojiConfig config;
	private final EventBus eventBus;
	private final Object stateLock = new Object();

	@Inject
	public EmojiStateManager(ConfigManager configManager, CustomEmojiConfig config, EventBus eventBus)
	{
		this.configManager = configManager;
		this.config = config;
		this.eventBus = eventBus;
	}

	public boolean isEmojiEnabled(String emojiName)
	{
		synchronized (this.stateLock)
		{
			Set<String> disabled = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
			return !disabled.contains(emojiName);
		}
	}

	public boolean isResizingEnabled(String emojiName)
	{
		synchronized (this.stateLock)
		{
			Set<String> resizingDisabled = PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
			return !resizingDisabled.contains(emojiName);
		}
	}

	public Set<String> getDisabledEmojis()
	{
		synchronized (this.stateLock)
		{
			return new HashSet<>(PluginUtils.parseDisabledEmojis(this.config.disabledEmojis()));
		}
	}

	public Set<String> getResizingDisabledEmojis()
	{
		synchronized (this.stateLock)
		{
			return new HashSet<>(PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis()));
		}
	}

	public void setEmojiEnabled(String emojiName, boolean enabled)
	{
		synchronized (this.stateLock)
		{
			Set<String> disabled = new HashSet<>(PluginUtils.parseDisabledEmojis(this.config.disabledEmojis()));

			if (enabled)
			{
				disabled.remove(emojiName);
			}
			else
			{
				disabled.add(emojiName);
			}

			String serialized = String.join(",", disabled);
			this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_DISABLED_EMOJIS, serialized);
		}

		ChangeType changeType = enabled ? ChangeType.ENABLED : ChangeType.DISABLED;
		this.eventBus.post(new EmojiStateChanged(emojiName, changeType));
	}

	public void setEmojiResizing(String emojiName, boolean resizingEnabled)
	{
		synchronized (this.stateLock)
		{
			Set<String> resizingDisabled = new HashSet<>(PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis()));

			if (resizingEnabled)
			{
				resizingDisabled.remove(emojiName);
			}
			else
			{
				resizingDisabled.add(emojiName);
			}

			String serialized = String.join(",", resizingDisabled);
			this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS, serialized);
		}

		this.eventBus.post(new EmojiStateChanged(emojiName, ChangeType.RESIZING_TOGGLED));
	}

	public void setMultipleEmojisEnabled(Set<String> emojiNames, boolean enabled)
	{
		if (emojiNames == null || emojiNames.isEmpty())
		{
			return;
		}

		Set<String> emojisToNotify = new HashSet<>();

		synchronized (this.stateLock)
		{
			Set<String> disabled = new HashSet<>(PluginUtils.parseDisabledEmojis(this.config.disabledEmojis()));

			for (String emojiName : emojiNames)
			{
				boolean wasDisabled = disabled.contains(emojiName);

				if (enabled)
				{
					disabled.remove(emojiName);
					if (wasDisabled)
					{
						emojisToNotify.add(emojiName);
					}
				}
				else
				{
					disabled.add(emojiName);
					if (!wasDisabled)
					{
						emojisToNotify.add(emojiName);
					}
				}
			}

			String serialized = String.join(",", disabled);
			this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_DISABLED_EMOJIS, serialized);
		}

		ChangeType changeType = enabled ? ChangeType.ENABLED : ChangeType.DISABLED;
		for (String emojiName : emojisToNotify)
		{
			this.eventBus.post(new EmojiStateChanged(emojiName, changeType));
		}
	}

	public void setMultipleEmojisResizing(Set<String> emojiNames, boolean resizingEnabled)
	{
		if (emojiNames == null || emojiNames.isEmpty())
		{
			return;
		}

		Set<String> emojisToNotify = new HashSet<>();

		synchronized (this.stateLock)
		{
			Set<String> resizingDisabled = new HashSet<>(PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis()));

			for (String emojiName : emojiNames)
			{
				boolean wasResizingEnabled = !resizingDisabled.contains(emojiName);
				boolean stateChanged = wasResizingEnabled != resizingEnabled;

				if (resizingEnabled)
				{
					resizingDisabled.remove(emojiName);
				}
				else
				{
					resizingDisabled.add(emojiName);
				}

				if (stateChanged)
				{
					emojisToNotify.add(emojiName);
				}
			}

			String serialized = String.join(",", resizingDisabled);
			this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS, serialized);
		}

		for (String emojiName : emojisToNotify)
		{
			this.eventBus.post(new EmojiStateChanged(emojiName, ChangeType.RESIZING_TOGGLED));
		}
	}
}
