package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.animation.AnimationManager;
import com.customemoji.model.Lifecycle;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatEmojiRenderer extends EmojiWidgetRenderer implements Lifecycle
{
	@Inject
	private AnimationManager animationManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CustomEmojiPlugin plugin;

	@Inject
	public ChatEmojiRenderer(Client client, CustomEmojiConfig config, EventBus eventBus)
	{
		super(client, config, eventBus, InterfaceID.Chatbox.SCROLLAREA);
		this.setPriority(0.5f);
	}

	@Override
	public void startUp()
	{
		this.emojisSupplier = this.plugin::provideEmojis;
		this.animationLoader = this.animationManager::getOrLoadAnimation;
		this.markVisibleCallback = this.animationManager::markAnimationVisible;
		this.unloadStaleCallback = this.animationManager::unloadStaleAnimations;
		this.overlayManager.add(this);
	}

	@Override
	public void shutDown()
	{
		this.overlayManager.remove(this);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}
}
