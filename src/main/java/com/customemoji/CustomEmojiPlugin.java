package com.customemoji;

import com.customemoji.animation.AnimationManager;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.BeforeEmojisLoaded;
import com.customemoji.event.EmojiStateChanged;
import com.customemoji.event.ReloadEmojisRequested;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.renderer.ChatEmojiRenderer;
import com.customemoji.renderer.NewMessageBannerRenderer;
import com.customemoji.renderer.OverheadEmojiRenderer;
import com.customemoji.service.EmojiStateManager;
import com.customemoji.service.LifecycleManager;
import com.google.inject.Provides;
import com.google.inject.Provider;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.customemoji.panel.CustomEmojiPanel;
import com.customemoji.panel.PanelConstants;
import com.customemoji.panel.StatusMessagePanel;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;

@Slf4j
@PluginDescriptor(
		name = "Custom Emoji",
		description = "Allows you to use custom emojis in chat messages",
		tags = {"emoji", "chat", "message", "custom", "icon", "emote", "text", "clan", "notification"}
)
public class CustomEmojiPlugin extends Plugin
{
	public static final String EMOJI_ERROR_COMMAND = "emojierror";
	public static final String EMOJI_FOLDER_COMMAND = "emojifolder";
	public static final String SOUNDOJI_FOLDER_COMMAND = "soundojifolder";

	public static final float NOISE_FLOOR = -60f;

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");
	private static final String IMG_TAG_PREFIX = "<img=";
	private static final int MAX_REGISTRATION_RETRIES = 10;
	private static final String UNKNOWN_EMOJI_PLACEHOLDER = "[?]";

	@Inject
	private EventBus eventBus;

	@Inject
	private CustomEmojiOverlay overlay;

	@Inject
	private CustomEmojiTooltip tooltip;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ChatSpacingManager chatSpacingManager;

	@Inject
	private AnimationManager animationManager;

	@Inject
	private ChatEmojiRenderer chatEmojiRenderer;

	@Inject
	private OverheadEmojiRenderer overheadEmojiRenderer;

	@Inject
	private NewMessageBannerRenderer newMessageBannerRenderer;

	@Inject
	private Provider<CustomEmojiPanel> panelProvider;

	@Inject
	private EmojiStateManager emojiStateManager;

	@Inject
	private EmojiLoader emojiLoader;

	@Inject
	private SoundojiLoader soundojiLoader;

	@Inject
	private GitHubEmojiDownloader githubDownloader;

	@Inject
	private LifecycleManager lifecycleManager;

	private CustomEmojiPanel panel;
	private NavigationButton navButton;

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
	public void onBeforeEmojisLoaded(BeforeEmojisLoaded event)
	{
		event.registerParticipant();
		this.replaceAllEmojisWithText(event.getOldEmojis());
		this.animationManager.clearAllAnimations();
		event.markComplete();
	}

	@Subscribe
	public void onAfterEmojisLoaded(AfterEmojisLoaded event)
	{
		this.clearRenderCaches();
		this.replaceAllTextWithEmojis();
	}

	@Override
	protected void startUp() throws Exception
	{
		this.toggleButton(this.config.showPanel());

		this.lifecycleManager.startUp();
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.replaceAllEmojisWithText();

		this.lifecycleManager.shutDown();

		if (this.panel != null)
		{
			this.panel.shutdown();
			this.toggleButton(false);
		}

		log.debug("Plugin shutdown complete");
	}

	public void triggerGitHubDownloadAndReload()
	{
		if (!PluginUtils.isGitHubDownloadConfigured(this.config))
		{
			this.showPanelStatus("GitHub download disabled - configure repository in settings", StatusMessagePanel.MessageType.WARNING);
			return;
		}

		Runnable onStarted = () ->
		{
			if (this.navButton != null)
			{
				SwingUtilities.invokeLater(() -> this.clientToolbar.openPanel(this.navButton));
			}
		};

		boolean hadPreviousDownload = this.githubDownloader.hasDownloadedBefore();

		this.replaceAllEmojisWithText();

		this.githubDownloader.downloadEmojis(this.config.githubRepoUrl(), onStarted, result ->
		{
			if (!result.isSuccess())
			{
				this.clientThread.invokeLater(() ->
					this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", result.formatMessage(), null));
			}
			else
			{
				StatusMessagePanel.MessageType messageType = StatusMessagePanel.MessageType.SUCCESS;
				this.showPanelStatus(result.formatPanelMessage(), messageType);
			}

			if (result.hasChanges())
			{
				List<String> changedNames = result.getChangedEmojiNames();
				if (this.panel != null && hadPreviousDownload)
				{
					this.panel.setPendingRecentlyDownloaded(changedNames);
				}
				this.eventBus.post(new ReloadEmojisRequested());
			}
			else
			{
				this.clientThread.invokeLater(this::replaceAllTextWithEmojis);
			}
		});
	}

	private void toggleButton(boolean show)
	{
		if (show)
		{
			panel = panelProvider.get();
			panel.setEventBus(this.eventBus);

			final BufferedImage icon = ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_SMILEY);
			navButton = NavigationButton.builder().tooltip("Custom Emoji").icon(icon).priority(5).panel(panel).build();

			clientToolbar.addNavigation(navButton);
		}
		else
		{
			if (navButton != null)
			{
				clientToolbar.removeNavigation(navButton);
				navButton = null;
			}
			if (panel != null)
			{
				panel.shutDownProgressPanel();
				panel = null;
			}
		}
	}

	private void showPanelStatus(String message, StatusMessagePanel.MessageType type)
	{
		if (this.panel != null)
		{
			SwingUtilities.invokeLater(() -> this.panel.showStatusMessage(message, type));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		this.clientThread.invokeLater(() -> this.newMessageBannerRenderer.onNewMessage());
		this.clientThread.invokeAtTickEnd(this.chatSpacingManager::clearStoredPositions);
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
	public void onScriptPostFired(ScriptPostFired event)
	{
		switch (event.getScriptId())
		{
			case 84:
				this.chatSpacingManager.applyChatSpacing();
			default:
				return;
		}
	}
	
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"chatFilterCheck".equals(event.getEventName()))
		{
			return;
		}

		CustomEmojiConfig.DisabledEmojiFilterMode filterMode = this.config.disabledEmojiFilterMode();

		if (filterMode == CustomEmojiConfig.DisabledEmojiFilterMode.OFF)
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

		boolean requireAll = filterMode == CustomEmojiConfig.DisabledEmojiFilterMode.LENIENT;
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
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Apply chat spacing when chat-related widgets are loaded
		int groupId = event.getGroupId();
		boolean isChatbox = groupId == InterfaceID.Chatbox.SCROLLAREA;
		boolean isPmChat = groupId == InterfaceID.PmChat.CONTAINER;

		if (isChatbox || isPmChat)
		{
			clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
		}
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
			case CustomEmojiConfig.KEY_DYNAMIC_EMOJI_SPACING:
			case CustomEmojiConfig.KEY_CHAT_MESSAGE_SPACING:
				this.clearRenderCaches();
				this.chatSpacingManager.applyChatSpacing();
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				this.clearRenderCaches();
				this.eventBus.post(new ReloadEmojisRequested(true));
				break;
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
			case CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS:
			case CustomEmojiConfig.KEY_MESSAGE_PROCESS_LIMIT:
				this.client.refreshChat();
				// intentional fallthrough
			case CustomEmojiConfig.KEY_ANIMATION_LOADING_MODE:
				this.clearRenderCaches();
				this.animationManager.clearAllAnimations();
				break;
			case CustomEmojiConfig.KEY_SHOW_SIDE_PANEL:
				this.toggleButton(this.config.showPanel());
				break;
			case CustomEmojiConfig.KEY_GITHUB_ADDRESS:
				this.triggerGitHubDownloadAndReload();
				break;
			case CustomEmojiConfig.KEY_SPLIT_PRIVATE_CHAT:
				this.clientThread.invokeLater(() -> 
				{
					this.client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
							"", 
							"<col=ff0000>Split private chat was causing some bugs and is temporarily disabled. Sorry :(</col>", 
							null
					);
				});

			default:
				break;
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		switch (event.getIndex())
		{
			case VarClientID.CHAT_VIEW:
				this.chatSpacingManager.clearStoredPositions();
			case VarClientID.MESLAYERMODE:
			case VarClientID.CHAT_LASTREBUILD:
				//this.chatSpacingManager.clearStoredPositions();
				// intentional fallthrough
			case VarClientID.CHAT_FORCE_CHATBOX_REBUILD: // Triggered when a friend logs in/out
				this.chatSpacingManager.applyChatSpacing();
				break;
			case VarClientID.CHAT_LASTSCROLLPOS:
				this.newMessageBannerRenderer.onScrollPositionChanged();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		int index = event.getIndex();
		String value = this.client.getVarcStrValue(index);

		if (value.startsWith("!") || value.startsWith("::"))
		{
			return; // Skip command messages
		}

		boolean isNormalChatInput  = index == VarClientID.CHATINPUT;
		boolean isPrivateChatInput = index == VarClientID.MESLAYERINPUT && this.client.getVarcIntValue(VarClientID.MESLAYERMODE) == 6;

		if (isNormalChatInput || isPrivateChatInput)
		{
			this.overlay.updateChatInput(value);
		}
	}

	@Subscribe
	public void onEmojiStateChanged(EmojiStateChanged event)
	{
		switch (event.getChangeType())
		{
			case ENABLED:
				this.replaceEmojiInChat(event.getEmojiName(), true);
				break;
			case DISABLED:
				this.replaceEmojiInChat(event.getEmojiName(), false);
				break;
			case RESIZING_TOGGLED:
				this.handleEmojiResizingToggled(event.getEmojiName());
				break;
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		this.tooltip.onMenuOpened();
	}

	@Nullable
	String updateMessage(final String message, boolean sound)
	{
		final String[] messageWords = WHITESPACE_REGEXP.split(message);

		boolean editedMessage = false;
		boolean previousWasEmoji = false;

		for (int i = 0; i < messageWords.length; i++)
		{
			// Remove tags except for <lt> and <gt>
			final String trigger = Text.removeFormattingTags(messageWords[i]);
			final Emoji emoji = this.provideEmojis().get(trigger.toLowerCase());
			final Soundoji soundoji = this.provideSoundojis().get(trigger.toLowerCase());

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
				// Check if this word is already an image tag (existing emoji)
				previousWasEmoji = messageWords[i].contains(IMG_TAG_PREFIX);
			}

			if (soundoji != null)
			{
				if (sound)
				{
					try
					{
						this.audioPlayer.play(soundoji.getFile(), volumeToGain(this.config.volume()));
					}
					catch (Exception e)
					{
						log.error("Failed to play soundoji: " + soundoji.getText(), e);
					}
				}
				messageWords[i] = messageWords[i].replace(trigger, "*" + trigger + "*");
				editedMessage = true;
				log.debug("Playing soundoji {}", trigger);
			}

		}

		// If we haven't edited the message any, don't update it.
		if (!editedMessage)
		{
			return null;
		}

		return String.join(" ", messageWords);
	}

	private int getImageIdForEmoji(Emoji emoji, boolean previousWasEmoji)
	{
		if (emoji.hasZeroWidthId() && previousWasEmoji)
		{
			return this.chatIconManager.chatIconIndex(emoji.getZeroWidthIndex());
		}
		return this.chatIconManager.chatIconIndex(emoji.getIndex());
	}

	private void replaceEmojiInChat(String emojiName, boolean showAsImage)
	{
		Emoji emoji = this.provideEmojis().get(emojiName);
		if (emoji == null)
		{
			return;
		}

		this.waitForRegistration(emoji, () ->
		{
			this.populateImageId(emoji);
			this.processAllChatMessages(value ->
			{
				if (showAsImage)
				{
					String updated = this.updateMessage(value, false);
					return updated != null ? updated : value;
				}
				return this.replaceEmojiTagsWithText(value, emoji, emojiName);
			});
		});
	}

	private void replaceAllEmojisWithText()
	{
		this.replaceAllEmojisWithText(this.provideEmojis());
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

	private void replaceAllTextWithEmojis()
	{
		List<Emoji> enabledEmojis = this.provideEmojis().values().stream()
			.filter(emoji -> this.emojiStateManager.isEmojiEnabled(emoji.getText()))
			.collect(Collectors.toList());

		this.waitForRegistration(enabledEmojis, () ->
		{
			this.populateImageIds(enabledEmojis);
			this.processAllChatMessages(message ->
			{
				String updated = this.updateMessage(message, false);
				return updated != null ? updated : message;
			});
		});
	}

	private void processAllChatMessages(UnaryOperator<String> transformer)
	{
		log.debug("Processing all chat messages...");
		IterableHashTable<MessageNode> messages = this.client.getMessages();
		for (MessageNode messageNode : messages)
		{
			ChatMessageType type = messageNode.getType();
			String value = messageNode.getValue();

			boolean shouldProcess = shouldUpdateChatMessage(type) && value != null;
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

	private void waitForRegistration(Emoji emoji, Runnable onRegistered)
	{
		this.waitForRegistration(List.of(emoji), onRegistered, 0);
	}

	private void waitForRegistration(Collection<Emoji> emojis, Runnable onRegistered)
	{
		this.waitForRegistration(emojis, onRegistered, 0);
	}

	private void waitForRegistration(Collection<Emoji> emojis, Runnable onRegistered, int retryCount)
	{
		boolean allRegistered = emojis.stream()
			.allMatch(emoji -> this.chatIconManager.chatIconIndex(emoji.getIndex()) >= 0);

		if (allRegistered)
		{
			onRegistered.run();
			return;
		}

		if (retryCount >= MAX_REGISTRATION_RETRIES)
		{
			log.warn("Max retries reached waiting for {} emoji(s) registration", emojis.size());
			return;
		}

		this.clientThread.invokeLater(() -> this.waitForRegistration(emojis, onRegistered, retryCount + 1));
	}

	private void populateImageIds(Collection<Emoji> emojis)
	{
		for (Emoji emoji : emojis)
		{
			this.populateImageId(emoji);
		}
	}

	private void populateImageId(Emoji emoji)
	{
		int imageId = this.chatIconManager.chatIconIndex(emoji.getIndex());
		emoji.setIconId(imageId);

		if (emoji.hasZeroWidthId())
		{
			int zeroWidthImageId = this.chatIconManager.chatIconIndex(emoji.getZeroWidthIndex());
			emoji.setZeroWidthIconId(zeroWidthImageId);
		}
	}

	private void handleEmojiResizingToggled(String emojiName)
	{
		Emoji emoji = this.provideEmojis().get(emojiName);
		if (emoji instanceof AnimatedEmoji)
		{
			this.animationManager.invalidateAnimation(emoji.getIndex());
		}
	}

	private void clearRenderCaches()
	{
		this.chatEmojiRenderer.resetCache();
		this.overheadEmojiRenderer.resetCache();
	}

	public static float volumeToGain(int volume100)
	{
		// range[NOISE_FLOOR, 0]
		float gainDB;

		// Graph of the function
		// https://www.desmos.com/calculator/wdhsfbxgeo

		// clamp to 0-100
		float volume = Math.min(100, volume100);
		// convert linear volume 0-100 to log control
		if (volume <= 0.1)
		{
			gainDB = NOISE_FLOOR;
		}
		else
		{
			gainDB = (float) (10 * (Math.log(volume / 100)));
		}

		return gainDB;
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
				return false;
				//boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;
				//return (this.config.splitPrivateChat() && splitChatEnabled) || !splitChatEnabled;
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
			Emoji emoji = this.provideEmojis().get(trigger);
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

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}

	public Map<String, Emoji> provideEmojis()
	{
		return this.emojiLoader.getEmojis();
	}

	private Map<String, Soundoji> provideSoundojis()
	{
		return this.soundojiLoader.getSoundojis();
	}

	public void openConfiguration()
	{
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
	}
}
