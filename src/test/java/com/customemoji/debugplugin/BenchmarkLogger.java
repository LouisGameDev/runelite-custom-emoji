package com.customemoji.debugplugin;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Singleton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class BenchmarkLogger
{
	private static final String LOG_FOLDER_NAME = "gif-benchmarks";
	private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private File lastLogFile;

	public File writeLog(BenchmarkResult result)
	{
		if (result == null)
		{
			return null;
		}

		File logFolder = this.getLogFolder();
		if (!logFolder.exists() && !logFolder.mkdirs())
		{
			log.error("Failed to create benchmark log folder: {}", logFolder);
			return null;
		}

		LocalDateTime now = LocalDateTime.now();
		String fileName = "benchmark-" + result.getEmojiName() + "-" + now.format(FILE_DATE_FORMAT) + ".log";
		File logFile = new File(logFolder, fileName);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile)))
		{
			this.writeHeader(writer, result, now);
			this.writeSummaryStatistics(writer, result);
			this.writeIterationData(writer, result);

			this.lastLogFile = logFile;
			log.info("Benchmark log written to: {}", logFile.getAbsolutePath());
			return logFile;
		}
		catch (IOException e)
		{
			log.error("Failed to write benchmark log", e);
			return null;
		}
	}

	public File getLastLogFile()
	{
		return this.lastLogFile;
	}

	private File getLogFolder()
	{
		return new File(RuneLite.RUNELITE_DIR, LOG_FOLDER_NAME);
	}

	private void writeHeader(BufferedWriter writer, BenchmarkResult result, LocalDateTime timestamp) throws IOException
	{
		writer.write("================================================================================");
		writer.newLine();
		writer.write("GIF Benchmark Report");
		writer.newLine();
		writer.write("================================================================================");
		writer.newLine();
		writer.newLine();

		writer.write("Generated: " + timestamp.format(REPORT_DATE_FORMAT));
		writer.newLine();
		writer.write("Emoji: " + result.getEmojiName());
		writer.newLine();
		writer.write("Iterations: " + result.getIterations());
		writer.newLine();
		writer.newLine();
	}

	private void writeSummaryStatistics(BufferedWriter writer, BenchmarkResult result) throws IOException
	{
		writer.write("=== SUMMARY STATISTICS ===");
		writer.newLine();
		writer.newLine();

		writer.write("--- Loading Metrics ---");
		writer.newLine();
		this.writeMetric(writer, "avgFileReadTimeNs", result.getAvgFileReadTimeNs());
		this.writeMetric(writer, "avgInitTimeNs", result.getAvgInitTimeNs());
		this.writeMetric(writer, "avgFirstFrameTimeNs", result.getAvgFirstFrameTimeNs());
		this.writeMetric(writer, "avgTotalLoadTimeNs", result.getAvgTotalLoadTimeNs());
		this.writeMetric(writer, "avgFrameLoadTimeNs", result.getAvgFrameLoadTimeNs());
		writer.newLine();

		writer.write("--- Playback Metrics ---");
		writer.newLine();
		this.writeMetric(writer, "avgFrameRetrievalTimeNs", result.getAvgFrameRetrievalTimeNs());
		this.writeMetric(writer, "minFrameRetrievalTimeNs", result.getMinFrameRetrievalTimeNs());
		this.writeMetric(writer, "maxFrameRetrievalTimeNs", result.getMaxFrameRetrievalTimeNs());
		writer.newLine();

		writer.write("--- Memory Metrics ---");
		writer.newLine();
		this.writeMetric(writer, "gifDataSizeBytes", result.getGifDataSizeBytes());
		this.writeMetric(writer, "totalFrameMemoryBytes", result.getTotalFrameMemoryBytes());
		writer.write("frameCount: " + result.getFrameCount());
		writer.newLine();
		writer.write("canvasDimensions: " + result.getCanvasWidth() + "x" + result.getCanvasHeight());
		writer.newLine();
		writer.newLine();

		writer.write("--- Disposal Methods ---");
		writer.newLine();
		Map<String, Integer> disposalCounts = result.getDisposalMethodCounts();
		if (disposalCounts != null && !disposalCounts.isEmpty())
		{
			for (Map.Entry<String, Integer> entry : disposalCounts.entrySet())
			{
				writer.write("  " + entry.getKey() + ": " + entry.getValue());
				writer.newLine();
			}
		}
		else
		{
			writer.write("  (no disposal method data)");
			writer.newLine();
		}
		writer.newLine();
	}

	private void writeIterationData(BufferedWriter writer, BenchmarkResult result) throws IOException
	{
		writer.write("=== RAW ITERATION DATA ===");
		writer.newLine();
		writer.newLine();

		List<BenchmarkResult.IterationData> iterations = result.getIterationData();
		if (iterations == null || iterations.isEmpty())
		{
			writer.write("(no iteration data available)");
			writer.newLine();
			return;
		}

		for (BenchmarkResult.IterationData iteration : iterations)
		{
			writer.write("--- Iteration " + iteration.getIteration() + " ---");
			writer.newLine();

			this.writeMetric(writer, "fileReadTimeNs", iteration.getFileReadTimeNs());
			this.writeMetric(writer, "initTimeNs", iteration.getInitTimeNs());
			this.writeMetric(writer, "firstFrameTimeNs", iteration.getFirstFrameTimeNs());
			this.writeMetric(writer, "totalLoadTimeNs", iteration.getTotalLoadTimeNs());

			List<Long> retrievalTimes = iteration.getFrameRetrievalTimesNs();
			if (retrievalTimes != null && !retrievalTimes.isEmpty())
			{
				writer.write("frameRetrievalSamples: " + retrievalTimes.size());
				writer.newLine();

				// Write first 10 and last 10 samples for brevity
				int sampleCount = Math.min(10, retrievalTimes.size());
				writer.write("  first " + sampleCount + " samples (ns): ");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < sampleCount; i++)
				{
					if (i > 0)
					{
						sb.append(", ");
					}
					sb.append(retrievalTimes.get(i));
				}
				writer.write(sb.toString());
				writer.newLine();
			}

			writer.newLine();
		}
	}

	private void writeMetric(BufferedWriter writer, String name, long valueNs) throws IOException
	{
		double ms = valueNs / 1_000_000.0;
		writer.write(String.format("%s: %d (%.3f ms)%n", name, valueNs, ms));
	}
}
