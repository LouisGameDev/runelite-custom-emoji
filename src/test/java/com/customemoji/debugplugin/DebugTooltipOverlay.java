package com.customemoji.debugplugin;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.CustomEmojiTooltip;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import java.awt.Color;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public class DebugTooltipOverlay extends OverlayPanel
{
	private static final String RUNELITE_CONFIG_GROUP = "runelite";
	private static final String OVERLAY_BACKGROUND_COLOR_KEY = "overlayBackgroundColor";

	private final CustomEmojiDebugConfig config;
	private final EventBus eventBus;
	private final ConfigManager configManager;

	private Emoji currentEmoji = null;
	private int currentImageId = -1;
	private int currentFrameCount = -1;
	private int currentFrame = -1;

	@Inject
	public DebugTooltipOverlay(CustomEmojiDebugConfig config, EventBus eventBus, ConfigManager configManager)
	{
		this.config = config;
		this.eventBus = eventBus;
		this.configManager = configManager;

		this.setPosition(OverlayPosition.TOP_LEFT);
	}

	public void startUp()
	{
		this.eventBus.register(this);
	}

	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.clearData();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		boolean isOurNamespace = CustomEmojiTooltip.PLUGIN_MESSAGE_NAMESPACE.equals(event.getNamespace());
		boolean isHoverMessage = CustomEmojiTooltip.TOOLTIP_HOVER_MESSAGE.equals(event.getName());

		if (!isOurNamespace || !isHoverMessage)
		{
			return;
		}

		Object emojiValue = event.getData().get(CustomEmojiTooltip.TOOLTIP_HOVER_EMOJI_KEY);
		Object imageIdValue = event.getData().get(CustomEmojiTooltip.TOOLTIP_HOVER_IMAGE_ID_KEY);
		Object frameCountValue = event.getData().get(CustomEmojiTooltip.TOOLTIP_HOVER_FRAME_COUNT_KEY);
		Object currentFrameValue = event.getData().get(CustomEmojiTooltip.TOOLTIP_HOVER_CURRENT_FRAME_KEY);

		if (emojiValue instanceof Emoji)
		{
			this.currentEmoji = (Emoji) emojiValue;
		}
		else
		{
			this.currentEmoji = null;
		}

		if (imageIdValue instanceof Integer)
		{
			this.currentImageId = (Integer) imageIdValue;
		}
		else
		{
			this.currentImageId = -1;
		}

		if (frameCountValue instanceof Integer)
		{
			this.currentFrameCount = (Integer) frameCountValue;
		}
		else
		{
			this.currentFrameCount = -1;
		}

		if (currentFrameValue instanceof Integer)
		{
			this.currentFrame = (Integer) currentFrameValue;
		}
		else
		{
			this.currentFrame = -1;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!this.config.showTooltipDebugInfo())
		{
			return null;
		}

		if (this.currentEmoji == null)
		{
			this.panelComponent.setBackgroundColor(new Color(0, 0, 0, 0));
			this.panelComponent.getChildren().add(LineComponent.builder()
				.left(" ")
				.build());
			return super.render(graphics);
		}

		this.panelComponent.setBackgroundColor(this.getOverlayBackgroundColor());
		this.panelComponent.getChildren().add(TitleComponent.builder()
			.text("Emoji Debug")
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Image ID:")
			.right(String.valueOf(this.currentImageId))
			.build());

		boolean isAnimated = this.currentEmoji instanceof AnimatedEmoji;
		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Animated:")
			.right(String.valueOf(isAnimated))
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("File Size:")
			.right(this.formatFileSize(this.currentEmoji.getFile().length()))
			.build());

		String fileDim = "unknown";
		try
		{
			BufferedImage fileImage = ImageIO.read(this.currentEmoji.getFile());
			if (fileImage != null)
			{
				fileDim = fileImage.getWidth() + "x" + fileImage.getHeight();
			}
		}
		catch (IOException e)
		{
			// leave as unknown
		}

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("File Dim:")
			.right(fileDim)
			.build());

		Dimension chatDim = this.currentEmoji.getDimension();
		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("Chat Dim:")
			.right(chatDim.width + "x" + chatDim.height)
			.build());

		this.panelComponent.getChildren().add(LineComponent.builder()
			.left("File:")
			.right(this.currentEmoji.getFile().getName())
			.build());

		if (isAnimated && this.currentFrameCount >= 0)
		{
			this.panelComponent.getChildren().add(LineComponent.builder()
				.left("Frames:")
				.right(String.valueOf(this.currentFrameCount))
				.build());

			this.panelComponent.getChildren().add(LineComponent.builder()
				.left("Current:")
				.right(String.valueOf(this.currentFrame))
				.build());
		}

		return super.render(graphics);
	}

	private void clearData()
	{
		this.currentEmoji = null;
		this.currentImageId = -1;
		this.currentFrameCount = -1;
		this.currentFrame = -1;
	}

	private String formatFileSize(long bytes)
	{
		if (bytes >= 1024 * 1024)
		{
			double mb = bytes / (1024.0 * 1024.0);
			return String.format("%.2f MB", mb);
		}
		double kb = bytes / 1024.0;
		return String.format("%.2f KB", kb);
	}

	private Color getOverlayBackgroundColor()
	{
		Color color = this.configManager.getConfiguration(RUNELITE_CONFIG_GROUP, OVERLAY_BACKGROUND_COLOR_KEY, Color.class);
		if (color != null)
		{
			return color;
		}
		return ComponentConstants.STANDARD_BACKGROUND_COLOR;
	}
}
