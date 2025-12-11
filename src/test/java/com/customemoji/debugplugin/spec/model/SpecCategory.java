package com.customemoji.debugplugin.spec.model;

public enum SpecCategory
{
    EMOJI_LOADING("Emoji Loading", "LOAD", "File loading, formats, folders"),
    ANIMATED_EMOJI("Animated Emoji", "ANIM", "GIF rendering, memory, timing"),
    SOUNDOJI("Soundoji", "SND", "WAV playback, volume"),
    SUGGESTION_OVERLAY("Suggestion Overlay", "SUG", "Typing suggestions"),
    TOOLTIP_SYSTEM("Tooltip System", "TIP", "Hover tooltips"),
    CHAT_SPACING("Chat Spacing", "SPC", "Line height management"),
    CONTEXT_MENU("Context Menu", "CTX", "Right-click menu"),
    EMOJI_PANEL("Emoji Panel", "PNL", "Sidebar browser"),
    HOT_RELOAD("Hot Reload", "HOT", "File watching"),
    IMAGE_PROCESSING("Image Processing", "IMG", "Resizing, transparency");

    private final String displayName;
    private final String prefix;
    private final String description;

    SpecCategory(String displayName, String prefix, String description)
    {
        this.displayName = displayName;
        this.prefix = prefix;
        this.description = description;
    }

    public String getDisplayName()
    {
        return this.displayName;
    }

    public String getPrefix()
    {
        return this.prefix;
    }

    public String getDescription()
    {
        return this.description;
    }

    @Override
    public String toString()
    {
        return this.displayName;
    }
}
