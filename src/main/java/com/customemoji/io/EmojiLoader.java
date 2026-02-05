package com.customemoji.io;

import javax.inject.Inject;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.LoadingProgress;
import com.customemoji.event.LoadingProgress.LoadingStage;
import com.customemoji.model.Emoji;
import com.customemoji.model.EmojiDto;
import com.customemoji.model.Lifecycle;
import com.customemoji.service.EmojiStateManager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ChatIconManager;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class EmojiLoader implements Lifecycle
{
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

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

	private ExecutorService executor;

	@Override
	public void startUp()
	{
		log.debug("EmojiLoader.startUp() called, useNewEmojiLoader={}", this.config.useNewEmojiLoader());
		if (this.config.useNewEmojiLoader())
		{
			this.executor = Executors.newSingleThreadExecutor(r ->
			{
				Thread thread = new Thread(r, "CustomEmoji-Loader");
				thread.setDaemon(true);
				return thread;
			});
			this.executor.submit(this::loadAllEmojis);
		}
	}

	@Override
	public void shutDown()
	{
		this.emojis.clear();
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor = null;
		}
	}
	
	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.useNewEmojiLoader();
	}
	
	private void loadAllEmojis()
	{
		try
		{
			if (!EMOJIS_FOLDER.isDirectory())
			{
				log.error("Provided path is not a directory: {}", EMOJIS_FOLDER.getPath());
				return;
			}

			List<File> files = FileUtils.flattenFolder(EMOJIS_FOLDER);
			int totalFiles = files.size();

			List<EmojiDto> loadedDtos = new ArrayList<>();
			for (int i = 0; i < files.size(); i++)
			{
				File file = files.get(i);
				this.eventBus.post(new LoadingProgress(LoadingStage.LOADING_IMAGES, totalFiles, i + 1, file.getName()));

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
			Integer index = this.chatIconManager.reserveChatIcon();
			Integer iconId = this.chatIconManager.chatIconIndex(index);

			BufferedImage placeholderImage = new BufferedImage(
				dto.getDimension().width,
				dto.getDimension().height,
				BufferedImage.TYPE_INT_ARGB
			);
			this.chatIconManager.updateChatIcon(index, placeholderImage);

			EmojiDto.EmojiDtoBuilder builder = EmojiDto.builder()
				.text(dto.getText())
				.file(dto.getFile())
				.dimension(dto.getDimension())
				.lastModified(dto.getLastModified())
				.staticImage(dto.getStaticImage())
				.id(index)
				.iconId(iconId)
				.isAnimated(dto.isAnimated())
				.isZeroWidth(dto.isZeroWidth());

			if (dto.isZeroWidth())
			{
				Integer zeroWidthIndex = this.chatIconManager.reserveChatIcon();
				Integer zeroWidthIconId = this.chatIconManager.chatIconIndex(zeroWidthIndex);

				BufferedImage zeroWidthPlaceholder = new BufferedImage(
					1,
					dto.getDimension().height,
					BufferedImage.TYPE_INT_ARGB
				);
				this.chatIconManager.updateChatIcon(zeroWidthIndex, zeroWidthPlaceholder);

				builder.zeroWidthId(zeroWidthIndex)
					   .zeroWidthIconId(zeroWidthIconId);
			}

			return builder.build().toEmoji();
		}
		catch (Exception e)
		{
			log.error("Failed to register emoji: {}", dto.getText(), e);
			return null;
		}
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
			log.error("Failed to load image for emoji: {}", name);
			return null;
		}

		try
		{
			boolean shouldResize = this.emojiStateManager.isResizingEnabled(name);
			boolean isAnimated = PluginUtils.isAnimatedGif(file);
			boolean isZeroWidth = name.endsWith("00");

			BufferedImage image = shouldResize ? PluginUtils.resizeImage(imageResult, this.config.maxImageHeight()) : imageResult;
			Dimension dimension = new Dimension(isZeroWidth ? 1 : image.getWidth(), image.getHeight());

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
			log.error("Failed to load image for emoji: {}", name, e);
			return null;
		}
	}
}
