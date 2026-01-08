package com.customemoji.debugplugin;

import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Singleton
public class GifBenchmark
{
	private static final int DEFAULT_ITERATIONS = 5;
	private static final int DEFAULT_MAX_HEIGHT = 20;

	@Setter
	private Supplier<Map<String, Emoji>> emojisSupplier;

	@Setter
	private Supplier<Integer> maxHeightSupplier;

	@Setter
	private Function<String, Boolean> resizingEnabledLookup;

	private BenchmarkResult lastResult;
	private boolean running;
	private String errorMessage;

	// Progress tracking
	@Getter
	private int currentIteration;
	@Getter
	private int totalIterations;
	@Getter
	private String currentEmojiName;

	public BenchmarkResult runBenchmark(String emojiName, int iterations)
	{
		this.running = true;
		this.errorMessage = null;
		this.lastResult = null;
		this.currentIteration = 0;
		this.totalIterations = iterations;
		this.currentEmojiName = emojiName;

		try
		{
			if (this.emojisSupplier == null)
			{
				this.errorMessage = "Benchmark not configured - emojis supplier not set";
				return null;
			}

			Map<String, Emoji> emojis = this.emojisSupplier.get();
			if (emojis == null || emojis.isEmpty())
			{
				this.errorMessage = "No emojis loaded";
				return null;
			}

			Emoji emoji = emojis.get(emojiName.toLowerCase());
			if (emoji == null)
			{
				this.errorMessage = "Emoji not found: " + emojiName;
				return null;
			}

			boolean isAnimated = emoji instanceof AnimatedEmoji;
			if (!isAnimated)
			{
				this.errorMessage = "Emoji '" + emojiName + "' is not animated (not a GIF)";
				return null;
			}

			AnimatedEmoji animatedEmoji = (AnimatedEmoji) emoji;
			File file = animatedEmoji.getFile();

			if (!file.exists())
			{
				this.errorMessage = "GIF file not found: " + file.getAbsolutePath();
				return null;
			}

			int maxHeight = this.maxHeightSupplier != null ? this.maxHeightSupplier.get() : DEFAULT_MAX_HEIGHT;
			boolean shouldResize = this.resizingEnabledLookup != null && this.resizingEnabledLookup.apply(emojiName);

			List<BenchmarkResult.IterationData> iterationDataList = new ArrayList<>();

			// Aggregation variables
			long totalFileReadTimeNs = 0;
			long totalInitTimeNs = 0;
			long totalLoadTimeNs = 0;
			long totalFrameLoadTimeNs = 0;

			long totalRetrievalTimeNs = 0;
			long minRetrievalTimeNs = Long.MAX_VALUE;
			long maxRetrievalTimeNs = 0;

			// Memory metrics (captured once from first iteration)
			long gifDataSizeBytes = 0;
			int frameCount = 0;
			int canvasWidth = 0;
			int canvasHeight = 0;
			long totalFrameMemoryBytes = 0;
			Map<String, Integer> disposalMethodCounts = new HashMap<>();

			for (int i = 0; i < iterations; i++)
			{
				this.currentIteration = i + 1;

				try (BenchmarkGifAnimation benchmark = new BenchmarkGifAnimation(file, maxHeight, shouldResize))
				{
					benchmark.runBenchmark();

					// Collect timing data
					long fileReadNs = benchmark.getFileReadTimeNs();
					long initNs = benchmark.getInitAndFirstFrameTimeNs();
					long allFramesNs = benchmark.getAllFramesLoadTimeNs();

					totalFileReadTimeNs += fileReadNs;
					totalInitTimeNs += initNs;
					totalLoadTimeNs += fileReadNs + initNs + allFramesNs;

					// Frame retrieval stats
					long avgRetrieval = benchmark.getAvgFrameRetrievalTimeNs();
					long minRetrieval = benchmark.getMinFrameRetrievalTimeNs();
					long maxRetrieval = benchmark.getMaxFrameRetrievalTimeNs();

					totalRetrievalTimeNs += avgRetrieval;
					minRetrievalTimeNs = Math.min(minRetrievalTimeNs, minRetrieval);
					maxRetrievalTimeNs = Math.max(maxRetrievalTimeNs, maxRetrieval);

					// Capture memory metrics from first iteration
					if (i == 0)
					{
						gifDataSizeBytes = benchmark.getGifDataSizeBytes();
						frameCount = benchmark.getFrameCount();
						canvasWidth = benchmark.getCanvasWidth();
						canvasHeight = benchmark.getCanvasHeight();
						totalFrameMemoryBytes = benchmark.getTotalFrameMemoryBytes();
						disposalMethodCounts.putAll(benchmark.getDisposalMethodCounts());
					}

					// Calculate per-frame load time
					int frames = benchmark.getFrameCount();
					long allFramesLoadTime = benchmark.getAllFramesLoadTimeNs();
					long avgFrameLoadNs = frames > 0 ? allFramesLoadTime / frames : 0;
					totalFrameLoadTimeNs += avgFrameLoadNs;

					BenchmarkResult.IterationData iterationData = BenchmarkResult.IterationData.builder()
						.iteration(i + 1)
						.fileReadTimeNs(fileReadNs)
						.initTimeNs(initNs)
						.firstFrameTimeNs(initNs)
						.totalLoadTimeNs(fileReadNs + initNs + allFramesNs)
						.frameLoadTimesNs(new ArrayList<>())
						.frameRetrievalTimesNs(new ArrayList<>(benchmark.getFrameRetrievalTimesNs()))
						.build();

					iterationDataList.add(iterationData);

					log.debug("Iteration {}: fileRead={}ms, init={}ms, allFrames={}ms",
						i + 1,
						fileReadNs / 1_000_000.0,
						initNs / 1_000_000.0,
						allFramesNs / 1_000_000.0);
				}
				catch (IOException e)
				{
					log.error("Benchmark iteration {} failed", i + 1, e);
				}
			}

			// Handle edge case of no successful iterations
			if (iterationDataList.isEmpty())
			{
				this.errorMessage = "All benchmark iterations failed";
				return null;
			}

			int successfulIterations = iterationDataList.size();

			this.lastResult = BenchmarkResult.builder()
				.emojiName(emojiName)
				.iterations(successfulIterations)
				.avgFileReadTimeNs(totalFileReadTimeNs / successfulIterations)
				.avgInitTimeNs(totalInitTimeNs / successfulIterations)
				.avgFirstFrameTimeNs(totalInitTimeNs / successfulIterations)
				.avgTotalLoadTimeNs(totalLoadTimeNs / successfulIterations)
				.avgFrameLoadTimeNs(totalFrameLoadTimeNs / successfulIterations)
				.avgFrameRetrievalTimeNs(totalRetrievalTimeNs / successfulIterations)
				.minFrameRetrievalTimeNs(minRetrievalTimeNs == Long.MAX_VALUE ? 0 : minRetrievalTimeNs)
				.maxFrameRetrievalTimeNs(maxRetrievalTimeNs)
				.gifDataSizeBytes(gifDataSizeBytes)
				.totalFrameMemoryBytes(totalFrameMemoryBytes)
				.frameCount(frameCount)
				.canvasWidth(canvasWidth)
				.canvasHeight(canvasHeight)
				.disposalMethodCounts(disposalMethodCounts)
				.avgResizeTimeNs(0)
				.iterationData(iterationDataList)
				.build();

			return this.lastResult;
		}
		finally
		{
			this.running = false;
		}
	}

	public BenchmarkResult runBenchmark(String emojiName)
	{
		return this.runBenchmark(emojiName, DEFAULT_ITERATIONS);
	}

	public BenchmarkResult getLastResult()
	{
		return this.lastResult;
	}

	public boolean isRunning()
	{
		return this.running;
	}

	public String getErrorMessage()
	{
		return this.errorMessage;
	}

	public void clearResult()
	{
		this.lastResult = null;
		this.errorMessage = null;
	}
}
