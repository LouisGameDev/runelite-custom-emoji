package com.customemoji.event;

import java.util.List;
import java.util.Map;

import com.customemoji.model.Emoji;

import lombok.Value;

/**
 * Event fired when the EmojiLoader has finished loading all emojis.
 */
@Value
public class AfterEmojisLoaded
{
	Map<String, Emoji> emojis;
	List<String> newEmojis;

	public AfterEmojisLoaded(Map<String, Emoji> emojis)
	{
		this.emojis = emojis;
		this.newEmojis = List.of();
	}

	public AfterEmojisLoaded(Map<String, Emoji> emojis, List<String> newEmojis)
	{
		this.emojis = emojis;
		this.newEmojis = newEmojis != null ? newEmojis : List.of();
	}
}
