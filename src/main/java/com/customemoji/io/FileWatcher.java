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
			this.pendingReload.cancel(true);
			log.debug("Pending reload task cancelled");
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

			this.pendingReload = this.debounceExecutor.schedule(
				() -> this.clientThread.invokeLater(() -> this.reloadCallback.onReload(force)),
				DEBOUNCE_DELAY_MS,
				TimeUnit.MILLISECONDS
			);

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
				.filter(p -> !FileWatcher.containsGitFolder(p))
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
						log.error("Failed to register subdirectory for watching: {}", subPath, e);
					}
				});
		}
	}

	private static boolean containsGitFolder(Path path)
	{
		for (Path segment : path)
		{
			boolean isGitFolder = ".git".equals(segment.toString());
			if (isGitFolder)
			{
				return true;
			}
		}
		return false;
	}

	private void watchForChanges()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			boolean shouldStop = this.processNextWatchKey();
			if (shouldStop)
			{
				break;
			}
		}
		log.debug("File watcher thread exiting");
	}

	private boolean processNextWatchKey()
	{
		try
		{
			if (this.watchService == null)
			{
				log.debug("Watch service is null, stopping file watcher");
				return true;
			}

			WatchKey key = this.watchService.take();

			if (this.watchService == null)
			{
				log.debug("Watch service closed during take(), stopping file watcher");
				return true;
			}

			boolean shouldReload = this.processWatchEvents(key);
			if (shouldReload)
			{
				this.scheduleReload(false);
			}

			this.handleKeyReset(key);
			return false;
		}
		catch (InterruptedException e)
		{
			log.debug("File watcher interrupted, stopping");
			Thread.currentThread().interrupt();
			return true;
		}
		catch (Exception e)
		{
			return this.handleWatcherException(e);
		}
	}

	private boolean processWatchEvents(WatchKey key)
	{
		boolean shouldReload = false;
		for (WatchEvent<?> event : key.pollEvents())
		{
			boolean isRelevantFile = this.processSingleEvent(event, key);
			if (isRelevantFile)
			{
				shouldReload = true;
			}
		}
		return shouldReload;
	}

	@SuppressWarnings("unchecked")
	private boolean processSingleEvent(WatchEvent<?> event, WatchKey key)
	{
		if (event == null || event.kind() == StandardWatchEventKinds.OVERFLOW)
		{
			return false;
		}

		WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
		Path changed = pathEvent.context();

		if (changed == null)
		{
			log.debug("Skipping file event with null context");
			return false;
		}

		boolean isRelevantFile = FileUtils.isEmojiFile(changed) || FileUtils.isSoundojiFile(changed);
		if (isRelevantFile)
		{
			log.debug("Detected change in emoji/soundoji file: {}", changed);
		}

		this.handleNewDirectoryCreation(event, key, changed);

		return isRelevantFile;
	}

	private void handleNewDirectoryCreation(WatchEvent<?> event, WatchKey key, Path changed)
	{
		boolean isCreateEvent = event.kind() == StandardWatchEventKinds.ENTRY_CREATE;
		if (!isCreateEvent || key.watchable() == null)
		{
			return;
		}

		Path fullPath = ((Path) key.watchable()).resolve(changed);
		if (!Files.isDirectory(fullPath) || this.watchService == null)
		{
			return;
		}

		try
		{
			this.registerRecursively(fullPath);
			log.debug("Registered new directory for watching: {}", fullPath);
		}
		catch (IOException e)
		{
			log.error("Failed to register new directory: {}", fullPath, e);
		}
	}

	private void handleKeyReset(WatchKey key)
	{
		if (key.reset())
		{
			return;
		}

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

	private boolean handleWatcherException(Exception e)
	{
		if (this.watchService == null)
		{
			log.debug("File watcher error due to closed watch service, stopping");
			return true;
		}
		log.error("Error in file watcher", e);
		return true;
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