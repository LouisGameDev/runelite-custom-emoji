package com.customemoji.lifecycle;

import com.customemoji.CustomEmojiConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates the lifecycle of all registered components.
 * Handles startup in registration order and shutdown in reverse order.
 * Provides rollback on startup failure and prevents double-start/double-shutdown.
 */
@Slf4j
@Singleton
public class LifecycleCoordinator
{
	private final CustomEmojiConfig config;
	private final List<Lifecycle> components = new ArrayList<>();
	private final List<Lifecycle> startedComponents = new ArrayList<>();
	private boolean isRunning = false;

	@Inject
	public LifecycleCoordinator(CustomEmojiConfig config)
	{
		this.config = config;
	}

	/**
	 * Registers a component to be managed by this coordinator.
	 * Components are started in the order they are registered.
	 *
	 * @param component the component to register
	 */
	public void register(Lifecycle component)
	{
		if (this.isRunning)
		{
			log.warn("Cannot register component '{}' while coordinator is running", component.getClass().getSimpleName());
			return;
		}

		this.components.add(component);
		log.debug("Registered lifecycle component: {}", component.getClass().getSimpleName());
	}

	/**
	 * Starts all registered and enabled components in registration order.
	 * If any component fails to start, all previously started components are shut down.
	 *
	 * @throws Exception if startup fails and rollback completes
	 */
	public void startAll() throws Exception
	{
		if (this.isRunning)
		{
			log.warn("LifecycleCoordinator is already running");
			return;
		}

		log.info("Starting {} lifecycle components", this.components.size());

		for (Lifecycle component : this.components)
		{
			boolean componentEnabled = component.isEnabled(this.config);
			if (!componentEnabled)
			{
				log.debug("Skipping disabled component: {}", component.getClass().getSimpleName());
				continue;
			}

			if (component.isStarted())
			{
				log.debug("Component already started: {}", component.getClass().getSimpleName());
				this.startedComponents.add(component);
				continue;
			}

			try
			{
				log.debug("Starting component: {}", component.getClass().getSimpleName());
				component.startUp();
				this.startedComponents.add(component);
				log.info("Started component: {}", component.getClass().getSimpleName());
			}
			catch (Exception e)
			{
				log.error("Failed to start component: {}", component.getClass().getSimpleName(), e);
				this.rollback();
				throw e;
			}
		}

		this.isRunning = true;
		log.info("All lifecycle components started successfully");
	}

	/**
	 * Shuts down all started components in reverse order.
	 */
	public void shutDownAll()
	{
		if (!this.isRunning && this.startedComponents.isEmpty())
		{
			log.debug("LifecycleCoordinator is not running, nothing to shut down");
			return;
		}

		log.info("Shutting down {} lifecycle components", this.startedComponents.size());

		List<Lifecycle> reverseOrder = new ArrayList<>(this.startedComponents);
		Collections.reverse(reverseOrder);

		for (Lifecycle component : reverseOrder)
		{
			this.safeShutDown(component);
		}

		this.startedComponents.clear();
		this.isRunning = false;
		log.info("All lifecycle components shut down");
	}

	/**
	 * Restarts a specific component if it's registered and enabled.
	 *
	 * @param component the component to restart
	 */
	public void restart(Lifecycle component)
	{
		if (!this.components.contains(component))
		{
			log.warn("Cannot restart unregistered component: {}", component.getClass().getSimpleName());
			return;
		}

		this.safeShutDown(component);
		this.startedComponents.remove(component);

		boolean componentEnabled = component.isEnabled(this.config);
		if (!componentEnabled)
		{
			log.debug("Component is disabled, not restarting: {}", component.getClass().getSimpleName());
			return;
		}

		try
		{
			component.startUp();
			this.startedComponents.add(component);
			log.info("Restarted component: {}", component.getClass().getSimpleName());
		}
		catch (Exception e)
		{
			log.error("Failed to restart component: {}", component.getClass().getSimpleName(), e);
		}
	}

	/**
	 * Returns whether the coordinator is currently running.
	 */
	public boolean isRunning()
	{
		return this.isRunning;
	}

	/**
	 * Returns the number of registered components.
	 */
	public int getComponentCount()
	{
		return this.components.size();
	}

	/**
	 * Returns the number of currently started components.
	 */
	public int getStartedCount()
	{
		return this.startedComponents.size();
	}

	private void rollback()
	{
		log.warn("Rolling back {} started components due to startup failure", this.startedComponents.size());

		List<Lifecycle> reverseOrder = new ArrayList<>(this.startedComponents);
		Collections.reverse(reverseOrder);

		for (Lifecycle component : reverseOrder)
		{
			this.safeShutDown(component);
		}

		this.startedComponents.clear();
	}

	private void safeShutDown(Lifecycle component)
	{
		try
		{
			if (component.isStarted())
			{
				log.debug("Shutting down component: {}", component.getClass().getSimpleName());
				component.shutDown();
				log.info("Shut down component: {}", component.getClass().getSimpleName());
			}
		}
		catch (Exception e)
		{
			log.error("Error shutting down component: {}", component.getClass().getSimpleName(), e);
		}
	}
}
