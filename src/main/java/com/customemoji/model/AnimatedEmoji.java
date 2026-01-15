package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.Value;

@Value
public class AnimatedEmoji implements Emoji
{
	int id;
	int zeroWidthId;
	String text;
	File file;
	long lastModified;
	Dimension dimension;
	BufferedImage staticImage;
}
