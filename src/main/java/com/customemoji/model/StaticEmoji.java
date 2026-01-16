package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StaticEmoji implements Emoji
{
	private final int id;
	private final int zeroWidthId;
	private int imageId;
	private int zeroWidthImageId;
	private final String text;
	private final File file;
	private final long lastModified;
	private final Dimension dimension;
	private final BufferedImage staticImage;
}
