package com.customemoji;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("custom-emote")
public interface CustomEmojiConfig extends Config
{
  @ConfigItem(
      keyName = "instructions",
      name = "Instructions",
      description = "Link to instruction",
      position = 0)
  default String __instructions() {
		return "https://github.com/LouisGameDev/runelite-custom-emoji/blob/master/README.md";
	}

	@ConfigItem(
		keyName = "Update 2025-08-14",
		name = "Update 2025-08-14",
		description = "Update Details",
		position = 1
	)
	default String __update() {
		return "!emojifolder has been changed to ::emojifolder. All ! prefix commands are now :: prefix commands.";
	}

	@ConfigItem(
		keyName = "volume",
		name = "Soundoji Volume",
		description = "Volume of soundojis. [0-100]"
	)
	@Range(min = 0, max = 100)
	default int volume()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "message_loaded",
		name = "Show Loaded Message",
		description = "Used for development, shows chat messages when emojis are loaded"
	)
	default boolean showLoadedMessage()
	{
		return false;
	}

}
