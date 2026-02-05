package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.EmojiDisabled;
import com.customemoji.event.EmojiEnabled;
import com.customemoji.event.EmojiResizingToggled;
import com.customemoji.model.Lifecycle;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class EmojiStateManager implements Lifecycle
{
	private final Object stateLock = new Object();

	@Inject
	private EventBus eventBus;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CustomEmojiConfig config;

	@Override
	public void startUp()
	{
		this.eventBus.register(this);
	}

	@Override
	public void shutDown()
	{
		this.eventBus.unregister(this);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
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

		if (enabled)
		{
			this.eventBus.post(new EmojiEnabled(emojiName));
		}
		else
		{
			this.eventBus.post(new EmojiDisabled(emojiName));
		}
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

		this.eventBus.post(new EmojiResizingToggled(emojiName));
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

		for (String emojiName : emojisToNotify)
		{
			if (enabled)
			{
				this.eventBus.post(new EmojiEnabled(emojiName));
			}
			else
			{
				this.eventBus.post(new EmojiDisabled(emojiName));
			}
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
			this.eventBus.post(new EmojiResizingToggled(emojiName));
		}
	}
}
