package com.customemoji.module;

import com.customemoji.CustomEmojiConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.GameEventManager;

/**
 * Manages all the subcomponents of the plugin so they can register themselves
 * to RuneLite resources (EventBus, OverlayManager, etc.) instead of the main
 * plugin class handling everything.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class ComponentManager
{
	private final EventBus eventBus;
	private final OverlayManager overlayManager;
	private final GameEventManager gameEventManager;
	private final CustomEmojiConfig config;
	private final Set<PluginLifecycleComponent> components;

	private final Map<PluginLifecycleComponent, Boolean> states = new HashMap<>();

	public void onPluginStart()
	{
		this.eventBus.register(this);
		this.components.forEach(c -> this.states.put(c, false));
		this.revalidateComponentStates();
	}

	public void onPluginStop()
	{
		this.eventBus.unregister(this);
		this.components.stream()
			.filter(this.states::get)
			.forEach(this::tryShutDown);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!CustomEmojiConfig.KEY_CONFIG_GROUP.equals(e.getGroup()))
		{
			return;
		}

		this.revalidateComponentStates();
	}

	private void revalidateComponentStates()
	{
		this.components.forEach(c ->
		{
			boolean shouldBeEnabled = c.isEnabled(this.config);
			boolean isEnabled = this.states.get(c);
			if (shouldBeEnabled == isEnabled)
			{
				return;
			}

			if (shouldBeEnabled)
			{
				this.tryStartUp(c);
			}
			else
			{
				this.tryShutDown(c);
			}
		});
	}

	private void tryStartUp(PluginLifecycleComponent component)
	{
		if (this.states.get(component))
		{
			return;
		}

		log.debug("Enabling Custom Emoji component [{}]", component.getClass().getName());

		try
		{
			component.startUp();

			this.eventBus.register(component);
			if (component instanceof Overlay)
			{
				this.overlayManager.add((Overlay) component);
			}

			this.gameEventManager.simulateGameEvents(component);
			this.states.put(component, true);
		}
		catch (Throwable e)
		{
			log.error("Failed to start Custom Emoji component [{}]", component.getClass().getName(), e);
		}
	}

	private void tryShutDown(PluginLifecycleComponent component)
	{
		this.eventBus.unregister(component);
		if (component instanceof Overlay)
		{
			this.overlayManager.remove((Overlay) component);
		}

		if (!this.states.get(component))
		{
			return;
		}

		log.debug("Disabling Custom Emoji component [{}]", component.getClass().getName());

		try
		{
			component.shutDown();
		}
		catch (Throwable e)
		{
			log.error("Failed to cleanly shut down Custom Emoji component [{}]", component.getClass().getName());
		}
		finally
		{
			this.states.put(component, false);
		}
	}
}
