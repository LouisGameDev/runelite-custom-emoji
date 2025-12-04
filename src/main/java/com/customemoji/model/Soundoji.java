package com.customemoji.model;

import com.customemoji.PluginUtils;
import net.runelite.client.audio.AudioPlayer;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public class Soundoji
{
	String text;
	File file;

	/**
	 * Plays this soundoji at the specified volume.
	 *
	 * @param audioPlayer The audio player instance
	 * @param volume The volume level (0-100)
	 */
	public void play(AudioPlayer audioPlayer, int volume)
	{
		try
		{
			audioPlayer.play(this.file, PluginUtils.volumeToGain(volume));
		}
		catch (IOException | UnsupportedAudioFileException | LineUnavailableException e)
		{
			log.error("Failed to play soundoji: {}", this.text, e);
		}
	}
}