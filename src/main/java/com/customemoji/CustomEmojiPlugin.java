package com.customemoji;

import com.customemoji.io.EmojiLoader;
import com.customemoji.io.FileUtils;
import com.customemoji.io.FileWatcher;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.google.common.io.Resources;
import com.google.inject.Provides;
import com.google.inject.Provider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import lombok.Getter;
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
	public static final String PRINT_ALL_EMOJI_COMMAND = "emojiprint";

	public static final URL EXAMPLE_EMOJI = Resources.getResource(CustomEmojiPlugin.class, "checkmark.png");
	public static final URL EXAMPLE_SOUNDOJI = Resources.getResource(CustomEmojiPlugin.class, "customemoji.wav");

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

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
	private FileWatcher fileWatcher;

	@Inject
	private SoundojiLoader soundojiLoader;

	@Getter
	protected final Map<String, Emoji> emojis = new HashMap<>();
	private final Map<String, Soundoji> soundojis = new HashMap<>();
	private final List<String> errors = new ArrayList<>();
	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	private void firstTimeSetup()
	{
		if (EmojiLoader.EMOJIS_FOLDER.mkdir())
		{
			// copy example emoji
			File exampleEmoji = new File(EmojiLoader.EMOJIS_FOLDER, "com/customemoji/checkmark.png");
			try (InputStream in = EXAMPLE_EMOJI.openStream())
			{
				Files.copy(in, exampleEmoji.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to copy example emoji", e);
			}
		}

		if (SoundojiLoader.SOUNDOJIS_FOLDER.mkdir())
		{
			// copy example soundoji
			File exampleSoundoji = new File(SoundojiLoader.SOUNDOJIS_FOLDER, "com/customemoji/customemoji.wav");
			try (InputStream in = EXAMPLE_SOUNDOJI.openStream())
			{
				Files.copy(in, exampleSoundoji.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to copy example soundoji", e);
			}
		}
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

				for (String error : errors)
				{
					client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			case PRINT_ALL_EMOJI_COMMAND:
				StringBuilder sb = new StringBuilder();

				sb.append("Currently loaded emoji: ");

				for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
				{
					sb.append(entry.getKey()).append(" ");
				}

				String message = updateMessage(sb.toString(), false);
				client.addChatMessage(ChatMessageType.CONSOLE, "Currently loaded emoji", message, null);

				break;
			default:
				break;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		firstTimeSetup();

		this.emojiLoader.loadEmojis(EmojiLoader.EMOJIS_FOLDER, this.emojis);
		this.soundojiLoader.loadSoundojis(this.soundojis);

		if (config.showPanel())
		{
			showButton();
		}

		overlay.startUp();
		overlayManager.add(overlay);

		tooltip.startUp();
		overlayManager.add(tooltip);

		// Apply initial chat spacing
		clientThread.invokeLater(chatSpacingManager::applyChatSpacing);

		try
		{
			Path[] watchPaths = new Path[]{EmojiLoader.EMOJIS_FOLDER.toPath(), SoundojiLoader.SOUNDOJIS_FOLDER.toPath()};
			this.fileWatcher.start(watchPaths, this::reloadEmojis);
		}
		catch (IOException e)
		{
			log.error("Failed to setup file watcher", e);
		}

		if (!errors.isEmpty())
		{
			clientThread.invokeLater(() ->
			{
				String message =
						"<col=FF0000>Custom Emoji: There were " + errors.size() +
								" errors loading emojis and soundojis.<br><col=FF0000>Use <col=00FFFF>::emojierror <col=FF0000>to see them.";
				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
			});
		}
		else
		{
			log.debug("<col=00FF00>Custom Emoji: Loaded " + emojis.size() + soundojis.size() + " emojis and soundojis.");
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.fileWatcher.shutdown();
		this.emojis.clear();
		errors.clear();
		chatSpacingManager.clearStoredPositions();

		overlay.shutDown();
		overlayManager.remove(overlay);

		tooltip.shutDown();
		overlayManager.remove(tooltip);

		if (panel != null)
		{
			hideButton();
		}

		// Clear soundojis - AudioPlayer handles clip management automatically
		soundojis.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
	}

	private void showButton()
	{
		// Create panel lazily after emojis are loaded
		panel = panelProvider.get();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "../../com/customemoji/smiley.png");

		navButton = NavigationButton.builder()
			.tooltip("Custom Emoji")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
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
				clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				this.fileWatcher.scheduleReload(true);
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

			final Emoji emoji = emojis.get(trigger.toLowerCase());
			final Soundoji soundoji = soundojis.get(trigger.toLowerCase());

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

	public void reloadSingleEmoji(String emojiName)
	{
		this.emojiLoader.reloadSingleEmoji(emojiName, this.emojis);
	}

	private void reloadEmojis(boolean force)
	{
		log.info("Reloading emojis and soundojis due to file changes");

		// Store current emoji names for deletion detection
		Set<String> currentEmojiNames = new HashSet<>(emojis.keySet());

		if (force)
		{
			emojis.clear();
		}

		soundojis.clear();

		errors.clear();

		// Reload emojis (using updateChatIcon for existing, registerChatIcon for new)
		File emojiFolder = EmojiLoader.EMOJIS_FOLDER;
		if (emojiFolder.exists())
		{
			Result<List<Emoji>, List<Throwable>> result = this.emojiLoader.loadEmojisFromFolder(emojiFolder, this.emojis);

			// Track which emojis are still present
			Set<String> newEmojiNames = new HashSet<>();
			result.ifOk(list ->
			{
				list.forEach(e ->
				{
					this.emojis.put(e.getText(), e);
					newEmojiNames.add(e.getText());
				});
				log.info("Loaded {} emojis", result.unwrap().size());
			});
			result.ifError(e ->
				e.forEach(t ->
				{
					String fileName = FileUtils.extractFileNameFromErrorMessage(t.getMessage());
					log.debug("Skipped non-emoji file: {}", fileName);
				})
			);

			// Remove deleted emojis from our map
			currentEmojiNames.removeAll(newEmojiNames);
			currentEmojiNames.forEach(deletedEmoji ->
			{
				log.debug("Removing deleted emoji: {}", deletedEmoji);
				this.emojis.remove(deletedEmoji);
			});
		}
		else
		{
			log.warn("Emoji folder does not exist: {}", emojiFolder);
			emojis.clear();
		}

		this.soundojiLoader.loadSoundojis(this.soundojis);

		String message = String.format("<col=00FF00>Custom Emoji: Reloaded %d emojis and %d soundojis", emojis.size(), soundojis.size());

		client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);

		// Refresh the panel to show updated emoji tree
		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refreshEmojiTree());
		}
	}

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}

	@Provides
	Map<String, Emoji> provideEmojis()
	{
		return this.emojis;
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
