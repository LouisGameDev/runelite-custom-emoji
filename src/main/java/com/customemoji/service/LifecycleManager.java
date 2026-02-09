package com.customemoji.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.customemoji.ChatScrollingManager;
import com.customemoji.ChatSpacingManager;
import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiOverlay;
import com.customemoji.CustomEmojiTooltip;
import com.customemoji.animation.AnimationManager;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.model.Lifecycle;
import com.customemoji.service.SoundojiPlayer;
import com.customemoji.panel.EmojiPanelManager;
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
		GitHubEmojiDownloader githubDownloader,
		EmojiUsageRecorder usageRecorder,
		CustomEmojiOverlay overlay,
		CustomEmojiTooltip tooltip,
		NewMessageBannerRenderer newMessageBannerRenderer,
		ChatSpacingManager chatSpacingManager,
		ChatScrollingManager chatScrollingManager,
		EmojiLoader emojiLoader,
		SoundojiLoader soundojiLoader,
		SoundojiPlayer soundojiPlayer,
		EmojiMessageManager messageManager,
		ChatEmojiRenderer chatEmojiRenderer,
		OverheadEmojiRenderer overheadEmojiRenderer,
		SplitPrivateChatEmojiRenderer splitPrivateChatEmojiRenderer,
		EmojiPanelManager emojiPanelManager
	)
	{
		this.managedObjects.put(animationManager, false);
		this.managedObjects.put(githubDownloader, false);
		this.managedObjects.put(usageRecorder, false);
		this.managedObjects.put(overlay, false);
		this.managedObjects.put(tooltip, false);
		this.managedObjects.put(newMessageBannerRenderer, false);
		this.managedObjects.put(chatSpacingManager, false);
		this.managedObjects.put(chatScrollingManager, false);
		this.managedObjects.put(emojiLoader, false);
		this.managedObjects.put(soundojiLoader, false);
		this.managedObjects.put(soundojiPlayer, false);
		this.managedObjects.put(messageManager, false);
		this.managedObjects.put(chatEmojiRenderer, false);
		this.managedObjects.put(overheadEmojiRenderer, false);
		this.managedObjects.put(splitPrivateChatEmojiRenderer, false);
		this.managedObjects.put(emojiPanelManager, false);
	}

	public void startUp()
	{
		this.eventBus.register(this);
		this.updateManagedObjects();
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);

		List<Map.Entry<Lifecycle, Boolean>> entries = new ArrayList<>(this.managedObjects.entrySet());
		Collections.reverse(entries);

		for (Map.Entry<Lifecycle, Boolean> entry : entries)
		{
			boolean isRunning = entry.getValue();

			if (isRunning)
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
