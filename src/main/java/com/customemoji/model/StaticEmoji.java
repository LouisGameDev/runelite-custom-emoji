package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.Value;

@Value
public class StaticEmoji implements Emoji
{
	int id;
	String text;
	File file;
	long lastModified;
	Dimension dimension;
	BufferedImage staticImage;
}
