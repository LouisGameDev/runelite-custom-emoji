package com.customemoji;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;
import com.google.common.io.Resources;
import com.google.inject.Provides;
import java.awt.Desktop;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

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

	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	public static final URL EXAMPLE_EMOJI = Resources.getResource(CustomEmojiPlugin.class, "checkmark.png");
	public static final URL EXAMPLE_SOUNDOJI = Resources.getResource(CustomEmojiPlugin.class, "customemoji.wav");

	public static final float NOISE_FLOOR = -60f;

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

	@Value
	private static class Emoji
	{
		int id;
		String text;
		File file;
		long lastModified;
		boolean isAnimated;
	}

	private static class AnimatedEmoji
	{
		final String text;
		final File file;
		final long lastModified;
		final List<Integer> frameIds;
		final List<Integer> frameDelays;
		int currentFrame;
		ScheduledFuture<?> animationTask;
		boolean isVisible;
		long lastVisibilityCheck;

		public AnimatedEmoji(String text, File file, long lastModified, List<Integer> frameIds, List<Integer> frameDelays)
		{
			this.text = text;
			this.file = file;
			this.lastModified = lastModified;
			this.frameIds = frameIds;
			this.frameDelays = frameDelays;
			this.currentFrame = 0;
			this.animationTask = null;
			this.isVisible = false;
			this.lastVisibilityCheck = System.currentTimeMillis();
		}
	}
	@Value
	private static class Soundoji
	{
		String text;
		File file;
	}

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AudioPlayer audioPlayer;

	private final Map<String, Emoji> emojis = new HashMap<>();
	private final Map<String, AnimatedEmoji> animatedEmojis = new HashMap<>();
	private final Map<String, Soundoji> soundojis = new HashMap<>();

	private final List<String> errors = new ArrayList<>();

	private WatchService watchService;
	private ExecutorService watcherExecutor;
	private ScheduledExecutorService debounceExecutor;
	private ScheduledExecutorService animationExecutor;
	private ScheduledFuture<?> pendingReload;
	private ScheduledFuture<?> cleanupTask;


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
				try {
					if (Desktop.isDesktopSupported()) {
						Desktop.getDesktop().open(EMOJIS_FOLDER);
					}
				} catch (IOException ignored) {}
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				try {
					if (Desktop.isDesktopSupported()) {
						Desktop.getDesktop().open(SOUNDOJIS_FOLDER);
					}
				} catch (IOException ignored) {}
				break;
			case EMOJI_ERROR_COMMAND:

				for (String error : errors) {
					client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		setup();

		loadEmojis();
		loadSoundojis();

		try
		{
			setupFileWatcher();
		} catch (IOException e)
		{
			log.error("Failed to setup file watcher", e);
		}

		startAnimationSystem();

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
		else if (config.showLoadedMessage())
		{
			clientThread.invoke(() ->
			{
				int totalEmojis = emojis.size();
				int animatedCount = animatedEmojis.size();
				String message = String.format("<col=00FF00>Custom Emoji: Loaded %d emojis (%d animated) and %d soundojis.",
					totalEmojis, animatedCount, soundojis.size());
				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
			});
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		shutdownFileWatcher();
		stopAnimationSystem();

		frameImageCache.clear();
		animatedEmojis.clear();
		emojis.clear();
		errors.clear();

		soundojis.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
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

		executor.shutdown();

		try
		{
			// Wait for existing tasks to terminate with 2 sec timeout
			if (!executor.awaitTermination(2, TimeUnit.SECONDS))
			{
				log.debug("{} did not terminate gracefully, forcing shutdown", executorName);

				// Force if graceful shutdown fails
				executor.shutdownNow();

				// Wait for tasks to respond to being cancelled
				if (!executor.awaitTermination(1, TimeUnit.SECONDS))
				{
					log.warn("{} did not terminate even after forced shutdown", executorName);
				}
				else
				{
					log.debug("{} terminated after forced shutdown", executorName);
				}
			}
			else
			{
				log.debug("{} terminated gracefully", executorName);
			}
		} catch (InterruptedException e)
		{
			log.debug("Interrupted while waiting for {} termination, forcing immediate shutdown", executorName);
			executor.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	private void startAnimationSystem()
	{
		if (!config.enableAnimatedGifs())
		{
			log.debug("Animated GIFs disabled in config, skipping animation system");
			return;
		}

		animationExecutor = Executors.newScheduledThreadPool(4, r -> {
			Thread t = new Thread(r, "CustomEmoji-Animation");
			t.setDaemon(true);
			return t;
		});

		// Start cleanup task for invisible animations and memory monitor
		cleanupTask = animationExecutor.scheduleAtFixedRate(() -> {
			cleanupInvisibleAnimations();
			logMemoryUsage();
		}, 30, 30, TimeUnit.SECONDS);
		log.debug("Animation system started with memory monitor");
	}

	private void stopAnimationSystem()
	{
		// Stop all individual animation tasks
		animatedEmojis.values().forEach(this::stopAnimation);

		if (cleanupTask != null)
		{
			cleanupTask.cancel(true);
			log.debug("Animation cleanup task stopped");
		}
		shutdownExecutor(animationExecutor, "animation executor");
	}

	private void startAnimation(AnimatedEmoji animatedEmoji)
	{
		if (!config.enableAnimatedGifs() || animatedEmoji.frameIds.size() <= 1)
		{
			return;
		}

		stopAnimation(animatedEmoji);

		// Mark as visible and start animation
		animatedEmoji.isVisible = true;
		animatedEmoji.lastVisibilityCheck = System.currentTimeMillis();

		scheduleNextFrame(animatedEmoji);
		log.debug("Started animation for {}", animatedEmoji.text);
	}

	private void stopAnimation(AnimatedEmoji animatedEmoji)
	{
		if (animatedEmoji.animationTask != null)
		{
			animatedEmoji.animationTask.cancel(false);
			animatedEmoji.animationTask = null;
		}
		animatedEmoji.isVisible = false;
	}

	private void scheduleNextFrame(AnimatedEmoji animatedEmoji)
	{
		if (!config.enableAnimatedGifs() || !animatedEmoji.isVisible)
		{
			return;
		}

		int currentDelay = animatedEmoji.frameDelays.get(animatedEmoji.currentFrame);

		animatedEmoji.animationTask = animationExecutor.schedule(() -> {
			try
			{
				// Move to next frame
				animatedEmoji.currentFrame = (animatedEmoji.currentFrame + 1) % animatedEmoji.frameIds.size();
				int frameId = animatedEmoji.frameIds.get(animatedEmoji.currentFrame);

				// Update the chat icon with the new frame
				Emoji staticEmoji = emojis.get(animatedEmoji.text);
				if (staticEmoji != null)
				{
					BufferedImage frameImage = getCachedFrameImage(frameId);
					if (frameImage != null)
					{
						clientThread.invokeLater(() -> {
							try
							{
								chatIconManager.updateChatIcon(staticEmoji.id, frameImage);
							}
							catch (Exception e)
							{
								log.error("Failed to update animated emoji frame for {}", animatedEmoji.text, e);
							}
						});
					}
				}

				// Schedule next frame if still visible
				if (animatedEmoji.isVisible)
				{
					scheduleNextFrame(animatedEmoji);
				}
			}
			catch (Exception e)
			{
				log.error("Error in animation frame update for {}", animatedEmoji.text, e);
			}
		}, currentDelay, TimeUnit.MILLISECONDS);
	}

	private void cleanupInvisibleAnimations()
	{
		long currentTime = System.currentTimeMillis();
		List<String> toRemove = new ArrayList<>();

		animatedEmojis.forEach((name, animatedEmoji) -> {
			// If animation has been invisible for more than 30 seconds, stop it
			if (!animatedEmoji.isVisible && (currentTime - animatedEmoji.lastVisibilityCheck) > 30000)
			{
				stopAnimation(animatedEmoji);

				// Remove cached frames for this emoji to free memory
				animatedEmoji.frameIds.forEach(frameImageCache::remove);
				toRemove.add(name);

				log.debug("Cleaned up invisible animation and cached frames for {}", animatedEmoji.text);
			}
		});

		// Remove cleaned up animations from the map
		toRemove.forEach(animatedEmojis::remove);
	}

	// Cache for frame images to avoid repeated icon manager calls
	private final Map<Integer, BufferedImage> frameImageCache = new HashMap<>();

	private BufferedImage getCachedFrameImage(int frameId)
	{
		return frameImageCache.get(frameId);
	}

	/**
	 * Get memory usage statistics for debugging
	 */
	private void logMemoryUsage()
	{
		int activeAnimations = (int) animatedEmojis.values().stream()
			.mapToLong(ae -> ae.animationTask != null && !ae.animationTask.isCancelled() ? 1 : 0)
			.sum();
		int cachedFrames = frameImageCache.size();
		int totalEmojis = emojis.size();
		int animatedCount = animatedEmojis.size();

		log.debug("Memory Usage - Total Emojis: {}, Animated: {}, Active Animations: {}, Cached Frames: {}",
			totalEmojis, animatedCount, activeAnimations, cachedFrames);
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

			if (emoji != null)
			{
				messageWords[i] = messageWords[i].replace(trigger,
						"<img=" + chatIconManager.chatIconIndex(emoji.id) + ">");
				editedMessage = true;
				log.debug("Replacing {} with emoji {}", trigger, emoji.text);

				// Start animation if this is an animated emoji
				AnimatedEmoji animatedEmoji = animatedEmojis.get(trigger.toLowerCase());
				if (animatedEmoji != null)
				{
					startAnimation(animatedEmoji);
				}
			}

			if (soundoji != null)
			{
				if (sound)
				{
					try {
						audioPlayer.play(soundoji.file, volumeToGain(config.volume()));
					} catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
						log.error("Failed to play soundoji: " + soundoji.text, e);
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

	private void loadEmojis()
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
				errors.add(String.format("Skipped non-emoji file: %s", fileName));
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

		var result = loadSoundojisFolder(soundojiFolder);
		result.ifOk(list ->
		{
			list.forEach(e -> soundojis.put(e.text, e));
			log.info("Loaded {} soundojis", result.unwrap().size());
		});
		result.ifError(e ->
		{
			e.forEach(t ->
			{
				String fileName = extractFileName(t.getMessage());
				log.debug("Skipped non-audio file: {}", fileName);
				errors.add(String.format("Skipped non-audio file: %s", fileName));
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
		Emoji existingEmoji = emojis.get(text);

		// If emoji exists and file hasn't been modified, return existing emoji unchanged
		if (existingEmoji != null && existingEmoji.lastModified == fileModified)
		{
			log.debug("Emoji file unchanged, skipping: {} (last modified: {})", text, fileModified);
			return Ok(existingEmoji);
		}

		// Check if this is a GIF file and try animated loading first
		String fileName = file.getName().toLowerCase();
		if (fileName.endsWith(".gif"))
		{
			Result<Emoji, String> animatedResult = loadAnimatedGif(file, text, fileModified);
			if (animatedResult.isOk())
			{
				return Ok(animatedResult.unwrap());
			}
			else
			{
				log.debug("Animated GIF loading failed for {}, falling back to static: {}", text, animatedResult.unwrapError());
				// Fall through to static loading
			}
		}

		// File has been modified or is new, need to load image
		Result<BufferedImage, Throwable> image = loadImage(file);

		if (image.isOk())
		{
			try
			{
				int id;

				if (existingEmoji != null)
				{
					// Update existing emoji in place
					chatIconManager.updateChatIcon(existingEmoji.id, image.unwrap());
					id = existingEmoji.id;
					log.info("Updated existing chat icon for emoji: {} (id: {})", text, id);
				}
				else
				{
					// Register new emoji
					id = chatIconManager.registerChatIcon(image.unwrap());
					log.info("Registered new chat icon for emoji: {} (id: {})", text, id);
				}

				return Ok(new Emoji(id, text, file, fileModified, false));
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

	private static Result<BufferedImage, Throwable> loadImage(final File file)
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

	private Result<List<BufferedImage>, Throwable> loadGifFrames(final File file)
	{
		try (InputStream in = new FileInputStream(file))
		{
			ImageInputStream imageStream = ImageIO.createImageInputStream(in);
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(imageStream);

			int numFrames = reader.getNumImages(true);
			List<BufferedImage> frames = new ArrayList<>();

			for (int i = 0; i < numFrames; i++)
			{
				BufferedImage frame = reader.read(i);
				frames.add(frame);
			}

			reader.dispose();
			imageStream.close();

			return Ok(frames);
		} catch (IOException e)
		{
			return Error(e);
		}
	}

	private Result<List<Integer>, Throwable> loadGifFrameDelays(final File file)
	{
		try (InputStream in = new FileInputStream(file))
		{
			ImageInputStream imageStream = ImageIO.createImageInputStream(in);
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(imageStream);

			int numFrames = reader.getNumImages(true);
			List<Integer> delays = new ArrayList<>();

			for (int i = 0; i < numFrames; i++)
			{
				IIOMetadata metadata = reader.getImageMetadata(i);
				String formatName = metadata.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

				IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
				int delay = 100; // Default delay in milliseconds

				if (graphicsControlExtensionNode != null)
				{
					String delayTimeStr = graphicsControlExtensionNode.getAttribute("delayTime");
					if (delayTimeStr != null && !delayTimeStr.isEmpty())
					{
						// GIF delay is in centiseconds, convert to milliseconds
						delay = Math.max(Integer.parseInt(delayTimeStr) * 10, 50); // Minimum 50ms delay
					}
				}

				delays.add(delay);
			}

			reader.dispose();
			imageStream.close();

			return Ok(delays);
		} catch (IOException | NumberFormatException e)
		{
			return Error(e);
		}
	}

	private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName)
	{
		int nodeCount = rootNode.getLength();
		for (int i = 0; i < nodeCount; i++)
		{
			IIOMetadataNode node = (IIOMetadataNode) rootNode.item(i);
			if (node.getNodeName().equalsIgnoreCase(nodeName))
			{
				return node;
			}
		}
		return null;
	}

	private Result<Emoji, String> loadAnimatedGif(File file, String emojiName, long lastModified)
	{
		if (!config.enableAnimatedGifs())
		{
			log.debug("Animated GIFs disabled, falling back to static loading for {}", emojiName);
			return loadStaticEmoji(file, emojiName, lastModified);
		}

		try
		{
			// Load all frames
			Result<List<BufferedImage>, Throwable> framesResult = loadGifFrames(file);
			if (framesResult.isError())
			{
				log.debug("Failed to load GIF frames for {}, falling back to static: {}", emojiName, framesResult.unwrapError().getMessage());
				return loadStaticEmoji(file, emojiName, lastModified);
			}

			List<BufferedImage> frames = framesResult.unwrap();
			if (frames.size() <= 1)
			{
				log.debug("GIF {} has only {} frame(s), treating as static", emojiName, frames.size());
				return loadStaticEmoji(file, emojiName, lastModified);
			}

			// Load frame delays
			Result<List<Integer>, Throwable> delaysResult = loadGifFrameDelays(file);
			if (delaysResult.isError())
			{
				log.debug("Failed to load GIF frame delays for {}, falling back to static: {}", emojiName, delaysResult.unwrapError().getMessage());
				return loadStaticEmoji(file, emojiName, lastModified);
			}

			List<Integer> frameDelays = delaysResult.unwrap();

			// Register each frame as a chat icon and collect their IDs
			List<Integer> frameIds = new ArrayList<>();
			for (int i = 0; i < frames.size(); i++)
			{
				BufferedImage frame = frames.get(i);
				int frameId = chatIconManager.registerChatIcon(frame);
				frameIds.add(frameId);
				frameImageCache.put(frameId, frame); // Cache for later updates
			}

			// Create the primary emoji (uses first frame initially)
			int primaryId = frameIds.get(0);
			Emoji emoji = new Emoji(primaryId, emojiName, file, lastModified, true);

			// Create animated emoji tracking entry
			AnimatedEmoji animatedEmoji = new AnimatedEmoji(
				emojiName,
				file,
				lastModified,
				frameIds,
				frameDelays
			);

			animatedEmojis.put(emojiName, animatedEmoji);
			log.debug("Loaded animated GIF {} with {} frames", emojiName, frames.size());

			return Ok(emoji);
		}
		catch (Exception e)
		{
			log.error("Error loading animated GIF {}, falling back to static", emojiName, e);
			return loadStaticEmoji(file, emojiName, lastModified);
		}
	}

	private Result<Emoji, String> loadStaticEmoji(File file, String emojiName, long lastModified)
	{
		Result<BufferedImage, Throwable> imageResult = loadImage(file);
		if (imageResult.isError())
		{
			return Error("Error loading " + file.getName() + ": " + imageResult.unwrapError().getMessage());
		}

		BufferedImage image = imageResult.unwrap();
		int id = chatIconManager.registerChatIcon(image);
		Emoji emoji = new Emoji(id, emojiName, file, lastModified, false);
		return Ok(emoji);
	}

	private boolean wasMessageSentByOtherPlayer(ChatMessage message)
	{
		return !Objects.equals(Text.sanitize(message.getName()), client.getLocalPlayer().getName());
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

					// Multiple null checks to prevent NPE
					if (pathEvent == null)
					{
						log.debug("Skipping null path event");
						continue;
					}

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
					scheduleReload();
				}

				if (!key.reset())
				{
					log.debug("Watch key reset failed, stopping file watcher");
					break;
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

	private void reloadEmojis()
	{
		log.info("Reloading emojis and soundojis due to file changes");

		Set<String> currentEmojiNames = new HashSet<>(emojis.keySet());

		// Stop all existing animations before clearing
		animatedEmojis.values().forEach(this::stopAnimation);
		animatedEmojis.clear();

		frameImageCache.clear();
		log.debug("Cleared frame image cache during reload");

		// Note: Soundoji clips are handled by the AudioPlayer, no direct cleanup needed
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
					errors.add(String.format("Skipped non-emoji file: %s", fileName));
				});
			});

			// Remove deleted emojis from our map and clean up their cached frames
			currentEmojiNames.removeAll(newEmojiNames);
			currentEmojiNames.forEach(deletedEmoji -> {
				log.debug("Removing deleted emoji: {}", deletedEmoji);
				emojis.remove(deletedEmoji);

				// Clean up animated emoji and its cached frames
				AnimatedEmoji animatedEmoji = animatedEmojis.remove(deletedEmoji);
				if (animatedEmoji != null)
				{
					stopAnimation(animatedEmoji);
					animatedEmoji.frameIds.forEach(frameImageCache::remove);
					log.debug("Cleaned up cached frames for deleted animated emoji: {}", deletedEmoji);
				}
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
	}

	private void scheduleReload()
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
				clientThread.invokeLater(this::reloadEmojis);
			}, 500, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with 500ms debounce");
		}
	}

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}
}
