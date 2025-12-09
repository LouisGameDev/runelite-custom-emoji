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
public class StaticEmoji implements Emoji
{
	int id;
	String text;
	File file;
	long lastModified;
	Dimension dimension;

	public BufferedImage getCacheImage(Client client, ChatIconManager chatIconManager)
	{
		int iconIndex = chatIconManager.chatIconIndex(this.id);
		if (iconIndex < 0)
		{
			return null;
		}

		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null || iconIndex >= modIcons.length)
		{
			return null;
		}

		IndexedSprite indexedSprite = modIcons[iconIndex];
		if (indexedSprite != null)
		{
			return CustomEmojiImageUtilities.indexedSpriteToBufferedImage(indexedSprite);
		}

		return null;
	}
}