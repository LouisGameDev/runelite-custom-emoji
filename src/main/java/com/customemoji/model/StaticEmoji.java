package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.Value;
import net.runelite.api.Client;
import net.runelite.client.game.ChatIconManager;

@Value
public class StaticEmoji implements Emoji
{
	int id;
	String text;
	File file;
	long lastModified;
	Dimension dimension;
	BufferedImage staticImage;

	@Override
	public BufferedImage getCacheImage(Client client, ChatIconManager chatIconManager)
	{
		return this.staticImage;
	}
}
