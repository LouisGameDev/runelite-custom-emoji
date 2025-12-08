package com.customemoji;

import net.runelite.client.config.*;

@ConfigGroup(CustomEmojiConfig.KEY_CONFIG_GROUP)
public interface CustomEmojiConfig extends Config
{
	// Configuration constants
	String KEY_CONFIG_GROUP = "custom-emote";
	String KEY_MAX_IMAGE_HEIGHT = "max_image_height";
	String KEY_SUGGESTION_OVERLAY = "suggestion_overlay";
	String KEY_OVERLAY_MAX_SUGGESTIONS = "overlay_max_suggestions";
	String KEY_SHOW_EMOJI_TOOLTIPS = "show_emoji_tooltips";
	String KEY_ENABLE_ANIMATED_EMOJIS = "enable_animated_emojis";
	String KEY_VOLUME = "volume";
	String KEY_CHAT_MESSAGE_SPACING = "chat_message_spacing";
	String KEY_SHOW_SIDE_PANEL = "show_panel";
	String KEY_DISABLED_EMOJIS = "disabled_emojis";
	String KEY_RESIZING_DISABLED_EMOJIS = "resizing_disabled_emojis";
	String KEY_SHOW_TOOLTIP_IMAGE = "show_tooltip_image";
	String KEY_TOOLTIP_IMAGE_MAX_WIDTH = "tooltip_image_max_width";
	String KEY_TOOLTIP_IMAGE_MAX_HEIGHT = "tooltip_image_max_height";
	
	// Emoji section
	@ConfigSection(
			name = "Emoji Settings",
			description = "Emoji configuration options",
			position = 1
	)
	String EMOJI_SECTION = "emojiSettingsSection";

	@ConfigItem(
			keyName = KEY_MAX_IMAGE_HEIGHT,
			name = "Max Emoji Height",
			description = "Configures the maximum image height (in pixels) for emojis with resizing enabled.",
			section = EMOJI_SECTION,
			position = 0
	)
	default int maxImageHeight() { return 24; }

	@ConfigItem(
		keyName = KEY_SUGGESTION_OVERLAY,
		name = "Show Suggestion Overlay",
		description = "Displays a list of potential emoji in an overlay while you're typing a chat message.",
		section = EMOJI_SECTION,
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_OVERLAY_MAX_SUGGESTIONS,
		name = "Max Suggestions",
		description = "Configures the maximum number of suggestions for the emoji suggestion overlay.",
		section = EMOJI_SECTION,
		position = 2
	)
	default int maxImageSuggestions() { return 10; }

	@ConfigItem(
		keyName = KEY_ENABLE_ANIMATED_EMOJIS,
		name = "Enable Animated Emojis",
		description = "Enables animation for multi-frame GIF emojis in chat.",
		section = EMOJI_SECTION,
		position = 3
	)
	default boolean enableAnimatedEmojis() { return true; }

	// Tooltip section
	@ConfigSection(
			name = "Tooltips",
			description = "Emoji tooltip configuration options",
			position = 2
	)
	String TOOLTIP_SECTION = "tooltipSection";

	@ConfigItem(
			keyName = KEY_SHOW_EMOJI_TOOLTIPS,
			name = "Show Emoji Tooltips",
			description = "Shows the emoji name in a tooltip when hovering over emojis in chat messages.",
			section = TOOLTIP_SECTION,
			position = 0
	)
	default boolean showEmojiTooltips() { return true; }

	@ConfigItem(
			keyName = KEY_SHOW_TOOLTIP_IMAGE,
			name = "Show Tooltip Image",
			description = "Shows an image preview of the emoji in the tooltip when hovering.",
			section = TOOLTIP_SECTION,
			position = 1
	)
	default boolean showTooltipImage()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_TOOLTIP_IMAGE_MAX_WIDTH,
			name = "Tooltip Image Max Width",
			description = "The maximum width (in pixels) of the emoji image in tooltips.",
			section = TOOLTIP_SECTION,
			position = 2
	)
	@Range(min = 16, max = 256)
	default int tooltipImageMaxWidth()
	{
		return 128;
	}

	@ConfigItem(
			keyName = KEY_TOOLTIP_IMAGE_MAX_HEIGHT,
			name = "Tooltip Image Max Height",
			description = "The maximum height (in pixels) of the emoji image in tooltips.",
			section = TOOLTIP_SECTION,
			position = 3
	)
	@Range(min = 16, max = 256)
	default int tooltipImageMaxHeight()
	{
		return 128;
	}

	// Soundoji section
	@ConfigSection(
			name = "Soundoji",
			description = "Soundoji configuration options",
			position = 3
	)
	String SOUNDOJI_SECTION = "overlaySection";

	@ConfigItem(
			keyName = KEY_VOLUME,
			name = "Soundoji Volume",
			description = "Volume of soundojis. [0-100]",
			position = 0,
			section = SOUNDOJI_SECTION
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
			position = 4
	)
	String CHAT_SECTION = "chatSection";

	@ConfigItem(
			keyName = KEY_CHAT_MESSAGE_SPACING,
			name = "Chat Message Spacing",
			description = "Adjusts the vertical spacing between chat messages (in pixels). Default is 0.",
			section = CHAT_SECTION,
			position = 0
	)
	@Range(min = 0, max = 20)
	default int chatMessageSpacing()
	{
		return 0;
	}

	@ConfigItem(
			keyName = KEY_SHOW_SIDE_PANEL,
			name = "Show Emoji Panel",
			description = "Show the emoji selection panel in the sidebar",
			section = EMOJI_SECTION,
			position = 4
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_DISABLED_EMOJIS,
			name = "",
			description = "",
			hidden = true
	)
	default String disabledEmojis()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_RESIZING_DISABLED_EMOJIS,
		name = "",
		description = "",
		hidden = true
	)
	default String resizingDisabledEmojis()
	{
		return "";
	}
}
