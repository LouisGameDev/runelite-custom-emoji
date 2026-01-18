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
	String KEY_ANIMATION_LOADING_MODE = "animation_loading_mode";
	String KEY_VOLUME = "volume";
	String KEY_CHAT_MESSAGE_SPACING = "chat_message_spacing";
	String KEY_DYNAMIC_EMOJI_SPACING = "dynamic_emoji_spacing";
	String KEY_SHOW_SIDE_PANEL = "show_panel";
	String KEY_DISABLED_EMOJIS = "disabled_emojis";
	String KEY_RESIZING_DISABLED_EMOJIS = "resizing_disabled_emojis";
	String KEY_GITHUB_ADDRESS = "github_repo_address";
	
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
		keyName = KEY_SHOW_EMOJI_TOOLTIPS,
		name = "Show Emoji Tooltips",
		description = "Shows the emoji name in a tooltip when hovering over emojis in chat messages.",
		section = EMOJI_SECTION,
		position = 3
	)
	default boolean showEmojiTooltips() { return true; }

	@ConfigItem(
		keyName = KEY_ENABLE_ANIMATED_EMOJIS,
		name = "Enable Animated Emojis",
		description = "Enables animation for multi-frame GIF emojis in chat.",
		section = EMOJI_SECTION,
		position = 4
	)
	default boolean enableAnimatedEmojis() { return true; }

	@ConfigItem(
		keyName = KEY_ANIMATION_LOADING_MODE,
		name = "Animation Loading Mode",
		description = "<b>Lazy:</b> Loads animation frames progressively as needed. <b>[Recommended]</b><br>" +
					  "<b>Eager:</b> Loads all animation frames immediately.",
		section = EMOJI_SECTION,
		position = 5
	)
	default AnimationLoadingMode animationLoadingMode() { return AnimationLoadingMode.LAZY; }

	// Soundoji section
	@ConfigSection(
			name = "Soundoji",
			description = "Soundoji configuration options",
			position = 2
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
			position = 3
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
			keyName = KEY_DYNAMIC_EMOJI_SPACING,
			name = "Dynamic Emoji Spacing",
			description = "Automatically adds extra spacing for chat lines containing tall emojis to prevent overlap.",
			section = CHAT_SECTION,
			position = 1
	)
	default boolean dynamicEmojiSpacing()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_SIDE_PANEL,
			name = "Show Emoji Panel",
			description = "Show the emoji selection panel in the sidebar",
			section = EMOJI_SECTION,
			position = 6
	)
	default boolean showPanel()
	{
		return true;
	}

	// GitHub Pack section
	@ConfigSection(
			name = "GitHub Emoji Pack",
			description = "Download emojis from a GitHub repository",
			position = 4
	)
	String GITHUB_SECTION = "githubSection";

	@ConfigItem(
			keyName = KEY_GITHUB_ADDRESS,
			name = "Repository",
			description = "GitHub repository in format 'user/repo' or 'user/repo/tree/branch'. Leave empty to disable.",
			section = GITHUB_SECTION,
			position = 0
	)
	default String githubRepoUrl()
	{
		return "";
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

	enum AnimationLoadingMode
	{
		LAZY,
		EAGER,
		;
	}
}
