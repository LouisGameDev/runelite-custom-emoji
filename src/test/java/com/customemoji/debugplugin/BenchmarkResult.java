package com.customemoji.debugplugin;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class BenchmarkResult
{
	String emojiName;
	int iterations;

	// Loading metrics (nanoseconds)
	long avgFileReadTimeNs;
	long avgInitTimeNs;
	long avgFirstFrameTimeNs;
	long avgTotalLoadTimeNs;
	long avgFrameLoadTimeNs;

	// Playback metrics (nanoseconds)
	long avgFrameRetrievalTimeNs;
	long maxFrameRetrievalTimeNs;
	long minFrameRetrievalTimeNs;

	// Memory metrics (bytes)
	long gifDataSizeBytes;
	long totalFrameMemoryBytes;
	int frameCount;
	int canvasWidth;
	int canvasHeight;

	// Processing metrics
	Map<String, Integer> disposalMethodCounts;
	long avgResizeTimeNs;

	// Raw data for detailed logging
	List<IterationData> iterationData;

	@Value
	@Builder
	public static class IterationData
	{
		int iteration;
		long fileReadTimeNs;
		long initTimeNs;
		long firstFrameTimeNs;
		long totalLoadTimeNs;
		List<Long> frameLoadTimesNs;
		List<Long> frameRetrievalTimesNs;
	}

	public String formatAsOverlayText()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("=== GIF Benchmark: ").append(this.emojiName).append(" ===\n");
		sb.append("Iterations: ").append(this.iterations).append("\n\n");

		sb.append("LOADING\n");
		sb.append("  File read:     ").append(formatNsAsMs(this.avgFileReadTimeNs)).append("\n");
		sb.append("  Init:          ").append(formatNsAsMs(this.avgInitTimeNs)).append("\n");
		sb.append("  First frame:   ").append(formatNsAsMs(this.avgFirstFrameTimeNs)).append("\n");
		sb.append("  Total:         ").append(formatNsAsMs(this.avgTotalLoadTimeNs)).append("\n");
		sb.append("  Avg per frame: ").append(formatNsAsMs(this.avgFrameLoadTimeNs)).append("\n\n");

		sb.append("PLAYBACK\n");
		sb.append("  Avg retrieval: ").append(formatNsAsMs(this.avgFrameRetrievalTimeNs)).append("\n");
		sb.append("  Min retrieval: ").append(formatNsAsMs(this.minFrameRetrievalTimeNs)).append("\n");
		sb.append("  Max retrieval: ").append(formatNsAsMs(this.maxFrameRetrievalTimeNs)).append("\n\n");

		sb.append("MEMORY\n");
		sb.append("  GIF data:      ").append(formatBytes(this.gifDataSizeBytes)).append("\n");
		sb.append("  Frames:        ").append(this.frameCount).append("\n");
		sb.append("  Total decoded: ").append(formatBytes(this.totalFrameMemoryBytes)).append("\n");
		sb.append("  Canvas:        ").append(this.canvasWidth).append("x").append(this.canvasHeight).append("\n");

		return sb.toString();
	}

	private static String formatNsAsMs(long nanoseconds)
	{
		double ms = nanoseconds / 1_000_000.0;
		return String.format("%.2f ms", ms);
	}

	private static String formatBytes(long bytes)
	{
		if (bytes < 1024)
		{
			return bytes + " B";
		}
		if (bytes < 1024 * 1024)
		{
			return String.format("%.1f KB", bytes / 1024.0);
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}
}
