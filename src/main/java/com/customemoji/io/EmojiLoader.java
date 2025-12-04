package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiImageUtilities;
import com.customemoji.PluginUtils;
import com.customemoji.Result;
import com.customemoji.lifecycle.Lifecycle;
import com.customemoji.model.Emoji;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.io.Resources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;

@Slf4j
@Singleton
public class EmojiLoader implements Lifecycle
{
	private static final URL EXAMPLE_EMOJI = Resources.getResource(EmojiLoader.class, "../checkmark.png");

	private final ClientThread clientThread;
	private final ChatIconManager chatIconManager;
	private final CustomEmojiConfig config;
	private final Map<String, Emoji> emojis;

	private ExecutorService loaderExecutor;
	private boolean started = false;

	@Inject
	public EmojiLoader(
		ClientThread clientThread,
		ChatIconManager chatIconManager,
		CustomEmojiConfig config,
		Map<String, Emoji> emojis)
	{
		this.clientThread = clientThread;
		this.chatIconManager = chatIconManager;
		this.config = config;
		this.emojis = emojis;
	}

	public void firstTimeSetup(File emojisFolder)
	{
		if (emojisFolder.mkdir())
		{
			File exampleEmoji = new File(emojisFolder, "checkmark.png");
			try (InputStream in = EXAMPLE_EMOJI.openStream())
			{
				Files.copy(in, exampleEmoji.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to copy example emoji", e);
			}
		}
	}

	public void loadAsync(File emojiFolder, boolean isReload, @Nullable Runnable onComplete)
	{
		if (emojiFolder.mkdir())
		{
			log.info("Created emoji folder");
		}

		if (this.loaderExecutor == null || this.loaderExecutor.isShutdown())
		{
			this.loaderExecutor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "CustomEmoji-Loader");
				t.setDaemon(true);
				return t;
			});
		}

		Set<String> currentEmojiNames = isReload ? new HashSet<>(this.emojis.keySet()) : Collections.emptySet();

		this.loaderExecutor.submit(() -> {
			try
			{
				List<File> files = PluginUtils.flattenFolder(emojiFolder);
				List<PreloadedEmoji> preloadedEmojis = new ArrayList<>();
				List<Throwable> loadErrors = new ArrayList<>();

				for (File file : files)
				{
					Result<PreloadedEmoji, Throwable> result = this.preloadEmoji(file);
					result.ifOk(preloadedEmojis::add);
					result.ifError(loadErrors::add);
				}

				log.info("Preloaded {} emoji images on background thread", preloadedEmojis.size());

				this.clientThread.invokeLater(() -> {
					Set<String> newEmojiNames = new HashSet<>();

					for (PreloadedEmoji preloaded : preloadedEmojis)
					{
						try
						{
							Emoji emoji = this.registerPreloadedEmoji(preloaded);
							this.emojis.put(emoji.getText(), emoji);
							newEmojiNames.add(emoji.getText());
						}
						catch (Exception e)
						{
							log.error("Failed to register emoji: {}", preloaded.text, e);
						}
					}

					if (isReload)
					{
						currentEmojiNames.removeAll(newEmojiNames);
						currentEmojiNames.forEach(deletedEmoji -> {
							log.debug("Removing deleted emoji: {}", deletedEmoji);
							this.emojis.remove(deletedEmoji);
						});
					}

					loadErrors.forEach(t -> {
						String fileName = PluginUtils.extractFileName(t.getMessage());
						//log.debug("Skipped non-emoji file: {}", fileName);
					});

					log.info("Registered {} emojis on client thread", newEmojiNames.size());

					if (onComplete != null)
					{
						onComplete.run();
					}
				});
			}
			catch (Exception e)
			{
				log.error("Error during async emoji loading", e);
			}
		});
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	@Override
	public boolean isStarted()
	{
		return this.started;
	}

	@Override
	public void startUp()
	{
		if (this.started)
		{
			return;
		}

		this.started = true;
		log.debug("EmojiLoader started");
	}

	@Override
	public void shutDown()
	{
		if (!this.started)
		{
			return;
		}

		if (this.loaderExecutor != null)
		{
			log.debug("Shutting down emoji loader executor");
			this.loaderExecutor.shutdownNow();
			this.loaderExecutor = null;
		}

		this.started = false;
	}

	public static boolean isEmojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
	}

	private Result<PreloadedEmoji, Throwable> preloadEmoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			return Error(new IllegalArgumentException("Illegal file name <col=00FFFF>" + file));
		}

		String text = file.getName().substring(0, extension).toLowerCase();
		long fileModified = file.lastModified();

		Emoji existingEmoji = this.emojis.get(text);

		if (existingEmoji != null)
		{
			boolean isUnchanged = existingEmoji.getLastModified() == fileModified;
			if (isUnchanged)
			{
				log.debug("Emoji file unchanged, skipping: {} (last modified: {})", text, fileModified);
				Dimension existingDimension = existingEmoji.getDimension();
				return Ok(new PreloadedEmoji(text, file, fileModified, null, existingDimension, existingEmoji));
			}
		}

		Result<BufferedImage, Throwable> imageResult = loadImage(file);

		if (imageResult.isOk())
		{
			try
			{
				BufferedImage normalizedImage = CustomEmojiImageUtilities.normalizeImage(imageResult.unwrap(), this.config);
				Dimension dimension = new Dimension(normalizedImage.getWidth(), normalizedImage.getHeight());
				return Ok(new PreloadedEmoji(text, file, fileModified, normalizedImage, dimension, existingEmoji));
			}
			catch (RuntimeException e)
			{
				return Error(new RuntimeException(
					"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + e.getMessage(), e));
			}
		}
		else
		{
			Throwable throwable = imageResult.unwrapError();
			return Error(new RuntimeException(
				"<col=FF0000>" + file.getName() + "</col> failed because <col=FF0000>" + throwable.getMessage(), throwable));
		}
	}

	private Emoji registerPreloadedEmoji(PreloadedEmoji preloaded)
	{
		boolean hasExistingEmoji = preloaded.existingEmoji != null;
		boolean hasNewImage = preloaded.normalizedImage != null;

		if (hasExistingEmoji && !hasNewImage)
		{
			return preloaded.existingEmoji;
		}

		int id;

		if (hasExistingEmoji)
		{
			Emoji existingEmoji = preloaded.existingEmoji;
			this.chatIconManager.updateChatIcon(existingEmoji.getId(), preloaded.normalizedImage);
			id = existingEmoji.getId();
			log.info("Updated existing chat icon for emoji: {} (id: {})", preloaded.text, id);
		}
		else
		{
			id = this.chatIconManager.registerChatIcon(preloaded.normalizedImage);
			//log.info("Registered new chat icon for emoji: {} (id: {})", preloaded.text, id);
		}

		return new Emoji(id, preloaded.text, preloaded.file, preloaded.lastModified, preloaded.dimension);
	}

	public static Result<BufferedImage, Throwable> loadImage(File file)
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

	static class PreloadedEmoji
	{
		final String text;
		final File file;
		final long lastModified;
		final BufferedImage normalizedImage;
		final Dimension dimension;
		final Emoji existingEmoji;

		PreloadedEmoji(String text, File file, long lastModified, BufferedImage normalizedImage, Dimension dimension, Emoji existingEmoji)
		{
			this.text = text;
			this.file = file;
			this.lastModified = lastModified;
			this.normalizedImage = normalizedImage;
			this.dimension = dimension;
			this.existingEmoji = existingEmoji;
		}
	}
}
