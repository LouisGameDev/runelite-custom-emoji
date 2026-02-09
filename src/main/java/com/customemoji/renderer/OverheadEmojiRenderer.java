package com.customemoji.renderer;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;
import com.customemoji.animation.AnimationManager;
import com.customemoji.model.Emoji;
import com.customemoji.model.Lifecycle;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class OverheadEmojiRenderer extends EmojiRendererBase implements Lifecycle
{
	@Inject
	private AnimationManager animationManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CustomEmojiPlugin plugin;

	@Inject
	public OverheadEmojiRenderer(Client client, CustomEmojiConfig config)
	{
		super(client, config);
		this.setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public void startUp()
	{
		this.emojisSupplier = this.plugin::provideEmojis;
		this.animationLoader = this.animationManager::getOrLoadAnimation;
		this.markVisibleCallback = this.animationManager::markAnimationVisible;
		this.overlayManager.add(this);
	}

	@Override
	public void shutDown()
	{
		this.overlayManager.remove(this);
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return true;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (this.emojisSupplier == null)
		{
			return null;
		}

		if (this.client.getTopLevelWorldView() == null)
		{
			return null;
		}

		List<Player> players = this.client.getTopLevelWorldView().players().stream().collect(Collectors.toList());
		if (players.isEmpty())
		{
			return null;
		}

		Map<Integer, Emoji> emojiLookup = PluginUtils.buildEmojiLookup(this.emojisSupplier);
		Set<Integer> visibleEmojiIds = new HashSet<>();

		for (Player player : players)
		{
			if (!this.shouldShowOverheadForPlayer(player))
			{
				continue;
			}

			this.renderPlayerOverhead(graphics, player, visibleEmojiIds, emojiLookup);
		}

		this.cleanupStaleEmojis(visibleEmojiIds);

		return null;
	}

	private void renderPlayerOverhead(Graphics2D graphics, Player player, Set<Integer> visibleEmojiIds, Map<Integer, Emoji> emojiLookup)
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

		Point centerPoint = player.getCanvasTextLocation(graphics, "", player.getLogicalHeight());
		if (centerPoint == null)
		{
			return;
		}

		int centerX = centerPoint.getX();
		int baseY = centerPoint.getY();

		IndexedSprite[] modIcons = this.client.getModIcons();
		EmojiPositionCalculator.DimensionLookup dimensionLookup = imageId -> PluginUtils.getEmojiDimension(modIcons, imageId);

		List<EmojiPosition> positions = EmojiPositionCalculator.calculateOverheadEmojiPositions(graphics, overheadText, centerX, baseY, dimensionLookup);

		PluginUtils.linkZeroWidthEmojisToTarget(positions, emojiLookup);

		for (EmojiPosition position : positions)
		{
			Emoji emoji = emojiLookup.get(position.getImageId());
			if (emoji != null)
			{
				this.renderEmoji(graphics, emoji, position, visibleEmojiIds);
			}
		}
	}

	private void renderEmoji(Graphics2D graphics, Emoji emoji, EmojiPosition position, Set<Integer> visibleEmojiIds)
	{
		if (this.isEmojiDisabled(emoji))
		{
			return;
		}

		int emojiId = emoji.getIndex();
		BufferedImage image = this.resolveEmojiImage(emoji, emojiId, visibleEmojiIds);
		this.drawEmojiImage(graphics, image, position);
	}

	private boolean shouldShowOverheadForPlayer(Player player)
	{
		boolean isLocalPlayer = player == this.client.getLocalPlayer();

		if (isLocalPlayer)
		{
			return true;
		}

		Widget publicChatFilterWidget = this.client.getWidget(InterfaceID.Chatbox.CHAT_PUBLIC_FILTER);
		if (publicChatFilterWidget == null)
		{
			return true;
		}

		String filterText = publicChatFilterWidget.getText();
		if (filterText == null)
		{
			return true;
		}

		boolean isOn = filterText.contains("On");
		boolean isHide = filterText.contains("Hide");
		boolean isFriends = filterText.contains("Friends");
		boolean isOff = filterText.contains("Off");
		boolean isAutochat = filterText.contains("Autochat");

		if (isOn || isHide)
		{
			return true;
		}

		if (isOff || isAutochat)
		{
			return false;
		}

		if (isFriends)
		{
			String playerName = player.getName();
			if (playerName == null)
			{
				return false;
			}

			boolean isFriend = this.client.isFriended(playerName, false);
			return isFriend;
		}

		return true;
	}
}
