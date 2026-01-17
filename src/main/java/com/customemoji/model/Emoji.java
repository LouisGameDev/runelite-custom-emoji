package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

public interface Emoji
{
	int getId();

	int getZeroWidthId();

	default boolean hasZeroWidthId()
	{
		return this.getZeroWidthId() >= 0;
	}

	int getImageId();

	void setImageId(int imageId);

	int getZeroWidthImageId();

	void setZeroWidthImageId(int zeroWidthImageId);

	String getText();

	File getFile();

	long getLastModified();

	Dimension getDimension();

	BufferedImage getStaticImage();
}