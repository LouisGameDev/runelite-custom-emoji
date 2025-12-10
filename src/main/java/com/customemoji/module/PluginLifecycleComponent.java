package com.customemoji.module;

import com.customemoji.CustomEmojiConfig;

/**
 * Interface for components that participate in the plugin lifecycle.
 * Components can be dynamically enabled/disabled based on configuration.
 */
public interface PluginLifecycleComponent
{
	/**
	 * Determines if this component should be enabled based on the current config.
	 * ComponentManager will call startUp/shutDown when this changes.
	 */
	default boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	/**
	 * Called when the component is started (plugin starts or component becomes enabled).
	 */
	default void startUp()
	{
	}

	/**
	 * Called when the component is stopped (plugin stops or component becomes disabled).
	 */
	default void shutDown()
	{
	}
}
