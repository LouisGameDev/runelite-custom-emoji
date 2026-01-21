package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class StateManagerBase
{
	protected final ConfigManager configManager;
	protected final CustomEmojiConfig config;
	protected final Object stateLock = new Object();

	protected Consumer<String> onEnabled;
	protected Consumer<String> onDisabled;
	protected Consumer<String> onResizingToggled;

	protected StateManagerBase(ConfigManager configManager, CustomEmojiConfig config)
	{
		this.configManager = configManager;
		this.config = config;
	}

	protected abstract String getDisabledConfigValue();
	protected abstract String getResizingDisabledConfigValue();
	protected abstract String getDisabledConfigKey();
	protected abstract String getResizingDisabledConfigKey();

	public void setOnEnabled(Consumer<String> callback)
	{
		this.onEnabled = callback;
	}

	public void setOnDisabled(Consumer<String> callback)
	{
		this.onDisabled = callback;
	}

	public void setOnResizingToggled(Consumer<String> callback)
	{
		this.onResizingToggled = callback;
	}

	public boolean isEnabled(String name)
	{
		synchronized (this.stateLock)
		{
			Set<String> disabled = this.parseCommaSeparatedSet(this.getDisabledConfigValue());
			return !disabled.contains(name);
		}
	}

	public boolean isResizingEnabled(String name)
	{
		synchronized (this.stateLock)
		{
			Set<String> disabled = this.parseCommaSeparatedSet(this.getResizingDisabledConfigValue());
			return !disabled.contains(name);
		}
	}

	public Set<String> getDisabled()
	{
		synchronized (this.stateLock)
		{
			return new HashSet<>(this.parseCommaSeparatedSet(this.getDisabledConfigValue()));
		}
	}

	public Set<String> getResizingDisabled()
	{
		synchronized (this.stateLock)
		{
			return new HashSet<>(this.parseCommaSeparatedSet(this.getResizingDisabledConfigValue()));
		}
	}

	public void setEnabled(String name, boolean enabled)
	{
		this.setMultipleEnabled(Set.of(name), enabled);
	}

	public void setResizingEnabled(String name, boolean resizingEnabled)
	{
		this.setMultipleResizingEnabled(Set.of(name), resizingEnabled);
	}

	public void setMultipleEnabled(Set<String> names, boolean enabled)
	{
		Consumer<String> callback = enabled ? this.onEnabled : this.onDisabled;
		this.updateAndNotify(names, enabled, this.getDisabledConfigValue(), this.getDisabledConfigKey(), callback);
	}

	public void setMultipleResizingEnabled(Set<String> names, boolean resizingEnabled)
	{
		this.updateAndNotify(names, resizingEnabled, this.getResizingDisabledConfigValue(),
			this.getResizingDisabledConfigKey(), this.onResizingToggled);
	}

	private void updateAndNotify(Set<String> names, boolean enabling, String configValue, String configKey, Consumer<String> callback)
	{
		if (names == null || names.isEmpty())
		{
			return;
		}

		Set<String> changed = this.updateDisabledSet(names, enabling, configValue, configKey);
		this.notifyChanged(changed, callback);
	}

	protected Set<String> parseCommaSeparatedSet(String value)
	{
		return new HashSet<>(Text.fromCSV(value != null ? value : ""));
	}

	private Set<String> updateDisabledSet(Set<String> names, boolean enabling, String configValue, String configKey)
	{
		Set<String> changed = new HashSet<>();

		synchronized (this.stateLock)
		{
			Set<String> disabledSet = new HashSet<>(this.parseCommaSeparatedSet(configValue));

			for (String name : names)
			{
				boolean isCurrentlyDisabled = disabledSet.contains(name);

				if (enabling && isCurrentlyDisabled)
				{
					disabledSet.remove(name);
					changed.add(name);
				}
				else if (!enabling && !isCurrentlyDisabled)
				{
					disabledSet.add(name);
					changed.add(name);
				}
			}

			if (!changed.isEmpty())
			{
				String serialized = String.join(",", disabledSet);
				this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, configKey, serialized);
			}
		}

		return changed;
	}

	private void notifyChanged(Set<String> changed, Consumer<String> callback)
	{
		if (callback == null)
		{
			return;
		}

		for (String name : changed)
		{
			callback.accept(name);
		}
	}
}
