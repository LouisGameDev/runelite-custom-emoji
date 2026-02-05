package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class StaticEmoji implements Emoji
{
	private final int index;
	@Builder.Default
	private final int zeroWidthIndex = -1;
	private int iconId;
	@Builder.Default
	private int zeroWidthIconId = -1;
	private final String text;
	private final File file;
	private final long lastModified;
	private final Dimension dimension;
	private final BufferedImage staticImage;
}
