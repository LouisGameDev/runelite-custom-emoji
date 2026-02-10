package com.customemoji.event;

import java.util.Map;

import com.customemoji.model.Soundoji;

import lombok.Value;

@Value
public class AfterSoundojisLoaded
{
	Map<String, Soundoji> soundojis;
}
