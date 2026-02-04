package com.customemoji.io;

import javax.inject.Inject;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.PluginUtils;
import com.customemoji.event.AfterEmojisLoaded;
import com.customemoji.event.BeforeEmojisLoaded;
import com.customemoji.model.Emoji;
import com.customemoji.model.EmojiDto;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class EmojiLoader
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

	public void startUp()
	{
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

	public void shutDown()
	{
		this.emojis.clear();
		if (this.executor != null)
		{
			this.executor.shutdownNow();
			this.executor = null;
		}
	}
	
	private void loadAllEmojis()
	{
		this.eventBus.post(new BeforeEmojisLoaded());

		if (!EMOJIS_FOLDER.isDirectory())
		{
			log.error("Provided path is not a directory: {}", EMOJIS_FOLDER.getPath());
			return;
		}

		this.clientThread.invoke(() ->
		{
			List<File> files = FileUtils.flattenFolder(EMOJIS_FOLDER);

			for (File file : files)
			{
				Emoji result = this.loadAndRegisterEmoji(file);
				this.emojis.put(result.getText(), result);
			}

			this.eventBus.post(new AfterEmojisLoaded(this.emojis));
		});
	}

	private Emoji loadAndRegisterEmoji(File file)
	{
		EmojiDto dto = this.loadSingleEmoji(file);

		if (dto == null || !dto.isValid())
		{
			return null;
		}

		BufferedImage placeholderImage = new BufferedImage(dto.getDimension().width, dto.getDimension().height, BufferedImage.TYPE_INT_ARGB);
		this.chatIconManager.updateChatIcon(dto.getId(), placeholderImage);

		return dto.toEmoji();
	}

	private EmojiDto loadSingleEmoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			log.error("Skipping file without extension: {}", file.getName());
			return EmojiDto.builder().build();
		}

		String name = file.getName().substring(0, extension).toLowerCase();
		long fileModified = file.lastModified();

		Emoji existingEmoji = this.emojis.get(name);

		if (existingEmoji != null && existingEmoji.getLastModified() == fileModified)
		{
			log.error("Emoji file unchanged: {}", name);
			return EmojiDto.builder().build();
		}

		if (existingEmoji != null)
		{
			boolean existingIsLocal = !existingEmoji.getFile().getPath().contains("github-pack");
			boolean newIsGithub = file.getPath().contains("github-pack");
			if (existingIsLocal && newIsGithub)
			{
				log.error("Skipped - local emoji takes priority: {}", name);
				return EmojiDto.builder().build();
			}
		}

		BufferedImage imageResult = FileUtils.loadImage(file);

		if (imageResult == null)
		{
			log.error("Failed to load image for emoji: {}", name);
			return EmojiDto.builder().build();
		}

		try
		{
			boolean shouldResize = this.emojiStateManager.isResizingEnabled(name);
			boolean isAnimated = PluginUtils.isAnimatedGif(file);
			boolean isZeroWidth = name.endsWith("00");

			BufferedImage image = shouldResize ? PluginUtils.resizeImage(imageResult, this.config.maxImageHeight()) : imageResult;
			Integer index = existingEmoji != null ? existingEmoji.getId() : this.chatIconManager.reserveChatIcon();
			Integer iconId = this.chatIconManager.chatIconIndex(index);
			Dimension dimension = new Dimension(isZeroWidth ? 1 : image.getWidth(), image.getHeight());

			return EmojiDto.builder()
						   .text(name)
						   .file(file)
						   .dimension(dimension)
						   .lastModified(fileModified)
						   .staticImage(image)
						   .id(index)
						   .iconId(iconId)
						   .isAnimated(isAnimated)
						   .build();
		}
		catch (RuntimeException e)
		{
			log.error("Failed to load image for emoji: {}", name);
			return EmojiDto.builder().build();
		}
	}
}
