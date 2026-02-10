package com.customemoji.io;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

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
	private FileUtils()
	{
	}

	@Value
	public static class ImageMetadata
	{
		boolean isAnimated;
		Dimension dimension;
	}

	public static BufferedImage loadImage(final File file)
	{
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

	@Nullable
	public static String getNameWithoutExtension(File file)
	{
		int extensionIndex = file.getName().lastIndexOf('.');
		if (extensionIndex <= 0)
		{
			return null;
		}

		return file.getName().substring(0, extensionIndex).toLowerCase();
	}

	public static List<File> flattenFolder(@NonNull File folder, Predicate<File> fileFilter)
	{
		return FileUtils.flattenFolder(folder, fileFilter, 0);
	}

	private static List<File> flattenFolder(@NonNull File folder, Predicate<File> fileFilter, int depth)
	{
		final int MAX_DEPTH = 8;

		if (depth > MAX_DEPTH)
		{
			log.warn("Max depth of {} was reached path:{}", depth, folder);
			return List.of();
		}

		if (!folder.isDirectory())
		{
			return List.of(folder);
		}

		File[] children = folder.listFiles();
		if (children == null)
		{
			return List.of();
		}

		List<File> flattened = new ArrayList<>();
		for (File child : children)
		{
			boolean isDirectory = child.isDirectory();

			if (!isDirectory && !fileFilter.test(child))
			{
				continue;
			}

			flattened.addAll(FileUtils.flattenFolder(child, fileFilter, depth + 1));
		}

		return flattened;
	}
}
