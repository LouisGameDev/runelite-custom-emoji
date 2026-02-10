package com.customemoji.event;

import lombok.Value;

@Value
public class EmojiStateChanged
{
	String emojiName;
	ChangeType changeType;

	public enum ChangeType
	{
		ENABLED,
		DISABLED,
		RESIZING_TOGGLED
	}
}
