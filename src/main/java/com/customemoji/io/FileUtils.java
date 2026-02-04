package com.customemoji.io;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils
{
	private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif");

	private FileUtils()
	{
	}

	@Value
	public static class ImageMetadata
	{
		boolean isAnimated;
		Dimension dimension;
	}

	public static boolean isSupportedImageFormat(File file)
	{
		String fileName = file.getName().toLowerCase();
		return SUPPORTED_IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}

	public static BufferedImage loadImage(final File file)
	{
		if (!FileUtils.isSupportedImageFormat(file))
		{
			log.error("Unsupported image format: {}", file.getName());
			return null;
		}

		try (ImageInputStream imageStream = ImageIO.createImageInputStream(file))
		{
			if (imageStream != null)
			{
				Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
				while (readers.hasNext())
				{
					ImageReader reader = readers.next();
					try
					{
						reader.setInput(imageStream);
						BufferedImage image = reader.read(0);
						if (image != null)
						{
							return image;
						}
					}
					finally
					{
						reader.dispose();
					}
				}
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load image from file: {}", file.getPath(), e);
			return null;
		}

		log.error("No ImageReader found for file: {}", file.getPath());
		return null;
	}

	@Nullable
	public static ImageMetadata getImageMetadata(File file)
	{
		try (ImageInputStream inputStream = ImageIO.createImageInputStream(file))
		{
			if (inputStream == null)
			{
				log.error("Failed to create ImageInputStream for file: {}", file.getPath());
				return null;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
			if (!readers.hasNext())
			{
				log.error("No ImageReader found for file: {}", file.getPath());
				return null;
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(inputStream);

				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				Dimension dimension = new Dimension(width, height);

				boolean isAnimated = false;
				String formatName = reader.getFormatName();
				boolean isGif = formatName != null && formatName.equalsIgnoreCase("gif");
				if (isGif)
				{
					int frameCount = reader.getNumImages(true);
					isAnimated = frameCount > 1;
				}

				return new ImageMetadata(isAnimated, dimension);
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException e)
		{
			log.error("Failed to read image metadata for file: {}", file.getPath(), e);
			return null;
		}
	}

	public static List<File> flattenFolder(@NonNull File folder)
	{
		return FileUtils.flattenFolder(folder, 0);
	}

	private static List<File> flattenFolder(@NonNull File folder, int depth)
	{
		// sanity guard
		final long MAX_DEPTH = 8;

		if (depth > MAX_DEPTH)
		{
			log.warn("Max depth of {} was reached path:{}", depth, folder);
			return List.of();
		}

		// file found
		if (!folder.isDirectory())
		{
			return List.of(folder);
		}

		// no children
		File[] children = folder.listFiles();
		if (children == null)
		{
			return List.of();
		}

		List<File> flattened = new ArrayList<>();
		for (File child : children)
		{
			boolean isDirectory = child.isDirectory();
			boolean isSupportedImage = FileUtils.isSupportedImageFormat(child);

			if (!isDirectory && !isSupportedImage)
			{
				continue;
			}

			flattened.addAll(flattenFolder(child, depth + 1));
		}

		return flattened;
	}
}
