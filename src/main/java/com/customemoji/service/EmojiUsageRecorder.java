package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.model.Lifecycle;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Year;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class EmojiUsageRecorder implements Lifecycle
{
	private static final String USAGE_KEY_PREFIX = "usage_";

	private Map<String, Emoji> emojis;
	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Inject
	public EmojiUsageRecorder(ConfigManager configManager, Client client)
	{
		this.configManager = configManager;
		this.client = client;
	}

	@Subscribe
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		this.emojis = event.getEmojis();
	}

	@Override
	public void startUp()
	{
		this.eventBus.register(this);
	}

	@Override
	public void shutDown()
	{
		this.eventBus.unregister(this);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (this.emojis == null)
		{
			return;
		}

		boolean isRecordableType = this.isRecordableMessageType(event.getType());
		if (!isRecordableType)
		{
			return;
		}

		boolean isFromLocalPlayer = this.isFromLocalPlayer(event.getName());
		if (!isFromLocalPlayer)
		{
			return;
		}

		String message = event.getMessage();
		this.recordUsage(message);
	}

	private boolean isRecordableMessageType(ChatMessageType type)
	{
		return type == ChatMessageType.PUBLICCHAT
			|| type == ChatMessageType.PRIVATECHATOUT
			|| type == ChatMessageType.FRIENDSCHAT
			|| type == ChatMessageType.CLAN_CHAT;
	}

	private boolean isFromLocalPlayer(String senderName)
	{
		Player localPlayer = this.client.getLocalPlayer();
		if (localPlayer == null)
		{
			return false;
		}

		String localPlayerName = localPlayer.getName();
		if (localPlayerName == null)
		{
			return false;
		}

		String normalizedSender = Text.toJagexName(Text.removeTags(senderName));
		return localPlayerName.equals(normalizedSender);
	}

	private void recordUsage(String message)
	{;
		List<String> emojisFound = PluginUtils.findEmojiTriggersInMessage(message, this.emojis);

		for (String emojiName : emojisFound)
		{
			this.incrementCount(emojiName);
		}
	}

	private void incrementCount(String emojiName)
	{
		String key = USAGE_KEY_PREFIX + Year.now().getValue() + "_" + emojiName;
		String current = this.configManager.getConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, key);
		int count = (current != null) ? Integer.parseInt(current) : 0;
		this.configManager.setConfiguration(CustomEmojiConfig.KEY_CONFIG_GROUP, key, String.valueOf(count + 1));
		log.debug("Incremented usage count for {}: {}", emojiName, count + 1);
	}
}
