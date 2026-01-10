package com.customemoji.animation;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.service.EmojiStateManager;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class AnimationManager
{
	private static final long STALE_ANIMATION_TIMEOUT_MS = 500;
	private static final int FRAME_LOADER_THREAD_COUNT = 2;

	private final Map<Integer, GifAnimation> animationCache = new ConcurrentHashMap<>();
	private final Map<Integer, Long> animationLastSeenTime = new ConcurrentHashMap<>();
	private final Set<Integer> pendingAnimationLoads = ConcurrentHashMap.newKeySet();

	private final CustomEmojiConfig config;
	private final ScheduledExecutorService executor;
	private final EmojiStateManager emojiStateManager;
	private ExecutorService frameLoaderPool;

	@Inject
	public AnimationManager(CustomEmojiConfig config, ScheduledExecutorService executor, EmojiStateManager emojiStateManager)
	{
		this.config = config;
		this.executor = executor;
		this.emojiStateManager = emojiStateManager;
	}

	public void initialize()
	{
		if (this.frameLoaderPool == null || this.frameLoaderPool.isShutdown())
		{
			this.frameLoaderPool = Executors.newFixedThreadPool(FRAME_LOADER_THREAD_COUNT, this::createLoaderThread);
		}
	}

	public GifAnimation getOrLoadAnimation(AnimatedEmoji emoji)
	{
		int emojiId = emoji.getId();
		GifAnimation cached = this.animationCache.get(emojiId);
		if (cached != null)
		{
			this.startBackgroundLoadingIfNeeded(cached);
			this.startPreloadingIfNeeded(cached);
			return cached;
		}

		boolean isAlreadyLoading = this.pendingAnimationLoads.contains(emojiId);
		if (isAlreadyLoading)
		{
			return null;
		}

		this.pendingAnimationLoads.add(emojiId);
		String emojiText = emoji.getText();

		this.executor.submit(() ->
		{
			try
			{
				GifAnimation animation = this.loadAnimation(emoji);
				if (animation != null)
				{
					this.animationCache.put(emojiId, animation);
					log.debug("Loaded animation: {} (id={})", emojiText, emojiId);
					this.startBackgroundLoadingIfNeeded(animation);
				}
			}
			finally
			{
				this.pendingAnimationLoads.remove(emojiId);
			}
		});

		return null;
	}

	public void markAnimationVisible(int emojiId)
	{
		this.animationLastSeenTime.put(emojiId, System.currentTimeMillis());
	}

	public void unloadStaleAnimations(Set<Integer> currentlyVisibleIds)
	{
		long currentTime = System.currentTimeMillis();

		this.animationCache.entrySet().removeIf(entry ->
		{
			int emojiId = entry.getKey();
			boolean isCurrentlyVisible = currentlyVisibleIds.contains(emojiId);
			if (isCurrentlyVisible)
			{
				return false;
			}

			Long lastSeen = this.animationLastSeenTime.get(emojiId);
			if (lastSeen == null)
			{
				entry.getValue().close();
				return true;
			}

			boolean isStale = (currentTime - lastSeen) > STALE_ANIMATION_TIMEOUT_MS;
			if (isStale)
			{
				this.animationLastSeenTime.remove(emojiId);
				entry.getValue().close();
				log.debug("Unloading stale animation for emoji id: {}", emojiId);
			}
			return isStale;
		});
	}

	public void clearAllAnimations()
	{
		this.animationCache.values().forEach(GifAnimation::close);
		this.animationCache.clear();
		this.animationLastSeenTime.clear();
	}

	public void shutdown()
	{
		this.clearAllAnimations();
		this.frameLoaderPool.shutdown();
		try
		{
			boolean terminated = this.frameLoaderPool.awaitTermination(2, TimeUnit.SECONDS);
			if (!terminated)
			{
				this.frameLoaderPool.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			this.frameLoaderPool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	public void invalidateAnimation(int emojiId)
	{
		GifAnimation animation = this.animationCache.remove(emojiId);
		if (animation != null)
		{
			animation.close();
		}
		this.animationLastSeenTime.remove(emojiId);
		this.pendingAnimationLoads.remove(emojiId);
	}

		private Thread createLoaderThread(Runnable runnable)
	{
		Thread thread = new Thread(runnable);
		thread.setName("CustomEmoji-FrameLoader");
		thread.setDaemon(true);
		return thread;
	}

	private void startBackgroundLoadingIfNeeded(GifAnimation animation)
	{
		if (animation.needsBackgroundLoading())
		{
			this.frameLoaderPool.submit(animation::loadAllFrames);
		}
	}

	private void startPreloadingIfNeeded(GifAnimation animation)
	{
		if (animation.needsPreloading())
		{
			this.frameLoaderPool.submit(animation::preloadFrames);
		}
	}

	private GifAnimation loadAnimation(AnimatedEmoji emoji)
	{
		File file = emoji.getFile();
		String emojiName = emoji.getText();

		try
		{
			byte[] gifData = Files.readAllBytes(file.toPath());

			int maxHeight = this.config.maxImageHeight();
			boolean shouldResize = this.emojiStateManager.isResizingEnabled(emojiName);
			boolean useLazyLoading = this.config.lazyGifLoading();

			GifAnimation animation = new GifAnimation(gifData, maxHeight, shouldResize, useLazyLoading);

			BufferedImage firstFrame = animation.getCurrentFrame();
			if (firstFrame == null)
			{
				animation.close();
				return null;
			}

			return animation;
		}
		catch (IOException e)
		{
			log.error("Failed to load animation for emoji: {}", emojiName, e);
			return null;
		}
	}
}
