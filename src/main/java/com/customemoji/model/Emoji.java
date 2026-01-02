package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import net.runelite.api.Client;
import net.runelite.client.game.ChatIconManager;

public interface Emoji
{
	int getId();

	String getText();

	File getFile();

	long getLastModified();

	Dimension getDimension();

	BufferedImage getStaticImage();

	BufferedImage getCacheImage(Client client, ChatIconManager chatIconManager);
}