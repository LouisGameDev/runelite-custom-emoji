package com.customemoji.debugplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("custom-emoji-debug")
public interface CustomEmojiDebugConfig extends Config
{
    @ConfigItem(
        keyName = "showEmojiHitboxes",
        name = "Show Emoji Hitboxes",
        description = "Draws cyan borders around emoji hit areas in chat",
        position = 0
    )
    default boolean showEmojiHitboxes()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showRawTextTooltip",
        name = "Show Raw Text Tooltip",
        description = "Shows raw widget text when hovering over chat messages",
        position = 1
    )
    default boolean showRawTextTooltip()
    {
        return true;
    }
}