package com.customemoji.io;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.collect.ImmutableSet;

@Slf4j
@Singleton
public class EmojiLoader implements Lifecycle
{
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = ImmutableSet.of("png", "jpg", "jpeg", "gif");

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

	public final AtomicBoolean isLoading = new AtomicBoolean(false);

	private ExecutorService executor;

	@Override
	public void startUp()
	{
		this.firstTimeSetup();
		this.executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "CustomEmoji-Loader");
			thread.setDaemon(true);
			return thread;
		});
		this.eventBus.register(this);
		this.executor.submit(() -> this.loadAllEmojis(false));
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
		EMOJIS_FOLDER.mkdir();
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
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
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
				this.executor.submit(() -> this.loadAllEmojis(true));
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onReloadEmojisRequested(ReloadEmojisRequested event)
	{
		if (this.executor == null || this.executor.isShutdown())
		{
			return;
		}

		this.executor.submit(() -> this.loadAllEmojis(event.isForceReload()));
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

		this.executor.submit(() -> this.updateEmoji(event.getEmojiName()));
	}

	private void updateEmoji(String emojiName)
	{
		Emoji existing = this.emojis.get(emojiName);
		if (existing == null)
		{
			return;
		}

		EmojiDto dto = this.buildEmojiDto(emojiName, existing.getFile());
		if (dto == null)
		{
			return;
		}

		this.clientThread.invokeLater(() ->
		{
			Emoji emoji = this.registerEmoji(dto);
			if (emoji != null)
			{
				this.emojis.put(emojiName, emoji);
				this.eventBus.post(new AfterEmojisLoaded(this.emojis));
			}
		});
	}

	private void loadAllEmojis(boolean forceReload)
	{
		BeforeEmojisLoaded beforeEvent = new BeforeEmojisLoaded(this.emojis);
		this.eventBus.post(beforeEvent);
		beforeEvent.awaitCompletion();

		this.isLoading.set(true);
		this.errors.clear();

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

				EmojiDto dto = this.loadEmojiData(file, forceReload);
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
			this.isLoading.set(false);
			log.debug("EmojiLoader finished loading {} emojis", this.emojis.size());
			this.eventBus.post(new LoadingProgress(LoadingStage.COMPLETE, 0, 0, null));
			this.eventBus.post(new AfterEmojisLoaded(this.emojis));
		}
	}

	private Emoji registerEmoji(EmojiDto dto)
	{
		try
		{
			Emoji existing = this.emojis.get(dto.getText());

			if (existing != null)
			{
				// Reuse existing IDs (swapped due to iconId/index naming mismatch in toEmoji)
				dto.setIconId(existing.getIndex());
				dto.setIndex(existing.getIconId());
			}
			else
			{
				Integer iconId = this.chatIconManager.reserveChatIcon();
				Integer index = this.chatIconManager.chatIconIndex(iconId);
				dto.setIconId(iconId);
				dto.setIndex(index);
			}

			BufferedImage placeholderImage = new BufferedImage(
				dto.getDimension().width,
				dto.getDimension().height,
				BufferedImage.TYPE_INT_ARGB
			);

			this.chatIconManager.updateChatIcon(dto.getIconId(), placeholderImage);

			if (dto.isZeroWidth())
			{
				if (existing != null && existing.hasZeroWidthId())
				{
					dto.setZeroWidthIconId(existing.getZeroWidthIndex());
					dto.setZeroWidthIndex(existing.getZeroWidthIconId());
				}
				else
				{
					Integer zeroWidthIconId = this.chatIconManager.reserveChatIcon();
					Integer zeroWidthIndex = this.chatIconManager.chatIconIndex(zeroWidthIconId);
					dto.setZeroWidthIconId(zeroWidthIconId);
					dto.setZeroWidthIndex(zeroWidthIndex);
				}

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

	private EmojiDto loadEmojiData(File file, boolean forceReload)
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

		boolean fileUnchanged = existingEmoji != null && existingEmoji.getLastModified() == fileModified;
		if (fileUnchanged && !forceReload)
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

		return this.buildEmojiDto(name, file);
	}

	private EmojiDto buildEmojiDto(String name, File file)
	{
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
						   .lastModified(file.lastModified())
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
