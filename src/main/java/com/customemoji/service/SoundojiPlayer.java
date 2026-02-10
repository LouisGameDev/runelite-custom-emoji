package com.customemoji.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.event.AfterSoundojisLoaded;
import com.customemoji.event.SoundojiTriggered;
import com.customemoji.model.Lifecycle;
import com.customemoji.model.Soundoji;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
public class SoundojiPlayer implements Lifecycle
{
	private static final float NOISE_FLOOR = -60f;

	@Inject
	private EventBus eventBus;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private CustomEmojiConfig config;

	private Map<String, Soundoji> soundojis = new ConcurrentHashMap<>();

	@Override
	public void startUp()
	{
		this.eventBus.register(this);
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
	public void onAfterSoundojisLoaded(AfterSoundojisLoaded event)
	{
		this.soundojis = event.getSoundojis();
	}

	@Subscribe
	public void onSoundojiTriggered(SoundojiTriggered event)
	{
		Soundoji soundoji = this.soundojis.get(event.getTrigger().toLowerCase());
		if (soundoji == null)
		{
			return;
		}

		try
		{
			float gain = SoundojiPlayer.volumeToGain(this.config.volume());
			this.audioPlayer.play(soundoji.getFile(), gain);
		}
		catch (Exception e)
		{
			log.error("Failed to play soundoji: " + soundoji.getText(), e);
		}
	}

	static float volumeToGain(int volume100)
	{
		// Graph of the function
		// https://www.desmos.com/calculator/wdhsfbxgeo

		// clamp to 0-100
		float volume = Math.min(100, volume100);

		// convert linear volume 0-100 to log control
		if (volume <= 0.1)
		{
			return NOISE_FLOOR;
		}

		return (float) (10 * (Math.log(volume / 100)));
	}
}
