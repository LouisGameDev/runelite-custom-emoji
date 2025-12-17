package com.customemoji.animation;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiImageUtilities;
import com.customemoji.model.AnimatedEmoji;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.w3c.dom.NodeList;

@Slf4j
@Singleton
public class AnimationManager
{
	private static final long STALE_ANIMATION_TIMEOUT_MS = 500;
	private static final int DEFAULT_FRAME_DELAY_MS = 100;

	private final Map<Integer, GifAnimation> animationCache = new ConcurrentHashMap<>();
	private final Map<Integer, Long> animationLastSeenTime = new ConcurrentHashMap<>();
	private final Set<Integer> pendingAnimationLoads = ConcurrentHashMap.newKeySet();

	private final CustomEmojiConfig config;
	private final ScheduledExecutorService executor;

	@Inject
	public AnimationManager(CustomEmojiConfig config, ScheduledExecutorService executor)
	{
		this.config = config;
		this.executor = executor;
	}

	public void markAnimationVisible(int emojiId)
	{
		this.animationLastSeenTime.put(emojiId, System.currentTimeMillis());
	}

	public GifAnimation getOrLoadAnimation(AnimatedEmoji emoji)
	{
		int emojiId = emoji.getId();
		GifAnimation cached = this.animationCache.get(emojiId);
		if (cached != null)
		{
			return cached;
		}

		// Already loading - return null and wait for next frame
		boolean isAlreadyLoading = this.pendingAnimationLoads.contains(emojiId);
		if (isAlreadyLoading)
		{
			return null;
		}

		// Start async load
		this.pendingAnimationLoads.add(emojiId);
		File file = emoji.getFile();
		String emojiText = emoji.getText();

		this.executor.submit(() ->
		{
			try
			{
				GifAnimation animation = this.loadAnimation(emoji);
				if (animation != null)
				{
					this.animationCache.put(emojiId, animation);
					log.debug("Loaded animation: {} (id={}, frames={})", emojiText, emojiId, animation.getFrameCount());
				}
			}
			finally
			{
				this.pendingAnimationLoads.remove(emojiId);
			}
		});

		return null;
	}

	public void unloadStaleAnimations(Set<Integer> currentlyVisibleIds)
	{
		long currentTime = System.currentTimeMillis();

		this.animationCache.keySet().removeIf(emojiId ->
		{
			boolean isCurrentlyVisible = currentlyVisibleIds.contains(emojiId);
			if (isCurrentlyVisible)
			{
				return false;
			}

			Long lastSeen = this.animationLastSeenTime.get(emojiId);
			if (lastSeen == null)
			{
				return true;
			}

			boolean isStale = (currentTime - lastSeen) > STALE_ANIMATION_TIMEOUT_MS;
			if (isStale)
			{
				this.animationLastSeenTime.remove(emojiId);
				log.debug("Unloading stale animation for emoji id: {}", emojiId);
			}
			return isStale;
		});
	}

	public void clearAllAnimations()
	{
		this.animationCache.clear();
		this.animationLastSeenTime.clear();
	}

	private GifAnimation loadAnimation(AnimatedEmoji emoji)
	{
		File file = emoji.getFile();

		try
		{
			byte[] gifData = this.loadFileToMemory(file);
			if (gifData == null)
			{
				return null;
			}

			return this.extractFramesFromGifData(gifData);
		}
		catch (IOException e)
		{
			log.error("Failed to load animation for emoji: {}", emoji.getText(), e);
			return null;
		}
	}

	private byte[] loadFileToMemory(File file) throws IOException
	{
		try (FileInputStream fis = new FileInputStream(file);
			 ByteArrayOutputStream baos = new ByteArrayOutputStream())
		{
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1)
			{
				baos.write(buffer, 0, bytesRead);
			}
			return baos.toByteArray();
		}
	}

	private GifAnimation extractFramesFromGifData(byte[] gifData) throws IOException
	{
		try (ByteArrayInputStream bais = new ByteArrayInputStream(gifData);
			 ImageInputStream stream = ImageIO.createImageInputStream(bais))
		{
			if (stream == null)
			{
				return null;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext())
			{
				return null;
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(stream);
				int frameCount = reader.getNumImages(true);
				if (frameCount <= 0)
				{
					return null;
				}

				BufferedImage[] frames = new BufferedImage[frameCount];
				int[] delays = new int[frameCount];

				int maxHeight = this.config.maxImageHeight();

				for (int i = 0; i < frameCount; i++)
				{
					BufferedImage frame = reader.read(i);
					delays[i] = this.getFrameDelay(reader, i);

					BufferedImage resizedFrame = CustomEmojiImageUtilities.resizeImage(frame, maxHeight);
					frames[i] = CustomEmojiImageUtilities.fixPureBlackPixels(resizedFrame);
				}

				return new GifAnimation(frames, delays);
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	private int getFrameDelay(ImageReader reader, int frameIndex)
	{
		try
		{
			IIOMetadata metadata = reader.getImageMetadata(frameIndex);
			if (metadata == null)
			{
				return DEFAULT_FRAME_DELAY_MS;
			}

			String formatName = metadata.getNativeMetadataFormatName();
			IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

			NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
			if (gceList.getLength() > 0)
			{
				IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);
				String delayStr = gce.getAttribute("delayTime");
				if (delayStr != null && !delayStr.isEmpty())
				{
					int delayHundredths = Integer.parseInt(delayStr);
					int delayMs = delayHundredths * 10;
					return delayMs > 0 ? delayMs : DEFAULT_FRAME_DELAY_MS;
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to get frame delay for frame {}", frameIndex, e);
		}

		return DEFAULT_FRAME_DELAY_MS;
	}
}
