package com.customemoji.event;

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
}
