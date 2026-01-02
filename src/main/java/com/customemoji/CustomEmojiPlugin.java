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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.OverheadTextChanged;
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

	private WatchService watchService;
	private ExecutorService watcherExecutor;
	private ScheduledExecutorService debounceExecutor;
	private ScheduledFuture<?> pendingReload;
	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	private void setup()
	{
		if (EMOJIS_FOLDER.mkdir())
		{
			// copy example emoji
			File exampleEmoji = new File(EMOJIS_FOLDER, "com/customemoji/checkmark.png");
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
			File exampleSoundoji = new File(SOUNDOJIS_FOLDER, "com/customemoji/customemoji.wav");
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

		this.githubDownloader = new GitHubEmojiDownloader(this.okHttpClient, this.gson, this.executor);

		loadEmojis();
		loadSoundojis();

		if (this.isGitHubDownloadConfigured())
		{
			this.triggerGitHubDownload();
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

		// Apply initial chat spacing
		clientThread.invokeLater(chatSpacingManager::applyChatSpacing);

		try
		{
			setupFileWatcher();
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
		this.githubDownloader.shutdown();
		shutdownFileWatcher();
		emojis.clear();
		errors.clear();
		chatSpacingManager.clearStoredPositions();

		overlay.shutDown();
		overlayManager.remove(overlay);

		tooltip.shutDown();
		overlayManager.remove(tooltip);

		// Clean up animation overlays
		this.teardownAnimationOverlays();

		if (panel != null)
		{
			hideButton();
		}

		// Clear soundojis - AudioPlayer handles clip management automatically
		soundojis.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
	}

	public void triggerGitHubDownload()
	{
		if (!this.isGitHubDownloadConfigured())
		{
			this.clientThread.invokeLater(() ->
				this.client.addChatMessage(ChatMessageType.CONSOLE, "",
					"<col=FF6600>GitHub download disabled - configure repository in settings", null));
			return;
		}

		this.githubDownloader.downloadEmojis(this.config.githubRepoUrl(), result ->
		{
			this.clientThread.invokeLater(() ->
				this.client.addChatMessage(ChatMessageType.CONSOLE, "", result.formatMessage(), null));

			if (result.hasChanges())
			{
				this.scheduleReload(false);
			}
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

		String message = parts.isEmpty() ? "Already up to date" : String.join(", ", parts);
		return "<col=00FF00>Custom Emoji: " + message;
	}

	private void showButton()
	{
		// Create panel lazily after emojis are loaded
		panel = panelProvider.get();

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
		panel = null;
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

	private void shutdownFileWatcher()
	{
		log.debug("Starting file watcher shutdown");

		// Cancel any pending reload debounce task first to prevent new reloads
		if (pendingReload != null)
		{
			boolean cancelled = pendingReload.cancel(true); // Use true to interrupt if running
			log.debug("Pending reload task cancelled: {}", cancelled);
			pendingReload = null; // Clear reference
		}

		shutdownExecutor(debounceExecutor, "debounce executor");
		shutdownExecutor(watcherExecutor, "watcher executor");

		// Close watch service first to interrupt the blocking take() call
		if (watchService != null)
		{
			try
			{
				watchService.close();
				log.debug("Watch service closed");
			}
			catch (IOException e)
			{
				log.error("Failed to close watch service", e);
			}
			watchService = null; // Clear reference
		}

		// Clear executor references
		debounceExecutor = null;
		watcherExecutor = null;

		log.debug("File watcher shutdown complete");
	}

	private void shutdownExecutor(ExecutorService executor, String executorName)
	{
		if (executor == null)
		{
			return;
		}

		log.debug("Shutting down {}", executorName);
		executor.shutdownNow();
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
			case CustomEmojiConfig.KEY_CHAT_MESSAGE_SPACING:
				clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				scheduleReload(true);
				break;
			case CustomEmojiConfig.KEY_RESIZING_DISABLED_EMOJIS:
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
				this.triggerGitHubDownload();
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
		switch (event.getIndex())
		{
			case VarClientID.MESLAYERINPUT:
			case VarClientID.CHATINPUT:
				String chatInput = this.client.getVarcStrValue(event.getIndex());
				this.overlay.updateChatInput(chatInput);
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

			if (emoji != null && this.isEmojiEnabled(emoji.getText()))
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

	boolean isEmojiEnabled(String emojiName)
	{
		return !PluginUtils.parseDisabledEmojis(this.config.disabledEmojis()).contains(emojiName);
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
					this.clientThread.invokeLater(onComplete);
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
			BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);
			boolean isAnimated = CustomEmojiImageUtilities.isAnimatedGif(file);
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

		if (loaded.isAnimated())
		{
			return new AnimatedEmoji(iconId, name, file, lastModified, dim, staticImage);
		}
		return new StaticEmoji(iconId, name, file, lastModified, dim, staticImage);
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

	public void reloadSingleEmoji(String emojiName)
	{
		Emoji emoji = this.emojis.get(emojiName);
		if (emoji == null)
		{
			log.warn("Cannot reload emoji '{}' - not found", emojiName);
			return;
		}

		File file = emoji.getFile();

		this.executor.submit(() ->
		{
			Result<BufferedImage, Throwable> imageResult = loadImage(file);

			if (!imageResult.isOk())
			{
				log.error("Failed to load image for emoji '{}'", emojiName, imageResult.unwrapError());
				return;
			}

			try
			{
				boolean shouldResize = this.shouldResizeEmoji(emojiName);
				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);
				long fileModified = file.lastModified();
				boolean isAnimated = CustomEmojiImageUtilities.isAnimatedGif(file);
				LoadedEmoji loaded = new LoadedEmoji(emojiName, file, fileModified, normalizedImage, emoji.getId(), isAnimated);

				this.clientThread.invokeLater(() ->
				{
					Emoji updatedEmoji = this.registerLoadedEmoji(loaded);
					this.emojis.put(emojiName, updatedEmoji);
					log.info("Reloaded emoji '{}' with resizing={}", emojiName, shouldResize);
				});
			}
			catch (RuntimeException e)
			{
				log.error("Failed to reload emoji '{}'", emojiName, e);
			}
		});
	}

	/**
	 * Determines if a specific emoji should be resized based on per-emoji settings.
	 * Returns true if the emoji is NOT in the resizing disabled list.
	 */
	private boolean shouldResizeEmoji(String emojiName)
	{
		Set<String> resizingDisabledEmojis = PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
		return !resizingDisabledEmojis.contains(emojiName);
	}

	public static Result<BufferedImage, Throwable> loadImage(final File file)
	{
		try (InputStream in = new FileInputStream(file))
		{
			synchronized (ImageIO.class)
			{
				BufferedImage read = ImageIO.read(in);
				if (read == null)
				{
					return Error(new IOException("image format not supported. (PNG,JPG,GIF only)"));
				}

				return Ok(read);
			}
		}
		catch (IllegalArgumentException | IOException e)
		{
			return Error(e);
		}
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

	private void setupFileWatcher() throws IOException
	{
		watchService = FileSystems.getDefault().newWatchService();

		// Register emoji and soundoji folders for watching
		Path emojiPath = EMOJIS_FOLDER.toPath();
		Path soundojiPath = SOUNDOJIS_FOLDER.toPath();

		if (Files.exists(emojiPath))
		{
			registerRecursively(emojiPath);
		}

		if (Files.exists(soundojiPath))
		{
			registerRecursively(soundojiPath);
		}

		watcherExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "CustomEmoji-FileWatcher");
			t.setDaemon(true);
			return t;
		});

		// Create executor for debouncing reloads (many files changed at once, potentially from a git pull)
		debounceExecutor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "CustomEmoji-Debouncer");
			t.setDaemon(true);
			return t;
		});

		watcherExecutor.submit(this::watchForChanges);

		log.info("File watcher setup complete for emoji folders");
	}

	private void registerRecursively(Path path) throws IOException
	{
		path.register(watchService,
			StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_DELETE,
			StandardWatchEventKinds.ENTRY_MODIFY);

		try (Stream<Path> walkStream = Files.walk(path))
		{
			walkStream
				.filter(Files::isDirectory)
				.filter(p -> !p.equals(path))
				.filter(p -> !p.getFileName().toString().equals(".git")) // Ignore .git folders
				.forEach(subPath ->
				{
					try
					{
						subPath.register(watchService,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					}
					catch (IOException e)
					{
						log.error("Failed to register subdirectory for watching: " + subPath, e);
					}
				});
		}
	}

	private void watchForChanges()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				// Check if watch service is still open before attempting to use it
				if (watchService == null)
				{
					log.debug("Watch service is null, stopping file watcher");
					break;
				}

				WatchKey key = watchService.take();

				// Check again after take() in case watch service was closed
				if (watchService == null)
				{
					log.debug("Watch service closed during take(), stopping file watcher");
					break;
				}

				boolean shouldReload = false;
				for (WatchEvent<?> event : key.pollEvents())
				{
					if (event == null || event.kind() == StandardWatchEventKinds.OVERFLOW)
					{
						continue;
					}

					@SuppressWarnings("unchecked")
					WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;

					Path changed = pathEvent.context();

					// Skip if context is null (can happen during shutdown or filesystem issues)
					if (changed == null)
					{
						log.debug("Skipping file event with null context");
						continue;
					}

					// Only reload if it's an image or audio file
					if (isEmojiFile(changed) || isSoundojiFile(changed))
					{
						shouldReload = true;
						log.debug("Detected change in emoji/soundoji file: " + changed);
					}

					// If new directory created, register it for watching
					if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE)
					{
						if (key.watchable() == null)
						{
							log.debug("Skipping directory registration - key watchable is null");
							continue;
						}

						Path fullPath = ((Path) key.watchable()).resolve(changed);
						if (Files.isDirectory(fullPath))
						{
							try
							{
								// Check if watch service is still valid before registering
								if (watchService != null)
								{
									registerRecursively(fullPath);
									log.debug("Registered new directory for watching: " + fullPath);
								}
							}
							catch (IOException e)
							{
								log.error("Failed to register new directory: " + fullPath, e);
							}
						}
					}
				}

				if (shouldReload)
				{
					scheduleReload(false);
				}

				if (!key.reset())
				{
					// Key is no longer valid (directory was deleted/replaced, e.g., during git branch switch)
					// Don't break the loop - re-register all directories to pick up new structure
					log.debug("Watch key reset failed, re-registering directories");
					try
					{
						Path emojiPath = EMOJIS_FOLDER.toPath();
						Path soundojiPath = SOUNDOJIS_FOLDER.toPath();

						if (Files.exists(emojiPath))
						{
							registerRecursively(emojiPath);
						}
						if (Files.exists(soundojiPath))
						{
							registerRecursively(soundojiPath);
						}
					}
					catch (IOException e)
					{
						log.error("Failed to re-register directories after key reset failure", e);
					}
				}
			}
			catch (InterruptedException e)
			{
				log.debug("File watcher interrupted, stopping");
				Thread.currentThread().interrupt();
				break;
			}
			catch (Exception e)
			{
				// Check if this is due to closed watch service
				if (watchService == null)
				{
					log.debug("File watcher error due to closed watch service, stopping");
					break;
				}
				log.error("Error in file watcher", e);
				// Break on repeated errors to prevent spam
				break;
			}
		}
		log.debug("File watcher thread exiting");
	}

	private boolean isEmojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
	}

	private boolean isSoundojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.endsWith(".wav");
	}

	private void reloadEmojis(boolean force)
	{
		log.info("Reloading emojis and soundojis due to file changes");

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
				String chatMessage = this.formatReloadMessage(addedCount[0], deletedCount, this.soundojis.size());
				this.client.addChatMessage(ChatMessageType.CONSOLE, "", chatMessage, null);

				// Refresh the panel to show updated emoji tree
				if (this.panel != null)
				{
					SwingUtilities.invokeLater(() -> this.panel.refreshEmojiTree());
				}
			});
		});
	}

	private void scheduleReload(boolean force)
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
			pendingReload = debounceExecutor.schedule(() -> reloadEmojis(force), 500, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with 500ms debounce");
		}
	}

	private boolean shouldUpdateChatMessage(ChatMessageType type)
	{
		boolean splitChatEnabled = this.client.getVarpValue(VarPlayerID.OPTION_PM) == 1;

		switch (type)
		{
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				return !splitChatEnabled;
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
