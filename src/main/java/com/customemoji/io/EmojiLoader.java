package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiImageUtilities;
import com.customemoji.PluginUtils;
import com.customemoji.Result;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.StaticEmoji;
import com.customemoji.model.Emoji;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.w3c.dom.NodeList;

import com.customemoji.animation.GifAnimation;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class EmojiLoader
{
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();
	private static final long DEBOUNCE_DELAY_MS = 500;
	private static final long ANIMATION_UNLOAD_DELAY_MS = 500;
	private static final String LOG_RELOADED_EMOJIS = "Reloaded {} emojis";

	private final ChatIconManager chatIconManager;
	private final CustomEmojiConfig config;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final FileWatcher fileWatcher;
	private final List<String> errors = new ArrayList<>();

	@Getter
	private final Map<String, Emoji> emojis = new HashMap<>();

	private final Map<Integer, GifAnimation> loadedAnimations = new HashMap<>();
	private final Map<Integer, Long> animationLastSeenTime = new HashMap<>();
	private final Set<Integer> pendingAnimationLoads = new HashSet<>();

	private IntConsumer reloadCallback;
	private ScheduledFuture<?> pendingReload;

	@Inject
	public EmojiLoader(ChatIconManager chatIconManager, CustomEmojiConfig config, ClientThread clientThread, ScheduledExecutorService executor, FileWatcher fileWatcher)
	{
		this.chatIconManager = chatIconManager;
		this.config = config;
		this.clientThread = clientThread;
		this.executor = executor;
		this.fileWatcher = fileWatcher;
	}

	public void startWatching(Path[] watchPaths, IntConsumer onReloadComplete) throws IOException
	{
		this.reloadCallback = onReloadComplete;
		this.fileWatcher.start(watchPaths, this::onFileChange);
	}

	public void shutdown()
	{
		this.cancelPendingReload();
		this.fileWatcher.shutdown();
		this.emojis.clear();
		this.errors.clear();
		this.clearAnimationCache();
	}

	public void scheduleReload(boolean force)
	{
		this.cancelPendingReload();

		this.pendingReload = this.executor.schedule(
			() -> this.reloadAllEmojis(force),
			DEBOUNCE_DELAY_MS,
			TimeUnit.MILLISECONDS
		);

		log.debug("Scheduled emoji reload with {}ms debounce (force={})", DEBOUNCE_DELAY_MS, force);
	}

	private void cancelPendingReload()
	{
		if (this.pendingReload != null && !this.pendingReload.isDone())
		{
			this.pendingReload.cancel(false);
			log.debug("Cancelled pending emoji reload");
		}
	}

	public void loadInitialEmojis(Runnable onComplete)
	{
		this.loadEmojisAsync(onComplete);
	}

	private void onFileChange(boolean force)
	{
		this.scheduleReload(force);
	}

	private void reloadAllEmojis(boolean force)
	{
		log.info("Reloading emojis due to file changes");

		Set<String> previousEmojiNames = new HashSet<>(this.emojis.keySet());

		if (force)
		{
			this.emojis.clear();
		}

		Set<String> emojiNamesInFolder = EmojiLoader.getEmojiNamesFromFolder(EmojiLoader.EMOJIS_FOLDER);

		this.loadEmojisAsync(() ->
		{
			previousEmojiNames.removeAll(emojiNamesInFolder);
			previousEmojiNames.forEach(deletedEmoji ->
			{
				log.debug("Removing deleted emoji: {}", deletedEmoji);
				this.emojis.remove(deletedEmoji);
			});

			log.info(LOG_RELOADED_EMOJIS, this.emojis.size());

			if (this.reloadCallback != null)
			{
				this.reloadCallback.accept(this.emojis.size());
			}
		});
	}

	public void reloadSelectedEmojis(List<String> emojiNames, Runnable onComplete)
	{
		if (emojiNames.isEmpty())
		{
			if (onComplete != null)
			{
				onComplete.run();
			}
			return;
		}

		this.executor.submit(() ->
		{
			List<LoadedEmoji> preparedEmojis = this.prepareEmojisForReload(emojiNames);

			if (preparedEmojis.isEmpty())
			{
				if (onComplete != null)
				{
					onComplete.run();
				}
				return;
			}

			this.clientThread.invokeLater(() ->
			{
				List<Emoji> registered = this.registerLoadedEmojis(preparedEmojis);
				registered.forEach(emoji -> this.emojis.put(emoji.getText(), emoji));
				log.info(LOG_RELOADED_EMOJIS, preparedEmojis.size());

				if (onComplete != null)
				{
					// Defer callback by one tick to allow modIcons to be rebuilt
					this.clientThread.invokeLater(onComplete);
				}
			});
		});
	}

	private List<LoadedEmoji> prepareEmojisForReload(List<String> emojiNames)
	{
		List<LoadedEmoji> preparedEmojis = new ArrayList<>();

		for (String emojiName : emojiNames)
		{
			Result<LoadedEmoji, String> result = this.prepareEmojiForReload(emojiName);
			result.ifOk(preparedEmojis::add);
			result.ifError(error -> log.warn("{}", error));
		}

		return preparedEmojis;
	}

	private Result<LoadedEmoji, String> prepareEmojiForReload(String emojiName)
	{
		Emoji emoji = this.emojis.get(emojiName);
		if (emoji == null)
		{
			return Error("Cannot reload emoji '" + emojiName + "' - not found");
		}

		File file = emoji.getFile();
		Result<BufferedImage, Throwable> imageResult = EmojiLoader.loadImage(file);

		if (imageResult.isError())
		{
			return Error("Failed to load image for emoji '" + emojiName + "': " + imageResult.unwrapError().getMessage());
		}

		try
		{
			boolean shouldResize = this.shouldResizeEmoji(emojiName);
			BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);
			boolean isAnimated = CustomEmojiImageUtilities.isAnimatedGif(file);
			LoadedEmoji loaded = new LoadedEmoji(emojiName, file, file.lastModified(), normalizedImage, emoji.getId(), isAnimated);
			return Ok(loaded);
		}
		catch (RuntimeException e)
		{
			return Error("Failed to reload emoji '" + emojiName + "': " + e.getMessage());
		}
	}

	public static Result<BufferedImage, Throwable> loadImage(File file)
	{
		try (InputStream inputStream = new FileInputStream(file))
		{
			synchronized (ImageIO.class)
			{
				BufferedImage image = ImageIO.read(inputStream);
				if (image == null)
				{
					return Error(new IOException("image format not supported. (PNG,JPG,GIF only)"));
				}
				return Ok(image);
			}
		}
		catch (IllegalArgumentException | IOException e)
		{
			return Error(e);
		}
	}

	private void loadEmojisAsync(Runnable onComplete)
	{
		if (EmojiLoader.EMOJIS_FOLDER.mkdir())
		{
			log.debug("Created emoji folder: {}", EmojiLoader.EMOJIS_FOLDER);
			return;
		}

		this.executor.submit(() ->
		{
			Result<List<LoadedEmoji>, List<Throwable>> result = this.prepareEmojisFromFolder();

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
					this.errors.clear();
					for (Throwable throwable : loadErrors)
					{
						String message = throwable.getMessage();
						boolean isSkippedFile = message.contains("image format not supported") || message.contains("Illegal file name");
						if (!isSkippedFile)
						{
							log.warn("Failed to load emoji: {}", message);
							this.errors.add(message);
						}
					}
				});

				if (onComplete != null)
				{
					// Defer callback by one tick to allow modIcons to be rebuilt
					this.clientThread.invokeLater(onComplete);
				}
			});
		});
	}

	private Result<List<LoadedEmoji>, List<Throwable>> prepareEmojisFromFolder()
	{
		if (!EmojiLoader.EMOJIS_FOLDER.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder: " + EmojiLoader.EMOJIS_FOLDER)));
		}

		List<File> files = FileUtils.flattenFolder(EmojiLoader.EMOJIS_FOLDER);
		List<LoadedEmoji> loadedEmojis = new ArrayList<>();
		List<Throwable> prepareErrors = new ArrayList<>();

		for (File file : files)
		{
			boolean isUnchanged = this.isFileUnchanged(file);
			if (isUnchanged)
			{
				continue;
			}

			Result<LoadedEmoji, Throwable> result = this.prepareEmoji(file);
			result.ifOk(loadedEmojis::add);
			result.ifError(prepareErrors::add);
		}

		if (prepareErrors.isEmpty())
		{
			return Ok(loadedEmojis);
		}
		else
		{
			return PartialOk(loadedEmojis, prepareErrors);
		}
	}

	private boolean isFileUnchanged(File file)
	{
		int extensionIndex = file.getName().lastIndexOf('.');
		if (extensionIndex < 0)
		{
			return false;
		}

		String emojiName = file.getName().substring(0, extensionIndex).toLowerCase();
		Emoji existingEmoji = this.emojis.get(emojiName);

		if (existingEmoji == null)
		{
			return false;
		}

		long fileModified = file.lastModified();
		return existingEmoji.getLastModified() == fileModified;
	}

	private Result<LoadedEmoji, Throwable> prepareEmoji(File file)
	{
		int extensionIndex = file.getName().lastIndexOf('.');
		boolean hasNoExtension = extensionIndex < 0;

		if (hasNoExtension)
		{
			return Error(new IllegalArgumentException("Illegal file name <col=00FFFF>" + file));
		}

		String emojiName = file.getName().substring(0, extensionIndex).toLowerCase();
		long fileModified = file.lastModified();
		Emoji existingEmoji = this.emojis.get(emojiName);
		boolean isAnimated = CustomEmojiImageUtilities.isAnimatedGif(file);

		Result<BufferedImage, Throwable> imageResult = EmojiLoader.loadImage(file);

		if (imageResult.isOk())
		{
			try
			{
				boolean shouldResize = this.shouldResizeEmoji(emojiName);
				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);
				Integer existingId = existingEmoji != null ? existingEmoji.getId() : null;

				return Ok(new LoadedEmoji(emojiName, file, fileModified, normalizedImage, existingId, isAnimated));
			}
			catch (RuntimeException e)
			{
				return Error(new RuntimeException(
					"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + e.getMessage(),
					e));
			}
		}
		else
		{
			Throwable throwable = imageResult.unwrapError();
			return Error(new RuntimeException(
				"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + throwable.getMessage(),
				throwable));
		}
	}

	public List<Emoji> registerLoadedEmojis(List<LoadedEmoji> loadedEmojis)
	{
		List<Emoji> registered = new ArrayList<>();

		for (LoadedEmoji loadedEmoji : loadedEmojis)
		{
			int width = loadedEmoji.getImage().getWidth();
			int height = loadedEmoji.getImage().getHeight();
			Dimension dim = new Dimension(width, height);
			Integer existingId = loadedEmoji.getExistingId();
			String name = loadedEmoji.getName();
			File file = loadedEmoji.getFile();
			long lastModified = loadedEmoji.getLastModified();

			if (loadedEmoji.isAnimated())
			{
				Emoji animatedEmoji = this.registerAnimatedEmoji(name, file, lastModified, dim, loadedEmoji.getImage(), existingId);
				registered.add(animatedEmoji);
			}
			else
			{
				Emoji staticEmoji = this.registerStaticEmoji(name, file, lastModified, dim, loadedEmoji.getImage(), existingId);
				registered.add(staticEmoji);
			}
		}

		return registered;
	}

	private StaticEmoji registerStaticEmoji(String name, File file, long lastModified, Dimension dim, BufferedImage image, Integer existingId)
	{
		int iconId;
		if (existingId != null)
		{
			iconId = existingId;
			this.chatIconManager.updateChatIcon(iconId, image);
		}
		else
		{
			iconId = this.chatIconManager.registerChatIcon(image);
		}
		return new StaticEmoji(iconId, name, file, lastModified, dim);
	}

	private AnimatedEmoji registerAnimatedEmoji(String name, File file, long lastModified, Dimension dim, BufferedImage staticImage, Integer existingId)
	{
		BufferedImage placeholderImage = this.createTransparentPlaceholder(dim.width, dim.height);
		int iconId;
		if (existingId != null)
		{
			iconId = existingId;
			this.chatIconManager.updateChatIcon(iconId, placeholderImage);
		}
		else
		{
			iconId = this.chatIconManager.registerChatIcon(placeholderImage);
		}
		return new AnimatedEmoji(iconId, name, file, lastModified, dim, staticImage, placeholderImage);
	}

	private BufferedImage createTransparentPlaceholder(int width, int height)
	{
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	private boolean shouldResizeEmoji(String emojiName)
	{
		Set<String> resizingDisabledEmojis = PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
		boolean isResizingDisabled = resizingDisabledEmojis.contains(emojiName);
		return !isResizingDisabled;
	}

	public List<String> getErrors()
	{
		return this.errors;
	}

	public GifAnimation getOrLoadAnimation(AnimatedEmoji emoji)
	{
		int emojiId = emoji.getId();

		GifAnimation cached = this.loadedAnimations.get(emojiId);
		if (cached != null)
		{
			return cached;
		}

		boolean isAlreadyLoading = this.pendingAnimationLoads.contains(emojiId);
		if (isAlreadyLoading)
		{
			return null;
		}

		this.pendingAnimationLoads.add(emojiId);
		File file = emoji.getFile();
		String emojiText = emoji.getText();

		Dimension targetDimension = emoji.getDimension();

		this.executor.submit(() ->
		{
			try
			{
				GifAnimation animation = this.extractGifFrames(file, targetDimension);
				if (animation != null)
				{
					this.loadedAnimations.put(emojiId, animation);
					log.debug("Loaded animation: {} (id={}, frames={}, total loaded={})", emojiText, emojiId, animation.getFrameCount(), this.loadedAnimations.size());
				}
			}
			catch (IOException e)
			{
				log.warn("Failed to load animation: {}", emojiText, e);
			}
			finally
			{
				this.pendingAnimationLoads.remove(emojiId);
			}
		});

		return null;
	}

	private GifAnimation extractGifFrames(File file, Dimension targetDimension) throws IOException
	{
		byte[] imageBytes = Files.readAllBytes(file.toPath());

		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes)))
		{
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(stream);

			int frameCount = reader.getNumImages(true);
			if (frameCount == 0)
			{
				return null;
			}

			BufferedImage[] frames = new BufferedImage[frameCount];
			int[] frameDelays = new int[frameCount];

			int targetWidth = targetDimension.width;
			int targetHeight = targetDimension.height;

			for (int i = 0; i < frameCount; i++)
			{
				BufferedImage frame = reader.read(i);
				boolean needsResize = frame.getWidth() != targetWidth || frame.getHeight() != targetHeight;
				if (needsResize)
				{
					frame = ImageUtil.resizeImage(frame, targetWidth, targetHeight);
				}
				frames[i] = frame;
				frameDelays[i] = this.getFrameDelay(reader, i);
			}

			reader.dispose();
			return new GifAnimation(frames, frameDelays);
		}
	}

	private int getFrameDelay(ImageReader reader, int frameIndex)
	{
		try
		{
			IIOMetadata metadata = reader.getImageMetadata(frameIndex);
			String formatName = "javax_imageio_gif_image_1.0";
			IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
			NodeList graphicControlExtensions = root.getElementsByTagName("GraphicControlExtension");

			if (graphicControlExtensions.getLength() > 0)
			{
				IIOMetadataNode graphicControlExtension = (IIOMetadataNode) graphicControlExtensions.item(0);
				String delayTime = graphicControlExtension.getAttribute("delayTime");
				int delay = Integer.parseInt(delayTime) * 10;
				return delay > 0 ? delay : 100;
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to get frame delay for frame {}: {}", frameIndex, e.getMessage());
		}

		return 100;
	}

	public void markAnimationVisible(int emojiId)
	{
		this.animationLastSeenTime.put(emojiId, System.currentTimeMillis());
	}

	public void unloadStaleAnimations(Set<Integer> visibleEmojiIds)
	{
		long currentTime = System.currentTimeMillis();
		Set<Integer> toRemove = new HashSet<>();

		for (Map.Entry<Integer, Long> entry : this.animationLastSeenTime.entrySet())
		{
			int emojiId = entry.getKey();
			long lastSeen = entry.getValue();

			boolean isVisible = visibleEmojiIds.contains(emojiId);
			boolean isStale = (currentTime - lastSeen) > ANIMATION_UNLOAD_DELAY_MS;

			if (!isVisible && isStale)
			{
				toRemove.add(emojiId);
			}
		}

		if (!toRemove.isEmpty())
		{
			log.debug("Unloading {} stale animations (remaining={})", toRemove.size(), this.loadedAnimations.size() - toRemove.size());
		}

		for (Integer emojiId : toRemove)
		{
			GifAnimation animation = this.loadedAnimations.remove(emojiId);
			if (animation != null)
			{
				this.flushAnimationFrames(animation);
			}
			this.animationLastSeenTime.remove(emojiId);
		}
	}

	public void clearAnimationCache()
	{
		log.debug("Clearing animation cache - unloading {} animations", this.loadedAnimations.size());
		for (GifAnimation animation : this.loadedAnimations.values())
		{
			this.flushAnimationFrames(animation);
		}
		this.loadedAnimations.clear();
		this.animationLastSeenTime.clear();
		this.pendingAnimationLoads.clear();
	}

	private void flushAnimationFrames(GifAnimation animation)
	{
		for (BufferedImage frame : animation.getFrames())
		{
			if (frame != null)
			{
				frame.flush();
			}
		}
	}

	public static Set<String> getEmojiNamesFromFolder(File folder)
	{
		Set<String> names = new HashSet<>();
		List<File> files = FileUtils.flattenFolder(folder);

		for (File file : files)
		{
			String fileName = file.getName();
			int extensionIndex = fileName.lastIndexOf('.');
			if (extensionIndex > 0)
			{
				String emojiName = fileName.substring(0, extensionIndex).toLowerCase();
				names.add(emojiName);
			}
		}

		return names;
	}

	@Value
	private static class LoadedEmoji
	{
		String name;
		File file;
		long lastModified;
		BufferedImage image;
		Integer existingId;
		boolean animated;
	}
}