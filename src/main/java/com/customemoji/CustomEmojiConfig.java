package com.customemoji;

import net.runelite.client.config.*;

@ConfigGroup("custom-emote")
public interface CustomEmojiConfig extends Config
{
	// Info section
	@ConfigSection(
			name = "Info",
			description = "Information",
			position = 0
	)
	String infoSection = "infoSection";

	@ConfigItem(
		keyName = "instructions",
		name = "Instructions",
		description = "Link to instruction",
		position = 0,
		section = infoSection)
	default String __instructions() {
		return "https://github.com/LouisGameDev/runelite-custom-emoji/blob/master/README.md";
	}

	@ConfigItem(
		keyName = "Update 2025-08-14",
		name = "Update 2025-08-14",
		description = "Update Details",
		position = 1,
		section = infoSection
	)
	default String __update() {
		return "!emojifolder has been changed to ::emojifolder. All ! prefix commands are now :: prefix commands.";
	}

	// Emoji section
	@ConfigSection(
			name = "Emoji Settings",
			description = "Emoji configuration options",
			position = 1
	)
	String emojiSection = "emojiSettingsSection";

	@ConfigItem(
			keyName = "resize_emotes",
			name = "Resize emotes",
			description = "Configures whether to resize emotes throughout the plugin. Takes effect after plugin reload.",
			section = emojiSection,
			position = 0
	)
	default boolean resizeEmotes() { return false; }

	@ConfigItem(
			keyName = "max_image_height",
			name = "Max Emote Height",
			description = "Configures the maximum image height (in pixels) for the plugin. Only works when 'Resize Emotes' option is enabled.",
			section = emojiSection,
			position = 1
	)
	default int maxImageHeight() { return 24; }

	@ConfigItem(
		keyName = "suggestion_overlay",
		name = "Show Suggestion Overlay",
		description = "Displays a list of potential emotes in an overlay while you're typing a chat message.",
		section = emojiSection,
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlay_max_suggestions",
		name = "Max Suggestions",
		description = "Configures the maximum number of suggestions for the emote suggestion overlay.",
		section = emojiSection,
		position = 3
	)
	default int maxImageSuggestions() { return 10; }

	@ConfigItem(
		keyName = "show_emoji_tooltips",
		name = "Show Emoji Tooltips",
		description = "Shows the emoji name in a tooltip when hovering over emojis in chat messages.",
		section = emojiSection,
		position = 4
	)
	default boolean showEmojiTooltips() { return true; }

	// Soundoji section
	@ConfigSection(
			name = "Soundoji",
			description = "Soundoji configuration options",
			position = 2
	)
	String soundojiSection = "overlaySection";

	@ConfigItem(
			keyName = "volume",
			name = "Soundoji Volume",
			description = "Volume of soundojis. [0-100]",
			position = 0,
			section = soundojiSection
	)
	@Range(min = 0, max = 100)
	default int volume()
	{
		return 70;
	}

	// Chat section
	@ConfigSection(
			name = "Chat Widget",
			description = "Chat display configuration options",
			position = 3
	)
	String chatSection = "chatSection";

	@ConfigItem(
			keyName = "chat_message_spacing",
			name = "Chat Message Spacing",
			description = "Adjusts the vertical spacing between chat messages (in pixels). Default is 0.",
			section = chatSection,
			position = 0
	)
	@Range(min = 0, max = 20)
	default int chatMessageSpacing()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "show_panel",
			name = "Show Emoji Panel",
			description = "Show the emoji selection panel in the sidebar",
			section = emojiSection,
			position = 5
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
			keyName = "disabled_emojis",
			name = "",
			description = "",
			hidden = true
	)
	default String disabledEmojis()
	{
		return "";
	}
}
