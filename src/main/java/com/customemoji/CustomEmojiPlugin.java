package com.customemoji;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.google.common.io.Resources;
import com.google.inject.Provides;
import com.google.inject.Provider;

import java.awt.*;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
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
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarClientID;
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
import com.customemoji.Panel.CustomEmojiPanel;
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
		name="Custom Emoji",
		description="Allows you to use custom emojis in chat messages",
		tags={"emoji", "chat", "message", "custom", "icon", "emote", "text", "clan", "notification"}
)
public class CustomEmojiPlugin extends Plugin
{
	public static final String EMOJI_ERROR_COMMAND = "emojierror";
	public static final String EMOJI_FOLDER_COMMAND = "emojifolder";
	public static final String SOUNDOJI_FOLDER_COMMAND = "soundojifolder";
	public static final String PRINT_ALL_EMOJI_COMMAND = "emojiprint";

	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	public static final URL EXAMPLE_EMOJI = Resources.getResource(CustomEmojiPlugin.class, "checkmark.png");
	public static final URL EXAMPLE_SOUNDOJI = Resources.getResource(CustomEmojiPlugin.class, "customemoji.wav");

	public static final float NOISE_FLOOR = -60f;

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

	private static CustomEmojiPlugin instance;

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

	@Getter
    protected final Map<String, Emoji> emojis = new HashMap<>();
	private final Map<String, Soundoji> soundojis = new HashMap<>();
	private final List<String> errors = new ArrayList<>();
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
			} catch (IOException e)
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
			} catch (IOException e)
			{
				log.error("Failed to copy example soundoji", e);
			}
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e) {
		switch (e.getCommand()) {
			case EMOJI_FOLDER_COMMAND:
				LinkBrowser.open(EMOJIS_FOLDER.toString());
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				LinkBrowser.open(SOUNDOJIS_FOLDER.toString());
				break;
			case EMOJI_ERROR_COMMAND:

				for (String error : errors) {
					client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			case PRINT_ALL_EMOJI_COMMAND:
				StringBuilder sb = new StringBuilder();

				sb.append("Currently loaded emoji: ");
				
				for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
				{
					sb.append(entry.getKey() + " ");
				}

				String message = updateMessage(sb.toString(), false);
				client.addChatMessage(ChatMessageType.CONSOLE, "Currently loaded emoji", message, null);
				
				break;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		instance = this;
		setup();

		loadEmojis();
		loadSoundojis();

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
			setupFileWatcher();
		} catch (IOException e)
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
		instance = null;
		shutdownFileWatcher();
		emojis.clear();
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
			} catch (IOException e)
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
		// Apply chat spacing when chat-related widgets are loaded
		if (event.getGroupId() == InterfaceID.Chatbox.SCROLLAREA)
		{
			clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("custom-emote") == false)
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
			case CustomEmojiConfig.KEY_RESIZE_EMOJI:
				scheduleReload(true);
				break;
			case CustomEmojiConfig.KEY_SHOW_SIDE_PANEL:
				if (this.config.showPanel()) this.showButton(); else this.hideButton();
				break;
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
				// Panel already updated itself, skip redundant refresh
				shouldRefreshPanel = false;
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
		switch (event.getIndex()) {
			case VarClientID.CHAT_LASTREBUILD:
				this.chatSpacingManager.clearStoredPositions();
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::applyChatSpacing);
				break;
			case VarClientID.CHAT_LASTSCROLLPOS:
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::captureScrollPosition);
				break;
		}
	}

	public static BufferedImage scaleDown(BufferedImage originalImage, int targetHeight)
	{
		int originalWidth = originalImage.getWidth();
		int originalHeight = originalImage.getHeight();

		// Do not scale if already short enough
		if (originalHeight <= targetHeight) {
			return originalImage;
		}

		// Compute new width while preserving aspect ratio
		double scaleFactor = (double) targetHeight / originalHeight;
		int newWidth = (int) Math.round(originalWidth * scaleFactor);

		// Create scaled image
		return ImageUtil.resizeImage(originalImage, newWidth, targetHeight);
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
			//			final net.runelite.client.plugins.emojis.Emoji emoji = net.runelite.client.plugins.emojis.Emoji.getEmoji(trigger);
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
					try {
						this.audioPlayer.play(soundoji.getFile(), volumeToGain(this.config.volume()));
					} catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
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
		return !parseDisabledEmojis(this.config.disabledEmojis()).contains(emojiName);
	}

	/**
	 * Parses the comma-separated disabled emojis string into a Set.
	 * This is a utility method that can be used anywhere in the plugin.
	 *
	 * @param disabledEmojisString The comma-separated string of disabled emoji names
	 * @return Set of disabled emoji names (never null)
	 */
	public static Set<String> parseDisabledEmojis(String disabledEmojisString)
	{
		Set<String> result = new HashSet<>();

		if (disabledEmojisString != null && !disabledEmojisString.trim().isEmpty())
		{
			String[] parts = disabledEmojisString.split(",");
			for (String part : parts)
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					result.add(trimmed);
				}
			}
		}

		return result;
	}

	public void loadEmojis()
	{
		File emojiFolder = EMOJIS_FOLDER;
		if (emojiFolder.mkdir())
		{
			log.error("Created emoji folder");
		}

		var result = loadEmojisFolder(emojiFolder);
		result.ifOk(list ->
		{
			list.forEach(e -> emojis.put(e.getText(), e));
			log.info("Loaded {} emojis", result.unwrap().size());
		});
		result.ifError(e ->
		{
			e.forEach(t ->
			{
				String fileName = extractFileName(t.getMessage());
				log.debug("Skipped non-emoji file: {}", fileName);
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
		{
			e.forEach(t ->
			{
				String fileName = extractFileName(t.getMessage());
				log.debug("Skipped non-audio file: {}", fileName);
			});
		});
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
		List<Throwable> errors = new ArrayList<>();

		for (File file : files)
		{
			Result<Soundoji, Throwable> result = loadSoundoji(file);
			result.ifOk(loaded::add);
			result.ifError(errors::add);
		}

		if (errors.isEmpty())
		{
			return Ok(loaded);
		}
		else
		{
			return PartialOk(loaded, errors);
		}
	}

	private Result<List<Emoji>, List<Throwable>> loadEmojisFolder(File folder)
	{
		// recursively flattenFolder files in the folder
		List<File> files = flattenFolder(folder);

		if (!folder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder " + folder)));
		}

		List<Emoji> loaded = new ArrayList<>();
		List<Throwable> errors = new ArrayList<>();

		for (File file : files)
		{
			Result<Emoji, Throwable> result = loadEmoji(file);
			result.ifOk(loaded::add);
			result.ifError(errors::add);
		}

		if (errors.isEmpty())
		{
			return Ok(loaded);
		}
		else
		{
			return PartialOk(loaded, errors);
		}

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

	private Result<Emoji, Throwable> loadEmoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			return Error(new IllegalArgumentException("Illegal file name <col=00FFFF>" + file));
		}

		String text = file.getName().substring(0, extension).toLowerCase();
		long fileModified = file.lastModified();

		// Check if we already have an emoji with this name
		Emoji existingEmoji = this.emojis.get(text);

		// If emoji exists and file hasn't been modified, return existing emoji unchanged
		if (existingEmoji != null && existingEmoji.getLastModified() == fileModified)
		{
			log.debug("Emoji file unchanged, skipping: {} (last modified: {})", text, fileModified);
			return Ok(existingEmoji);
		}

		// File has been modified or is new, need to load image
		Result<BufferedImage, Throwable> image = loadImage(file);

		if (image.isOk())
		{
			try
			{
				int id;

				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(image.unwrap(), this.config);

				if (existingEmoji != null)
				{
					// Update existing emoji in place
					this.chatIconManager.updateChatIcon(existingEmoji.getId(), normalizedImage);
					id = existingEmoji.getId();
					log.info("Updated existing chat icon for emoji: {} (id: {})", text, id);
				}
				else
				{
					// Register new emoji
					id = chatIconManager.registerChatIcon(normalizedImage);
					log.info("Registered new chat icon for emoji: {} (id: {})", text, id);
				}

				Dimension dimension = new Dimension(normalizedImage.getWidth(), normalizedImage.getHeight());

				return Ok(new Emoji(id, text, file, fileModified, dimension));
			} catch (RuntimeException e)
			{
				return Error(new RuntimeException(
						"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + e.getMessage(),
						e));
			}
		}
		else
		{
			Throwable throwable = image.unwrapError();
			return Error(new RuntimeException(
					"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + throwable.getMessage(),
					throwable));
		}
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
		} catch (IllegalArgumentException | IOException e)
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

		watcherExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "CustomEmoji-FileWatcher");
			t.setDaemon(true);
			return t;
		});

		// Create executor for debouncing reloads (many files changed at once, potentially from a git pull)
		debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
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

		Files.walk(path)
			.filter(Files::isDirectory)
			.filter(p -> !p.equals(path))
			.filter(p -> !p.getFileName().toString().equals(".git")) // Ignore .git folders
			.forEach(subPath -> {
				try
				{
					subPath.register(watchService,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE,
						StandardWatchEventKinds.ENTRY_MODIFY);
				} catch (IOException e)
				{
					log.error("Failed to register subdirectory for watching: " + subPath, e);
				}
			});
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
							} catch (IOException e)
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
			} catch (InterruptedException e)
			{
				log.debug("File watcher interrupted, stopping");
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e)
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
		Set<String> currentEmojiNames = new HashSet<>(emojis.keySet());

		if (force)
		{
			emojis.clear();
		}

		soundojis.clear();

		errors.clear();

		// Reload emojis (using updateChatIcon for existing, registerChatIcon for new)
		File emojiFolder = EMOJIS_FOLDER;
		if (emojiFolder.exists())
		{
			var result = loadEmojisFolder(emojiFolder);

			// Track which emojis are still present
			Set<String> newEmojiNames = new HashSet<>();
			result.ifOk(list -> {
				list.forEach(e -> {
					emojis.put(e.getText(), e);
					newEmojiNames.add(e.getText());
				});
				log.info("Loaded {} emojis", result.unwrap().size());
			});
			result.ifError(e -> {
				e.forEach(t -> {
					String fileName = extractFileName(t.getMessage());
					log.debug("Skipped non-emoji file: {}", fileName);
				});
			});

			// Remove deleted emojis from our map
			currentEmojiNames.removeAll(newEmojiNames);
			currentEmojiNames.forEach(deletedEmoji -> {
				log.debug("Removing deleted emoji: {}", deletedEmoji);
				emojis.remove(deletedEmoji);
			});
		}
		else
		{
			log.warn("Emoji folder does not exist: {}", emojiFolder);
			emojis.clear();
		}

		loadSoundojis();

		String message = String.format("<col=00FF00>Custom Emoji: Reloaded %d emojis and %d soundojis", emojis.size(), soundojis.size());

		client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
		
		// Refresh the panel to show updated emoji tree
		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refreshEmojiTree());
		}
	}

	private void scheduleReload(boolean force)
	{
		synchronized (this)
		{
			// Don't schedule reload if debounceExecutor is null (during shutdown)
			if (debounceExecutor.isShutdown())
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
			pendingReload = debounceExecutor.schedule(() -> {
				clientThread.invokeLater(() -> reloadEmojis(force));
			}, 500, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with 500ms debounce");
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
	Set<String> provideDisabledEmojis()
	{
		return parseDisabledEmojis(this.config.disabledEmojis());
	}

	public static CustomEmojiPlugin getInstance()
	{
		return instance;
	}

	public void openConfiguration()
	{
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
	}
}
