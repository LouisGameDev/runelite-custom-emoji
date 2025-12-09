package com.customemoji.debugplugin;

import com.customemoji.PluginUtils;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class OverheadDebugOverlay extends Overlay
{
	private static final Pattern IMG_PATTERN = Pattern.compile("<img=(\\d+)>");

	private final Client client;
	private final CustomEmojiDebugConfig config;

	@Inject
	public OverheadDebugOverlay(Client client, CustomEmojiDebugConfig config)
	{
		this.client = client;
		this.config = config;
		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean showCrosshair = this.config.showOverheadCrosshair();
		boolean showEmojiBorder = this.config.showEmojiBorder();

		if (!showCrosshair && !showEmojiBorder)
		{
			return null;
		}

		Player localPlayer = this.client.getLocalPlayer();
		if (localPlayer != null && showCrosshair)
		{
			this.renderLocalPlayerCrosshair(graphics, localPlayer);
		}

		if (showEmojiBorder)
		{
			this.renderEmojiBorders(graphics);
		}

		return null;
	}

	private void renderLocalPlayerCrosshair(Graphics2D graphics, Player localPlayer)
	{
		Point debugPoint = localPlayer.getCanvasTextLocation(graphics, "TEST", localPlayer.getLogicalHeight());
		if (debugPoint == null)
		{
			return;
		}

		graphics.setColor(Color.CYAN);
		graphics.drawLine(debugPoint.getX() - 15, debugPoint.getY(), debugPoint.getX() + 15, debugPoint.getY());
		graphics.drawLine(debugPoint.getX(), debugPoint.getY() - 15, debugPoint.getX(), debugPoint.getY() + 15);
	}

	private void renderEmojiBorders(Graphics2D graphics)
	{
		if (this.client.getTopLevelWorldView() == null)
		{
			return;
		}

		List<Player> players = this.client.getTopLevelWorldView().players().stream().collect(Collectors.toList());
		if (players.isEmpty())
		{
			return;
		}

		for (Player player : players)
		{
			this.renderPlayerEmojiBorders(graphics, player);
		}
	}

	private void renderPlayerEmojiBorders(Graphics2D graphics, Player player)
	{
		if (player == null)
		{
			return;
		}

		String overheadText = player.getOverheadText();
		if (overheadText == null || overheadText.isEmpty())
		{
			return;
		}

		boolean hasImageTag = overheadText.contains("<img=");
		if (!hasImageTag)
		{
			return;
		}

		Point basePoint = player.getCanvasTextLocation(graphics, overheadText, player.getLogicalHeight());
		if (basePoint == null)
		{
			return;
		}

		Font font = FontManager.getRunescapeSmallFont();
		FontMetrics metrics = graphics.getFontMetrics(font);
		IndexedSprite[] modIcons = this.client.getModIcons();

		int totalWidth = this.calculateTotalWidth(overheadText, metrics, modIcons);
		int centerX = basePoint.getX();
		int startX = centerX - (totalWidth / 2);

		int currentX = startX;
		int baseY = basePoint.getY();
		int textIndex = 0;
		Matcher matcher = IMG_PATTERN.matcher(overheadText);

		graphics.setColor(Color.MAGENTA);

		while (matcher.find())
		{
			String textBefore = overheadText.substring(textIndex, matcher.start());
			String cleanTextBefore = this.removeHtmlTags(textBefore);
			currentX += metrics.stringWidth(cleanTextBefore);

			int imageId = Integer.parseInt(matcher.group(1));

			Dimension emojiDim = PluginUtils.getEmojiDimension(modIcons, imageId);
			int emojiWidth = emojiDim != null ? emojiDim.width : 12;
			int emojiHeight = emojiDim != null ? emojiDim.height : 12;

			int emojiY = baseY - emojiHeight + 2;
			graphics.drawRect(currentX, emojiY, emojiWidth, emojiHeight);

			currentX += emojiWidth;
			textIndex = matcher.end();
		}
	}

	private int calculateTotalWidth(String text, FontMetrics metrics, IndexedSprite[] modIcons)
	{
		int totalWidth = 0;
		int textIndex = 0;
		Matcher matcher = IMG_PATTERN.matcher(text);

		while (matcher.find())
		{
			String textBefore = text.substring(textIndex, matcher.start());
			String cleanTextBefore = this.removeHtmlTags(textBefore);
			totalWidth += metrics.stringWidth(cleanTextBefore);

			int imageId = Integer.parseInt(matcher.group(1));
			Dimension emojiDim = PluginUtils.getEmojiDimension(modIcons, imageId);
			int emojiWidth = emojiDim != null ? emojiDim.width : 12;
			totalWidth += emojiWidth;

			textIndex = matcher.end();
		}

		String remainingText = text.substring(textIndex);
		String cleanRemaining = this.removeHtmlTags(remainingText);
		totalWidth += metrics.stringWidth(cleanRemaining);

		return totalWidth;
	}

	private String removeHtmlTags(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replaceAll("<[^>]*>", "");
	}
}
