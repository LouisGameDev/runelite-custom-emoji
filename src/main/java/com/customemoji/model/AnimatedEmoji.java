package com.customemoji.model;

import java.awt.Image;
import java.io.File;

import lombok.Value;

/**
 * Represents an animated emoji loaded from a GIF file.
 * Uses ImageIcon's Image which preserves GIF animation when drawn with Graphics2D.
 */
@Value
public class AnimatedEmoji
{
	/**
	 * The trigger text for this emoji (filename without extension).
	 */
	String text;

	/**
	 * The source GIF file.
	 */
	File file;

	/**
	 * The animated Image from ImageIcon - this preserves GIF animation.
	 */
	Image animatedImage;

	/**
	 * The ChatIconManager ID for the static fallback (first frame).
	 */
	int staticIconId;

	/**
	 * Width of the animated image.
	 */
	int width;

	/**
	 * Height of the animated image.
	 */
	int height;

	/**
	 * File modification timestamp for change detection.
	 */
	long lastModified;
}
