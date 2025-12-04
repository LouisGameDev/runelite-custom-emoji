package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.game.ChatIconManager;

import com.customemoji.CustomEmojiImageUtilities;

@Value
public class Emoji
{
	int id;
	String text;
	File file;
	long lastModified;
	Dimension dimension;
	
	/**
	 * Retrieves the cached image for this emoji from the client's mod icons.
	 *
	 * @param client The RuneLite client instance
	 * @param chatIconManager The chat icon manager for resolving icon indices
	 * @return The emoji image as a {@link BufferedImage}, or null if the sprite is not available
	 * @see CustomEmojiImageUtilities#indexedSpriteToBufferedImage(IndexedSprite)
	 */
	public BufferedImage getCacheImage(Client client, ChatIconManager chatIconManager)
	{
		int iconIndex = chatIconManager.chatIconIndex(this.id);
		IndexedSprite indexedSprite = client.getModIcons()[iconIndex];
		if (indexedSprite != null)
		{
			return CustomEmojiImageUtilities.indexedSpriteToBufferedImage(indexedSprite);
		}

		return null;
	}
}