package com.customemoji.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.customemoji.ChatScrollingManager;
import com.customemoji.ChatSpacingManager;
import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiOverlay;
import com.customemoji.CustomEmojiTooltip;
import com.customemoji.animation.AnimationManager;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.model.Lifecycle;
import com.customemoji.renderer.ChatEmojiRenderer;
import com.customemoji.renderer.NewMessageBannerRenderer;
import com.customemoji.renderer.OverheadEmojiRenderer;
import com.customemoji.renderer.SplitPrivateChatEmojiRenderer;
import com.google.inject.Inject;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public class LifecycleManager
{
	private final Map<Lifecycle, Boolean> managedObjects = new LinkedHashMap<>();
	
	@Inject
	private EventBus eventBus;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	public LifecycleManager
	(
		AnimationManager animationManager,
		ChatEmojiRenderer chatEmojiRenderer,
		ChatScrollingManager chatScrollingManager,
		ChatSpacingManager chatSpacingManager,
		CustomEmojiOverlay customEmojiOverlay,
		CustomEmojiTooltip customEmojiTooltip,
		EmojiLoader emojiLoader,
		EmojiStateManager emojiStateManager,
		EmojiUsageRecorder emojiUsageRecorder,
		GitHubEmojiDownloader gitHubEmojiDownloader,
		NewMessageBannerRenderer newMessageBannerRenderer,
		OverheadEmojiRenderer overheadEmojiRenderer,
		SplitPrivateChatEmojiRenderer splitPrivateChatEmojiRenderer
	)
	{
		this.managedObjects.put(animationManager, false);
		this.managedObjects.put(chatEmojiRenderer, false);
		this.managedObjects.put(chatScrollingManager, false);
		this.managedObjects.put(chatSpacingManager, false);
		this.managedObjects.put(customEmojiOverlay, false);
		this.managedObjects.put(customEmojiTooltip, false);
		this.managedObjects.put(emojiLoader, false);
		this.managedObjects.put(emojiStateManager, false);
		this.managedObjects.put(emojiUsageRecorder, false);
		this.managedObjects.put(gitHubEmojiDownloader, false);
		this.managedObjects.put(newMessageBannerRenderer, false);
		this.managedObjects.put(overheadEmojiRenderer, false);
		this.managedObjects.put(splitPrivateChatEmojiRenderer, false);
	}

	public void startUp()
	{
		this.eventBus.register(this);
		this.updateManagedObjects();
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);

		for (Map.Entry<Lifecycle, Boolean> entry : this.managedObjects.entrySet())
		{
			if (entry.getValue())
			{
				entry.getKey().shutDown();
				entry.setValue(false);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("custom-emote"))
		{
			return;
		}

		this.updateManagedObjects();
	}

	private void updateManagedObjects()
	{
		for (Map.Entry<Lifecycle, Boolean> entry : this.managedObjects.entrySet())
		{
			boolean isEnabled = entry.getKey().isEnabled(this.config);
			boolean isActive = entry.getValue();

			if (isEnabled && !isActive)
			{
				entry.getKey().startUp();
				entry.setValue(true);
			}
			else if (!isEnabled && isActive)
			{
				entry.getKey().shutDown();
				entry.setValue(false);
			}
		}
	}
}
