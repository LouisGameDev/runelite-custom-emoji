package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Singleton
public class EmojiStateManager
{
	private final ConfigManager configManager;
	private final CustomEmojiConfig config;
	private final Object stateLock = new Object();

	private Consumer<String> onEmojiEnabled;
	private Consumer<String> onEmojiDisabled;
	private Consumer<String> onEmojiResizingToggled;

	@Inject
	public EmojiStateManager(ConfigManager configManager, CustomEmojiConfig config)
	{
		this.configManager = configManager;
		this.config = config;
	}

	public void setOnEmojiEnabled(Consumer<String> callback)
	{
		this.onEmojiEnabled = callback;
	}

	public void setOnEmojiDisabled(Consumer<String> callback)
	{
		this.onEmojiDisabled = callback;
	}

	public void setOnEmojiResizingToggled(Consumer<String> callback)
	{
		this.onEmojiResizingToggled = callback;
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

		if (enabled && this.onEmojiEnabled != null)
		{
			this.onEmojiEnabled.accept(emojiName);
		}
		else if (!enabled && this.onEmojiDisabled != null)
		{
			this.onEmojiDisabled.accept(emojiName);
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

		if (this.onEmojiResizingToggled != null)
		{
			this.onEmojiResizingToggled.accept(emojiName);
		}
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

		if (enabled && this.onEmojiEnabled != null)
		{
			for (String emojiName : emojisToNotify)
			{
				this.onEmojiEnabled.accept(emojiName);
			}
		}
		else if (!enabled && this.onEmojiDisabled != null)
		{
			for (String emojiName : emojisToNotify)
			{
				this.onEmojiDisabled.accept(emojiName);
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

		if (this.onEmojiResizingToggled != null)
		{
			for (String emojiName : emojisToNotify)
			{
				this.onEmojiResizingToggled.accept(emojiName);
			}
		}
	}
}
