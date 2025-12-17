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

    @ConfigItem(
        keyName = "specWindowAlwaysOnTop",
        name = "Spec Window Always on Top",
        description = "Keep the spec validation window on top of other windows",
        position = 2
    )
    default boolean specWindowAlwaysOnTop()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showAnimationCounter",
        name = "Show Animation Counter",
        description = "Shows animation frame counter overlay",
        position = 3
    )
    default boolean showAnimationCounter()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showOverheadCrosshair",
        name = "Show Overhead Crosshair",
        description = "Shows crosshair at overhead emoji positions",
        position = 4
    )
    default boolean showOverheadCrosshair()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showEmojiBorder",
        name = "Show Emoji Border",
        description = "Shows border around overhead emojis",
        position = 5
    )
    default boolean showEmojiBorder()
    {
        return false;
    }
}