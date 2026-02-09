package com.customemoji.event;

import java.util.List;

import lombok.Value;

@Value
public class ReloadEmojisRequested
{
	List<String> newEmojis;

	public ReloadEmojisRequested()
	{
		this(List.of());
	}

	public ReloadEmojisRequested(List<String> newEmojis)
	{
		this.newEmojis = newEmojis != null ? newEmojis : List.of();
	}
}
