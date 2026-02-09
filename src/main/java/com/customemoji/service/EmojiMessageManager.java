package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiConfig.DisabledEmojiFilterMode;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.AfterSoundojisLoaded;
import com.customemoji.event.BeforeEmojisLoaded;
import com.customemoji.event.EmojiStateChanged;
import com.customemoji.event.GitHubDownloadStarted;
import com.customemoji.event.SoundojiTriggered;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.model.Emoji;
import com.customemoji.model.Lifecycle;
import com.customemoji.model.Soundoji;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class EmojiMessageManager implements Lifecycle
{
	public static final String EMOJI_ERROR_COMMAND = "emojierror";
	public static final String EMOJI_FOLDER_COMMAND = "emojifolder";
	public static final String SOUNDOJI_FOLDER_COMMAND = "soundojifolder";

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");
	private static final String IMG_TAG_PREFIX = "<img=";
	private static final String UNKNOWN_EMOJI_PLACEHOLDER = "[?]";

	@Inject
	private EventBus eventBus;

	@Inject
	private Client client;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private EmojiStateManager emojiStateManager;

	@Inject
	private EmojiLoader emojiLoader;

	@Inject
	private GitHubEmojiDownloader githubDownloader;

	private Map<String, Emoji> emojis = new HashMap<>();
	private Map<String, Soundoji> soundojis = new HashMap<>();

	@Override
	public void startUp()
	{
		this.eventBus.register(this);
	}

	@Override
	public void shutDown()
	{
		this.replaceAllEmojisWithText();
		this.eventBus.unregister(this);
		this.emojis.clear();
		this.soundojis.clear();
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	@Subscribe
	public void onBeforeEmojisLoaded(BeforeEmojisLoaded event)
	{
		event.registerParticipant();
		this.replaceAllEmojisWithText(event.getOldEmojis());
		event.markComplete();
	}

	@Subscribe
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		this.emojis = event.getEmojis();
		this.replaceAllTextWithEmojis();
	}

	@Subscribe
	public void onAfterSoundojisLoaded(AfterSoundojisLoaded event)
	{
		this.soundojis = event.getSoundojis();
	}

	@Subscribe
	public void onGitHubDownloadStarted(GitHubDownloadStarted event)
	{
		this.replaceAllEmojisWithText();
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		switch (e.getCommand())
		{
			case EMOJI_FOLDER_COMMAND:
				LinkBrowser.open(EmojiLoader.EMOJIS_FOLDER.toString());
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				LinkBrowser.open(SoundojiLoader.SOUNDOJIS_FOLDER.toString());
				break;
			case EMOJI_ERROR_COMMAND:
				List<String> errorList = this.emojiLoader.getErrors();
				for (String error : errorList)
				{
					this.client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (!this.shouldUpdateChatMessage(chatMessage.getType()))
		{
			return;
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		final String message = messageNode.getValue();
		final String updatedMessage = this.updateMessage(message, true);
		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"chatFilterCheck".equals(event.getEventName()))
		{
			return;
		}

		int[] intStack = this.client.getIntStack();
		int intStackSize = this.client.getIntStackSize();
		Object[] objectStack = this.client.getObjectStack();
		int objectStackSize = this.client.getObjectStackSize();

		int messageId = intStack[intStackSize - 1];
		MessageNode messageNode = this.client.getMessages().get(messageId);
		String senderName = Text.toJagexName(Text.removeTags(messageNode.getName()));
		String localPlayerName = this.client.getLocalPlayer().getName();
		boolean isFromLocalPlayer = senderName.equals(localPlayerName);

		if (isFromLocalPlayer)
		{
			return;
		}

		String message = (String) objectStack[objectStackSize - 1];

		DisabledEmojiFilterMode filterMode = this.config.disabledEmojiFilterMode();
		boolean requireAll = filterMode == DisabledEmojiFilterMode.LENIENT;
		boolean shouldFilter = this.shouldFilterMessage(message, requireAll);

		if (shouldFilter)
		{
			intStack[intStackSize - 3] = 0;
		}
	}

	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		final String message = event.getOverheadText();
		final String updatedMessage = this.updateMessage(message, false);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("custom-emote"))
		{
			return;
		}

		switch (event.getKey())
		{
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
			case CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS:
			case CustomEmojiConfig.KEY_MESSAGE_PROCESS_LIMIT:
				this.client.refreshChat();
				break;
			case CustomEmojiConfig.KEY_SPLIT_PRIVATE_CHAT:
				this.client.addChatMessage(
					ChatMessageType.GAMEMESSAGE,
					"",
					"<col=ff0000>Split private chat was causing some bugs and is temporarily disabled. Sorry :(</col>",
					null
				);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onEmojiStateChanged(EmojiStateChanged event)
	{
		switch (event.getChangeType())
		{
			case ENABLED:
			case DISABLED:
				this.replaceAllEmojisWithText();
				this.replaceAllTextWithEmojis();
				break;
			default:
				break;
		}
	}

	public void replaceAllEmojisWithText()
	{
		this.replaceAllEmojisWithText(this.emojis);
	}

	public void replaceAllTextWithEmojis()
	{
		this.processAllChatMessages(message ->
		{
			String updated = this.updateMessage(message, false);
			return updated != null ? updated : message;
		});
	}

	@Nullable
	private String updateMessage(final String message, boolean sound)
	{
		final String[] messageWords = WHITESPACE_REGEXP.split(message);

		boolean editedMessage = false;
		boolean previousWasEmoji = false;

		for (int i = 0; i < messageWords.length; i++)
		{
			final String trigger = Text.removeFormattingTags(messageWords[i]);
			final Emoji emoji = this.emojis.get(trigger.toLowerCase());

			if (emoji != null && this.emojiStateManager.isEmojiEnabled(emoji.getText()))
			{
				int imageId = this.getImageIdForEmoji(emoji, previousWasEmoji);
				messageWords[i] = messageWords[i].replace(trigger, IMG_TAG_PREFIX + imageId + ">");
				editedMessage = true;
				previousWasEmoji = true;
				log.debug("Replacing {} with emoji {}", trigger, emoji.getText());
			}
			else
			{
				previousWasEmoji = messageWords[i].contains(IMG_TAG_PREFIX);
			}

			if (this.soundojis.containsKey(trigger.toLowerCase()))
			{
				if (sound)
				{
					this.eventBus.post(new SoundojiTriggered(trigger));
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

		private boolean shouldUpdateChatMessage(ChatMessageType type)
	{
		if (this.emojiLoader.isLoading.get() || this.githubDownloader.isDownloading.get())
		{
			return false;
		}

		switch (type)
		{
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				return this.client.getVarpValue(VarPlayerID.OPTION_PM) == 0;
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
				return true;
			default:
				return false;
		}
	}

	private boolean shouldFilterMessage(String message, boolean requireAll)
	{
		String[] messageWords = WHITESPACE_REGEXP.split(message);
		int disabledCount = 0;
		int wordCount = 0;

		for (String word : messageWords)
		{
			String trigger = Text.removeFormattingTags(word).replaceAll("(^\\p{Punct}+)|(\\p{Punct}+$)", "").toLowerCase();
			boolean shouldSkip = trigger.isEmpty() || trigger.endsWith("00");

			if (shouldSkip)
			{
				continue;
			}

			wordCount++;
			Emoji emoji = this.emojis.get(trigger);
			boolean isDisabled = emoji != null && !this.emojiStateManager.isEmojiEnabled(emoji.getText());

			if (isDisabled)
			{
				disabledCount++;
				if (!requireAll)
				{
					return true;
				}
			}
			else if (requireAll)
			{
				return false;
			}
		}

		return requireAll && disabledCount > 0 && disabledCount == wordCount;
	}

	private int getImageIdForEmoji(Emoji emoji, boolean previousWasEmoji)
	{
		if (emoji.hasZeroWidthId() && previousWasEmoji)
		{
			return this.chatIconManager.chatIconIndex(emoji.getZeroWidthIndex());
		}
		return this.chatIconManager.chatIconIndex(emoji.getIndex());
	}

	private void replaceAllEmojisWithText(Map<String, Emoji> emojisToReplace)
	{
		this.processAllChatMessages(value ->
		{
			String updated = value;
			for (Emoji emoji : emojisToReplace.values())
			{
				String emojiText = emoji.getText();
				boolean hasValidText = emojiText != null && !emojiText.isEmpty();
				String replacement = hasValidText ? emojiText : UNKNOWN_EMOJI_PLACEHOLDER;
				updated = this.replaceEmojiTagsWithText(updated, emoji, replacement);
			}
			return updated;
		});
	}

	private String replaceEmojiTagsWithText(String message, Emoji emoji, String replacement)
	{
		int imageId = this.chatIconManager.chatIconIndex(emoji.getIndex());
		String imageTag = IMG_TAG_PREFIX + imageId + ">";

		String updated = message.replace(imageTag, replacement);

		if (emoji.hasZeroWidthId())
		{
			int zeroWidthId = this.chatIconManager.chatIconIndex(emoji.getZeroWidthIndex());
			String zeroWidthTag = IMG_TAG_PREFIX + zeroWidthId + ">";
			updated = updated.replace(zeroWidthTag, replacement);
		}

		return updated;
	}

	private void processAllChatMessages(UnaryOperator<String> transformer)
	{
		log.debug("Processing all chat messages...");
		IterableHashTable<MessageNode> messages = this.client.getMessages();
		for (MessageNode messageNode : messages)
		{
			ChatMessageType type = messageNode.getType();
			String value = messageNode.getValue();

			boolean shouldProcess = this.shouldUpdateChatMessage(type) && value != null;
			if (shouldProcess)
			{
				String updatedValue = transformer.apply(value);
				if (!updatedValue.equals(value))
				{
					messageNode.setValue(updatedValue);
				}
			}
		}
		this.client.refreshChat();
	}
}
