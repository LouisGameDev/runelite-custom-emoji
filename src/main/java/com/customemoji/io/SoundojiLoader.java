package com.customemoji.io;

import static com.customemoji.Result.Error;
import static com.customemoji.Result.Ok;
import static com.customemoji.Result.PartialOk;

import com.customemoji.Result;
import com.customemoji.model.Soundoji;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
@Singleton
public class SoundojiLoader
{
	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();

	public void loadSoundojis(Map<String, Soundoji> soundojis)
	{
		if (SOUNDOJIS_FOLDER.mkdir())
		{
			log.debug("Created soundoji folder: {}", SOUNDOJIS_FOLDER);
		}

		Result<List<Soundoji>, List<Throwable>> result = this.loadSoundojisFromFolder(SOUNDOJIS_FOLDER);
		result.ifOk(list ->
		{
			list.forEach(soundoji -> soundojis.put(soundoji.getText(), soundoji));
			log.info("Loaded {} soundojis", result.unwrap().size());
		});
		result.ifError(errors ->
			errors.forEach(throwable ->
			{
				String fileName = FileUtils.extractFileNameFromErrorMessage(throwable.getMessage());
				log.debug("Skipped non-audio file: {}", fileName);
			})
		);
	}

	public Result<List<Soundoji>, List<Throwable>> loadSoundojisFromFolder(File folder)
	{
		if (!folder.isDirectory())
		{
			return Error(List.of(new IllegalArgumentException("Not a folder: " + folder)));
		}

		List<File> files = FileUtils.flattenFolder(folder);
		List<Soundoji> loadedSoundojis = new ArrayList<>();
		List<Throwable> errors = new ArrayList<>();

		for (File file : files)
		{
			Result<Soundoji, Throwable> result = this.loadSoundoji(file);
			result.ifOk(loadedSoundojis::add);
			result.ifError(errors::add);
		}

		if (errors.isEmpty())
		{
			return Ok(loadedSoundojis);
		}
		else
		{
			return PartialOk(loadedSoundojis, errors);
		}
	}

	public Result<Soundoji, Throwable> loadSoundoji(File file)
	{
		int extensionIndex = file.getName().lastIndexOf('.');
		boolean hasNoExtension = extensionIndex < 0;

		if (hasNoExtension)
		{
			return Error(new IllegalArgumentException("Illegal file name " + file));
		}

		String soundojiName = file.getName().substring(0, extensionIndex).toLowerCase();
		return Ok(new Soundoji(soundojiName, file));
	}
}
