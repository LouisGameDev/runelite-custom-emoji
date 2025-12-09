package com.customemoji;

import com.customemoji.animation.AnimatedEmojiOverlay;
import com.customemoji.animation.OverheadAnimatedEmojiOverlay;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.google.inject.Provides;
import com.google.inject.Provider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarClientIntChanged;
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
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.customemoji.panel.CustomEmojiPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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

	private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
	private static final String PRINT_EMOJIS_MESSAGE = "print-emojis";
	private static final String PRINT_EMOJIS_FILTER_KEY = "filter";
	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

	@Inject
	private EventBus eventBus;

	@Inject
	private CustomEmojiOverlay overlay;

	@Inject
	private CustomEmojiTooltip tooltip;

	@Inject
	private AnimatedEmojiOverlay animatedEmojiOverlay;

	@Inject
	private OverheadAnimatedEmojiOverlay overheadAnimatedEmojiOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ChatSpacingManager chatSpacingManager;

	@Inject
	private Provider<CustomEmojiPanel> panelProvider;

	@Inject
	private EmojiLoader emojiLoader;

	@Inject
	private SoundojiLoader soundojiLoader;
	private final Map<String, Soundoji> soundojis = new HashMap<>();
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
				for (String error : this.emojiLoader.getErrors())
				{
					this.client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			default:
				break;
		}
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

	private Boolean parseAnimatedFilter(Object filterValue)
	{
		if (filterValue instanceof Boolean)
		{
			return (Boolean) filterValue;
		}
		return null;
	}

	@Override
	protected void startUp() throws Exception
	{
		this.emojiLoader.loadInitialEmojis(this::onInitialLoadComplete);
		this.soundojiLoader.loadSoundojis(this.soundojis);

		this.overlay.startUp();
		this.overlayManager.add(this.overlay);

		this.tooltip.startUp();
		this.overlayManager.add(this.tooltip);

		if (this.config.enableAnimatedEmojis())
		{
			this.overlayManager.add(this.animatedEmojiOverlay);
			this.overlayManager.add(this.overheadAnimatedEmojiOverlay);
		}

		this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);

		try
		{
			Path[] watchPaths = new Path[]{EmojiLoader.EMOJIS_FOLDER.toPath(), SoundojiLoader.SOUNDOJIS_FOLDER.toPath()};
			this.emojiLoader.startWatching(watchPaths, this::onReloadComplete);
		}
		catch (IOException e)
		{
			log.error("Failed to setup file watcher", e);
		}
	}

	private void onInitialLoadComplete()
	{
		if (config.showPanel())
		{
			this.showButton();
		}

		List<String> loadErrors = this.emojiLoader.getErrors();
		if (!loadErrors.isEmpty())
		{
			String message = "<col=FF0000>Custom Emoji: There were " + loadErrors.size()
					+ " errors loading emojis.<br><col=FF0000>Use <col=00FFFF>::emojierror <col=FF0000>to see them.";
			this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.emojiLoader.shutdown();
		this.chatSpacingManager.clearStoredPositions();

		this.overlay.shutDown();
		this.overlayManager.remove(this.overlay);

		this.tooltip.shutDown();
		this.overlayManager.remove(this.tooltip);

		this.overlayManager.remove(this.animatedEmojiOverlay);
		this.overlayManager.remove(this.overheadAnimatedEmojiOverlay);

		if (this.panel != null)
		{
			this.hideButton();
		}

		// Clear soundojis - AudioPlayer handles clip management automatically
		this.soundojis.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		switch (chatMessage.getType())
		{
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				break;
			default:
				return;
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		final String message = messageNode.getValue();
		final String updatedMessage = updateMessage(message, true);

		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
	}

	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		final String message = event.getOverheadText();
		final String updatedMessage = updateMessage(message, false);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.Chatbox.SCROLLAREA:
				clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("custom-emote"))
		{
			return;
		}
		boolean shouldRefreshPanel = true;

		switch (event.getKey())
		{
			case CustomEmojiConfig.KEY_CHAT_MESSAGE_SPACING:
				this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				this.emojiLoader.scheduleReload(true);
				break;
			case CustomEmojiConfig.KEY_SHOW_SIDE_PANEL:
				if (this.config.showPanel())
				{
					this.showButton();
				}
				else
				{
					this.hideButton();
				}
				break;
			case CustomEmojiConfig.KEY_ENABLE_ANIMATED_EMOJIS:
				if (this.config.enableAnimatedEmojis())
				{
					this.overlayManager.add(this.animatedEmojiOverlay);
					this.overlayManager.add(this.overheadAnimatedEmojiOverlay);
				}
				else
				{
					this.overlayManager.remove(this.animatedEmojiOverlay);
					this.overlayManager.remove(this.overheadAnimatedEmojiOverlay);
				}
				break;
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
				// Panel already updated itself, skip redundant refresh
				shouldRefreshPanel = false;
				break;
			default:
				break;
		}

		if (shouldRefreshPanel && this.panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.updateFromConfig());
		}

	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		switch (event.getIndex())
		{
			case VarClientID.CHAT_LASTREBUILD:
				this.chatSpacingManager.clearStoredPositions();
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::applyChatSpacing);
				break;
			case VarClientID.CHAT_LASTSCROLLPOS:
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::captureScrollPosition);
				break;
			case VarClientID.CHATBOX_MODE:
			default:
				break;
		}
	}

	@Nullable
	String updateMessage(final String message, boolean sound)
	{
		final String[] messageWords = WHITESPACE_REGEXP.split(message);

		boolean editedMessage = false;
		for (int i = 0; i < messageWords.length; i++)
		{
			// Remove tags except for <lt> and <gt>
			final String trigger = Text.removeFormattingTags(messageWords[i]);

			final Emoji emoji = this.emojiLoader.getEmojis().get(trigger.toLowerCase());
			final Soundoji soundoji = this.soundojis.get(trigger.toLowerCase());

			Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
			if (emoji != null && PluginUtils.isEmojiEnabled(emoji.getText(), disabledEmojis))
			{
				messageWords[i] = messageWords[i].replace(trigger,
						"<img=" + this.chatIconManager.chatIconIndex(emoji.getId()) + ">");
				editedMessage = true;
				log.debug("Replacing {} with emoji {}", trigger, emoji.getText());
			}

			if (soundoji != null)
			{
				if (sound)
				{
					try
					{
						this.audioPlayer.play(soundoji.getFile(), PluginUtils.volumeToGain(this.config.volume()));
					}
					catch (IOException | UnsupportedAudioFileException | LineUnavailableException e)
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

	public void reloadSelectedEmojis(List<String> emojiNames, Runnable onComplete)
	{
		this.emojiLoader.reloadSelectedEmojis(emojiNames, onComplete);
	}

	private void printEmojis(Boolean animatedFilter)
	{
		Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
		StringBuilder sb = new StringBuilder();

		String label = this.getEmojiPrintLabel(animatedFilter);
		sb.append(label);

		for (Emoji emoji : this.emojiLoader.getEmojis().values())
		{
			boolean isEnabled = PluginUtils.isEmojiEnabled(emoji.getText(), disabledEmojis);
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

	private void onReloadComplete(int emojiCount)
	{
		String message = String.format("<col=00FF00>Custom Emoji: Reloaded %d emojis", emojiCount);
		this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);

		if (this.panel != null)
		{
			SwingUtilities.invokeLater(this.panel::refreshEmojiTree);
		}
	}

	private void showButton()
	{
		panel = panelProvider.get();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "../../com/customemoji/smiley.png");

		this.navButton = NavigationButton.builder()
			.tooltip("Custom Emoji")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		
		SwingUtilities.invokeLater(panel::refreshEmojiTree);
	}

	private void hideButton()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}

	@Provides
	@Named("disabledEmojis")
	Set<String> provideDisabledEmojis()
	{
		return PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
	}

	@Provides
	@Named("resizingDisabledEmojis")
	Set<String> provideResizingDisabledEmojis()
	{
		return PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
	}

	public void openConfiguration()
	{
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
	}
}
