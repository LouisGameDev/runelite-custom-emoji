package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages emoji enable/disable and resizing state.
 * Decoupled from UI concerns to allow use from both panel and right-click menu.
 */
@Singleton
public class EmojiStateManager
{
	private final CustomEmojiConfig config;
	private final ConfigManager configManager;

	private Consumer<String> onEmojiResizingToggled;
	private Consumer<String> onEmojiDisabled;

	@Inject
	public EmojiStateManager(CustomEmojiConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	/**
	 * Sets a callback to be invoked when an emoji's resizing state changes.
	 * This allows the plugin to reload the emoji with the new size setting.
	 */
	public void setOnEmojiResizingToggled(Consumer<String> callback)
	{
		this.onEmojiResizingToggled = callback;
	}

	/**
	 * Sets a callback to be invoked when an emoji is disabled.
	 * This allows the plugin to replace the emoji image with text in chat.
	 */
	public void setOnEmojiDisabled(Consumer<String> callback)
	{
		this.onEmojiDisabled = callback;
	}

	public boolean isEmojiEnabled(String emojiName)
	{
		return !this.getDisabledEmojis().contains(emojiName);
	}

	public boolean isResizingEnabled(String emojiName)
	{
		return !this.getResizingDisabledEmojis().contains(emojiName);
	}

	public Set<String> getDisabledEmojis()
	{
		return PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
	}

	public Set<String> getResizingDisabledEmojis()
	{
		return PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
	}

	public void toggleEmojiEnabled(String emojiName)
	{
		Set<String> disabled = new HashSet<>(this.getDisabledEmojis());
		boolean wasDisabled = disabled.contains(emojiName);

		if (wasDisabled)
		{
			disabled.remove(emojiName);
		}
		else
		{
			disabled.add(emojiName);
		}

		this.saveDisabledEmojis(disabled);

		boolean isNowDisabled = !wasDisabled;
		if (isNowDisabled)
		{
			this.notifyEmojiDisabled(emojiName);
		}
	}

	public void setEmojiEnabled(String emojiName, boolean enabled)
	{
		Set<String> disabled = new HashSet<>(this.getDisabledEmojis());

		if (enabled)
		{
			disabled.remove(emojiName);
		}
		else
		{
			disabled.add(emojiName);
		}

		this.saveDisabledEmojis(disabled);
	}

	public void toggleEmojiResizing(String emojiName)
	{
		Set<String> resizingDisabled = new HashSet<>(this.getResizingDisabledEmojis());
		boolean wasDisabled = resizingDisabled.contains(emojiName);

		if (wasDisabled)
		{
			resizingDisabled.remove(emojiName);
		}
		else
		{
			resizingDisabled.add(emojiName);
		}

		this.saveResizingDisabledEmojis(resizingDisabled);
		this.notifyEmojiResizingToggled(emojiName);
	}

	public void setEmojiResizing(String emojiName, boolean resizingEnabled)
	{
		Set<String> resizingDisabled = new HashSet<>(this.getResizingDisabledEmojis());

		if (resizingEnabled)
		{
			resizingDisabled.remove(emojiName);
		}
		else
		{
			resizingDisabled.add(emojiName);
		}

		this.saveResizingDisabledEmojis(resizingDisabled);
		this.notifyEmojiResizingToggled(emojiName);
	}

	public void setMultipleEmojisEnabled(Set<String> emojiNames, boolean enabled)
	{
		Set<String> disabled = new HashSet<>(this.getDisabledEmojis());

		for (String emojiName : emojiNames)
		{
			if (enabled)
			{
				disabled.remove(emojiName);
			}
			else
			{
				disabled.add(emojiName);
			}
		}

		this.saveDisabledEmojis(disabled);
	}

	public void setMultipleEmojisResizing(Set<String> emojiNames, boolean resizingEnabled)
	{
		Set<String> resizingDisabled = new HashSet<>(this.getResizingDisabledEmojis());

		for (String emojiName : emojiNames)
		{
			if (resizingEnabled)
			{
				resizingDisabled.remove(emojiName);
			}
			else
			{
				resizingDisabled.add(emojiName);
			}
		}

		this.saveResizingDisabledEmojis(resizingDisabled);

		for (String emojiName : emojiNames)
		{
			this.notifyEmojiResizingToggled(emojiName);
		}
	}

	private void saveDisabledEmojis(Set<String> disabled)
	{
		String newValue = String.join(",", disabled);
		this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_DISABLED_EMOJIS, newValue);
	}

	private void saveResizingDisabledEmojis(Set<String> resizingDisabled)
	{
		String newValue = String.join(",", resizingDisabled);
		this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS, newValue);
	}

	private void notifyEmojiResizingToggled(String emojiName)
	{
		if (this.onEmojiResizingToggled != null)
		{
			this.onEmojiResizingToggled.accept(emojiName);
		}
	}

	private void notifyEmojiDisabled(String emojiName)
	{
		if (this.onEmojiDisabled != null)
		{
			this.onEmojiDisabled.accept(emojiName);
		}
	}
}
