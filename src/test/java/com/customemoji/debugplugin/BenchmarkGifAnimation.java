package com.customemoji.debugplugin;

import com.customemoji.animation.GifAnimation;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Slf4j
public class BenchmarkGifAnimation implements AutoCloseable
{
	private static final int PLAYBACK_SAMPLE_COUNT = 100;

	@Getter
	private final File file;
	@Getter
	private final int maxHeight;
	@Getter
	private final boolean shouldResize;

	private byte[] gifData;
	private GifAnimation animation;

	// Timing results (nanoseconds)
	@Getter
	private long fileReadTimeNs;
	@Getter
	private long initAndFirstFrameTimeNs;
	@Getter
	private long allFramesLoadTimeNs;
	@Getter
	private final List<Long> frameRetrievalTimesNs = new ArrayList<>();

	// Memory and metadata
	@Getter
	private int canvasWidth;
	@Getter
	private int canvasHeight;
	@Getter
	private int frameCount;
	@Getter
	private long totalFrameMemoryBytes;
	@Getter
	private final Map<String, Integer> disposalMethodCounts = new HashMap<>();

	public BenchmarkGifAnimation(File file, int maxHeight, boolean shouldResize)
	{
		this.file = file;
		this.maxHeight = maxHeight;
		this.shouldResize = shouldResize;
	}

	public void runBenchmark() throws IOException
	{
		this.readFileWithTiming();
		this.initializeWithTiming();
		this.loadAllFramesWithTiming();
		this.measurePlaybackTiming();

		// Count frames separately - not timed as part of benchmark
		this.countFramesFromGifData();
	}

	private void readFileWithTiming() throws IOException
	{
		long startTime = System.nanoTime();
		this.gifData = Files.readAllBytes(this.file.toPath());
		this.fileReadTimeNs = System.nanoTime() - startTime;
	}

	private void initializeWithTiming()
	{
		long startTime = System.nanoTime();
		this.animation = new GifAnimation(this.gifData, this.maxHeight, this.shouldResize, false);
		BufferedImage firstFrame = this.animation.getCurrentFrame();
		this.initAndFirstFrameTimeNs = System.nanoTime() - startTime;

		if (firstFrame != null)
		{
			this.canvasWidth = firstFrame.getWidth();
			this.canvasHeight = firstFrame.getHeight();
		}
	}

	private void loadAllFramesWithTiming()
	{
		long startTime = System.nanoTime();
		this.animation.loadAllFrames();
		this.allFramesLoadTimeNs = System.nanoTime() - startTime;
	}

	private void measurePlaybackTiming()
	{
		this.frameRetrievalTimesNs.clear();

		for (int i = 0; i < PLAYBACK_SAMPLE_COUNT; i++)
		{
			long startTime = System.nanoTime();
			this.animation.getCurrentFrame();
			long elapsed = System.nanoTime() - startTime;
			this.frameRetrievalTimesNs.add(elapsed);
		}
	}


	public long getGifDataSizeBytes()
	{
		return this.gifData != null ? this.gifData.length : 0;
	}

	public long getAvgFrameRetrievalTimeNs()
	{
		if (this.frameRetrievalTimesNs.isEmpty())
		{
			return 0;
		}

		long sum = 0;
		for (Long time : this.frameRetrievalTimesNs)
		{
			sum += time;
		}
		return sum / this.frameRetrievalTimesNs.size();
	}

	public long getMinFrameRetrievalTimeNs()
	{
		return this.frameRetrievalTimesNs.stream()
			.mapToLong(Long::longValue)
			.min()
			.orElse(0);
	}

	public long getMaxFrameRetrievalTimeNs()
	{
		return this.frameRetrievalTimesNs.stream()
			.mapToLong(Long::longValue)
			.max()
			.orElse(0);
	}

	private void countFramesFromGifData()
	{
		if (this.gifData == null)
		{
			return;
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(this.gifData);
			 ImageInputStream iis = ImageIO.createImageInputStream(bais))
		{
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext())
			{
				log.warn("No GIF ImageReader available");
				return;
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(iis, false, true);
				this.frameCount = reader.getNumImages(true);

				// Estimate memory: each frame is canvasWidth * canvasHeight * 4 bytes (ARGB)
				if (this.canvasWidth > 0 && this.canvasHeight > 0 && this.frameCount > 0)
				{
					long bytesPerFrame = (long) this.canvasWidth * this.canvasHeight * 4;
					this.totalFrameMemoryBytes = bytesPerFrame * this.frameCount;
				}
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to count GIF frames", e);
		}
	}

	@Override
	public void close()
	{
		if (this.animation != null)
		{
			this.animation.close();
			this.animation = null;
		}
		this.gifData = null;
	}
}
