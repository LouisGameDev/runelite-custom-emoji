package com.customemoji;

import net.runelite.client.config.*;

@ConfigGroup("custom-emote")
public interface CustomEmojiConfig extends Config
{
	@ConfigSection(
		name = "Overlay",
		description = "Configuration for the emote suggestion overlay",
		position = 10
	)
	String overlaySection = "overlaySection";

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

	@ConfigItem(
			keyName = "suggestion_overlay",
			name = "Show Overlay",
			description = "Displays a list of potential emotes in an overlay while you're typing a chat message.",
			section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlay_image_height",
			name = "Max Image Height",
			description = "Configures the maximum image height for the emote suggestion overlay.",
			section = overlaySection
	)
	default int maxImageHeight() { return 24; }



}
