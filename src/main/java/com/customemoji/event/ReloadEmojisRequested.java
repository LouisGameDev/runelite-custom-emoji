package com.customemoji.event;

import java.util.List;

import lombok.Value;

@Value
public class ReloadEmojisRequested
{
	List<String> newEmojis;
	boolean forceReload;

	public ReloadEmojisRequested()
	{
		this(List.of(), false);
	}

	public ReloadEmojisRequested(boolean forceReload)
	{
		this(List.of(), forceReload);
	}

	public ReloadEmojisRequested(List<String> newEmojis)
	{
		this(newEmojis, false);
	}

	public ReloadEmojisRequested(List<String> newEmojis, boolean forceReload)
	{
		this.newEmojis = newEmojis != null ? newEmojis : List.of();
		this.forceReload = forceReload;
	}
}
