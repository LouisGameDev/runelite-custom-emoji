package com.customemoji.io;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.lifecycle.Lifecycle;
import com.customemoji.model.Emoji;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class FileWatcher implements Lifecycle
{
	private final ClientThread clientThread;
	private final EmojiLoader emojiLoader;
	private final SoundojiLoader soundojiLoader;
	private final Map<String, Emoji> emojis;

	private WatchService watchService;
	private ExecutorService watcherExecutor;
	private ScheduledExecutorService debounceExecutor;
	private ScheduledFuture<?> pendingReload;

	private File emojisFolder;
	private File soundojisFolder;
	private ReloadCallback reloadCallback;
	private boolean started = false;

	@FunctionalInterface
	public interface ReloadCallback
	{
		void onReloadComplete(int emojiCount, int soundojiCount);
	}

	@Inject
	public FileWatcher(
		ClientThread clientThread,
		EmojiLoader emojiLoader,
		SoundojiLoader soundojiLoader,
		Map<String, Emoji> emojis)
	{
		this.clientThread = clientThread;
		this.emojiLoader = emojiLoader;
		this.soundojiLoader = soundojiLoader;
		this.emojis = emojis;
	}

	/**
	 * Configures the file watcher with folders and callback.
	 * Must be called before startUp().
	 */
	public void configure(File emojisFolder, File soundojisFolder, ReloadCallback reloadCallback)
	{
		this.emojisFolder = emojisFolder;
		this.soundojisFolder = soundojisFolder;
		this.reloadCallback = reloadCallback;
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
	public void startUp() throws IOException
	{
		if (this.started)
		{
			return;
		}

		if (this.emojisFolder == null || this.soundojisFolder == null)
		{
			log.warn("FileWatcher not configured - call configure() before startUp()");
			return;
		}

		this.watchService = FileSystems.getDefault().newWatchService();

		Path emojiPath = this.emojisFolder.toPath();
		Path soundojiPath = this.soundojisFolder.toPath();

		if (Files.exists(emojiPath))
		{
			this.registerRecursively(emojiPath);
		}

		if (Files.exists(soundojiPath))
		{
			this.registerRecursively(soundojiPath);
		}

		this.watcherExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "CustomEmoji-FileWatcher");
			t.setDaemon(true);
			return t;
		});

		this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CustomEmoji-Debouncer");
			t.setDaemon(true);
			return t;
		});

		this.watcherExecutor.submit(this::watchForChanges);

		this.started = true;
		log.info("File watcher setup complete for emoji folders");
	}

	@Override
	public void shutDown()
	{
		if (!this.started)
		{
			return;
		}

		log.debug("Starting file watcher shutdown");

		if (this.pendingReload != null)
		{
			boolean cancelled = this.pendingReload.cancel(true);
			log.debug("Pending reload task cancelled: {}", cancelled);
			this.pendingReload = null;
		}

		if (this.watchService != null)
		{
			try
			{
				this.watchService.close();
				log.debug("Watch service closed");
			}
			catch (IOException e)
			{
				log.error("Failed to close watch service", e);
			}
			this.watchService = null;
		}

		this.shutdownExecutor(this.watcherExecutor, "watcher executor");
		this.shutdownExecutor(this.debounceExecutor, "debounce executor");

		this.debounceExecutor = null;
		this.watcherExecutor = null;

		this.started = false;
		log.debug("File watcher shutdown complete");
	}

	public void scheduleReload(boolean force)
	{
		synchronized (this)
		{
			if (this.debounceExecutor == null || this.debounceExecutor.isShutdown())
			{
				log.debug("Skipping reload schedule - executor is shutdown");
				return;
			}

			if (this.pendingReload != null && !this.pendingReload.isDone())
			{
				this.pendingReload.cancel(false);
				log.debug("Cancelled pending emoji reload due to new file changes");
			}

			int delayMilliseconds = 500;

			this.pendingReload = this.debounceExecutor.schedule(() -> {
				this.clientThread.invokeLater(() -> this.reloadEmojis(force));
			}, delayMilliseconds, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with 500ms debounce");
		}
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

	private void registerRecursively(Path path) throws IOException
	{
		path.register(this.watchService,
			StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_DELETE,
			StandardWatchEventKinds.ENTRY_MODIFY);

		Files.walk(path)
			.filter(Files::isDirectory)
			.filter(p -> !p.equals(path))
			.filter(p -> !p.getFileName().toString().equals(".git"))
			.forEach(subPath -> {
				try
				{
					subPath.register(this.watchService,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE,
						StandardWatchEventKinds.ENTRY_MODIFY);
				}
				catch (IOException e)
				{
					log.error("Failed to register subdirectory for watching: {}", subPath, e);
				}
			});
	}

	private void watchForChanges()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				if (this.watchService == null)
				{
					log.debug("Watch service is null, stopping file watcher");
					break;
				}

				WatchKey key = this.watchService.take();

				if (this.watchService == null)
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

					if (changed == null)
					{
						log.debug("Skipping file event with null context");
						continue;
					}

					if (EmojiLoader.isEmojiFile(changed) || SoundojiLoader.isSoundojiFile(changed))
					{
						shouldReload = true;
						log.debug("Detected change in emoji/soundoji file: {}", changed);
					}

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
								if (this.watchService != null)
								{
									this.registerRecursively(fullPath);
									log.debug("Registered new directory for watching: {}", fullPath);
								}
							}
							catch (IOException e)
							{
								log.error("Failed to register new directory: {}", fullPath, e);
							}
						}
					}
				}

				if (shouldReload)
				{
					this.scheduleReload(false);
				}

				if (!key.reset())
				{
					log.debug("Watch key reset failed, stopping file watcher");
					break;
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
				if (this.watchService == null)
				{
					log.debug("File watcher error due to closed watch service, stopping");
					break;
				}
				log.error("Error in file watcher", e);
				break;
			}
		}
		log.debug("File watcher thread exiting");
	}

	private void reloadEmojis(boolean force)
	{
		log.info("Reloading emojis and soundojis due to file changes");

		if (force)
		{
			this.emojis.clear();
		}

		this.soundojiLoader.clear();

		if (!this.emojisFolder.exists())
		{
			log.warn("Emoji folder does not exist: {}", this.emojisFolder);
			this.emojis.clear();
			this.soundojiLoader.load(this.soundojisFolder);
			this.notifyReloadComplete();
			return;
		}

		this.emojiLoader.loadAsync(this.emojisFolder, true, () -> {
			this.soundojiLoader.load(this.soundojisFolder);
			this.notifyReloadComplete();
		});
	}

	private void notifyReloadComplete()
	{
		int emojiCount = this.emojis.size();
		int soundojiCount = this.soundojiLoader.getLoadedCount();

		if (this.reloadCallback != null)
		{
			this.reloadCallback.onReloadComplete(emojiCount, soundojiCount);
		}
	}
}
