package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;

import com.customemoji.PluginUtils;
import com.customemoji.Result;
import com.customemoji.model.Soundoji;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SoundojiLoader
{
	private static final URL EXAMPLE_SOUNDOJI = Resources.getResource(SoundojiLoader.class, "../customemoji.wav");

	private final Map<String, Soundoji> soundojis;

	@Inject
	public SoundojiLoader(Map<String, Soundoji> soundojis)
	{
		this.soundojis = soundojis;
	}

	public void firstTimeSetup(File soundojisFolder)
	{
		if (soundojisFolder.mkdir())
		{
			File exampleSoundoji = new File(soundojisFolder, "customemoji.wav");
			try (InputStream in = EXAMPLE_SOUNDOJI.openStream())
			{
				Files.copy(in, exampleSoundoji.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to copy example soundoji", e);
			}
		}
	}

	public void load(File soundojiFolder)
	{
		if (soundojiFolder.mkdir())
		{
			log.info("Created soundoji folder");
		}

		Result<List<Soundoji>, List<Throwable>> result = this.loadFolder(soundojiFolder);
		result.ifOk(list ->
		{
			list.forEach(soundoji -> this.soundojis.put(soundoji.getText(), soundoji));
			log.info("Loaded {} soundojis", list.size());
		});
		result.ifError(errors ->
		{
			errors.forEach(t ->
			{
				String fileName = PluginUtils.extractFileName(t.getMessage());
				log.debug("Skipped non-audio file: {}", fileName);
			});
		});
	}

	public void clear()
	{
		this.soundojis.clear();
	}

	public int getLoadedCount()
	{
		return this.soundojis.size();
	}

	public static boolean isSoundojiFile(Path path)
	{
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.endsWith(".wav");
	}

	private Result<List<Soundoji>, List<Throwable>> loadFolder(File soundojiFolder)
	{
		List<File> files = PluginUtils.flattenFolder(soundojiFolder);

		if (!soundojiFolder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder " + soundojiFolder)));
		}

		List<Soundoji> loaded = new ArrayList<>();
		List<Throwable> errors = new ArrayList<>();

		for (File file : files)
		{
			Result<Soundoji, Throwable> result = this.loadSoundoji(file);
			result.ifOk(loaded::add);
			result.ifError(errors::add);
		}

		if (errors.isEmpty())
		{
			return Ok(loaded);
		}
		else
		{
			return PartialOk(loaded, errors);
		}
	}

	private Result<Soundoji, Throwable> loadSoundoji(File file)
	{
		int extension = file.getName().lastIndexOf('.');

		if (extension < 0)
		{
			return Error(new IllegalArgumentException("Illegal file name " + file));
		}

		String text = file.getName().substring(0, extension).toLowerCase();
		return Ok(new Soundoji(text, file));
	}
}