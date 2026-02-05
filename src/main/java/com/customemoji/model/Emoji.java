package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

public interface Emoji
{
	int getIndex();

	int getZeroWidthIndex();

	default boolean hasZeroWidthId()
	{
		return this.getZeroWidthIndex() >= 0;
	}

	int getIconId();

	void setIconId(int iconId);

	int getZeroWidthIconId();

	void setZeroWidthIconId(int zeroWidthIconId);

	String getText();

	File getFile();

	long getLastModified();

	Dimension getDimension();

	BufferedImage getStaticImage();
}