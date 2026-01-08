package com.customemoji.debugplugin;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

@Singleton
public class BenchmarkOverlay extends OverlayPanel
{
	private static final Color TITLE_COLOR = Color.YELLOW;
	private static final Color LABEL_COLOR = Color.WHITE;
	private static final Color VALUE_COLOR = Color.GREEN;
	private static final Color SECTION_COLOR = Color.CYAN;
	private static final Color ERROR_COLOR = Color.RED;
	private static final Color PROGRESS_COLOR = new Color(100, 200, 255);

	private final GifBenchmark benchmark;

	private boolean visible;

	@Inject
	public BenchmarkOverlay(GifBenchmark benchmark)
	{
		this.benchmark = benchmark;
		this.setPosition(OverlayPosition.TOP_LEFT);
	}

	public void show()
	{
		this.visible = true;
	}

	public void hide()
	{
		this.visible = false;
	}

	public boolean isVisible()
	{
		return this.visible;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!this.visible)
		{
			return null;
		}

		if (this.benchmark.isRunning())
		{
			this.renderRunning();
		}
		else if (this.benchmark.getErrorMessage() != null)
		{
			this.renderError();
		}
		else if (this.benchmark.getLastResult() != null)
		{
			this.renderResult();
		}
		else
		{
			return null;
		}

		return super.render(graphics);
	}

	private void renderRunning()
	{
		String emojiName = this.benchmark.getCurrentEmojiName();
		int current = this.benchmark.getCurrentIteration();
		int total = this.benchmark.getTotalIterations();

		String title = emojiName != null ? "Benchmarking: " + emojiName : "GIF Benchmark";

		this.panelComponent.getChildren().add(TitleComponent.builder()
			.text(title)
			.color(TITLE_COLOR)
			.build());

		String progressText = current + " / " + total;

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Progress:")
			.leftColor(LABEL_COLOR)
			.right(progressText)
			.rightColor(PROGRESS_COLOR)
			.build());

		ProgressBarComponent progressBar = new ProgressBarComponent();
		progressBar.setMaximum(total);
		progressBar.setValue(current);
		progressBar.setForegroundColor(PROGRESS_COLOR);
		progressBar.setBackgroundColor(new Color(50, 50, 50));

		this.panelComponent.getChildren().add(progressBar);
	}

	private void renderError()
	{
		this.panelComponent.getChildren().add(TitleComponent.builder()
			.text("GIF Benchmark - Error")
			.color(ERROR_COLOR)
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left(this.benchmark.getErrorMessage())
			.leftColor(ERROR_COLOR)
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Press ESC to close")
			.leftColor(LABEL_COLOR)
			.build());
	}

	private void renderResult()
	{
		BenchmarkResult result = this.benchmark.getLastResult();

		this.panelComponent.getChildren().add(TitleComponent.builder()
			.text("GIF Benchmark: " + result.getEmojiName())
			.color(TITLE_COLOR)
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Iterations:")
			.leftColor(LABEL_COLOR)
			.right(String.valueOf(result.getIterations()))
			.rightColor(VALUE_COLOR)
			.build());

		this.addSectionHeader("LOADING");

		this.addMetricLine("File read", BenchmarkOverlay.formatNsAsMs(result.getAvgFileReadTimeNs()));
		this.addMetricLine("Init", BenchmarkOverlay.formatNsAsMs(result.getAvgInitTimeNs()));
		this.addMetricLine("First frame", BenchmarkOverlay.formatNsAsMs(result.getAvgFirstFrameTimeNs()));
		this.addMetricLine("Total", BenchmarkOverlay.formatNsAsMs(result.getAvgTotalLoadTimeNs()));
		this.addMetricLine("Avg per frame", BenchmarkOverlay.formatNsAsMs(result.getAvgFrameLoadTimeNs()));

		this.addSectionHeader("PLAYBACK");

		this.addMetricLine("Avg retrieval", BenchmarkOverlay.formatNsAsMs(result.getAvgFrameRetrievalTimeNs()));
		this.addMetricLine("Min retrieval", BenchmarkOverlay.formatNsAsMs(result.getMinFrameRetrievalTimeNs()));
		this.addMetricLine("Max retrieval", BenchmarkOverlay.formatNsAsMs(result.getMaxFrameRetrievalTimeNs()));

		this.addSectionHeader("MEMORY");

		this.addMetricLine("GIF data", BenchmarkOverlay.formatBytes(result.getGifDataSizeBytes()));
		this.addMetricLine("Frames", String.valueOf(result.getFrameCount()));
		this.addMetricLine("Total decoded", BenchmarkOverlay.formatBytes(result.getTotalFrameMemoryBytes()));
		this.addMetricLine("Canvas", result.getCanvasWidth() + "x" + result.getCanvasHeight());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("")
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Press ESC to close")
			.leftColor(Color.GRAY)
			.build());
	}

	private void addSectionHeader(String title)
	{
		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("")
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left(title)
			.leftColor(SECTION_COLOR)
			.build());
	}

	private void addMetricLine(String label, String value)
	{
		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("  " + label + ":")
			.leftColor(LABEL_COLOR)
			.right(value)
			.rightColor(VALUE_COLOR)
			.build());
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
