package com.customemoji.events;

import lombok.Value;

/**
 * Event fired when emojis have been reloaded.
 * The panel subscribes to this event to refresh its display.
 */
@Value
public class EmojisReloaded
{
	int emojiCount;
	int soundojiCount;
}
