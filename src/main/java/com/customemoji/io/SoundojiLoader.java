package com.customemoji.io;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.event.AfterSoundojisLoaded;
import com.customemoji.event.ReloadEmojisRequested;
import com.customemoji.model.Lifecycle;
import com.customemoji.model.Soundoji;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ImmutableSet;

@Slf4j
@Singleton
public class SoundojiLoader implements Lifecycle
{
	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();

	private static final Set<String> SUPPORTED_AUDIO_EXTENSIONS = ImmutableSet.of("wav");

	public static boolean isSupportedAudioFormat(File file)
	{
		String name = file.getName().toLowerCase();
		int dotIndex = name.lastIndexOf('.');
		if (dotIndex < 0)
		{
			return false;
		}

		String extension = name.substring(dotIndex + 1);
		return SUPPORTED_AUDIO_EXTENSIONS.contains(extension);
	}

	@Inject
	private EventBus eventBus;

	@Getter
	private final Map<String, Soundoji> soundojis = new ConcurrentHashMap<>();

	@Override
	public void startUp()
	{
		this.firstTimeSetup();
		this.eventBus.register(this);
		this.loadAllSoundojis();
	}

	private void firstTimeSetup()
	{
		SOUNDOJIS_FOLDER.mkdir();
	}

	@Override
	public void shutDown()
	{
		this.eventBus.unregister(this);
		this.soundojis.clear();
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.volume() != 0;
	}

	@Subscribe
	public void onReloadEmojisRequested(ReloadEmojisRequested event)
	{
		this.loadAllSoundojis();
	}

	private void loadAllSoundojis()
	{
		this.soundojis.clear();

		if (!SOUNDOJIS_FOLDER.isDirectory())
		{
			log.debug("Soundojis folder not found: {}", SOUNDOJIS_FOLDER.getPath());
			return;
		}

		List<File> files = FileUtils.flattenFolder(SOUNDOJIS_FOLDER, SoundojiLoader::isSupportedAudioFormat);

		for (File file : files)
		{
			String name = FileUtils.getNameWithoutExtension(file);
			if (name == null)
			{
				log.debug("Skipping file without extension: {}", file.getName());
				continue;
			}

			this.soundojis.put(name, new Soundoji(name, file));
		}

		log.debug("Loaded {} soundojis", this.soundojis.size());
		this.eventBus.post(new AfterSoundojisLoaded(this.soundojis));
	}
}
