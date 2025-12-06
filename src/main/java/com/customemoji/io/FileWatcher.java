package com.customemoji.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class FileWatcher
{
	@FunctionalInterface
	public interface ReloadCallback
	{
		void onReload(boolean force);
	}

	private static final long DEBOUNCE_DELAY_MS = 500;

	private final ClientThread clientThread;

	private ReloadCallback reloadCallback;
	private Path[] watchPaths;
	private WatchService watchService;
	private ExecutorService watcherExecutor;
	private ScheduledExecutorService debounceExecutor;
	private ScheduledFuture<?> pendingReload;

	@Inject
	public FileWatcher(ClientThread clientThread)
	{
		this.clientThread = clientThread;
	}

	public void start(Path[] paths, ReloadCallback callback) throws IOException
	{
		this.watchPaths = paths;
		this.reloadCallback = callback;
		this.setupWatcher();
	}

	public void shutdown()
	{
		log.debug("Starting file watcher shutdown");

		if (this.pendingReload != null)
		{
			boolean cancelled = this.pendingReload.cancel(true);
			log.debug("Pending reload task cancelled: {}", cancelled);
			this.pendingReload = null;
		}

		this.shutdownExecutor(this.debounceExecutor, "debounce executor");
		this.shutdownExecutor(this.watcherExecutor, "watcher executor");

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

		this.debounceExecutor = null;
		this.watcherExecutor = null;

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

			this.pendingReload = this.debounceExecutor.schedule(() ->
			{
				this.clientThread.invokeLater(() -> this.reloadCallback.onReload(force));
			}, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

			log.debug("Scheduled emoji reload with {}ms debounce", DEBOUNCE_DELAY_MS);
		}
	}

	private void setupWatcher() throws IOException
	{
		this.watchService = FileSystems.getDefault().newWatchService();

		for (Path path : this.watchPaths)
		{
			if (Files.exists(path))
			{
				this.registerRecursively(path);
			}
		}

		this.watcherExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "CustomEmoji-FileWatcher");
			t.setDaemon(true);
			return t;
		});

		this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "CustomEmoji-Debouncer");
			t.setDaemon(true);
			return t;
		});

		this.watcherExecutor.submit(this::watchForChanges);

		log.info("File watcher setup complete for emoji folders");
	}

	private void registerRecursively(Path path) throws IOException
	{
		path.register(this.watchService,
			StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_DELETE,
			StandardWatchEventKinds.ENTRY_MODIFY);

		try (Stream<Path> walkStream = Files.walk(path))
		{
			walkStream
				.filter(Files::isDirectory)
				.filter(p -> !p.equals(path))
				.filter(p -> !p.getFileName().toString().equals(".git"))
				.forEach(subPath ->
				{
					try
					{
						subPath.register(this.watchService,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					}
					catch (IOException e)
					{
						log.error("Failed to register subdirectory for watching: " + subPath, e);
					}
				});
		}
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

					if (FileUtils.isEmojiFile(changed) || FileUtils.isSoundojiFile(changed))
					{
						shouldReload = true;
						log.debug("Detected change in emoji/soundoji file: " + changed);
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
									log.debug("Registered new directory for watching: " + fullPath);
								}
							}
							catch (IOException e)
							{
								log.error("Failed to register new directory: " + fullPath, e);
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
					log.debug("Watch key reset failed, re-registering directories");
					try
					{
						for (Path path : this.watchPaths)
						{
							if (Files.exists(path))
							{
								this.registerRecursively(path);
							}
						}
					}
					catch (IOException e)
					{
						log.error("Failed to re-register directories after key reset failure", e);
					}
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

	private void shutdownExecutor(ExecutorService executor, String executorName)
	{
		if (executor == null)
		{
			return;
		}

		log.debug("Shutting down {}", executorName);
		executor.shutdownNow();
	}
}