package com.customemoji.lifecycle;

import com.customemoji.CustomEmojiConfig;

/**
 * Interface for components that have a lifecycle (startup/shutdown).
 * Components implementing this interface can be managed by {@link LifecycleCoordinator}.
 */
public interface Lifecycle
{
	/**
	 * Called when the component should start up and acquire resources.
	 * This method should be idempotent - calling it multiple times should be safe.
	 *
	 * @throws Exception if startup fails
	 */
	void startUp() throws Exception;

	/**
	 * Called when the component should shut down and release resources.
	 * This method should be idempotent - calling it multiple times should be safe.
	 * Implementations should not throw exceptions; errors should be logged instead.
	 */
	void shutDown();

	/**
	 * Returns whether this component is enabled and should be started.
	 */
	boolean isEnabled(CustomEmojiConfig config);

	/**
	 * Returns whether this component is currently started.
	 */
	boolean isStarted();
}