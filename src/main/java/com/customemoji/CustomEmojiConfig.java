package com.customemoji;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CustomEmojiConfig.KEY_CONFIG_GROUP)
public interface CustomEmojiConfig extends Config
{
	String KEY_CONFIG_GROUP = "custom-emote";

	// Display section
	String KEY_SPLIT_PRIVATE_CHAT = "split_private_chat";
	String KEY_DYNAMIC_EMOJI_SPACING = "dynamic_emoji_spacing";
	String KEY_CHAT_MESSAGE_SPACING = "chat_message_spacing";
	String KEY_MESSAGE_PROCESS_LIMIT = "message_process_limit";
	String KEY_MAX_IMAGE_HEIGHT = "max_image_height";
	String KEY_ANIMATION_LOADING_MODE = "animation_loading_mode";
	String KEY_FILTER_DISABLED_EMOJI_MESSAGES = "filter_disabled_emoji_messages";

	// UI Components section
	String KEY_SHOW_SIDE_PANEL = "show_panel";
	String KEY_SHOW_EMOJI_TOOLTIPS = "show_emoji_tooltips";
	String KEY_OVERLAY_MAX_SUGGESTIONS = "overlay_max_suggestions";
	String KEY_NEW_MESSAGE_INDICATOR_MODE = "new_message_indicator_mode";

	// GitHub section
	String KEY_GITHUB_ADDRESS = "github_repo_address";

	// Soundoji section
	String KEY_VOLUME = "volume";

	// Experimental section
	String KEY_NEW_EMOJI_LOADER = "new_emoji_loader";

	// Hidden
	String KEY_DISABLED_EMOJIS = "disabled_emojis";
	String KEY_RESIZING_DISABLED_EMOJIS = "resizing_disabled_emojis";

	@ConfigSection(
		name = "Display",
		description = "General emoji display settings",
		position = 0
	)
	String DISPLAY_SECTION = "displaySection";

	@ConfigItem(
		keyName = KEY_SPLIT_PRIVATE_CHAT,
		name = "Split Private Chat",
		description = "Display emojis in the split private chat window.",
		section = DISPLAY_SECTION,
		position = 0
	)
	default boolean splitPrivateChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_DYNAMIC_EMOJI_SPACING,
		name = "Dynamic Spacing",
		description = "Dynamically add extra spacing for lines with tall emojis.",
		section = DISPLAY_SECTION,
		position = 1
	)
	default boolean dynamicEmojiSpacing()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_CHAT_MESSAGE_SPACING,
		name = "Extra Spacing",
		description = "Extra vertical spacing between chat messages (in pixels).",
		section = DISPLAY_SECTION,
		position = 2
	)
	@Range(min = 0, max = 20)
	default int chatMessageSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = KEY_MESSAGE_PROCESS_LIMIT,
		name = "Message Process Limit",
		description = "Maximum number of recent messages to apply spacing to. Lower values improve performance with long chat history.  <b>(0 = unlimited)</b>",
		section = DISPLAY_SECTION,
		position = 3
	)
	@Range(min = 0, max = 500)
	default int messageProcessLimit()
	{
		return 50;
	}

	@ConfigItem(
		keyName = KEY_MAX_IMAGE_HEIGHT,
		name = "Max Emoji Height",
		description = "Maximum height (in pixels) for emojis with resizing enabled.",
		section = DISPLAY_SECTION,
		position = 4
	)
	@Range(min = 0, max = 100)
	default int maxImageHeight()
	{
		return 24;
	}

	@ConfigItem(
		keyName = KEY_ANIMATION_LOADING_MODE,
		name = "Animation Mode",
		description = "<b>Off:</b> Animations disabled.<br>" +
					  "<b>Lazy:</b> Loads frames progressively as needed. <b>[Recommended]</b><br>" +
					  "<b>Eager:</b> Loads all frames immediately.",
		section = DISPLAY_SECTION,
		position = 5
	)
	default AnimationLoadingMode animationLoadingMode()
	{
		return AnimationLoadingMode.LAZY;
	}

	@ConfigItem(
		keyName = KEY_FILTER_DISABLED_EMOJI_MESSAGES,
		name = "Message Filter",
		description = "<b>Off:</b> No filtering applied.<br>" +
			"<b>Lenient:</b> Hide messages containing only disabled emojis.<br>" +
			"<b>Strict:</b> Hide messages containing any disabled emoji.",
		section = DISPLAY_SECTION,
		position = 5
	)
	default DisabledEmojiFilterMode disabledEmojiFilterMode()
	{
		return DisabledEmojiFilterMode.OFF;
	}

	@ConfigSection(
		name = "UI Components",
		description = "Plugin panels, tooltips, and overlays",
		position = 1
	)
	String UI_COMPONENTS_SECTION = "uiComponentsSection";

	@ConfigItem(
		keyName = KEY_SHOW_SIDE_PANEL,
		name = "Show Sidebar Panel",
		description = "Show the emoji panel in the sidebar.",
		section = UI_COMPONENTS_SECTION,
		position = 0
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_EMOJI_TOOLTIPS,
		name = "Show Tooltips",
		description = "Display emoji name when hovering over emojis in chat.",
		section = UI_COMPONENTS_SECTION,
		position = 1
	)
	default boolean showEmojiTooltips()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_OVERLAY_MAX_SUGGESTIONS,
		name = "Max Suggestions",
		description = "Maximum number of emoji suggestions to display. <b>(0 = disabled)</b>",
		section = UI_COMPONENTS_SECTION,
		position = 3
	)
	default int maxImageSuggestions()
	{
		return 10;
	}

	@ConfigItem(
		keyName = KEY_NEW_MESSAGE_INDICATOR_MODE,
		name = "New Message Indicator",
		description = "<b>Off:</b> No indicator shown.<br>" +
					  "<b>Banner:</b> Shows a banner at the bottom of the chat.<br>" +
					  "<b>Arrow:</b> Shows a circular arrow button at the bottom-right.<br><br>" +
					  "When clicked, the chat will scroll to the latest message.<br>" +
					  "<b>Note:</b> The indicator is not clickable when <u>Transparent chatbox</u> and<br>" +
					  "<u>Click through transparent chatbox</u> are enabled.",
		section = UI_COMPONENTS_SECTION,
		position = 4
	)
	default NewMessageIndicatorMode newMessageIndicatorMode()
	{
		return NewMessageIndicatorMode.OFF;
	}

	@ConfigSection(
		name = "GitHub Emoji Pack",
		description = "Download emojis from a GitHub repository",
		position = 2
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

	@ConfigSection(
		name = "Soundoji",
		description = "Sound-enabled emoji settings",
		position = 3
	)
	String SOUNDOJI_SECTION = "overlaySection";

	@ConfigItem(
		keyName = KEY_VOLUME,
		name = "Volume",
		description = "Soundoji playback volume. [0-100]",
		section = SOUNDOJI_SECTION,
		position = 0
	)
	@Range(min = 0, max = 100)
	default int volume()
	{
		return 70;
	}

	@ConfigSection(
		name = "Experimental",
		description = "Experimental features (may be unstable)",
		position = 4
	)
	String EXPERIMENTAL_SECTION = "experimentalSection";

	@ConfigItem(
		keyName = KEY_NEW_EMOJI_LOADER,
		name = "New Emoji Loader",
		description = "Load emojis using the new emoji loader",
		warning = "This is an experimental feature and may cause issues with the plugin. " +
				  "Are you sure you want to toggle it?",
		section = EXPERIMENTAL_SECTION,
		position = 0
	)
	default boolean useNewEmojiLoader()
	{
		return false;
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
		OFF("Off"),
		LAZY("Lazy"),
		EAGER("Eager");

		private final String name;

		AnimationLoadingMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return this.name;
		}
	}

	enum DisabledEmojiFilterMode
	{
		OFF("Off"),
		LENIENT("Lenient"),
		STRICT("Strict");

		private final String name;

		DisabledEmojiFilterMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return this.name;
		}
	}

	enum NewMessageIndicatorMode
	{
		OFF("Off"),
		BANNER("Banner"),
		ARROW("Arrow");

		private final String name;

		NewMessageIndicatorMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return this.name;
		}
	}
}
