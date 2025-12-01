package com.customemoji;

import net.runelite.client.config.*;

@ConfigGroup("custom-emote")
public interface CustomEmojiConfig extends Config
{
	// Configuration key constants
	String KEY_RESIZE_EMOJI = "resize_emoji";
	String KEY_MAX_IMAGE_HEIGHT = "max_image_height";
	String KEY_SUGGESTION_OVERLAY = "suggestion_overlay";
	String KEY_OVERLAY_MAX_SUGGESTIONS = "overlay_max_suggestions";
	String KEY_SHOW_EMOJI_TOOLTIPS = "show_emoji_tooltips";
	String KEY_VOLUME = "volume";
	String KEY_CHAT_MESSAGE_SPACING = "chat_message_spacing";
	
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
			keyName = KEY_RESIZE_EMOJI,
			name = "Resize amoji",
			description = "Configures whether to resize emoji throughout the plugin. Takes effect after plugin reload.",
			section = emojiSection,
			position = 0
	)
	default boolean resizeEmoji() { return false; }

	@ConfigItem(
			keyName = KEY_MAX_IMAGE_HEIGHT,
			name = "Max Emoji Height",
			description = "Configures the maximum image height (in pixels) for the plugin. Only works when 'Resize Emoji' option is enabled.",
			section = emojiSection,
			position = 1
	)
	default int maxImageHeight() { return 24; }

	@ConfigItem(
		keyName = KEY_SUGGESTION_OVERLAY,
		name = "Show Suggestion Overlay",
		description = "Displays a list of potential emoji in an overlay while you're typing a chat message.",
		section = emojiSection,
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_OVERLAY_MAX_SUGGESTIONS,
		name = "Max Suggestions",
		description = "Configures the maximum number of suggestions for the emoji suggestion overlay.",
		section = emojiSection,
		position = 3
	)
	default int maxImageSuggestions() { return 10; }

	@ConfigItem(
		keyName = KEY_SHOW_EMOJI_TOOLTIPS,
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
			keyName = KEY_VOLUME,
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
			keyName = KEY_CHAT_MESSAGE_SPACING,
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
}
