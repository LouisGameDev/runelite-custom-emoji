package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiImageUtilities;
import com.customemoji.PluginUtils;
import com.customemoji.Result;
import com.customemoji.model.Emoji;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ChatIconManager;

@Slf4j
@Singleton
public class EmojiLoader
{
	private final ChatIconManager chatIconManager;
	private final CustomEmojiConfig config;

	@Inject
	public EmojiLoader(ChatIconManager chatIconManager, CustomEmojiConfig config)
	{
		this.chatIconManager = chatIconManager;
		this.config = config;
	}

	public void loadEmojis(File emojiFolder, Map<String, Emoji> emojis)
	{
		if (emojiFolder.mkdir())
		{
			log.debug("Created emoji folder: {}", emojiFolder);
		}

		Result<List<Emoji>, List<Throwable>> result = this.loadEmojisFromFolder(emojiFolder, emojis);
		result.ifOk(list ->
		{
			list.forEach(emoji -> emojis.put(emoji.getText(), emoji));
			log.info("Loaded {} emojis", result.unwrap().size());
		});
		result.ifError(errors ->
			errors.forEach(throwable ->
			{
				String fileName = FileUtils.extractFileNameFromErrorMessage(throwable.getMessage());
				log.debug("Skipped non-emoji file: {}", fileName);
			})
		);
	}

	public Result<List<Emoji>, List<Throwable>> loadEmojisFromFolder(File folder, Map<String, Emoji> existingEmojis)
	{
		if (!folder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder: " + folder)));
		}

		List<File> files = FileUtils.flattenFolder(folder);
		List<Emoji> loadedEmojis = new ArrayList<>();
		List<Throwable> errors = new ArrayList<>();

		for (File file : files)
		{
			Result<Emoji, Throwable> result = this.loadEmoji(file, existingEmojis);
			result.ifOk(loadedEmojis::add);
			result.ifError(errors::add);
		}

		if (errors.isEmpty())
		{
			return Ok(loadedEmojis);
		}
		else
		{
			return PartialOk(loadedEmojis, errors);
		}
	}

	public Result<Emoji, Throwable> loadEmoji(File file, Map<String, Emoji> existingEmojis)
	{
		int extensionIndex = file.getName().lastIndexOf('.');
		boolean hasNoExtension = extensionIndex < 0;

		if (hasNoExtension)
		{
			return Error(new IllegalArgumentException("Illegal file name <col=00FFFF>" + file));
		}

		String emojiName = file.getName().substring(0, extensionIndex).toLowerCase();
		long fileModified = file.lastModified();

		Emoji existingEmoji = existingEmojis.get(emojiName);
		boolean emojiExists = existingEmoji != null;
		boolean fileUnchanged = emojiExists && existingEmoji.getLastModified() == fileModified;

		if (fileUnchanged)
		{
			log.debug("Emoji file unchanged, skipping: {} (last modified: {})", emojiName, fileModified);
			return Ok(existingEmoji);
		}

		Result<BufferedImage, Throwable> imageResult = EmojiLoader.loadImage(file);

		if (imageResult.isOk())
		{
			try
			{
				int iconId;
				boolean shouldResize = this.shouldResizeEmoji(emojiName);
				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);

				if (emojiExists)
				{
					this.chatIconManager.updateChatIcon(existingEmoji.getId(), normalizedImage);
					iconId = existingEmoji.getId();
					log.info("Updated existing chat icon for emoji: {} (id: {})", emojiName, iconId);
				}
				else
				{
					iconId = this.chatIconManager.registerChatIcon(normalizedImage);
					log.info("Registered new chat icon for emoji: {} (id: {})", emojiName, iconId);
				}

				Dimension dimension = new Dimension(normalizedImage.getWidth(), normalizedImage.getHeight());
				return Ok(new Emoji(iconId, emojiName, file, fileModified, dimension));
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

	public void reloadSingleEmoji(String emojiName, Map<String, Emoji> emojis)
	{
		Emoji emoji = emojis.get(emojiName);
		if (emoji == null)
		{
			log.warn("Cannot reload emoji '{}' - not found", emojiName);
			return;
		}

		File file = emoji.getFile();
		Result<BufferedImage, Throwable> imageResult = EmojiLoader.loadImage(file);

		if (imageResult.isOk())
		{
			try
			{
				boolean shouldResize = this.shouldResizeEmoji(emojiName);
				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config, shouldResize);

				this.chatIconManager.updateChatIcon(emoji.getId(), normalizedImage);

				Dimension dimension = new Dimension(normalizedImage.getWidth(), normalizedImage.getHeight());
				Emoji updatedEmoji = new Emoji(emoji.getId(), emojiName, file, file.lastModified(), dimension);
				emojis.put(emojiName, updatedEmoji);

				log.info("Reloaded emoji '{}' with resizing={}", emojiName, shouldResize);
			}
			catch (RuntimeException e)
			{
				log.error("Failed to reload emoji '{}'", emojiName, e);
			}
		}
		else
		{
			log.error("Failed to load image for emoji '{}'", emojiName, imageResult.unwrapError());
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

	private boolean shouldResizeEmoji(String emojiName)
	{
		Set<String> resizingDisabledEmojis = PluginUtils.parseResizingDisabledEmojis(this.config.resizingDisabledEmojis());
		boolean isResizingDisabled = resizingDisabledEmojis.contains(emojiName);
		return !isResizingDisabled;
	}
}