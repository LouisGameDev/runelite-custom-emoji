package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Singleton
public class EmojiUsageRecorder
{
	private static final String USAGE_KEY_PREFIX = "usage_";

	private final ConfigManager configManager;
	private final Client client;
	private Supplier<Map<String, Emoji>> emojisSupplier;

	@Inject
	public EmojiUsageRecorder(ConfigManager configManager, Client client)
	{
		this.configManager = configManager;
		this.client = client;
	}

	public void setEmojisSupplier(Supplier<Map<String, Emoji>> supplier)
	{
		this.emojisSupplier = supplier;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (this.emojisSupplier == null)
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
	{
		Map<String, Emoji> emojis = this.emojisSupplier.get();
		List<String> emojisFound = PluginUtils.findEmojiTriggersInMessage(message, emojis);

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
