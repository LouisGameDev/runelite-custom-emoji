package com.customemoji.io;

import javax.inject.Inject;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.BeforeEmojisLoaded;
import com.customemoji.event.LoadingProgress;
import com.customemoji.event.LoadingProgress.LoadingStage;
import com.customemoji.event.EmojiStateChanged;
import com.customemoji.event.ReloadEmojisRequested;
import com.customemoji.model.Emoji;
import com.customemoji.model.EmojiDto;
import com.customemoji.model.Lifecycle;
import com.customemoji.service.EmojiStateManager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

@Slf4j
public class EmojiLoader implements Lifecycle
{
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = ImmutableSet.of("png", "jpg", "jpeg", "gif", "bmp");
	private static final URL EXAMPLE_EMOJI = Resources.getResource("com/customemoji/checkmark.png");

	public static boolean isSupportedImageFormat(File file)
	{
		String name = file.getName().toLowerCase();
		int dotIndex = name.lastIndexOf('.');
		if (dotIndex < 0)
		{
			return false;
		}

		String extension = name.substring(dotIndex + 1);
		return SUPPORTED_IMAGE_EXTENSIONS.contains(extension);
	}

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private EmojiStateManager emojiStateManager;

	@Inject
	private EventBus eventBus;

	@Getter
	protected final Map<String, Emoji> emojis = new ConcurrentHashMap<>();

	@Getter
	private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

	private ExecutorService executor;

	@Override
	public void startUp()
	{
		log.debug("EmojiLoader.startUp() called, useNewEmojiLoader={}", this.config.useNewEmojiLoader());
		if (this.config.useNewEmojiLoader())
		{
			this.firstTimeSetup();
			this.executor = Executors.newSingleThreadExecutor(r ->
			{
				Thread thread = new Thread(r, "CustomEmoji-Loader");
				thread.setDaemon(true);
				return thread;
			});
			this.eventBus.register(this);
			this.executor.submit(this::loadAllEmojis);
		}
	}

	@Override
	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.emojis.clear();
		if (this.executor != null)
		{
			this.executor.shutdownNow();
			this.executor = null;
		}
	}

	private void firstTimeSetup()
	{
		if (!EMOJIS_FOLDER.mkdir())
		{
			return;
		}

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

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.useNewEmojiLoader();
	}

	@Subscribe
	public void onReloadEmojisRequested(ReloadEmojisRequested event)
	{
		if (this.executor == null || this.executor.isShutdown())
		{
			return;
		}

		this.executor.submit(this::loadAllEmojis);
	}

	@Subscribe
	public void onEmojiStateChanged(EmojiStateChanged event)
	{
		if (event.getChangeType() != EmojiStateChanged.ChangeType.RESIZING_TOGGLED)
		{
			return;
		}

		if (this.executor == null || this.executor.isShutdown())
		{
			return;
		}

		this.emojis.remove(event.getEmojiName());
		this.executor.submit(this::loadAllEmojis);
	}

	private void loadAllEmojis()
	{
		this.errors.clear();

		BeforeEmojisLoaded beforeEvent = new BeforeEmojisLoaded(this.emojis);
		this.eventBus.post(beforeEvent);
		beforeEvent.awaitCompletion();

		try
		{
			if (!EMOJIS_FOLDER.isDirectory())
			{
				log.error("Provided path is not a directory: {}", EMOJIS_FOLDER.getPath());
				return;
			}

			List<File> files = FileUtils.flattenFolder(EMOJIS_FOLDER, EmojiLoader::isSupportedImageFormat);
			int totalFiles = files.size();

			Set<String> namesOnDisk = new HashSet<>();
			List<EmojiDto> loadedDtos = new ArrayList<>();
			for (int i = 0; i < files.size(); i++)
			{
				File file = files.get(i);
				this.eventBus.post(new LoadingProgress(LoadingStage.LOADING_IMAGES, totalFiles, i + 1, file.getName()));

				String emojiName = FileUtils.getNameWithoutExtension(file);
				if (emojiName != null)
				{
					namesOnDisk.add(emojiName);
				}

				EmojiDto dto = this.loadEmojiData(file);
				if (dto != null && dto.getStaticImage() != null)
				{
					loadedDtos.add(dto);
				}
			}

			log.debug("Loaded {} emoji images, registering with ChatIconManager", loadedDtos.size());

			CountDownLatch latch = new CountDownLatch(1);
			this.clientThread.invokeLater(() ->
			{
				try
				{
					for (int i = 0; i < loadedDtos.size(); i++)
					{
						EmojiDto dto = loadedDtos.get(i);
						Emoji emoji = this.registerEmoji(dto);
						if (emoji != null)
						{
							this.emojis.put(emoji.getText(), emoji);
						}
					}

					this.emojis.keySet().removeIf(name -> !namesOnDisk.contains(name));
				}
				finally
				{
					latch.countDown();
				}
			});

			latch.await();
		}
		catch (Exception e)
		{
			log.error("Error loading emojis", e);
		}
		finally
		{
			log.debug("EmojiLoader finished loading {} emojis", this.emojis.size());
			this.eventBus.post(new LoadingProgress(LoadingStage.COMPLETE, 0, 0, null));
			this.eventBus.post(new AfterEmojisLoaded(this.emojis));
		}
	}

	private Emoji registerEmoji(EmojiDto dto)
	{
		try
		{
			Integer iconId = this.chatIconManager.reserveChatIcon();
			Integer index = this.chatIconManager.chatIconIndex(iconId);

			dto.setIconId(iconId);
			dto.setIndex(index);
			
			BufferedImage placeholderImage = new BufferedImage(
				dto.getDimension().width,
				dto.getDimension().height,
				BufferedImage.TYPE_INT_ARGB
			);

			this.chatIconManager.updateChatIcon(dto.getIconId(), placeholderImage);

			if (dto.isZeroWidth())
			{
				Integer zerWidthIconId = this.chatIconManager.reserveChatIcon();
				Integer zerWidthIndex = this.chatIconManager.chatIconIndex(zerWidthIconId);

				dto.setZeroWidthIconId(zerWidthIconId);
				dto.setZeroWidthIndex(zerWidthIndex);

				BufferedImage zeroWidthPlaceholder = new BufferedImage(
					1,
					dto.getDimension().height,
					BufferedImage.TYPE_INT_ARGB
				);

				this.chatIconManager.updateChatIcon(dto.getZeroWidthIconId(), zeroWidthPlaceholder);
			}

			return dto.toEmoji();
		}
		catch (Exception e)
		{
			this.recordError("Failed to register emoji: " + dto.getText());
			return null;
		}
	}

	private void recordError(String message)
	{
		log.error(message);
		this.errors.add(message);
	}

	private EmojiDto loadEmojiData(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			log.debug("Skipping file without extension: {}", file.getName());
			return null;
		}

		String name = file.getName().substring(0, extension).toLowerCase();
		long fileModified = file.lastModified();

		Emoji existingEmoji = this.emojis.get(name);

		if (existingEmoji != null && existingEmoji.getLastModified() == fileModified)
		{
			log.debug("Emoji file unchanged: {}", name);
			return null;
		}

		if (existingEmoji != null)
		{
			boolean existingIsLocal = !existingEmoji.getFile().getPath().contains("github-pack");
			boolean newIsGithub = file.getPath().contains("github-pack");
			if (existingIsLocal && newIsGithub)
			{
				log.debug("Skipped - local emoji takes priority: {}", name);
				return null;
			}
		}

		BufferedImage imageResult = FileUtils.loadImage(file);

		if (imageResult == null)
		{
			this.recordError("Failed to load image for emoji: " + name);
			return null;
		}

		try
		{
			boolean shouldResize = this.emojiStateManager.isResizingEnabled(name);
			boolean isAnimated = PluginUtils.isAnimatedGif(file);

			BufferedImage image = shouldResize ? PluginUtils.resizeImage(imageResult, this.config.maxImageHeight()) : imageResult;
			Dimension dimension = new Dimension(image.getWidth(), image.getHeight());

			boolean isZeroWidth = name.endsWith("00");

			return EmojiDto.builder()
						   .text(name)
						   .file(file)
						   .dimension(dimension)
						   .lastModified(fileModified)
						   .staticImage(image)
						   .isAnimated(isAnimated)
						   .isZeroWidth(isZeroWidth)
						   .build();
		}
		catch (RuntimeException e)
		{
			this.recordError("Failed to process image for emoji: " + name);
			return null;
		}
	}
}
