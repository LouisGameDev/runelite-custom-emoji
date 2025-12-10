package com.customemoji.features.chat;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.util.PluginUtils;
import com.customemoji.features.loader.EmojiLoader;
import com.customemoji.features.loader.SoundojiLoader;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.customemoji.module.PluginLifecycleComponent;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

/**
 * Handles chat message processing, emoji replacement, and soundoji playback.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class ChatMessageHandler implements PluginLifecycleComponent
{
	private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
	private static final String PRINT_EMOJIS_MESSAGE = "print-emojis";
	private static final String PRINT_EMOJIS_FILTER_KEY = "filter";
	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

	private final Client client;
	private final CustomEmojiConfig config;
	private final EmojiLoader emojiLoader;
	private final SoundojiLoader soundojiLoader;
	private final ChatIconManager chatIconManager;
	private final AudioPlayer audioPlayer;

	private Set<String> cachedDisabledEmojis = Set.of();

	@Override
	public void startUp()
	{
		this.cachedDisabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CustomEmojiConfig.KEY_CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (CustomEmojiConfig.KEY_DISABLED_EMOJIS.equals(event.getKey()))
		{
			this.cachedDisabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (!this.isSupportedChatType(chatMessage.getType()))
		{
			return;
		}

		MessageNode messageNode = chatMessage.getMessageNode();
		String message = messageNode.getValue();
		String updatedMessage = this.updateMessage(message, true);

		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		String message = event.getOverheadText();
		String updatedMessage = this.updateMessage(message, false);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		boolean isOurMessage = PLUGIN_MESSAGE_NAMESPACE.equals(event.getNamespace());
		boolean isPrintMessage = PRINT_EMOJIS_MESSAGE.equals(event.getName());

		if (!isOurMessage || !isPrintMessage)
		{
			return;
		}

		Object filterValue = event.getData().get(PRINT_EMOJIS_FILTER_KEY);
		Boolean animatedFilter = this.parseAnimatedFilter(filterValue);
		this.printEmojis(animatedFilter);
	}

	private boolean isSupportedChatType(ChatMessageType type)
	{
		return type == ChatMessageType.PUBLICCHAT
			|| type == ChatMessageType.MODCHAT
			|| type == ChatMessageType.FRIENDSCHAT
			|| type == ChatMessageType.CLAN_CHAT
			|| type == ChatMessageType.CLAN_GUEST_CHAT
			|| type == ChatMessageType.CLAN_GIM_CHAT
			|| type == ChatMessageType.PRIVATECHAT
			|| type == ChatMessageType.PRIVATECHATOUT
			|| type == ChatMessageType.MODPRIVATECHAT;
	}

	@Nullable
	public String updateMessage(String message, boolean playSound)
	{
		String[] messageWords = WHITESPACE_REGEXP.split(message);

		boolean editedMessage = false;
		for (int i = 0; i < messageWords.length; i++)
		{
			String trigger = Text.removeFormattingTags(messageWords[i]);
			String triggerLower = trigger.toLowerCase();

			Emoji emoji = this.emojiLoader.getEmojis().get(triggerLower);
			Soundoji soundoji = this.soundojiLoader.getSoundojis().get(triggerLower);

			if (emoji != null && PluginUtils.isEmojiEnabled(emoji.getText(), this.cachedDisabledEmojis))
			{
				int iconIndex = this.chatIconManager.chatIconIndex(emoji.getId());
				messageWords[i] = messageWords[i].replace(trigger, "<img=" + iconIndex + ">");
				editedMessage = true;
				log.debug("Replacing {} with emoji {}", trigger, emoji.getText());
			}

			if (soundoji != null)
			{
				if (playSound)
				{
					this.playSoundoji(soundoji);
				}
				messageWords[i] = messageWords[i].replace(trigger, "*" + trigger + "*");
				editedMessage = true;
				log.debug("Playing soundoji {}", trigger);
			}
		}

		if (!editedMessage)
		{
			return null;
		}

		return String.join(" ", messageWords);
	}

	private void playSoundoji(Soundoji soundoji)
	{
		try
		{
			this.audioPlayer.play(soundoji.getFile(), PluginUtils.volumeToGain(this.config.volume()));
		}
		catch (IOException | UnsupportedAudioFileException | LineUnavailableException e)
		{
			log.error("Failed to play soundoji: {}", soundoji.getText(), e);
		}
	}

	private void printEmojis(Boolean animatedFilter)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(this.getEmojiPrintLabel(animatedFilter));

		for (Emoji emoji : this.emojiLoader.getEmojis().values())
		{
			boolean isEnabled = PluginUtils.isEmojiEnabled(emoji.getText(), this.cachedDisabledEmojis);
			boolean isAnimated = emoji instanceof AnimatedEmoji;
			boolean matchesFilter = animatedFilter == null || animatedFilter == isAnimated;

			if (isEnabled && matchesFilter)
			{
				sb.append(emoji.getText()).append(" ");
			}
		}

		String message = this.updateMessage(sb.toString(), false);
		this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
	}

	private String getEmojiPrintLabel(Boolean animatedFilter)
	{
		if (animatedFilter == null)
		{
			return "Enabled emoji: ";
		}

		if (animatedFilter)
		{
			return "Enabled animated emoji: ";
		}

		return "Enabled static emoji: ";
	}

	private Boolean parseAnimatedFilter(Object filterValue)
	{
		if (filterValue instanceof Boolean)
		{
			return (Boolean) filterValue;
		}
		return null;
	}
}
