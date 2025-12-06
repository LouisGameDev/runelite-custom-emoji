package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.customemoji.Result;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FileUtils
{
    private static final int MAX_FOLDER_DEPTH = 8;

    private static final List<String> EMOJI_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif");
    private static final List<String> SOUNDOJI_EXTENSIONS = List.of(".wav");

    private FileUtils()
    {
    }

    public static List<File> flattenFolder(@NonNull File folder)
	{
		return FileUtils.flattenFolder(folder, 0);
	}

	public static List<File> flattenFolder(@NonNull File folder, int depth)
	{
		boolean maxDepthReached = depth > MAX_FOLDER_DEPTH;
		if (maxDepthReached)
		{
			log.warn("Max depth of {} was reached at path: {}", MAX_FOLDER_DEPTH, folder);
			return List.of();
		}

		boolean isFile = !folder.isDirectory();
		if (isFile)
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
			flattened.addAll(FileUtils.flattenFolder(child, depth + 1));
		}

		return flattened;
	}

	public static String extractFileNameFromErrorMessage(String errorMessage)
    {
        if (errorMessage == null)
        {
            return "";
        }

        boolean hasColorTag = errorMessage.contains("<col=");
        if (hasColorTag)
        {
            int startIndex = errorMessage.indexOf(">");
            int endIndex = errorMessage.indexOf("</col>");
            boolean hasValidIndices = startIndex != -1 && endIndex != -1 && startIndex < endIndex;

            if (hasValidIndices)
            {
                String fullPath = errorMessage.substring(startIndex + 1, endIndex);
                int separatorIndex = fullPath.lastIndexOf(File.separator);
                return fullPath.substring(separatorIndex + 1);
            }
        }

        boolean hasFileSeparator = errorMessage.contains(File.separator);
        if (hasFileSeparator)
        {
            String[] parts = errorMessage.split("[" + Pattern.quote(File.separator) + "]");
            boolean hasParts = parts.length > 0;
            if (hasParts)
            {
                return parts[parts.length - 1];
            }
        }

        return errorMessage;
    }
    
    public static Result<BufferedImage, Throwable> loadImage(File file)
    {
        try (InputStream inputStream = new FileInputStream(file))
        {
            synchronized (ImageIO.class)
            {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null)
                {
                    return Error(new IOException("image format not supported. (PNG,JPG,GIF only)"));
                }
                return Ok(image);
            }
        }
        catch (IllegalArgumentException | IOException e)
        {
            return Error(e);
        }
    }
    
    public static boolean isEmojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return EMOJI_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}

	public static boolean isSoundojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return SOUNDOJI_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}
}
