package com.customemoji;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;
import com.customemoji.animation.AnimationManager;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.customemoji.model.StaticEmoji;
import com.customemoji.io.GitHubEmojiDownloader;
import com.customemoji.service.EmojiStateManager;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.google.inject.Provider;
import okhttp3.OkHttpClient;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.util.Iterator;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.NonNull;
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
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.RuneLite;
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

	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	public static final URL EXAMPLE_EMOJI = Resources.getResource(CustomEmojiPlugin.class, "checkmark.png");
	public static final URL EXAMPLE_SOUNDOJI = Resources.getResource(CustomEmojiPlugin.class, "customemoji.wav");

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
	private AnimationManager animationManager;

	@Inject
	private ChatEmojiRenderer chatEmojiRenderer;

	@Inject
	private OverheadEmojiRenderer overheadEmojiRenderer;

	@Inject
	private Provider<CustomEmojiPanel> panelProvider;

	@Inject
	private EmojiStateManager emojiStateManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private GitHubEmojiDownloader githubDownloader;

	@Getter
	protected final Map<String, Emoji> emojis = new ConcurrentHashMap<>();
	private final Map<String, Soundoji> soundojis = new ConcurrentHashMap<>();
	private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

	@lombok.Value
	private static class LoadedEmoji
	{
		String name;
		File file;
		long lastModified;
		BufferedImage image;
		Integer existingId;
		boolean animated;
	}

	private ScheduledExecutorService debounceExecutor;
	private ScheduledFuture<?> pendingReload;
	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	private void setup()
	{
		if (EMOJIS_FOLDER.mkdir())
		{
			// copy example emoji
			File exampleEmoji = new File(EMOJIS_FOLDER, "checkmark.png");
			try (InputStream in = EXAMPLE_EMOJI.openStream())
			{
				Files.copy(in, exampleEmoji.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to copy example emoji", e);
			}
		}

		if (SOUNDOJIS_FOLDER.mkdir())
		{
			// copy example soundoji
			File exampleSoundoji = new File(SOUNDOJIS_FOLDER, "customemoji.wav");
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
				LinkBrowser.open(EMOJIS_FOLDER.toString());
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				LinkBrowser.open(SOUNDOJIS_FOLDER.toString());
				break;
			case EMOJI_ERROR_COMMAND:

				for (String error : errors)
				{
					client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			default:
				break;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		setup();
		this.animationManager.initialize();

		this.githubDownloader = new GitHubEmojiDownloader(this.okHttpClient, this.gson, this.executor);

		this.emojiStateManager.setOnEmojiEnabled(this::replaceEnabledEmojiInChat);
		this.emojiStateManager.setOnEmojiDisabled(this::replaceDisabledEmojiInChat);
		this.emojiStateManager.setOnEmojiResizingToggled(this::handleEmojiResizingToggled);

		loadSoundojis();

		if (this.isGitHubDownloadConfigured())
		{
			this.triggerGitHubDownloadForStartup();
		}
		else
		{
			this.loadEmojisAsync(this::replaceAllTextWithEmojis);
		}

		if (config.showPanel())
		{
			showButton();
		}

		overlay.startUp();
		overlayManager.add(overlay);

		tooltip.startUp();
		overlayManager.add(tooltip);

		// Set up animation overlays (they check config.enableAnimatedEmojis() during render)
		this.setupAnimationOverlays();

		this.chatSpacingManager.setEmojiLookupSupplier(() ->
			PluginUtils.buildEmojiLookup(() -> this.emojis));

		// Apply initial chat spacing
		clientThread.invokeLater(chatSpacingManager::applyChatSpacing);

		// Create executor for debouncing reloads (many files changed at once, potentially from a git pull)
		debounceExecutor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "CustomEmoji-Debouncer");
			t.setDaemon(true);
			return t;
		});

		if (!errors.isEmpty())
		{
			String message = "There were " + errors.size() + " errors loading emojis. Use ::emojierror to see them.";
			this.showPanelStatus(message, StatusMessagePanel.MessageType.ERROR, false);
		}
		else
		{
			log.debug("Custom Emoji: Loaded " + emojis.size() + soundojis.size() + " emojis and soundojis.");
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.replaceAllEmojisWithText();

		this.githubDownloader.shutdown();
		this.shutdownDebounceExecutor();
		emojis.clear();
		errors.clear();
		chatSpacingManager.clearStoredPositions();

		overlay.shutDown();
		overlayManager.remove(overlay);

		tooltip.shutDown();
		overlayManager.remove(tooltip);

		// Clean up animation overlays
		this.teardownAnimationOverlays();
		this.animationManager.shutdown();

		if (panel != null)
		{
			hideButton();
		}

		// Clear soundojis - AudioPlayer handles clip management automatically
		soundojis.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
	}

	private void triggerGitHubDownloadForStartup()
	{
		this.githubDownloader.downloadEmojis(this.config.githubRepoUrl(), null, result ->
		{
			StatusMessagePanel.MessageType messageType = result.isSuccess()
				? StatusMessagePanel.MessageType.SUCCESS
				: StatusMessagePanel.MessageType.ERROR;
			this.showPanelStatus(result.formatPanelMessage(), messageType);

			this.loadEmojisAsync(this::replaceAllTextWithEmojis);
		});
	}

	public void triggerGitHubDownloadAndReload()
	{
		if (!this.isGitHubDownloadConfigured())
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
				this.forceReloadChangedEmojis(changedNames);
			}

			this.clientThread.invokeLater(this::replaceAllTextWithEmojis);
		});
	}

	public boolean isGitHubDownloadConfigured()
	{
		String repoIdentifier = this.config.githubRepoUrl();
		return repoIdentifier != null && !repoIdentifier.trim().isEmpty();
	}

	private String formatReloadMessage(int added, int deleted, int soundojis)
	{
		List<String> parts = new ArrayList<>();
		if (added > 0)
		{
			parts.add(String.format("Loaded %d emoji%s", added, added == 1 ? "" : "s"));
		}
		if (deleted > 0)
		{
			parts.add(String.format("Removed %d emoji%s", deleted, deleted == 1 ? "" : "s"));
		}
		if (soundojis > 0)
		{
			parts.add(String.format("(%d soundoji%s)", soundojis, soundojis == 1 ? "" : "s"));
		}

		return parts.isEmpty() ? "Already up to date" : String.join(", ", parts);
	}

	private void showButton()
	{
		// Create panel lazily after emojis are loaded
		panel = panelProvider.get();
		panel.setProgressSupplier(this.githubDownloader::getCurrentProgress);

		final BufferedImage icon = ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_SMILEY);
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
		if (panel != null)
		{
			panel.stopProgressPolling();
			panel = null;
		}
	}

	private void showPanelStatus(String message, StatusMessagePanel.MessageType type)
	{
		if (this.panel != null)
		{
			SwingUtilities.invokeLater(() -> this.panel.showStatusMessage(message, type));
		}
	}

	private void showPanelStatus(String message, StatusMessagePanel.MessageType type, boolean autoDismiss)
	{
		if (this.panel != null)
		{
			SwingUtilities.invokeLater(() -> this.panel.showStatusMessage(message, type, autoDismiss));
		}
	}

	private void setupAnimationOverlays()
	{
		this.chatEmojiRenderer.setEmojisSupplier(() -> this.emojis);
		this.chatEmojiRenderer.setAnimationLoader(this.animationManager::getOrLoadAnimation);
		this.chatEmojiRenderer.setMarkVisibleCallback(this.animationManager::markAnimationVisible);
		this.chatEmojiRenderer.setUnloadStaleCallback(this.animationManager::unloadStaleAnimations);
		this.overlayManager.add(this.chatEmojiRenderer);

		this.overheadEmojiRenderer.setEmojisSupplier(() -> this.emojis);
		this.overheadEmojiRenderer.setAnimationLoader(this.animationManager::getOrLoadAnimation);
		this.overheadEmojiRenderer.setMarkVisibleCallback(this.animationManager::markAnimationVisible);
		this.overlayManager.add(this.overheadEmojiRenderer);

		log.debug("Animation overlays set up");
	}

	private void teardownAnimationOverlays()
	{
		this.overlayManager.remove(this.chatEmojiRenderer);
		this.overlayManager.remove(this.overheadEmojiRenderer);
		this.animationManager.clearAllAnimations();
		log.debug("Animation overlays torn down");
	}

	private void shutdownDebounceExecutor()
	{
		if (this.pendingReload != null)
		{
			this.pendingReload.cancel(true);
			this.pendingReload = null;
		}

		if (this.debounceExecutor != null)
		{
			this.debounceExecutor.shutdownNow();
			this.debounceExecutor = null;
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

		CustomEmojiConfig.DisabledEmojiFilterMode filterMode = this.config.disabledEmojiFilterMode();

		if (filterMode == CustomEmojiConfig.DisabledEmojiFilterMode.OFF)
		{
			return;
		}

		int[] intStack = this.client.getIntStack();
		int intStackSize = this.client.getIntStackSize();
		Object[] objectStack = this.client.getObjectStack();
		int objectStackSize = this.client.getObjectStackSize();

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
		if (event.getGroupId() == InterfaceID.Chatbox.SCROLLAREA)
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
				clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				scheduleReload(true);
				break;
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
				// Panel refresh handled at end of method
				break;
			case CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS:
				this.animationManager.clearAllAnimations();
				this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_ANIMATION_LOADING_MODE:
			case CustomEmojiConfig.KEY_ENABLE_ANIMATED_EMOJIS:
				this.animationManager.clearAllAnimations();
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
			case CustomEmojiConfig.KEY_GITHUB_ADDRESS:
				this.triggerGitHubDownloadAndReload();
				break;
			default:
				break;
		}

		if (this.panel != null)
		{
			SwingUtilities.invokeLater(panel::updateFromConfig);
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

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		int index = event.getIndex();
		String value = this.client.getVarcStrValue(index);

		boolean isNormalChatInput  = index == VarClientID.CHATINPUT;
		boolean isPrivateChatInput = index == VarClientID.MESLAYERINPUT;

		boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;

		if (isNormalChatInput || (isPrivateChatInput && !splitChatEnabled)) // Split chat + Private messages unsupported
		{
			this.overlay.updateChatInput(value);
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

			final Emoji emoji = emojis.get(trigger.toLowerCase());
			final Soundoji soundoji = soundojis.get(trigger.toLowerCase());

			if (emoji != null && this.isEmojiEnabled(emoji.getText()))
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

	private int getImageIdForEmoji(Emoji emoji, boolean previousWasEmoji)
	{
		if (emoji.hasZeroWidthId() && previousWasEmoji)
		{
			return this.chatIconManager.chatIconIndex(emoji.getZeroWidthId());
		}
		return this.chatIconManager.chatIconIndex(emoji.getId());
	}

	boolean isEmojiEnabled(String emojiName)
	{
		return this.emojiStateManager.isEmojiEnabled(emojiName);
	}

	public void loadEmojis()
	{
		this.loadEmojisAsync(null);
	}

	private void loadEmojisAsync(Runnable onComplete)
	{
		File emojiFolder = EMOJIS_FOLDER;
		if (emojiFolder.mkdir())
		{
			log.debug("Created emoji folder");
			if (onComplete != null)
			{
				onComplete.run();
			}
			return;
		}

		this.executor.submit(() ->
		{
			Result<List<LoadedEmoji>, List<Throwable>> result = this.prepareEmojisFromFolder(emojiFolder);

			this.clientThread.invokeLater(() ->
			{
				result.ifOk(loadedList ->
				{
					List<Emoji> registered = this.registerLoadedEmojis(loadedList);
					registered.forEach(emoji -> this.emojis.put(emoji.getText(), emoji));
					log.info("Loaded {} emojis", registered.size());
				});

				result.ifError(loadErrors ->
				{
					for (Throwable throwable : loadErrors)
					{
						String message = throwable.getMessage();
						boolean isSkippedFile = message.contains("image format not supported")
							|| message.contains("Illegal file name")
							|| message.contains("file unchanged");
						if (!isSkippedFile)
						{
							log.warn("Failed to load emoji: {}", message);
							this.errors.add(message);
						}
					}
				});

				// Refresh the panel to show updated emoji tree
				if (this.panel != null)
				{
					SwingUtilities.invokeLater(() -> this.panel.refreshEmojiTree());
				}

				if (onComplete != null)
				{
					this.clientThread.invokeLater(() -> this.clientThread.invokeLater(onComplete));
				}
			});
		});
	}

	private void loadSoundojis()
	{
		File soundojiFolder = SOUNDOJIS_FOLDER;
		if (soundojiFolder.mkdir())
		{
			log.error("Created soundoji folder");
		}

		var result = this.loadSoundojisFolder(soundojiFolder);
		result.ifOk(list ->
		{
			list.forEach(e -> this.soundojis.put(e.getText(), e));
			log.info("Loaded {} soundojis", result.unwrap().size());
		});
		result.ifError(e ->
			e.forEach(t ->
			{
				String fileName = extractFileName(t.getMessage());
				log.debug("Skipped non-audio file: {}", fileName);
			})
		);
	}

	private Result<List<Soundoji>, List<Throwable>> loadSoundojisFolder(File soundojiFolder)
	{
		// recursively flattenFolder files in the folder
		List<File> files = flattenFolder(soundojiFolder);

		if (!soundojiFolder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder " + soundojiFolder)));
		}

		List<Soundoji> loaded = new ArrayList<>();
		List<Throwable> localErrors = new ArrayList<>();

		for (File file : files)
		{
			Result<Soundoji, Throwable> result = loadSoundoji(file);
			result.ifOk(loaded::add);
			result.ifError(localErrors::add);
		}

		if (localErrors.isEmpty())
		{
			return Ok(loaded);
		}
		else
		{
			return PartialOk(loaded, localErrors);
		}
	}

	private Result<List<LoadedEmoji>, List<Throwable>> prepareEmojisFromFolder(File folder)
	{
		List<File> files = flattenFolder(folder);

		if (!folder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder " + folder)));
		}

		List<LoadedEmoji> prepared = new ArrayList<>();
		List<Throwable> prepareErrors = new ArrayList<>();

		for (File file : files)
		{
			Result<LoadedEmoji, Throwable> result = this.prepareEmoji(file);
			result.ifOk(prepared::add);
			result.ifError(prepareErrors::add);
		}

		if (prepareErrors.isEmpty())
		{
			return Ok(prepared);
		}
		else
		{
			return PartialOk(prepared, prepareErrors);
		}
	}

	private Result<LoadedEmoji, Throwable> prepareEmoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			return Error(new IllegalArgumentException("Illegal file name <col=00FFFF>" + file));
		}

		String name = file.getName().substring(0, extension).toLowerCase();
		long fileModified = file.lastModified();

		Emoji existingEmoji = this.emojis.get(name);

		if (existingEmoji != null && existingEmoji.getLastModified() == fileModified)
		{
			return Error(new IllegalArgumentException("Emoji file unchanged: " + name));
		}

		// Local emoji priority: if a local emoji exists and the new file is from github-pack, skip it
		if (existingEmoji != null)
		{
			boolean existingIsLocal = !existingEmoji.getFile().getPath().contains("github-pack");
			boolean newIsGithub = file.getPath().contains("github-pack");
			if (existingIsLocal && newIsGithub)
			{
				return Error(new IllegalArgumentException("Skipped - local emoji takes priority: " + name));
			}
		}

		Result<BufferedImage, Throwable> imageResult = loadImage(file);

		if (!imageResult.isOk())
		{
			Throwable throwable = imageResult.unwrapError();
			return Error(new RuntimeException(
				"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + throwable.getMessage(),
				throwable));
		}

		try
		{
			boolean shouldResize = this.shouldResizeEmoji(name);
			BufferedImage normalizedImage = shouldResize ? PluginUtils.resizeImage(imageResult.unwrap(), this.config.maxImageHeight()) : imageResult.unwrap();
			boolean isAnimated = PluginUtils.isAnimatedGif(file);
			Integer existingId = existingEmoji != null ? existingEmoji.getId() : null;

			return Ok(new LoadedEmoji(name, file, fileModified, normalizedImage, existingId, isAnimated));
		}
		catch (RuntimeException e)
		{
			return Error(new RuntimeException(
				"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + e.getMessage(),
				e));
		}
	}

	private List<Emoji> registerLoadedEmojis(List<LoadedEmoji> loadedEmojis)
	{
		List<Emoji> registered = new ArrayList<>();

		for (LoadedEmoji loaded : loadedEmojis)
		{
			Emoji emoji = this.registerLoadedEmoji(loaded);
			registered.add(emoji);
		}

		return registered;
	}

	private Emoji registerLoadedEmoji(LoadedEmoji loaded)
	{
		int width = loaded.getImage().getWidth();
		int height = loaded.getImage().getHeight();
		Dimension dim = new Dimension(width, height);
		String name = loaded.getName();
		File file = loaded.getFile();
		long lastModified = loaded.getLastModified();
		Integer existingId = loaded.getExistingId();

		BufferedImage staticImage = loaded.getImage();
		BufferedImage placeholderImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int iconId;
		if (existingId != null)
		{
			iconId = existingId;
			this.chatIconManager.updateChatIcon(iconId, placeholderImage);
			log.info("Updated existing chat icon for emoji: {} (id: {})", name, iconId);
		}
		else
		{
			iconId = this.chatIconManager.registerChatIcon(placeholderImage);
			log.info("Registered new chat icon for emoji: {} (id: {})", name, iconId);
		}

		int zeroWidthId = -1;
		boolean isZeroWidth = name.endsWith("00");
		if (isZeroWidth)
		{
			BufferedImage zeroWidthPlaceholder = new BufferedImage(1, height, BufferedImage.TYPE_INT_ARGB);
			zeroWidthId = this.chatIconManager.registerChatIcon(zeroWidthPlaceholder);
			log.info("Registered zero-width placeholder for emoji: {} (zeroWidthId: {})", name, zeroWidthId);
		}

		if (loaded.isAnimated())
		{
			return new AnimatedEmoji(iconId, zeroWidthId, -1, -1, name, file, lastModified, dim, staticImage);
		}

		return new StaticEmoji(iconId, zeroWidthId, -1, -1, name, file, lastModified, dim, staticImage);
	}

	private Result<Soundoji, Throwable> loadSoundoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			return Error(new IllegalArgumentException("Illegal file name " + file));
		}

		String text = file.getName().substring(0, extension).toLowerCase();
		return Ok(new Soundoji(text, file));

	}


	private List<File> flattenFolder(@NonNull File folder)
	{
		return flattenFolder(folder, 0);
	}

	private List<File> flattenFolder(@NonNull File folder, int depth)
	{
		// sanity guard
		final long MAX_DEPTH = 8;

		if (depth > MAX_DEPTH)
		{
			log.warn("Max depth of {} was reached path:{}", depth, folder);
			return List.of();
		}

		// file found
		if (!folder.isDirectory())
		{
			return List.of(folder);
		}

		// no childs
		File[] childs = folder.listFiles();
		if (childs == null)
		{
			return List.of();
		}

		List<File> flattened = new ArrayList<>();
		for (File child : childs)
		{
			flattened.addAll(flattenFolder(child, depth + 1));
		}

		return flattened;
	}

	public void forceReloadChangedEmojis(List<String> emojiNames)
	{
		if (emojiNames == null || emojiNames.isEmpty())
		{
			return;
		}

		for (String emojiName : emojiNames)
		{
			this.emojis.remove(emojiName);
			log.debug("Removed emoji '{}' from cache to force reload", emojiName);
		}

		this.scheduleReload(false, false);
	}

	public void reloadSingleEmoji(String emojiName)
	{
		this.reloadSingleEmoji(emojiName, null);
	}

	public void reloadSingleEmoji(String emojiName, Runnable onComplete)
	{
		Emoji emoji = this.emojis.get(emojiName);
		if (emoji == null)
		{
			log.warn("Cannot reload emoji '{}' - not found", emojiName);
			if (onComplete != null)
			{
				onComplete.run();
			}
			return;
		}

		File file = emoji.getFile();

		this.executor.submit(() ->
		{
			Result<BufferedImage, Throwable> imageResult = loadImage(file);

			if (!imageResult.isOk())
			{
				log.error("Failed to load image for emoji '{}'", emojiName, imageResult.unwrapError());
				if (onComplete != null)
				{
					this.clientThread.invokeLater(onComplete);
				}
				return;
			}

			try
			{
				boolean shouldResize = this.shouldResizeEmoji(emojiName);
				BufferedImage normalizedImage = shouldResize ? PluginUtils.resizeImage(imageResult.unwrap(), this.config.maxImageHeight()) : imageResult.unwrap();
				long fileModified = file.lastModified();
				boolean isAnimated = PluginUtils.isAnimatedGif(file);
				LoadedEmoji loaded = new LoadedEmoji(emojiName, file, fileModified, normalizedImage, emoji.getId(), isAnimated);

				this.clientThread.invokeLater(() ->
				{
					Emoji updatedEmoji = this.registerLoadedEmoji(loaded);
					updatedEmoji.setImageId(emoji.getImageId());
					updatedEmoji.setZeroWidthImageId(emoji.getZeroWidthImageId());
					this.emojis.put(emojiName, updatedEmoji);
					log.info("Reloaded emoji '{}' with resizing={}", emojiName, shouldResize);

					if (onComplete != null)
					{
						onComplete.run();
					}
				});
			}
			catch (RuntimeException e)
			{
				log.error("Failed to reload emoji '{}'", emojiName, e);
				if (onComplete != null)
				{
					this.clientThread.invokeLater(onComplete);
				}
			}
		});
	}

	/**
	 * Determines if a specific emoji should be resized based on per-emoji settings.
	 * Returns true if the emoji is NOT in the resizing disabled list.
	 */
	private boolean shouldResizeEmoji(String emojiName)
	{
		return this.emojiStateManager.isResizingEnabled(emojiName);
	}

	private void replaceEnabledEmojiInChat(String emojiName)
	{
		this.replaceEmojiInChat(emojiName, true);
	}

	private void replaceDisabledEmojiInChat(String emojiName)
	{
		this.replaceEmojiInChat(emojiName, false);
	}

	private void replaceEmojiInChat(String emojiName, boolean showAsImage)
	{
		Emoji emoji = this.emojis.get(emojiName);
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
		this.processAllChatMessages(value ->
		{
			String updated = value;
			for (Emoji emoji : this.emojis.values())
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
		int imageId = this.chatIconManager.chatIconIndex(emoji.getId());
		String imageTag = IMG_TAG_PREFIX + imageId + ">";

		String updated = message.replace(imageTag, replacement);

		if (emoji.hasZeroWidthId())
		{
			int zeroWidthId = this.chatIconManager.chatIconIndex(emoji.getZeroWidthId());
			String zeroWidthTag = IMG_TAG_PREFIX + zeroWidthId + ">";
			updated = updated.replace(zeroWidthTag, replacement);
		}

		return updated;
	}

	private void replaceAllTextWithEmojis()
	{
		List<Emoji> enabledEmojis = this.emojis.values().stream()
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
			.allMatch(emoji -> this.chatIconManager.chatIconIndex(emoji.getId()) >= 0);

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
		int imageId = this.chatIconManager.chatIconIndex(emoji.getId());
		emoji.setImageId(imageId);

		if (emoji.hasZeroWidthId())
		{
			int zeroWidthImageId = this.chatIconManager.chatIconIndex(emoji.getZeroWidthId());
			emoji.setZeroWidthImageId(zeroWidthImageId);
		}
	}

	private void handleEmojiResizingToggled(String emojiName)
	{
		Emoji emoji = this.emojis.get(emojiName);
		if (emoji instanceof AnimatedEmoji)
		{
			this.animationManager.invalidateAnimation(emoji.getId());
		}

		this.reloadSingleEmoji(emojiName, this.chatSpacingManager::applyChatSpacing);
	}

	public static Result<BufferedImage, Throwable> loadImage(final File file)
	{
		String fileName = file.getName().toLowerCase();
		boolean isSupportedFormat = fileName.endsWith(".png") ||
									fileName.endsWith(".jpg") ||
									fileName.endsWith(".jpeg") ||
									fileName.endsWith(".gif");

		if (!isSupportedFormat)
		{
			return Error(new IOException("image format not supported. (PNG,JPG,GIF only)"));
		}

		try (ImageInputStream imageStream = ImageIO.createImageInputStream(file))
		{
			if (imageStream != null)
			{
				Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
				while (readers.hasNext())
				{
					ImageReader reader = readers.next();
					try
					{
						reader.setInput(imageStream);
						BufferedImage image = reader.read(0);
						if (image != null)
						{
							return Ok(image);
						}
					}
					finally
					{
						reader.dispose();
					}
				}
			}
		}
		catch (IOException e)
		{
			// Primary method failed, do InputStream fallback
		}

		try (InputStream in = new FileInputStream(file))
		{
			synchronized (ImageIO.class)
			{
				BufferedImage read = ImageIO.read(in);
				if (read != null)
				{
					return Ok(read);
				}
			}
		}
		catch (IllegalArgumentException | IOException e)
		{
			return Error(e);
		}

		return Error(new IOException("failed to read image"));
	}

	private static String extractFileName(String errorMessage)
	{
		// Extract just the filename from error messages like:
		// "<col=FF0000>filename.ext</col> failed because..."
		// or "Illegal file name <col=00FFFF>C:\full\path\filename"
		if (errorMessage.contains("<col="))
		{
			int start = errorMessage.indexOf(">");
			int end = errorMessage.indexOf("</col>");
			if (start != -1 && end != -1 && start < end)
			{
				String fullPath = errorMessage.substring(start + 1, end);
				// Extract just the filename from full path
				return fullPath.substring(fullPath.lastIndexOf(File.separator) + 1);
			}
		}

		// Fallback: try to extract filename from full path
		if (errorMessage.contains(File.separator))
		{
			String[] parts = errorMessage.split("[" + Pattern.quote(File.separator) + "]");
			if (parts.length > 0)
			{
				return parts[parts.length - 1];
			}
		}

		return errorMessage;
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

	private void reloadEmojis(boolean force, boolean showStatus)
	{
		log.info("Reloading emojis and soundojis due to file changes");

		// Replace all emoji images with text on the client thread, then continue reload
		this.clientThread.invokeLater(() ->
		{
			this.replaceAllEmojisWithText();
			this.continueReloadAfterTextReplacement(force, showStatus);
		});
	}

	private void continueReloadAfterTextReplacement(boolean force, boolean showStatus)
	{
		// Store current emoji names for deletion detection
		Set<String> currentEmojiNames = new HashSet<>(this.emojis.keySet());

		if (force)
		{
			this.emojis.clear();
		}

		this.soundojis.clear();
		this.errors.clear();

		File emojiFolder = EMOJIS_FOLDER;
		if (!emojiFolder.exists())
		{
			log.warn("Emoji folder does not exist: {}", emojiFolder);
			this.emojis.clear();
			this.loadSoundojis();
			return;
		}

		// Run heavy I/O work on background thread
		this.executor.submit(() ->
		{
			Result<List<LoadedEmoji>, List<Throwable>> result = this.prepareEmojisFromFolder(emojiFolder);

			// Register with ChatIconManager on client thread
			this.clientThread.invokeLater(() ->
			{
				Set<String> newEmojiNames = new HashSet<>();

				int[] addedCount = {0};

				result.ifOk(loadedList ->
				{
					List<Emoji> registered = this.registerLoadedEmojis(loadedList);
					registered.forEach(emoji ->
					{
						this.emojis.put(emoji.getText(), emoji);
						newEmojiNames.add(emoji.getText());
					});
					addedCount[0] = registered.size();
					log.info("Loaded {} emojis", registered.size());
				});

				result.ifError(loadErrors ->
				{
					for (Throwable throwable : loadErrors)
					{
						String message = throwable.getMessage();

						// Track unchanged emojis to prevent deletion
						if (message.contains("file unchanged"))
						{
							int colonIndex = message.lastIndexOf(": ");
							if (colonIndex != -1)
							{
								String name = message.substring(colonIndex + 2);
								newEmojiNames.add(name);
							}
							continue;
						}

						boolean isSkippedFile = message.contains("image format not supported")
							|| message.contains("Illegal file name");
						if (!isSkippedFile)
						{
							log.warn("Failed to load emoji: {}", message);
							this.errors.add(message);
						}
					}
				});

				// Remove deleted emojis from our map
				currentEmojiNames.removeAll(newEmojiNames);
				currentEmojiNames.forEach(deletedEmoji ->
				{
					log.debug("Removing deleted emoji: {}", deletedEmoji);
					this.emojis.remove(deletedEmoji);
				});

				this.loadSoundojis();

				int deletedCount = currentEmojiNames.size();
				if (showStatus)
				{
					String statusMessage = this.formatReloadMessage(addedCount[0], deletedCount, this.soundojis.size());
					this.showPanelStatus(statusMessage, StatusMessagePanel.MessageType.SUCCESS);
				}

				// Refresh the panel to show updated emoji tree
				if (this.panel != null)
				{
					SwingUtilities.invokeLater(() -> this.panel.refreshEmojiTree());
				}

				this.clientThread.invokeLater(this::replaceAllTextWithEmojis);
			});
		});
	}

	public void scheduleReload(boolean force)
	{
		this.scheduleReload(force, true);
	}

	public void scheduleReload(boolean force, boolean showStatus)
	{
		synchronized (this)
		{
			// Don't schedule reload if debounceExecutor is null or shutdown (during shutdown)
			if (this.debounceExecutor == null || this.debounceExecutor.isShutdown())
			{
				log.debug("Skipping reload schedule - executor is shutdown");
				return;
			}

			// Cancel any pending reload
			if (pendingReload != null && !pendingReload.isDone())
			{
				pendingReload.cancel(false);
				log.debug("Cancelled pending emoji reload due to new file changes");
			}

			// Schedule new reload with debounce delay
			pendingReload = debounceExecutor.schedule(() -> this.reloadEmojis(force, showStatus), 500, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with 500ms debounce");
		}
	}

	private boolean shouldUpdateChatMessage(ChatMessageType type)
	{
		if (this.githubDownloader.isDownloading.get())
		{
			return false;
		}

		boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;

		switch (type)
		{
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				return !splitChatEnabled;  // Split chat + Private messages unsupported
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

			if (!trigger.isEmpty())
			{
				wordCount++;
				Emoji emoji = this.emojis.get(trigger);
				boolean isDisabled = emoji != null && !this.isEmojiEnabled(emoji.getText());

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
		}

		return requireAll && disabledCount > 0 && disabledCount == wordCount;
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

	public void openConfiguration()
	{
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
	}
}
